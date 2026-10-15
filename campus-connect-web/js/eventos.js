// ============================================================================
// MÓDULO DE CARTELERA DE EVENTOS, FILTROS ETDA Y LISTA DE ASISTENCIA
// ============================================================================

let eventosCache = {};
let eventosLista = [];
let filtroCategoriaEvento = "Todos";
let busquedaEventoTexto = "";

function inicializarFiltrosCategorias() {
    const contenedor = document.getElementById("contenedor-chips-categorias");
    if (!contenedor) return;
    contenedor.innerHTML = CATEGORIAS_ETDA.map(cat => {
        const activo = filtroCategoriaEvento === cat.nombre;
        return `
            <button type="button" onclick="seleccionarCategoriaEvento('${esc(cat.nombre)}')"
                class="cat-filter-chip ${activo ? 'active' : ''}">
                <i class="fa-solid ${esc(cat.icono)} text-xs"></i>
                <span>${esc(cat.nombre)}</span>
            </button>
        `;
    }).join("");
}

function seleccionarCategoriaEvento(nombreCat) {
    filtroCategoriaEvento = nombreCat;
    inicializarFiltrosCategorias();
    renderizarTarjetasEventos();
}

function filtrarEventosPorTexto(valor) {
    busquedaEventoTexto = (valor || "").trim().toLowerCase();
    renderizarTarjetasEventos();
}

function etiquetaAudiencia(ev) {
    if (ev.esGeneral) {
        return `<span class="px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-[10px] font-extrabold tracking-wide">COMUNIDAD GENERAL</span>`;
    }
    const partes = [
        ev.idFacultadDestino,
        ev.idCarreraDestino,
        (ev.gruposDestino || []).length ? "Gpo. " + ev.gruposDestino.join(", ") : null
    ].filter(Boolean);
    return `<span class="px-2.5 py-0.5 rounded-full bg-sky-50 text-[#122652] border border-sky-200 text-[10px] font-extrabold">${esc(partes.join(" · ") || "Segmentado")}</span>`;
}

async function cargarEventos() {
    const container = document.getElementById("eventos-container");
    if (!container) return;
    inicializarFiltrosCategorias();
    container.innerHTML = `
        <div class="col-span-full bg-white rounded-2xl border p-10 text-center text-slate-400">
            <i class="fa-solid fa-circle-notch fa-spin text-2xl text-[#F57C00] mb-2"></i>
            <p class="text-sm font-medium text-[#122652]">Sincronizando cartelera institucional UNACH...</p>
        </div>`;

    try {
        const todos = await api("/eventos/feed");
        eventosLista = todos || [];
        eventosLista.forEach(e => { eventosCache[e.id] = e; });
        renderizarTarjetasEventos();
    } catch (err) {
        container.innerHTML = `
            <div class="col-span-full bg-red-50 border border-red-200 rounded-2xl p-6 text-center text-red-700 text-sm">
                <i class="fa-solid fa-triangle-exclamation text-xl mb-1"></i>
                <p class="font-bold">${esc(err.message)}</p>
            </div>`;
    }
}

