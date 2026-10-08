import re
from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator


class UsuarioCreate(BaseModel):
    nombre: str
    email: EmailStr
    password: str = Field(min_length=8)

    @field_validator("email")
    @classmethod
    def validar_estructura_correo(cls, valor: str) -> str:
        # Patrón: letras . letras + 2 números @ unach.mx
        patron = r"^[a-zA-Z]+\.[a-zA-Z]+[0-9]{2}@unach\.mx$"
        if not re.match(patron, str(valor)):
            raise ValueError("El correo debe tener la estructura: nombre.apellido##@unach.mx")
        return valor

    @field_validator("password")
    @classmethod
    def password_segura(cls, valor: str) -> str:
        if not any(c.isdigit() for c in valor) or not any(c.isalpha() for c in valor):
            raise ValueError("La contraseña debe incluir letras y números")
        return valor


class UsuarioOut(BaseModel):
    id: int
    nombre: str
    email: str
    activo: bool
    rol: str

    model_config = ConfigDict(from_attributes=True)

    @classmethod
    def desde_usuario(cls, usuario) -> "UsuarioOut":
        return cls(
            id=usuario.id,
            nombre=usuario.nombre,
            email=usuario.email,
            activo=usuario.activo,
            rol=usuario.rol.nombre,
        )


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    role: str


class RefreshRequest(BaseModel):
    refresh_token: str


class CambiarPasswordRequest(BaseModel):
    password_actual: str
    password_nueva: str = Field(min_length=8)


class SolicitarRecuperacionRequest(BaseModel):
    email: EmailStr


class RestablecerPasswordRequest(BaseModel):
    token: str
    password_nueva: str = Field(min_length=8)


class RolCreate(BaseModel):
    nombre: str


class RolOut(BaseModel):
    id: int
    nombre: str
    permisos: List[str]


class PermisoOut(BaseModel):
    id: int
    clave: str
    descripcion: str

    model_config = ConfigDict(from_attributes=True)


class AsignarPermisosRequest(BaseModel):
    claves_permisos: List[str]


class AsignarRolRequest(BaseModel):
    rol_id: int


class RegistroAuditoriaOut(BaseModel):
    id: int
    usuario_email: Optional[str]
    fecha_hora: datetime
    direccion_ip: str
    accion: str
    exitoso: bool

    model_config = ConfigDict(from_attributes=True)