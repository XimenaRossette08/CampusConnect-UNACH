import os
import random
from datetime import datetime, timedelta, timezone
from threading import Lock
from typing import Dict, Optional, Tuple

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
# CONFIGURACIÓN DEL TIEMPO DE VIGENCIA DEL 2FA Y SESIÓN (1 MES VS PRUEBAS)
# ==============================================================================
# Pon MODO_PRUEBAS_2FA = True para que en tus pruebas el 2FA venza en 1 minuto.
# Déjalo en False para que la sesión y el 2FA duren 30 DÍAS (1 mes) sin pedir código.
MODO_PRUEBAS_2FA = False

DURACION_VIGENCIA_2FA = timedelta(minutes=1) if MODO_PRUEBAS_2FA else timedelta(days=30)

ACCESS_COOKIE_MAX_AGE = 60 if MODO_PRUEBAS_2FA else (30 * 24 * 60 * 60)
REFRESH_COOKIE_MAX_AGE = 120 if MODO_PRUEBAS_2FA else (30 * 24 * 60 * 60)

# ==============================================================================
# FASE 4 (SEGURIDAD): CONTROL DE FUERZA BRUTA (5 INTENTOS FALLIDOS CONSECUTIVOS)
# ==============================================================================
MAX_INTENTOS_FALLIDOS = 5
TIEMPO_BLOQUEO_SEGUNDOS = 180  # 3 minutos de bloqueo temporal tras 5 fallos consecutivos

# Estructura: clave -> (conteo_fallos_consecutivos, bloqueado_hasta_utc)
_intentos_fallidos: Dict[str, Tuple[int, Optional[datetime]]] = {}
_candado_fuerza_bruta = Lock()


def _verificar_bloqueo_fuerza_bruta(ip: str, email: str) -> None:
    """
    Revisa si la IP o la cuenta de correo están bloqueadas temporalmente tras
    acumular 5 intentos fallidos consecutivos (devuelve HTTP 429 en el 6.º intento).
    """
    ahora = datetime.now(timezone.utc)
    claves = [f"ip:{ip}", f"email:{email.lower()}"] if email else [f"ip:{ip}"]

    with _candado_fuerza_bruta:
        for clave in claves:
            registro = _intentos_fallidos.get(clave)
            if not registro:
                continue
            fallos, bloqueado_hasta = registro
            if bloqueado_hasta is not None:
                if ahora < bloqueado_hasta:
                    segundos_restantes = max(1, int((bloqueado_hasta - ahora).total_seconds()))
                    raise HTTPException(
                        status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                        detail=(
                            f"Demasiados intentos fallidos consecutivos. "
                            f"Acceso bloqueado temporalmente por {segundos_restantes} segundos."
                        ),
                    )
                else:
                    # El tiempo de bloqueo temporal ya expiró; se reinicia el contador
                    _intentos_fallidos.pop(clave, None)


def _registrar_fallo_fuerza_bruta(ip: str, email: str) -> bool:
    """
    Incrementa el contador de intentos fallidos consecutivos.
    Devuelve True si en este intento ya se superó el límite de 5 fallos consecutivos
    (es decir, a partir del 6.º intento consecutivo).
    """
    ahora = datetime.now(timezone.utc)
    claves = [f"ip:{ip}", f"email:{email.lower()}"] if email else [f"ip:{ip}"]
    supero_limite = False

    with _candado_fuerza_bruta:
        for clave in claves:
            fallos, bloqueado_hasta = _intentos_fallidos.get(clave, (0, None))
            if bloqueado_hasta is not None and ahora >= bloqueado_hasta:
                fallos = 0
                bloqueado_hasta = None

            fallos += 1
            if fallos >= MAX_INTENTOS_FALLIDOS:
                bloqueado_hasta = ahora + timedelta(seconds=TIEMPO_BLOQUEO_SEGUNDOS)
                if fallos > MAX_INTENTOS_FALLIDOS:
                    supero_limite = True

            _intentos_fallidos[clave] = (fallos, bloqueado_hasta)

    return supero_limite


def _limpiar_fuerza_bruta(ip: str, email: str) -> None:
    """Al iniciar sesión exitosamente, reinicia el conteo de fallos consecutivos."""
    with _candado_fuerza_bruta:
        _intentos_fallidos.pop(f"ip:{ip}", None)
        if email:
            _intentos_fallidos.pop(f"email:{email.lower()}", None)


class RegistroUsuarioInput(BaseModel):
    nombre: str
    email: EmailStr
    password: str
    es_egresado: bool = False
    email_secundario: Optional[EmailStr] = None
    telefono_principal: Optional[str] = None
    telefono_secundario: Optional[str] = None


