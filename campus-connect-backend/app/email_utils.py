import os
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.utils import formataddr  # Permite agregar un nombre legible al remitente

SMTP_SERVER = os.getenv("SMTP_SERVER", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))

# 1. Cambias el correo y la contraseña por los de la nueva cuenta
SMTP_USER = os.getenv("SMTP_USER", "campusconnect1C0DE@gmail.com")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "dcrb vpni oatu koeb")


def enviar_correo_codigo_2fa(email_destino: str, codigo: str) -> bool:
    mensaje = MIMEMultipart()
    
    # 2. Formato con Nombre de la App + Correo
    # Esto mostrará "Campus Connect UNACH <campusconnect1C0DE@gmail.com>" en la bandeja
    mensaje["From"] = formataddr(("Campus Connect UNACH", SMTP_USER))
    mensaje["To"] = email_destino
    mensaje["Subject"] = f"{codigo} es tu código de verificación - UNACH"

    cuerpo = f"""
    Hola,

    Tu código de verificación para acceder a Campus Connect es:

    {codigo}

    Este código es válido durante 5 minutos. Si no solicitaste este acceso, ignora este mensaje.
    """
    mensaje.attach(MIMEText(cuerpo, "plain", "utf-8"))

    try:
        servidor = smtplib.SMTP(SMTP_SERVER, SMTP_PORT, timeout=10)
        servidor.starttls()
        servidor.login(SMTP_USER, SMTP_PASSWORD)
        servidor.send_message(mensaje)
        servidor.quit()
        return True
    except Exception as e:
        print(f"[ERROR EMAIL] No se pudo enviar el correo: {e}")
        return False