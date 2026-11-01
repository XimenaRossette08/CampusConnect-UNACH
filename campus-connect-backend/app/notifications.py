import json
import logging
import os
from datetime import datetime, timedelta, timezone
from typing import List, Optional

from sqlalchemy.orm import Session

from .database import SessionLocal
from .fechas import DURACION_EVENTO_HORAS, ahora_utc, asegurar_utc, evento_disponible, evento_finalizado
from .models import Evento, EventoAsistencia
from .models_auth import Usuario
from .segmentacion import evento_visible_para

logger = logging.getLogger("uvicorn.error")

# Intentar cargar firebase_admin
try:
    import firebase_admin
    from firebase_admin import credentials, messaging
    FIREBASE_SDK_DISPONIBLE = True
except ImportError:
    firebase_admin = None
    credentials = None
    messaging = None
    FIREBASE_SDK_DISPONIBLE = False

# Intentar cargar APScheduler
try:
    from apscheduler.schedulers.background import BackgroundScheduler
    APSCHEDULER_DISPONIBLE = True
except ImportError:
    BackgroundScheduler = None
    APSCHEDULER_DISPONIBLE = False

_firebase_inicializado = False
_scheduler = None


def inicializar_firebase() -> bool:
    """
    Inicializa Firebase Admin SDK soportando:
    1. Variable de entorno FIREBASE_CREDENTIALS_JSON (ideal para Render: pega el contenido JSON directo).
    2. Archivo en ruta FIREBASE_CREDENTIALS_PATH o 'serviceAccountKey.json' / '/etc/secrets/serviceAccountKey.json'.
    """
    global _firebase_inicializado
    if _firebase_inicializado:
        return True

    if not FIREBASE_SDK_DISPONIBLE:
        logger.warning("[FCM] La librería 'firebase-admin' no está instalada.")
        return False

    if firebase_admin._apps:
        _firebase_inicializado = True
        return True

    # Opción 1: Leer JSON directo desde variable de entorno FIREBASE_CREDENTIALS_JSON en Render
    raw_json = os.getenv("FIREBASE_CREDENTIALS_JSON", "").strip()
    if raw_json:
        try:
            info_dict = json.loads(raw_json)
            # Corregir saltos de línea escapados en private_key si Render los escapó
            if "private_key" in info_dict and isinstance(info_dict["private_key"], str):
                info_dict["private_key"] = info_dict["private_key"].replace("\\n", "\n")
            cred = credentials.Certificate(info_dict)
            firebase_admin.initialize_app(cred)
            _firebase_inicializado = True
            logger.info("[FCM] Firebase Admin SDK inicializado desde FIREBASE_CREDENTIALS_JSON.")
            return True
        except Exception as exc:
            logger.error(f"[FCM] Error al leer FIREBASE_CREDENTIALS_JSON: {exc}")

    # Opción 2: Buscar archivo en disco o Secret Files de Render (/etc/secrets/...)
    rutas_posibles = [
        os.getenv("FIREBASE_CREDENTIALS_PATH", "").strip(),
        "/etc/secrets/serviceAccountKey.json",
        "/etc/secrets/firebase-credentials.json",
        "serviceAccountKey.json",
        "firebase-credentials.json",
        os.path.join(os.path.dirname(__file__), "serviceAccountKey.json"),
        os.path.join(os.path.dirname(os.path.dirname(__file__)), "serviceAccountKey.json"),
    ]
    ruta_cred = next((r for r in rutas_posibles if r and os.path.isfile(r)), None)

    if not ruta_cred:
        logger.warning(
            "[FCM] No se encontró FIREBASE_CREDENTIALS_JSON ni 'serviceAccountKey.json'. "
            "Configura FIREBASE_CREDENTIALS_JSON en Render Environment Variables."
        )
        return False

    try:
        cred = credentials.Certificate(ruta_cred)
        firebase_admin.initialize_app(cred)
        _firebase_inicializado = True
        logger.info(f"[FCM] Firebase Admin SDK inicializado correctamente con archivo: {ruta_cred}")
        return True
    except Exception as exc:
        logger.error(f"[FCM] Error al inicializar Firebase Admin SDK: {exc}")
        return False


