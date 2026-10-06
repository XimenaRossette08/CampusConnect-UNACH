import uuid

from sqlalchemy import ARRAY, Boolean, Column, DateTime, String
from sqlalchemy.dialects.postgresql import UUID

from .database import Base


class Evento(Base):
    __tablename__ = "eventos"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    titulo = Column(String, nullable=False)
    descripcion = Column(String, nullable=False)
    fecha_hora = Column(DateTime(timezone=True), nullable=False)
    lugar = Column(String, nullable=False)
    organizador_email = Column(String, nullable=False)
    imagen_url = Column(String, nullable=True)  # <-- CAMPO AGREGADO

    # Segmentación: Facultad -> Carrera -> Grupo
    es_general = Column(Boolean, default=False, nullable=False)
    id_facultad_destino = Column(String, nullable=True)
    id_carrera_destino = Column(String, nullable=True)
    grupos_destino = Column(ARRAY(String), nullable=True)