import os
from datetime import datetime, timedelta, timezone

import bcrypt
from fastapi import Depends, HTTPException, Request, status
from jose import JWTError, jwt
from sqlalchemy.orm import Session

from .database import get_db
from .models_auth import Usuario

JWT_SECRET = os.getenv("JWT_SECRET", "cambia-esto-en-produccion")
JWT_ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = int(os.getenv("JWT_ACCESS_EXPIRE_MINUTES", "20"))
REFRESH_TOKEN_EXPIRE_DAYS = int(os.getenv("JWT_REFRESH_EXPIRE_DAYS", "7"))

_CREDENCIALES_INVALIDAS = HTTPException(
    status_code=status.HTTP_401_UNAUTHORIZED,
    detail="Credenciales inválidas",
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


def _crear_token(email: str, tipo: str, expira_en: timedelta) -> str:
    ahora = datetime.now(timezone.utc)
    payload = {"sub": email, "type": tipo, "iat": ahora, "exp": ahora + expira_en}
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)


def crear_access_token(email: str) -> str:
    return _crear_token(email, "access", timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES))


def crear_refresh_token(email: str) -> str:
    return _crear_token(email, "refresh", timedelta(days=REFRESH_TOKEN_EXPIRE_DAYS))


def crear_token_recuperacion(email: str) -> str:
    return _crear_token(email, "reset", timedelta(minutes=30))


def _decodificar(token: str, tipo_esperado: str) -> str:
    """Devuelve el email (sub) si el token es válido y del tipo correcto; si no, 401."""
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    except JWTError:
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
    Acepta el token vía encabezado Authorization: Bearer <token> (usado por la
    app móvil) o vía cookie httpOnly 'access_token' (recomendado para el
    frontend web, para evitar guardar el JWT en localStorage y exponerlo a XSS).
    """
    encabezado = request.headers.get("Authorization")
    if encabezado and encabezado.lower().startswith("bearer "):
        return encabezado.split(" ", 1)[1]

    token_cookie = request.cookies.get("access_token")
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


def requiere_permiso(*claves_permitidas: str):
    """
    Verifica el permiso contra la base de datos (no contra el token), para
    que un cambio hecho desde el panel de administración aplique de inmediato,
    sin esperar a que el usuario vuelva a iniciar sesión.
    """

    def _verificador(usuario: Usuario = Depends(get_current_user)) -> Usuario:
        claves_del_usuario = {permiso.clave for permiso in usuario.rol.permisos}
        if not claves_del_usuario.intersection(claves_permitidas):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="No tienes permisos para esta acción",
            )
        return usuario

    return _verificador