import os
import random
from datetime import datetime, timedelta, timezone
from typing import Optional

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
    RefreshRequest,
    RestablecerPasswordRequest,
    SolicitarRecuperacionRequest,
    TokenPair,
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

# ==============================================================================
# CONFIGURACIÓN DE COOKIES DE SESIÓN
# ==============================================================================
MODO_PRUEBAS_2FA = False

ACCESS_COOKIE_MAX_AGE = 60 if MODO_PRUEBAS_2FA else (30 * 24 * 60 * 60)
REFRESH_COOKIE_MAX_AGE = 120 if MODO_PRUEBAS_2FA else (30 * 24 * 60 * 60)


class RegistroUsuarioInput(BaseModel):
    nombre: str
    email: EmailStr
    password: str
    es_egresado: bool = False
    email_secundario: Optional[EmailStr] = None
    telefono_principal: Optional[str] = None
    telefono_secundario: Optional[str] = None


class LoginUsuarioInput(BaseModel):
    email: EmailStr
    password: str
    dispositivo_confiable: bool = False


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
def registrar_usuario(datos: RegistroUsuarioInput, db: Session = Depends(get_db)):
    email_principal = datos.email.strip().lower()
    ya_existe = db.query(Usuario).filter(Usuario.email == email_principal).first()
    if ya_existe:
        raise HTTPException(status_code=400, detail="El correo electrónico ya está registrado")

    email_sec = None
    tel_1 = None
    tel_2 = None

    # Validaciones específicas cuando se registra un Alumno Egresado
    if datos.es_egresado:
        if not datos.email_secundario:
            raise HTTPException(status_code=400, detail="Los alumnos egresados deben proporcionar dos correos electrónicos")
        email_sec = datos.email_secundario.strip().lower()
        if email_sec == email_principal:
            raise HTTPException(status_code=400, detail="Los dos correos electrónicos del alumno egresado deben ser diferentes")

        tel_1 = (datos.telefono_principal or "").strip()
        tel_2 = (datos.telefono_secundario or "").strip()
        if len(tel_1) < 10 or len(tel_2) < 10:
            raise HTTPException(status_code=400, detail="Los alumnos egresados deben proporcionar dos números telefónicos de 10 dígitos")
        if tel_1 == tel_2:
            raise HTTPException(status_code=400, detail="Los dos números telefónicos del alumno egresado deben ser diferentes")

    rol_por_defecto = db.query(Rol).filter(Rol.nombre == "Usuario Regular").first()
    if rol_por_defecto is None:
        raise HTTPException(status_code=500, detail="No fue posible completar el registro")

    nuevo_usuario = Usuario(
        nombre=datos.nombre.strip(),
        email=email_principal,
        password_hash=hash_password(datos.password),
        rol_id=rol_por_defecto.id,
        es_egresado=datos.es_egresado,
        email_secundario=email_sec,
        telefono_principal=tel_1,
        telefono_secundario=tel_2,
    )
    db.add(nuevo_usuario)
    db.commit()
    db.refresh(nuevo_usuario)
    return UsuarioOut.desde_usuario(nuevo_usuario)


@router.post("/login")
@limiter.limit("10/minute")
def iniciar_sesion(
    request: Request,
    response: Response,
    datos: LoginUsuarioInput,
    db: Session = Depends(get_db),
):
    ip = _ip_del_request(request)
    usuario = db.query(Usuario).filter(Usuario.email == datos.email.strip().lower()).first()

    credenciales_validas = (
        usuario is not None
        and usuario.activo
        and verificar_password(datos.password, usuario.password_hash)
    )

    if not credenciales_validas:
        registrar_auditoria(db, usuario_email=datos.email, ip=ip, accion="login", exitoso=False)
        raise HTTPException(status_code=401, detail="Correo o contraseña incorrectos")

    registrar_auditoria(db, usuario_email=usuario.email, ip=ip, accion="login", exitoso=True)

    # Generar siempre un nuevo código 2FA de 6 dígitos en cada inicio de sesión
    ahora = datetime.now(timezone.utc)
    codigo_6_digitos = f"{random.randint(100000, 999999)}"
    usuario.codigo_2fa = codigo_6_digitos
    usuario.codigo_2fa_expiracion = ahora + timedelta(minutes=5)
    db.commit()

    print("\n==========================================")
    print(f"[2FA] CÓDIGO GENERADO PARA {usuario.email}: {codigo_6_digitos}")
    print("==========================================\n")

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
@limiter.limit("10/minute")
def verificar_2fa(
    request: Request,
    response: Response,
    datos: Verificar2FARequest,
    db: Session = Depends(get_db),
):
    usuario = db.query(Usuario).filter(Usuario.email == datos.email.strip().lower()).first()

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
    usuario.ultima_verificacion_2fa = ahora
    db.commit()

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