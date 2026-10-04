from typing import List

from fastapi import APIRouter, Depends, HTTPException, Request
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
from ..security import requiere_permiso

router = APIRouter(prefix="/admin", tags=["administración"])


@router.get("/usuarios", response_model=List[UsuarioOut])
def listar_usuarios(
    _admin: Usuario = Depends(requiere_permiso("usuarios:leer")),
    db: Session = Depends(get_db),
):
    return [UsuarioOut.desde_usuario(u) for u in db.query(Usuario).all()]


@router.patch("/usuarios/{usuario_id}/rol", response_model=UsuarioOut)
def cambiar_rol_de_usuario(
    usuario_id: int,
    datos: AsignarRolRequest,
    request: Request,
    admin: Usuario = Depends(requiere_permiso("usuarios:gestionar")),
    db: Session = Depends(get_db),
):
    usuario_objetivo = db.query(Usuario).filter(Usuario.id == usuario_id).first()
    if usuario_objetivo is None:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")

    rol = db.query(Rol).filter(Rol.id == datos.rol_id).first()
    if rol is None:
        raise HTTPException(status_code=404, detail="Rol no encontrado")

    usuario_objetivo.rol_id = rol.id
    db.commit()
    db.refresh(usuario_objetivo)

    ip = request.client.host if request.client else "desconocida"
    registrar_auditoria(
        db,
        usuario_email=admin.email,
        ip=ip,
        accion=f"Cambió el rol de {usuario_objetivo.email} a {rol.nombre}",
    )
    return UsuarioOut.desde_usuario(usuario_objetivo)


@router.get("/permisos", response_model=List[PermisoOut])
def listar_permisos(
    _admin: Usuario = Depends(requiere_permiso("roles:gestionar")),
    db: Session = Depends(get_db),
):
    return db.query(Permiso).all()


@router.get("/roles", response_model=List[RolOut])
def listar_roles(
    _admin: Usuario = Depends(requiere_permiso("roles:gestionar")),
    db: Session = Depends(get_db),
):
    return [RolOut(id=r.id, nombre=r.nombre, permisos=[p.clave for p in r.permisos]) for r in db.query(Rol).all()]


@router.post("/roles", response_model=RolOut, status_code=201)
def crear_rol(
    datos: RolCreate,
    request: Request,
    admin: Usuario = Depends(requiere_permiso("roles:gestionar")),
    db: Session = Depends(get_db),
):
    ya_existe = db.query(Rol).filter(Rol.nombre == datos.nombre).first()
    if ya_existe:
        raise HTTPException(status_code=400, detail="Ese rol ya existe")

    nuevo_rol = Rol(nombre=datos.nombre)
    db.add(nuevo_rol)
    db.commit()
    db.refresh(nuevo_rol)

    ip = request.client.host if request.client else "desconocida"
    registrar_auditoria(db, usuario_email=admin.email, ip=ip, accion=f"Creó el rol {nuevo_rol.nombre}")

    return RolOut(id=nuevo_rol.id, nombre=nuevo_rol.nombre, permisos=[])


@router.post("/roles/{rol_id}/permisos", response_model=RolOut)
def asignar_permisos_a_rol(
    rol_id: int,
    datos: AsignarPermisosRequest,
    request: Request,
    admin: Usuario = Depends(requiere_permiso("roles:gestionar")),
    db: Session = Depends(get_db),
):
    rol = db.query(Rol).filter(Rol.id == rol_id).first()
    if rol is None:
        raise HTTPException(status_code=404, detail="Rol no encontrado")

    permisos = db.query(Permiso).filter(Permiso.clave.in_(datos.claves_permisos)).all()
    rol.permisos = permisos
    db.commit()
    db.refresh(rol)

    ip = request.client.host if request.client else "desconocida"
    registrar_auditoria(db, usuario_email=admin.email, ip=ip, accion=f"Actualizó permisos del rol {rol.nombre}")

    return RolOut(id=rol.id, nombre=rol.nombre, permisos=[p.clave for p in rol.permisos])


@router.get("/auditoria", response_model=List[RegistroAuditoriaOut])
def ver_registro_auditoria(
    _admin: Usuario = Depends(requiere_permiso("auditoria:leer")),
    db: Session = Depends(get_db),
):
    return (
        db.query(RegistroAuditoria)
        .order_by(RegistroAuditoria.fecha_hora.desc())
        .limit(200)
        .all()
    )
