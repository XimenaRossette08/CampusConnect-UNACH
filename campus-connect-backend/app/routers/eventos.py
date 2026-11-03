import json
import re
import unicodedata
import uuid
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Set

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


def _obtener_mapa_capacidades_auditorios(db: Session) -> Dict[str, int]:
    """Obtiene el diccionario {nombre_auditorio_lower: asientos} desde catalogos_institucionales."""
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
    """Cruza el nombre del lugar del evento con el catálogo de auditorios configurado."""
    if not lugar:
        return 100
    limpio = lugar.strip().lower()
    if limpio in mapa_auditorios:
        return mapa_auditorios[limpio]
    for nombre_aud, cap in mapa_auditorios.items():
        if limpio.startswith(nombre_aud) or nombre_aud in limpio:
            return cap
    return 100


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
    """
    Dispara oportunamente las notificaciones de aparición, 30 min antes del cierre
    y de evento finalizado cuando se consulta el feed o eventos pasados, sin esperar al siguiente ciclo.
    """
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

            if ha_fin and not getattr(ev, "notificacion_finalizado_enviada", False):
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
    eventos_inscritos_usuario: Optional[Set[uuid.UUID]] = None,
    mapa_auditorios: Optional[Dict[str, int]] = None,
) -> EventoOutConVigencia:
    archivos = [_archivo_out(a, base) for a in evento.archivos]
    flyer = next((a.url for a in archivos if a.tipo == "material" and a.es_imagen), None)
    registrados, asistieron = conteos.get(evento.id, (0, 0)) if conteos is not None else (0, 0)
    ha_finalizado = _evento_ha_finalizado(evento)

    # Si el evento ya finalizó, jamás se marca como inscrito activo para evitar eventos fantasma en "Mis Inscritos"
    esta_inscrito = bool(
        not ha_finalizado
        and eventos_inscritos_usuario
        and evento.id in eventos_inscritos_usuario
    )

    capacidad = _resolver_capacidad_lugar(
        evento.lugar,
        mapa_auditorios if mapa_auditorios is not None else AUDITORIOS_CAPACIDAD_POR_DEFECTO,
    )

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
        totalRegistrados=registrados,
        totalAsistieron=asistieron,
        capacidadTotal=capacidad,
        asientosOcupados=registrados,
    )


def _conteos(db: Session, ids: List[uuid.UUID]) -> Dict[uuid.UUID, tuple]:
    resultado: Dict[uuid.UUID, List[int]] = {i: [0, 0] for i in ids}
    if ids:
        filas = db.query(EventoAsistencia.evento_id, EventoAsistencia.asistio).filter(
            EventoAsistencia.evento_id.in_(ids)
        ).all()
        for evento_id, asistio in filas:
            resultado[evento_id][0] += 1
            resultado[evento_id][1] += 1 if asistio else 0
    return {k: tuple(v) for k, v in resultado.items()}


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

    # Verificar y enviar de inmediato notificaciones pendientes (incluyendo eventos que acaban de finalizar)
    _verificar_notificaciones_pendientes_en_lista(db, todos_eventos)

    user_agent = (request.headers.get("user-agent") or "").lower()
    es_app_android = "okhttp" in user_agent
    es_gestor = _puede_gestionar(usuario)

    eventos = todos_eventos
    if es_app_android or not (incluir_todos and es_gestor):
        eventos = [e for e in eventos if not _evento_ha_finalizado(e)]

    if es_app_android or not (incluir_programados and es_gestor):
        eventos = [e for e in eventos if _evento_esta_disponible_ahora(e)]

    # Los egresados ven todos los eventos sin bloqueo por carrera/grupo
    if not es_gestor and not getattr(usuario, "es_egresado", False):
        tiene_perfil_segmentado = bool(usuario.id_facultad or usuario.id_carrera or usuario.grupos)
        if tiene_perfil_segmentado:
            eventos = [e for e in eventos if evento_visible_para(e, usuario)]

    ids_eventos_visibles = [e.id for e in eventos]
    conteos = _conteos(db, ids_eventos_visibles)
    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)

    # Obtener únicamente los IDs de eventos vigentes en los que el usuario está inscrito
    ids_vigentes_set = set(ids_eventos_visibles)
    ids_inscritos_vigentes: Set[uuid.UUID] = {
        fila[0]
        for fila in db.query(EventoAsistencia.evento_id)
        .filter(EventoAsistencia.usuario_id == usuario.id)
        .all()
        if fila[0] in ids_vigentes_set
    }

    base = _base_url(request)
    return [
        _serializar(
            e,
            base,
            conteos=conteos,
            eventos_inscritos_usuario=ids_inscritos_vigentes,
            mapa_auditorios=mapa_auditorios,
        )
        for e in eventos
    ]


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
    conteos = _conteos(db, [e.id for e in eventos])
    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)
    base = _base_url(request)
    return [_serializar(e, base, conteos=conteos, mapa_auditorios=mapa_auditorios) for e in eventos]


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
    conteos = _conteos(db, [evento.id])
    mapa_auditorios = _obtener_mapa_capacidades_auditorios(db)
    return _serializar(evento, _base_url(request), conteos=conteos, mapa_auditorios=mapa_auditorios)


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


