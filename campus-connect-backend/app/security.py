import os
from datetime import datetime, timedelta, timezone
from typing import Optional

import bcrypt
from fastapi import Depends, HTTPException, Request, status
from jose import JWTError, jwt
from sqlalchemy.orm import Session

from .database import get_db
from .models_auth import Usuario

JWT_SECRET = os.getenv("JWT_SECRET", "cambia-esto-en-produccion")
JWT_ALGORITHM = "HS256"

# Exigencia 3 del PDF: Expiración corta (20 minutos por defecto, dentro del rango 15-30 min)
ACCESS_TOKEN_EXPIRE_MINUTES = int(os.getenv("JWT_ACCESS_EXPIRE_MINUTES", "20"))
REFRESH_TOKEN_EXPIRE_DAYS = int(os.getenv("JWT_REFRESH_EXPIRE_DAYS", "7"))

_CREDENCIALES_INVALIDAS = HTTPException(
    status_code=status.HTTP_401_UNAUTHORIZED,
    detail="Credenciales inválidas o sesión expirada",
    headers={"WWW-Authenticate": "Bearer"},
)


def hash_password(password: str) -> str:
    pwd_bytes = password.encode("utf-8")
    salt = bcrypt.gensalt()
    return bcrypt.hashpw(pwd_bytes, salt).decode("utf-8")


def verificar_password(password: str, password_hash: str) -> bool:
    return bcrypt.checkpw(
        password.encode("utf-8"),
        password_hash.encode("utf-8")
    )


def _crear_token(email: str, tipo: str, expira_en: timedelta, rol: Optional[str] = None) -> str:
    ahora = datetime.now(timezone.utc)
    payload = {"sub": email, "type": tipo, "iat": ahora, "exp": ahora + expira_en}
    if rol:
        payload["role"] = rol
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)


def crear_access_token(email: str, rol: Optional[str] = None) -> str:
    return _crear_token(email, "access", timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES), rol=rol)


def crear_refresh_token(email: str) -> str:
    return _crear_token(email, "refresh", timedelta(days=REFRESH_TOKEN_EXPIRE_DAYS))


def crear_token_recuperacion(email: str) -> str:
    return _crear_token(email, "reset", timedelta(minutes=30))


def _decodificar(token: str, tipo_esperado: str) -> str:
    """Devuelve el email (sub) si el token es válido y del tipo correcto; si no, 401."""
    # Evita que un token nulo o vacío rompa jose.jwt con AttributeError
    if not token or not isinstance(token, str) or not token.strip():
        raise _CREDENCIALES_INVALIDAS

    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    except Exception:
        raise _CREDENCIALES_INVALIDAS

    if payload.get("type") != tipo_esperado:
        raise _CREDENCIALES_INVALIDAS

    email = payload.get("sub")
    if not email:
        raise _CREDENCIALES_INVALIDAS

    return email


def email_desde_refresh(refresh_token: str) -> str:
    return _decodificar(refresh_token, "refresh")


def email_desde_token_recuperacion(token: str) -> str:
    return _decodificar(token, "reset")


def _obtener_token_del_request(request: Request) -> str:
    """
    Exigencia 2 del PDF:
    1. Acepta la cookie HttpOnly 'access_token' enviada por el Panel Web (evitando localStorage).
    2. Acepta el encabezado Authorization: Bearer <token> usado por la App Android.
    """
    token_cookie = request.cookies.get("access_token")
    if token_cookie:
        return token_cookie

    encabezado = request.headers.get("Authorization")
    if encabezado and encabezado.lower().startswith("bearer "):
        candidato = encabezado.split(" ", 1)[1].strip()
        if candidato and candidato != "null" and candidato != "undefined":
            return candidato

    raise _CREDENCIALES_INVALIDAS


def obtener_refresh_token_del_request(request: Request, token_body: Optional[str] = None) -> str:
    """
    Obtiene el refresh_token ya sea desde la Cookie HttpOnly (Panel Web)
    o desde el cuerpo JSON de la petición (App Android).
    """
    if token_body and token_body.strip():
        return token_body.strip()
    token_cookie = request.cookies.get("refresh_token")
    if token_cookie:
        return token_cookie
    raise _CREDENCIALES_INVALIDAS


def get_current_user(request: Request, db: Session = Depends(get_db)) -> Usuario:
    token = _obtener_token_del_request(request)
    email = _decodificar(token, "access")

    usuario = db.query(Usuario).filter(Usuario.email == email).first()
    if usuario is None or not usuario.activo:
        raise _CREDENCIALES_INVALIDAS
    return usuario


def verificar_rol_admin(usuario: Usuario = Depends(get_current_user)) -> Usuario:
    """
    Vulnerabilidad 1 del Profe (Control de Acceso Crítico):
    Verifica que el usuario autenticado tenga rol de Administrador o permisos de gestión.
    Si un alumno o usuario regular intenta invocar un endpoint administrativo directamente,
    rechaza la petición con 403 Forbidden antes de tocar la base de datos.
    """
    nombre_rol = (usuario.rol.nombre if usuario.rol else "").strip().lower()
    claves = {p.clave for p in (usuario.rol.permisos if usuario.rol else [])}
    if nombre_rol == "administrador" or "usuarios:gestionar" in claves or "roles:gestionar" in claves:
        return usuario
    raise HTTPException(
        status_code=status.HTTP_403_FORBIDDEN,
        detail="403 Forbidden: Se requiere rol de Administrador para realizar esta acción.",
    )


def requiere_permiso(*claves_permitidas: str):
    """
    Vulnerabilidad 1 del Profe (Control de Acceso Crítico):
    Verifica en cada petición que el rol del usuario tenga el permiso requerido
    (ej. 'eventos:escribir', 'eventos:eliminar', 'usuarios:gestionar').
    Si un usuario normal intenta borrar o modificar datos, devuelve 403 Forbidden.
    """

    def _verificador(usuario: Usuario = Depends(get_current_user)) -> Usuario:
        nombre_rol = (usuario.rol.nombre if usuario.rol else "").strip().lower()
        if nombre_rol == "administrador":
            return usuario
        claves_del_usuario = {permiso.clave for permiso in (usuario.rol.permisos if usuario.rol else [])}
        if not claves_del_usuario.intersection(claves_permitidas):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="403 Forbidden: No tienes permisos para modificar o eliminar este recurso.",
            )
        return usuario

    return _verificador
