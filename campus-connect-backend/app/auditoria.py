from typing import Optional

from sqlalchemy.orm import Session

from .models_auth import RegistroAuditoria


def registrar_auditoria(
    db: Session,
    *,
    usuario_email: Optional[str],
    ip: str,
    accion: str,
    exitoso: bool = True,
) -> None:
    entrada = RegistroAuditoria(
        usuario_email=usuario_email,
        direccion_ip=ip,
        accion=accion,
        exitoso=exitoso,
    )
    db.add(entrada)
    db.commit()