@router.post("/{evento_id}/registro", status_code=200)
def registrar_asistencia(
    evento_id: uuid.UUID,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
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

    ya = db.query(EventoAsistencia).filter_by(evento_id=evento.id, usuario_id=usuario.id).first()
    ocupados_actuales = db.query(EventoAsistencia).filter_by(evento_id=evento.id).count()

    if not ya:
        if ocupados_actuales >= capacidad_maxima:
            raise HTTPException(
                status_code=400,
                detail=f"El cupo para este evento está agotado ({capacidad_maxima}/{capacidad_maxima} asientos ocupados).",
            )
        db.add(EventoAsistencia(evento_id=evento.id, usuario_id=usuario.id))
        db.commit()
        ocupados_actuales += 1

    # Si al momento de inscribirse ya quedan 30 minutos o menos para finalizar el evento,
    # disparar de inmediato el push de últimos 30 minutos para este alumno
    ahora = ahora_utc()
    fin_real = asegurar_utc(evento.fecha_fin) if evento.fecha_fin is not None else (
        asegurar_utc(evento.fecha_hora) + timedelta(hours=DURACION_EVENTO_HORAS)
    )
    if fin_real is not None and timedelta(seconds=0) < (fin_real - ahora) <= timedelta(minutes=30):
        try:
            _enviar_aviso_30m_antes_de_finalizar(db, evento)
        except Exception as exc:
            print(f"[FCM] Aviso no bloqueante al enviar push de 30m en inscripción: {exc}")

    disponibles = max(0, capacidad_maxima - ocupados_actuales)
    return {
        "mensaje": "Inscripción realizada correctamente.",
        "inscrito": True,
        "capacidadTotal": capacidad_maxima,
        "asientosOcupados": ocupados_actuales,
        "asientosDisponibles": disponibles,
        "totalRegistrados": ocupados_actuales,
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
    ocupados_actuales = db.query(EventoAsistencia).filter_by(evento_id=evento_id).count()
    disponibles = max(0, capacidad_maxima - ocupados_actuales)

    return {
        "mensaje": "Inscripción cancelada correctamente.",
        "inscrito": False,
        "capacidadTotal": capacidad_maxima,
        "asientosOcupados": ocupados_actuales,
        "asientosDisponibles": disponibles,
        "totalRegistrados": ocupados_actuales,
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
    _buscar_evento(db, evento_id)
    return [
        {
            "id": a.id,
            "nombre": u.nombre,
            "email": u.email,
            "facultad": u.id_facultad,
            "carrera": "Alumno Egresado" if getattr(u, "es_egresado", False) else u.id_carrera,
            "grupos": [] if getattr(u, "es_egresado", False) else (u.grupos or []),
            "fecha_registro": a.fecha_registro.isoformat() if a.fecha_registro else None,
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
    db.commit()
    return {"id": fila.id, "asistio": fila.asistio}


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