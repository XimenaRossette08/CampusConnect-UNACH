"""Genera el PDF de la lista de asistencia de un evento (reportlab)."""
import io
from datetime import datetime
from typing import Dict, List
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

AZUL = colors.HexColor("#0C2340")
DORADO = colors.HexColor("#B8860B")


def _p(texto, estilo):
    return Paragraph(escape(str(texto or "")), estilo)


def generar_pdf_asistencia(evento: Dict[str, str], filas: List[Dict[str, str]], generado_por: str) -> bytes:
    """
    evento: titulo, fecha, lugar, audiencia, categoria (ya formateados como texto).
    filas: nombre, email, carrera, grupos, registro, asistio (bool).
    """
    buffer = io.BytesIO()
    doc = SimpleDocTemplate(
        buffer, pagesize=landscape(A4), leftMargin=1.5 * cm, rightMargin=1.5 * cm,
        topMargin=1.5 * cm, bottomMargin=1.5 * cm, title=f"Asistencia - {evento['titulo']}",
        author="Campus Connect UNACH",
    )
    base = getSampleStyleSheet()
    titulo = ParagraphStyle("t", parent=base["Title"], textColor=AZUL, fontSize=18, alignment=0)
    sub = ParagraphStyle("s", parent=base["Normal"], fontSize=10, leading=14)
    celda = ParagraphStyle("c", parent=base["Normal"], fontSize=8, leading=10)
    cab = ParagraphStyle("h", parent=celda, textColor=colors.white, fontName="Helvetica-Bold")

    total = len(filas)
    asistieron = sum(1 for f in filas if f.get("asistio"))

    historia = [
        _p("Lista de asistencia", ParagraphStyle("k", parent=sub, textColor=DORADO, fontName="Helvetica-Bold")),
        _p(evento["titulo"], titulo),
        _p(f"Fecha: {evento['fecha']}    |    Lugar: {evento['lugar']}", sub),
    ]
    if evento.get("audiencia"):
        historia.append(_p(f"Dirigido a: {evento['audiencia']}", sub))
    historia += [
        _p(f"Registrados: {total}    |    Asistieron: {asistieron}", sub),
        Spacer(1, 0.4 * cm),
    ]

    encabezado = [_p(t, cab) for t in
                  ["No.", "Nombre", "Correo institucional", "Carrera", "Grupo", "Registro", "Asistió", "Firma"]]
    datos = [encabezado]
    for i, f in enumerate(filas, 1):
        datos.append([
            _p(i, celda), _p(f.get("nombre"), celda), _p(f.get("email"), celda),
            _p(f.get("carrera") or "—", celda), _p(f.get("grupos") or "—", celda),
            _p(f.get("registro"), celda), _p("Sí" if f.get("asistio") else "", celda), "",
        ])
    if total == 0:
        datos.append([_p("", celda), _p("Aún no hay alumnos registrados.", celda)] + [""] * 6)

    anchos = [1.1 * cm, 5.2 * cm, 5.6 * cm, 3.4 * cm, 2.2 * cm, 3.3 * cm, 1.6 * cm, 4.2 * cm]
    tabla = Table(datos, colWidths=anchos, repeatRows=1)
    tabla.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), AZUL),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F1F5F9")]),
        ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#CBD5E1")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("TOPPADDING", (0, 1), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 1), (-1, -1), 6),
    ]))
    historia.append(tabla)

    pie = f"Generado por {generado_por} el {datetime.now():%d/%m/%Y %H:%M} · Campus Connect UNACH"

    def _pie(canvas, documento):
        canvas.saveState()
        canvas.setFont("Helvetica", 7.5)
        canvas.setFillColor(colors.HexColor("#64748B"))
        canvas.drawString(1.5 * cm, 0.9 * cm, pie)
        canvas.drawRightString(landscape(A4)[0] - 1.5 * cm, 0.9 * cm, f"Página {documento.page}")
        canvas.restoreState()

    doc.build(historia, onFirstPage=_pie, onLaterPages=_pie)
    return buffer.getvalue()
