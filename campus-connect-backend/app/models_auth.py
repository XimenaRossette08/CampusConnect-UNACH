from datetime import datetime, timezone

from sqlalchemy import Boolean, Column, DateTime, ForeignKey, Integer, String, Table
from sqlalchemy.dialects.postgresql import ARRAY
from sqlalchemy.orm import relationship

from .database import Base

rol_permiso = Table(
    "rol_permiso",
    Base.metadata,
    Column("rol_id", Integer, ForeignKey("roles.id", ondelete="CASCADE"), primary_key=True),
    Column("permiso_id", Integer, ForeignKey("permisos.id", ondelete="CASCADE"), primary_key=True),
)


class Rol(Base):
    __tablename__ = "roles"

    id = Column(Integer, primary_key=True)
    nombre = Column(String, unique=True, nullable=False)

    permisos = relationship("Permiso", secondary=rol_permiso, back_populates="roles")
    usuarios = relationship("Usuario", back_populates="rol")


class Permiso(Base):
    __tablename__ = "permisos"

    id = Column(Integer, primary_key=True)
    clave = Column(String, unique=True, nullable=False)  # p.ej. "eventos:eliminar"
    descripcion = Column(String, nullable=False)

    roles = relationship("Rol", secondary=rol_permiso, back_populates="permisos")


class Usuario(Base):
    __tablename__ = "usuarios"

    id = Column(Integer, primary_key=True)
    nombre = Column(String, nullable=False)
    email = Column(String, unique=True, nullable=False, index=True)
    password_hash = Column(String, nullable=False)
    activo = Column(Boolean, default=True, nullable=False)
    creado_en = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))

    rol_id = Column(Integer, ForeignKey("roles.id"), nullable=False)
    rol = relationship("Rol", back_populates="usuarios")

    # Segmentación de eventos (Facultad -> Carrera -> Grupo)
    id_facultad = Column(String, nullable=True)
    id_carrera = Column(String, nullable=True)
    grupos = Column(ARRAY(String), nullable=True)

    # Registro diferenciado para Alumnos Egresados (2 correos y 2 teléfonos)
    es_egresado = Column(Boolean, default=False, nullable=False)
    email_secundario = Column(String, nullable=True)
    telefono_principal = Column(String, nullable=True)
    telefono_secundario = Column(String, nullable=True)

    # Autenticación de dos factores (2FA) y memoria de confianza de 30 días
    codigo_2fa = Column(String(6), nullable=True)
    codigo_2fa_expiracion = Column(DateTime(timezone=True), nullable=True)
    ultima_verificacion_2fa = Column(DateTime(timezone=True), nullable=True)

    # Token de Firebase Cloud Messaging (FCM) enviado por la App Android
    fcm_token = Column(String, nullable=True)


class RegistroAuditoria(Base):
    """
    Tabla inmutable de auditoría: solo permite inserción y lectura.
    """

    __tablename__ = "registro_auditoria"

    id = Column(Integer, primary_key=True)
    usuario_email = Column(String, nullable=True)
    fecha_hora = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    direccion_ip = Column(String, nullable=False)
    accion = Column(String, nullable=False)
    exitoso = Column(Boolean, default=True, nullable=False)