function renderizarTarjetasEventos() {
    const container = document.getElementById("eventos-container");
    if (!container) return;

    const activos = eventosLista.filter(e => !e.finalizado);
    const filtrados = activos.filter(ev => {
        const catDetectada = detectarCategoriaEventoWeb(ev);
        const coincideCat = filtroCategoriaEvento === "Todos" ||
            catDetectada.nombre.toLowerCase() === filtroCategoriaEvento.toLowerCase() ||
            (ev.categoria || "").toLowerCase().includes(filtroCategoriaEvento.toLowerCase());
        const texto = `${ev.titulo || ""} ${ev.descripcion || ""} ${ev.lugar || ""} ${ev.categoria || ""}`.toLowerCase();
        const coincideBusqueda = !busquedaEventoTexto || texto.includes(busquedaEventoTexto);
        return coincideCat && coincideBusqueda;
    });

    const contadorEl = document.getElementById("contador-eventos-disponibles");
    if (contadorEl) {
        contadorEl.innerText = `${filtrados.length} ${filtrados.length === 1 ? "evento activo" : "eventos activos"}`;
    }

    if (filtrados.length === 0) {
        container.innerHTML = `
            <div class="col-span-full etda-card p-10 text-center">
                <div class="w-14 h-14 rounded-full bg-[#0093FF]/10 text-[#0093FF] flex items-center justify-center mx-auto mb-3">
                    <i class="fa-regular fa-calendar-xmark text-2xl"></i>
                </div>
                <h3 class="text-base font-extrabold text-[#122652]">No se encontraron eventos próximos</h3>
                <p class="text-xs text-[#5B6670] mt-1">Prueba cambiando el filtro de categoría o revisa la pestaña de Eventos Pasados.</p>
            </div>`;
        return;
    }

    // Permisos individuales para cada botón de acción
    const pCompartir = tienePermiso("redes.compartir");
    const pEditar = tienePermiso("eventos.publicar");
    const pEliminar = tienePermiso("eventos.eliminar");
    const pAsistentes = tienePermiso("asistencia.gestionar");

    container.innerHTML = filtrados.map(ev => {
        const f = extraerFechaVisualWeb(ev.fechaHora);
        const catInfo = detectarCategoriaEventoWeb(ev);
        const materiales = (ev.archivos || []).filter(a => a.tipo === "material");
        const infoAud = typeof buscarAuditorioPorNombre === "function" ? buscarAuditorioPorNombre(ev.lugar) : null;

        return `
            <article class="etda-card overflow-hidden flex flex-col justify-between">
                <div>
                    <!-- Flyer o cabecera con insignia de fecha estilo EventosScreen.kt -->
                    <div class="relative">
                        ${ev.imagenUrl ? `
                            <div class="w-full h-44 overflow-hidden bg-slate-100 border-b border-slate-100">
                                <img src="${esc(ev.imagenUrl)}" alt="${esc(ev.titulo)}" class="w-full h-full object-cover">
                            </div>` : `
                            <div class="w-full h-32 bg-etda-gradient flex items-center justify-center text-white/30">
                                <i class="fa-solid ${esc(catInfo.icono)} text-4xl"></i>
                            </div>`}

                        <!-- Columna flotante de fecha estilo App Móvil ETDA -->
                        <div class="absolute top-3 left-3 bg-etda-date-badge text-white rounded-xl px-3 py-2 shadow-lg text-center min-w-[3.6rem] border border-white/15">
                            <span class="block bg-[#F57C00] text-white text-[9px] font-extrabold px-1.5 py-0.5 rounded uppercase tracking-wider">${esc(f.mes)}</span>
                            <span class="block text-xl font-extrabold leading-tight mt-0.5">${esc(f.dia)}</span>
                            <span class="block text-[10px] text-white/90 font-semibold">${esc(f.hora)}</span>
                        </div>

                        <!-- Acciones administrativas según permisos granulares -->
                        ${(pCompartir || pEditar || pEliminar) ? `
                            <div class="absolute top-3 right-3 flex items-center gap-1.5 bg-white/95 backdrop-blur-sm p-1 rounded-xl shadow border border-slate-200">
                                ${pCompartir ? `
                                    <button onclick="abrirSocial('${esc(ev.id)}')" class="w-8 h-8 flex items-center justify-center rounded-lg text-[#F57C00] hover:bg-amber-50 transition" title="Compartir en Redes Sociales">
                                        <i class="fa-solid fa-share-nodes text-xs"></i>
                                    </button>` : ''}
                                ${pEditar ? `
                                    <button onclick="abrirModalEvento('${esc(ev.id)}')" class="w-8 h-8 flex items-center justify-center rounded-lg text-[#122652] hover:bg-slate-100 transition" title="Editar Evento">
                                        <i class="fa-solid fa-pen-to-square text-xs"></i>
                                    </button>` : ''}
                                ${pEliminar ? `
                                    <button onclick="eliminarEvento('${esc(ev.id)}')" class="w-8 h-8 flex items-center justify-center rounded-lg text-red-600 hover:bg-red-50 transition" title="Eliminar Evento">
                                        <i class="fa-solid fa-trash-can text-xs"></i>
                                    </button>` : ''}
                            </div>` : ''}
                    </div>

                    <div class="p-5">
                        <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
                            <span class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-extrabold uppercase tracking-wider"
                                style="background-color: ${catInfo.color}18; color: ${catInfo.color};">
                                <i class="fa-solid ${esc(catInfo.icono)}"></i>
                                ${esc(ev.categoria || catInfo.nombre)}
                            </span>
                            ${etiquetaAudiencia(ev)}
                        </div>

                        <h3 class="text-base font-extrabold text-[#122652] leading-snug">${esc(ev.titulo)}</h3>
                        <p class="text-xs text-[#5B6670] mt-2 line-clamp-3 leading-relaxed">${esc(ev.descripcion)}</p>

                        ${materiales.length ? `
                            <div class="mt-3 pt-2.5 border-t border-slate-100 flex items-center gap-2 text-[11px] text-[#0093FF] font-semibold">
                                <i class="fa-solid fa-paperclip"></i>
                                <span>${materiales.length} material(es) adjunto(s) para la app móvil</span>
                            </div>` : ''}
                    </div>
                </div>

                <div class="px-5 py-3.5 bg-[#F4F7FB] border-t border-slate-200/80 space-y-3">
                    <div class="flex justify-between items-center text-xs text-[#5B6670] font-medium">
                        <span class="truncate"><i class="fa-regular fa-clock text-[#0093FF] mr-1"></i> ${esc(f.completo)}</span>
                        <span class="truncate ml-2"><i class="fa-solid fa-location-dot text-[#F57C00] mr-1"></i> ${esc(ev.lugar || 'Campus I')}${infoAud ? ` <b class="text-[#122652]">(${infoAud.asientos} asientos)</b>` : ""}</span>
                    </div>

                    <div class="flex gap-2">
                        <button onclick="registrarAsistenciaEvento('${esc(ev.id)}')"
                            class="flex-1 bg-[#F57C00] hover:bg-amber-600 text-white text-xs font-bold py-2 px-3 rounded-xl shadow-sm transition flex items-center justify-center gap-1.5">
                            <i class="fa-solid fa-user-check"></i> Inscribirme
                        </button>
                        ${pCompartir ? `
                            <button onclick="abrirSocial('${esc(ev.id)}')"
                                class="bg-white hover:bg-amber-50 text-[#122652] border border-slate-300 text-xs font-bold px-3 py-2 rounded-xl transition flex items-center gap-1.5" title="Difundir en redes">
                                <i class="fa-solid fa-share-nodes text-[#F57C00]"></i> Redes
                            </button>` : ''}
                        ${pAsistentes ? `
                            <button onclick="verAsistentesEvento('${esc(ev.id)}')"
                                class="bg-[#122652] hover:bg-[#193773] text-white text-xs font-bold px-3.5 py-2 rounded-xl transition flex items-center gap-1.5">
                                <i class="fa-solid fa-users text-[#F57C00]"></i> Asistentes
                            </button>` : ''}
                    </div>
                </div>
            </article>`;
    }).join("");
}

