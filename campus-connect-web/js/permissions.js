// ============================================================================
// MOTOR DE PERMISOS GRANULARES A MEDIDA (SIN ROLES RÍGIDOS)
// ============================================================================

const STORAGE_MAPA_PERMISOS = "cc_unach_permisos_por_usuario_v1";

// Obtiene el conjunto de IDs válidos definidos en CATALOGO_PERMISOS (10 permisos del panel)
function obtenerSetPermisosValidosCatalogo() {
    if (typeof CATALOGO_PERMISOS === "undefined" || !Array.isArray(CATALOGO_PERMISOS)) {
        return new Set([
            "catalogos.gestionar",
            "eventos.publicar",
            "eventos.eliminar",
            "redes.compartir",
            "asistencia.gestionar",
            "pasados.ver",
            "evidencias.gestionar",
            "estadisticas.ver",
            "seguridad.auditoria",
            "usuarios.permisos"
        ]);
    }
    return new Set(CATALOGO_PERMISOS.map(p => p.id));
}

// Traduce claves del backend (con dos puntos, ej. "eventos:escribir") a IDs del panel (con punto, ej. "eventos.publicar")
function traducirClavesBackendACatalogo(clavesBackend, rolFallback = "") {
    const validos = obtenerSetPermisosValidosCatalogo();
    const r = String(rolFallback || "").toLowerCase().trim();
    if (r === "administrador" || r === "admin" || r === "superadmin") {
        return [...validos];
    }
    if (!Array.isArray(clavesBackend) || clavesBackend.length === 0) {
        return permisosPorDefectoSegunRol(rolFallback);
    }

    const resultado = new Set();
    for (const raw of clavesBackend) {
        const clave = String(raw || "").trim();
        if (!clave) continue;
        // Si ya es un ID válido del catálogo del panel (ej. "redes.compartir"), conservarlo directamente
        if (validos.has(clave)) {
            resultado.add(clave);
            continue;
        }
        // Si es una clave de la tabla 'permisos' del backend, traducirla a los permisos del panel correspondientes
        if (clave === "eventos:escribir") {
            resultado.add("eventos.publicar");
            resultado.add("redes.compartir");
            resultado.add("asistencia.gestionar");
            resultado.add("pasados.ver");
            resultado.add("evidencias.gestionar");
            resultado.add("catalogos.gestionar");
        } else if (clave === "eventos:eliminar") {
            resultado.add("eventos.eliminar");
        } else if (clave === "estadisticas:leer") {
            resultado.add("estadisticas.ver");
            resultado.add("pasados.ver");
        } else if (clave === "auditoria:leer") {
            resultado.add("seguridad.auditoria");
        } else if (clave === "usuarios:gestionar" || clave === "roles:gestionar") {
            resultado.add("usuarios.permisos");
        }
        // Nota: "eventos:leer" y "usuarios:leer" por sí solos no otorgan permisos administrativos especiales
    }
    return [...resultado].filter(id => validos.has(id));
}

// Filtra y deduplica cualquier arreglo de permisos para que SOLO contenga IDs válidos de CATALOGO_PERMISOS (máx. 10)
function normalizarListaPermisosCatalogo(listaRaw, rolFallback = "") {
    if (!Array.isArray(listaRaw)) return [];
    const validos = obtenerSetPermisosValidosCatalogo();

    // Separar IDs que ya pertenecen a CATALOGO_PERMISOS de posibles claves de backend ("eventos:...")
    const directos = [];
    const clavesBackend = [];

    for (const item of listaRaw) {
        const s = String(item || "").trim();
        if (!s) continue;
        if (validos.has(s)) {
            if (!directos.includes(s)) directos.push(s);
        } else if (s.includes(":")) {
            clavesBackend.push(s);
        }
    }

    // Si ya existen permisos granulares directos del catálogo (o el arreglo estaba vacío intencionalmente), usarlos sin mezclar claves de backend
    if (directos.length > 0 || clavesBackend.length === 0) {
        return directos;
    }

    // Si el arreglo solo contenía claves de backend (ej. respuesta cruda de Rol), traducirlas
    return traducirClavesBackendACatalogo(clavesBackend, rolFallback);
}

