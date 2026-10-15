// ============================================================================
// MÓDULO DE CATÁLOGOS INSTITUCIONALES DE LA FACULTAD (CERO ESCRITURA MANUAL)
// Administra:
// 1. Facultades / Escuelas (ej. ETDA, FCA, FI)
// 2. Carreras vinculadas a cada Facultad
// 3. Grupos Académicos (ej. 1A, 3B, 5A, 7N, 7O)
// 4. Auditorios y Salas con su capacidad exacta de asientos (cupo máximo)
// 5. Categorías oficiales de eventos
// ============================================================================

const STORAGE_CATALOGOS_KEY = "cc_unach_catalogos_institucionales_v1";

const CATALOGO_POR_DEFECTO = {
    facultadActiva: "ETDA",
    facultades: [
        { id: "ETDA", nombre: "Escuela de Tecnologías Digitales Aplicadas (ETDA)" },
        { id: "FCA", nombre: "Facultad de Contaduría y Administración (FCA)" },
        { id: "FI", nombre: "Facultad de Ingeniería (FI)" }
    ],
    carreras: [
        { id: "LIDTS", nombre: "Lic. en Ingeniería en Desarrollo y Tecnologías de Software", facultadId: "ETDA" },
        { id: "LSC", nombre: "Lic. en Sistemas Computacionales", facultadId: "FCA" },
        { id: "IC", nombre: "Ingeniería Civil", facultadId: "FI" }
    ],
    grupos: ["1A", "1B", "3A", "3B", "5A", "5B", "7N", "7O", "8A"],
    auditorios: [
        { id: "aud-etda", nombre: "Auditorio Principal ETDA", asientos: 180, ubicacion: "Edificio Principal ETDA" },
        { id: "sala-av1", nombre: "Sala Audiovisual 1", asientos: 60, ubicacion: "Planta Alta ETDA" },
        { id: "sala-av2", nombre: "Sala Audiovisual 2", asientos: 50, ubicacion: "Planta Alta ETDA" },
        { id: "lab-comp-a", nombre: "Laboratorio de Cómputo A", asientos: 35, ubicacion: "Centro de Cómputo" },
        { id: "aud-constituyentes", nombre: "Auditorio Los Constituyentes", asientos: 350, ubicacion: "Campus I Central" },
        { id: "explanada-etda", nombre: "Explanada Central ETDA", asientos: 250, ubicacion: "Área Abierta Campus I" }
    ],
    categorias: ["Académicos", "Conferencias", "Talleres", "Tutorías", "Culturales", "Deporte"]
};

let estadoCatalogos = cargarCatalogosLocal();

function cargarCatalogosLocal() {
    try {
        const raw = localStorage.getItem(STORAGE_CATALOGOS_KEY);
        if (!raw) return JSON.parse(JSON.stringify(CATALOGO_POR_DEFECTO));
        const parsed = JSON.parse(raw);
        return {
            facultadActiva: parsed.facultadActiva || CATALOGO_POR_DEFECTO.facultadActiva,
            facultades: Array.isArray(parsed.facultades) && parsed.facultades.length ? parsed.facultades : [...CATALOGO_POR_DEFECTO.facultades],
            carreras: Array.isArray(parsed.carreras) && parsed.carreras.length ? parsed.carreras : [...CATALOGO_POR_DEFECTO.carreras],
            grupos: Array.isArray(parsed.grupos) && parsed.grupos.length ? parsed.grupos : [...CATALOGO_POR_DEFECTO.grupos],
            auditorios: Array.isArray(parsed.auditorios) && parsed.auditorios.length ? parsed.auditorios : [...CATALOGO_POR_DEFECTO.auditorios],
            categorias: Array.isArray(parsed.categorias) && parsed.categorias.length ? parsed.categorias : [...CATALOGO_POR_DEFECTO.categorias]
        };
    } catch (_) {
        return JSON.parse(JSON.stringify(CATALOGO_POR_DEFECTO));
    }
}

