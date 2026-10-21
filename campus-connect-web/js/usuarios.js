// ============================================================================
// MÓDULO DE GESTIÓN DE USUARIOS, ALTA DE EMPLEADOS Y PERMISOS GRANULARES
// Permite:
// 1. Dar de alta nuevos empleados/usuarios directo desde la web sin usar la app
//    y asignarles sus permisos personalizados en el mismo paso.
// 2. Eliminar usuarios/empleados del sistema con confirmación de seguridad.
// 3. Configurar permisos granulares a medida sin grupos ni roles rígidos.
// ============================================================================

let usuariosCache = {};
let usuariosLista = [];
let filtroTextoUsuarios = "";
let filtroPermisoUsuarios = "todos";
let usuarioEditandoPermisos = null;
let setPermisosModal = new Set();
let setPermisosNuevoUsuario = new Set();

async function cargarUsuariosAdmin() {
    if (!tienePermiso("usuarios.permisos")) return;
    const tbody = document.getElementById("tabla-usuarios-body");
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="3" class="p-8 text-center text-slate-400"><i class="fa-solid fa-spinner fa-spin mr-2 text-[#F57C00]"></i> Cargando directorio de usuarios, empleados y matriz de permisos...</td></tr>`;

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
        tbody.innerHTML = `<tr><td colspan="3" class="p-6 text-center text-red-500 font-semibold">${esc(err.message || "Error al obtener usuarios.")}</td></tr>`;
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
        tbody.innerHTML = `<tr><td colspan="3" class="p-8 text-center text-slate-500">No se encontraron usuarios o empleados con ese criterio de búsqueda.</td></tr>`;
        return;
    }

    tbody.innerHTML = filtrados.map(u => {
        const iniciales = obtenerIniciales(u.nombre, u.email);
        const permisos = u._permisos || [];
        const esAccesoTotal = permisos.includes("usuarios.permisos") || permisos.length === CATALOGO_PERMISOS.length;
        const esMismaSesion = userState.email && u.email && userState.email.toLowerCase().trim() === u.email.toLowerCase().trim();

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
                                ${esMismaSesion ? `<span class="text-[10px] font-extrabold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800">Tú</span>` : ""}
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
                            <i class="fa-solid fa-sliders text-[#F57C00]"></i> Permisos
                        </button>
                        ${!esMismaSesion ? `
                            <button type="button" onclick="eliminarUsuarioAdmin(${Number(u.id)})"
                                class="text-xs font-bold bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 px-3 py-2 rounded-xl transition"
                                title="Eliminar usuario o empleado del sistema">
                                <i class="fa-solid fa-trash-can"></i>
                            </button>` : `
                            <button type="button" disabled
                                class="text-xs font-bold bg-slate-100 text-slate-300 border border-slate-200 px-3 py-2 rounded-xl cursor-not-allowed"
                                title="No puedes eliminar tu propia cuenta en sesión">
                                <i class="fa-solid fa-trash-can"></i>
                            </button>`}
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

// ============================================================================
// ALTA DIRECTA DE EMPLEADOS / USUARIOS SIN NECESIDAD DE LA APP MÓVIL
// ============================================================================
function abrirModalCrearUsuarioAdmin() {
    if (!tienePermiso("usuarios.permisos")) {
        toast("No tienes permiso para dar de alta usuarios.", "alerta");
        return;
    }
    document.getElementById("nuevo-usr-nombre").value = "";
    document.getElementById("nuevo-usr-email").value = "";
    document.getElementById("nuevo-usr-password").value = "";
    setPermisosNuevoUsuario = new Set();

    renderizarPlantillasNuevoUsuario();
    renderizarCasillasNuevoUsuario();
    document.getElementById("modal-crear-usuario").classList.remove("hidden");
}

function cerrarModalCrearUsuarioAdmin() {
    document.getElementById("modal-crear-usuario").classList.add("hidden");
}

function generarPasswordEmpleadoSugerida() {
    const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    let pass = "Unach";
    for (let i = 0; i < 5; i++) {
        pass += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    pass += "!";
    const input = document.getElementById("nuevo-usr-password");
    if (input) {
        input.type = "text";
        input.value = pass;
    }
}

function renderizarPlantillasNuevoUsuario() {
    const cont = document.getElementById("nuevo-usr-plantillas");
    if (!cont) return;
    cont.innerHTML = PLANTILLAS_RAPIDAS_PERMISOS.map(pl => `
        <button type="button" onclick="aplicarPlantillaNuevoUsuario('${esc(pl.id)}')"
            class="text-[11px] font-bold px-2.5 py-1.5 rounded-xl border border-slate-200 bg-white hover:border-[#0093FF] hover:bg-sky-50/50 text-[#122652] transition flex items-center gap-1.5">
            <i class="fa-solid ${esc(pl.icono)} text-[#F57C00]"></i>
            <span>${esc(pl.nombre)}</span>
        </button>
    `).join("");
}

function aplicarPlantillaNuevoUsuario(plantillaId) {
    const pl = PLANTILLAS_RAPIDAS_PERMISOS.find(p => p.id === plantillaId);
    if (!pl) return;
    setPermisosNuevoUsuario = new Set(pl.permisos);
    renderizarCasillasNuevoUsuario();
}

function alternarPermisoNuevoUsuario(permisoId, checked) {
    if (checked) setPermisosNuevoUsuario.add(permisoId);
    else setPermisosNuevoUsuario.delete(permisoId);
    renderizarCasillasNuevoUsuario();
}

function renderizarCasillasNuevoUsuario() {
    const cont = document.getElementById("nuevo-usr-lista-permisos");
    if (!cont) return;
    const contadorEl = document.getElementById("nuevo-usr-contador-permisos");
    if (contadorEl) {
        contadorEl.innerText = `${setPermisosNuevoUsuario.size} permiso(s) seleccionado(s)`;
    }

    cont.innerHTML = CATALOGO_PERMISOS.map(p => {
        const activo = setPermisosNuevoUsuario.has(p.id);
        return `
            <label class="perm-card-label ${activo ? 'checked' : ''}">
                <div class="flex items-start gap-2.5">
                    <div class="w-7 h-7 rounded-lg ${activo ? 'bg-[#122652] text-[#F57C00]' : 'bg-slate-100 text-slate-500'} flex items-center justify-center shrink-0 mt-0.5">
                        <i class="fa-solid ${esc(p.icono)} text-xs"></i>
                    </div>
                    <div>
                        <span class="block text-xs font-extrabold text-[#122652]">${esc(p.titulo)}</span>
                        <span class="block text-[10px] text-[#5B6670] leading-tight mt-0.5">${esc(p.descripcion)}</span>
                    </div>
                </div>
                <input type="checkbox" class="perm-toggle-input"
                    ${activo ? "checked" : ""}
                    onchange="alternarPermisoNuevoUsuario('${esc(p.id)}', this.checked)">
            </label>
        `;
    }).join("");
}

async function guardarNuevoUsuarioAdmin(e) {
    e.preventDefault();
    const nombre = document.getElementById("nuevo-usr-nombre").value.trim();
    const email = document.getElementById("nuevo-usr-email").value.trim().toLowerCase();
    const password = document.getElementById("nuevo-usr-password").value.trim();
    const permisosIniciales = [...setPermisosNuevoUsuario];
    const rolCompatible = calcularRolBaseCompatible(permisosIniciales);

    if (!nombre || !email || !password) {
        toast("Completa el nombre, correo y contraseña del empleado.", "alerta");
        return;
    }

    const btn = document.getElementById("btn-crear-usr-submit");
    btn.disabled = true;
    btn.classList.add("opacity-60");

    try {
        // 1. Crear el usuario (intenta primero /admin/usuarios y si no existe usa /auth/registro)
        let creado = null;
        try {
            creado = await api("/admin/usuarios", {
                method: "POST",
                json: { nombre, email, password, rol: rolCompatible, permisos: permisosIniciales }
            });
        } catch (_) {
            creado = await api("/auth/registro", {
                method: "POST",
                json: { nombre, email, password }
            });
        }

        // 2. Guardar sus permisos personalizados en el mapa local y sincronizar su rol base en el servidor
        const usuarioObj = {
            id: creado?.id,
            nombre,
            email,
            rol: rolCompatible
        };
        guardarPermisosEnMapaLocal(usuarioObj, permisosIniciales);

        if (creado?.id && rolCompatible !== "Usuario Regular") {
            try {
                await api(`/admin/usuarios/${creado.id}/rol`, {
                    method: "POST",
                    json: { rol: rolCompatible, permisos: permisosIniciales }
                });
            } catch (_) {
                /* el mapa local ya respalda los permisos exactos */
            }
        }

        cerrarModalCrearUsuarioAdmin();
        toast(`¡Empleado "${nombre}" registrado con éxito y permisos asignados!`, "exito");
        await cargarUsuariosAdmin();
    } catch (err) {
        toast(err.message || "No se pudo registrar el empleado.", "error");
    } finally {
        btn.disabled = false;
        btn.classList.remove("opacity-60");
    }
}

// ============================================================================
// ELIMINAR USUARIO / EMPLEADO DESDE EL PANEL DE ADMINISTRACIÓN
// ============================================================================
async function eliminarUsuarioAdmin(userId) {
    if (!tienePermiso("usuarios.permisos")) {
        toast("No tienes permiso para eliminar usuarios.", "alerta");
        return;
    }
    const u = usuariosCache[userId];
    if (!u) return;

    if (userState.email && u.email && userState.email.toLowerCase().trim() === u.email.toLowerCase().trim()) {
        toast("Por seguridad no puedes eliminar tu propia cuenta en sesión.", "alerta");
        return;
    }

    if (!confirm(`¿Estás seguro de que deseas eliminar permanentemente al usuario "${u.nombre}" (${u.email}) del sistema?`)) {
        return;
    }

    try {
        await api(`/admin/usuarios/${userId}`, { method: "DELETE" });

        // Limpiar del mapa local de permisos
        const mapa = obtenerMapaPermisosLocal();
        delete mapa[`id:${userId}`];
        if (u.email) delete mapa[`email:${u.email.toLowerCase().trim()}`];
        localStorage.setItem(STORAGE_MAPA_PERMISOS, JSON.stringify(mapa));

        toast(`Usuario "${u.nombre}" eliminado correctamente.`, "exito");
        await cargarUsuariosAdmin();
    } catch (err) {
        toast(err.message || "Error al eliminar el usuario en el servidor.", "error");
    }
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
        try {
            await api(`/admin/usuarios/${u.id}/permisos`, {
                method: "PUT",
                json: { permisos: nuevosPermisos, rol: rolCompatible }
            });
        } catch (_) {
            await api(`/admin/usuarios/${u.id}/rol`, {
                method: "POST",
                json: { rol: rolCompatible, permisos: nuevosPermisos }
            });
        }

        guardarPermisosEnMapaLocal(u, nuevosPermisos);
        u._permisos = nuevosPermisos;
        u.rol = rolCompatible;

        cerrarModalPermisosUsuario();
        actualizarContadoresUsuarios();
        renderizarTablaUsuarios();
        toast(`Permisos de ${u.nombre} actualizados a tu medida.`, "exito");
    } catch (err) {
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