// ---------- Modal Crear / Editar Evento ----------
let estadoModalEvento = { nuevos: [], existentes: [], grupos: [] };

function alternarAudiencia() {
    const seg = document.querySelector('input[name="ev-audiencia"]:checked')?.value === "segmentado";
    document.getElementById("ev-segmentacion")?.classList.toggle("hidden", !seg);
}

function pintarChipsGrupos() {
    const el = document.getElementById("ev-grupos-chips");
    if (!el) return;
    el.innerHTML = estadoModalEvento.grupos.map((g, i) =>
        `<span class="bg-[#122652]/10 text-[#122652] font-semibold text-xs px-2.5 py-0.5 rounded-full flex items-center gap-1">
            ${esc(g)}
            <button type="button" onclick="quitarGrupo(${i})" class="hover:text-red-600 font-bold">×</button>
        </span>`
    ).join("");
}

function agregarGruposDesdeTexto(texto) {
    (texto || "").split(",").map(t => t.trim()).filter(Boolean).forEach(g => {
        if (!estadoModalEvento.grupos.some(x => x.toLowerCase() === g.toLowerCase())) {
            estadoModalEvento.grupos.push(g);
        }
    });
    pintarChipsGrupos();
}

function quitarGrupo(i) {
    estadoModalEvento.grupos.splice(i, 1);
    pintarChipsGrupos();
}

