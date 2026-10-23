import logging
import os
from datetime import datetime, timedelta, timezone
from typing import List, Optional

from sqlalchemy.orm import Session

from .database import SessionLocal
from .models import Evento, EventoAsistencia
from .models_auth import Usuario
from .segmentacion import evento_visible_para

logger = logging.getLogger("uvicorn.error")

# Intentar cargar firebase_admin (si está instalado)
try:
    import firebase_admin
    from firebase_admin import credentials, messaging
    FIREBASE_SDK_DISPONIBLE = True
except ImportError:
    firebase_admin = None
    credentials = None
    messaging = None
    FIREBASE_SDK_DISPONIBLE = False

# Intentar cargar APScheduler para el trabajador en segundo plano (recordatorios 24h antes)
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
    1. Integrar Firebase Admin SDK:
    Busca el archivo JSON de credenciales de Firebase en la variable de entorno
    FIREBASE_CREDENTIALS_PATH (o por defecto 'serviceAccountKey.json' / 'firebase-credentials.json').
    """
    global _firebase_inicializado
    if _firebase_inicializado:
        return True

    if not FIREBASE_SDK_DISPONIBLE:
        logger.warning("[FCM] La librería 'firebase-admin' no está instalada. Ejecuta: pip install firebase-admin")
        return False

    if firebase_admin._apps:
        _firebase_inicializado = True
        return True

    rutas_posibles = [
        os.getenv("FIREBASE_CREDENTIALS_PATH", "").strip(),
        "serviceAccountKey.json",
        "firebase-credentials.json",
        os.path.join(os.path.dirname(__file__), "serviceAccountKey.json"),
        os.path.join(os.path.dirname(os.path.dirname(__file__)), "serviceAccountKey.json"),
    ]
    ruta_cred = next((r for r in rutas_posibles if r and os.path.isfile(r)), None)

    if not ruta_cred:
        logger.warning(
            "[FCM] No se encontró 'serviceAccountKey.json'. Coloca el archivo JSON de Firebase en la raíz "
            "del backend o define FIREBASE_CREDENTIALS_PATH en tu archivo .env."
        )
        return False

    try:
        cred = credentials.Certificate(ruta_cred)
        firebase_admin.initialize_app(cred)
        _firebase_inicializado = True
        logger.info(f"[FCM] Firebase Admin SDK inicializado correctamente con: {ruta_cred}")
        return True
    except Exception as exc:
        logger.error(f"[FCM] Error al inicializar Firebase Admin SDK: {exc}")
        return False


def enviar_push_multicast(tokens: List[str], titulo: str, cuerpo: str, data_extra: Optional[dict] = None) -> dict:
    """
    Envía una notificación push de Firebase a una lista de tokens FCM de dispositivos Android.
    """
    tokens_limpios = list({t.strip() for t in tokens if t and t.strip()})
    if not tokens_limpios:
        print(f"[FCM] Sin tokens FCM registrados para enviar: '{titulo}' -> '{cuerpo}'")
        return {"enviados": 0, "total_tokens": 0, "modo": "sin_destinatarios"}

    print(f"\n[FCM PUSH] Disparando notificación a {len(tokens_limpios)} dispositivo(s):")
    print(f"  • Título : {titulo}")
    print(f"  • Mensaje: {cuerpo}\n")

    if not inicializar_firebase():
        # Modo simulación segura en consola si aún no se ha colocado serviceAccountKey.json
        return {
            "enviados": len(tokens_limpios),
            "total_tokens": len(tokens_limpios),
            "modo": "simulacion_consola",
            "titulo": titulo,
            "cuerpo": cuerpo,
        }

    enviados_ok = 0
    payload_data = {k: str(v) for k, v in (data_extra or {}).items()}

    # Enviar usando send_each_for_multicast (API moderna de firebase-admin >= 6.x)
    try:
        mensaje = messaging.MulticastMessage(
            notification=messaging.Notification(title=titulo, body=cuerpo),
            data=payload_data,
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
    2. Notificación de "Nuevo Evento":
    En el mismo momento en que el administrador hace clic en "Publicar Evento" y los datos
    se guardan en PostgreSQL, envía un mensaje masivo de Firebase a todos los tokens de los
    alumnos registrados indicando: "¡Nuevo evento disponible: [Título del Evento]!".
    """
    usuarios_con_token = (
        db.query(Usuario)
        .filter(Usuario.activo == True, Usuario.fcm_token.isnot(None))  # noqa: E712
        .all()
    )

    # Filtrar alumnos a los que va dirigido el evento (o todos si el evento es general)
    tokens_destino = [
        u.fcm_token
        for u in usuarios_con_token
        if u.fcm_token and (evento.es_general or evento_visible_para(evento, u))
    ]

    # Si ningún usuario coincide por segmentación pero hay tokens registrados, enviar a todos los registrados
    if not tokens_destino and usuarios_con_token:
        tokens_destino = [u.fcm_token for u in usuarios_con_token if u.fcm_token]

    titulo_push = f"¡Nuevo evento disponible: {evento.titulo}!"
    cuerpo_push = f"Lugar: {evento.lugar} · Consulta los detalles e inscríbete en Campus Connect UNACH."

    return enviar_push_multicast(
        tokens=tokens_destino,
        titulo=titulo_push,
        cuerpo=cuerpo_push,
        data_extra={
            "tipo": "nuevo_evento",
            "evento_id": str(evento.id),
            "titulo": evento.titulo,
        },
    )