// Obtiene y sanea automáticamente el diccionario local { [email_o_id]: ["redes.compartir", ...] }
function obtenerMapaPermisosLocal() {
    try {
        const crudo = JSON.parse(localStorage.getItem(STORAGE_MAPA_PERMISOS) || "{}");
        if (!crudo || typeof crudo !== "object") return {};
        const validos = obtenerSetPermisosValidosCatalogo();
        let huboCambios = false;
        const limpio = {};

        for (const [k, arr] of Object.entries(crudo)) {
            if (Array.isArray(arr)) {
                const depurados = [...new Set(arr.map(x => String(x || "").trim()).filter(x => validos.has(x)))];
                if (depurados.length !== arr.length) {
                    huboCambios = true;
                }
                limpio[k] = depurados;
            }
        }

        if (huboCambios) {
            localStorage.setItem(STORAGE_MAPA_PERMISOS, JSON.stringify(limpio));
        }
        return limpio;
    } catch (_) {
        return {};
    }
}

function guardarPermisosEnMapaLocal(usuario, listaPermisos) {
    const mapa = obtenerMapaPermisosLocal();
    const limpios = normalizarListaPermisosCatalogo(listaPermisos);
    if (usuario.id !== undefined && usuario.id !== null) {
        mapa[`id:${usuario.id}`] = limpios;
    }
    if (usuario.email) {
        mapa[`email:${usuario.email.toLowerCase().trim()}`] = limpios;
    }
    localStorage.setItem(STORAGE_MAPA_PERMISOS, JSON.stringify(mapa));

    // Si el administrador modificó sus propios permisos, actualizar sesión inmediatamente
    if (userState.email && usuario.email && userState.email.toLowerCase().trim() === usuario.email.toLowerCase().trim()) {
        userState.permisos = limpios;
        localStorage.setItem("user_permisos", JSON.stringify(limpios));
        configurarPermisosInterfaz();
    }
}

// Convierte un rol tradicional del backend en su lista inicial de permisos si el usuario aún no tiene permisos personalizados
function permisosPorDefectoSegunRol(rol) {
    const validos = obtenerSetPermisosValidosCatalogo();
    const r = String(rol || "").toLowerCase().trim();
    if (r === "administrador" || r === "admin" || r === "superadmin") {
        return [...validos];
    }
    if (r === "editor" || r === "organizador" || r === "auxiliar") {
        return [
            "eventos.publicar",
            "redes.compartir",
            "asistencia.gestionar",
            "pasados.ver",
            "evidencias.gestionar",
            "estadisticas.ver"
        ].filter(id => validos.has(id));
    }
    if (r.startsWith("permisos:")) {
        return normalizarListaPermisosCatalogo(String(rol).slice(9).split(","));
    }
    return [];
}

// Resuelve los permisos efectivos de cualquier objeto usuario devolviendo SIEMPRE IDs válidos de CATALOGO_PERMISOS (0 a 10)
function resolverPermisosDeUsuario(u) {
    if (!u) return [];

    // 1. Prioridad máxima: si el backend ya devuelve `permisos_personalizados` guardados en la tabla `usuarios`
    if (Array.isArray(u.permisos_personalizados)) {
        const limpios = normalizarListaPermisosCatalogo(u.permisos_personalizados);
        guardarPermisosEnMapaLocal(u, limpios);
        return limpios;
    }

    // 2. Si existe configuración personalizada guardada en el mapa local por email o ID
    const mapa = obtenerMapaPermisosLocal();
    const porEmail = u.email ? mapa[`email:${u.email.toLowerCase().trim()}`] : undefined;
    if (Array.isArray(porEmail)) {
        return normalizarListaPermisosCatalogo(porEmail);
    }
    const porId = u.id !== undefined ? mapa[`id:${u.id}`] : undefined;
    if (Array.isArray(porId)) {
        return normalizarListaPermisosCatalogo(porId);
    }

    // 3. Si el campo `rol` codifica permisos personalizados ("permisos:redes.compartir,estadisticas.ver")
    if (typeof u.rol === "string" && u.rol.startsWith("permisos:")) {
        return normalizarListaPermisosCatalogo(u.rol.slice(9).split(","));
    }

    // 4. Si el backend devuelve `u.permisos` (claves del rol o IDs del catálogo), traducirlos y filtrarlos contra CATALOGO_PERMISOS
    if (Array.isArray(u.permisos) && u.permisos.length > 0) {
        return normalizarListaPermisosCatalogo(u.permisos, u.rol);
    }

    // 5. Respaldo inicial según rol base
    return permisosPorDefectoSegunRol(u.rol);
}

