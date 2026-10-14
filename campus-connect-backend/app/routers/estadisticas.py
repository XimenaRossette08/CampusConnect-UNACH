import uuid
from collections import Counter, defaultdict
from typing import Dict

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, selectinload

from ..database import get_db
from ..fechas import a_local, evento_finalizado, fecha_larga
from ..models import Evento, EventoAsistencia
from ..models_auth import Usuario
from ..security import requiere_permiso

router = APIRouter(prefix="/estadisticas", tags=["estadísticas"])


def _pct(parte: int, total: int) -> float:
    return round(parte * 100 / total, 1) if total else 0.0


def _registros(db: Session):
    """(evento_id, asistio, fecha_registro, carrera, grupos) de todas las inscripciones."""
    return (
        db.query(
            EventoAsistencia.evento_id, EventoAsistencia.asistio, EventoAsistencia.fecha_registro,
            Usuario.id_carrera, Usuario.grupos,
        )
        .join(Usuario, Usuario.id == EventoAsistencia.usuario_id)
        .all()
    )


@router.get("/general")
def estadisticas_generales(
    usuario: Usuario = Depends(requiere_permiso("estadisticas:leer")),
    db: Session = Depends(get_db),
):
    eventos = db.query(Evento).options(selectinload(Evento.archivos)).all()
    registros = _registros(db)

    reg_por_evento: Dict[uuid.UUID, int] = Counter()
    asis_por_evento: Dict[uuid.UUID, int] = Counter()
    por_carrera = defaultdict(lambda: [0, 0])  # carrera -> [registros, asistencias]
    for evento_id, asistio, _fecha, carrera, _grupos in registros:
        reg_por_evento[evento_id] += 1
        if asistio:
            asis_por_evento[evento_id] += 1
        clave = carrera or "Sin carrera"
        por_carrera[clave][0] += 1
        por_carrera[clave][1] += 1 if asistio else 0

    finalizados = [e for e in eventos if evento_finalizado(e.fecha_hora)]
    ids_fin = {e.id for e in finalizados}
    reg_fin = sum(reg_por_evento[i] for i in ids_fin)
    asis_fin = sum(asis_por_evento[i] for i in ids_fin)

    # Últimos 12 meses (hora local)
    por_mes = defaultdict(lambda: {"eventos": 0, "registros": 0})
    for e in eventos:
        l = a_local(e.fecha_hora)
        clave = f"{l.year}-{l.month:02d}"
        por_mes[clave]["eventos"] += 1
        por_mes[clave]["registros"] += reg_por_evento[e.id]

    por_categoria = defaultdict(lambda: {"eventos": 0, "registros": 0})
    for e in eventos:
        c = por_categoria[e.categoria or "Sin categoría"]
        c["eventos"] += 1
        c["registros"] += reg_por_evento[e.id]

    top = sorted(eventos, key=lambda e: reg_por_evento[e.id], reverse=True)[:5]
    con_evidencia = sum(1 for e in finalizados if any(a.tipo == "evidencia" for a in e.archivos))

    return {
        "totalEventos": len(eventos),
        "proximos": len(eventos) - len(finalizados),
        "finalizados": len(finalizados),
        "totalRegistros": len(registros),
        "promedioRegistrosPorEvento": round(len(registros) / len(eventos), 1) if eventos else 0.0,
        "tasaAsistencia": _pct(asis_fin, reg_fin),
        "asistenciasConfirmadas": asis_fin,
        "finalizadosConEvidencia": con_evidencia,
        "finalizadosSinEvidencia": len(finalizados) - con_evidencia,
        "eventosGenerales": sum(1 for e in eventos if e.es_general),
        "eventosSegmentados": sum(1 for e in eventos if not e.es_general),
        "porMes": [{"mes": k, **v} for k, v in sorted(por_mes.items())[-12:]],
        "porCategoria": [{"categoria": k, **v} for k, v in
                         sorted(por_categoria.items(), key=lambda kv: -kv[1]["eventos"])],
        "porCarrera": [{"carrera": k, "registros": v[0], "asistencias": v[1]} for k, v in
                       sorted(por_carrera.items(), key=lambda kv: -kv[1][0])],
        "topEventos": [
            {"id": str(e.id), "titulo": e.titulo, "registrados": reg_por_evento[e.id],
             "asistieron": asis_por_evento[e.id]} for e in top
        ],
    }


@router.get("/evento/{evento_id}")
def estadisticas_de_evento(
    evento_id: uuid.UUID,
    usuario: Usuario = Depends(requiere_permiso("estadisticas:leer")),
    db: Session = Depends(get_db),
):
    evento = db.query(Evento).options(selectinload(Evento.archivos)).filter(Evento.id == evento_id).first()
    if not evento:
        raise HTTPException(status_code=404, detail="Evento no encontrado.")

    filas = [r for r in _registros(db) if r[0] == evento.id]
    total = len(filas)
    asistieron = sum(1 for r in filas if r[1])

    por_carrera = defaultdict(lambda: [0, 0])
    por_grupo = defaultdict(lambda: [0, 0])
    por_dia = Counter()
    for _eid, asistio, fecha, carrera, grupos in filas:
        c = por_carrera[carrera or "Sin carrera"]
        c[0] += 1
        c[1] += 1 if asistio else 0
        for g in (grupos or ["Sin grupo"]):
            gg = por_grupo[g]
            gg[0] += 1
            gg[1] += 1 if asistio else 0
        if fecha:
            por_dia[a_local(fecha).strftime("%Y-%m-%d")] += 1

    return {
        "evento": {
            "id": str(evento.id), "titulo": evento.titulo, "lugar": evento.lugar,
            "fecha": fecha_larga(evento.fecha_hora), "categoria": evento.categoria,
            "finalizado": evento_finalizado(evento.fecha_hora),
        },
        "registrados": total,
        "asistieron": asistieron,
        "tasaAsistencia": _pct(asistieron, total),
        "archivosMaterial": sum(1 for a in evento.archivos if a.tipo == "material"),
        "archivosEvidencia": sum(1 for a in evento.archivos if a.tipo == "evidencia"),
        "porCarrera": [{"carrera": k, "registros": v[0], "asistencias": v[1]} for k, v in
                       sorted(por_carrera.items(), key=lambda kv: -kv[1][0])],
        "porGrupo": [{"grupo": k, "registros": v[0], "asistencias": v[1]} for k, v in
                     sorted(por_grupo.items(), key=lambda kv: -kv[1][0])],
        "inscripcionesPorDia": [{"fecha": k, "registros": v} for k, v in sorted(por_dia.items())],
    }
