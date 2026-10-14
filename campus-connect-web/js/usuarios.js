// ============================================================================
// MÓDULO DE GESTIÓN DE USUARIOS Y PERMISOS GRANULARES A MEDIDA
// (Sin asignación de grupos ni roles rígidos; control individual por permiso)
// ============================================================================

let usuariosCache = {};
let usuariosLista = [];
let filtroTextoUsuarios = "";
let filtroPermisoUsuarios = "todos";
let usuarioEditandoPermisos = null;
let setPermisosModal = new Set();

async function cargarUsuariosAdmin() {
    if (!tienePermiso("usuarios.permisos")) return;
    const tbody = document.getElementById("tabla-usuarios-body");
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="4" class="p-8 text-center text-slate-400"><i class="fa-solid fa-spinner fa-spin mr-2 text-[#F57C00]"></i> Cargando directorio de usuarios y matriz de permisos...</td></tr>`;

    try {
        const usuarios = await api("/admin/usuarios");
        usuariosLista = usuarios || [];
        usuariosCache = {};
        usuariosLista.forEach(u => {
            u._permisos = resolverPermisosDeUsuario(u);
            usuariosCache[u.id] = u;
        });

        actualizarContadoresUsuarios();
        renderizarTablaUsuarios();
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="4" class="p-6 text-center text-red-500 font-semibold">${esc(err.message || "Error al obtener usuarios.")}</td></tr>`;
    }
}

function actualizarContadoresUsuarios() {
    const total = usuariosLista.length;
    const conRedes = usuariosLista.filter(u => u._permisos.includes("redes.compartir")).length;
    const conSeguridad = usuariosLista.filter(u => u._permisos.includes("seguridad.auditoria")).length;
    const conEstadisticas = usuariosLista.filter(u => u._permisos.includes("estadisticas.ver") || u._permisos.includes("pasados.ver")).length;

    const setTxt = (id, val) => { const el = document.getElementById(id); if (el) el.innerText = val; };
    setTxt("kpi-usr-total", total);
    setTxt("kpi-usr-redes", conRedes);
    setTxt("kpi-usr-seguridad", conSeguridad);
    setTxt("kpi-usr-stats", conEstadisticas);
}

function filtrarTablaUsuarios() {
    filtroTextoUsuarios = (document.getElementById("buscar-usuario-input")?.value || "").trim().toLowerCase();
    filtroPermisoUsuarios = document.getElementById("filtro-permiso-select")?.value || "todos";
    renderizarTablaUsuarios();
}

