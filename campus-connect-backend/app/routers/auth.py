import json
import logging
import os
import re
import secrets
from datetime import datetime, timedelta, timezone
from threading import Lock
from typing import Any, Dict, List, Optional, Tuple

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from pydantic import BaseModel, EmailStr, Field, field_validator
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

logger = logging.getLogger("uvicorn.error")

router = APIRouter(prefix="/auth", tags=["autenticación"])
limiter = Limiter(key_func=get_remote_address)

# ==============================================================================
# CONFIGURACIÓN DEL TIEMPO DE VIGENCIA DEL 2FA Y SESIÓN (1 MES VS PRUEBAS)
# ==============================================================================
MODO_PRUEBAS_2FA = os.getenv("MODO_PRUEBAS_2FA", "false").lower() == "true"

DURACION_VIGENCIA_2FA = timedelta(minutes=1) if MODO_PRUEBAS_2FA else timedelta(days=30)
DURACION_CODIGO_OTP = timedelta(minutes=10)

ACCESS_COOKIE_MAX_AGE = 60 if MODO_PRUEBAS_2FA else (30 * 24 * 60 * 60)
REFRESH_COOKIE_MAX_AGE = 120 if MODO_PRUEBAS_2FA else (30 * 24 * 60 * 60)

# ==============================================================================
# CONTROL DE FUERZA BRUTA (CON LÍMITE DE MEMORIA Y EXPIRACIÓN AUTOMÁTICA)
# ==============================================================================
MAX_INTENTOS_FALLIDOS = 5
TIEMPO_BLOQUEO_SEGUNDOS = 180  # 3 minutos de bloqueo temporal tras 5 fallos consecutivos
MAX_REGISTROS_FUERZA_BRUTA = 10000

_intentos_fallidos: Dict[str, Tuple[int, Optional[datetime], datetime]] = {}
_candado_fuerza_bruta = Lock()

REGEX_TELEFONO_10_DIGITOS = re.compile(r"^\d{10}$")
REGEX_MATRICULA_ALFANUMERICA = re.compile(r"^[A-Za-z0-9\-]+$")


def _limpiar_registros_expirados_fuerza_bruta(ahora: datetime) -> None:
    """Evita agotamiento de memoria (DoS) limpiando entradas antiguas del diccionario."""
    if len(_intentos_fallidos) < MAX_REGISTROS_FUERZA_BRUTA:
        return
    expiradas = [
        k
        for k, (_, bloqueado_hasta, ultimo_intento) in _intentos_fallidos.items()
        if (bloqueado_hasta is not None and ahora >= bloqueado_hasta)
        or (ahora - ultimo_intento) > timedelta(minutes=30)
    ]
    for k in expiradas:
        _intentos_fallidos.pop(k, None)
    if len(_intentos_fallidos) >= MAX_REGISTROS_FUERZA_BRUTA:
        _intentos_fallidos.clear()


def _verificar_bloqueo_fuerza_bruta(ip: str, email: str) -> None:
    ahora = datetime.now(timezone.utc)
    claves = [f"ip:{ip}", f"email:{email.lower()}"] if email else [f"ip:{ip}"]

    with _candado_fuerza_bruta:
        _limpiar_registros_expirados_fuerza_bruta(ahora)
        for clave in claves:
            registro = _intentos_fallidos.get(clave)
            if not registro:
                continue
            fallos, bloqueado_hasta, _ = registro
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
        _limpiar_registros_expirados_fuerza_bruta(ahora)
        for clave in claves:
            fallos, bloqueado_hasta, _ = _intentos_fallidos.get(clave, (0, None, ahora))
            if bloqueado_hasta is not None and ahora >= bloqueado_hasta:
                fallos = 0
                bloqueado_hasta = None

            fallos += 1
            if fallos >= MAX_INTENTOS_FALLIDOS:
                bloqueado_hasta = ahora + timedelta(seconds=TIEMPO_BLOQUEO_SEGUNDOS)
                if fallos > MAX_INTENTOS_FALLIDOS:
                    supero_limite = True

            _intentos_fallidos[clave] = (fallos, bloqueado_hasta, ahora)

    return supero_limite


def _limpiar_fuerza_bruta(ip: str, email: str) -> None:
    with _candado_fuerza_bruta:
        _intentos_fallidos.pop(f"ip:{ip}", None)
        if email:
            _intentos_fallidos.pop(f"email:{email.lower()}", None)