async function sincronizarCatalogosDesdeServidor() {
    try {
        const remoto = await api("/catalogos/facultad");
        if (remoto && typeof remoto === "object" && Array.isArray(remoto.auditorios)) {
            estadoCatalogos = {
                facultadActiva: remoto.facultadActiva || estadoCatalogos.facultadActiva,
                facultades: remoto.facultades?.length ? remoto.facultades : estadoCatalogos.facultades,
                carreras: remoto.carreras?.length ? remoto.carreras : estadoCatalogos.carreras,
                grupos: remoto.grupos?.length ? remoto.grupos : estadoCatalogos.grupos,
                auditorios: remoto.auditorios?.length ? remoto.auditorios : estadoCatalogos.auditorios,
                categorias: remoto.categorias?.length ? remoto.categorias : estadoCatalogos.categorias
            };
            localStorage.setItem(STORAGE_CATALOGOS_KEY, JSON.stringify(estadoCatalogos));
        }
    } catch (_) {
        /* Si el endpoint aún no está activo, se usa el catálogo local sin problema */
    }
}

async function persistirCatalogos(mensajeToast = "Catálogo institucional actualizado.") {
    localStorage.setItem(STORAGE_CATALOGOS_KEY, JSON.stringify(estadoCatalogos));
    try {
        await api("/catalogos/facultad", {
            method: "PUT",
            json: estadoCatalogos
        });
    } catch (_) {
        /* Guardado en almacenamiento local listo */
    }
    renderizarVistaCatalogos();
    poblarSelectoresModalEvento();
    if (mensajeToast) toast(mensajeToast, "exito");
}

// Busca un auditorio por su nombre y devuelve su objeto { nombre, asientos, ubicacion }
function buscarAuditorioPorNombre(nombreLugar) {
    if (!nombreLugar) return null;
    const limpio = nombreLugar.trim().toLowerCase();
    return (estadoCatalogos.auditorios || []).find(a =>
        a.nombre.toLowerCase() === limpio ||
        limpio.startsWith(a.nombre.toLowerCase())
    ) || null;
}

// ============================================================================
// RENDERIZADO DE LA PESTAÑA "CATÁLOGOS DE FACULTAD"
// ============================================================================
async function iniciarCatalogosFacultad() {
    await sincronizarCatalogosDesdeServidor();
    renderizarVistaCatalogos();
}

