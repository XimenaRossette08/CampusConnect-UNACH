import json
import logging
import os
from contextlib import asynccontextmanager
from dotenv import load_dotenv

load_dotenv()

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address
from sqlalchemy import text
from sqlalchemy.orm import Session

from . import models  # noqa: F401
from . import models_auth  # noqa: F401
from .database import Base, SessionLocal, engine, get_db
from .models_auth import Permiso, Rol, Usuario
from .notifications import (
    detener_scheduler_recordatorios,
    inicializar_firebase,
    iniciar_scheduler_recordatorios,
)
from .routers import admin, auth, estadisticas, eventos, social
from .security import get_current_user, requiere_permiso, verificar_rol_admin
from .storage import UPLOADS_DIR

# Configuración de registro de errores
logger = logging.getLogger("uvicorn.error")

# Crear tablas si no existen
Base.metadata.create_all(bind=engine)

# 1. Asegurar columnas categoria y recordatorio_enviado en la tabla eventos
try:
    with engine.begin() as _conn:
        _conn.execute(text("ALTER TABLE eventos ADD COLUMN IF NOT EXISTS categoria VARCHAR"))
        _conn.execute(text("ALTER TABLE eventos ADD COLUMN IF NOT EXISTS recordatorio_enviado BOOLEAN NOT NULL DEFAULT FALSE"))
except Exception as e:
    logger.warning(f"No se pudo alterar la tabla eventos: {e}")

# 1b. Asegurar columnas de Alumnos Egresados, vigencia de 2FA y Token FCM de Firebase en usuarios
try:
    with engine.begin() as _conn:
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS es_egresado BOOLEAN NOT NULL DEFAULT FALSE"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS email_secundario VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS telefono_principal VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS telefono_secundario VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS ultima_verificacion_2fa TIMESTAMPTZ"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS fcm_token VARCHAR"))
except Exception as e:
    logger.warning(f"No se pudo alterar la tabla usuarios para egresados/2FA/FCM: {e}")

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
    # Inicializar Firebase Admin SDK y el Job Scheduler (APScheduler) de recordatorios 24h
    inicializar_firebase()
    iniciar_scheduler_recordatorios()
    yield
    detener_scheduler_recordatorios()


limiter = Limiter(key_func=get_remote_address)

app = FastAPI(title="Campus Connect API", lifespan=lifespan)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# ==============================================================================
# EXIGENCIA 5 DEL PDF: CORS ESTRICTO (SIN COMODÍN '*' NI REGEX GLOBAL)
# ==============================================================================
origenes_defecto = (
    "http://localhost:8080,http://127.0.0.1:8080,"
    "http://localhost:5500,http://127.0.0.1:5500,"
    "http://localhost:3000,http://127.0.0.1:3000,"
    "http://localhost:8000,http://127.0.0.1:8000"
)
origenes_permitidos = [
    origen.strip()
    for origen in os.getenv("CORS_ORIGINS", origenes_defecto).split(",")
    if origen.strip() and origen.strip() != "*"
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origenes_permitidos,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"],
)


@app.middleware("http")
async def cabeceras_seguras(request: Request, call_next):
    respuesta = await call_next(request)
    respuesta.headers["X-Content-Type-Options"] = "nosniff"
    respuesta.headers["X-Frame-Options"] = "DENY"
    return respuesta


app.include_router(auth.router)
app.include_router(admin.router)
app.include_router(eventos.router)
app.include_router(estadisticas.router)
app.include_router(social.router)
app.include_router(social.router_publico)

os.makedirs(UPLOADS_DIR, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=UPLOADS_DIR), name="uploads")


# ==============================================================================
# EXIGENCIA 5 DEL PDF: MANEJO DE ERRORES 500 SIN FILTRAR DETALLES DE BD NI CÓDIGO
# ==============================================================================
@app.exception_handler(Exception)
async def manejador_errores_genericos(request: Request, exc: Exception):
    logger.error(f"Error no controlado en {request.method} {request.url}: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"detail": "Ocurrió un error interno en el servidor. Intenta más tarde."}
    )


@app.get("/health")
def health_check():
    return {"status": "ok"}


# ==============================================================================
# PERSISTENCIA DEL CATÁLOGO DE FACULTAD, CARRERAS, GRUPOS Y AUDITORIOS (ASIENTOS)
# ==============================================================================
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


@app.get("/catalogos/facultad")
def obtener_catalogos_facultad():
    with engine.begin() as conn:
        row = conn.execute(text("SELECT payload_json FROM catalogos_institucionales WHERE id = 1")).fetchone()
        if row and row[0]:
            try:
                return json.loads(row[0])
            except Exception:
                pass
    return {}


@app.put("/catalogos/facultad")
async def guardar_catalogos_facultad(
    request: Request,
    usuario_actual: Usuario = Depends(requiere_permiso("eventos:escribir", "usuarios:gestionar")),
):
    """Protegido con requiere_permiso -> devuelve 403 Forbidden si un alumno intenta alterar catálogos."""
    datos = await request.json()
    payload_str = json.dumps(datos, ensure_ascii=False)
    with engine.begin() as conn:
        conn.execute(
            text("""
                INSERT INTO catalogos_institucionales (id, payload_json)
                VALUES (1, :payload)
                ON CONFLICT (id) DO UPDATE SET payload_json = EXCLUDED.payload_json
            """),
            {"payload": payload_str},
        )
    return {"mensaje": "Catálogo institucional guardado correctamente"}
