import logging
import os
from dotenv import load_dotenv

load_dotenv()

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import text
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address

from . import models  # noqa: F401
from . import models_auth  # noqa: F401
from .database import Base, SessionLocal, engine
from .models_auth import Permiso, Rol
from .routers import admin, auth, estadisticas, eventos, social
from .storage import UPLOADS_DIR

# Configuración de registro de errores
logger = logging.getLogger("uvicorn.error")

# Crear tablas si no existen
Base.metadata.create_all(bind=engine)

# 1. Asegurar columna categoria en la tabla eventos
try:
    with engine.begin() as _conn:
        _conn.execute(text("ALTER TABLE eventos ADD COLUMN IF NOT EXISTS categoria VARCHAR"))
except Exception as e:
    logger.warning(f"No se pudo alterar la tabla eventos: {e}")

# 1b. Asegurar columnas de Alumnos Egresados y vigencia de 1 mes de 2FA en la tabla usuarios
try:
    with engine.begin() as _conn:
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS es_egresado BOOLEAN NOT NULL DEFAULT FALSE"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS email_secundario VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS telefono_principal VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS telefono_secundario VARCHAR"))
        _conn.execute(text("ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS ultima_verificacion_2fa TIMESTAMPTZ"))
except Exception as e:
    logger.warning(f"No se pudo alterar la tabla usuarios para egresados/2FA: {e}")

# 2. Sembrar facultades base
try:
    with engine.begin() as _conn:
        _conn.execute(text("""
            INSERT INTO facultades (id, nombre) 
            VALUES 
                ('ETDA', 'Escuela de Tecnologías y Desarrollo Adaptativo'),
                ('FCA', 'Facultad de Contaduría y Administración'),
                ('FI', 'Facultad de Ingeniería')
            ON CONFLICT (id) DO NOTHING;
        """))
except Exception as e:
    logger.warning(f"No se pudieron sembrar facultades iniciales: {e}")

# 3. Sembrar carreras base (usando solo id y nombre)
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
    Crea, si no existen, los permisos básicos y los tres roles predeterminados.
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

limiter = Limiter(key_func=get_remote_address)

app = FastAPI(title="Campus Connect API")
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

origenes_defecto = (
    "http://localhost:8080,http://127.0.0.1:8080,"
    "http://localhost:5500,http://127.0.0.1:5500,"
    "http://localhost:3000,http://127.0.0.1:3000"
)
origenes_permitidos = [
    origen.strip()
    for origen in os.getenv("CORS_ORIGINS", origenes_defecto).split(",")
    if origen.strip()
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origenes_permitidos,
    allow_origin_regex=r"http://.*",
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def cabeceras_seguras(request: Request, call_next):
    respuesta = await call_next(request)
    respuesta.headers["X-Content-Type-Options"] = "nosniff"
    return respuesta


app.include_router(auth.router)
app.include_router(admin.router)
app.include_router(eventos.router)
app.include_router(estadisticas.router)
app.include_router(social.router)
app.include_router(social.router_publico)

os.makedirs(UPLOADS_DIR, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=UPLOADS_DIR), name="uploads")


@app.exception_handler(Exception)
async def manejador_errores_genericos(request: Request, exc: Exception):
    logger.error(f"Error no controlado en {request.method} {request.url}: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"detail": "Ocurrió un error interno"}
    )


@app.get("/health")
def health_check():
    return {"status": "ok"}


# Endpoint administrativo para eliminar usuarios/empleados desde el panel web
from fastapi import Depends, HTTPException
from sqlalchemy.orm import Session
from .database import get_db
from .models_auth import Usuario
from .security import get_current_user

@app.delete("/admin/usuarios/{usuario_id}")
def eliminar_usuario_administrativo(
    usuario_id: int,
    db: Session = Depends(get_db),
    usuario_actual: Usuario = Depends(get_current_user),
):
    rol_actual = usuario_actual.rol.nombre if usuario_actual.rol else ""
    if rol_actual != "Administrador":
        raise HTTPException(status_code=403, detail="Solo un administrador puede eliminar usuarios")

    if usuario_actual.id == usuario_id:
        raise HTTPException(status_code=400, detail="No puedes eliminar tu propia cuenta activa")

    objetivo = db.query(Usuario).filter(Usuario.id == usuario_id).first()
    if objetivo is None:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")

    db.delete(objetivo)
    db.commit()
    return {"mensaje": f"Usuario {objetivo.email} eliminado correctamente"}


# ==============================================================================
# PERSISTENCIA DEL CATÁLOGO DE FACULTAD, CARRERAS, GRUPOS Y AUDITORIOS (ASIENTOS)
# ==============================================================================
import json

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
    usuario_actual: Usuario = Depends(get_current_user),
):
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
