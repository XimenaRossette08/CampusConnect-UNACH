import json
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel, EmailStr
from sqlalchemy import text
from sqlalchemy.orm import Session

from ..auditoria import registrar_auditoria
from ..database import get_db
from ..models_auth import Permiso, RegistroAuditoria, Rol, Usuario
from ..schemas_auth import (
    AsignarPermisosRequest,
    AsignarRolRequest,
    PermisoOut,
    RegistroAuditoriaOut,
    RolCreate,
    RolOut,
    UsuarioOut,
)
from ..security import hash_password, requiere_permiso, verificar_rol_admin

router = APIRouter(prefix="/admin", tags=["administración"])

PERMISOS_PANEL_VALIDOS = {
    "catalogos.gestionar",
    "eventos.publicar",
    "eventos.eliminar",
    "redes.compartir",
    "asistencia.gestionar",
    "pasados.ver",
    "evidencias.gestionar",
    "estadisticas.ver",
    "seguridad.auditoria",
    "usuarios.permisos",
}


def _limpiar_permisos_granulares(lista: Optional[List[str]]) -> Optional[List[str]]:
    if lista is None:
        return None
    limpios: List[str] = []
    for item in lista:
        clave = str(item or "").strip()
        if clave in PERMISOS_PANEL_VALIDOS and clave not in limpios:
            limpios.append(clave)
    return limpios


def _ip(request: Request) -> str:
    return request.client.host if request.client else "desconocida"


def _rol_out(rol: Rol) -> RolOut:
    return RolOut(
        id=rol.id,
        nombre=rol.nombre,
        permisos=[p.clave for p in rol.permisos],
    )


class CrearEmpleadoAdminRequest(BaseModel):
    nombre: str
    email: EmailStr
    password: str
    rol: Optional[str] = "Editor"
    permisos: Optional[List[str]] = None


@router.get("/usuarios", response_model=List[UsuarioOut])
def listar_usuarios(
    usuario_actual: Usuario = Depends(requiere_permiso("usuarios:leer", "usuarios:gestionar")),
    db: Session = Depends(get_db),
):
    usuarios = db.query(Usuario).order_by(Usuario.id.asc()).all()
    return [UsuarioOut.desde_usuario(u) for u in usuarios]


@router.post("/usuarios", response_model=UsuarioOut, status_code=status.HTTP_201_CREATED)
def crear_usuario_desde_admin(
    request: Request,
    datos: CrearEmpleadoAdminRequest,
    usuario_actual: Usuario = Depends(verificar_rol_admin),
    db: Session = Depends(get_db),
):
    """Alta directa de empleados/usuarios desde el Panel Web (protegido con 403 Forbidden)."""
    email_limpio = datos.email.strip().lower()
    if db.query(Usuario).filter(Usuario.email == email_limpio).first():
        raise HTTPException(status_code=400, detail="Ese correo ya está registrado")

    nombre_rol = (datos.rol or "Usuario Regular").strip()
    rol = db.query(Rol).filter(Rol.nombre == nombre_rol).first()
    if rol is None:
        rol = db.query(Rol).filter(Rol.nombre == "Usuario Regular").first()

    permisos_limpios = _limpiar_permisos_granulares(datos.permisos)
    permisos_json = json.dumps(permisos_limpios, ensure_ascii=False) if permisos_limpios is not None else None

    nuevo = Usuario(
        nombre=datos.nombre.strip(),
        email=email_limpio,
        password_hash=hash_password(datos.password),
        rol_id=rol.id,
        activo=True,
    )
    if hasattr(nuevo, "permisos_personalizados"):
        nuevo.permisos_personalizados = permisos_json

    db.add(nuevo)
    db.commit()
    db.refresh(nuevo)

    if permisos_json is not None:
        try:
            db.execute(
                text("UPDATE usuarios SET permisos_personalizados = :p WHERE id = :uid"),
                {"p": permisos_json, "uid": nuevo.id},
            )
            db.commit()
            db.refresh(nuevo)
        except Exception:
            db.rollback()

    registrar_auditoria(
        db,
        usuario_email=usuario_actual.email,
        ip=_ip(request),
        accion=f"Alta de empleado: {nuevo.email} (rol: {rol.nombre}, permisos: {len(permisos_limpios or [])})",
    )
    return UsuarioOut.desde_usuario(nuevo)