def verificar_y_enviar_recordatorios_24h() -> dict:
    """
    3. Notificación de "Recordatorio" (Job Scheduler con APScheduler):
    Se ejecuta automáticamente cada hora, busca en la base de datos:
    "¿Qué eventos empiezan en las próximas 24 horas y aún no tienen recordatorio enviado?".
    Si encuentra uno, busca qué alumnos están inscritos a ese evento (tabla evento_asistencias)
    y les dispara la notificación a sus respectivos tokens FCM:
    "Recordatorio: Mañana es el evento [Título]".
    """
    db = SessionLocal()
    eventos_notificados = 0
    alumnos_notificados = 0
    try:
        ahora = datetime.now(timezone.utc)
        limite_24h = ahora + timedelta(hours=24)

        eventos_proximos = (
            db.query(Evento)
            .filter(
                Evento.fecha_hora > ahora,
                Evento.fecha_hora <= limite_24h,
                Evento.recordatorio_enviado == False,  # noqa: E712
            )
            .all()
        )

        for ev in eventos_proximos:
            # Buscar todos los alumnos inscritos a este evento en evento_asistencias
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

            titulo_push = f"Recordatorio: Mañana es el evento {ev.titulo}"
            cuerpo_push = f"Te esperamos en {ev.lugar}. ¡Prepara tu código QR de acceso en Campus Connect!"

            res = enviar_push_multicast(
                tokens=tokens_inscritos,
                titulo=titulo_push,
                cuerpo=cuerpo_push,
                data_extra={
                    "tipo": "recordatorio_24h",
                    "evento_id": str(ev.id),
                    "titulo": ev.titulo,
                },
            )

            # Marcar como enviado para no repetir el recordatorio cada hora
            ev.recordatorio_enviado = True
            db.commit()
            eventos_notificados += 1
            alumnos_notificados += res.get("total_tokens", 0)

        return {
            "eventos_procesados": eventos_notificados,
            "alumnos_notificados": alumnos_notificados,
        }
    except Exception as exc:
        logger.error(f"[Scheduler FCM] Error al procesar recordatorios de 24h: {exc}")
        return {"error": str(exc)}
    finally:
        db.close()


def iniciar_scheduler_recordatorios() -> None:
    """
    Inicia el trabajador en segundo plano (APScheduler) al arrancar FastAPI.
    Se ejecuta cada 60 minutos de forma automática.
    """
    global _scheduler
    if _scheduler is not None:
        return

    if not APSCHEDULER_DISPONIBLE:
        logger.warning(
            "[Scheduler FCM] La librería 'apscheduler' no está instalada. "
            "Ejecuta: pip install apscheduler"
        )
        return

    _scheduler = BackgroundScheduler(timezone="UTC")
    _scheduler.add_job(
        verificar_y_enviar_recordatorios_24h,
        trigger="interval",
        hours=1,
        id="job_recordatorios_eventos_24h",
        replace_existing=True,
    )
    _scheduler.start()
    logger.info("[Scheduler FCM] Trabajador APScheduler iniciado (revisión de recordatorios 24h cada 1 hora).")


def detener_scheduler_recordatorios() -> None:
    global _scheduler
    if _scheduler is not None:
        try:
            _scheduler.shutdown(wait=False)
        except Exception:
            pass
        _scheduler = None
