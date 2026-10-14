"""Reglas de visibilidad de eventos por Facultad -> Carrera -> Grupo (sin dependencias externas)."""
from typing import Iterable, Optional


def _norm(valor: Optional[str]) -> str:
    return (valor or "").strip().casefold()


def _normalizar_lista(valores: Optional[Iterable[str]]) -> set:
    return {_norm(v) for v in (valores or []) if _norm(v)}


def tiene_segmentacion(evento) -> bool:
    return bool(
        _norm(evento.id_facultad_destino)
        or _norm(evento.id_carrera_destino)
        or _normalizar_lista(evento.grupos_destino)
    )


def evento_visible_para(evento, usuario) -> bool:
    """
    - Evento general: lo ve todo el mundo.
    - Evento segmentado: el alumno debe cumplir TODOS los filtros que el evento defina
      (facultad, carrera y/o grupo). Un filtro que el evento no define no restringe.
    - Evento no general y sin ningún filtro: no se muestra a alumnos.
    """
    if evento.es_general:
        return True
    if not tiene_segmentacion(evento):
        return False

    fac = _norm(evento.id_facultad_destino)
    if fac and fac != _norm(usuario.id_facultad):
        return False

    car = _norm(evento.id_carrera_destino)
    if car and car != _norm(usuario.id_carrera):
        return False

    grupos_evento = _normalizar_lista(evento.grupos_destino)
    if grupos_evento and not (grupos_evento & _normalizar_lista(usuario.grupos)):
        return False

    return True
