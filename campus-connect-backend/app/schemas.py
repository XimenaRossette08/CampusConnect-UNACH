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
    imagen_url: Optional[str] = Field(default=None, alias="imagenUrl")

    model_config = ConfigDict(populate_by_name=True)

class EventoCreate(EventoBase):
    pass

class EventoOut(EventoBase):
    id: uuid.UUID
    # Se vuelve Optional para admitir registros antiguos sin correo
    organizador_email: Optional[str] = Field(default="contacto@unach.mx", alias="organizador")

    model_config = ConfigDict(populate_by_name=True, from_attributes=True)