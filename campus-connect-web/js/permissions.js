// ============================================================================
// MOTOR DE PERMISOS GRANULARES A MEDIDA (SIN ROLES RÍGIDOS)
// ============================================================================

const STORAGE_MAPA_PERMISOS = "cc_unach_permisos_por_usuario_v1";

// Obtiene el diccionario local { [email_o_id]: ["redes.compartir", ...] }
function obtenerMapaPermisosLocal() {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_MAPA_PERMISOS) || "{}");
    } catch (_) {
        return {};
    }
}

function guardarPermisosEnMapaLocal(usuario, listaPermisos) {
    const mapa = obtenerMapaPermisosLocal();
    const limpios = [...new Set(listaPermisos)];
    if (usuario.id !== undefined) mapa[`id:${usuario.id}`] = limpios;
    if (usuario.email) mapa[`email:${usuario.email.toLowerCase().trim()}`] = limpios;
    localStorage.setItem(STORAGE_MAPA_PERMISOS, JSON.stringify(mapa));

    // Si el administrador modificó sus propios permisos, actualizar sesión
    if (userState.email && usuario.email && userState.email.toLowerCase().trim() === usuario.email.toLowerCase().trim()) {
        userState.permisos = limpios;
        localStorage.setItem("user_permisos", JSON.stringify(limpios));
        configurarPermisosInterfaz();
    }
}

// Convierte un rol tradicional del backend en su lista inicial de permisos si el usuario aún no tiene permisos personalizados
function permisosPorDefectoSegunRol(rol) {
    const r = String(rol || "").toLowerCase().trim();
    if (r === "administrador" || r === "admin" || r === "superadmin") {
        return CATALOGO_PERMISOS.map(p => p.id);
    }
    if (r === "editor" || r === "organizador") {
        return [
            "eventos.publicar",
            "redes.compartir",
            "asistencia.gestionar",
            "pasados.ver",
            "evidencias.gestionar",
            "estadisticas.ver"
        ];
    }
    // Soporte por si el campo `rol` en la base de datos guarda una cadena "permisos:a,b,c"
    if (r.startsWith("permisos:")) {
        return String(rol).slice(9).split(",").map(x => x.trim()).filter(Boolean);
    }
    return [];
}

// Resuelve los permisos efectivos de cualquier objeto usuario
function resolverPermisosDeUsuario(u) {
    if (!u) return [];
    // 1. Si el backend ya devuelve el arreglo `permisos`
    if (Array.isArray(u.permisos)) {
        return u.permisos;
    }
    // 2. Si el campo `rol` codifica permisos personalizados ("permisos:redes.compartir,estadisticas.ver")
    if (typeof u.rol === "string" && u.rol.startsWith("permisos:")) {
        return u.rol.slice(9).split(",").map(s => s.trim()).filter(Boolean);
    }
    // 3. Si existe configuración personalizada guardada en el mapa local
    const mapa = obtenerMapaPermisosLocal();
    const porEmail = u.email ? mapa[`email:${u.email.toLowerCase().trim()}`] : undefined;
    if (Array.isArray(porEmail)) return porEmail;
    const porId = u.id !== undefined ? mapa[`id:${u.id}`] : undefined;
    if (Array.isArray(porId)) return porId;

    // 4. Respaldo inicial según rol base
    return permisosPorDefectoSegunRol(u.rol);
}

// Obtiene la lista de permisos activos del usuario en sesión (o de la vista previa si está simulando)
function obtenerPermisosSesionActiva() {
    if (Array.isArray(userState.simulandoPermisos)) {
        return userState.simulandoPermisos;
    }
    if (Array.isArray(userState.permisos)) {
        return userState.permisos;
    }
    const mapa = obtenerMapaPermisosLocal();
    if (userState.email) {
        const guardados = mapa[`email:${userState.email.toLowerCase().trim()}`];
        if (Array.isArray(guardados)) return guardados;
    }
    return permisosPorDefectoSegunRol(userState.role);
}

// Función principal para consultar si el usuario actual tiene un permiso específico
function tienePermiso(clavePermiso) {
    const activos = obtenerPermisosSesionActiva();
    return activos.includes(clavePermiso) || activos.includes("usuarios.permisos");
}

// Verifica si el usuario actual es administrador de permisos real (ignora simulación para poder salir de ella)
function esAdminReal() {
    const mapa = obtenerMapaPermisosLocal();
    const guardados = userState.email ? mapa[`email:${userState.email.toLowerCase().trim()}`] : null;
    const base = Array.isArray(userState.permisos)
        ? userState.permisos
        : (Array.isArray(guardados) ? guardados : permisosPorDefectoSegunRol(userState.role));
    return base.includes("usuarios.permisos") || String(userState.role || "").toLowerCase() === "administrador";
}