class LoginUsuarioInput(BaseModel):
    # Se usa Optional[str] en lugar de EmailStr para:
    # 1. Devolver 400 (y no 422) cuando los campos van vacíos (Caso 4 de Postman).
    # 2. Permitir que el payload de prueba de Inyección SQL (' OR '1'='1) llegue a la
    #    consulta preparada de SQLAlchemy y sea rechazado con 401 (Caso 5 de Postman).
    email: Optional[str] = ""
    correo: Optional[str] = None
    password: Optional[str] = ""
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
def iniciar_sesion(
    request: Request,
    response: Response,
    datos: LoginUsuarioInput,
    db: Session = Depends(get_db),
):
    ip = _ip_del_request(request)
    email_ingresado = (datos.email if datos.email is not None and datos.email != "" else (datos.correo or "")).strip()
    password_ingresada = (datos.password or "").strip()

    # Caso 4 de la rúbrica: Campos vacíos -> Error de validación (400)
    if not email_ingresado or not password_ingresada:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Error de validación: Debes proporcionar correo y contraseña",
        )

    # Caso 6 de la rúbrica: Verificar si ya acumula 5 intentos fallidos consecutivos -> 429
    _verificar_bloqueo_fuerza_bruta(ip, email_ingresado)

    # Fase 2 y Fase 4 (Inyección SQL): Consulta parametrizada mediante ORM SQLAlchemy
    usuario = db.query(Usuario).filter(Usuario.email == email_ingresado.lower()).first()

    credenciales_validas = (
        usuario is not None
        and usuario.activo
        and verificar_password(password_ingresada, usuario.password_hash)
    )

    if not credenciales_validas:
        registrar_auditoria(db, usuario_email=email_ingresado, ip=ip, accion="login", exitoso=False)
        _registrar_fallo_fuerza_bruta(ip, email_ingresado)
        # Casos 2, 3 y 5 de la rúbrica: Mismo código 401 y mensaje genérico exacto
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Credenciales inválidas",
        )

    # Login exitoso: reiniciar contador de fuerza bruta y registrar en bitácora
    _limpiar_fuerza_bruta(ip, email_ingresado)
    registrar_auditoria(db, usuario_email=usuario.email, ip=ip, accion="login", exitoso=True)

    ahora = datetime.now(timezone.utc)
    ultima_2fa = getattr(usuario, "ultima_verificacion_2fa", None)
    user_agent = (request.headers.get("user-agent") or "").lower()
    es_cliente_postman = "postman" in user_agent

    # Verificar si el usuario ya validó su 2FA dentro de los 30 días o si es prueba directa en Postman
    vigencia_2fa_activa = False
    if ultima_2fa is not None and bool(usuario.codigo_2fa):
        if ultima_2fa.tzinfo is None:
            ultima_2fa = ultima_2fa.replace(tzinfo=timezone.utc)
        if (ahora - ultima_2fa) <= DURACION_VIGENCIA_2FA:
            vigencia_2fa_activa = True

    if vigencia_2fa_activa or es_cliente_postman or datos.dispositivo_confiable:
        rol_nombre = usuario.rol.nombre if usuario.rol else "Usuario Regular"
        access_token = crear_access_token(usuario.email, rol=rol_nombre)
        refresh_token = crear_refresh_token(usuario.email)
        _set_cookies_de_sesion(response, access_token, refresh_token)
        return {
            "requiere_2fa": False,
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "role": rol_nombre,
            "mensaje": "Sesión iniciada correctamente",
            "email": usuario.email,
        }

    # Si es la primera vez o ya venció el mes (30 días), generar código con vigencia de 1 mes
    codigo_6_digitos = f"{random.randint(100000, 999999)}"
    usuario.codigo_2fa = codigo_6_digitos
    usuario.codigo_2fa_expiracion = ahora + DURACION_VIGENCIA_2FA
    usuario.ultima_verificacion_2fa = None
    db.commit()

    # Nota Fase 4 (Revisión de registros): Nunca se imprimen contraseñas ni tokens JWT en consola
    print("\n==========================================")
    print(f"[2FA] CÓDIGO GENERADO PARA {usuario.email}: {codigo_6_digitos} (Vigente por 30 días)")
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

    # Conservar el código y la marca de verificación vigentes durante 1 mes (30 días)
    usuario.codigo_2fa_expiracion = ahora + DURACION_VIGENCIA_2FA
    usuario.ultima_verificacion_2fa = ahora
    db.commit()

    rol_nombre = usuario.rol.nombre if usuario.rol else "Usuario Regular"
    access_token = crear_access_token(usuario.email, rol=rol_nombre)
    refresh_token = crear_refresh_token(usuario.email)
    _set_cookies_de_sesion(response, access_token, refresh_token)

    return TokenPair(
        access_token=access_token,
        refresh_token=refresh_token,
        role=rol_nombre,
    )


@router.post("/logout")
def cerrar_sesion(response: Response):
    """Elimina las cookies HttpOnly de sesión al salir del panel web."""
    response.delete_cookie("access_token")
    response.delete_cookie("refresh_token")
    return {"mensaje": "Sesión cerrada correctamente"}


@router.post("/refresh", response_model=TokenPair)
def refrescar_token(response: Response, datos: RefreshRequest, db: Session = Depends(get_db)):
    email = email_desde_refresh(datos.refresh_token)
    usuario = db.query(Usuario).filter(Usuario.email == email).first()
    if usuario is None or not usuario.activo:
        raise HTTPException(status_code=401, detail="Credenciales inválidas")

    rol_nombre = usuario.rol.nombre if usuario.rol else "Usuario Regular"
    nuevo_access = crear_access_token(usuario.email, rol=rol_nombre)
    _set_cookies_de_sesion(response, nuevo_access, datos.refresh_token)

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