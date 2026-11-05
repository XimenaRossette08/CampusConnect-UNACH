import json
import logging
import os
import re
from contextlib import asynccontextmanager
from typing import Any, Dict, List, Optional

from dotenv import load_dotenv

load_dotenv(override=True)

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, field_validator
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address
from sqlalchemy import text

from . import models  # noqa: F401
from . import models_auth  # noqa: F401
from .auditoria import registrar_auditoria
from .database import Base, SessionLocal, engine
from .models_auth import Permiso, Rol, Usuario
from .notifications import (
    detener_scheduler_recordatorios,
    inicializar_firebase,
    iniciar_scheduler_recordatorios,
)
from .routers import admin, auth, estadisticas, eventos, social
from .security import requiere_permiso
from .storage import UPLOADS_DIR

# Configuración de registro de errores
logger = logging.getLogger("uvicorn.error")

# Crear tablas si no existen
Base.metadata.create_all(bind=engine)

# 1. Asegurar columnas categoria, recordatorio_enviado, disponible_desde y fecha_fin en la tabla eventos
try:
    with engine.begin() as _conn:
        _conn.execute(text("ALTER TABLE eventos ADD COLUMN IF NOT EXISTS categoria VARCHAR"))
        _conn.execute(text("ALTER TABLE eventos ADD COLUMN IF NOT EXISTS recordatorio_enviado BOOLEAN NOT NULL DEFAULT FALSE"))
        _conn.execute(text("ALTER TABLE eventos ADD COLUMN IF NOT EXISTS disponible_desde TIMESTAMPTZ"))
        _conn.execute(text("ALTER TABLE eventos ADD COLUMN IF NOT EXISTS fecha_fin TIMESTAMPTZ"))
        _conn.execute(text("ALTER TABLE eventos ADD COLUMN IF NOT EXISTS notificacion_aparicion_enviada BOOLEAN NOT NULL DEFAULT FALSE"))
        _conn.execute(text("ALTER TABLE eventos ADD COLUMN IF NOT EXISTS notificacion_fin_30m_enviada BOOLEAN NOT NULL DEFAULT FALSE"))
        _conn.execute(text("ALTER TABLE eventos ADD COLUMN IF NOT EXISTS notificacion_finalizado_enviada BOOLEAN NOT NULL DEFAULT FALSE"))
        _conn.execute(text("ALTER TABLE eventos ADD COLUMN IF NOT EXISTS asientos_reservados_json TEXT DEFAULT '{}'"))
except Exception as e:
    logger.warning(f"No se pudo alterar la tabla eventos: {e}")

# 1b. Asegurar columnas de Alumnos Egresados, Expediente Académico, Permisos Personalizados, 2FA y FCM en usuarios
try:
    with engine.begin() as _conn:
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS es_egresado BOOLEAN NOT NULL DEFAULT FALSE"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS email_secundario VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS telefono_principal VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS telefono_secundario VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS ultima_verificacion_2fa TIMESTAMPTZ"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS fcm_token VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS matricula VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS id_facultad VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS id_carrera VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS permisos_personalizados TEXT"))
        _conn.execute(text("ALTER TABLE evento_asistencias ADD COLUMN IF NOT EXISTS asiento_codigo VARCHAR"))
        _conn.execute(text("ALTER TABLE evento_asistencias ADD COLUMN IF NOT EXISTS qr_validado BOOLEAN NOT NULL DEFAULT FALSE"))
        _conn.execute(text("ALTER TABLE evento_asistencias ADD COLUMN IF NOT EXISTS gps_validado BOOLEAN NOT NULL DEFAULT FALSE"))
        _conn.execute(text("ALTER TABLE evento_asistencias ADD COLUMN IF NOT EXISTS pings_gps INTEGER NOT NULL DEFAULT 0"))
        _conn.execute(text("ALTER TABLE evento_asistencias ADD COLUMN IF NOT EXISTS ultimo_ping_gps TIMESTAMPTZ"))
except Exception as e:
    logger.warning(f"No se pudo alterar la tabla usuarios para expediente/egresados/permisos/2FA/FCM: {e}")

# 2. Sembrar únicamente la facultad ETDA
try:
    with engine.begin() as _conn:
        _conn.execute(text("""
            INSERT INTO facultades (id, nombre) 
            VALUES ('ETDA', 'Escuela de Tecnologías Digitales Aplicadas (ETDA)')
            ON CONFLICT (id) DO NOTHING;
        """))
