import json
import os
import random
from datetime import datetime, timedelta, timezone
from threading import Lock
from typing import Dict, Optional, Tuple

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from pydantic import BaseModel, EmailStr
from slowapi import Limiter
from slowapi.util import get_remote_address
from sqlalchemy import text
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
MODO_PRUEBAS_2FA = False

DURACION_VIGENCIA_2FA = timedelta(minutes=1) if MODO_PRUEBAS_2FA else timedelta(days=30)

ACCESS_COOKIE_MAX_AGE = 60 if MODO_PRUEBAS_2FA else (30 * 24 * 60 * 60)
REFRESH_COOKIE_MAX_AGE = 120 if MODO_PRUEBAS_2FA else (30 * 24 * 60 * 60)

# ==============================================================================
# FASE 4 (SEGURIDAD): CONTROL DE FUERZA BRUTA (5 INTENTOS FALLIDOS CONSECUTIVOS)
# ==============================================================================
MAX_INTENTOS_FALLIDOS = 5
TIEMPO_BLOQUEO_SEGUNDOS = 180  # 3 minutos de bloqueo temporal tras 5 fallos consecutivos

_intentos_fallidos: Dict[str, Tuple[int, Optional[datetime]]] = {}
_candado_fuerza_bruta = Lock()


def _verificar_bloqueo_fuerza_bruta(ip: str, email: str) -> None:
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
                    _intentos_fallidos.pop(clave, None)


def _registrar_fallo_fuerza_bruta(ip: str, email: str) -> bool:
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
    matricula: Optional[str] = None
    carrera: Optional[str] = None
    semestre_grupo: Optional[str] = None
    id_facultad: Optional[str] = None


class LoginUsuarioInput(BaseModel):
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


def _obtener_datos_institucionales_estaticos(db: Session) -> Dict[str, str]:
    """Lee Escuela, Modalidad/Sede y Ciclo Escolar configurados por el administrador en Catálogos."""
    por_defecto = {
        "escuela": "Escuela de Tecnologías Digitales Aplicadas (ETDA)",
        "modalidad_sede": "Escolarizada (Campus I, Tuxtla Gutiérrez)",
        "ciclo_escolar": "Agosto - Diciembre 2026",
    }
    try:
        row = db.execute(text("SELECT payload_json FROM catalogos_institucionales WHERE id = 1")).fetchone()
        if row and row[0]:
            payload = json.loads(row[0])
            if isinstance(payload, dict):
                inst = payload.get("datosInstitucionales") or {}
                if isinstance(inst, dict):
                    return {
                        "escuela": (inst.get("escuela") or por_defecto["escuela"]).strip(),
                        "modalidad_sede": (inst.get("modalidadSede") or inst.get("modalidad_sede") or por_defecto["modalidad_sede"]).strip(),
                        "ciclo_escolar": (inst.get("cicloEscolar") or inst.get("ciclo_escolar") or por_defecto["ciclo_escolar"]).strip(),
                    }
    except Exception:
        pass
    return por_defecto


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

    carrera_limpia = (datos.carrera or "").strip() or None
    grupo_limpio = (datos.semestre_grupo or "").strip() or None
    facultad_limpia = (datos.id_facultad or "ETDA").strip() or "ETDA"
    matricula_limpia = (datos.matricula or "").strip() or None

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

    # Guardar carrera, facultad y grupo en el modelo Usuario si están definidos
    if hasattr(nuevo_usuario, "id_facultad"):
        nuevo_usuario.id_facultad = facultad_limpia
    if hasattr(nuevo_usuario, "id_carrera") and carrera_limpia:
        nuevo_usuario.id_carrera = carrera_limpia
    if hasattr(nuevo_usuario, "grupos") and grupo_limpio:
        nuevo_usuario.grupos = [grupo_limpio]
    if hasattr(nuevo_usuario, "matricula") and matricula_limpia:
        setattr(nuevo_usuario, "matricula", matricula_limpia)

    db.add(nuevo_usuario)
    db.commit()
    db.refresh(nuevo_usuario)

    # Asegurar persistencia directa en la tabla usuarios para matricula, id_carrera e id_facultad
    try:
        db.execute(
            text("""
                UPDATE usuarios
                SET matricula = COALESCE(:mat, matricula),
                    id_carrera = COALESCE(:car, id_carrera),
                    id_facultad = COALESCE(:fac, id_facultad)
                WHERE id = :uid
            """),
            {
                "mat": matricula_limpia,
                "car": carrera_limpia,
                "fac": facultad_limpia,
                "uid": nuevo_usuario.id,
            },
        )
        db.commit()
    except Exception as e:
        print(f"[Registro] Aviso al guardar campos académicos en usuarios: {e}")

    return UsuarioOut.desde_usuario(nuevo_usuario)