// Obtiene la lista de permisos activos del usuario en sesión (o de la vista previa si está simulando)
function obtenerPermisosSesionActiva() {
    if (Array.isArray(userState.simulandoPermisos)) {
        return normalizarListaPermisosCatalogo(userState.simulandoPermisos);
    }
    const mapa = obtenerMapaPermisosLocal();
    if (userState.email) {
        const guardados = mapa[`email:${userState.email.toLowerCase().trim()}`];
        if (Array.isArray(guardados)) {
            return normalizarListaPermisosCatalogo(guardados);
        }
    }
    if (Array.isArray(userState.permisos)) {
        return normalizarListaPermisosCatalogo(userState.permisos, userState.role);
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
    const base = Array.isArray(guardados)
        ? normalizarListaPermisosCatalogo(guardados)
        : (Array.isArray(userState.permisos)
            ? normalizarListaPermisosCatalogo(userState.permisos, userState.role)
            : permisosPorDefectoSegunRol(userState.role));
    return base.includes("usuarios.permisos") || String(userState.role || "").toLowerCase() === "administrador";
}

// Etiqueta descriptiva del perfil de acceso según sus permisos activos
function describirNivelAcceso(listaPermisos) {
    const limpios = normalizarListaPermisosCatalogo(listaPermisos);
    const total = CATALOGO_PERMISOS.length;
    if (limpios.length === 0) {
        return { texto: "Lectura de Cartelera", clase: "bg-slate-100 text-slate-600 border-slate-300", icono: "fa-eye" };
    }
    if (limpios.length >= total) {
        return { texto: `Acceso Total (${total}/${total})`, clase: "bg-[#F57C00]/15 text-[#F57C00] border-[#F57C00]/40", icono: "fa-crown" };
    }
    if (limpios.length === 1) {
        const info = CATALOGO_PERMISOS.find(p => p.id === limpios[0]);
        return {
            texto: info ? `Acceso: ${info.etiquetaCorta}` : "1 Permiso Activo",
            clase: "bg-[#0093FF]/15 text-[#0093FF] border-[#0093FF]/30",
            icono: info ? info.icono : "fa-key"
        };
    }
    return {
        texto: `${limpios.length}/${total} Permisos Activos`,
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
    const verCatalogos = esAdminReal() || tienePermiso("catalogos.gestionar") || tienePermiso("eventos.publicar");
    const verUsuarios = tienePermiso("usuarios.permisos");
    const verAuditoria = tienePermiso("seguridad.auditoria");
    const puedePublicar = tienePermiso("eventos.publicar");

    document.getElementById("tab-btn-pasados")?.classList.toggle("hidden", !verPasados);
    document.getElementById("tab-btn-estadisticas")?.classList.toggle("hidden", !verEstadisticas);
    document.getElementById("tab-btn-catalogos")?.classList.toggle("hidden", !verCatalogos);
    document.getElementById("tab-btn-usuarios")?.classList.toggle("hidden", !verUsuarios);
    document.getElementById("tab-btn-auditoria")?.classList.toggle("hidden", !verAuditoria);
    document.getElementById("btn-crear-evento")?.classList.toggle("hidden", !puedePublicar);

    // Si el usuario tiene ÚNICAMENTE permiso de Seguridad o Estadísticas y la pestaña actual le quedó oculta, redirigir
    const pestañaActiva = PESTAÑAS.find(t => !document.getElementById(`tab-${t}`)?.classList.contains("hidden")) || "eventos";
    const mapaVisibilidad = {
        eventos: true,
        pasados: verPasados,
        estadisticas: verEstadisticas,
        catalogos: verCatalogos,
        usuarios: verUsuarios,
        auditoria: verAuditoria
    };
    if (!mapaVisibilidad[pestañaActiva]) {
        cambiarPestaña("eventos");
    }
}

// Permite al Administrador probar en vivo cómo ve la plataforma un usuario con ciertos permisos
function iniciarSimulacionPermisos(listaPermisos, etiqueta = "") {
    userState.simulandoPermisos = normalizarListaPermisosCatalogo(listaPermisos);
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