except Exception as e:
    logger.warning(f"No se pudieron sembrar facultades iniciales: {e}")

# 3. Sembrar carreras base
try:
    with engine.begin() as _conn:
        _conn.execute(text("""
            INSERT INTO carreras (id, nombre) 
            VALUES 
                ('Licenciatura en ingenieria en desarrollo de tecnologias y software', 'Licenciatura en Ingeniería en Desarrollo de Tecnologías y Software'),
                ('Ingeniería en Desarrollo de Software', 'Ingeniería en Desarrollo de Software'),
                ('Licenciatura en Sistemas Computacionales', 'Licenciatura en Sistemas Computacionales')
            ON CONFLICT (id) DO NOTHING;
        """))
except Exception as e:
    logger.warning(f"No se pudieron sembrar carreras iniciales: {e}")


def sembrar_datos_iniciales() -> None:
    """
    Crea los permisos base y los roles iniciales (incluyendo 'Auxiliar' para cumplir
    con el Punto 6 de la rúbrica de roles y permisos).
    """
    permisos_necesarios = {
        "eventos:leer": "Ver los eventos publicados",
        "eventos:escribir": "Crear y editar eventos",
        "eventos:eliminar": "Eliminar eventos",
        "usuarios:leer": "Ver la lista de usuarios",
        "usuarios:gestionar": "Asignar o revocar roles a usuarios",
        "roles:gestionar": "Crear roles y asignarles permisos",
        "auditoria:leer": "Consultar el registro de auditoría",
        "estadisticas:leer": "Ver las estadísticas de eventos",
    }
    roles_necesarios = {
        "Administrador": list(permisos_necesarios.keys()),
        "Editor": ["eventos:leer", "eventos:escribir", "eventos:eliminar", "estadisticas:leer"],
        "Auxiliar": ["eventos:leer", "eventos:escribir", "estadisticas:leer"],
        "Usuario Regular": ["eventos:leer"],
    }

    db = SessionLocal()
    try:
        permisos_por_clave = {}
        for clave, descripcion in permisos_necesarios.items():
            permiso = db.query(Permiso).filter(Permiso.clave == clave).first()
            if permiso is None:
                permiso = Permiso(clave=clave, descripcion=descripcion)
                db.add(permiso)
                db.flush()
            permisos_por_clave[clave] = permiso

        for nombre_rol, claves in roles_necesarios.items():
            rol = db.query(Rol).filter(Rol.nombre == nombre_rol).first()
            if rol is None:
                rol = Rol(nombre=nombre_rol)
                db.add(rol)
                db.flush()
            rol.permisos = [permisos_por_clave[clave] for clave in claves]

        db.commit()
    finally:
        db.close()


sembrar_datos_iniciales()


@asynccontextmanager
async def lifespan(app_instance: FastAPI):
    inicializar_firebase()
    iniciar_scheduler_recordatorios()
    yield
    detener_scheduler_recordatorios()


limiter = Limiter(key_func=get_remote_address)

app = FastAPI(title="Campus Connect API", lifespan=lifespan)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# ==============================================================================
# CONFIGURACIÓN DE CORS Y CABECERAS DE SEGURIDAD HTTP
# ==============================================================================
origenes_defecto = [
    "http://localhost:5500", "http://127.0.0.1:5500",
    "http://localhost:5501", "http://127.0.0.1:5501",
    "http://localhost:5502", "http://127.0.0.1:5502",
    "http://localhost:8080", "http://127.0.0.1:8080",
    "http://localhost:3000", "http://127.0.0.1:3000",
    "http://localhost:5173", "http://127.0.0.1:5173",
    "http://localhost:8000", "http://127.0.0.1:8000",
]
origenes_env = [
    origen.strip().rstrip("/")
    for origen in os.getenv("CORS_ORIGINS", "").split(",")
    if origen.strip() and origen.strip() != "*"
]
origenes_permitidos = list(dict.fromkeys(origenes_defecto + origenes_env))


@app.middleware("http")
async def cabeceras_seguras(request: Request, call_next):
    respuesta = await call_next(request)
    respuesta.headers["X-Content-Type-Options"] = "nosniff"
    respuesta.headers["X-Frame-Options"] = "DENY"
    respuesta.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    respuesta.headers["Permissions-Policy"] = "geolocation=(), microphone=(), camera=()"
    return respuesta


