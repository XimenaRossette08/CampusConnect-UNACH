import uuid
from datetime import datetime, timezone

from sqlalchemy import (
    ARRAY, Boolean, Column, DateTime, ForeignKey, Integer, String, UniqueConstraint,
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship

from .database import Base


def _ahora():
    return datetime.now(timezone.utc)


class Evento(Base):
    __tablename__ = "eventos"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    titulo = Column(String, nullable=False)
    descripcion = Column(String, nullable=False)
    fecha_hora = Column(DateTime(timezone=True), nullable=False)
    lugar = Column(String, nullable=False)
    organizador_email = Column(String, nullable=False)
    imagen_url = Column(String, nullable=True)  # legado: eventos creados antes de la subida de archivos
    categoria = Column(String, nullable=True)

    # Segmentación: Facultad -> Carrera -> Grupo
    es_general = Column(Boolean, default=False, nullable=False)
    id_facultad_destino = Column(String, nullable=True)
    id_carrera_destino = Column(String, nullable=True)
    grupos_destino = Column(ARRAY(String), nullable=True)

    # Control para el Job Scheduler (APScheduler) de recordatorios 24h antes
    recordatorio_enviado = Column(Boolean, default=False, nullable=False)

    archivos = relationship(
        "EventoArchivo", back_populates="evento", cascade="all, delete-orphan",
        order_by="EventoArchivo.creado_en",
    )
    asistencias = relationship(
        "EventoAsistencia", back_populates="evento", cascade="all, delete-orphan",
    )


class EventoArchivo(Base):
    """Flyers/materiales (tipo 'material', visibles para los alumnos) y fotos de evidencia."""

    __tablename__ = "evento_archivos"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    evento_id = Column(UUID(as_uuid=True), ForeignKey("eventos.id", ondelete="CASCADE"),
                       nullable=False, index=True)
    tipo = Column(String, nullable=False, default="material")  # 'material' | 'evidencia'
    nombre_original = Column(String, nullable=False)
    nombre_guardado = Column(String, nullable=False, unique=True)
    mime = Column(String, nullable=False)
    tamano = Column(Integer, nullable=False)
    subido_por = Column(String, nullable=True)
    creado_en = Column(DateTime(timezone=True), default=_ahora, nullable=False)

    evento = relationship("Evento", back_populates="archivos")


class EventoAsistencia(Base):
    """Inscripción de un usuario a un evento; 'asistio' lo confirma el organizador."""

    __tablename__ = "evento_asistencias"
    __table_args__ = (UniqueConstraint("evento_id", "usuario_id", name="uq_evento_usuario"),)

    id = Column(Integer, primary_key=True)
    evento_id = Column(UUID(as_uuid=True), ForeignKey("eventos.id", ondelete="CASCADE"),
                       nullable=False, index=True)
    usuario_id = Column(Integer, ForeignKey("usuarios.id", ondelete="CASCADE"), nullable=False)
    fecha_registro = Column(DateTime(timezone=True), default=_ahora, nullable=False)
    asistio = Column(Boolean, default=False, nullable=False)

    evento = relationship("Evento", back_populates="asistencias")
    usuario = relationship("Usuario")
