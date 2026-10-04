from typing import List

from fastapi import APIRouter, Depends
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
    Toda la segmentación (Facultad -> Carrera -> Grupo) se resuelve aquí,
    en el servidor; el cliente (móvil o web) solo pinta la respuesta.
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
        id_facultad_destino=evento_in.id_facultad_destino,
        id_carrera_destino=evento_in.id_carrera_destino,
        grupos_destino=evento_in.grupos_destino,
        organizador_email=usuario.email,
    )
    db.add(nuevo_evento)
    db.commit()
    db.refresh(nuevo_evento)
    return nuevo_evento