app.add_middleware(
    CORSMiddleware,
    allow_origins=origenes_permitidos,
    allow_origin_regex=r"^https?://(localhost|127\.0\.0\.1)(:[0-9]+)?$",
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"],
)

app.include_router(auth.router)
app.include_router(admin.router)
app.include_router(eventos.router)
app.include_router(estadisticas.router)
app.include_router(social.router)
app.include_router(social.router_publico)

os.makedirs(UPLOADS_DIR, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=UPLOADS_DIR), name="uploads")


# ==============================================================================
# MANEJO DE ERRORES 500 SIN FILTRAR DETALLES DE BD NI CÓDIGO
# ==============================================================================
@app.exception_handler(Exception)
async def manejador_errores_genericos(request: Request, exc: Exception):
    logger.error(f"Error no controlado en {request.method} {request.url}: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"detail": "Ocurrió un error interno en el servidor. Intenta más tarde."},
    )


@app.get("/health")
def health_check():
    return {"status": "ok"}


# ==============================================================================
# PERSISTENCIA Y VALIDACIÓN DEL CATÁLOGO DE FACULTAD, CARRERAS Y REGISTRO
# ==============================================================================
CATALOGO_INSTITUCIONAL_DEFECTO: Dict[str, Any] = {
    "facultadActiva": "ETDA",
    "datosInstitucionales": {
        "escuela": "Escuela de Tecnologías Digitales Aplicadas (ETDA)",
        "modalidadSede": "Escolarizada (Campus I, Tuxtla Gutiérrez)",
        "cicloEscolar": "Agosto - Diciembre 2026",
    },
    "configuracionRegistro": {
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
    },
    "facultades": [
        {"id": "ETDA", "nombre": "Escuela de Tecnologías Digitales Aplicadas (ETDA)"},
        {"id": "FCA", "nombre": "Facultad de Contaduría y Administración (FCA)"},
        {"id": "FI", "nombre": "Facultad de Ingeniería (FI)"},
    ],
    "carreras": [
        {
            "id": "LIDTS",
            "nombre": "Lic. en Ingeniería en Desarrollo y Tecnologías de Software",
            "facultadId": "ETDA",
            "grupos": ["1A", "1B", "3A", "3B", "5A", "5B", "7N", "7O", "8A"],
        },
        {
            "id": "LSC",
            "nombre": "Lic. en Sistemas Computacionales",
            "facultadId": "FCA",
            "grupos": ["1A", "1B", "3A", "5A", "7A", "9A"],
        },
        {
            "id": "IC",
            "nombre": "Ingeniería Civil",
            "facultadId": "FI",
            "grupos": ["1A", "2A", "3A", "4A", "5A", "6A", "7A", "8A"],
        },
    ],
    "grupos": ["1A", "1B", "2A", "3A", "3B", "4A", "5A", "5B", "6A", "7A", "7N", "7O", "8A", "9A"],
    "auditorios": [
        {
            "id": "aud-etda", "nombre": "Auditorio Principal ETDA", "asientos": 180, "ubicacion": "Edificio Principal ETDA",
            "toleranciaMetros": 12,
            "malla": [
                {"lat": 16.754420, "lon": -93.154050}, {"lat": 16.754420, "lon": -93.153550},
                {"lat": 16.753980, "lon": -93.153550}, {"lat": 16.753980, "lon": -93.154050},
            ],
        },
        {
            "id": "sala-av1", "nombre": "Sala Audiovisual 1", "asientos": 60, "ubicacion": "Planta Alta ETDA",
            "toleranciaMetros": 12,
            "malla": [
                {"lat": 16.754360, "lon": -93.153960}, {"lat": 16.754360, "lon": -93.153680},
                {"lat": 16.754100, "lon": -93.153680}, {"lat": 16.754100, "lon": -93.153960},
            ],
        },
        {
            "id": "sala-av2", "nombre": "Sala Audiovisual 2", "asientos": 50, "ubicacion": "Planta Alta ETDA",
            "toleranciaMetros": 12,
            "malla": [
                {"lat": 16.754340, "lon": -93.153920}, {"lat": 16.754340, "lon": -93.153660},
                {"lat": 16.754080, "lon": -93.153660}, {"lat": 16.754080, "lon": -93.153920},
            ],
        },
        {
            "id": "lab-comp-a", "nombre": "Laboratorio de Cómputo A", "asientos": 35, "ubicacion": "Centro de Cómputo",
            "toleranciaMetros": 12,
            "malla": [
                {"lat": 16.754480, "lon": -93.154120}, {"lat": 16.754480, "lon": -93.153820},
                {"lat": 16.754220, "lon": -93.153820}, {"lat": 16.754220, "lon": -93.154120},
            ],
        },
        {
            "id": "aud-constituyentes", "nombre": "Auditorio Los Constituyentes", "asientos": 350, "ubicacion": "Campus I Central",
            "toleranciaMetros": 12,
            "malla": [
                {"lat": 16.753250, "lon": -93.152950}, {"lat": 16.753250, "lon": -93.152250},
                {"lat": 16.752550, "lon": -93.152250}, {"lat": 16.752550, "lon": -93.152950},
            ],
        },
        {
            "id": "explanada-etda", "nombre": "Explanada Central ETDA", "asientos": 250, "ubicacion": "Área Abierta Campus I",
            "toleranciaMetros": 12,
            "malla": [
                {"lat": 16.754600, "lon": -93.154200}, {"lat": 16.754600, "lon": -93.153400},
                {"lat": 16.753850, "lon": -93.153400}, {"lat": 16.753850, "lon": -93.154200},
            ],
        },
    ],
    "categorias": ["Académicos", "Conferencias", "Talleres", "Tutorías", "Culturales", "Deporte"],
}