@router.delete("/usuarios/{usuario_id}")
def eliminar_usuario(
    request: Request,
    usuario_id: int,
    usuario_actual: Usuario = Depends(verificar_rol_admin),
    db: Session = Depends(get_db),
):
    """
    Protegido con Depends(verificar_rol_admin) -> Devuelve 403 Forbidden si un alumno
    intenta eliminar un usuario.
    """
    if usuario_actual.id == usuario_id:
        raise HTTPException(status_code=400, detail="No puedes eliminar tu propia cuenta activa")

    objetivo = db.query(Usuario).filter(Usuario.id == usuario_id).first()
    if objetivo is None:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")

    email_borrado = objetivo.email
    db.delete(objetivo)
    db.commit()

    registrar_auditoria(
        db,
        usuario_email=usuario_actual.email,
        ip=_ip(request),
        accion=f"Usuario eliminado: {email_borrado}",
    )
    return {"mensaje": f"Usuario {email_borrado} eliminado correctamente"}


@router.post("/usuarios/{usuario_id}/rol", response_model=UsuarioOut)
@router.put("/usuarios/{usuario_id}/permisos", response_model=UsuarioOut)
def asignar_rol_a_usuario(
    request: Request,
    usuario_id: int,
    datos: AsignarRolRequest,
    usuario_actual: Usuario = Depends(requiere_permiso("usuarios:gestionar")),
    db: Session = Depends(get_db),
):
    objetivo = db.query(Usuario).filter(Usuario.id == usuario_id).first()
    if objetivo is None:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")

    permisos_limpios = _limpiar_permisos_granulares(datos.permisos)

    rol = None
    if datos.rol_id is not None:
        rol = db.query(Rol).filter(Rol.id == datos.rol_id).first()
    elif datos.rol:
        rol = db.query(Rol).filter(Rol.nombre == datos.rol).first()
    elif permisos_limpios is not None:
        if "usuarios.permisos" in permisos_limpios or "seguridad.auditoria" in permisos_limpios:
            rol = db.query(Rol).filter(Rol.nombre == "Administrador").first()
        elif len(permisos_limpios) > 0:
            rol = db.query(Rol).filter(Rol.nombre == "Editor").first()
        else:
            rol = db.query(Rol).filter(Rol.nombre == "Usuario Regular").first()

    if rol is None:
        raise HTTPException(status_code=404, detail="Rol no encontrado")

    objetivo.rol_id = rol.id
    if permisos_limpios is not None:
        permisos_json = json.dumps(permisos_limpios, ensure_ascii=False)
        if hasattr(objetivo, "permisos_personalizados"):
            objetivo.permisos_personalizados = permisos_json
        try:
            db.execute(
                text("UPDATE usuarios SET permisos_personalizados = :p WHERE id = :uid"),
                {"p": permisos_json, "uid": objetivo.id},
            )
        except Exception:
            pass

    db.commit()
    db.refresh(objetivo)

    registrar_auditoria(
        db,
        usuario_email=usuario_actual.email,
        ip=_ip(request),
        accion=f"Permisos actualizados para {objetivo.email} -> {rol.nombre} ({len(permisos_limpios or [])} permisos)",
    )
    return UsuarioOut.desde_usuario(objetivo)


# ==============================================================================
# EXIGENCIA 6 DEL PDF: CREACIÓN EXPLÍCITA DE ROLES Y ASIGNACIÓN DE PERMISOS
# ==============================================================================

@router.get("/permisos", response_model=List[PermisoOut])
def listar_permisos(
    usuario_actual: Usuario = Depends(requiere_permiso("roles:gestionar", "usuarios:leer")),
    db: Session = Depends(get_db),
):
    return db.query(Permiso).order_by(Permiso.id.asc()).all()