function renderizarVistaCatalogos() {
    // Contadores KPI superiores
    const setTxt = (id, val) => { const el = document.getElementById(id); if (el) el.innerText = val; };
    const totalAsientos = (estadoCatalogos.auditorios || []).reduce((acc, a) => acc + (Number(a.asientos) || 0), 0);
    setTxt("kpi-cat-facultades", (estadoCatalogos.facultades || []).length);
    setTxt("kpi-cat-carreras", (estadoCatalogos.carreras || []).length);
    setTxt("kpi-cat-grupos", (estadoCatalogos.grupos || []).length);
    setTxt("kpi-cat-auditorios", `${(estadoCatalogos.auditorios || []).length} (${totalAsientos} asientos)`);

    // 1. Selector de facultad para asociar nuevas carreras
    const selFacCarrera = document.getElementById("cat-nueva-carrera-facultad");
    if (selFacCarrera) {
        selFacCarrera.innerHTML = (estadoCatalogos.facultades || []).map(f =>
            `<option value="${esc(f.id)}">${esc(f.id)} — ${esc(f.nombre)}</option>`
        ).join("");
    }

    // 2. Lista de Auditorios y Salas con su capacidad de asientos
    const contAuditorios = document.getElementById("lista-cat-auditorios");
    if (contAuditorios) {
        contAuditorios.innerHTML = (estadoCatalogos.auditorios || []).map((a, idx) => `
            <div class="flex items-center justify-between gap-3 p-3.5 rounded-2xl border border-slate-200 bg-[#F4F7FB] hover:bg-white transition">
                <div class="flex items-center gap-3">
                    <div class="w-10 h-10 rounded-xl bg-[#122652] text-[#F57C00] flex items-center justify-center font-extrabold text-sm shrink-0">
                        <i class="fa-solid fa-chair"></i>
                    </div>
                    <div>
                        <div class="flex items-center gap-2 flex-wrap">
                            <span class="font-extrabold text-sm text-[#122652]">${esc(a.nombre)}</span>
                            <span class="px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 border border-emerald-200 text-[11px] font-extrabold">
                                <i class="fa-solid fa-users mr-1"></i>${Number(a.asientos)} asientos
                            </span>
                        </div>
                        <p class="text-xs text-[#5B6670] mt-0.5">
                            <i class="fa-solid fa-location-dot text-[#0093FF] mr-1"></i>${esc(a.ubicacion || "Campus I UNACH")}
                        </p>
                    </div>
                </div>
                <div class="flex items-center gap-1.5">
                    <button type="button" onclick="editarAsientosAuditorio(${idx})"
                        class="text-xs font-bold text-[#122652] bg-white hover:bg-slate-100 border border-slate-300 px-2.5 py-1.5 rounded-xl transition"
                        title="Cambiar número de asientos">
                        <i class="fa-solid fa-pen text-[#0093FF] mr-1"></i>Asientos
                    </button>
                    <button type="button" onclick="eliminarAuditorioCatalogo(${idx})"
                        class="text-xs font-bold text-red-600 bg-red-50 hover:bg-red-100 border border-red-200 px-2.5 py-1.5 rounded-xl transition"
                        title="Eliminar auditorio">
                        <i class="fa-solid fa-trash-can"></i>
                    </button>
                </div>
            </div>
        `).join("") || `<p class="text-xs text-slate-400 text-center py-4">No hay auditorios registrados.</p>`;
    }

    // 3. Lista de Facultades
    const contFacultades = document.getElementById("lista-cat-facultades");
    if (contFacultades) {
        contFacultades.innerHTML = (estadoCatalogos.facultades || []).map((f, idx) => `
            <div class="flex items-center justify-between gap-2 p-3 rounded-xl border border-slate-200 bg-slate-50">
                <div>
                    <span class="inline-block text-[10px] font-extrabold bg-[#122652] text-[#F57C00] px-2 py-0.5 rounded mr-1.5">${esc(f.id)}</span>
                    <span class="text-xs font-bold text-[#122652]">${esc(f.nombre)}</span>
                </div>
                <button type="button" onclick="eliminarFacultadCatalogo(${idx})" class="text-red-500 hover:text-red-700 text-xs px-2 py-1">
                    <i class="fa-solid fa-trash-can"></i>
                </button>
            </div>
        `).join("");
    }

    // 4. Lista de Carreras / Programas Educativos
    const contCarreras = document.getElementById("lista-cat-carreras");
    if (contCarreras) {
        contCarreras.innerHTML = (estadoCatalogos.carreras || []).map((c, idx) => `
            <div class="flex items-center justify-between gap-2 p-3 rounded-xl border border-slate-200 bg-slate-50">
                <div>
                    <span class="inline-block text-[10px] font-extrabold bg-sky-100 text-[#0093FF] px-2 py-0.5 rounded mr-1.5">${esc(c.facultadId || "UNACH")}</span>
                    <span class="text-xs font-bold text-[#122652]">${esc(c.nombre)}</span>
                </div>
                <button type="button" onclick="eliminarCarreraCatalogo(${idx})" class="text-red-500 hover:text-red-700 text-xs px-2 py-1">
                    <i class="fa-solid fa-trash-can"></i>
                </button>
            </div>
        `).join("");
    }

    // 5. Chips de Grupos Académicos
    const contGrupos = document.getElementById("lista-cat-grupos");
    if (contGrupos) {
        contGrupos.innerHTML = (estadoCatalogos.grupos || []).map((g, idx) => `
            <span class="inline-flex items-center gap-1.5 bg-[#122652] text-white text-xs font-extrabold px-3 py-1.5 rounded-xl shadow-sm">
                <i class="fa-solid fa-users-rectangle text-[#F57C00] text-[10px]"></i>
                <span>Grupo ${esc(g)}</span>
                <button type="button" onclick="eliminarGrupoCatalogo(${idx})" class="ml-1 text-white/70 hover:text-red-300 font-bold" title="Quitar grupo">×</button>
            </span>
        `).join("");
    }

    // 6. Chips de Categorías de Eventos
    const contCategorias = document.getElementById("lista-cat-categorias");
    if (contCategorias) {
        contCategorias.innerHTML = (estadoCatalogos.categorias || []).map((cat, idx) => `
            <span class="inline-flex items-center gap-1.5 bg-amber-50 text-[#122652] border border-[#F57C00]/40 text-xs font-extrabold px-3 py-1.5 rounded-xl">
                <i class="fa-solid fa-tag text-[#F57C00] text-[10px]"></i>
                <span>${esc(cat)}</span>
                <button type="button" onclick="eliminarCategoriaCatalogo(${idx})" class="ml-1 text-slate-400 hover:text-red-600 font-bold" title="Quitar categoría">×</button>
            </span>
        `).join("");
    }
}

