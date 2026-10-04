import os
import random
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from pydantic import BaseModel, EmailStr
from slowapi import Limiter
from slowapi.util import get_remote_address
from sqlalchemy.orm import Session

from ..auditoria import registrar_auditoria
from ..database import get_db
from ..email_utils import enviar_correo_codigo_2fa
from ..models_auth import Rol, Usuario
from ..schemas_auth import (
    CambiarPasswordRequest,
    LoginRequest,
    RefreshRequest,
    RestablecerPasswordRequest,
    SolicitarRecuperacionRequest,
    TokenPair,
    UsuarioCreate,
    UsuarioOut,
)
from ..security import (
    crear_access_token,
    crear_refresh_token,
    crear_token_recuperacion,
    email_desde_refresh,
    email_desde_token_recuperacion,
    get_current_user,
    hash_password,
    verificar_password,
)

router = APIRouter(prefix="/auth", tags=["autenticación"])
limiter = Limiter(key_func=get_remote_address)

ACCESS_COOKIE_MAX_AGE = 20 * 60
REFRESH_COOKIE_MAX_AGE = 7 * 24 * 60 * 60


class Verificar2FARequest(BaseModel):
    email: EmailStr
    codigo: str


def _ip_del_request(request: Request) -> str:
    return request.client.host if request.client else "desconocida"


def _set_cookies_de_sesion(response: Response, access_token: str, refresh_token: str) -> None:
    es_produccion = os.getenv("ENVIRONMENT", "development").lower() == "production"

    response.set_cookie(
        "access_token",
        access_token,
        httponly=True,
        secure=es_produccion,
        samesite="lax",
        max_age=ACCESS_COOKIE_MAX_AGE,
    )
    response.set_cookie(
        "refresh_token",
        refresh_token,
        httponly=True,
        secure=es_produccion,
        samesite="lax",
        max_age=REFRESH_COOKIE_MAX_AGE,
    )


@router.post("/registro", response_model=UsuarioOut, status_code=status.HTTP_201_CREATED)
def registrar_usuario(datos: UsuarioCreate, db: Session = Depends(get_db)):
    ya_existe = db.query(Usuario).filter(Usuario.email == datos.email).first()
    if ya_existe:
        raise HTTPException(status_code=400, detail="No fue posible completar el registro")

    rol_por_defecto = db.query(Rol).filter(Rol.nombre == "Usuario Regular").first()
    if rol_por_defecto is None:
        raise HTTPException(status_code=500, detail="No fue posible completar el registro")

    nuevo_usuario = Usuario(
        nombre=datos.nombre,
        email=datos.email,
        password_hash=hash_password(datos.password),
        rol_id=rol_por_defecto.id,
    )
    db.add(nuevo_usuario)
    db.commit()
    db.refresh(nuevo_usuario)
    return UsuarioOut.desde_usuario(nuevo_usuario)


@router.post("/login")
@limiter.limit("5/minute")
def iniciar_sesion(
    request: Request,
    datos: LoginRequest,
    db: Session = Depends(get_db),
):
    ip = _ip_del_request(request)
    usuario = db.query(Usuario).filter(Usuario.email == datos.email).first()

    credenciales_validas = (
        usuario is not None
        and usuario.activo
        and verificar_password(datos.password, usuario.password_hash)
    )

    if not credenciales_validas:
        registrar_auditoria(db, usuario_email=datos.email, ip=ip, accion="login", exitoso=False)
        raise HTTPException(status_code=401, detail="Correo o contraseña incorrectos")

    registrar_auditoria(db, usuario_email=usuario.email, ip=ip, accion="login", exitoso=True)

    # Generar código de 6 dígitos y expiración a 5 minutos
    codigo_6_digitos = f"{random.randint(100000, 999999)}"
    usuario.codigo_2fa = codigo_6_digitos
    usuario.codigo_2fa_expiracion = datetime.now(timezone.utc) + timedelta(minutes=5)
    db.commit()

    # Muestra el código en la consola de Uvicorn para facilitar las pruebas locales
    print("\n==========================================")
    print(f"[2FA] CÓDIGO GENERADO PARA {usuario.email}: {codigo_6_digitos}")
    print("==========================================\n")

    # Envío del correo por SMTP
    try:
        enviar_correo_codigo_2fa(usuario.email, codigo_6_digitos)
    except Exception as e:
        print(f"[2FA] Error al enviar correo: {e}")

    return {
        "requiere_2fa": True,
        "mensaje": "Se ha enviado un código de verificación a tu correo institucional",
        "email": usuario.email,
    }


