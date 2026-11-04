import math
import json
import re
import unicodedata
import uuid
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Set, Tuple

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, Response, UploadFile, status
from pydantic import BaseModel, Field
from sqlalchemy import text
from sqlalchemy.orm import Session, selectinload

from ..auditoria import registrar_auditoria
from ..database import get_db
from ..fechas import (
    DURACION_EVENTO_HORAS,
    ahora_utc,
    asegurar_utc,
    evento_disponible,
    evento_finalizado,
    fecha_corta,
    fecha_larga,
)
from ..models import Evento, EventoArchivo, EventoAsistencia
from ..models_auth import Usuario
from ..notifications import (
    _enviar_aviso_30m_antes_de_finalizar,
    enviar_notificacion_evento_finalizado,
    enviar_notificacion_nuevo_evento,
    verificar_y_enviar_recordatorios_24h,
)
from ..pdf_asistencia import generar_pdf_asistencia
from ..publicaciones import audiencia_texto
from ..schemas import ArchivoOut, EventoCreate, EventoOut
from ..security import get_current_user, requiere_permiso, verificar_rol_admin
from ..segmentacion import evento_visible_para
from ..storage import ArchivoInvalido, borrar_archivo, guardar_archivo, limpiar_nombre_original

router = APIRouter(prefix="/eventos", tags=["eventos"])

AUDITORIOS_CAPACIDAD_POR_DEFECTO: Dict[str, int] = {
    "auditorio principal etda": 180,
    "sala audiovisual 1": 60,
    "sala audiovisual 2": 50,
    "laboratorio de cómputo a": 35,
    "laboratorio de computo a": 35,
    "auditorio los constituyentes": 350,
    "explanada central etda": 250,
}

CATEGORIAS_OFICIALES_RECOMPENSAS = [
    "Tutorías",
    "Conferencias",
    "Talleres",
    "Académicos",
    "Culturales",
    "Deporte",
]