function pintarArchivosModal() {
    document.getElementById("ev-existentes").innerHTML = estadoModalEvento.existentes.map(a => `
        <div class="relative h-20 border rounded-xl overflow-hidden bg-slate-100">
            ${iconoArchivo(a)}
            <button type="button" onclick="quitarArchivoExistente('${esc(a.id)}')" class="absolute top-1 right-1 bg-red-600 text-white rounded-full w-5 h-5 text-[10px]" title="Eliminar"><i class="fa-solid fa-xmark"></i></button>
            <span class="absolute bottom-0 inset-x-0 bg-black/60 text-white text-[9px] px-1.5 py-0.5 truncate">${esc(a.nombre)}</span>
        </div>`).join("");

    document.getElementById("ev-nuevos").innerHTML = estadoModalEvento.nuevos.map((f, i) => `
        <div class="relative h-20 border-2 border-[#0093FF] rounded-xl overflow-hidden bg-slate-100">
            ${f.type.startsWith("image/")
                ? `<img src="${URL.createObjectURL(f)}" class="w-full h-full object-cover">`
                : `<div class="w-full h-full flex items-center justify-center text-red-600"><i class="fa-solid fa-file-pdf text-2xl"></i></div>`}
            <button type="button" onclick="quitarArchivoNuevo(${i})" class="absolute top-1 right-1 bg-slate-800 text-white rounded-full w-5 h-5 text-[10px]"><i class="fa-solid fa-xmark"></i></button>
            <span class="absolute bottom-0 inset-x-0 bg-black/60 text-white text-[9px] px-1.5 py-0.5 truncate">${esc(f.name)} · ${formatoBytes(f.size)}</span>
        </div>`).join("");
}

function quitarArchivoNuevo(i) {
    estadoModalEvento.nuevos.splice(i, 1);
    pintarArchivosModal();
}

async function quitarArchivoExistente(archivoId) {
    if (!confirm("¿Eliminar este archivo del evento? Los alumnos dejarán de verlo en la app.")) return;
    try {
        const eventoId = document.getElementById("ev-id").value;
        await api(`/eventos/${eventoId}/archivos/${archivoId}`, { method: "DELETE" });
        estadoModalEvento.existentes = estadoModalEvento.existentes.filter(a => a.id !== archivoId);
        pintarArchivosModal();
        toast("Archivo eliminado.", "info");
    } catch (err) {
        toast(err.message, "error");
    }
}

