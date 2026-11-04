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
    disponible_desde = Column(DateTime(timezone=True), nullable=True)
    fecha_hora = Column(DateTime(timezone=True), nullable=False)
    fecha_fin = Column(DateTime(timezone=True), nullable=True)
    lugar = Column(String, nullable=False)
    organizador_email = Column(String, nullable=False)
    imagen_url = Column(String, nullable=True)
    categoria = Column(String, nullable=True)

    # Segmentación: Facultad -> Carrera -> Grupo
    es_general = Column(Boolean, default=False, nullable=False)
    id_facultad_destino = Column(String, nullable=True)
    id_carrera_destino = Column(String, nullable=True)
    grupos_destino = Column(ARRAY(String), nullable=True)

    # Mapa JSON de asientos reservados por administración para maestros/invitados: {"A-01": {"etiqueta": "Maestro / Invitado", ...}}
    asientos_reservados_json = Column(String, nullable=True, default="{}")

    # Control para el Job Scheduler (APScheduler): aparición, 30m antes del fin, finalizado y 24h antes
    recordatorio_enviado = Column(Boolean, default=False, nullable=False)
    notificacion_aparicion_enviada = Column(Boolean, default=False, nullable=False)
    notificacion_fin_30m_enviada = Column(Boolean, default=False, nullable=False)
    notificacion_finalizado_enviada = Column(Boolean, default=False, nullable=False)

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
    """
    Apartado de asiento individual tipo cine (1 asiento por alumno) + Validación en 2 pasos (1/2 QR + 2/2 GPS).
    - asiento_codigo: Código del asiento apartado por el alumno (ej. "A-05", "B-08").
    - qr_validado: Se activa cuando el alumno escanea el QR oficial del evento al llegar (Validación 1/2).
    - gps_validado: Se activa cuando el alumno cumple la permanencia GPS en el recinto (Validación 2/2).
    - asistio: Se activa automáticamente al cumplir (qr_validado y gps_validado) o cuando el organizador lo valida,
               acreditando +1 en la categoría del evento para las insignias de RewardsScreen.
    """

    __tablename__ = "evento_asistencias"
    __table_args__ = (UniqueConstraint("evento_id", "usuario_id", name="uq_evento_usuario"),)

    id = Column(Integer, primary_key=True)
    evento_id = Column(UUID(as_uuid=True), ForeignKey("eventos.id", ondelete="CASCADE"),
                       nullable=False, index=True)
    usuario_id = Column(Integer, ForeignKey("usuarios.id", ondelete="CASCADE"), nullable=False)
    asiento_codigo = Column(String, nullable=True)
    fecha_registro = Column(DateTime(timezone=True), default=_ahora, nullable=False)
    qr_validado = Column(Boolean, default=False, nullable=False)
    gps_validado = Column(Boolean, default=False, nullable=False)
    pings_gps = Column(Integer, default=0, nullable=False)
    ultimo_ping_gps = Column(DateTime(timezone=True), nullable=True)
    asistio = Column(Boolean, default=False, nullable=False)

    evento = relationship("Evento", back_populates="asistencias")
    usuario = relationship("Usuario")