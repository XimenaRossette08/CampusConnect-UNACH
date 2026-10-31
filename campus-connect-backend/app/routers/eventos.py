import re
import unicodedata
import uuid
from datetime import datetime, timezone
from typing import Dict, List, Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, Response, UploadFile, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session, selectinload

from ..auditoria import registrar_auditoria
from ..database import get_db
from ..fechas import ahora_utc, evento_finalizado, fecha_corta, fecha_larga
from ..models import Evento, EventoArchivo, EventoAsistencia
from ..models_auth import Usuario
from ..notifications import enviar_notificacion_nuevo_evento, verificar_y_enviar_recordatorios_24h
from ..pdf_asistencia import generar_pdf_asistencia
from ..publicaciones import audiencia_texto
from ..schemas import ArchivoOut, EventoCreate, EventoOut
from ..security import get_current_user, requiere_permiso, verificar_rol_admin
from ..segmentacion import evento_visible_para
from ..storage import ArchivoInvalido, borrar_archivo, guardar_archivo, limpiar_nombre_original

router = APIRouter(prefix="/eventos", tags=["eventos"])

class EventoCreateConVigencia(EventoCreate):
    disponible_desde: Optional[datetime] = Field(default=None, alias="disponibleDesde")
    fecha_fin: Optional[datetime] = Field(default=None, alias="fechaFin")


class EventoOutConVigencia(EventoOut):
    disponible_desde: Optional[datetime] = Field(default=None, serialization_alias="disponibleDesde")
    fecha_fin: Optional[datetime] = Field(default=None, serialization_alias="fechaFin")


def _asegurar_utc(dt: Optional[datetime]) -> Optional[datetime]:
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _evento_ha_finalizado(evento: Evento) -> bool:
    """
    Un evento se considera finalizado/expirado si:
    - Tiene fecha_fin configurada y la hora actual ya alcanzó o superó fecha_fin.
    - O si no tiene fecha_fin y la hora actual ya alcanzó o superó fecha_hora.
    """
    limite = getattr(evento, "fecha_fin", None) or evento.fecha_hora
    return evento_finalizado(limite)


def _evento_esta_disponible_ahora(evento: Evento) -> bool:
    """
    Verifica si ya llegó la fecha/hora configurada en disponible_desde.
    Si disponible_desde es NULL, está disponible desde el momento de su creación.
    """
    disp = _asegurar_utc(getattr(evento, "disponible_desde", None))
    if disp is None:
        return True
    return ahora_utc() >= disp


TIPOS_ARCHIVO = {"material", "evidencia"}
MAX_ARCHIVOS_POR_PETICION = 20


# ---------------------------------------------------------------- helpers

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


