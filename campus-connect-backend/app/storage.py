"""
Almacenamiento seguro de archivos subidos (flyers, materiales y evidencias).

Seguridad:
- El nombre del archivo en disco lo genera el servidor (UUID); jamás se usa el del cliente.
- El tipo se detecta por los primeros bytes (magic bytes), no por la extensión ni por el
  Content-Type que declara el cliente.
- Solo se admiten JPG, PNG, WEBP, GIF y PDF. SVG/HTML quedan fuera a propósito (riesgo XSS).
- Tamaño máximo configurable (MAX_UPLOAD_MB, 10 MB por defecto).
"""
import os
import uuid
from typing import BinaryIO, Optional, Tuple

UPLOADS_DIR = os.path.abspath(
    os.getenv("UPLOADS_DIR", os.path.join(os.path.dirname(__file__), "..", "uploads"))
)
MAX_BYTES = int(float(os.getenv("MAX_UPLOAD_MB", "10")) * 1024 * 1024)

EXTENSION_POR_MIME = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "image/gif": ".gif",
    "application/pdf": ".pdf",
}


class ArchivoInvalido(ValueError):
    pass


def detectar_mime(cabecera: bytes) -> Optional[str]:
    if cabecera.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if cabecera.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if cabecera.startswith((b"GIF87a", b"GIF89a")):
        return "image/gif"
    if cabecera[:4] == b"RIFF" and cabecera[8:12] == b"WEBP":
        return "image/webp"
    if cabecera.startswith(b"%PDF-"):
        return "application/pdf"
    return None


def guardar_archivo(fileobj: BinaryIO) -> Tuple[str, str, int]:
    """Guarda el contenido y devuelve (nombre_guardado, mime, tamano_en_bytes)."""
    cabecera = fileobj.read(16)
    mime = detectar_mime(cabecera)
    if mime is None:
        raise ArchivoInvalido("Formato no permitido. Usa JPG, PNG, WEBP, GIF o PDF.")

    os.makedirs(UPLOADS_DIR, exist_ok=True)
    nombre = f"{uuid.uuid4().hex}{EXTENSION_POR_MIME[mime]}"
    ruta = os.path.join(UPLOADS_DIR, nombre)

    tamano = len(cabecera)
    try:
        with open(ruta, "wb") as destino:
            destino.write(cabecera)
            while True:
                bloque = fileobj.read(1024 * 256)
                if not bloque:
                    break
                tamano += len(bloque)
                if tamano > MAX_BYTES:
                    raise ArchivoInvalido(
                        f"El archivo supera el máximo de {MAX_BYTES // (1024 * 1024)} MB."
                    )
                destino.write(bloque)
    except Exception:
        if os.path.exists(ruta):
            os.remove(ruta)
        raise
    return nombre, mime, tamano


def borrar_archivo(nombre_guardado: str) -> None:
    # basename evita cualquier intento de salir de la carpeta de subidas
    ruta = os.path.join(UPLOADS_DIR, os.path.basename(nombre_guardado))
    try:
        os.remove(ruta)
    except FileNotFoundError:
        pass


def limpiar_nombre_original(nombre: Optional[str]) -> str:
    base = os.path.basename((nombre or "archivo").replace("\\", "/"))
    base = "".join(c for c in base if c.isprintable())
    return (base or "archivo")[:150]
