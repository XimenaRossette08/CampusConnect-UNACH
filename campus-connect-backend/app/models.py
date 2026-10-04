import uuid

from sqlalchemy import ARRAY, Boolean, Column, DateTime, String
from sqlalchemy.dialects.postgresql import UUID

from .database import Base


class Evento(Base):
    """
    Tabla de eventos. Los nombres de columna van en snake_case (convención
    de PostgreSQL); el mapeo a camelCase para el cliente Android
    (fechaHora, esGeneral, idFacultadDestino, etc.) se resuelve en schemas.py
    con Field(alias=...), no aquí.
    """

    __tablename__ = "eventos"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    titulo = Column(String, nullable=False)
    descripcion = Column(String, nullable=False)
    fecha_hora = Column(DateTime(timezone=True), nullable=False)
    lugar = Column(String, nullable=False)
    organizador_email = Column(String, nullable=False)

    # Segmentación: Facultad -> Carrera -> Grupo
    es_general = Column(Boolean, default=False, nullable=False)
    id_facultad_destino = Column(String, nullable=True)
    id_carrera_destino = Column(String, nullable=True)
    grupos_destino = Column(ARRAY(String), nullable=True)
