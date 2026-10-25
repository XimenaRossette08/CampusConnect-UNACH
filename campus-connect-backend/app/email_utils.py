import os
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from dotenv import load_dotenv

load_dotenv()

SMTP_HOST = os.getenv("SMTP_HOST", "smtp.gmail.com").strip()
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER = os.getenv("SMTP_USER", "").strip()
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "").strip()
SMTP_FROM = os.getenv("SMTP_FROM", "").strip() or SMTP_USER


def enviar_correo_codigo_2fa(destinatario: str, codigo: str) -> bool:
    if not SMTP_USER or not SMTP_PASSWORD:
        print("[SMTP] ERROR: Faltan credenciales en .env (SMTP_USER y SMTP_PASSWORD).")
        print(f"[SMTP] Para pruebas locales tu código es: {codigo}")
        return False

    msg = MIMEMultipart("alternative")
    msg["Subject"] = f"Tu código de acceso es {codigo} — Campus Connect UNACH"
    msg["From"] = f"Campus Connect UNACH <{SMTP_FROM}>"
    msg["To"] = destinatario

    texto_plano = f"Campus Connect UNACH\n\nTu código de verificación es: {codigo}\n\nVálido por 5 minutos."
    html_contenido = f"""<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #F4F7FB; margin: 0; padding: 24px; color: #122652; }}
        .card {{ max-width: 480px; margin: 0 auto; background: #FFFFFF; border-radius: 20px; border: 1px solid #E2E8F0; overflow: hidden; box-shadow: 0 4px 14px rgba(18, 38, 82, 0.08); }}
        .header {{ background: linear-gradient(135deg, #0C1B3B 0%, #122652 100%); padding: 26px 20px; text-align: center; color: #FFFFFF; border-bottom: 4px solid #F57C00; }}
        .header h1 {{ margin: 0; font-size: 20px; font-weight: 800; }}
        .header p {{ margin: 4px 0 0; font-size: 11px; color: #FFB74D; font-weight: bold; letter-spacing: 0.5px; text-transform: uppercase; }}
        .content {{ padding: 30px 24px; text-align: center; }}
        .instruction {{ font-size: 13px; color: #5B6670; line-height: 1.6; margin-bottom: 20px; }}
        .code-box {{ background-color: #F4F7FB; border: 2px dashed #0093FF; border-radius: 14px; padding: 16px 24px; display: inline-block; margin: 0 auto 20px; }}
        .code {{ font-family: monospace; font-size: 32px; font-weight: 800; letter-spacing: 6px; color: #122652; margin: 0; }}
        .validity {{ font-size: 11px; color: #F57C00; font-weight: bold; margin-top: 6px; }}
        .security-note {{ font-size: 11px; color: #8C98A4; line-height: 1.5; border-top: 1px solid #EEF2F6; padding-top: 16px; }}
        .footer {{ background: #F8FAFC; padding: 14px; text-align: center; font-size: 10px; color: #64748B; }}
    </style>
</head>
<body>
    <div class="card">
        <div class="header">
            <h1>Campus Connect UNACH</h1>
            <p>Escuela de Tecnologías Digitales Aplicadas (ETDA)</p>
        </div>
        <div class="content">
            <p class="instruction">Hola. Estás iniciando sesión en <b>Campus Connect UNACH</b>.<br>Ingresa este código en tu aplicación para continuar:</p>
            <div class="code-box">
                <div class="code">{codigo}</div>
                <div class="validity">Válido durante 5 minutos</div>
            </div>
            <p class="security-note">Al verificar tu código en este dispositivo, tu sesión se mantendrá activa por <b>30 días</b>.</p>
        </div>
        <div class="footer">“Por la conciencia de la necesidad de servir” — UNACH</div>
    </div>
</body>
</html>"""

    msg.attach(MIMEText(texto_plano, "plain", "utf-8"))
    msg.attach(MIMEText(html_contenido, "html", "utf-8"))

    if SMTP_PORT == 465:
        server = smtplib.SMTP_SSL(SMTP_HOST, SMTP_PORT, timeout=15)
    else:
        server = smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15)
        server.ehlo()
        server.starttls()
        server.ehlo()

    try:
        server.login(SMTP_USER, SMTP_PASSWORD)
        server.sendmail(SMTP_FROM, [destinatario], msg.as_string())
        print(f"[SMTP] Correo 2FA enviado exitosamente a {destinatario}")
        return True
    except Exception as e:
        print(f"[SMTP] ERROR al autenticar o enviar correo: {e}")
        raise
    finally:
        try:
            server.quit()
        except Exception:
            pass