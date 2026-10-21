"""
================================================================================
PARCHE PARA FASTAPI: EGRESADOS (2 CORREOS Y 2 TELÉFONOS) + VIGENCIA DE 1 MES 2FA
Integra estos fragmentos en tus archivos schemas.py, models.py y auth.py
================================================================================
"""

from datetime import datetime, timedelta
from typing import Optional
from pydantic import BaseModel, EmailStr, field_validator
from sqlalchemy import Column, Integer, String, Boolean

# ------------------------------------------------------------------------------
# 1. CONFIGURACIÓN DEL TIEMPO DE SESIÓN Y 2FA EN FASTAPI (PRODUCCIÓN VS PRUEBAS)
# ------------------------------------------------------------------------------
# Pon MODO_PRUEBAS_TOKEN = True para que el token dure 1 minuto en tus pruebas,
# o déjalo en False para que dure 30 DÍAS (1 mes = 43,200 minutos).
MODO_PRUEBAS_TOKEN = False

ACCESS_TOKEN_EXPIRE_MINUTES = 1 if MODO_PRUEBAS_TOKEN else (30 * 24 * 60)  # 43,200 min = 30 días


# ------------------------------------------------------------------------------
# 2. COLUMNAS PARA TU MODELO Usuario EN models.py (SQLAlchemy / PostgreSQL)
# ------------------------------------------------------------------------------
"""
Agrega estas 4 columnas a tu clase Usuario en models.py:

    es_egresado = Column(Boolean, default=False, nullable=False)
    email_secundario = Column(String(150), nullable=True)
    telefono_principal = Column(String(20), nullable=True)
    telefono_secundario = Column(String(20), nullable=True)

Y en pgAdmin ejecuta este ALTER TABLE una sola vez:
    ALTER TABLE usuarios
        ADD COLUMN IF NOT EXISTS es_egresado BOOLEAN NOT NULL DEFAULT FALSE,
        ADD COLUMN IF NOT EXISTS email_secundario VARCHAR(150),
        ADD COLUMN IF NOT EXISTS telefono_principal VARCHAR(20),
        ADD COLUMN IF NOT EXISTS telefono_secundario VARCHAR(20);
"""


# ------------------------------------------------------------------------------
# 3. ESQUEMA DE REGISTRO Y LOGIN EN schemas.py
# ------------------------------------------------------------------------------
class RegistroUsuarioSchema(BaseModel):
    nombre: str
    email: EmailStr
    password: str
    es_egresado: bool = False
    email_secundario: Optional[EmailStr] = None
    telefono_principal: Optional[str] = None
    telefono_secundario: Optional[str] = None

    @field_validator("email_secundario")
    @classmethod
    def validar_segundo_correo(cls, v, info):
        datos = info.data
        if datos.get("es_egresado"):
            if not v:
                raise ValueError("Los alumnos egresados deben registrar un segundo correo electrónico.")
            if v.lower().strip() == datos.get("email", "").lower().strip():
                raise ValueError("Los dos correos electrónicos deben ser diferentes.")
        return v

    @field_validator("telefono_secundario")
    @classmethod
    def validar_telefonos_egresado(cls, v, info):
        datos = info.data
        if datos.get("es_egresado"):
            t1 = (datos.get("telefono_principal") or "").strip()
            t2 = (v or "").strip()
            if len(t1) < 10 or len(t2) < 10:
                raise ValueError("Los alumnos egresados deben registrar dos números telefónicos de 10 dígitos.")
            if t1 == t2:
                raise ValueError("Los dos números telefónicos deben ser diferentes.")
        return v


class LoginUsuarioSchema(BaseModel):
    email: EmailStr
    password: str
    dispositivo_confiable: bool = False
