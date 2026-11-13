import html
import json
import logging
import os
import smtplib
import urllib.error
import urllib.request
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from .security import enmascarar_email

logger = logging.getLogger("uvicorn.error")

# Remitente institucional con respaldo garantizado al correo verificado en Brevo
SENDER_NAME = os.getenv("SENDER_NAME", "Campus Connect UNACH • ETDA")
FALLBACK_VERIFIED_SENDER = "campusconnect1c0de@gmail.com"
SENDER_EMAIL = (
    os.getenv("BREVO_SENDER_EMAIL", "").strip()
    or os.getenv("SMTP_USER", "").strip()
    or FALLBACK_VERIFIED_SENDER
)
RESEND_FROM = os.getenv("RESEND_FROM", "Campus Connect UNACH <onboarding@resend.dev>")


def _generar_plantilla_html_2fa(codigo: str, destinatario: str) -> str:
    dest_seguro = html.escape(destinatario)
    cod_seguro = html.escape(codigo)
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
      <p>Hola. Hemos recibido una solicitud para iniciar sesión en tu cuenta <b>{dest_seguro}</b>. Ingresa el siguiente código de seguridad en la plataforma:</p>
      <div class="code-box">
        <span class="code">{cod_seguro}</span>
      </div>
      <p style="font-size: 12px; color: #64748B;">Este código expira en <b>10 minutos</b> y es de un solo uso. Si no realizaste esta solicitud, ignora este mensaje y cambia tu contraseña.</p>
    </div>
    <div class="footer">
      <span>“Por la conciencia de la necesidad de servir”</span><br>
      <span>Universidad Autónoma de Chiapas · Campus I Tuxtla Gutiérrez</span>
    </div>
  </div>
</body>
</html>"""


def _generar_plantilla_html_recuperacion(token_recuperacion: str, destinatario: str) -> str:
    dest_seguro = html.escape(destinatario)
    token_seguro = html.escape(token_recuperacion)
    return f"""<!DOCTYPE html>
<html lang="es">
<head><meta charset="utf-8"></head>
<body style="font-family: 'Segoe UI', Arial, sans-serif; background:#F4F7FB; padding:24px; color:#1E293B;">
  <div style="max-width:520px; margin:0 auto; background:#FFFFFF; border-radius:18px; padding:24px; border:1px solid #E2E8F0;">
    <h2 style="color:#122652; margin-top:0;">Recuperación de Contraseña · Campus Connect UNACH</h2>
    <p style="font-size:13px; color:#475569;">Se solicitó restablecer la contraseña de la cuenta <b>{dest_seguro}</b>.</p>
    <p style="font-size:13px; color:#475569;">Utiliza el siguiente token de recuperación (válido por 30 minutos y de un solo uso):</p>
    <div style="background:#F8FAFC; border:1px dashed #0093FF; border-radius:12px; padding:12px; font-family:monospace; font-size:13px; word-break:break-all; color:#122652;">
      {token_seguro}
    </div>
    <p style="font-size:11px; color:#64748B; margin-top:16px;">Si no solicitaste este cambio, ignora este mensaje.</p>
  </div>