// ---------- Acciones CRUD del Catálogo ----------
function agregarAuditorioCatalogo(e) {
    e.preventDefault();
    const nombre = document.getElementById("cat-nuevo-aud-nombre").value.trim();
    const asientos = parseInt(document.getElementById("cat-nuevo-aud-asientos").value, 10);
    const ubicacion = document.getElementById("cat-nuevo-aud-ubicacion").value.trim() || "Campus I UNACH";

    if (!nombre || isNaN(asientos) || asientos <= 0) {
        toast("Indica el nombre del auditorio/sala y un número válido de asientos.", "alerta");
        return;
    }

    const existenteIdx = estadoCatalogos.auditorios.findIndex(a => a.nombre.toLowerCase() === nombre.toLowerCase());
    if (existenteIdx >= 0) {
        estadoCatalogos.auditorios[existenteIdx].asientos = asientos;
        estadoCatalogos.auditorios[existenteIdx].ubicacion = ubicacion;
    } else {
        estadoCatalogos.auditorios.push({
            id: "aud-" + Date.now(),
            nombre,
            asientos,
            ubicacion
        });
    }

    document.getElementById("cat-nuevo-aud-nombre").value = "";
    document.getElementById("cat-nuevo-aud-asientos").value = "";
    document.getElementById("cat-nuevo-aud-ubicacion").value = "";
    persistirCatalogos(`Auditorio "${nombre}" (${asientos} asientos) guardado en el catálogo.`);
}

function editarAsientosAuditorio(idx) {
    const aud = estadoCatalogos.auditorios[idx];
    if (!aud) return;
    const nuevo = prompt(`¿Cuántos asientos tiene "${aud.nombre}"?`, String(aud.asientos));
    if (nuevo === null) return;
    const num = parseInt(nuevo, 10);
    if (isNaN(num) || num <= 0) {
        toast("Ingresa un número de asientos mayor a 0.", "alerta");
        return;
    }
    aud.asientos = num;
    persistirCatalogos(`Capacidad de "${aud.nombre}" actualizada a ${num} asientos.`);
}

function eliminarAuditorioCatalogo(idx) {
    const aud = estadoCatalogos.auditorios[idx];
    if (!aud) return;
    if (!confirm(`¿Quitar "${aud.nombre}" de la lista de auditorios?`)) return;
    estadoCatalogos.auditorios.splice(idx, 1);
    persistirCatalogos("Auditorio eliminado del catálogo.");
}

function agregarFacultadCatalogo(e) {
    e.preventDefault();
    const siglas = document.getElementById("cat-nueva-fac-id").value.trim().toUpperCase();
    const nombre = document.getElementById("cat-nueva-fac-nombre").value.trim();
    if (!siglas || !nombre) return;
    if (!estadoCatalogos.facultades.some(f => f.id.toLowerCase() === siglas.toLowerCase())) {
        estadoCatalogos.facultades.push({ id: siglas, nombre });
    }
    document.getElementById("cat-nueva-fac-id").value = "";
    document.getElementById("cat-nueva-fac-nombre").value = "";
    persistirCatalogos(`Facultad "${siglas}" agregada.`);
}

function eliminarFacultadCatalogo(idx) {
    estadoCatalogos.facultades.splice(idx, 1);
    persistirCatalogos("Facultad eliminada del catálogo.");
}

function agregarCarreraCatalogo(e) {
    e.preventDefault();
    const facultadId = document.getElementById("cat-nueva-carrera-facultad").value;
    const nombre = document.getElementById("cat-nueva-carrera-nombre").value.trim();
    if (!nombre) return;
    if (!estadoCatalogos.carreras.some(c => c.nombre.toLowerCase() === nombre.toLowerCase())) {
        estadoCatalogos.carreras.push({
            id: nombre.slice(0, 12).toUpperCase().replace(/\s+/g, "_"),
            nombre,
            facultadId
        });
    }
    document.getElementById("cat-nueva-carrera-nombre").value = "";
    persistirCatalogos(`Carrera "${nombre}" agregada a ${facultadId}.`);
}

function eliminarCarreraCatalogo(idx) {
    estadoCatalogos.carreras.splice(idx, 1);
    persistirCatalogos("Carrera eliminada del catálogo.");
}

