from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ..database import get_db
from ..models_auth import RegistroAuditoria, Rol, Usuario
from ..security import requiere_permiso

router = APIRouter(prefix="/admin", tags=["admin"])


class CambiarRolRequest(BaseModel):
    rol: str


class SegmentacionUsuarioRequest(BaseModel):
    id_facultad: Optional[str] = Field(default=None, max_length=120)
    id_carrera: Optional[str] = Field(default=None, max_length=120)
    grupos: Optional[List[str]] = None


@router.get("/usuarios")
def listar_usuarios(
    usuario_actual: Usuario = Depends(requiere_permiso("usuarios:leer")),
    db: Session = Depends(get_db),
):
    """Obtiene la lista de todos los usuarios registrados."""
    usuarios = db.query(Usuario).all()
    return [
        {
            "id": u.id,
            "nombre": u.nombre,
            "email": u.email,
            "rol": u.rol.nombre if u.rol else "Usuario Regular",
            "id_facultad": u.id_facultad,
            "id_carrera": u.id_carrera,
            "grupos": u.grupos or [],
        }
        for u in usuarios
    ]


@router.post("/usuarios/{usuario_id}/rol")
def cambiar_rol_usuario_post(
    usuario_id: int,
    datos: CambiarRolRequest,
    usuario_actual: Usuario = Depends(requiere_permiso("usuarios:gestionar")),
    db: Session = Depends(get_db),
):
    """Soporta peticiones POST enviadas por la interfaz web."""
    return _actualizar_rol(usuario_id, datos.rol, db)


@router.put("/usuarios/{usuario_id}/rol")
def cambiar_rol_usuario_put(
    usuario_id: int,
    datos: CambiarRolRequest,
    usuario_actual: Usuario = Depends(requiere_permiso("usuarios:gestionar")),
    db: Session = Depends(get_db),
):
    """Soporta peticiones PUT."""
    return _actualizar_rol(usuario_id, datos.rol, db)


def _actualizar_rol(usuario_id: int, nombre_rol: str, db: Session):
    usuario = db.query(Usuario).filter(Usuario.id == usuario_id).first()
    if not usuario:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Usuario no encontrado",
        )

    nuevo_rol = db.query(Rol).filter(Rol.nombre == nombre_rol).first()
    if not nuevo_rol:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"El rol '{nombre_rol}' no existe",
        )

    usuario.rol_id = nuevo_rol.id
    db.commit()
    return {"mensaje": f"Rol actualizado a {nombre_rol}"}


@router.put("/usuarios/{usuario_id}/segmentacion")
def actualizar_segmentacion_usuario(
    usuario_id: int,
    datos: SegmentacionUsuarioRequest,
    usuario_actual: Usuario = Depends(requiere_permiso("usuarios:gestionar")),
    db: Session = Depends(get_db),
):
    """Define facultad, carrera y grupo(s) de un alumno: de ahí depende qué eventos segmentados ve."""
    usuario = db.query(Usuario).filter(Usuario.id == usuario_id).first()
    if not usuario:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Usuario no encontrado")
    usuario.id_facultad = (datos.id_facultad or "").strip() or None
    usuario.id_carrera = (datos.id_carrera or "").strip() or None
    grupos = [g.strip()[:30] for g in (datos.grupos or []) if g and g.strip()]
    usuario.grupos = grupos[:30] or None
    db.commit()
    return {"mensaje": "Datos académicos actualizados."}


@router.get("/auditoria")
def obtener_auditoria(
    usuario_actual: Usuario = Depends(requiere_permiso("auditoria:leer")),
    db: Session = Depends(get_db),
):
    """Retorna los últimos 100 registros de auditoría del sistema."""
    registros = (
        db.query(RegistroAuditoria)
        .order_by(RegistroAuditoria.fecha_hora.desc())
        .limit(100)
        .all()
    )
    return [
        {
            "id": r.id,
            "fecha_hora": r.fecha_hora.isoformat() if r.fecha_hora else None,
            "usuario_email": r.usuario_email,
            "direccion_ip": r.direccion_ip,
            "accion": r.accion,
            "exitoso": r.exitoso,
        }
        for r in registros
    ]