def _codigo_asiento_por_indice(indice_base_0: int, columnas_por_fila: int = 10) -> str:
    """Genera códigos tipo cine: A-01 .. A-10, B-01 .. B-10, ..., Z-10, AA-01."""
    fila_idx = max(0, indice_base_0) // columnas_por_fila
    col_num = (max(0, indice_base_0) % columnas_por_fila) + 1
    if fila_idx < 26:
        letra_fila = chr(ord("A") + fila_idx)
    else:
        primera = chr(ord("A") + (fila_idx // 26) - 1)
        segunda = chr(ord("A") + (fila_idx % 26))
        letra_fila = f"{primera}{segunda}"
    return f"{letra_fila}-{col_num:02d}"


def _catalogo_codigos_asiento_validos(capacidad_total: int) -> List[str]:
    return [_codigo_asiento_por_indice(i, 10) for i in range(max(1, capacidad_total))]


def _normalizar_codigo_asiento(raw: Optional[str]) -> Optional[str]:
    if not raw:
        return None
    limpio = raw.strip().upper().replace(" ", "")
    m = re.match(r"^([A-Z]{1,2})-?(\d{1,2})$", limpio)
    if not m:
        return None
    return f"{m.group(1)}-{int(m.group(2)):02d}"


def _leer_asientos_reservados_admin(evento: Evento) -> Dict[str, Dict[str, Any]]:
    """Lee el diccionario {codigo_asiento: {etiqueta, reservadoPor, fecha}} de asientos reservados por Admin."""
    crudo = getattr(evento, "asientos_reservados_json", None)
    if not crudo or not isinstance(crudo, str):
        return {}
    try:
        parsed = json.loads(crudo)
        if isinstance(parsed, dict):
            limpio: Dict[str, Dict[str, Any]] = {}
            for k, v in parsed.items():
                cod = _normalizar_codigo_asiento(k)
                if cod:
                    if isinstance(v, dict):
                        limpio[cod] = v
                    else:
                        limpio[cod] = {"etiqueta": str(v or "Reservado Invitado / Maestro")}
            return limpio
    except Exception:
        pass
    return {}


def _guardar_asientos_reservados_admin(db: Session, evento: Evento, mapa: Dict[str, Dict[str, Any]]) -> None:
    serializado = json.dumps(mapa, ensure_ascii=False)
    evento.asientos_reservados_json = serializado
    try:
        db.execute(
            text("UPDATE eventos SET asientos_reservados_json = :val WHERE id = :eid"),
            {"val": serializado, "eid": evento.id},
        )
    except Exception:
        pass
    db.commit()


def _normalizar_nombre_categoria(cat_raw: Optional[str], titulo: str = "", desc: str = "") -> str:
    limpio = (cat_raw or "").strip().lower()
    if "tutor" in limpio:
        return "Tutorías"
    if "conferenc" in limpio or "ponenc" in limpio:
        return "Conferencias"
    if "taller" in limpio or "workshop" in limpio:
        return "Talleres"
    if "deport" in limpio or "torneo" in limpio:
        return "Deporte"
    if "cultur" in limpio or "arte" in limpio:
        return "Culturales"
    if "académ" in limpio or "academ" in limpio:
        return "Académicos"

    texto = f"{titulo} {desc}".lower()
    if "tutor" in texto:
        return "Tutorías"
    if "conferenc" in texto or "ponenc" in texto or "congreso" in texto:
        return "Conferencias"
    if "taller" in texto or "práctic" in texto or "practic" in texto:
        return "Talleres"
    if "deport" in texto or "torneo" in texto or "fútbol" in texto or "futbol" in texto:
        return "Deporte"
    if "cultur" in texto or "música" in texto or "musica" in texto or "danza" in texto:
        return "Culturales"
    return "Académicos"


def _generar_codigo_qr_oficial(evento_id: uuid.UUID) -> str:
    return f"CCUNACH|EVENTO|{evento_id}"


def _obtener_mapa_capacidades_auditorios(db: Session) -> Dict[str, int]:
    """Obtiene el diccionario {nombre_auditorio_lower: asientos} siempre desde catalogos_institucionales."""
    mapa = dict(AUDITORIOS_CAPACIDAD_POR_DEFECTO)
    try:
        row = db.execute(text("SELECT payload_json FROM catalogos_institucionales WHERE id = 1")).fetchone()
        if row and row[0]:
            payload = json.loads(row[0])
            if isinstance(payload, dict) and isinstance(payload.get("auditorios"), list):
                for aud in payload["auditorios"]:
                    if isinstance(aud, dict) and aud.get("nombre"):
                        nombre_key = str(aud["nombre"]).strip().lower()
                        asientos_val = int(aud.get("asientos") or 100)
                        if nombre_key and asientos_val > 0:
                            mapa[nombre_key] = asientos_val
    except Exception:
        pass
    return mapa


def _resolver_capacidad_lugar(lugar: Optional[str], mapa_auditorios: Dict[str, int]) -> int:
    """Cruza el nombre del lugar del evento con el catálogo de auditorios configurado en la web."""
    if not lugar:
        return 100
    limpio = lugar.strip().lower()
    if limpio in mapa_auditorios:
        return mapa_auditorios[limpio]
    for nombre_aud, cap in mapa_auditorios.items():
        if limpio.startswith(nombre_aud) or nombre_aud in limpio:
            return cap
    return 100


def _asegurar_asientos_asignados_en_evento(
    db: Session,
    evento: Evento,
    capacidad: int,
) -> Tuple[List[EventoAsistencia], Dict[str, Dict[str, Any]]]:
    """
    Garantiza que toda inscripción existente tenga un `asiento_codigo` único asignado
    y devuelve (filas_asistencia, mapa_reservados_admin).
    """
    reservados_admin = _leer_asientos_reservados_admin(evento)
    todos_codigos = _catalogo_codigos_asiento_validos(capacidad)
    filas = (
        db.query(EventoAsistencia)
        .filter(EventoAsistencia.evento_id == evento.id)
        .order_by(EventoAsistencia.fecha_registro.asc(), EventoAsistencia.id.asc())
        .all()
    )

    usados: Set[str] = set(reservados_admin.keys())
    hubo_cambio = False

    for f in filas:
        cod = _normalizar_codigo_asiento(getattr(f, "asiento_codigo", None))
        if cod and cod not in usados:
            if getattr(f, "asiento_codigo", None) != cod:
                f.asiento_codigo = cod
                hubo_cambio = True
            usados.add(cod)
        else:
            libre = next((c for c in todos_codigos if c not in usados), None)
            if libre:
                f.asiento_codigo = libre
                usados.add(libre)
                hubo_cambio = True

    if hubo_cambio:
        try:
            db.commit()
        except Exception:
            db.rollback()

    return filas, reservados_admin


class EventoCreateConVigencia(EventoCreate):
    disponible_desde: Optional[datetime] = Field(default=None, alias="disponibleDesde")
    fecha_fin: Optional[datetime] = Field(default=None, alias="fechaFin")


class EventoOutConVigencia(EventoOut):
    disponible_desde: Optional[datetime] = Field(default=None, alias="disponibleDesde", serialization_alias="disponibleDesde")
    fecha_fin: Optional[datetime] = Field(default=None, alias="fechaFin", serialization_alias="fechaFin")
    disponible_ahora: bool = Field(default=True, alias="disponibleAhora", serialization_alias="disponibleAhora")
    inscrito: bool = Field(default=False, alias="inscrito", serialization_alias="inscrito")


def _evento_ha_finalizado(evento: Evento) -> bool:
    fin = getattr(evento, "fecha_fin", None)
    return evento_finalizado(evento.fecha_hora, ahora=ahora_utc(), fecha_fin=fin)


def _evento_esta_disponible_ahora(evento: Evento) -> bool:
    disp = getattr(evento, "disponible_desde", None)
    return evento_disponible(disp, ahora=ahora_utc())


def _verificar_notificaciones_pendientes_en_lista(db: Session, lista_eventos: List[Evento]) -> None:
    ahora = ahora_utc()
    for ev in lista_eventos:
        try:
            ha_fin = _evento_ha_finalizado(ev)
            esta_disp = _evento_esta_disponible_ahora(ev)
            fin_real = asegurar_utc(ev.fecha_fin) if ev.fecha_fin is not None else (
                asegurar_utc(ev.fecha_hora) + timedelta(hours=DURACION_EVENTO_HORAS)
            )

            if esta_disp and not ha_fin and not getattr(ev, "notificacion_aparicion_enviada", False):
                enviar_notificacion_nuevo_evento(db, ev)

            if not ha_fin and fin_real is not None and not getattr(ev, "notificacion_fin_30m_enviada", False):
                restante = fin_real - ahora
                if timedelta(seconds=0) < restante <= timedelta(minutes=30):
                    _enviar_aviso_30m_antes_de_finalizar(db, ev)

            if ha_fin:
                try:
                    pendientes_cierre = (
                        db.query(EventoAsistencia)
                        .filter(
                            EventoAsistencia.evento_id == ev.id,
                            EventoAsistencia.qr_validado == True,  # noqa: E712
                            EventoAsistencia.pings_gps >= 1,
                            EventoAsistencia.asistio == False,  # noqa: E712
                        )
                        .all()
                    )
                    if pendientes_cierre:
                        for reg in pendientes_cierre:
                            reg.gps_validado = True
                            reg.asistio = True
                        db.commit()
                except Exception:
                    db.rollback()

                if not getattr(ev, "notificacion_finalizado_enviada", False):
                    if fin_real is None or (ahora - fin_real) <= timedelta(hours=12):
                        enviar_notificacion_evento_finalizado(db, ev)
                    else:
                        ev.notificacion_finalizado_enviada = True
                        db.commit()
        except Exception as exc:
            print(f"[FCM] Aviso no bloqueante al verificar notificaciones del evento {ev.id}: {exc}")


TIPOS_ARCHIVO = {"material", "evidencia"}
MAX_ARCHIVOS_POR_PETICION = 20


def _ip(request: Request) -> str:
    return request.client.host if request.client else "desconocida"


def _base_url(request: Request) -> str:
    return str(request.base_url).rstrip("/")


def _buscar_evento(db: Session, evento_id: uuid.UUID) -> Evento:
    evento = db.query(Evento).filter(Evento.id == evento_id).first()
    if not evento:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Evento no encontrado.")
    return evento


def _archivo_out(a: EventoArchivo, base: str) -> ArchivoOut:
    return ArchivoOut(
        id=a.id, tipo=a.tipo, nombre=a.nombre_original, mime=a.mime, tamano=a.tamano,
        url=f"{base}/uploads/{a.nombre_guardado}", es_imagen=a.mime.startswith("image/"),
    )


def _serializar(
    evento: Evento,
    base: str,
    conteos: Optional[Dict[uuid.UUID, tuple]] = None,
    estado_usuario_por_evento: Optional[Dict[uuid.UUID, Tuple[bool, bool, bool, Optional[str]]]] = None,
    mapa_auditorios: Optional[Dict[str, int]] = None,
    asientos_alumnos_por_evento: Optional[Dict[uuid.UUID, List[str]]] = None,
) -> EventoOutConVigencia:
    archivos = [_archivo_out(a, base) for a in evento.archivos]
    flyer = next((a.url for a in archivos if a.tipo == "material" and a.es_imagen), None)
    registrados_alumnos, asistieron = conteos.get(evento.id, (0, 0)) if conteos is not None else (0, 0)
    ha_finalizado = _evento_ha_finalizado(evento)

    esta_reg, qr_ok, gps_ok, mi_asiento_cod = (False, False, False, None)
    if estado_usuario_por_evento and evento.id in estado_usuario_por_evento:
        esta_reg, qr_ok, gps_ok, mi_asiento_cod = estado_usuario_por_evento[evento.id]

    pasos = 2 if (qr_ok and gps_ok) else (1 if qr_ok else 0)
    esta_inscrito = bool(not ha_finalizado and esta_reg)

    capacidad = _resolver_capacidad_lugar(
        evento.lugar,
        mapa_auditorios if mapa_auditorios is not None else AUDITORIOS_CAPACIDAD_POR_DEFECTO,
    )

    reservados_admin_map = _leer_asientos_reservados_admin(evento)
    lista_reservados_admin = sorted(reservados_admin_map.keys())
    lista_asientos_alumnos = sorted(
        (asientos_alumnos_por_evento or {}).get(evento.id, [])
    )
    set_todos_ocupados = set(lista_reservados_admin) | set(lista_asientos_alumnos)
    ocupados_totales = max(registrados_alumnos + len(lista_reservados_admin), len(set_todos_ocupados))
    ocupados_totales = min(capacidad, ocupados_totales)

    return EventoOutConVigencia(
        id=evento.id,
        titulo=evento.titulo,
        descripcion=evento.descripcion,
        disponibleDesde=asegurar_utc(evento.disponible_desde),
        fecha_hora=asegurar_utc(evento.fecha_hora),
        fechaFin=asegurar_utc(evento.fecha_fin),
        disponibleAhora=_evento_esta_disponible_ahora(evento),
        inscrito=esta_inscrito,
        lugar=evento.lugar,
        categoria=evento.categoria,
        es_general=evento.es_general,
        id_facultad_destino=evento.id_facultad_destino,
        id_carrera_destino=evento.id_carrera_destino,
        grupos_destino=evento.grupos_destino,
        imagen_url=flyer or evento.imagen_url,
        organizador_email=evento.organizador_email,
        finalizado=ha_finalizado,
        archivos=archivos,
        totalRegistrados=ocupados_totales,
        totalAsistieron=asistieron,
        capacidadTotal=capacidad,
        asientosOcupados=ocupados_totales,
        miAsiento=mi_asiento_cod if not ha_finalizado else None,
        asientosTomados=sorted(set_todos_ocupados),
        asientosReservadosAdmin=lista_reservados_admin,
        qrCodigo=_generar_codigo_qr_oficial(evento.id),
        validacionQr=qr_ok,
        validacionGps=gps_ok,
        pasosValidacion=pasos,
    )


def _conteos_y_asientos(db: Session, eventos_lista: List[Evento], mapa_auditorios: Dict[str, int]) -> Tuple[Dict[uuid.UUID, tuple], Dict[uuid.UUID, List[str]]]:
    ids = [e.id for e in eventos_lista]
    resultado_conteos: Dict[uuid.UUID, List[int]] = {i: [0, 0] for i in ids}
    asientos_por_ev: Dict[uuid.UUID, List[str]] = {i: [] for i in ids}

    for ev in eventos_lista:
        cap = _resolver_capacidad_lugar(ev.lugar, mapa_auditorios)
        filas, _ = _asegurar_asientos_asignados_en_evento(db, ev, cap)
        for f in filas:
            resultado_conteos[ev.id][0] += 1
            if f.asistio:
                resultado_conteos[ev.id][1] += 1
            if f.asiento_codigo:
                asientos_por_ev[ev.id].append(f.asiento_codigo)

    return {k: tuple(v) for k, v in resultado_conteos.items()}, asientos_por_ev


def _puede_gestionar(usuario: Usuario) -> bool:
    nombre_rol = (usuario.rol.nombre if usuario.rol else "").strip().lower()
    if nombre_rol == "administrador":
        return True
    return "eventos:escribir" in {p.clave for p in (usuario.rol.permisos if usuario.rol else [])}


def _aplicar_datos(evento: Evento, datos: EventoCreateConVigencia) -> None:
    evento.titulo = datos.titulo
    evento.descripcion = datos.descripcion
    evento.disponible_desde = asegurar_utc(datos.disponible_desde)
    evento.fecha_hora = asegurar_utc(datos.fecha_hora)
    evento.fecha_fin = asegurar_utc(datos.fecha_fin)
    evento.lugar = datos.lugar
    evento.categoria = datos.categoria
    evento.es_general = datos.es_general
    if datos.es_general:
        evento.id_facultad_destino = None
        evento.id_carrera_destino = None
        evento.grupos_destino = None
    else:
        evento.id_facultad_destino = datos.id_facultad_destino
        evento.id_carrera_destino = datos.id_carrera_destino
        evento.grupos_destino = datos.grupos_destino
    if "imagen_url" in datos.model_fields_set:
        evento.imagen_url = datos.imagen_url


@router.get("/feed", response_model=List[EventoOutConVigencia])
def obtener_feed(
    request: Request,
    incluir_todos: bool = False,
    incluir_programados: bool = False,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    todos_eventos = (
        db.query(Evento).options(selectinload(Evento.archivos))
        .order_by(Evento.fecha_hora.asc()).all()
    )

    _verificar_notificaciones_pendientes_en_lista(db, todos_eventos)

    user_agent = (request.headers.get("user-agent") or "").lower()
    es_app_android = "okhttp" in user_agent
    es_gestor = _puede_gestionar(usuario)

    eventos = todos_eventos
    if es_app_android or not (incluir_todos and es_gestor):
        eventos = [e for e in eventos if not _evento_ha_finalizado(e)]

    if es_app_android or not (incluir_programados and es_gestor):
        eventos = [e for e in eventos if _evento_esta_disponible_ahora(e)]

    if not es_gestor and not getattr(usuario, "es_egresado", False):
        tiene_perfil_segmentado = bool(usuario.id_facultad or usuario.id_carrera or usuario.grupos)
        if tiene_perfil_segmentado:
            eventos = [e for e in eventos if evento_visible_para(e, usuario)]

    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)
    conteos, asientos_por_ev = _conteos_y_asientos(db, eventos, mapa_auditorios)

    ids_vigentes_set = {e.id for e in eventos}
    estado_usuario: Dict[uuid.UUID, Tuple[bool, bool, bool, Optional[str]]] = {}
    for fila in db.query(EventoAsistencia).filter(EventoAsistencia.usuario_id == usuario.id).all():
        if fila.evento_id in ids_vigentes_set:
            qr_ok = bool(getattr(fila, "qr_validado", False) or fila.asistio)
            gps_ok = bool(getattr(fila, "gps_validado", False) or fila.asistio)
            estado_usuario[fila.evento_id] = (True, qr_ok, gps_ok, getattr(fila, "asiento_codigo", None))

    base = _base_url(request)
    return [
        _serializar(
            e,
            base,
            conteos=conteos,
            estado_usuario_por_evento=estado_usuario,
            mapa_auditorios=mapa_auditorios,
            asientos_alumnos_por_evento=asientos_por_ev,
        )
        for e in eventos
    ]


@router.get("/mis-recompensas")
def obtener_mis_recompensas(
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    todos_eventos = db.query(Evento).all()
    _verificar_notificaciones_pendientes_en_lista(db, todos_eventos)

    conteo_por_categoria: Dict[str, int] = {cat: 0 for cat in CATEGORIAS_OFICIALES_RECOMPENSAS}
    eventos_acreditados: List[Dict[str, str]] = []

    filas = (
        db.query(EventoAsistencia, Evento)
        .join(Evento, Evento.id == EventoAsistencia.evento_id)
        .filter(
            EventoAsistencia.usuario_id == usuario.id,
            EventoAsistencia.asistio == True,  # noqa: E712
        )
        .all()
    )

    for asis, ev in filas:
        cat_norm = _normalizar_nombre_categoria(ev.categoria, ev.titulo, ev.descripcion)
        conteo_por_categoria[cat_norm] = conteo_por_categoria.get(cat_norm, 0) + 1
        eventos_acreditados.append({
            "eventoId": str(ev.id),
            "titulo": ev.titulo,
            "categoria": cat_norm,
        })

    return {
        "totalAsistenciasValidadas": sum(conteo_por_categoria.values()),
        "asistenciasPorCategoria": conteo_por_categoria,
        "eventosAcreditados": eventos_acreditados,
    }


@router.get("/{evento_id}/mapa-asientos")
def obtener_mapa_asientos_evento(
    evento_id: uuid.UUID,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Devuelve el mapa de asientos tipo cine en tiempo real para la App Android y el Panel Web:
    - Capacidad exacta tomada del Catálogo de Auditorios para `evento.lugar`.
    - Asientos apartados por alumnos (con datos completos del alumno para administradores al dar clic).
    - Asientos reservados por administración para invitados o maestros.
    """
    evento = _buscar_evento(db, evento_id)
    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)
    capacidad = _resolver_capacidad_lugar(evento.lugar, mapa_auditorios)
    es_gestor = _puede_gestionar(usuario)

    _, reservados_admin = _asegurar_asientos_asignados_en_evento(db, evento, capacidad)
    filas_con_usuario = _filas_asistencia(db, evento.id)

    ocupados_detalle: Dict[str, Dict[str, Any]] = {}
    mi_asiento: Optional[str] = None

    # 1. Asientos reservados para maestros/invitados
    for cod_res, info_res in reservados_admin.items():
        ocupados_detalle[cod_res] = {
            "asiento": cod_res,
            "estado": "reservado_admin",
            "etiqueta": info_res.get("etiqueta") or "Reservado: Maestro / Invitado Especial",
            "reservadoPor": info_res.get("reservadoPor") or "Administración ETDA",
            "fechaApartado": info_res.get("fecha"),
        }

    # 2. Asientos apartados por alumnos
    for asis, usr in filas_con_usuario:
        cod = _normalizar_codigo_asiento(getattr(asis, "asiento_codigo", None))
        if not cod:
            continue
        es_mio = usr.id == usuario.id
        if es_mio:
            mi_asiento = cod

        qr_ok = bool(getattr(asis, "qr_validado", False) or asis.asistio)
        gps_ok = bool(getattr(asis, "gps_validado", False) or asis.asistio)

        item_asiento: Dict[str, Any] = {
            "asiento": cod,
            "estado": "mio" if es_mio else "ocupado_alumno",
            "asistenciaId": asis.id,
            "esMio": es_mio,
            "qrValidado": qr_ok,
            "gpsValidado": gps_ok,
            "asistio": bool(asis.asistio),
            "pasosValidacion": 2 if (qr_ok and gps_ok) else (1 if qr_ok else 0),
        }
        if es_gestor or es_mio:
            item_asiento.update({
                "usuarioId": usr.id,
                "nombre": usr.nombre,
                "email": usr.email,
                "matricula": getattr(usr, "matricula", None) or "",
                "carrera": "Alumno Egresado" if getattr(usr, "es_egresado", False) else (usr.id_carrera or ""),
                "grupos": [] if getattr(usr, "es_egresado", False) else (usr.grupos or []),
                "esEgresado": bool(getattr(usr, "es_egresado", False)),
                "fechaApartado": asis.fecha_registro.isoformat() if asis.fecha_registro else None,
            })
        else:
            # Para otros alumnos, mostrar iniciales para privacidad sin exponer correo
            partes = (usr.nombre or "Alumno").split()
            iniciales = "".join(p[0].upper() for p in partes[:2])
            item_asiento["nombre"] = f"Alumno ({iniciales})"

        ocupados_detalle[cod] = item_asiento

    total_ocupados = min(capacidad, len(ocupados_detalle))
    disponibles = max(0, capacidad - total_ocupados)

    return {
        "eventoId": str(evento.id),
        "titulo": evento.titulo,
        "lugar": evento.lugar,
        "categoria": _normalizar_nombre_categoria(evento.categoria, evento.titulo, evento.descripcion),
        "fechaHora": asegurar_utc(evento.fecha_hora),
        "fechaFin": asegurar_utc(evento.fecha_fin),
        "capacidadTotal": capacidad,
        "asientosOcupados": total_ocupados,
        "asientosDisponibles": disponibles,
        "miAsiento": mi_asiento,
        "asientosTomados": sorted(ocupados_detalle.keys()),
        "asientosReservadosAdmin": sorted(reservados_admin.keys()),
        "detalleAsientos": list(ocupados_detalle.values()),
    }


class GestionarReservaAdminRequest(BaseModel):
    asiento: Optional[str] = None
    asientos: Optional[List[str]] = None
    accion: str = Field(default="reservar", description="'reservar' | 'liberar'")
    etiqueta: Optional[str] = Field(default="Reservado: Maestro / Invitado Especial", max_length=140)


@router.post("/{evento_id}/asientos-reservados", status_code=200)
def gestionar_asientos_reservados_admin(
    request: Request,
    evento_id: uuid.UUID,
    datos: GestionarReservaAdminRequest,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    """
    Permite al administrador en la página web reservar lugares para invitados o maestros,
    o liberar un asiento (tanto reservado como de alumno en caso de conflicto).
    """
    evento = _buscar_evento(db, evento_id)
    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)
    capacidad = _resolver_capacidad_lugar(evento.lugar, mapa_auditorios)
    codigos_validos = set(_catalogo_codigos_asiento_validos(capacidad))

    objetivos_raw: List[str] = []
    if datos.asiento:
        objetivos_raw.append(datos.asiento)
    if datos.asientos:
        objetivos_raw.extend(datos.asientos)

    codigos = [c for c in (_normalizar_codigo_asiento(x) for x in objetivos_raw) if c and c in codigos_validos]
    if not codigos:
        raise HTTPException(status_code=400, detail="Selecciona al menos un código de asiento válido del recinto.")

    filas_alumnos, reservados_admin = _asegurar_asientos_asignados_en_evento(db, evento, capacidad)
    mapa_alumnos_por_asiento = {
        _normalizar_codigo_asiento(f.asiento_codigo): f for f in filas_alumnos if f.asiento_codigo
    }

    accion = (datos.accion or "reservar").strip().lower()
    etiqueta_limpia = (datos.etiqueta or "Reservado: Maestro / Invitado Especial").strip()

    if accion == "liberar":
        for cod in codigos:
            reservados_admin.pop(cod, None)
            if cod in mapa_alumnos_por_asiento:
                db.delete(mapa_alumnos_por_asiento[cod])
        _guardar_asientos_reservados_admin(db, evento, reservados_admin)
        registrar_auditoria(
            db,
            usuario_email=usuario.email,
            ip=_ip(request),
            accion=f"Asiento(s) liberado(s) en '{evento.titulo}': {', '.join(codigos)}",
        )
    else:
        for cod in codigos:
            if cod in mapa_alumnos_por_asiento:
                raise HTTPException(
                    status_code=409,
                    detail=f"El asiento {cod} ya está apartado por un alumno. Libéralo primero si deseas reasignarlo.",
                )
            reservados_admin[cod] = {
                "etiqueta": etiqueta_limpia,
                "reservadoPor": usuario.email,
                "fecha": ahora_utc().isoformat(),
            }
        _guardar_asientos_reservados_admin(db, evento, reservados_admin)
        registrar_auditoria(
            db,
            usuario_email=usuario.email,
            ip=_ip(request),
            accion=f"Asiento(s) reservado(s) para invitados/maestros en '{evento.titulo}': {', '.join(codigos)}",
        )

    return obtener_mapa_asientos_evento(evento_id=evento.id, usuario=usuario, db=db)


@router.get("/pasados", response_model=List[EventoOutConVigencia])
def eventos_pasados(
    request: Request,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir", "estadisticas:leer")),
    db: Session = Depends(get_db),
):
    eventos = (
        db.query(Evento).options(selectinload(Evento.archivos))
        .order_by(Evento.fecha_hora.desc()).all()
    )
    _verificar_notificaciones_pendientes_en_lista(db, eventos)
    eventos = [e for e in eventos if _evento_ha_finalizado(e)]
    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)
    conteos, asientos_por_ev = _conteos_y_asientos(db, eventos, mapa_auditorios)
    base = _base_url(request)
    return [
        _serializar(e, base, conteos=conteos, mapa_auditorios=mapa_auditorios, asientos_alumnos_por_evento=asientos_por_ev)
        for e in eventos
    ]


@router.get("/opciones-segmentacion")
def opciones_segmentacion(
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    facultades, carreras, grupos = set(), set(), set()
    for fac, car, grp in db.query(Usuario.id_facultad, Usuario.id_carrera, Usuario.grupos).all():
        if fac: facultades.add(fac)
        if car: carreras.add(car)
        grupos.update(g for g in (grp or []) if g)
    for fac, car, grp in db.query(
        Evento.id_facultad_destino, Evento.id_carrera_destino, Evento.grupos_destino
    ).all():
        if fac: facultades.add(fac)
        if car: carreras.add(car)
        grupos.update(g for g in (grp or []) if g)
    return {"facultades": sorted(facultades), "carreras": sorted(carreras), "grupos": sorted(grupos)}


@router.post("/", response_model=EventoOutConVigencia, status_code=201)
def crear_evento(
    request: Request,
    evento_in: EventoCreateConVigencia,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    nuevo = Evento(
        organizador_email=usuario.email,
        asientos_reservados_json="{}",
        notificacion_aparicion_enviada=False,
        notificacion_fin_30m_enviada=False,
        notificacion_finalizado_enviada=False,
    )
    _aplicar_datos(nuevo, evento_in)
    db.add(nuevo)
    db.commit()
    db.refresh(nuevo)

    registrar_auditoria(db, usuario_email=usuario.email, ip=_ip(request),
                        accion=f"Evento creado: {nuevo.titulo}")

    if _evento_esta_disponible_ahora(nuevo) and not _evento_ha_finalizado(nuevo):
        try:
            enviar_notificacion_nuevo_evento(db, nuevo)
        except Exception as exc:
            print(f"[FCM] Error no bloqueante al enviar push de nuevo evento: {exc}")

    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)
    return _serializar(nuevo, _base_url(request), conteos={nuevo.id: (0, 0)}, mapa_auditorios=mapa_auditorios)


@router.put("/{evento_id}", response_model=EventoOutConVigencia)
def editar_evento(
    request: Request,
    evento_id: uuid.UUID,
    evento_in: EventoCreateConVigencia,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    evento = _buscar_evento(db, evento_id)
    disp_anterior = asegurar_utc(evento.disponible_desde)
    fin_anterior = asegurar_utc(evento.fecha_fin)

    _aplicar_datos(evento, evento_in)

    if asegurar_utc(evento.disponible_desde) != disp_anterior:
        evento.notificacion_aparicion_enviada = False

    if asegurar_utc(evento.fecha_fin) != fin_anterior:
        evento.notificacion_fin_30m_enviada = False
        evento.notificacion_finalizado_enviada = False

    db.commit()
    db.refresh(evento)

    if (
        _evento_esta_disponible_ahora(evento)
        and not _evento_ha_finalizado(evento)
        and not getattr(evento, "notificacion_aparicion_enviada", False)
    ):
        try:
            enviar_notificacion_nuevo_evento(db, evento)
        except Exception as exc:
            print(f"[FCM] Error no bloqueante al enviar push en edición: {exc}")

    registrar_auditoria(db, usuario_email=usuario.email, ip=_ip(request),
                        accion=f"Evento editado: {evento.titulo}")
    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)
    conteos, asientos_por_ev = _conteos_y_asientos(db, [evento], mapa_auditorios)
    return _serializar(
        evento,
        _base_url(request),
        conteos=conteos,
        mapa_auditorios=mapa_auditorios,
        asientos_alumnos_por_evento=asientos_por_ev,
    )


@router.delete("/{evento_id}", status_code=200)
def eliminar_evento(
    request: Request,
    evento_id: uuid.UUID,
    usuario: Usuario = Depends(requiere_permiso("eventos:eliminar")),
    db: Session = Depends(get_db),
):
    evento = _buscar_evento(db, evento_id)
    titulo = evento.titulo
    nombres = [a.nombre_guardado for a in evento.archivos]
    db.delete(evento)
    db.commit()
    for nombre in nombres:
        borrar_archivo(nombre)
    registrar_auditoria(db, usuario_email=usuario.email, ip=_ip(request),
                        accion=f"Evento eliminado: {titulo}")
    return {"mensaje": "Evento eliminado correctamente."}


@router.post("/probar-recordatorios", status_code=200)
def disparar_recordatorios_manual(
    usuario: Usuario = Depends(verificar_rol_admin),
):
    resultado = verificar_y_enviar_recordatorios_24h()
    return {"mensaje": "Verificación de notificaciones ejecutada", "resultado": resultado}


@router.post("/{evento_id}/archivos", response_model=List[ArchivoOut], status_code=201)
def subir_archivos(
    request: Request,
    evento_id: uuid.UUID,
    tipo: str = Form("material"),
    archivos: List[UploadFile] = File(...),
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    if tipo not in TIPOS_ARCHIVO:
        raise HTTPException(status_code=400, detail="Tipo de archivo inválido.")
    if not archivos or len(archivos) > MAX_ARCHIVOS_POR_PETICION:
        raise HTTPException(status_code=400, detail=f"Envía entre 1 y {MAX_ARCHIVOS_POR_PETICION} archivos.")

    evento = _buscar_evento(db, evento_id)
    if tipo == "evidencia" and not _evento_ha_finalizado(evento):
        raise HTTPException(status_code=400, detail="Solo se pueden subir evidencias de eventos finalizados.")

    guardados: List[EventoArchivo] = []
    try:
        for up in archivos:
            nombre, mime, tamano = guardar_archivo(up.file)
            fila = EventoArchivo(
                evento_id=evento.id, tipo=tipo, nombre_original=limpiar_nombre_original(up.filename),
                nombre_guardado=nombre, mime=mime, tamano=tamano, subido_por=usuario.email,
            )
            db.add(fila)
            guardados.append(fila)
        db.commit()
    except ArchivoInvalido as exc:
        db.rollback()
        for fila in guardados:
            borrar_archivo(fila.nombre_guardado)
        raise HTTPException(status_code=400, detail=f"{up.filename or 'Archivo'}: {exc}")
    except Exception:
        db.rollback()
        for fila in guardados:
            borrar_archivo(fila.nombre_guardado)
        raise

    for fila in guardados:
        db.refresh(fila)
    registrar_auditoria(db, usuario_email=usuario.email, ip=_ip(request),
                        accion=f"{len(guardados)} archivo(s) de {tipo} subidos al evento: {evento.titulo}")
    base = _base_url(request)
    return [_archivo_out(a, base) for a in guardados]


@router.delete("/{evento_id}/archivos/{archivo_id}")
def eliminar_archivo(
    request: Request,
    evento_id: uuid.UUID,
    archivo_id: uuid.UUID,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    archivo = (
        db.query(EventoArchivo)
        .filter(EventoArchivo.id == archivo_id, EventoArchivo.evento_id == evento_id)
        .first()
    )
    if not archivo:
        raise HTTPException(status_code=404, detail="Archivo no encontrado.")
    nombre, tipo = archivo.nombre_guardado, archivo.tipo
    db.delete(archivo)
    db.commit()
    borrar_archivo(nombre)
    registrar_auditoria(db, usuario_email=usuario.email, ip=_ip(request),
                        accion=f"Archivo de {tipo} eliminado del evento {evento_id}")
    return {"mensaje": "Archivo eliminado."}



SEDES_UNACH_COORDS = [
    (16.7542, -93.1538),  # ETDA / Edificio Principal Campus I
    (16.7529, -93.1526),  # Auditorio Los Constituyentes / Biblioteca Central Campus I
    (16.7551, -93.1549),  # Zona Norte Campus I UNACH
]
RADIO_PERMITIDO_METROS = 280.0


def _distancia_haversine_metros(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371000.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2.0) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2.0) ** 2
    return 2.0 * r * math.atan2(math.sqrt(a), math.sqrt(max(0.0, 1.0 - a)))


def _esta_dentro_del_recinto_unach(lat: Optional[float], lon: Optional[float]) -> Tuple[bool, float]:
    if lat is None or lon is None:
        return False, 999999.0
    distancias = [_distancia_haversine_metros(lat, lon, slat, slon) for slat, slon in SEDES_UNACH_COORDS]
    menor = min(distancias) if distancias else 999999.0
    return (menor <= RADIO_PERMITIDO_METROS), menor


class ApartarAsientoRequest(BaseModel):
    asiento: Optional[str] = None
    codigo_qr: Optional[str] = None
    latitud: Optional[float] = None
    longitud: Optional[float] = None
    asistio: Optional[bool] = None


class PingGpsEventoRequest(BaseModel):
    latitud: float = Field(..., ge=-90.0, le=90.0)
    longitud: float = Field(..., ge=-180.0, le=180.0)
    finalizar: bool = False


@router.post("/{evento_id}/registro", status_code=200)
def apartar_asiento_e_inscribir(
    evento_id: uuid.UUID,
    datos: Optional[ApartarAsientoRequest] = None,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Aparta el asiento individual elegido por el alumno en la interfaz tipo cine (máximo 1 asiento por alumno).
    El asiento queda bloqueado y sincronizado en tiempo real con la página web de administración.
    """
    evento = _buscar_evento(db, evento_id)
    if not _evento_esta_disponible_ahora(evento) and not _puede_gestionar(usuario):
        raise HTTPException(status_code=400, detail="Este evento aún no está abierto para inscripciones.")
    if _evento_ha_finalizado(evento):
        raise HTTPException(status_code=400, detail="Este evento ya finalizó.")

    tiene_perfil_segmentado = bool(usuario.id_facultad or usuario.id_carrera or usuario.grupos)
    if (
        not _puede_gestionar(usuario)
        and not getattr(usuario, "es_egresado", False)
        and tiene_perfil_segmentado
        and not evento_visible_para(evento, usuario)
    ):
        raise HTTPException(status_code=403, detail="Este evento no está dirigido a tu carrera o grupo.")

    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)
    capacidad_maxima = _resolver_capacidad_lugar(evento.lugar, mapa_auditorios)
    codigos_validos = _catalogo_codigos_asiento_validos(capacidad_maxima)

    filas_actuales, reservados_admin = _asegurar_asientos_asignados_en_evento(db, evento, capacidad_maxima)
    ya = next((f for f in filas_actuales if f.usuario_id == usuario.id), None)

    # Si ya está inscrito, permitir actualizar validación QR/GPS o asignar asiento si no tenía
    if ya is not None:
        hubo_mod = False
        if datos and datos.asiento:
            cod_sol = _normalizar_codigo_asiento(datos.asiento)
            ocupados_otros = (set(reservados_admin.keys()) | {
                _normalizar_codigo_asiento(f.asiento_codigo) for f in filas_actuales if f.id != ya.id and f.asiento_codigo
            })
            if cod_sol and cod_sol in codigos_validos and cod_sol not in ocupados_otros and ya.asiento_codigo != cod_sol:
                ya.asiento_codigo = cod_sol
                hubo_mod = True
        if datos and datos.codigo_qr and str(evento.id).lower() in datos.codigo_qr.lower():
            ya.qr_validado = True
            hubo_mod = True
        if datos and datos.asistio is True and datos.latitud is not None and datos.longitud is not None:
            dentro, _ = _esta_dentro_del_recinto_unach(datos.latitud, datos.longitud)
            if dentro:
                ya.qr_validado = True
                ya.gps_validado = True
                ya.asistio = True
                hubo_mod = True
        if hubo_mod:
            db.commit()
            db.refresh(ya)

        ocupados_totales = min(capacidad_maxima, len(filas_actuales) + len(reservados_admin))
        disponibles = max(0, capacidad_maxima - ocupados_totales)
        qr_ok = bool(getattr(ya, "qr_validado", False) or ya.asistio)
        gps_ok = bool(getattr(ya, "gps_validado", False) or ya.asistio)
        return {
            "mensaje": f"Asiento {ya.asiento_codigo} confirmado.",
            "inscrito": True,
            "miAsiento": ya.asiento_codigo,
            "validacionQr": qr_ok,
            "validacionGps": gps_ok,
            "asistio": bool(ya.asistio),
            "pasosValidacion": 2 if (qr_ok and gps_ok) else (1 if qr_ok else 0),
            "capacidadTotal": capacidad_maxima,
            "asientosOcupados": ocupados_totales,
            "asientosDisponibles": disponibles,
            "totalRegistrados": ocupados_totales,
        }

    asientos_ocupados_set = set(reservados_admin.keys()) | {
        _normalizar_codigo_asiento(f.asiento_codigo) for f in filas_actuales if f.asiento_codigo
    }

    if len(asientos_ocupados_set) >= capacidad_maxima:
        raise HTTPException(
            status_code=400,
            detail=f"El auditorio está lleno ({capacidad_maxima}/{capacidad_maxima} asientos ocupados). Ya no es posible inscribirse.",
        )

    asiento_solicitado = _normalizar_codigo_asiento(datos.asiento if datos else None)
    if asiento_solicitado:
        if asiento_solicitado not in codigos_validos:
            raise HTTPException(status_code=400, detail=f"El asiento {asiento_solicitado} no existe en este recinto.")
        if asiento_solicitado in reservados_admin:
            raise HTTPException(
                status_code=409,
                detail=f"El asiento {asiento_solicitado} está reservado para invitados o maestros. Elige otro asiento.",
            )
        if asiento_solicitado in asientos_ocupados_set:
            raise HTTPException(
                status_code=409,
                detail=f"El asiento {asiento_solicitado} ya fue apartado por otro alumno. Elige otro lugar disponible.",
            )
        asiento_final = asiento_solicitado
    else:
        asiento_final = next((c for c in codigos_validos if c not in asientos_ocupados_set), None)
        if not asiento_final:
            raise HTTPException(status_code=400, detail="No quedan asientos disponibles en este recinto.")

    nueva_fila = EventoAsistencia(
        evento_id=evento.id,
        usuario_id=usuario.id,
        asiento_codigo=asiento_final,
        qr_validado=False,
        gps_validado=False,
        pings_gps=0,
        asistio=False,
    )
    db.add(nueva_fila)
    db.commit()
    db.refresh(nueva_fila)

    ahora = ahora_utc()
    fin_real = asegurar_utc(evento.fecha_fin) if evento.fecha_fin is not None else (
        asegurar_utc(evento.fecha_hora) + timedelta(hours=DURACION_EVENTO_HORAS)
    )
    if fin_real is not None and timedelta(seconds=0) < (fin_real - ahora) <= timedelta(minutes=30):
        try:
            _enviar_aviso_30m_antes_de_finalizar(db, evento)
        except Exception as exc:
            print(f"[FCM] Aviso no bloqueante al enviar push de 30m en inscripción: {exc}")

    ocupados_totales = min(capacidad_maxima, len(asientos_ocupados_set) + 1)
    disponibles = max(0, capacidad_maxima - ocupados_totales)
    return {
        "mensaje": f"¡Asiento {asiento_final} apartado con éxito! El día del evento escanea el QR para validar tu llegada.",
        "inscrito": True,
        "miAsiento": asiento_final,
        "validacionQr": False,
        "validacionGps": False,
        "pasosValidacion": 0,
        "capacidadTotal": capacidad_maxima,
        "asientosOcupados": ocupados_totales,
        "asientosDisponibles": disponibles,
        "totalRegistrados": ocupados_totales,
    }


@router.post("/{evento_id}/validar-qr", status_code=200)
def validar_qr_llegada_evento(
    evento_id: uuid.UUID,
    datos_qr: Optional[ApartarAsientoRequest] = None,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Validación 1 de 2 al llegar a la conferencia (Escaneo del QR oficial generado por la página web):
    - Verifica que el código QR corresponda al evento.
    - Marca `qr_validado = True` (Validación 1/2 lista) e inicia la validación 2/2 por GPS.
    """
    evento = _buscar_evento(db, evento_id)
    if _evento_ha_finalizado(evento):
        raise HTTPException(status_code=400, detail="Este evento ya finalizó.")

    if datos_qr and datos_qr.codigo_qr:
        crudo = datos_qr.codigo_qr.strip()
        if str(evento.id).lower() not in crudo.lower():
            raise HTTPException(
                status_code=400,
                detail="El código QR escaneado no corresponde a este evento oficial.",
            )

    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)
    capacidad_maxima = _resolver_capacidad_lugar(evento.lugar, mapa_auditorios)
    filas_actuales, reservados_admin = _asegurar_asientos_asignados_en_evento(db, evento, capacidad_maxima)

    fila = next((f for f in filas_actuales if f.usuario_id == usuario.id), None)
    ahora = ahora_utc()

    if fila is None:
        asientos_ocupados_set = set(reservados_admin.keys()) | {
            _normalizar_codigo_asiento(f.asiento_codigo) for f in filas_actuales if f.asiento_codigo
        }
        if len(asientos_ocupados_set) >= capacidad_maxima:
            raise HTTPException(status_code=400, detail="El auditorio ya tiene todos sus asientos ocupados.")
        codigos_validos = _catalogo_codigos_asiento_validos(capacidad_maxima)
        asiento_auto = next((c for c in codigos_validos if c not in asientos_ocupados_set), "A-01")
        fila = EventoAsistencia(
            evento_id=evento.id,
            usuario_id=usuario.id,
            asiento_codigo=asiento_auto,
            qr_validado=True,
            gps_validado=False,
            pings_gps=1 if (datos_qr and datos_qr.latitud is not None) else 0,
            ultimo_ping_gps=ahora if (datos_qr and datos_qr.latitud is not None) else None,
            asistio=False,
        )
        db.add(fila)
    else:
        fila.qr_validado = True
        if datos_qr and datos_qr.latitud is not None:
            fila.pings_gps = (getattr(fila, "pings_gps", 0) or 0) + 1
            fila.ultimo_ping_gps = ahora

    db.commit()
    db.refresh(fila)

    gps_ok = bool(getattr(fila, "gps_validado", False) or fila.asistio)
    return {
        "mensaje": f"Validación 1/2 completada (Asiento {fila.asiento_codigo}). Mantén tu GPS activo para la Validación 2/2.",
        "inscrito": True,
        "miAsiento": fila.asiento_codigo,
        "validacionQr": True,
        "validacionGps": gps_ok,
        "pasosValidacion": 2 if gps_ok else 1,
        "asistio": bool(fila.asistio),
        "categoria": _normalizar_nombre_categoria(evento.categoria, evento.titulo, evento.descripcion),
    }


@router.post("/{evento_id}/ping-gps", status_code=200)
def registrar_ping_gps_permanencia(
    evento_id: uuid.UUID,
    datos: PingGpsEventoRequest,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Validación 2 de 2 (Permanencia GPS durante la conferencia / al cierre):
    - Al acumular el segundo ping de permanencia (o al cierre del evento estando en el recinto),
      marca `gps_validado = True` y `asistio = True`, acreditando automáticamente +1 en la categoría.
    """
    evento = _buscar_evento(db, evento_id)
    fila = db.query(EventoAsistencia).filter_by(evento_id=evento.id, usuario_id=usuario.id).first()
    if not fila:
        raise HTTPException(
            status_code=400,
            detail="Primero debes apartar tu asiento y escanear el código QR oficial del evento.",
        )

    dentro_recinto, distancia_m = _esta_dentro_del_recinto_unach(datos.latitud, datos.longitud)
    cat_norm = _normalizar_nombre_categoria(evento.categoria, evento.titulo, evento.descripcion)

    if not dentro_recinto:
        return {
            "mensaje": f"Te encuentras fuera del recinto UNACH ({int(round(distancia_m))} m). Acércate a la sede para validar tu permanencia GPS.",
            "eventoId": str(evento.id),
            "miAsiento": fila.asiento_codigo,
            "validacionQr": bool(getattr(fila, "qr_validado", False)),
            "validacionGps": False,
            "asistio": False,
            "dentroDelRecinto": False,
            "distanciaMetros": round(distancia_m, 1),
            "pasosValidacion": 1 if getattr(fila, "qr_validado", False) else 0,
            "pingsGps": int(getattr(fila, "pings_gps", 0) or 0),
            "categoria": cat_norm,
        }

    ahora = ahora_utc()
    pings_previos = int(getattr(fila, "pings_gps", 0) or 0)
    fila.qr_validado = True
    fila.pings_gps = pings_previos + 1
    fila.ultimo_ping_gps = ahora

    fin_real = asegurar_utc(evento.fecha_fin) if evento.fecha_fin is not None else (
        asegurar_utc(evento.fecha_hora) + timedelta(hours=DURACION_EVENTO_HORAS)
    )
    cerca_del_cierre = fin_real is not None and (fin_real - ahora) <= timedelta(minutes=15)

    if fila.pings_gps >= 2 or cerca_del_cierre or datos.finalizar:
        fila.gps_validado = True
        fila.asistio = True

    db.commit()
    db.refresh(fila)

    cat_norm = _normalizar_nombre_categoria(evento.categoria, evento.titulo, evento.descripcion)
    completado = bool(fila.asistio)

    return {
        "mensaje": (
            f"¡Validación 2/2 completada! Permanencia GPS verificada (+1 acreditado en {cat_norm})."
            if completado
            else "Ping GPS de permanencia registrado (1/2 QR listo • Verificando permanencia GPS)."
        ),
        "eventoId": str(evento.id),
        "miAsiento": fila.asiento_codigo,
        "validacionQr": True,
        "validacionGps": completado,
        "asistio": completado,
        "pasosValidacion": 2 if completado else 1,
        "pingsGps": fila.pings_gps,
        "categoria": cat_norm,
    }


@router.delete("/{evento_id}/registro", status_code=200)
def cancelar_asistencia(
    evento_id: uuid.UUID,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    evento = _buscar_evento(db, evento_id)
    fila = db.query(EventoAsistencia).filter_by(evento_id=evento_id, usuario_id=usuario.id).first()
    if fila:
        db.delete(fila)
        db.commit()

    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)
    capacidad_maxima = _resolver_capacidad_lugar(evento.lugar, mapa_auditorios)
    reservados_admin = _leer_asientos_reservados_admin(evento)
    ocupados_alumnos = db.query(EventoAsistencia).filter_by(evento_id=evento_id).count()
    ocupados_totales = min(capacidad_maxima, ocupados_alumnos + len(reservados_admin))
    disponibles = max(0, capacidad_maxima - ocupados_totales)

    return {
        "mensaje": "Inscripción cancelada y asiento liberado correctamente.",
        "inscrito": False,
        "miAsiento": None,
        "validacionQr": False,
        "validacionGps": False,
        "pasosValidacion": 0,
        "capacidadTotal": capacidad_maxima,
        "asientosOcupados": ocupados_totales,
        "asientosDisponibles": disponibles,
        "totalRegistrados": ocupados_totales,
    }


def _filas_asistencia(db: Session, evento_id: uuid.UUID):
    return (
        db.query(EventoAsistencia, Usuario)
        .join(Usuario, Usuario.id == EventoAsistencia.usuario_id)
        .filter(EventoAsistencia.evento_id == evento_id)
        .order_by(Usuario.nombre.asc())
        .all()
    )


@router.get("/{evento_id}/asistentes")
def obtener_asistentes(
    evento_id: uuid.UUID,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    evento = _buscar_evento(db, evento_id)
    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)
    cap = _resolver_capacidad_lugar(evento.lugar, mapa_auditorios)
    _asegurar_asientos_asignados_en_evento(db, evento, cap)

    return [
        {
            "id": a.id,
            "nombre": u.nombre,
            "email": u.email,
            "matricula": getattr(u, "matricula", None) or "",
            "facultad": u.id_facultad,
            "carrera": "Alumno Egresado" if getattr(u, "es_egresado", False) else u.id_carrera,
            "grupos": [] if getattr(u, "es_egresado", False) else (u.grupos or []),
            "asiento": getattr(a, "asiento_codigo", None) or "—",
            "fecha_registro": a.fecha_registro.isoformat() if a.fecha_registro else None,
            "qr_validado": bool(getattr(a, "qr_validado", False) or a.asistio),
            "gps_validado": bool(getattr(a, "gps_validado", False) or a.asistio),
            "asistio": a.asistio,
        }
        for a, u in _filas_asistencia(db, evento_id)
    ]


class MarcarAsistenciaRequest(BaseModel):
    asistio: bool


@router.patch("/{evento_id}/asistentes/{asistencia_id}")
def marcar_asistencia(
    evento_id: uuid.UUID,
    asistencia_id: int,
    datos: MarcarAsistenciaRequest,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    fila = (
        db.query(EventoAsistencia)
        .filter(EventoAsistencia.id == asistencia_id, EventoAsistencia.evento_id == evento_id)
        .first()
    )
    if not fila:
        raise HTTPException(status_code=404, detail="Registro de asistencia no encontrado.")
    fila.asistio = datos.asistio
    if datos.asistio:
        fila.qr_validado = True
        fila.gps_validado = True
    db.commit()
    return {"id": fila.id, "asistio": fila.asistio, "asiento": getattr(fila, "asiento_codigo", None)}


def _slug(texto: str) -> str:
    ascii_ = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-zA-Z0-9]+", "-", ascii_).strip("-").lower()[:50] or "evento"


@router.get("/{evento_id}/asistentes/pdf")
def descargar_asistencia_pdf(
    request: Request,
    evento_id: uuid.UUID,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    evento = _buscar_evento(db, evento_id)
    filas = [
        {
            "nombre": u.nombre,
            "email": u.email,
            "carrera": "Egresado" if getattr(u, "es_egresado", False) else u.id_carrera,
            "grupos": "—" if getattr(u, "es_egresado", False) else ", ".join(u.grupos or []),
            "asiento": getattr(a, "asiento_codigo", None) or "—",
            "registro": fecha_corta(a.fecha_registro),
            "asistio": a.asistio,
        }
        for a, u in _filas_asistencia(db, evento_id)
    ]
    pdf = generar_pdf_asistencia(
        {
            "titulo": evento.titulo, "fecha": fecha_larga(evento.fecha_hora), "lugar": evento.lugar,
            "audiencia": audiencia_texto(evento.es_general, evento.id_facultad_destino,
                                         evento.id_carrera_destino, evento.grupos_destino),
        },
        filas, usuario.email,
    )
    registrar_auditoria(db, usuario_email=usuario.email, ip=_ip(request),
                        accion=f"Lista de asistencia descargada (PDF): {evento.titulo}")
    return Response(
        content=pdf, media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="asistencia-{_slug(evento.titulo)}.pdf"'},
    )