REGEX_COLOR_HEX = re.compile(r"^#[0-9A-Fa-f]{6}$")
REGEX_LOGO_SEGURO = re.compile(r"^(data:image/(png|jpeg|jpg|webp|gif);base64,[A-Za-z0-9+/=\s]+|https?://[^\s\"'<>]+)$")


class FacultadItemSchema(BaseModel):
    id: str = Field(..., min_length=1, max_length=30)
    nombre: str = Field(..., min_length=2, max_length=200)


class CarreraItemSchema(BaseModel):
    id: Optional[str] = Field(default=None, max_length=40)
    nombre: str = Field(..., min_length=2, max_length=200)
    facultadId: str = Field(default="ETDA", max_length=30)
    grupos: List[str] = Field(default_factory=list)


class VerticeMallaSchema(BaseModel):
    lat: float = Field(..., ge=-90.0, le=90.0)
    lon: float = Field(..., ge=-180.0, le=180.0)


class AuditorioItemSchema(BaseModel):
    id: Optional[str] = Field(default=None, max_length=60)
    nombre: str = Field(..., min_length=2, max_length=150)
    asientos: int = Field(..., ge=1, le=20000)
    ubicacion: Optional[str] = Field(default="Campus I UNACH", max_length=200)
    toleranciaMetros: float = Field(default=12.0, ge=2.0, le=80.0)
    malla: Optional[List[VerticeMallaSchema]] = None


class DatosInstitucionalesSchema(BaseModel):
    escuela: str = Field(default="Escuela de Tecnologías Digitales Aplicadas (ETDA)", min_length=2, max_length=200)
    modalidadSede: str = Field(default="Escolarizada (Campus I, Tuxtla Gutiérrez)", min_length=2, max_length=200)
    cicloEscolar: str = Field(default="Agosto - Diciembre 2026", min_length=2, max_length=120)


class ConfiguracionRegistroSchema(BaseModel):
    permitirRegistroEgresados: bool = True
    requerirMatricula: bool = True
    longitudMinMatricula: int = Field(default=6, ge=1, le=25)
    longitudMaxMatricula: int = Field(default=15, ge=1, le=30)
    soloDigitosMatricula: bool = False
    requerirCarreraGrupo: bool = True
    validarContraCatalogo: bool = True
    restringirDominioCorreo: bool = False
    dominiosPermitidos: List[str] = Field(default_factory=lambda: ["unach.mx", "alumnos.unach.mx"])
    longitudMinPassword: int = Field(default=8, ge=6, le=64)

    @field_validator("dominiosPermitidos", mode="before")
    @classmethod
    def normalizar_dominios(cls, v: Any) -> List[str]:
        if isinstance(v, str):
            items = [d.strip().lower().lstrip("@") for d in v.split(",") if d.strip()]
        elif isinstance(v, list):
            items = [str(d).strip().lower().lstrip("@") for d in v if str(d).strip()]
        else:
            items = ["unach.mx", "alumnos.unach.mx"]
        limpios = [d for d in items if re.match(r"^[a-z0-9.-]+\.[a-z]{2,}$", d)]
        return limpios or ["unach.mx"]


