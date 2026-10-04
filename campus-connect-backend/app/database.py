import os

from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

# En producción, define DATABASE_URL como variable de entorno.
# Formato: postgresql://usuario:password@host:5432/nombre_bd
DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql://usuario:password@localhost:5432/campusconnect",
)

engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def get_db():
    """Dependencia de FastAPI: abre una sesión de BD por request y la cierra al final."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