def enviar_push_multicast(tokens: List[str], titulo: str, cuerpo: str, data_extra: Optional[dict] = None) -> dict:
    """
    Envía una notificación push de Firebase con prioridad alta y canal Android 'campus_connect_channel'.
    """
    tokens_limpios = list({t.strip() for t in tokens if t and t.strip()})
    if not tokens_limpios:
        print(f"[FCM] Sin tokens FCM registrados para enviar: '{titulo}' -> '{cuerpo}'")
        return {"enviados": 0, "total_tokens": 0, "modo": "sin_destinatarios"}

    print(f"\n[FCM PUSH] Disparando notificación a {len(tokens_limpios)} dispositivo(s):")
    print(f"  • Título : {titulo}")
    print(f"  • Mensaje: {cuerpo}\n")

    if not inicializar_firebase():
        return {
            "enviados": 0,
            "total_tokens": len(tokens_limpios),
            "modo": "simulacion_consola_sin_credenciales",
            "titulo": titulo,
            "cuerpo": cuerpo,
        }

    enviados_ok = 0
    payload_data = {k: str(v) for k, v in (data_extra or {}).items()}
    payload_data.setdefault("title", titulo)
    payload_data.setdefault("body", cuerpo)

    try:
        android_cfg = messaging.AndroidConfig(
            priority="high",
            notification=messaging.AndroidNotification(
                channel_id="campus_connect_channel",
                priority="high",
                default_sound=True,
                default_vibrate_timings=True,
            ),
        )
        mensaje = messaging.MulticastMessage(
            notification=messaging.Notification(title=titulo, body=cuerpo),
            data=payload_data,
            android=android_cfg,
            tokens=tokens_limpios,
        )
        if hasattr(messaging, "send_each_for_multicast"):
            respuesta = messaging.send_each_for_multicast(mensaje)
        else:
            respuesta = messaging.send_multicast(mensaje)
        enviados_ok = respuesta.success_count
        logger.info(f"[FCM] Notificación enviada con éxito a {enviados_ok}/{len(tokens_limpios)} dispositivos.")
    except Exception as exc:
        logger.error(f"[FCM] Error al enviar multicast FCM: {exc}")

    return {"enviados": enviados_ok, "total_tokens": len(tokens_limpios), "modo": "firebase_real"}


def enviar_notificacion_nuevo_evento(db: Session, evento: Evento) -> dict:
    """
    Envía el push de aparición de evento a todos los usuarios activos con token FCM
    (respetando segmentación si aplica) y marca `evento.notificacion_aparicion_enviada = True`.
    """
    usuarios_con_token = (
        db.query(Usuario)
        .filter(Usuario.activo == True, Usuario.fcm_token.isnot(None))  # noqa: E712
        .all()
    )

    tokens_destino = [
        u.fcm_token
        for u in usuarios_con_token
        if u.fcm_token and (evento.es_general or evento_visible_para(evento, u))
    ]

    if not tokens_destino and usuarios_con_token:
        tokens_destino = [u.fcm_token for u in usuarios_con_token if u.fcm_token]

    titulo_push = f"¡Nuevo evento disponible: {evento.titulo}!"
    cuerpo_push = f"Sede: {evento.lugar} · Ya puedes consultar los detalles e inscribirte en Campus Connect UNACH."

    res = enviar_push_multicast(
        tokens=tokens_destino,
        titulo=titulo_push,
        cuerpo=cuerpo_push,
        data_extra={
            "tipo": "evento_disponible",
            "evento_id": str(evento.id),
            "titulo": evento.titulo,
        },
    )

    evento.notificacion_aparicion_enviada = True
    db.commit()
    return res


def _enviar_aviso_30m_antes_de_finalizar(db: Session, ev: Evento) -> int:
    """
    Envía notificación ÚNICAMENTE a los alumnos inscritos en `evento_asistencias`
    cuando faltan 30 minutos o menos para que el evento finalice (`fecha_fin`).
    """
    inscritos = (
        db.query(Usuario)
        .join(EventoAsistencia, EventoAsistencia.usuario_id == Usuario.id)
        .filter(
            EventoAsistencia.evento_id == ev.id,
            Usuario.activo == True,  # noqa: E712
            Usuario.fcm_token.isnot(None),
        )
        .all()
    )
    tokens_inscritos = [u.fcm_token for u in inscritos if u.fcm_token]

    titulo_push = f"⏳ Últimos 30 minutos: {ev.titulo}"
    cuerpo_push = f"El evento en {ev.lugar} está por finalizar en 30 minutos. ¡No olvides confirmar tu asistencia!"

    res = enviar_push_multicast(
        tokens=tokens_inscritos,
        titulo=titulo_push,
        cuerpo=cuerpo_push,
        data_extra={
            "tipo": "aviso_30m_fin",
            "evento_id": str(ev.id),
            "titulo": ev.titulo,
        },
    )
    ev.notificacion_fin_30m_enviada = True
    db.commit()
    return res.get("total_tokens", 0)


