import json
import logging
import os
import smtplib
import urllib.request
import urllib.error
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

logger = logging.getLogger("uvicorn.error")

# Configuración de remitente institucional por defecto
SENDER_NAME = "Campus Connect UNACH • ETDA"
SENDER_EMAIL = os.getenv("SMTP_USER", "campusconnect1c0de@gmail.com")


def _generar_plantilla_html_2fa(codigo: str, destinatario: str) -> str:
    """Genera un correo con diseño institucional oficial UNACH - ETDA."""
    return f"""<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <style>
    body {{ font-family: 'Segoe UI', Arial, sans-serif; background-color: #F4F7FB; margin: 0; padding: 24px; color: #1E293B; }}
    .card {{ max-width: 520px; margin: 0 auto; background: #FFFFFF; border-radius: 20px; overflow: hidden; box-shadow: 0 8px 24px rgba(18,38,82,0.08); border: 1px solid #E2E8F0; }}
    .header {{ background: #122652; padding: 28px 24px; text-align: center; color: #FFFFFF; border-bottom: 4px solid #F57C00; }}
    .header h1 {{ margin: 0; font-size: 20px; font-weight: 800; letter-spacing: 0.3px; }}
    .header p {{ margin: 4px 0 0 0; font-size: 11px; color: #FDE68A; font-weight: 600; text-transform: uppercase; }}
    .body {{ padding: 28px 24px; text-align: center; }}
    .body p {{ font-size: 13px; line-height: 1.5; color: #475569; margin: 0 0 16px 0; }}
    .code-box {{ display: inline-block; background: #F8FAFC; border: 2px dashed #0093FF; border-radius: 14px; padding: 14px 28px; margin: 12px 0 20px 0; }}
    .code {{ font-family: monospace; font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #122652; }}
    .badge {{ display: inline-block; background: #ECFDF5; color: #047857; font-size: 11px; font-weight: 700; padding: 4px 12px; border-radius: 50px; border: 1px solid #A7F3D0; margin-bottom: 16px; }}
    .footer {{ background: #F8FAFC; padding: 16px 24px; text-align: center; font-size: 11px; color: #64748B; border-top: 1px solid #E2E8F0; line-height: 1.4; }}
  </style>
</head>
<body>
  <div class="card">
    <div class="header">
      <p>Escuela de Tecnologías Digitales Aplicadas · UNACH</p>
      <h1>Campus Connect UNACH</h1>
    </div>
    <div class="body">
      <span class="badge">Autenticación de Dos Factores (2FA)</span>
      <p>Hola. Hemos recibido una solicitud para iniciar sesión en tu cuenta <b>{destinatario}</b>. Ingresa el siguiente código de seguridad en la plataforma:</p>
      <div class="code-box">
        <span class="code">{codigo}</span>
      </div>
      <p style="font-size: 12px; color: #64748B;">Este código tiene una vigencia de <b>30 días</b> de confianza en tu navegador o app. Si tú no realizaste este intento, ignora este mensaje o cambia tu contraseña.</p>
    </div>
    <div class="footer">
      <span>“Por la conciencia de la necesidad de servir”</span><br>
      <span>Universidad Autónoma de Chiapas · Campus I Tuxtla Gutiérrez</span>
    </div>
  </div>
</body>
</html>"""


def _enviar_via_brevo_api(destinatario: str, codigo: str, api_key: str) -> bool:
    """Envía correo mediante HTTPS REST API de Brevo (Puerto 443 - 300 correos/día gratis)."""
    url = "https://api.brevo.com/v3/smtp/email"
    headers = {
        "accept": "application/json",
        "api-key": api_key.strip(),
        "content-type": "application/json",
        "user-agent": "CampusConnect/1.0",
    }
    payload = {
        "sender": {"name": SENDER_NAME, "email": SENDER_EMAIL},
        "to": [{"email": destinatario}],
        "subject": f"Código de Seguridad 2FA: {codigo} — Campus Connect UNACH",
        "htmlContent": _generar_plantilla_html_2fa(codigo, destinatario),
    }

    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=12) as resp:
            if resp.status in (200, 201, 202):
                logger.info(f"[BREVO API] Correo 2FA enviado exitosamente a {destinatario} (Status {resp.status})")
                print(f"[BREVO API] Correo 2FA enviado exitosamente a {destinatario}")
                return True
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8", errors="ignore")
        logger.error(f"[BREVO API] Error HTTP {e.code}: {err_msg}")
        print(f"[BREVO API] Error HTTP {e.code}: {err_msg}")
    except Exception as e:
        logger.error(f"[BREVO API] Error de conexión: {e}")
        print(f"[BREVO API] Error de conexión: {e}")
    return False


