import uuid
from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field


class EventoBase(BaseModel):
    titulo: str
    descripcion: str
    fecha_hora: datetime = Field(alias="fechaHora")
    lugar: str
    es_general: bool = Field(default=False, alias="esGeneral")
    id_facultad_destino: Optional[str] = Field(default=None, alias="idFacultadDestino")
    id_carrera_destino: Optional[str] = Field(default=None, alias="idCarreraDestino")
    grupos_destino: Optional[List[str]] = Field(default=None, alias="gruposDestino")
    imagen_url: Optional[str] = Field(default=None, alias="imagenUrl")  # <-- CAMPO AGREGADO

    # Permite construir el modelo tanto por nombre de campo (Python)
    # como por alias (JSON/Kotlin), y serializa siempre en camelCase.
    model_config = ConfigDict(populate_by_name=True)


class EventoCreate(EventoBase):
    """Lo que el cliente envía al crear o editar un evento."""

    pass


class EventoOut(EventoBase):
    """Lo que el servidor devuelve al cliente."""

    id: uuid.UUID
    organizador_email: str = Field(alias="organizador")

    model_config = ConfigDict(populate_by_name=True, from_attributes=True)