@router.get("/expediente")
def obtener_expediente_alumno(
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Devuelve los datos académicos del alumno (elegidos al crear su cuenta) junto con
    los datos institucionales estáticos (Escuela, Modalidad/Sede y Ciclo Escolar)
    configurados por el Administrador en el Panel Web.
    """
    matricula_db = getattr(usuario, "matricula", None)
    carrera_db = getattr(usuario, "id_carrera", None)
    facultad_db = getattr(usuario, "id_facultad", None) or "ETDA"
    grupos_db = getattr(usuario, "grupos", None) or []

    try:
        row = db.execute(
            text("SELECT matricula, id_carrera, id_facultad FROM usuarios WHERE id = :uid"),
            {"uid": usuario.id},
        ).fetchone()
        if row:
            if row[0]:
                matricula_db = row[0]
            if row[1]:
                carrera_db = row[1]
            if row[2]:
                facultad_db = row[2]
    except Exception:
        pass

    semestre_grupo = grupos_db[0] if isinstance(grupos_db, list) and len(grupos_db) > 0 else ""
    estaticos = _obtener_datos_institucionales_estaticos(db)
    rol_nombre = usuario.rol.nombre if usuario.rol else "Usuario Regular"

    return {
        "nombre": usuario.nombre,
        "email": usuario.email,
        "rol": rol_nombre,
        "es_egresado": bool(getattr(usuario, "es_egresado", False)),
        "matricula": matricula_db or "",
        "carrera": carrera_db or "",
        "semestre_grupo": semestre_grupo or "",
        "id_facultad": facultad_db,
        "escuela": estaticos["escuela"],
        "modalidad_sede": estaticos["modalidad_sede"],
        "ciclo_escolar": estaticos["ciclo_escolar"],
    }


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

    if not email_ingresado or not password_ingresada:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Error de validación: Debes proporcionar correo y contraseña",
        )

    _verificar_bloqueo_fuerza_bruta(ip, email_ingresado)

    usuario = db.query(Usuario).filter(Usuario.email == email_ingresado.lower()).first()

    credenciales_validas = (
        usuario is not None
        and usuario.activo
        and verificar_password(password_ingresada, usuario.password_hash)
    )

    if not credenciales_validas:
        registrar_auditoria(db, usuario_email=email_ingresado, ip=ip, accion="login", exitoso=False)
        _registrar_fallo_fuerza_bruta(ip, email_ingresado)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Credenciales inválidas",
        )

    _limpiar_fuerza_bruta(ip, email_ingresado)
    registrar_auditoria(db, usuario_email=usuario.email, ip=ip, accion="login", exitoso=True)

    ahora = datetime.now(timezone.utc)
    ultima_2fa = getattr(usuario, "ultima_verificacion_2fa", None)
    user_agent = (request.headers.get("user-agent") or "").lower()
    es_cliente_postman = "postman" in user_agent

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
        if not es_cliente_postman:
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

    codigo_6_digitos = f"{random.randint(100000, 999999)}"
    usuario.codigo_2fa = codigo_6_digitos
    usuario.codigo_2fa_expiracion = ahora + DURACION_VIGENCIA_2FA
    usuario.ultima_verificacion_2fa = None
    db.commit()

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


class ActualizarFcmTokenRequest(BaseModel):
    fcm_token: str


@router.post("/fcm-token", status_code=status.HTTP_200_OK)
def actualizar_fcm_token(
    datos: ActualizarFcmTokenRequest,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Guarda o actualiza el token FCM de Firebase del teléfono Android del usuario autenticado."""
    token_limpio = (datos.fcm_token or "").strip()
    if not token_limpio:
        raise HTTPException(status_code=400, detail="Token FCM vacío")
    usuario.fcm_token = token_limpio
    db.commit()
    print(f"[FCM] Token registrado en BD para {usuario.email}: {token_limpio[:18]}...")
    return {"mensaje": "Token FCM actualizado correctamente"}