// Etiqueta descriptiva del perfil de acceso según sus permisos activos
function describirNivelAcceso(listaPermisos) {
    const total = CATALOGO_PERMISOS.length;
    if (!listaPermisos || listaPermisos.length === 0) {
        return { texto: "Lectura de Cartelera", clase: "bg-slate-100 text-slate-600 border-slate-300", icono: "fa-eye" };
    }
    if (listaPermisos.includes("usuarios.permisos") || listaPermisos.length >= total) {
        return { texto: "Acceso Total (Admin)", clase: "bg-[#F57C00]/15 text-[#F57C00] border-[#F57C00]/40", icono: "fa-crown" };
    }
    if (listaPermisos.length === 1) {
        const info = CATALOGO_PERMISOS.find(p => p.id === listaPermisos[0]);
        return {
            texto: info ? `Acceso: ${info.etiquetaCorta}` : "1 Permiso Activo",
            clase: "bg-[#0093FF]/15 text-[#0093FF] border-[#0093FF]/30",
            icono: info ? info.icono : "fa-key"
        };
    }
    return {
        texto: `${listaPermisos.length} Permisos Personalizados`,
        clase: "bg-emerald-500/15 text-emerald-300 border-emerald-400/30",
        icono: "fa-sliders"
    };
}

// Actualiza toda la interfaz (pestañas, botones y barra superior) según los permisos activos
function configurarPermisosInterfaz() {
    const activos = obtenerPermisosSesionActiva();
    const resumen = describirNivelAcceso(activos);

    const badge = document.getElementById("user-badge");
    if (badge) {
        badge.innerHTML = `<i class="fa-solid ${resumen.icono} mr-1.5"></i>${esc(resumen.texto)}`;
    }

    // Mostrar u ocultar aviso de simulación de vista
    const bannerSim = document.getElementById("banner-simulacion-permisos");
    if (bannerSim) {
        const simulando = Array.isArray(userState.simulandoPermisos);
        bannerSim.classList.toggle("hidden", !simulando);
        if (simulando) {
            const nombres = activos.map(id => CATALOGO_PERMISOS.find(p => p.id === id)?.etiquetaCorta || id);
            document.getElementById("texto-simulacion-detalle").innerText =
                nombres.length ? nombres.join(" + ") : "Ninguno (Vista de Estudiante / Solo Cartelera)";
        }
    }

    // Botón para probar vistas de permisos (visible para quien administra permisos)
    const btnSimulador = document.getElementById("btn-simular-vista");
    if (btnSimulador) {
        btnSimulador.classList.toggle("hidden", !esAdminReal());
    }

    // Visibilidad de pestañas según permisos granulares
    const verPasados = tienePermiso("pasados.ver") || tienePermiso("evidencias.gestionar");
    const verEstadisticas = tienePermiso("estadisticas.ver");
    const verUsuarios = tienePermiso("usuarios.permisos");
    const verAuditoria = tienePermiso("seguridad.auditoria");
    const puedePublicar = tienePermiso("eventos.publicar");

    document.getElementById("tab-btn-pasados")?.classList.toggle("hidden", !verPasados);
    document.getElementById("tab-btn-estadisticas")?.classList.toggle("hidden", !verEstadisticas);
    document.getElementById("tab-btn-usuarios")?.classList.toggle("hidden", !verUsuarios);
    document.getElementById("tab-btn-auditoria")?.classList.toggle("hidden", !verAuditoria);
    document.getElementById("btn-crear-evento")?.classList.toggle("hidden", !puedePublicar);

    // Si el usuario tiene ÚNICAMENTE permiso de Seguridad o Estadísticas y la pestaña actual le quedó oculta, redirigir
    const pestañaActiva = PESTAÑAS.find(t => !document.getElementById(`tab-${t}`)?.classList.contains("hidden")) || "eventos";
    const mapaVisibilidad = {
        eventos: true,
        pasados: verPasados,
        estadisticas: verEstadisticas,
        usuarios: verUsuarios,
        auditoria: verAuditoria
    };
    if (!mapaVisibilidad[pestañaActiva]) {
        cambiarPestaña("eventos");
    }
}

// Permite al Administrador probar en vivo cómo ve la plataforma un usuario con ciertos permisos
function iniciarSimulacionPermisos(listaPermisos, etiqueta = "") {
    userState.simulandoPermisos = [...listaPermisos];
    configurarPermisosInterfaz();
    cargarEventos();
    toast(etiqueta ? `Vista previa activada: ${etiqueta}` : "Vista previa de permisos activada.", "info");
}

function salirSimulacionPermisos() {
    userState.simulandoPermisos = null;
    configurarPermisosInterfaz();
    cargarEventos();
    toast("Volviste a tu vista de Administrador con todos tus permisos.", "exito");
}