async function abrirModalEvento(id = null) {
    if (!tienePermiso("eventos.publicar")) {
        toast("No tienes permiso para publicar o editar eventos.", "alerta");
        return;
    }
    const ev = id ? eventosCache[id] : null;
    estadoModalEvento = {
        nuevos: [],
        existentes: ev ? (ev.archivos || []).filter(a => a.tipo === "material") : [],
        grupos: ev ? [...(ev.gruposDestino || [])] : []
    };

    if (typeof poblarSelectoresModalEvento === "function") {
        poblarSelectoresModalEvento(ev);
    }
    document.getElementById("ev-modal-titulo").innerText = ev ? "Editar Evento Institucional" : "Publicar Nuevo Evento";
    document.getElementById("ev-btn-guardar").innerText = ev ? "Guardar Cambios" : "Publicar Evento";
    document.getElementById("ev-id").value = ev ? ev.id : "";
    document.getElementById("ev-titulo").value = ev ? ev.titulo : "";
    if (ev && ev.categoria) document.getElementById("ev-categoria").value = ev.categoria;
    document.getElementById("ev-descripcion").value = ev ? ev.descripcion : "";
    document.getElementById("ev-fecha-hora").value = ev ? aInputLocal(ev.fechaHora) : "";
    if (ev && ev.lugar) {
        document.getElementById("ev-lugar").value = ev.lugar;
        if (typeof actualizarBadgeAsientosModal === "function") actualizarBadgeAsientosModal();
    }
    if (ev && ev.idFacultadDestino) {
        document.getElementById("ev-facultad").value = ev.idFacultadDestino;
        if (typeof actualizarCarrerasSegunFacultadModal === "function") {
            actualizarCarrerasSegunFacultadModal(ev.idCarreraDestino || "");
        }
    }

    const radioVal = ev && !ev.esGeneral ? "segmentado" : "general";
    const radioEl = document.querySelector(`input[name="ev-audiencia"][value="${radioVal}"]`);
    if (radioEl) radioEl.checked = true;

    alternarAudiencia();
    pintarChipsGrupos();
    pintarArchivosModal();
    document.getElementById("modal-evento").classList.remove("hidden");
    cargarOpcionesSegmentacion();
}

function abrirModalCrearEvento() {
    abrirModalEvento(null);
}

function cerrarModalEvento() {
    document.getElementById("modal-evento").classList.add("hidden");
}

async function guardarEvento(e) {
    e.preventDefault();
    const id = document.getElementById("ev-id").value;
    const segmentado = document.querySelector('input[name="ev-audiencia"]:checked').value === "segmentado";
    const giEl = document.getElementById("ev-grupo-input");
    if (giEl) { agregarGruposDesdeTexto(giEl.value); giEl.value = ""; }

    const facultad = document.getElementById("ev-facultad").value.trim();
    const carrera = document.getElementById("ev-carrera").value.trim();
    if (segmentado && !facultad && !carrera && estadoModalEvento.grupos.length === 0) {
        toast("Indica al menos una carrera, facultad o grupo, o elige 'Todos los alumnos'.", "alerta");
        return;
    }

    const payload = {
        titulo: document.getElementById("ev-titulo").value.trim(),
        descripcion: document.getElementById("ev-descripcion").value.trim(),
        fechaHora: new Date(document.getElementById("ev-fecha-hora").value).toISOString(),
        lugar: document.getElementById("ev-lugar").value.trim(),
        categoria: document.getElementById("ev-categoria").value.trim() || null,
        esGeneral: !segmentado,
        idFacultadDestino: segmentado ? (facultad || null) : null,
        idCarreraDestino: segmentado ? (carrera || null) : null,
        gruposDestino: segmentado && estadoModalEvento.grupos.length ? estadoModalEvento.grupos : null
    };

    const boton = document.getElementById("ev-btn-guardar");
    boton.disabled = true;
    boton.classList.add("opacity-60");
    try {
        const guardado = await api(id ? `/eventos/${id}` : "/eventos/", {
            method: id ? "PUT" : "POST",
            json: payload
        });
        let aviso = id ? "Evento actualizado correctamente." : "¡Evento publicado en la cartelera!";
        if (estadoModalEvento.nuevos.length) {
            try {
                await subirArchivos(guardado.id, "material", estadoModalEvento.nuevos);
            } catch (err) {
                aviso = `El evento se guardó, pero los archivos fallaron: ${err.message}`;
            }
        }
        cerrarModalEvento();
        toast(aviso, "exito");
        await cargarEventos();
        if (!id && tienePermiso("redes.compartir")) {
            abrirSocial(guardado.id);
        }
    } catch (err) {
        toast(err.message, "error");
    } finally {
        boton.disabled = false;
        boton.classList.remove("opacity-60");
    }
}

