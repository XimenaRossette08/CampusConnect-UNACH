"""Utilidades de fecha sin dependencias de FastAPI (fáciles de probar)."""
import os
from datetime import datetime, timedelta, timezone

# Chiapas usa horario del centro sin horario de verano (UTC-6). Se puede cambiar por entorno.
_OFFSET_HORAS = float(os.getenv("TZ_OFFSET_HORAS", "-6"))
ZONA_LOCAL = timezone(timedelta(hours=_OFFSET_HORAS))

# Un evento se considera "finalizado" cuando pasan estas horas desde su fecha/hora de inicio.
DURACION_EVENTO_HORAS = float(os.getenv("DURACION_EVENTO_HORAS", "4"))

_MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
          "agosto", "septiembre", "octubre", "noviembre", "diciembre"]
_DIAS = ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"]


def ahora_utc() -> datetime:
    return datetime.now(timezone.utc)


def a_local(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(ZONA_LOCAL)


def evento_finalizado(fecha_hora: datetime, ahora: datetime | None = None) -> bool:
    ahora = ahora or ahora_utc()
    if fecha_hora.tzinfo is None:
        fecha_hora = fecha_hora.replace(tzinfo=timezone.utc)
    return fecha_hora + timedelta(hours=DURACION_EVENTO_HORAS) <= ahora


def fecha_larga(dt: datetime) -> str:
    """Ej.: 'jueves 15 de octubre de 2026, 09:00 h'."""
    l = a_local(dt)
    return f"{_DIAS[l.weekday()]} {l.day} de {_MESES[l.month - 1]} de {l.year}, {l:%H:%M} h"


def fecha_corta(dt: datetime) -> str:
    return a_local(dt).strftime("%d/%m/%Y %H:%M")