def _serializar(evento: Evento, base: str, conteos: Optional[Dict] = None) -> EventoOutConVigencia:
    archivos = [_archivo_out(a, base) for a in evento.archivos]
    flyer = next((a.url for a in archivos if a.tipo == "material" and a.es_imagen), None)
    registrados, asistieron = conteos.get(evento.id, (0, 0)) if conteos is not None else (None, None)
    return EventoOutConVigencia(
        id=evento.id, titulo=evento.titulo, descripcion=evento.descripcion,
        disponible_desde=getattr(evento, "disponible_desde", None),
        fecha_hora=evento.fecha_hora,
        fecha_fin=getattr(evento, "fecha_fin", None),
        lugar=evento.lugar, categoria=evento.categoria,
        es_general=evento.es_general, id_facultad_destino=evento.id_facultad_destino,
        id_carrera_destino=evento.id_carrera_destino, grupos_destino=evento.grupos_destino,
        imagen_url=flyer or evento.imagen_url, organizador_email=evento.organizador_email,
        finalizado=_evento_ha_finalizado(evento), archivos=archivos,
        total_registrados=registrados, total_asistieron=asistieron,
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
    evento.disponible_desde = datos.disponible_desde
    evento.fecha_hora = datos.fecha_hora
    evento.fecha_fin = datos.fecha_fin
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


# ---------------------------------------------------------------- listados

@router.get("/feed", response_model=List[EventoOutConVigencia])
def obtener_feed(
    request: Request,
    incluir_todos: bool = False,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Devuelve los eventos vigentes (que ya alcanzaron su fecha 'disponible_desde' y NO han
    alcanzado su 'fecha_fin' / 'fecha_hora' de expiración).
    Así los eventos finalizados desaparecen automáticamente de la App Android.
    """
    eventos = (
        db.query(Evento).options(selectinload(Evento.archivos))
        .order_by(Evento.fecha_hora.asc()).all()
    )
    user_agent = (request.headers.get("user-agent") or "").lower()
    es_app_android = "okhttp" in user_agent

    # En la App Android (o si no se pide incluir_todos para estadísticas web),
    # filtrar siempre eventos finalizados/expirados y eventos cuya fecha disponible_desde aún no llega
    if es_app_android or not incluir_todos:
        eventos = [
            e for e in eventos
            if not _evento_ha_finalizado(e) and _evento_esta_disponible_ahora(e)
        ]

    if not _puede_gestionar(usuario):
        tiene_perfil_segmentado = bool(usuario.id_facultad or usuario.id_carrera or usuario.grupos)
        if tiene_perfil_segmentado:
            eventos = [e for e in eventos if evento_visible_para(e, usuario)]
    base = _base_url(request)
    return [_serializar(e, base) for e in eventos]


@router.get("/pasados", response_model=List[EventoOutConVigencia])
def eventos_pasados(
    request: Request,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir", "estadisticas:leer")),
    db: Session = Depends(get_db),
):
    """Eventos finalizados (más recientes primero) con sus archivos de evidencia y conteos."""
    eventos = (
        db.query(Evento).options(selectinload(Evento.archivos))
        .order_by(Evento.fecha_hora.desc()).all()
    )
    eventos = [e for e in eventos if _evento_ha_finalizado(e)]
    conteos = _conteos(db, [e.id for e in eventos])
    base = _base_url(request)
    return [_serializar(e, base, conteos) for e in eventos]


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


# ---------------------------------------------------------------- CRUD (Protegidos con 403 Forbidden)

@router.post("/", response_model=EventoOutConVigencia, status_code=201)
def crear_evento(
    request: Request,
    evento_in: EventoCreateConVigencia,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    """
    1. Protegido con requiere_permiso("eventos:escribir") -> devuelve 403 Forbidden si un alumno intenta crear.
    2. Al guardar en PostgreSQL, dispara automáticamente la notificación push de Firebase:
       "¡Nuevo evento disponible: [Título del Evento]!".
    """
    nuevo = Evento(organizador_email=usuario.email)
    _aplicar_datos(nuevo, evento_in)
    db.add(nuevo)
    db.commit()
    db.refresh(nuevo)

    registrar_auditoria(db, usuario_email=usuario.email, ip=_ip(request),
                        accion=f"Evento creado: {nuevo.titulo}")

    # Disparar notificación masiva a los tokens FCM registrados en Firebase
    try:
        enviar_notificacion_nuevo_evento(db, nuevo)
    except Exception as exc:
        print(f"[FCM] Error no bloqueante al enviar push de nuevo evento: {exc}")

    return _serializar(nuevo, _base_url(request))


@router.put("/{evento_id}", response_model=EventoOutConVigencia)
def editar_evento(
    request: Request,
    evento_id: uuid.UUID,
    evento_in: EventoCreateConVigencia,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    evento = _buscar_evento(db, evento_id)
    _aplicar_datos(evento, evento_in)
    db.commit()
    db.refresh(evento)
    registrar_auditoria(db, usuario_email=usuario.email, ip=_ip(request),
                        accion=f"Evento editado: {evento.titulo}")
    return _serializar(evento, _base_url(request))


@router.delete("/{evento_id}", status_code=200)
def eliminar_evento(
    request: Request,
    evento_id: uuid.UUID,
    usuario: Usuario = Depends(requiere_permiso("eventos:eliminar")),
    db: Session = Depends(get_db),
):
    """
    Vulnerabilidad 1 del Profe:
    Si un usuario normal intenta borrar un evento enviando DELETE directamente,
    requiere_permiso("eventos:eliminar") lo rechaza con 403 Forbidden antes de tocar la BD.
    """
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
    """
    Permite probar al instante el Job Scheduler de recordatorios de 24h sin esperar 1 hora.
    """
    resultado = verificar_y_enviar_recordatorios_24h()
    return {"mensaje": "Verificación de recordatorios 24h ejecutada", "resultado": resultado}


# ---------------------------------------------------------------- archivos (flyers, materiales, evidencias)

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


# ---------------------------------------------------------------- asistencia

@router.post("/{evento_id}/registro", status_code=200)
def registrar_asistencia(
    evento_id: uuid.UUID,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Inscribe al usuario autenticado en el evento (para recibir también recordatorio 24h antes)."""
    evento = _buscar_evento(db, evento_id)
    if _evento_ha_finalizado(evento):
        raise HTTPException(status_code=400, detail="Este evento ya finalizó.")
    tiene_perfil_segmentado = bool(usuario.id_facultad or usuario.id_carrera or usuario.grupos)
    if not _puede_gestionar(usuario) and tiene_perfil_segmentado and not evento_visible_para(evento, usuario):
        raise HTTPException(status_code=403, detail="Este evento no está dirigido a tu carrera o grupo.")
    ya = db.query(EventoAsistencia).filter_by(evento_id=evento.id, usuario_id=usuario.id).first()
    if ya:
        return {"mensaje": "Ya estás registrado en este evento."}
    db.add(EventoAsistencia(evento_id=evento.id, usuario_id=usuario.id))
    db.commit()
    return {"mensaje": "Inscripción realizada correctamente."}


@router.delete("/{evento_id}/registro", status_code=200)
def cancelar_asistencia(
    evento_id: uuid.UUID,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    fila = db.query(EventoAsistencia).filter_by(evento_id=evento_id, usuario_id=usuario.id).first()
    if fila:
        db.delete(fila)
        db.commit()
    return {"mensaje": "Inscripción cancelada correctamente."}


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
            "carrera": u.id_carrera,
            "grupos": u.grupos or [],
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
            "nombre": u.nombre, "email": u.email, "carrera": u.id_carrera,
            "grupos": ", ".join(u.grupos or []), "registro": fecha_corta(a.fecha_registro),
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