function agregarGruposCatalogo(e) {
    e.preventDefault();
    const raw = document.getElementById("cat-nuevo-grupo-input").value;
    const nuevos = raw.split(",").map(g => g.trim().toUpperCase()).filter(Boolean);
    if (!nuevos.length) return;
    nuevos.forEach(g => {
        if (!estadoCatalogos.grupos.some(x => x.toUpperCase() === g)) {
            estadoCatalogos.grupos.push(g);
        }
    });
    estadoCatalogos.grupos.sort();
    document.getElementById("cat-nuevo-grupo-input").value = "";
    persistirCatalogos("Grupo(s) agregado(s) al catálogo.");
}

function eliminarGrupoCatalogo(idx) {
    estadoCatalogos.grupos.splice(idx, 1);
    persistirCatalogos("Grupo eliminado del catálogo.");
}

function agregarCategoriaCatalogo(e) {
    e.preventDefault();
    const nueva = document.getElementById("cat-nueva-categoria-input").value.trim();
    if (!nueva) return;
    if (!estadoCatalogos.categorias.some(c => c.toLowerCase() === nueva.toLowerCase())) {
        estadoCatalogos.categorias.push(nueva);
    }
    document.getElementById("cat-nueva-categoria-input").value = "";
    persistirCatalogos(`Categoría "${nueva}" agregada.`);
}

function eliminarCategoriaCatalogo(idx) {
    estadoCatalogos.categorias.splice(idx, 1);
    persistirCatalogos("Categoría eliminada.");
}

function restaurarCatalogosPorDefecto() {
    if (!confirm("¿Restaurar los valores predeterminados de la UNACH / ETDA?")) return;
    estadoCatalogos = JSON.parse(JSON.stringify(CATALOGO_POR_DEFECTO));
    persistirCatalogos("Catálogos restaurados a los valores oficiales UNACH-ETDA.");
}

// ============================================================================
// POBLAR AUTOMÁTICAMENTE LAS LISTAS DESPLEGABLES DEL MODAL DE CREAR EVENTO
// ============================================================================
function poblarSelectoresModalEvento(evActual = null) {
    // 1. Categorías desplegables
    const selCat = document.getElementById("ev-categoria");
    if (selCat) {
        const valorPrevio = evActual?.categoria || selCat.value || "";
        const listaCats = [...estadoCatalogos.categorias];
        if (valorPrevio && !listaCats.some(c => c.toLowerCase() === valorPrevio.toLowerCase())) {
            listaCats.unshift(valorPrevio);
        }
        selCat.innerHTML = listaCats.map(c =>
            `<option value="${esc(c)}" ${c.toLowerCase() === valorPrevio.toLowerCase() ? "selected" : ""}>${esc(c)}</option>`
        ).join("");
    }

    // 2. Auditorios / Salas desplegables con su capacidad de asientos
    const selLugar = document.getElementById("ev-lugar");
    if (selLugar) {
        const lugarPrevio = evActual?.lugar || selLugar.value || "";
        const listaAud = [...estadoCatalogos.auditorios];
        const coincide = listaAud.some(a => a.nombre.toLowerCase() === lugarPrevio.toLowerCase());
        let htmlOpts = listaAud.map(a =>
            `<option value="${esc(a.nombre)}" data-asientos="${Number(a.asientos)}" ${a.nombre.toLowerCase() === lugarPrevio.toLowerCase() ? "selected" : ""}>
                ${esc(a.nombre)} — (${Number(a.asientos)} asientos · ${esc(a.ubicacion || "Campus")})
            </option>`
        ).join("");
        if (lugarPrevio && !coincide) {
            htmlOpts = `<option value="${esc(lugarPrevio)}" data-asientos="100" selected>${esc(lugarPrevio)} (Personalizado)</option>` + htmlOpts;
        }
        selLugar.innerHTML = htmlOpts;
        actualizarBadgeAsientosModal();
    }

    // 3. Facultad desplegable
    const selFac = document.getElementById("ev-facultad");
    if (selFac) {
        const facPrevia = evActual?.idFacultadDestino || estadoCatalogos.facultadActiva || "";
        selFac.innerHTML = `<option value="">Todas las facultades</option>` +
            (estadoCatalogos.facultades || []).map(f =>
                `<option value="${esc(f.id)}" ${f.id.toLowerCase() === facPrevia.toLowerCase() ? "selected" : ""}>${esc(f.id)} — ${esc(f.nombre)}</option>`
            ).join("");
    }

    // 4. Carrera desplegable filtrada por la facultad seleccionada
    actualizarCarrerasSegunFacultadModal(evActual?.idCarreraDestino || "");

    // 5. Botones clicables de Grupos para no escribir nada a mano
    renderizarSelectorVisualGruposModal();
}

