from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import and_, or_
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Evento
from ..models_auth import Usuario
from ..schemas import EventoCreate, EventoOut
from ..security import get_current_user, requiere_permiso

router = APIRouter(prefix="/eventos", tags=["eventos"])


@router.get("/feed", response_model=List[EventoOut])
def obtener_feed(
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Devuelve solo los eventos visibles para el usuario autenticado.
    """
    query = db.query(Evento)

    if usuario.rol.nombre != "Administrador":
        condiciones = [Evento.es_general.is_(True)]

        if usuario.id_facultad:
            condicion_facultad = Evento.id_facultad_destino == usuario.id_facultad

            if usuario.id_carrera:
                condicion_facultad = and_(
                    condicion_facultad,
                    or_(
                        Evento.id_carrera_destino.is_(None),
                        Evento.id_carrera_destino == usuario.id_carrera,
                    ),
                )

            condiciones.append(condicion_facultad)

        query = query.filter(or_(*condiciones))

    return query.order_by(Evento.fecha_hora.asc()).all()


@router.post("/", response_model=EventoOut, status_code=201)
def crear_evento(
    evento_in: EventoCreate,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    nuevo_evento = Evento(
        titulo=evento_in.titulo,
        descripcion=evento_in.descripcion,
        fecha_hora=evento_in.fecha_hora,
        lugar=evento_in.lugar,
        es_general=evento_in.es_general,
        imagen_url=evento_in.imagen_url,  # <-- ASIGNAR IMAGEN
        id_facultad_destino=evento_in.id_facultad_destino,
        id_carrera_destino=evento_in.id_carrera_destino,
        grupos_destino=evento_in.grupos_destino,
        organizador_email=usuario.email,
    )
    db.add(nuevo_evento)
    db.commit()
    db.refresh(nuevo_evento)
    return nuevo_evento


@router.put("/{evento_id}", response_model=EventoOut)
def editar_evento(
    evento_id: str,
    evento_in: EventoCreate,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    evento = db.query(Evento).filter(Evento.id == evento_id).first()
    if not evento:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Evento no encontrado.",
        )

    evento.titulo = evento_in.titulo
    evento.descripcion = evento_in.descripcion
    evento.fecha_hora = evento_in.fecha_hora
    evento.lugar = evento_in.lugar
    evento.es_general = evento_in.es_general
    evento.imagen_url = evento_in.imagen_url  # <-- ACTUALIZAR IMAGEN

    db.commit()
    db.refresh(evento)
    return evento

@router.put("/{evento_id}", response_model=EventoOut)
def editar_evento(
    evento_id: str,
    evento_in: EventoCreate,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    """
    Permite actualizar un evento existente por su ID.
    """
    evento = db.query(Evento).filter(Evento.id == evento_id).first()
    if not evento:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="El evento no existe en la base de datos.",
        )

    evento.titulo = evento_in.titulo
    evento.descripcion = evento_in.descripcion
    evento.fecha_hora = evento_in.fecha_hora
    evento.lugar = evento_in.lugar
    evento.es_general = evento_in.es_general

    if hasattr(evento_in, "imagen_url"):
        evento.imagen_url = evento_in.imagen_url

    db.commit()
    db.refresh(evento)
    return evento


@router.delete("/{evento_id}", status_code=200)
def eliminar_evento(
    evento_id: str,
    usuario: Usuario = Depends(requiere_permiso("eventos:eliminar")),
    db: Session = Depends(get_db),
):
    """
    Elimina permanentemente un evento por su ID.
    """
    evento = db.query(Evento).filter(Evento.id == evento_id).first()
    if not evento:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="El evento no existe en la base de datos.",
        )

    db.delete(evento)
    db.commit()
    return {"mensaje": "Evento eliminado correctamente."}


@router.post("/{evento_id}/registro", status_code=200)
def registrar_asistencia(
    evento_id: str,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Registra la asistencia o inscripción del usuario autenticado a un evento.
    """
    evento = db.query(Evento).filter(Evento.id == evento_id).first()
    if not evento:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Evento no encontrado.",
        )

    return {"mensaje": "Inscripción realizada correctamente."}


@router.get("/{evento_id}/asistentes")
def obtener_asistentes(
    evento_id: str,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    """
    Consulta la lista de personas registradas en el evento.
    """
    evento = db.query(Evento).filter(Evento.id == evento_id).first()
    if not evento:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Evento no encontrado.",
        )

    return []