class MembreteCatalogoSchema(BaseModel):
    institucion: str = Field(default="UNIVERSIDAD AUTÓNOMA DE CHIAPAS", max_length=200)
    dependencia: str = Field(default="ESCUELA DE TECNOLOGÍAS DIGITALES APLICADAS CAMPUS I", max_length=200)
    subtituloDocumento: str = Field(default="LISTA OFICIAL DE ASISTENCIA A EVENTO ACADÉMICO", max_length=200)
    direccionPie: str = Field(default="", max_length=300)
    contactoPie: str = Field(default="", max_length=300)
    dominioPie: str = Field(default="unach.mx", max_length=100)
    mostrarFirma: bool = True
    firmaResponsable: str = Field(default="", max_length=160)
    cargoFirma: str = Field(default="", max_length=160)
    colorBarraSuperior: str = Field(default="#2B3E6B", max_length=12)
    colorBarraInferior: str = Field(default="#C49B27", max_length=12)
    logoIzquierdo: Optional[str] = None
    logoDerecho: Optional[str] = None

    @field_validator("colorBarraSuperior", "colorBarraInferior")
    @classmethod
    def validar_color_hex(cls, v: str) -> str:
        val = (v or "").strip()
        if not REGEX_COLOR_HEX.match(val):
            return "#2B3E6B"
        return val

    @field_validator("logoIzquierdo", "logoDerecho")
    @classmethod
    def validar_logo_seguro(cls, v: Optional[str]) -> Optional[str]:
        if not v:
            return None
        limpio = v.strip()
        if len(limpio) > 1_500_000:
            raise ValueError("El tamaño del logo excede el límite permitido")
        if not REGEX_LOGO_SEGURO.match(limpio):
            raise ValueError("Formato de logo inválido; solo se permiten imágenes base64 seguras o URLs HTTPS")
        return limpio


class CatalogoFacultadPayloadSchema(BaseModel):
    facultadActiva: str = Field(default="ETDA", max_length=30)
    datosInstitucionales: Optional[DatosInstitucionalesSchema] = None
    configuracionRegistro: Optional[ConfiguracionRegistroSchema] = None
    facultades: Optional[List[FacultadItemSchema]] = None
    carreras: Optional[List[CarreraItemSchema]] = None
    grupos: Optional[List[str]] = None
    auditorios: Optional[List[AuditorioItemSchema]] = None
    categorias: Optional[List[str]] = None
    membrete: Optional[MembreteCatalogoSchema] = None


try:
    with engine.begin() as _conn:
        _conn.execute(text("""
            CREATE TABLE IF NOT EXISTS catalogos_institucionales (
                id INTEGER PRIMARY KEY,
                payload_json TEXT NOT NULL
            )
        """))
except Exception as _e:
    logger.warning(f"No se pudo crear tabla catalogos_institucionales: {_e}")


def _normalizar_catalogo_guardado(guardado: Dict[str, Any]) -> Dict[str, Any]:
    """Garantiza que todos los bloques del catálogo institucional y opciones de registro estén presentes y limpios."""
    datos_inst = dict(CATALOGO_INSTITUCIONAL_DEFECTO["datosInstitucionales"])
    if isinstance(guardado.get("datosInstitucionales"), dict):
        for k in ("escuela", "modalidadSede", "cicloEscolar"):
            val = guardado["datosInstitucionales"].get(k)
            if isinstance(val, str) and val.strip():
                datos_inst[k] = val.strip()
    guardado["datosInstitucionales"] = datos_inst

    cfg_reg = dict(CATALOGO_INSTITUCIONAL_DEFECTO["configuracionRegistro"])
    if isinstance(guardado.get("configuracionRegistro"), dict):
        try:
            validado_cfg = ConfiguracionRegistroSchema(**guardado["configuracionRegistro"]).model_dump()
            cfg_reg.update(validado_cfg)
        except Exception:
            pass
    guardado["configuracionRegistro"] = cfg_reg

    if not isinstance(guardado.get("facultades"), list) or not guardado["facultades"]:
        guardado["facultades"] = list(CATALOGO_INSTITUCIONAL_DEFECTO["facultades"])

    grupos_globales = [
        str(g).strip().upper()
        for g in (guardado.get("grupos") or CATALOGO_INSTITUCIONAL_DEFECTO["grupos"])
        if str(g).strip()
    ]
    carreras_guardadas = guardado.get("carreras") or CATALOGO_INSTITUCIONAL_DEFECTO["carreras"]
    set_grupos_totales = set(grupos_globales)

    carreras_limpias = []
    for c in carreras_guardadas:
        if not isinstance(c, dict) or not c.get("nombre"):
            continue
        grupos_c = [
            str(g).strip().upper()
            for g in (c.get("grupos") or grupos_globales)
            if str(g).strip()
        ]
        grupos_c_unicos = list(dict.fromkeys(grupos_c))
        set_grupos_totales.update(grupos_c_unicos)
        carreras_limpias.append({
            "id": str(c.get("id") or str(c["nombre"])[:12].upper().replace(" ", "_")).strip(),
            "nombre": str(c["nombre"]).strip(),
            "facultadId": str(c.get("facultadId") or "ETDA").strip().upper(),
            "grupos": grupos_c_unicos,
        })

    guardado["carreras"] = carreras_limpias or list(CATALOGO_INSTITUCIONAL_DEFECTO["carreras"])
    guardado["grupos"] = sorted(set_grupos_totales)

    if not isinstance(guardado.get("auditorios"), list) or not guardado["auditorios"]:
        guardado["auditorios"] = list(CATALOGO_INSTITUCIONAL_DEFECTO["auditorios"])
    if not isinstance(guardado.get("categorias"), list) or not guardado["categorias"]:
        guardado["categorias"] = list(CATALOGO_INSTITUCIONAL_DEFECTO["categorias"])

    return guardado