@router.post("/verificar-2fa", response_model=TokenPair)
@limiter.limit("5/minute")
def verificar_2fa(
    request: Request,
    response: Response,
    datos: Verificar2FARequest,
    db: Session = Depends(get_db),
):
    usuario = db.query(Usuario).filter(Usuario.email == datos.email).first()

    if not usuario or not usuario.activo or not usuario.codigo_2fa:
        raise HTTPException(status_code=400, detail="Petición de verificación inválida")

    if usuario.codigo_2fa != datos.codigo.strip():
        raise HTTPException(status_code=400, detail="Código de seguridad incorrecto")

    ahora = datetime.now(timezone.utc)
    expiracion = usuario.codigo_2fa_expiracion
    if expiracion:
        if expiracion.tzinfo is None:
            expiracion = expiracion.replace(tzinfo=timezone.utc)
        if ahora > expiracion:
            raise HTTPException(status_code=400, detail="El código ha expirado, solicita uno nuevo")

    # Limpiar código tras verificación exitosa
    usuario.codigo_2fa = None
    usuario.codigo_2fa_expiracion = None
    db.commit()

    # Emitir tokens y configurar cookies
    access_token = crear_access_token(usuario.email)
    refresh_token = crear_refresh_token(usuario.email)
    _set_cookies_de_sesion(response, access_token, refresh_token)

    rol_nombre = usuario.rol.nombre if usuario.rol else "Usuario Regular"

    return TokenPair(
        access_token=access_token,
        refresh_token=refresh_token,
        role=rol_nombre,
    )


@router.post("/refresh", response_model=TokenPair)
def refrescar_token(response: Response, datos: RefreshRequest, db: Session = Depends(get_db)):
    email = email_desde_refresh(datos.refresh_token)
    usuario = db.query(Usuario).filter(Usuario.email == email).first()
    if usuario is None or not usuario.activo:
        raise HTTPException(status_code=401, detail="Credenciales inválidas")

    nuevo_access = crear_access_token(usuario.email)
    _set_cookies_de_sesion(response, nuevo_access, datos.refresh_token)

    rol_nombre = usuario.rol.nombre if usuario.rol else "Usuario Regular"

    return TokenPair(
        access_token=nuevo_access,
        refresh_token=datos.refresh_token,
        role=rol_nombre,
    )


@router.post("/cambiar-password")
def cambiar_password(
    datos: CambiarPasswordRequest,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if not verificar_password(datos.password_actual, usuario.password_hash):
        raise HTTPException(status_code=400, detail="La contraseña actual no es correcta")

    usuario.password_hash = hash_password(datos.password_nueva)
    db.commit()
    return {"mensaje": "Contraseña actualizada"}


@router.post("/recuperar-password")
def solicitar_recuperacion(datos: SolicitarRecuperacionRequest, db: Session = Depends(get_db)):
    usuario = db.query(Usuario).filter(Usuario.email == datos.email).first()

    if usuario is None:
        return {"mensaje": "Si el correo existe, se enviarán instrucciones"}

    token_recuperacion = crear_token_recuperacion(usuario.email)
    return {
        "mensaje": "Si el correo existe, se enviarán instrucciones",
        "token_solo_para_pruebas": token_recuperacion,
    }


@router.post("/restablecer-password")
def restablecer_password(datos: RestablecerPasswordRequest, db: Session = Depends(get_db)):
    email = email_desde_token_recuperacion(datos.token)
    usuario = db.query(Usuario).filter(Usuario.email == email).first()
    if usuario is None:
        raise HTTPException(status_code=400, detail="Token inválido")

    usuario.password_hash = hash_password(datos.password_nueva)
    db.commit()
    return {"mensaje": "Contraseña restablecida"}


@router.get("/me", response_model=UsuarioOut)
def obtener_perfil(usuario: Usuario = Depends(get_current_user)):
    return UsuarioOut.desde_usuario(usuario)