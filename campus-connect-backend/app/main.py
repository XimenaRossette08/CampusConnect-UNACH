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

from . import models  # noqa: F401 (necesario para detectar la tabla eventos)
from . import models_auth  # noqa: F401 (necesario para detectar usuarios/roles/permisos)
from .database import Base, SessionLocal, engine
from .models_auth import Permiso, Rol
from .routers import admin, auth, estadisticas, eventos, social
from .storage import UPLOADS_DIR

Base.metadata.create_all(bind=engine)

# create_all crea tablas nuevas pero no agrega columnas a tablas que ya existen.
with engine.begin() as _conn:
    _conn.execute(text("ALTER TABLE eventos ADD COLUMN IF NOT EXISTS categoria VARCHAR"))


def sembrar_datos_iniciales() -> None:
    """
    Crea, si no existen, los permisos básicos y los tres roles predeterminados
    que pide el caso práctico (Administrador, Editor, Usuario Regular).
    Es seguro llamarla en cada arranque: no duplica nada si ya existen.
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

# Dominios autorizados a llamar la API. Se amplían los orígenes por defecto para
# admitir servidores web de desarrollo (Live Server en 5500, http.server en 8080, etc.).
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
    allow_origin_regex=r"http://.*",  # Permite cualquier puerto u origen HTTP local (localhost, 127.0.0.1, ::1)
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

# Flyers y evidencias subidos. Son públicos (los nombres son UUID aleatorios) para que la app móvil
# y Facebook/Instagram puedan descargarlos sin token.
os.makedirs(UPLOADS_DIR, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=UPLOADS_DIR), name="uploads")


@app.exception_handler(Exception)
async def manejador_errores_genericos(request: Request, exc: Exception):
    # Nunca se devuelve al cliente el detalle real (traceback, tipo de excepción, estructura interna).
    return JSONResponse(
        status_code=500,
        content={"detail": "Ocurrió un error interno"}
    )


@app.get("/health")
def health_check():
    return {"status": "ok"}