async function eliminarEvento(id) {
    if (!tienePermiso("eventos.eliminar")) {
        toast("No tienes permiso para eliminar eventos.", "alerta");
        return;
    }
    if (!confirm("¿Estás seguro de que deseas eliminar permanentemente este evento (incluye sus archivos y registros)?")) return;
    try {
        await api(`/eventos/${id}`, { method: "DELETE" });
        toast("Evento eliminado correctamente.", "exito");
        cargarEventos();
    } catch (err) {
        toast(err.message, "error");
    }
}

// ---------- Registro y Lista de Asistencia ----------
async function registrarAsistenciaEvento(eventoId) {
    try {
        await api(`/eventos/${eventoId}/registro`, { method: "POST" });
        toast("¡Te has inscrito correctamente al evento!", "exito");
    } catch (err) {
        toast(err.message, "error");
    }
}

let asistentesActual = null;
async function verAsistentesEvento(eventoId) {
    if (!tienePermiso("asistencia.gestionar")) {
        toast("No tienes permiso para gestionar listas de asistencia.", "alerta");
        return;
    }
    const ev = eventosCache[eventoId] || {};
    asistentesActual = { id: eventoId, titulo: ev.titulo || "Evento" };
    document.getElementById("modal-asistentes-titulo").innerText = `Lista de Asistencia: ${asistentesActual.titulo}`;
    const tbody = document.getElementById("tabla-asistentes-body");
    tbody.innerHTML = `<tr><td colspan="6" class="p-6 text-center text-slate-400"><i class="fa-solid fa-spinner fa-spin mr-2"></i> Cargando lista de inscritos...</td></tr>`;
    document.getElementById("modal-asistentes").classList.remove("hidden");

    try {
        const asistentes = await api(`/eventos/${eventoId}/asistentes`);
        if (!asistentes || asistentes.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" class="p-6 text-center text-slate-500">Aún no hay alumnos registrados en este evento.</td></tr>`;
            return;
        }
        tbody.innerHTML = asistentes.map((a, i) => `
            <tr class="border-b hover:bg-slate-50">
                <td class="p-3 font-bold text-slate-400">${i + 1}</td>
                <td class="p-3 font-semibold text-[#122652]">${esc(a.nombre)}</td>
                <td class="p-3 text-slate-500 font-mono text-xs">${esc(a.email)}</td>
                <td class="p-3 text-slate-600">${esc([a.carrera, (a.grupos || []).join(", ")].filter(Boolean).join(" · ") || "—")}</td>
                <td class="p-3 text-slate-400">${a.fecha_registro ? new Date(a.fecha_registro).toLocaleString("es-MX") : "Reciente"}</td>
                <td class="p-3 text-center">
                    <input type="checkbox" ${a.asistio ? "checked" : ""} onchange="marcarAsistio(${Number(a.id)}, this)" class="w-4 h-4 accent-[#10B981] rounded cursor-pointer">
                </td>
            </tr>`).join("");
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" class="p-6 text-center text-red-500">${esc(err.message)}</td></tr>`;
    }
}

async function marcarAsistio(asistenciaId, checkbox) {
    try {
        await api(`/eventos/${asistentesActual.id}/asistentes/${asistenciaId}`, {
            method: "PATCH",
            json: { asistio: checkbox.checked }
        });
    } catch (err) {
        checkbox.checked = !checkbox.checked;
        toast(err.message, "error");
    }
}

function cerrarModalAsistentes() {
    document.getElementById("modal-asistentes").classList.add("hidden");
}

async function descargarPDFDe(eventoId) {
    try {
        const res = await api(`/eventos/${eventoId}/asistentes/pdf`, { blob: true });
        const nombre = (res.headers.get("Content-Disposition") || "").match(/filename="([^"]+)"/)?.[1] || "asistencia_unach.pdf";
        const url = URL.createObjectURL(await res.blob());
        const a = Object.assign(document.createElement("a"), { href: url, download: nombre });
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 5000);
        toast("Lista de asistencia en PDF descargada.", "exito");
    } catch (err) {
        toast(err.message, "error");
    }
}

function descargarAsistenciaPDF() {
    if (asistentesActual) descargarPDFDe(asistentesActual.id);
}
