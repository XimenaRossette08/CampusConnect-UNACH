"""
Compartir eventos en Facebook / Instagram.

- Flujo "listo para publicar" (no necesita credenciales): el panel abre el diálogo de Facebook
  con el enlace público del evento (/e/{id}, trae etiquetas Open Graph) y, para Instagram,
  copia el texto y descarga el flyer.  Instagram no ofrece un enlace web para precargar
  publicaciones, por eso ese paso es manual.
- Flujo opcional por API de Meta (Graph API): si defines las variables de entorno de abajo,
  el servidor publica directamente en la página de Facebook y/o la cuenta profesional de Instagram.

Variables de entorno:
  PUBLIC_BASE_URL       URL pública del backend (https://...). Meta debe poder descargar el flyer.
  META_GRAPH_VERSION    (opcional) por defecto v21.0
  FACEBOOK_PAGE_ID, FACEBOOK_PAGE_TOKEN
  INSTAGRAM_USER_ID, INSTAGRAM_TOKEN   (cuenta Business/Creator ligada a una página)
"""
import json
import logging
import os
import urllib.error
import urllib.parse
import urllib.request
import uuid
from typing import Literal, Optional

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session, selectinload

from ..auditoria import registrar_auditoria
from ..database import get_db
from ..models import Evento
from ..models_auth import Usuario
from ..publicaciones import construir_texto, html_publico, url_facebook_share
from ..security import requiere_permiso

logger = logging.getLogger("campusconnect.social")

router = APIRouter(prefix="/social", tags=["redes sociales"])
router_publico = APIRouter(tags=["público"])


def _cfg(nombre: str) -> Optional[str]:
    return (os.getenv(nombre) or "").strip() or None


def _version() -> str:
    return _cfg("META_GRAPH_VERSION") or "v21.0"


def _base_publica(request: Request) -> str:
    return (_cfg("PUBLIC_BASE_URL") or str(request.base_url)).rstrip("/")


def _es_local(url: str) -> bool:
    host = urllib.parse.urlparse(url).hostname or ""
    return host in ("localhost", "127.0.0.1", "::1") or host.endswith(".local")


def _flyer_publico(evento: Evento, base: str) -> Optional[str]:
    for a in evento.archivos:
        if a.tipo == "material" and a.mime.startswith("image/"):
            return f"{base}/uploads/{a.nombre_guardado}"
    return None


def _flyer_jpeg_publico(evento: Evento, base: str) -> Optional[str]:
    # Instagram solo acepta JPEG de forma oficial
    for a in evento.archivos:
        if a.tipo == "material" and a.mime == "image/jpeg":
            return f"{base}/uploads/{a.nombre_guardado}"
    return None


def _evento(db: Session, evento_id: uuid.UUID) -> Evento:
    ev = db.query(Evento).options(selectinload(Evento.archivos)).filter(Evento.id == evento_id).first()
    if not ev:
        raise HTTPException(status_code=404, detail="Evento no encontrado.")
    return ev


@router.get("/estado")
def estado_integraciones(usuario: Usuario = Depends(requiere_permiso("eventos:escribir"))):
    return {
        "facebookApi": bool(_cfg("FACEBOOK_PAGE_ID") and _cfg("FACEBOOK_PAGE_TOKEN")),
        "instagramApi": bool(_cfg("INSTAGRAM_USER_ID") and (_cfg("INSTAGRAM_TOKEN") or _cfg("FACEBOOK_PAGE_TOKEN"))),
        "urlPublicaConfigurada": bool(_cfg("PUBLIC_BASE_URL")) and not _es_local(_cfg("PUBLIC_BASE_URL") or ""),
    }


