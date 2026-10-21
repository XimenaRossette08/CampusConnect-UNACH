"""Texto y HTML para compartir eventos en redes sociales (sin dependencias de FastAPI)."""
import html
import urllib.parse
from typing import List, Optional

from .fechas import fecha_larga

HASHTAGS = "#CampusConnect #UNACH"


def audiencia_texto(es_general: bool, facultad: Optional[str], carrera: Optional[str],
                    grupos: Optional[List[str]]) -> str:
    if es_general:
        return "Abierto a toda la comunidad UNACH"
    partes = []
    if facultad:
        partes.append(f"Facultad: {facultad}")
    if carrera:
        partes.append(f"Carrera: {carrera}")
    if grupos:
        partes.append("Grupo(s): " + ", ".join(grupos))
    return " · ".join(partes)


def construir_texto(evento, extra_hashtags: str = HASHTAGS) -> str:
    lineas = [f"📢 {evento.titulo}", ""]
    if evento.categoria:
        lineas.append(f"🏷️ {evento.categoria}")
    lineas.append(f"📅 {fecha_larga(evento.fecha_hora)}")
    lineas.append(f"📍 {evento.lugar}")
    aud = audiencia_texto(evento.es_general, evento.id_facultad_destino,
                          evento.id_carrera_destino, evento.grupos_destino)
    if aud:
        lineas.append(f"🎓 {aud}")
    lineas += ["", evento.descripcion.strip(), "", extra_hashtags]
    return "\n".join(lineas)


def url_facebook_share(enlace_publico: str, texto: str) -> str:
    params = urllib.parse.urlencode({"u": enlace_publico, "quote": texto[:1000]})
    return f"https://www.facebook.com/sharer/sharer.php?{params}"


def html_publico(evento, imagen_publica: Optional[str], enlace: str) -> str:
    e = lambda s: html.escape(str(s or ""), quote=True)  # noqa: E731
    descripcion = evento.descripcion.strip()
    resumen = (descripcion[:297] + "…") if len(descripcion) > 300 else descripcion
    og_img = f'<meta property="og:image" content="{e(imagen_publica)}">' if imagen_publica else ""
    img = (f'<img src="{e(imagen_publica)}" alt="" style="max-width:100%;border-radius:12px">'
           if imagen_publica else "")
    return f"""<!DOCTYPE html>
<html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{e(evento.titulo)} · Campus Connect UNACH</title>
<meta property="og:type" content="website">
<meta property="og:title" content="{e(evento.titulo)}">
<meta property="og:description" content="{e(fecha_larga(evento.fecha_hora))} · {e(evento.lugar)} — {e(resumen)}">
<meta property="og:url" content="{e(enlace)}">
{og_img}
</head>
<body style="font-family:system-ui,sans-serif;max-width:640px;margin:2rem auto;padding:0 1rem;color:#0C2340">
{img}
<h1>{e(evento.titulo)}</h1>
<p><strong>📅 {e(fecha_larga(evento.fecha_hora))}</strong><br>📍 {e(evento.lugar)}</p>
<p style="white-space:pre-wrap">{e(descripcion)}</p>
<p style="color:#64748b;font-size:.85rem">Campus Connect · UNACH</p>
</body></html>"""