function renderizarTablaUsuarios() {
    const tbody = document.getElementById("tabla-usuarios-body");
    if (!tbody) return;

    const filtrados = usuariosLista.filter(u => {
        const coincideTexto = !filtroTextoUsuarios ||
            (u.nombre || "").toLowerCase().includes(filtroTextoUsuarios) ||
            (u.email || "").toLowerCase().includes(filtroTextoUsuarios) ||
            String(u.id).includes(filtroTextoUsuarios);

        let coincidePermiso = true;
        if (filtroPermisoUsuarios === "con_permisos") {
            coincidePermiso = u._permisos.length > 0;
        } else if (filtroPermisoUsuarios === "sin_permisos") {
            coincidePermiso = u._permisos.length === 0;
        } else if (filtroPermisoUsuarios !== "todos") {
            coincidePermiso = u._permisos.includes(filtroPermisoUsuarios);
        }

        return coincideTexto && coincidePermiso;
    });

    if (filtrados.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" class="p-8 text-center text-slate-500">No se encontraron usuarios con ese criterio de búsqueda.</td></tr>`;
        return;
    }

    tbody.innerHTML = filtrados.map(u => {
        const iniciales = obtenerIniciales(u.nombre, u.email);
        const permisos = u._permisos || [];
        const esAccesoTotal = permisos.includes("usuarios.permisos") || permisos.length === CATALOGO_PERMISOS.length;

        const chipsHtml = permisos.length === 0
            ? `<span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-100 text-slate-500 text-xs font-medium border border-slate-200">
                <i class="fa-regular fa-eye"></i> Solo consulta de cartelera (Sin permisos especiales)
               </span>`
            : esAccesoTotal
                ? `<span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-[#122652] text-white text-xs font-bold shadow-sm">
                    <i class="fa-solid fa-crown text-[#F57C00]"></i> Todos los permisos habilitados (${permisos.length}/${CATALOGO_PERMISOS.length})
                   </span>`
                : permisos.map(pid => {
                    const info = CATALOGO_PERMISOS.find(p => p.id === pid);
                    if (!info) return "";
                    return `<span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg border text-xs font-bold ${info.colorBadge}">
                        <i class="fa-solid ${info.icono}"></i> ${esc(info.etiquetaCorta)}
                    </span>`;
                }).join("");

        return `
            <tr class="border-b border-slate-100 hover:bg-slate-50/80 transition">
                <td class="p-4">
                    <div class="flex items-center gap-3">
                        <div class="w-10 h-10 rounded-full bg-[#122652] text-white font-extrabold text-xs flex items-center justify-center border-2 border-[#F57C00] shrink-0 shadow-sm">
                            ${esc(iniciales)}
                        </div>
                        <div>
                            <div class="flex items-center gap-2">
                                <span class="font-extrabold text-[#122652] text-sm">${esc(u.nombre)}</span>
                                <span class="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-100 text-slate-500">#${Number(u.id)}</span>
                            </div>
                            <p class="text-xs text-[#5B6670] font-mono">${esc(u.email)}</p>
                        </div>
                    </div>
                </td>
                <td class="p-4">
                    <div class="flex flex-wrap gap-1.5 max-w-xl">
                        ${chipsHtml}
                    </div>
                </td>
                <td class="p-4 text-right whitespace-nowrap">
                    <div class="inline-flex items-center gap-2">
                        <button type="button" onclick="probarVistaDeUsuario(${Number(u.id)})"
                            class="text-xs font-bold text-[#5B6670] hover:text-[#122652] bg-white hover:bg-slate-100 border border-slate-200 px-3 py-2 rounded-xl transition"
                            title="Ver cómo luce la página con los permisos de esta persona">
                            <i class="fa-solid fa-eye text-[#0093FF] mr-1"></i> Probar vista
                        </button>
                        <button type="button" onclick="abrirModalPermisosUsuario(${Number(u.id)})"
                            class="text-xs font-bold bg-[#122652] hover:bg-[#193773] text-white px-3.5 py-2 rounded-xl shadow-sm transition flex items-center gap-1.5">
                            <i class="fa-solid fa-sliders text-[#F57C00]"></i> Configurar Permisos
                        </button>
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

function probarVistaDeUsuario(userId) {
    const u = usuariosCache[userId];
    if (!u) return;
    iniciarSimulacionPermisos(u._permisos || [], `${u.nombre} (${u.email})`);
}

// ---------- Modal de Configuración de Permisos a Medida ----------
function abrirModalPermisosUsuario(userId) {
    const u = usuariosCache[userId];
    if (!u) return;
    usuarioEditandoPermisos = u;
    setPermisosModal = new Set(u._permisos || []);

    document.getElementById("perm-modal-nombre").innerText = u.nombre;
    document.getElementById("perm-modal-email").innerText = u.email;
    document.getElementById("perm-modal-iniciales").innerText = obtenerIniciales(u.nombre, u.email);

    renderizarPlantillasRapidasModal();
    renderizarCasillasPermisosModal();
    document.getElementById("modal-permisos-usuario").classList.remove("hidden");
}

function cerrarModalPermisosUsuario() {
    document.getElementById("modal-permisos-usuario").classList.add("hidden");
    usuarioEditandoPermisos = null;
}

function renderizarPlantillasRapidasModal() {
    const cont = document.getElementById("perm-modal-plantillas");
    if (!cont) return;
    cont.innerHTML = PLANTILLAS_RAPIDAS_PERMISOS.map(pl => `
        <button type="button" onclick="aplicarPlantillaRapida('${esc(pl.id)}')"
            class="text-xs font-bold px-3 py-1.5 rounded-xl border border-slate-200 bg-white hover:border-[#0093FF] hover:bg-sky-50/50 text-[#122652] transition flex items-center gap-1.5">
            <i class="fa-solid ${esc(pl.icono)} text-[#F57C00]"></i>
            <span>${esc(pl.nombre)}</span>
        </button>
    `).join("");
}

function aplicarPlantillaRapida(plantillaId) {
    const pl = PLANTILLAS_RAPIDAS_PERMISOS.find(p => p.id === plantillaId);
    if (!pl) return;
    setPermisosModal = new Set(pl.permisos);
    renderizarCasillasPermisosModal();
}

function alternarPermisoIndividual(permisoId, checked) {
    if (checked) {
        setPermisosModal.add(permisoId);
    } else {
        setPermisosModal.delete(permisoId);
    }
    renderizarCasillasPermisosModal();
}

function renderizarCasillasPermisosModal() {
    const cont = document.getElementById("perm-modal-lista");
    if (!cont) return;

    const contadorEl = document.getElementById("perm-modal-contador");
    if (contadorEl) {
        contadorEl.innerText = `${setPermisosModal.size} de ${CATALOGO_PERMISOS.length} permisos seleccionados`;
    }

    // Agrupar por categoría funcional
    const grupos = ["Cartelera y Difusión", "Seguimiento y Archivo", "Inteligencia y Seguridad"];
    cont.innerHTML = grupos.map(nombreGrupo => {
        const items = CATALOGO_PERMISOS.filter(p => p.grupo === nombreGrupo);
        return `
            <div class="space-y-2.5">
                <h4 class="text-xs font-extrabold uppercase tracking-wider text-[#0093FF] border-b border-slate-100 pb-1">
                    ${esc(nombreGrupo)}
                </h4>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-2.5">
                    ${items.map(p => {
                        const activo = setPermisosModal.has(p.id);
                        return `
                            <label class="perm-card-label ${activo ? 'checked' : ''}">
                                <div class="flex items-start gap-3">
                                    <div class="w-8 h-8 rounded-lg ${activo ? 'bg-[#122652] text-[#F57C00]' : 'bg-slate-100 text-slate-500'} flex items-center justify-center shrink-0 mt-0.5 transition">
                                        <i class="fa-solid ${esc(p.icono)} text-sm"></i>
                                    </div>
                                    <div>
                                        <span class="block text-xs font-extrabold text-[#122652]">${esc(p.titulo)}</span>
                                        <span class="block text-[11px] text-[#5B6670] leading-snug mt-0.5">${esc(p.descripcion)}</span>
                                    </div>
                                </div>
                                <input type="checkbox" class="perm-toggle-input"
                                    ${activo ? "checked" : ""}
                                    onchange="alternarPermisoIndividual('${esc(p.id)}', this.checked)">
                            </label>
                        `;
                    }).join("")}
                </div>
            </div>
        `;
    }).join("");
}

// Determina el rol base equivalente para retrocompatibilidad con endpoints legacy de FastAPI
function calcularRolBaseCompatible(listaPermisos) {
    if (listaPermisos.includes("usuarios.permisos") || listaPermisos.includes("seguridad.auditoria")) {
        return "Administrador";
    }
    if (listaPermisos.length > 0) {
        return "Editor";
    }
    return "Usuario Regular";
}

async function guardarPermisosDeUsuario() {
    if (!usuarioEditandoPermisos) return;
    const u = usuarioEditandoPermisos;
    const nuevosPermisos = [...setPermisosModal];
    const rolCompatible = calcularRolBaseCompatible(nuevosPermisos);

    const btn = document.getElementById("btn-guardar-permisos-modal");
    btn.disabled = true;
    btn.classList.add("opacity-60");

    try {
        // 1. Intentar endpoint dedicado de permisos granulares (si ya está en FastAPI)
        let guardadoEnBackend = false;
        try {
            await api(`/admin/usuarios/${u.id}/permisos`, {
                method: "PUT",
                json: { permisos: nuevosPermisos, rol: rolCompatible }
            });
            guardadoEnBackend = true;
        } catch (_) {
            // 2. Fallback automático al endpoint existente `/admin/usuarios/{id}/rol`
            await api(`/admin/usuarios/${u.id}/rol`, {
                method: "POST",
                json: { rol: rolCompatible, permisos: nuevosPermisos }
            });
            guardadoEnBackend = true;
        }

        // 3. Guardar siempre el mapa exacto de permisos personalizados
        guardarPermisosEnMapaLocal(u, nuevosPermisos);
        u._permisos = nuevosPermisos;
        u.rol = rolCompatible;

        cerrarModalPermisosUsuario();
        actualizarContadoresUsuarios();
        renderizarTablaUsuarios();
        toast(`Permisos de ${u.nombre} actualizados a tu medida.`, "exito");
    } catch (err) {
        // Aun si el servidor local está apagado o en pruebas, actualizar en sesión local y avisar
        guardarPermisosEnMapaLocal(u, nuevosPermisos);
        u._permisos = nuevosPermisos;
        cerrarModalPermisosUsuario();
        actualizarContadoresUsuarios();
        renderizarTablaUsuarios();
        toast(`Permisos guardados localmente (${err.message}).`, "alerta");
    } finally {
        btn.disabled = false;
        btn.classList.remove("opacity-60");
    }
}