@router.get("/eventos/{evento_id}/post")
def preparar_publicacion(
    request: Request,
    evento_id: uuid.UUID,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    ev = _evento(db, evento_id)
    base_publica = _base_publica(request)
    texto = construir_texto(ev)
    enlace = f"{base_publica}/e/{ev.id}"
    local = _es_local(base_publica)
    return {
        "texto": texto,
        "enlacePublico": enlace,
        "facebookShareUrl": url_facebook_share(enlace, texto),
        "imagenUrl": _flyer_publico(ev, str(request.base_url).rstrip("/")),  # para vista previa en el panel
        "advertencia": (
            "El servidor está en una dirección local: Facebook no podrá leer el enlace ni el flyer. "
            "Define PUBLIC_BASE_URL con una URL pública (https) para que la vista previa funcione."
            if local else None
        ),
    }


class PublicarRequest(BaseModel):
    plataforma: Literal["facebook", "instagram"]
    texto: Optional[str] = Field(default=None, max_length=2200)


def _graph_post(ruta: str, params: dict) -> dict:
    datos = urllib.parse.urlencode(params).encode()
    req = urllib.request.Request(f"https://graph.facebook.com/{_version()}/{ruta}", data=datos, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as exc:
        # El detalle va al log del servidor; nunca se devuelve al cliente (puede incluir datos de la cuenta).
        logger.error("Graph API %s -> %s %s", ruta, exc.code, exc.read().decode("utf-8", "replace")[:500])
    except Exception as exc:  # red, timeout, JSON inválido
        logger.error("Graph API %s falló: %s", ruta, exc)
    raise HTTPException(
        status_code=502,
        detail="Meta rechazó la publicación. Revisa credenciales/permisos y que el flyer sea accesible públicamente.",
    )


@router.post("/eventos/{evento_id}/publicar")
def publicar_por_api(
    request: Request,
    evento_id: uuid.UUID,
    datos: PublicarRequest,
    usuario: Usuario = Depends(requiere_permiso("eventos:escribir")),
    db: Session = Depends(get_db),
):
    ev = _evento(db, evento_id)
    base = _base_publica(request)
    if _es_local(base):
        raise HTTPException(status_code=400, detail="Define PUBLIC_BASE_URL con una URL pública para publicar por API.")
    texto = (datos.texto or construir_texto(ev)).strip()

    if datos.plataforma == "facebook":
        page_id, token = _cfg("FACEBOOK_PAGE_ID"), _cfg("FACEBOOK_PAGE_TOKEN")
        if not (page_id and token):
            raise HTTPException(status_code=503, detail="Facebook no está configurado en el servidor.")
        imagen = _flyer_publico(ev, base)
        if imagen:
            res = _graph_post(f"{page_id}/photos", {"url": imagen, "caption": texto, "access_token": token})
        else:
            res = _graph_post(f"{page_id}/feed", {"message": texto, "access_token": token})
        publicado = res.get("post_id") or res.get("id")
    else:
        ig_id = _cfg("INSTAGRAM_USER_ID")
        token = _cfg("INSTAGRAM_TOKEN") or _cfg("FACEBOOK_PAGE_TOKEN")
        if not (ig_id and token):
            raise HTTPException(status_code=503, detail="Instagram no está configurado en el servidor.")
        imagen = _flyer_jpeg_publico(ev, base)
        if not imagen:
            raise HTTPException(status_code=400, detail="Instagram requiere un flyer en formato JPG.")
        contenedor = _graph_post(f"{ig_id}/media", {"image_url": imagen, "caption": texto, "access_token": token})
        res = _graph_post(f"{ig_id}/media_publish", {"creation_id": contenedor.get("id", ""), "access_token": token})
        publicado = res.get("id")

    registrar_auditoria(db, usuario_email=usuario.email,
                        ip=request.client.host if request.client else "desconocida",
                        accion=f"Evento publicado en {datos.plataforma}: {ev.titulo}")
    return {"mensaje": f"Publicado en {datos.plataforma.capitalize()}.", "id": publicado}


@router_publico.get("/e/{evento_id}", response_class=HTMLResponse, include_in_schema=False)
def pagina_publica_evento(request: Request, evento_id: uuid.UUID, db: Session = Depends(get_db)):
    """Página pública mínima con etiquetas Open Graph, para que Facebook muestre título, datos y flyer."""
    ev = _evento(db, evento_id)
    base = _base_publica(request)
    return HTMLResponse(html_publico(ev, _flyer_publico(ev, base), f"{base}/e/{ev.id}"))