@router.get("/roles", response_model=List[RolOut])
def listar_roles(
    usuario_actual: Usuario = Depends(requiere_permiso("roles:gestionar", "usuarios:leer")),
    db: Session = Depends(get_db),
):
    roles = db.query(Rol).order_by(Rol.id.asc()).all()
    return [_rol_out(r) for r in roles]


@router.post("/roles", response_model=RolOut, status_code=status.HTTP_201_CREATED)
def crear_rol(
    request: Request,
    datos: RolCreate,
    usuario_actual: Usuario = Depends(requiere_permiso("roles:gestionar")),
    db: Session = Depends(get_db),
):
    nombre = datos.nombre.strip()
    if not nombre:
        raise HTTPException(status_code=400, detail="El nombre del rol no puede estar vacío")

    existente = db.query(Rol).filter(Rol.nombre.ilike(nombre)).first()
    if existente:
        raise HTTPException(status_code=400, detail=f"El rol '{nombre}' ya existe")

    nuevo_rol = Rol(nombre=nombre)
    if datos.claves_permisos:
        permisos_db = db.query(Permiso).filter(Permiso.clave.in_(datos.claves_permisos)).all()
        nuevo_rol.permisos = permisos_db

    db.add(nuevo_rol)
    db.commit()
    db.refresh(nuevo_rol)

    registrar_auditoria(
        db,
        usuario_email=usuario_actual.email,
        ip=_ip(request),
        accion=f"Rol creado: {nuevo_rol.nombre} ({len(nuevo_rol.permisos)} permisos)",
    )
    return _rol_out(nuevo_rol)


@router.post("/roles/{rol_id}/permisos", response_model=RolOut)
@router.put("/roles/{rol_id}/permisos", response_model=RolOut)
def actualizar_permisos_de_rol(
    request: Request,
    rol_id: int,
    datos: AsignarPermisosRequest,
    usuario_actual: Usuario = Depends(requiere_permiso("roles:gestionar")),
    db: Session = Depends(get_db),
):
    rol = db.query(Rol).filter(Rol.id == rol_id).first()
    if rol is None:
        raise HTTPException(status_code=404, detail="Rol no encontrado")

    permisos_db = db.query(Permiso).filter(Permiso.clave.in_(datos.claves_permisos)).all()
    rol.permisos = permisos_db
    db.commit()
    db.refresh(rol)

    registrar_auditoria(
        db,
        usuario_email=usuario_actual.email,
        ip=_ip(request),
        accion=f"Permisos actualizados para el rol '{rol.nombre}': {', '.join(datos.claves_permisos)}",
    )
    return _rol_out(rol)


@router.delete("/roles/{rol_id}")
def eliminar_rol(
    request: Request,
    rol_id: int,
    usuario_actual: Usuario = Depends(verificar_rol_admin),
    db: Session = Depends(get_db),
):
    rol = db.query(Rol).filter(Rol.id == rol_id).first()
    if rol is None:
        raise HTTPException(status_code=404, detail="Rol no encontrado")

    if rol.nombre in {"Administrador", "Editor", "Usuario Regular"}:
        raise HTTPException(status_code=400, detail="No se pueden eliminar los roles base del sistema")

    rol_regular = db.query(Rol).filter(Rol.nombre == "Usuario Regular").first()
    if rol_regular:
        for u in rol.usuarios:
            u.rol_id = rol_regular.id

    nombre_borrado = rol.nombre
    db.delete(rol)
    db.commit()

    registrar_auditoria(
        db,
        usuario_email=usuario_actual.email,
        ip=_ip(request),
        accion=f"Rol eliminado: {nombre_borrado}",
    )
    return {"mensaje": f"Rol '{nombre_borrado}' eliminado correctamente"}


@router.get("/auditoria", response_model=List[RegistroAuditoriaOut])
def consultar_auditoria(
    usuario_actual: Usuario = Depends(requiere_permiso("auditoria:leer")),
    db: Session = Depends(get_db),
):
    return (
        db.query(RegistroAuditoria)
        .order_by(RegistroAuditoria.fecha_hora.desc())
        .limit(500)
        .all()
    )