def verificar_y_enviar_recordatorios_24h() -> dict:
    """
    Trabajador que se ejecuta cada 1 minuto en segundo plano y verifica 3 condiciones:
    1. Eventos programados cuya hora de aparición (`disponible_desde`) acaba de cumplirse
       y aún no han enviado su notificación de aparición (`notificacion_aparicion_enviada == False`).
    2. Eventos activos a los que les faltan 30 minutos o menos para alcanzar su hora de término (`fecha_fin`)
       -> envía notificación SOLO a los alumnos inscritos (`notificacion_fin_30m_enviada == False`).
    3. Recordatorio 24h antes de iniciar para los alumnos inscritos (`recordatorio_enviado == False`).
    """
    db = SessionLocal()
    apariciones_enviadas = 0
    cierres_30m_enviados = 0
    recordatorios_24h_enviados = 0

    try:
        ahora = ahora_utc()
        todos_eventos = db.query(Evento).all()

        for ev in todos_eventos:
            fin_real = asegurar_utc(ev.fecha_fin) if ev.fecha_fin is not None else (
                asegurar_utc(ev.fecha_hora) + timedelta(hours=DURACION_EVENTO_HORAS)
            )
            ha_finalizado = evento_finalizado(ev.fecha_hora, ahora=ahora, fecha_fin=ev.fecha_fin)
            esta_disponible = evento_disponible(ev.disponible_desde, ahora=ahora)

            # 1. ¿Acaba de aparecer en la app (`disponible_desde` cumplido) y no se ha notificado?
            if esta_disponible and not ha_finalizado and not getattr(ev, "notificacion_aparicion_enviada", False):
                enviar_notificacion_nuevo_evento(db, ev)
                apariciones_enviadas += 1

            # 2. ¿Faltan 30 minutos o menos para finalizar (`fin_real`) y no se ha enviado el aviso de 30 min?
            if not ha_finalizado and fin_real is not None and not getattr(ev, "notificacion_fin_30m_enviada", False):
                tiempo_restante = fin_real - ahora
                if timedelta(seconds=0) < tiempo_restante <= timedelta(minutes=30):
                    _enviar_aviso_30m_antes_de_finalizar(db, ev)
                    cierres_30m_enviados += 1

            # 3. Recordatorio 24h antes del inicio (solo a inscritos)
            inicio_utc = asegurar_utc(ev.fecha_hora)
            if (
                not ha_finalizado
                and inicio_utc is not None
                and ahora < inicio_utc <= (ahora + timedelta(hours=24))
                and not ev.recordatorio_enviado
            ):
                inscritos = (
                    db.query(Usuario)
                    .join(EventoAsistencia, EventoAsistencia.usuario_id == Usuario.id)
                    .filter(
                        EventoAsistencia.evento_id == ev.id,
                        Usuario.activo == True,  # noqa: E712
                        Usuario.fcm_token.isnot(None),
                    )
                    .all()
                )
                tokens_inscritos = [u.fcm_token for u in inscritos if u.fcm_token]
                if tokens_inscritos:
                    enviar_push_multicast(
                        tokens=tokens_inscritos,
                        titulo=f"Recordatorio: Próximo evento {ev.titulo}",
                        cuerpo=f"Te esperamos en {ev.lugar}. ¡Prepara tu código QR en Campus Connect!",
                        data_extra={
                            "tipo": "recordatorio_24h",
                            "evento_id": str(ev.id),
                            "titulo": ev.titulo,
                        },
                    )
                ev.recordatorio_enviado = True
                db.commit()
                recordatorios_24h_enviados += 1

        return {
            "apariciones_notificadas": apariciones_enviadas,
            "avisos_30m_fin_notificados": cierres_30m_enviados,
            "recordatorios_24h_notificados": recordatorios_24h_enviados,
        }
    except Exception as exc:
        logger.error(f"[Scheduler FCM] Error al procesar notificaciones programadas: {exc}")
        return {"error": str(exc)}
    finally:
        db.close()


def iniciar_scheduler_recordatorios() -> None:
    """
    Inicia APScheduler revisando cada 1 MINUTO para que las notificaciones de aparición
    y de 30 minutos antes de finalizar salgan puntualmente.
    """
    global _scheduler
    if _scheduler is not None:
        return

    if not APSCHEDULER_DISPONIBLE:
        logger.warning("[Scheduler FCM] La librería 'apscheduler' no está instalada.")
        return

    _scheduler = BackgroundScheduler(timezone="UTC")
    _scheduler.add_job(
        verificar_y_enviar_recordatorios_24h,
        trigger="interval",
        minutes=1,
        id="job_notificaciones_eventos_1min",
        replace_existing=True,
    )
    _scheduler.start()
    logger.info("[Scheduler FCM] Trabajador APScheduler iniciado (revisión cada 1 minuto).")


def detener_scheduler_recordatorios() -> None:
    global _scheduler
    if _scheduler is not None:
        try:
            _scheduler.shutdown(wait=False)
        except Exception:
            pass
        _scheduler = None