# ==============================================================================
# MODELOS DE ENTRADA Y VALIDACIÓN DE FRONTERA
# ==============================================================================
class RegistroUsuarioInput(BaseModel):
    nombre: str = Field(..., min_length=2, max_length=120)
    email: EmailStr
    password: str = Field(..., min_length=6, max_length=128)
    es_egresado: bool = False
    email_secundario: Optional[EmailStr] = None
    telefono_principal: Optional[str] = Field(default=None, max_length=20)
    telefono_secundario: Optional[str] = Field(default=None, max_length=20)
    matricula: Optional[str] = Field(default=None, max_length=30)
    carrera: Optional[str] = Field(default=None, max_length=180)
    semestre_grupo: Optional[str] = Field(default=None, max_length=30)
    id_facultad: Optional[str] = Field(default=None, max_length=30)

    @field_validator("nombre")
    @classmethod
    def limpiar_nombre(cls, v: str) -> str:
        limpio = re.sub(r"\s+", " ", (v or "").strip())
        if len(limpio) < 2:
            raise ValueError("El nombre completo debe contener al menos 2 caracteres")
        return limpio


class LoginUsuarioInput(BaseModel):
    email: Optional[str] = ""
    correo: Optional[str] = None
    password: Optional[str] = ""
    dispositivo_confiable: bool = False


class Verificar2FARequest(BaseModel):
    email: EmailStr
    codigo: str = Field(..., min_length=6, max_length=6)