function actualizarBadgeAsientosModal() {
    const selLugar = document.getElementById("ev-lugar");
    const badge = document.getElementById("ev-asientos-info");
    if (!selLugar || !badge) return;
    const opt = selLugar.options[selLugar.selectedIndex];
    const asientos = opt ? opt.getAttribute("data-asientos") : null;
    if (asientos) {
        badge.innerHTML = `<i class="fa-solid fa-chair text-[#F57C00] mr-1"></i>Capacidad oficial: <b>${esc(asientos)} asientos</b> disponibles`;
    } else {
        badge.innerHTML = `<i class="fa-solid fa-chair text-[#F57C00] mr-1"></i>Selecciona un auditorio de la lista`;
    }
}

function actualizarCarrerasSegunFacultadModal(carreraSeleccionada = null) {
    const selFac = document.getElementById("ev-facultad");
    const selCar = document.getElementById("ev-carrera");
    if (!selCar) return;

    const facId = selFac ? selFac.value : "";
    const valorActual = carreraSeleccionada !== null ? carreraSeleccionada : selCar.value;

    const carrerasFiltradas = (estadoCatalogos.carreras || []).filter(c =>
        !facId || !c.facultadId || c.facultadId.toLowerCase() === facId.toLowerCase()
    );

    let html = `<option value="">Todas las carreras de la facultad</option>` +
        carrerasFiltradas.map(c =>
            `<option value="${esc(c.nombre)}" ${c.nombre.toLowerCase() === (valorActual || "").toLowerCase() ? "selected" : ""}>${esc(c.nombre)}</option>`
        ).join("");

    if (valorActual && !carrerasFiltradas.some(c => c.nombre.toLowerCase() === valorActual.toLowerCase())) {
        html += `<option value="${esc(valorActual)}" selected>${esc(valorActual)}</option>`;
    }
    selCar.innerHTML = html;
}

function alternarGrupoEnModalEvento(grupo) {
    const idx = estadoModalEvento.grupos.findIndex(x => x.toLowerCase() === grupo.toLowerCase());
    if (idx >= 0) {
        estadoModalEvento.grupos.splice(idx, 1);
    } else {
        estadoModalEvento.grupos.push(grupo);
    }
    renderizarSelectorVisualGruposModal();
    pintarChipsGrupos();
}

function seleccionarTodosLosGruposModal() {
    estadoModalEvento.grupos = [...estadoCatalogos.grupos];
    renderizarSelectorVisualGruposModal();
    pintarChipsGrupos();
}

function limpiarGruposModal() {
    estadoModalEvento.grupos = [];
    renderizarSelectorVisualGruposModal();
    pintarChipsGrupos();
}

function renderizarSelectorVisualGruposModal() {
    const cont = document.getElementById("ev-selector-botones-grupos");
    if (!cont) return;
    const seleccionados = estadoModalEvento.grupos.map(g => g.toUpperCase());

    cont.innerHTML = (estadoCatalogos.grupos || []).map(g => {
        const activo = seleccionados.includes(g.toUpperCase());
        return `
            <button type="button" onclick="alternarGrupoEnModalEvento('${esc(g)}')"
                class="px-3 py-1.5 rounded-xl text-xs font-extrabold border transition flex items-center gap-1.5 ${
                    activo
                        ? "bg-[#122652] text-white border-[#122652] shadow-sm"
                        : "bg-white text-[#122652] border-slate-300 hover:border-[#0093FF]"
                }">
                <i class="fa-solid ${activo ? "fa-circle-check text-[#F57C00]" : "fa-circle text-slate-300"} text-[10px]"></i>
                <span>${esc(g)}</span>
            </button>
        `;
    }).join("");
}

function irAConfigurarCatalogosDesdeModal() {
    abrirModalConfigAuditorios();
}

function abrirModalConfigAuditorios() {
    renderizarVistaCatalogos();
    document.getElementById("modal-config-auditorios")?.classList.remove("hidden");
}

function cerrarModalConfigAuditorios() {
    document.getElementById("modal-config-auditorios")?.classList.add("hidden");
    poblarSelectoresModalEvento();
}