@app.get("/catalogos/facultad")
def obtener_catalogos_facultad():
    with engine.begin() as conn:
        row = conn.execute(text("SELECT payload_json FROM catalogos_institucionales WHERE id = 1")).fetchone()
        if row and row[0]:
            try:
                guardado = json.loads(row[0])
                if isinstance(guardado, dict):
                    return _normalizar_catalogo_guardado(guardado)
            except Exception as exc:
                logger.warning(f"Error al parsear catalogos_institucionales: {exc}")
    return _normalizar_catalogo_guardado(dict(CATALOGO_INSTITUCIONAL_DEFECTO))


@app.put("/catalogos/facultad")
async def guardar_catalogos_facultad(
    payload: CatalogoFacultadPayloadSchema,
    request: Request,
    usuario_actual: Usuario = Depends(requiere_permiso("eventos:escribir", "usuarios:gestionar")),
):
    datos_validados = payload.model_dump(exclude_none=True)
    datos_normalizados = _normalizar_catalogo_guardado(datos_validados)
    if datos_normalizados["configuracionRegistro"]["longitudMaxMatricula"] < datos_normalizados["configuracionRegistro"]["longitudMinMatricula"]:
        datos_normalizados["configuracionRegistro"]["longitudMaxMatricula"] = datos_normalizados["configuracionRegistro"]["longitudMinMatricula"]

    payload_str = json.dumps(datos_normalizados, ensure_ascii=False)
    with engine.begin() as conn:
        conn.execute(
            text("""
                INSERT INTO catalogos_institucionales (id, payload_json)
                VALUES (1, :payload)
                ON CONFLICT (id) DO UPDATE SET payload_json = EXCLUDED.payload_json
            """),
            {"payload": payload_str},
        )

    try:
        with engine.begin() as conn:
            for fac in datos_normalizados.get("facultades", []):
                conn.execute(
                    text("""
                        INSERT INTO facultades (id, nombre)
                        VALUES (:id, :nombre)
                        ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre
                    """),
                    {"id": fac["id"], "nombre": fac["nombre"]},
                )
            for car in datos_normalizados.get("carreras", []):
                conn.execute(
                    text("""
                        INSERT INTO carreras (id, nombre)
                        VALUES (:id, :nombre)
                        ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre
                    """),
                    {"id": car["nombre"], "nombre": car["nombre"]},
                )
    except Exception as exc:
        logger.warning(f"Aviso al sincronizar tablas facultades/carreras: {exc}")

    try:
        db = SessionLocal()
        try:
            ip = request.client.host if request.client else "desconocida"
            registrar_auditoria(
                db,
                usuario_email=usuario_actual.email,
                ip=ip,
                accion="actualizar_catalogos_institucionales",
                exitoso=True,
            )
        finally:
            db.close()
    except Exception:
        pass

    return {
        "mensaje": "Catálogo institucional y políticas de creación de cuenta guardados correctamente",
        "catalogo": datos_normalizados,
    }