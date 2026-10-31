"""Utilidades de fecha sin dependencias de FastAPI (fáciles de probar)."""
import os
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

# Zona horaria oficial de Chiapas (America/Mexico_City, UTC-6 permanente sin horario de verano)
try:
    ZONA_LOCAL = ZoneInfo(os.getenv("TZ_NAME", "America/Mexico_City"))
except Exception:
    _OFFSET_HORAS = float(os.getenv("TZ_OFFSET_HORAS", "-6"))
    ZONA_LOCAL = timezone(timedelta(hours=_OFFSET_HORAS))

# Respaldo solo cuando un evento no tiene fecha_fin explícita
DURACION_EVENTO_HORAS = float(os.getenv("DURACION_EVENTO_HORAS", "4"))

_MESES = [
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
]
_DIAS = ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"]


def ahora_utc() -> datetime:
    """Devuelve la fecha/hora actual en UTC con tzinfo."""
    return datetime.now(timezone.utc)


def obtener_hora_local_mexico() -> datetime:
    """Devuelve la fecha/hora actual en Chiapas/México con tzinfo."""
    return datetime.now(ZONA_LOCAL)


def asegurar_utc(dt: datetime | None) -> datetime | None:
    """
    Convierte cualquier datetime a UTC con tzinfo.
    Si el datetime viene sin zona horaria (naive, ej. '2026-10-31T08:55' desde un input local),
    se interpreta como hora local de Chiapas (UTC-6) y se convierte a UTC.
    """
    if dt is None:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=ZONA_LOCAL)
    return dt.astimezone(timezone.utc)


def a_local(dt: datetime) -> datetime:
    """Convierte un datetime a la hora local de Chiapas (UTC-6)."""
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(ZONA_LOCAL)


def evento_disponible(disponible_desde: datetime | None, ahora: datetime | None = None) -> bool:
    """
    True si el evento no tiene restricción de publicación o si ya se cumplió la hora de disponible_desde.
    """
    if disponible_desde is None:
        return True
    ahora_ref = asegurar_utc(ahora) if ahora is not None else ahora_utc()
    disp_utc = asegurar_utc(disponible_desde)
    return ahora_ref >= disp_utc


def evento_finalizado(
    fecha_hora: datetime,
    ahora: datetime | None = None,
    fecha_fin: datetime | None = None,
) -> bool:
    """
    Determina si un evento ya expiró:
    - Si tiene `fecha_fin` configurada, expira exactamente al llegar a `fecha_fin` (sin sumar 4h extra).
    - Si NO tiene `fecha_fin`, expira `DURACION_EVENTO_HORAS` (4h) después de `fecha_hora`.
    """
    ahora_ref = asegurar_utc(ahora) if ahora is not None else ahora_utc()
    if fecha_fin is not None:
        return ahora_ref >= asegurar_utc(fecha_fin)
    inicio_utc = asegurar_utc(fecha_hora)
    return (inicio_utc + timedelta(hours=DURACION_EVENTO_HORAS)) <= ahora_ref


def fecha_larga(dt: datetime) -> str:
    """Ej.: 'jueves 15 de octubre de 2026, 09:00 h'."""
    l = a_local(dt)
    return f"{_DIAS[l.weekday()]} {l.day} de {_MESES[l.month - 1]} de {l.year}, {l:%H:%M} h"


def fecha_corta(dt: datetime) -> str:
    return a_local(dt).strftime("%d/%m/%Y %H:%M")