import uuid
from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator


class EventoBase(BaseModel):
    titulo: str
    descripcion: str
    fecha_hora: datetime = Field(alias="fechaHora")
    lugar: str
    categoria: Optional[str] = None
    es_general: bool = Field(default=False, alias="esGeneral")
    id_facultad_destino: Optional[str] = Field(default=None, alias="idFacultadDestino")
    id_carrera_destino: Optional[str] = Field(default=None, alias="idCarreraDestino")
    grupos_destino: Optional[List[str]] = Field(default=None, alias="gruposDestino")
    imagen_url: Optional[str] = Field(default=None, alias="imagenUrl")

    model_config = ConfigDict(populate_by_name=True)

    @field_validator("id_facultad_destino", "id_carrera_destino", "categoria")
    @classmethod
    def _limpiar_texto(cls, v):
        v = (v or "").strip()
        return v or None

    @field_validator("grupos_destino")
    @classmethod
    def _limpiar_grupos(cls, v):
        if v is None:
            return None
        limpios = []
        for g in v:
            g = (g or "").strip()[:30]
            if g and g.casefold() not in {x.casefold() for x in limpios}:
                limpios.append(g)
        return limpios[:30] or None


class EventoCreate(EventoBase):
    """Lo que el cliente envía al crear/editar un evento. Los límites de longitud solo se exigen aquí,
    para que un evento antiguo con datos largos nunca rompa las respuestas del servidor."""

    titulo: str = Field(min_length=1, max_length=200)
    descripcion: str = Field(min_length=1, max_length=5000)
    lugar: str = Field(min_length=1, max_length=200)
    categoria: Optional[str] = Field(default=None, max_length=60)
    id_facultad_destino: Optional[str] = Field(default=None, alias="idFacultadDestino", max_length=120)
    id_carrera_destino: Optional[str] = Field(default=None, alias="idCarreraDestino", max_length=120)
    imagen_url: Optional[str] = Field(default=None, alias="imagenUrl", max_length=500)


class ArchivoOut(BaseModel):
    id: uuid.UUID
    tipo: str
    nombre: str
    mime: str
    tamano: int
    url: str
    es_imagen: bool = Field(alias="esImagen")

    model_config = ConfigDict(populate_by_name=True)


class EventoOut(EventoBase):
    """Lo que el servidor devuelve al cliente."""

    id: uuid.UUID
    organizador_email: Optional[str] = Field(default="admin@unach.mx", alias="organizador")
    finalizado: bool = False
    archivos: List[ArchivoOut] = []
    total_registrados: Optional[int] = Field(default=None, alias="totalRegistrados")
    total_asistieron: Optional[int] = Field(default=None, alias="totalAsistieron")

    model_config = ConfigDict(populate_by_name=True, from_attributes=True)