</body>
</html>"""


def _intentar_envio_brevo_con_remitente(
    destinatario: str,
    remitente_email: str,
    asunto: str,
    html_body: str,
    api_key: str,
) -> bool:
    email_mask = enmascarar_email(destinatario)
    url = "https://api.brevo.com/v3/smtp/email"
    headers = {
        "accept": "application/json",
        "api-key": api_key.strip(),
        "content-type": "application/json",
        "user-agent": "CampusConnect/1.0",
    }
    payload = {
        "sender": {"name": SENDER_NAME, "email": remitente_email},
        "to": [{"email": destinatario}],
        "subject": asunto,
        "htmlContent": html_body,
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
                logger.info(f"[BREVO API] Correo enviado a {email_mask} desde {remitente_email} (Status {resp.status})")
                return True
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8", errors="ignore")[:240]
        logger.error(f"[BREVO API] Error HTTP {e.code} con remitente {remitente_email}: {err_msg}")
    except Exception as e:
        logger.error(f"[BREVO API] Error de conexión a {email_mask}: {type(e).__name__}")
    return False


def _enviar_html_generico(destinatario: str, asunto: str, html_body: str, texto_plano: str) -> bool:
    email_mask = enmascarar_email(destinatario)

    # 1. Brevo REST API (Puerto 443 HTTPS — Funciona en Render donde el puerto 587 está bloqueado)
    brevo_key = os.getenv("BREVO_API_KEY", "").strip()
    if brevo_key:
        if _intentar_envio_brevo_con_remitente(destinatario, SENDER_EMAIL, asunto, html_body, brevo_key):
            return True
        # Si SENDER_EMAIL fue configurado con un dominio aún no verificado en Brevo, reintentar con el correo verificado
        if SENDER_EMAIL.lower() != FALLBACK_VERIFIED_SENDER.lower():
            if _intentar_envio_brevo_con_remitente(destinatario, FALLBACK_VERIFIED_SENDER, asunto, html_body, brevo_key):
                return True

    # 2. Resend REST API (Puerto 443 HTTPS)
    resend_key = os.getenv("RESEND_API_KEY", "").strip()
    if resend_key:
        url = "https://api.resend.com/emails"
        headers = {
            "Authorization": f"Bearer {resend_key}",
            "Content-Type": "application/json",
            "User-Agent": "CampusConnect/1.0",
        }
        payload = {
            "from": RESEND_FROM,
            "to": [destinatario],
            "subject": asunto,
            "html": html_body,
        }
        req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=12) as resp:
                if resp.status in (200, 201, 202):
                    logger.info(f"[RESEND API] Correo enviado a {email_mask}")
                    return True
        except urllib.error.HTTPError as e:
            err_msg = e.read().decode("utf-8", errors="ignore")[:240]
            logger.error(f"[RESEND API] Error HTTP {e.code}: {err_msg}")
        except Exception as e:
            logger.error(f"[RESEND API] Error de envío a {email_mask}: {type(e).__name__}")

    # 3. SMTP tradicional (Entorno local; en Render Free los puertos 587/465 están bloqueados)
    host = os.getenv("SMTP_HOST", "smtp.gmail.com")
    user = os.getenv("SMTP_USER", "")
    pwd = (os.getenv("SMTP_PASSWORD", "") or "").replace(" ", "").strip()
    port = int(os.getenv("SMTP_PORT", "587"))

    if user and pwd:
        msg = MIMEMultipart("alternative")
        msg["Subject"] = asunto
        msg["From"] = f"{SENDER_NAME} <{user}>"
        msg["To"] = destinatario
        msg.attach(MIMEText(texto_plano, "plain", "utf-8"))
        msg.attach(MIMEText(html_body, "html", "utf-8"))
        for puerto_intento in ([port, 465] if port != 465 else [465]):
            try:
                if puerto_intento == 465:
                    with smtplib.SMTP_SSL(host, 465, timeout=6) as server:
                        server.login(user, pwd)
                        server.send_message(msg)
                        return True
                else:
                    with smtplib.SMTP(host, puerto_intento, timeout=6) as server:
                        server.starttls()
                        server.login(user, pwd)
                        server.send_message(msg)
                        return True
            except Exception as e:
                logger.warning(f"[SMTP] Fallo en puerto {puerto_intento} para {email_mask}: {type(e).__name__}")

    return False


def enviar_correo_codigo_2fa(destinatario: str, codigo: str) -> bool:
    """
    Envía el código 2FA por Brevo / Resend / SMTP sin exponer jamás el código OTP en los logs.
    """
    asunto = "Código de Seguridad 2FA — Campus Connect UNACH"
    html_body = _generar_plantilla_html_2fa(codigo, destinatario)
    enviado = _enviar_html_generico(
        destinatario=destinatario,
        asunto=asunto,
        html_body=html_body,
        texto_plano=f"Tu código de verificación 2FA de Campus Connect UNACH es: {codigo}",
    )
    if not enviado:
        logger.warning(
            f"[2FA AVISO] No se pudo entregar el correo OTP a {enmascarar_email(destinatario)}. "
            "Verifica BREVO_API_KEY y que el remitente esté verificado en Brevo."
        )
    return enviado


def enviar_correo_recuperacion_password(destinatario: str, token_recuperacion: str) -> bool:
    """Envía por correo el token de recuperación de contraseña de un solo uso."""
    asunto = "Recuperación de Contraseña — Campus Connect UNACH"
    html_body = _generar_plantilla_html_recuperacion(token_recuperacion, destinatario)
    return _enviar_html_generico(
        destinatario=destinatario,
        asunto=asunto,
        html_body=html_body,
        texto_plano=f"Token de recuperación de contraseña (válido por 30 min): {token_recuperacion}",
    )