def _enviar_via_resend_api(destinatario: str, codigo: str, api_key: str) -> bool:
    """Envía correo mediante HTTPS REST API de Resend (Puerto 443 - 100 correos/día gratis)."""
    url = "https://api.resend.com/emails"
    headers = {
        "Authorization": f"Bearer {api_key.strip()}",
        "Content-Type": "application/json",
        "User-Agent": "CampusConnect/1.0",
    }
    payload = {
        "from": "Campus Connect UNACH <onboarding@resend.dev>",
        "to": [destinatario],
        "subject": f"Código de Seguridad 2FA: {codigo} — Campus Connect UNACH",
        "html": _generar_plantilla_html_2fa(codigo, destinatario),
    }

    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=12) as resp:
            if resp.status in (200, 201, 202):
                logger.info(f"[RESEND API] Correo 2FA enviado exitosamente a {destinatario}")
                print(f"[RESEND API] Correo 2FA enviado exitosamente a {destinatario}")
                return True
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8", errors="ignore")
        logger.error(f"[RESEND API] Error HTTP {e.code}: {err_msg}")
        print(f"[RESEND API] Error HTTP {e.code}: {err_msg}")
    except Exception as e:
        logger.error(f"[RESEND API] Error de conexión: {e}")
        print(f"[RESEND API] Error de conexión: {e}")
    return False


def _enviar_via_smtp_tradicional(destinatario: str, codigo: str) -> bool:
    """Fallback: intento por SMTP estándar (funciona en local si no hay firewall bloqueando puertos)."""
    host = os.getenv("SMTP_HOST", "smtp.gmail.com")
    user = os.getenv("SMTP_USER", "")
    pwd = (os.getenv("SMTP_PASSWORD", "") or "").replace(" ", "").strip()
    port = int(os.getenv("SMTP_PORT", "587"))

    if not user or not pwd:
        return False

    msg = MIMEMultipart("alternative")
    msg["Subject"] = f"Código de Seguridad 2FA: {codigo} — Campus Connect UNACH"
    msg["From"] = f"{SENDER_NAME} <{user}>"
    msg["To"] = destinatario
    msg.attach(MIMEText(f"Tu código de verificación 2FA es: {codigo}", "plain", "utf-8"))
    msg.attach(MIMEText(_generar_plantilla_html_2fa(codigo, destinatario), "html", "utf-8"))

    # Intentar por el puerto configurado (ej. 587 con STARTTLS)
    try:
        if port == 465:
            with smtplib.SMTP_SSL(host, port, timeout=6) as server:
                server.login(user, pwd)
                server.send_message(msg)
                return True
        else:
            with smtplib.SMTP(host, port, timeout=6) as server:
                server.starttls()
                server.login(user, pwd)
                server.send_message(msg)
                return True
    except OSError as e:
        # [Errno 101] Network is unreachable (bloqueo típico de Render Free Tier)
        print(f"[SMTP] Puerto {port} bloqueado por la red de Render: {e}")
    except Exception as e:
        print(f"[SMTP] Error al enviar correo: {e}")

    # Si 587 falló por red, intentar puerto 465 directo
    if port != 465:
        try:
            with smtplib.SMTP_SSL(host, 465, timeout=6) as server:
                server.login(user, pwd)
                server.send_message(msg)
                return True
        except Exception:
            pass

    return False


def enviar_correo_codigo_2fa(destinatario: str, codigo: str) -> bool:
    """
    Función principal llamada desde auth.py para el envío del código 2FA.
    Estrategia de triple canal tolerante a la nube:
    1. Si existe BREVO_API_KEY -> envía por HTTPS puerto 443 (libre de bloqueos de Render).
    2. Si existe RESEND_API_KEY -> envía por HTTPS puerto 443 (libre de bloqueos de Render).
    3. Si no hay API key -> intenta por SMTP tradicional (útil para entorno local).
    """
    # 1. Intentar por Brevo API (Recomendado para Render Free Tier: 300 emails/día a cualquier correo)
    brevo_key = os.getenv("BREVO_API_KEY", "").strip()
    if brevo_key:
        if _enviar_via_brevo_api(destinatario, codigo, brevo_key):
            return True

    # 2. Intentar por Resend API (Alternativa HTTPS puerto 443)
    resend_key = os.getenv("RESEND_API_KEY", "").strip()
    if resend_key:
        if _enviar_via_resend_api(destinatario, codigo, resend_key):
            return True

    # 3. Intentar por SMTP tradicional (falla en Render con Errno 101, pero funciona en local)
    if _enviar_via_smtp_tradicional(destinatario, codigo):
        return True

    # Si todos los canales fallan, el código sigue vivo y disponible en consola y Supabase
    print(f"[2FA AVISO] El correo no pudo enviarse vía red. Código respaldado en BD y logs: {codigo}")
    return False