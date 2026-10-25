import os
import smtplib
import ssl
from email.header import Header
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.utils import formataddr
from pathlib import Path

from dotenv import load_dotenv

# Cargar variables del archivo .env en la raíz del backend
_BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(_BASE_DIR / ".env", override=True)
load_dotenv(Path(__file__).resolve().parent / ".env", override=True)


def _limpiar_env(valor: str) -> str:
    return (valor or "").strip().strip('"').strip("'")


def enviar_correo_codigo_2fa(destinatario: str, codigo: str) -> bool:
    # Recargar .env por si se editó mientras el servidor estaba corriendo
    load_dotenv(_BASE_DIR / ".env", override=True)

    smtp_host = _limpiar_env(os.getenv("SMTP_HOST", "smtp.gmail.com")) or "smtp.gmail.com"
    smtp_port_cfg = int(_limpiar_env(os.getenv("SMTP_PORT", "465")) or "465")
    smtp_user = _limpiar_env(os.getenv("SMTP_USER", ""))
    # Quitamos comillas y espacios internos de la Contraseña de Aplicación de Google (16 letras)
    smtp_password = _limpiar_env(os.getenv("SMTP_PASSWORD", "")).replace(" ", "")
    smtp_from = _limpiar_env(os.getenv("SMTP_FROM", "")) or smtp_user

    if not smtp_user or not smtp_password:
        print(f"[SMTP] ERROR: Faltan SMTP_USER y SMTP_PASSWORD en {_BASE_DIR / '.env'}")
        print(f"[SMTP] Para pruebas locales tu código es: {codigo}")
        return False

    msg = MIMEMultipart("alternative")
    msg["Subject"] = Header(f"Tu código de acceso es {codigo} - Campus Connect UNACH", "utf-8")
    msg["From"] = formataddr((str(Header("Campus Connect UNACH", "utf-8")), smtp_from))
    msg["To"] = destinatario

    texto_plano = (
        f"Campus Connect UNACH\n\n"
        f"Tu código de verificación es: {codigo}\n\n"
        f"Este código y tu verificación tienen una vigencia de 1 mes (30 días)."
    )
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
                <div class="validity">Vigencia de verificación: 30 días (1 mes)</div>
            </div>
            <p class="security-note">Al verificar tu código en este dispositivo, no se te volverá a solicitar otro código durante <b>1 mes (30 días)</b>.</p>
        </div>
        <div class="footer">“Por la conciencia de la necesidad de servir” — UNACH</div>
    </div>
</body>
</html>"""

    msg.attach(MIMEText(texto_plano, "plain", "utf-8"))
    msg.attach(MIMEText(html_contenido, "html", "utf-8"))

    # Probar primero por SSL directo (puerto 465) y, si la red o antivirus bloquea, probar STARTTLS (puerto 587)
    puertos_a_probar = [465, 587] if smtp_port_cfg == 465 else [smtp_port_cfg, 465, 587]
    puertos_a_probar = list(dict.fromkeys(puertos_a_probar))

    ultimo_error = None
    contexto_ssl = ssl.create_default_context()

    for puerto in puertos_a_probar:
        server = None
        try:
            if puerto == 465:
                server = smtplib.SMTP_SSL(smtp_host, 465, timeout=20, context=contexto_ssl)
                server.ehlo()
            else:
                server = smtplib.SMTP(smtp_host, puerto, timeout=20)
                server.ehlo()
                server.starttls(context=contexto_ssl)
                server.ehlo()

            server.login(smtp_user, smtp_password)
            server.sendmail(smtp_from, [destinatario], msg.as_string())
            print(f"[SMTP] Correo 2FA enviado exitosamente a {destinatario} (puerto {puerto})")
            return True
        except Exception as e:
            ultimo_error = e
            print(f"[SMTP] Intento por puerto {puerto} falló ({type(e).__name__}: {e}). Probando alternativa...")
        finally:
            if server is not None:
                try:
                    server.quit()
                except Exception:
                    pass

    print(
        f"[SMTP] ERROR FINAL al enviar correo con usuario '{smtp_user}' "
        f"(longitud password={len(smtp_password)}): {ultimo_error}"
    )
    return False