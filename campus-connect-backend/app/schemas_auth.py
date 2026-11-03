import json
from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator


class UsuarioCreate(BaseModel):
    nombre: str
    email: EmailStr
    password: str = Field(min_length=8)
    es_egresado: bool = False
    email_secundario: Optional[EmailStr] = None
    telefono_principal: Optional[str] = None
    telefono_secundario: Optional[str] = None
    matricula: Optional[str] = None
    carrera: Optional[str] = None
    semestre_grupo: Optional[str] = None

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
    permisos: List[str] = []
    permisos_personalizados: Optional[List[str]] = None
    es_egresado: bool = False

    model_config = ConfigDict(from_attributes=True)

    @classmethod
    def desde_usuario(cls, usuario) -> "UsuarioOut":
        claves = [p.clave for p in (usuario.rol.permisos if usuario.rol else [])]
        personalizados: Optional[List[str]] = None
        raw_pers = getattr(usuario, "permisos_personalizados", None)
        if isinstance(raw_pers, str) and raw_pers.strip():
            try:
                parsed = json.loads(raw_pers)
                if isinstance(parsed, list):
                    personalizados = [str(x).strip() for x in parsed if str(x).strip()]
            except Exception:
                pass
        elif isinstance(raw_pers, list):
            personalizados = [str(x).strip() for x in raw_pers if str(x).strip()]

        return cls(
            id=usuario.id,
            nombre=usuario.nombre,
            email=usuario.email,
            activo=usuario.activo,
            rol=usuario.rol.nombre if usuario.rol else "Usuario Regular",
            permisos=claves,
            permisos_personalizados=personalizados,
            es_egresado=bool(getattr(usuario, "es_egresado", False)),
        )


class LoginRequest(BaseModel):
    email: EmailStr
    password: str
    dispositivo_confiable: bool = False
    fcm_token: Optional[str] = None


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    role: str
    permisos: List[str] = []
    permisos_personalizados: Optional[List[str]] = None


class RefreshRequest(BaseModel):
    refresh_token: Optional[str] = None


class FCMTokenRequest(BaseModel):
    fcm_token: str = Field(..., min_length=10, description="Token FCM del dispositivo Android")


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
    claves_permisos: List[str] = []


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
    rol_id: Optional[int] = None
    rol: Optional[str] = None
    permisos: Optional[List[str]] = None


class RegistroAuditoriaOut(BaseModel):
    id: int
    usuario_email: Optional[str]
    fecha_hora: datetime
    direccion_ip: str
    accion: str
    exitoso: bool

    model_config = ConfigDict(from_attributes=True)