def _ip_del_request(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        primera_ip = forwarded.split(",")[0].strip()
        if primera_ip:
            return primera_ip
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


# ==============================================================================
# LECTURA DEL CATÁLOGO Y REGLAS DE REGISTRO CONFIGURADAS POR ADMINISTRACIÓN
# ==============================================================================
CONFIG_REGISTRO_POR_DEFECTO: Dict[str, Any] = {
    "permitirRegistroEgresados": True,
    "requerirMatricula": True,
    "longitudMinMatricula": 6,
    "longitudMaxMatricula": 15,
    "soloDigitosMatricula": False,
    "requerirCarreraGrupo": True,
    "validarContraCatalogo": True,
    "restringirDominioCorreo": False,
    "dominiosPermitidos": ["unach.mx", "alumnos.unach.mx"],
    "longitudMinPassword": 8,
}

DATOS_INSTITUCIONALES_POR_DEFECTO: Dict[str, str] = {
    "escuela": "Escuela de Tecnologías Digitales Aplicadas (ETDA)",
    "modalidad_sede": "Escolarizada (Campus I, Tuxtla Gutiérrez)",
    "ciclo_escolar": "Agosto - Diciembre 2026",
}


def _obtener_catalogo_institucional_completo(db: Session) -> Dict[str, Any]:
    """Obtiene el catálogo institucional completo configurado en el Panel de Administración."""
    resultado: Dict[str, Any] = {
        "facultadActiva": "ETDA",
        "datosInstitucionales": dict(DATOS_INSTITUCIONALES_POR_DEFECTO),
        "configuracionRegistro": dict(CONFIG_REGISTRO_POR_DEFECTO),
        "facultades": [],
        "carreras": [],
        "grupos": [],
    }
    try:
        row = db.execute(text("SELECT payload_json FROM catalogos_institucionales WHERE id = 1")).fetchone()
        if row and row[0]:
            payload = json.loads(row[0])
            if isinstance(payload, dict):
                if isinstance(payload.get("facultadActiva"), str) and payload["facultadActiva"].strip():
                    resultado["facultadActiva"] = payload["facultadActiva"].strip()

                inst = payload.get("datosInstitucionales")
                if isinstance(inst, dict):
                    resultado["datosInstitucionales"] = {
                        "escuela": (inst.get("escuela") or DATOS_INSTITUCIONALES_POR_DEFECTO["escuela"]).strip(),
                        "modalidad_sede": (
                            inst.get("modalidadSede")
                            or inst.get("modalidad_sede")
                            or DATOS_INSTITUCIONALES_POR_DEFECTO["modalidad_sede"]
                        ).strip(),
                        "ciclo_escolar": (
                            inst.get("cicloEscolar")
                            or inst.get("ciclo_escolar")
                            or DATOS_INSTITUCIONALES_POR_DEFECTO["ciclo_escolar"]
                        ).strip(),
                    }

                cfg = payload.get("configuracionRegistro")
                if isinstance(cfg, dict):
                    dominios_raw = cfg.get("dominiosPermitidos")
                    if isinstance(dominios_raw, str):
                        dominios = [d.strip().lower().lstrip("@") for d in dominios_raw.split(",") if d.strip()]
                    elif isinstance(dominios_raw, list):
                        dominios = [str(d).strip().lower().lstrip("@") for d in dominios_raw if str(d).strip()]
                    else:
                        dominios = list(CONFIG_REGISTRO_POR_DEFECTO["dominiosPermitidos"])

                    min_mat = max(1, int(cfg.get("longitudMinMatricula", CONFIG_REGISTRO_POR_DEFECTO["longitudMinMatricula"])))
                    max_mat = max(min_mat, int(cfg.get("longitudMaxMatricula", CONFIG_REGISTRO_POR_DEFECTO["longitudMaxMatricula"])))
                    min_pwd = max(6, int(cfg.get("longitudMinPassword", CONFIG_REGISTRO_POR_DEFECTO["longitudMinPassword"])))

                    resultado["configuracionRegistro"] = {
                        "permitirRegistroEgresados": bool(cfg.get("permitirRegistroEgresados", True)),
                        "requerirMatricula": bool(cfg.get("requerirMatricula", True)),
                        "longitudMinMatricula": min_mat,
                        "longitudMaxMatricula": max_mat,
                        "soloDigitosMatricula": bool(cfg.get("soloDigitosMatricula", False)),
                        "requerirCarreraGrupo": bool(cfg.get("requerirCarreraGrupo", True)),
                        "validarContraCatalogo": bool(cfg.get("validarContraCatalogo", True)),
                        "restringirDominioCorreo": bool(cfg.get("restringirDominioCorreo", False)),
                        "dominiosPermitidos": dominios or list(CONFIG_REGISTRO_POR_DEFECTO["dominiosPermitidos"]),
                        "longitudMinPassword": min_pwd,
                    }

                if isinstance(payload.get("facultades"), list):
                    resultado["facultades"] = [f for f in payload["facultades"] if isinstance(f, dict) and f.get("nombre")]
                if isinstance(payload.get("carreras"), list):
                    resultado["carreras"] = [c for c in payload["carreras"] if isinstance(c, dict) and c.get("nombre")]
                if isinstance(payload.get("grupos"), list):
                    resultado["grupos"] = [str(g).strip().upper() for g in payload["grupos"] if str(g).strip()]
    except Exception as exc:
        logger.warning(f"No se pudo leer catalogos_institucionales: {exc}")
    return resultado


def _obtener_datos_institucionales_estaticos(db: Session) -> Dict[str, str]:
    """Lee Escuela, Modalidad/Sede y Ciclo Escolar configurados por el administrador en Catálogos."""
    return _obtener_catalogo_institucional_completo(db)["datosInstitucionales"]


def _resolver_nombre_escuela_por_facultad(catalogo: Dict[str, Any], id_facultad: Optional[str]) -> str:
    escuela_defecto = catalogo["datosInstitucionales"]["escuela"]
    if not id_facultad:
        return escuela_defecto
    fac_limpia = id_facultad.strip().upper()
    for fac in catalogo.get("facultades", []):
        if str(fac.get("id", "")).strip().upper() == fac_limpia and fac.get("nombre"):
            return str(fac["nombre"]).strip()
    return escuela_defecto


@router.get("/configuracion-registro")
def obtener_configuracion_registro(db: Session = Depends(get_db)):
    """
    Devuelve las opciones y políticas de creación de cuenta configuradas por el administrador,
    junto con las facultades, carreras, grupos y datos institucionales vigentes.
    """
    catalogo = _obtener_catalogo_institucional_completo(db)
    return {
        "facultadActiva": catalogo["facultadActiva"],
        "configuracionRegistro": catalogo["configuracionRegistro"],
        "datosInstitucionales": {
            "escuela": catalogo["datosInstitucionales"]["escuela"],
            "modalidadSede": catalogo["datosInstitucionales"]["modalidad_sede"],
            "cicloEscolar": catalogo["datosInstitucionales"]["ciclo_escolar"],
        },
        "facultades": catalogo["facultades"],
        "carreras": catalogo["carreras"],
        "grupos": catalogo["grupos"],
    }


@router.post("/registro", response_model=UsuarioOut, status_code=status.HTTP_201_CREATED)
@limiter.limit("15/minute")
def registrar_usuario(request: Request, datos: RegistroUsuarioInput, db: Session = Depends(get_db)):
    catalogo = _obtener_catalogo_institucional_completo(db)
    cfg = catalogo["configuracionRegistro"]

    email_principal = datos.email.strip().lower()
    password_limpia = datos.password.strip()

    min_pwd = int(cfg.get("longitudMinPassword", 8))
    if len(password_limpia) < min_pwd:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"La contraseña debe tener al menos {min_pwd} caracteres",
        )

    # Validar si el registro de egresados está habilitado por Administración
    if datos.es_egresado and not cfg.get("permitirRegistroEgresados", True):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="El registro para alumnos egresados se encuentra deshabilitado por la administración",
        )

    # Validar dominio de correo institucional si la política administrativa lo exige para alumnos activos
    if not datos.es_egresado and cfg.get("restringirDominioCorreo", False):
        dominios_permitidos: List[str] = cfg.get("dominiosPermitidos") or ["unach.mx"]
        dominio_usuario = email_principal.split("@")[-1] if "@" in email_principal else ""
        if dominio_usuario not in [d.lower().lstrip("@") for d in dominios_permitidos]:
            dominios_txt = ", ".join(f"@{d.lstrip('@')}" for d in dominios_permitidos)
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Debes registrarte con un correo institucional autorizado ({dominios_txt})",
            )

    ya_existe = db.query(Usuario).filter(Usuario.email == email_principal).first()
    if ya_existe:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="El correo electrónico ya está registrado")

    email_sec = None
    tel_1 = None
    tel_2 = None

    # Validaciones específicas cuando se registra un Alumno Egresado:
    # A los egresados NO se les pide matrícula, licenciatura (carrera) ni semestre/grupo.
    if datos.es_egresado:
        if not datos.email_secundario:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Los alumnos egresados deben proporcionar dos correos electrónicos",
            )
        email_sec = datos.email_secundario.strip().lower()
        if email_sec == email_principal:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Los dos correos electrónicos del alumno egresado deben ser diferentes",
            )

        correo_sec_en_uso = (
            db.query(Usuario)
            .filter((Usuario.email == email_sec) | (Usuario.email_secundario == email_sec))
            .first()
        )
        if correo_sec_en_uso:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="El correo electrónico secundario ya está asociado a otra cuenta",
            )

        tel_1 = re.sub(r"[\s\-()]", "", (datos.telefono_principal or "").strip())
        tel_2 = re.sub(r"[\s\-()]", "", (datos.telefono_secundario or "").strip())
        if not REGEX_TELEFONO_10_DIGITOS.match(tel_1) or not REGEX_TELEFONO_10_DIGITOS.match(tel_2):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Los alumnos egresados deben proporcionar dos números telefónicos válidos de 10 dígitos numéricos",
            )
        if tel_1 == tel_2:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Los dos números telefónicos del alumno egresado deben ser diferentes",
            )

        matricula_limpia = None
        carrera_limpia = None
        grupo_limpio = None
        facultad_limpia = (datos.id_facultad or catalogo.get("facultadActiva") or "ETDA").strip().upper() or "ETDA"
    else:
        # Validaciones exclusivas para Estudiantes Activos (Matrícula, Licenciatura/Carrera y Grupo)
        matricula_limpia = (datos.matricula or "").strip().upper() or None
        if cfg.get("requerirMatricula", True) and not matricula_limpia:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="La matrícula estudiantil es obligatoria para crear una cuenta",
            )

        if matricula_limpia:
            min_mat = int(cfg.get("longitudMinMatricula", 6))
            max_mat = int(cfg.get("longitudMaxMatricula", 15))
            if len(matricula_limpia) < min_mat or len(matricula_limpia) > max_mat:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"La matrícula debe tener entre {min_mat} y {max_mat} caracteres",
                )
            if cfg.get("soloDigitosMatricula", False) and not matricula_limpia.isdigit():
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="La matrícula únicamente debe contener dígitos numéricos",
                )
            if not REGEX_MATRICULA_ALFANUMERICA.match(matricula_limpia):
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="La matrícula solo puede contener letras, números o guiones",
                )
            try:
                mat_existente = db.execute(
                    text("SELECT id FROM usuarios WHERE UPPER(matricula) = :mat LIMIT 1"),
                    {"mat": matricula_limpia},
                ).fetchone()
                if mat_existente:
                    raise HTTPException(
                        status_code=status.HTTP_400_BAD_REQUEST,
                        detail="La matrícula ingresada ya se encuentra registrada en otra cuenta",
                    )
            except HTTPException:
                raise
            except Exception:
                pass

        carrera_limpia = (datos.carrera or "").strip() or None
        grupo_limpio = (datos.semestre_grupo or "").strip().upper() or None
        facultad_limpia = (datos.id_facultad or catalogo.get("facultadActiva") or "ETDA").strip().upper() or "ETDA"

        if cfg.get("requerirCarreraGrupo", True):
            if not carrera_limpia:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Debes seleccionar un Programa Educativo (Carrera) válido",
                )
            if not grupo_limpio:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Debes seleccionar tu Grado y Grupo académico",
                )

        carreras_catalogo: List[Dict[str, Any]] = catalogo.get("carreras") or []
        if carrera_limpia and cfg.get("validarContraCatalogo", True) and carreras_catalogo:
            carrera_encontrada = next(
                (
                    c
                    for c in carreras_catalogo
                    if str(c.get("nombre", "")).strip().lower() == carrera_limpia.lower()
                    or str(c.get("id", "")).strip().lower() == carrera_limpia.lower()
                ),
                None,
            )
            if not carrera_encontrada:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="La carrera seleccionada no pertenece al catálogo institucional vigente",
                )
            carrera_limpia = str(carrera_encontrada.get("nombre")).strip()
            if carrera_encontrada.get("facultadId"):
                facultad_limpia = str(carrera_encontrada["facultadId"]).strip().upper()

            grupos_carrera = [
                str(g).strip().upper()
                for g in (carrera_encontrada.get("grupos") or catalogo.get("grupos") or [])
                if str(g).strip()
            ]
            if grupo_limpio and grupos_carrera and grupo_limpio not in grupos_carrera:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"El grupo '{grupo_limpio}' no está habilitado para la carrera '{carrera_limpia}'",
                )

    rol_por_defecto = db.query(Rol).filter(Rol.nombre == "Usuario Regular").first()
    if rol_por_defecto is None:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="No fue posible completar el registro",
        )

    nuevo_usuario = Usuario(
        nombre=datos.nombre,
        email=email_principal,
        password_hash=hash_password(password_limpia),
        rol_id=rol_por_defecto.id,
        es_egresado=datos.es_egresado,
        email_secundario=email_sec,
        telefono_principal=tel_1,
        telefono_secundario=tel_2,
    )

    if hasattr(nuevo_usuario, "id_facultad"):
        nuevo_usuario.id_facultad = None if datos.es_egresado else facultad_limpia
    if hasattr(nuevo_usuario, "id_carrera"):
        nuevo_usuario.id_carrera = None if datos.es_egresado else carrera_limpia
    if hasattr(nuevo_usuario, "grupos"):
        nuevo_usuario.grupos = None if datos.es_egresado else ([grupo_limpio] if grupo_limpio else None)
    if hasattr(nuevo_usuario, "matricula"):
        setattr(nuevo_usuario, "matricula", None if datos.es_egresado else matricula_limpia)

    try:
        db.add(nuevo_usuario)
        db.flush()

        db.execute(
            text("""
                UPDATE usuarios
                SET matricula = :mat,
                    id_carrera = :car,
                    id_facultad = :fac
                WHERE id = :uid
            """),
            {
                "mat": None if datos.es_egresado else matricula_limpia,
                "car": None if datos.es_egresado else carrera_limpia,
                "fac": None if datos.es_egresado else facultad_limpia,
                "uid": nuevo_usuario.id,
            },
        )
        db.commit()
        db.refresh(nuevo_usuario)
    except Exception as exc:
        db.rollback()
        logger.error(f"[Registro] Error al persistir usuario {email_principal}: {exc}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="No fue posible guardar la cuenta del usuario",
        )

    ip = _ip_del_request(request)
    try:
        registrar_auditoria(db, usuario_email=email_principal, ip=ip, accion="registro_usuario", exitoso=True)
    except Exception:
        pass

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
    Si el usuario es egresado, no devuelve matrícula, carrera ni semestre/grupo.
    """
    catalogo = _obtener_catalogo_institucional_completo(db)
    estaticos = catalogo["datosInstitucionales"]
    es_egresado = bool(getattr(usuario, "es_egresado", False))

    matricula_db = getattr(usuario, "matricula", None)
    carrera_db = getattr(usuario, "id_carrera", None)
    facultad_db = getattr(usuario, "id_facultad", None) or catalogo.get("facultadActiva") or "ETDA"
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
    rol_nombre = usuario.rol.nombre if usuario.rol else "Usuario Regular"
    escuela_resuelta = _resolver_nombre_escuela_por_facultad(catalogo, facultad_db)
    tiene_2fa = getattr(usuario, "ultima_verificacion_2fa", None) is not None

    return {
        "nombre": usuario.nombre,
        "email": usuario.email,
        "rol": rol_nombre,
        "es_egresado": es_egresado,
        "email_secundario": getattr(usuario, "email_secundario", None) or "",
        "telefono_principal": getattr(usuario, "telefono_principal", None) or "",
        "telefono_secundario": getattr(usuario, "telefono_secundario", None) or "",
        "matricula": "" if es_egresado else (matricula_db or ""),
        "carrera": "" if es_egresado else (carrera_db or ""),
        "semestre_grupo": "" if es_egresado else (semestre_grupo or ""),
        "id_facultad": facultad_db,
        "escuela": escuela_resuelta or estaticos["escuela"],
        "modalidad_sede": estaticos["modalidad_sede"],
        "ciclo_escolar": estaticos["ciclo_escolar"],
        "tiene_2fa_activo": tiene_2fa,
    }


@router.post("/login")
def iniciar_sesion(
    request: Request,
    response: Response,
    datos: LoginUsuarioInput,
    db: Session = Depends(get_db),
):
    ip = _ip_del_request(request)
    email_ingresado = (
        datos.email if datos.email is not None and datos.email.strip() != "" else (datos.correo or "")
    ).strip().lower()
    password_ingresada = (datos.password or "").strip()

    if not email_ingresado or not password_ingresada:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Error de validación: Debes proporcionar correo y contraseña",
        )

    _verificar_bloqueo_fuerza_bruta(ip, email_ingresado)

    usuario = db.query(Usuario).filter(Usuario.email == email_ingresado).first()

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

    vigencia_2fa_activa = False
    if ultima_2fa is not None:
        if ultima_2fa.tzinfo is None:
            ultima_2fa = ultima_2fa.replace(tzinfo=timezone.utc)
        if (ahora - ultima_2fa) <= DURACION_VIGENCIA_2FA:
            vigencia_2fa_activa = True

    es_produccion = os.getenv("ENVIRONMENT", "development").lower() == "production"
    permitir_bypass_dev = (
        not es_produccion
        and os.getenv("ALLOW_2FA_BYPASS_DEV", "false").lower() == "true"
        and datos.dispositivo_confiable
    )

    if vigencia_2fa_activa or permitir_bypass_dev:
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

    # Generación criptográficamente segura (CSPRNG) de código de 6 dígitos
    codigo_6_digitos = f"{secrets.randbelow(900000) + 100000}"
    usuario.codigo_2fa = codigo_6_digitos
    usuario.codigo_2fa_expiracion = ahora + DURACION_CODIGO_OTP
    db.commit()

    if not es_produccion and os.getenv("DEBUG_LOG_OTP", "false").lower() == "true":
        logger.info(f"[2FA-DEV] Código generado para {usuario.email}: {codigo_6_digitos}")
    else:
        logger.info(f"[2FA] Código de verificación generado y enviado para {usuario.email}")

    try:
        enviar_correo_codigo_2fa(usuario.email, codigo_6_digitos)
    except Exception as exc:
        logger.error(f"[2FA] Error al enviar correo a {usuario.email}: {exc}")

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
    ip = _ip_del_request(request)
    email_limpio = datos.email.strip().lower()

    _verificar_bloqueo_fuerza_bruta(ip, email_limpio)

    usuario = db.query(Usuario).filter(Usuario.email == email_limpio).first()

    if not usuario or not usuario.activo or not usuario.codigo_2fa:
        _registrar_fallo_fuerza_bruta(ip, email_limpio)
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Petición de verificación inválida")

    ahora = datetime.now(timezone.utc)
    expiracion = usuario.codigo_2fa_expiracion
    if expiracion:
        if expiracion.tzinfo is None:
            expiracion = expiracion.replace(tzinfo=timezone.utc)
        if ahora > expiracion:
            usuario.codigo_2fa = None
            usuario.codigo_2fa_expiracion = None
            db.commit()
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="El código ha expirado, inicia sesión nuevamente para solicitar uno nuevo",
            )

    codigo_ingresado = datos.codigo.strip()
    if not secrets.compare_digest(str(usuario.codigo_2fa), codigo_ingresado):
        _registrar_fallo_fuerza_bruta(ip, email_limpio)
        registrar_auditoria(db, usuario_email=email_limpio, ip=ip, accion="verificar_2fa", exitoso=False)
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Código de seguridad incorrecto")

    # Invalidar el código OTP tras su uso exitoso (un solo uso) y activar vigencia por 30 días
    _limpiar_fuerza_bruta(ip, email_limpio)
    usuario.codigo_2fa = None
    usuario.codigo_2fa_expiracion = None
    usuario.ultima_verificacion_2fa = ahora
    db.commit()

    registrar_auditoria(db, usuario_email=usuario.email, ip=ip, accion="verificar_2fa", exitoso=True)

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
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Credenciales inválidas")

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
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="La contraseña actual no es correcta")

    nueva_limpia = (datos.password_nueva or "").strip()
    if len(nueva_limpia) < 8:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="La nueva contraseña debe tener al menos 8 caracteres",
        )
    if verificar_password(nueva_limpia, usuario.password_hash):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="La nueva contraseña debe ser diferente a la actual",
        )

    usuario.password_hash = hash_password(nueva_limpia)
    db.commit()
    return {"mensaje": "Contraseña actualizada"}


@router.post("/recuperar-password")
@limiter.limit("5/minute")
def solicitar_recuperacion(request: Request, datos: SolicitarRecuperacionRequest, db: Session = Depends(get_db)):
    email_limpio = datos.email.strip().lower()
    usuario = db.query(Usuario).filter(Usuario.email == email_limpio).first()

    respuesta_estandar: Dict[str, Any] = {"mensaje": "Si el correo existe, se enviarán instrucciones"}
    if usuario is None or not usuario.activo:
        return respuesta_estandar

    token_recuperacion = crear_token_recuperacion(usuario.email)
    es_produccion = os.getenv("ENVIRONMENT", "development").lower() == "production"
    exponer_para_tests = not es_produccion and os.getenv("EXPOSE_RESET_TOKEN_FOR_TESTS", "false").lower() == "true"
    if exponer_para_tests:
        respuesta_estandar["token_solo_para_pruebas"] = token_recuperacion

    return respuesta_estandar


@router.post("/restablecer-password")
@limiter.limit("5/minute")
def restablecer_password(request: Request, datos: RestablecerPasswordRequest, db: Session = Depends(get_db)):
    nueva_limpia = (datos.password_nueva or "").strip()
    if len(nueva_limpia) < 8:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="La nueva contraseña debe tener al menos 8 caracteres",
        )

    email = email_desde_token_recuperacion(datos.token)
    usuario = db.query(Usuario).filter(Usuario.email == email).first()
    if usuario is None or not usuario.activo:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Token inválido")

    usuario.password_hash = hash_password(nueva_limpia)
    db.commit()
    return {"mensaje": "Contraseña restablecida"}


@router.get("/me", response_model=UsuarioOut)
def obtener_perfil(usuario: Usuario = Depends(get_current_user)):
    return UsuarioOut.desde_usuario(usuario)


class ActualizarFcmTokenRequest(BaseModel):
    fcm_token: str = Field(..., min_length=10, max_length=4096)


@router.post("/fcm-token", status_code=status.HTTP_200_OK)
def actualizar_fcm_token(
    datos: ActualizarFcmTokenRequest,
    usuario: Usuario = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Guarda o actualiza el token FCM de Firebase del teléfono Android del usuario autenticado."""
    token_limpio = (datos.fcm_token or "").strip()
    if not token_limpio or len(token_limpio) < 10:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Token FCM inválido")
    usuario.fcm_token = token_limpio
    db.commit()
    logger.info(f"[FCM] Token actualizado en BD para {usuario.email}")
    return {"mensaje": "Token FCM actualizado correctamente"}