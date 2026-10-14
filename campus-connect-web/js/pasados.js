// ============================================================================
// MÓDULO DE EVENTOS PASADOS Y GESTIÓN DE EVIDENCIAS
// ============================================================================

let pasadosCache = {};

async function cargarPasados() {
    const cont = document.getElementById("pasados-container");
    if (!cont) return;
    cont.innerHTML = `<p class="col-span-full text-center text-slate-400 py-8"><i class="fa-solid fa-spinner fa-spin mr-2"></i> Cargando historial de eventos finalizados...</p>`;

    const pEvidencias = tienePermiso("evidencias.gestionar");
    const pAsistencia = tienePermiso("asistencia.gestionar");
    const pEstadisticas = tienePermiso("estadisticas.ver");

    try {
        const lista = await api("/eventos/pasados");
        pasadosCache = {};
        (lista || []).forEach(e => {
            pasadosCache[e.id] = e;
            eventosCache[e.id] = e;
        });

        if (!lista || !lista.length) {
            cont.innerHTML = `
                <div class="col-span-full etda-card p-10 text-center text-slate-500">
                    <i class="fa-solid fa-clock-rotate-left text-3xl text-slate-300 mb-2"></i>
                    <p class="font-bold text-[#122652]">Todavía no hay eventos finalizados</p>
                    <p class="text-xs mt-1">Aquí aparecerán los eventos una vez cumplida su fecha programada.</p>
                </div>`;
            return;
        }

        cont.innerHTML = lista.map(ev => {
            const evid = (ev.archivos || []).filter(a => a.tipo === "evidencia");
            const f = extraerFechaVisualWeb(ev.fechaHora);
            return `
                <div class="etda-card overflow-hidden flex flex-col justify-between">
                    <div>
                        <div class="h-32 bg-slate-100 border-b grid ${evid.length ? "grid-cols-3 gap-0.5" : ""}">
                            ${evid.length
                                ? evid.slice(0, 3).map(a => `<div class="overflow-hidden h-full">${iconoArchivo(a)}</div>`).join("")
                                : `<div class="flex flex-col items-center justify-center text-slate-400 text-xs">
                                    <i class="fa-solid fa-camera text-xl mb-1 text-slate-300"></i>
                                    <span>Sin evidencias cargadas</span>
                                   </div>`}
                        </div>
                        <div class="p-5">
                            <div class="flex items-center justify-between gap-2 mb-1.5">
                                <span class="text-[10px] font-extrabold text-[#0093FF] uppercase tracking-wider">${esc(ev.categoria || "General")}</span>
                                <span class="text-[10px] font-bold px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">Finalizado</span>
                            </div>
                            <h3 class="font-extrabold text-[#122652] text-base">${esc(ev.titulo)}</h3>
                            <p class="text-xs text-[#5B6670] mt-1"><i class="fa-regular fa-calendar text-[#0093FF] mr-1"></i>${esc(f.completo)} · ${esc(ev.lugar)}</p>

                            <div class="grid grid-cols-3 gap-2 text-center bg-[#F4F7FB] p-2.5 rounded-xl mt-3 border border-slate-200/70">
                                <div>
                                    <span class="block text-sm font-extrabold text-[#122652]">${ev.totalRegistrados ?? 0}</span>
                                    <span class="text-[10px] text-[#5B6670]">Inscritos</span>
                                </div>
                                <div>
                                    <span class="block text-sm font-extrabold text-emerald-600">${ev.totalAsistieron ?? 0}</span>
                                    <span class="text-[10px] text-[#5B6670]">Asistieron</span>
                                </div>
                                <div>
                                    <span class="block text-sm font-extrabold ${evid.length ? "text-[#0093FF]" : "text-amber-600"}">${evid.length}</span>
                                    <span class="text-[10px] text-[#5B6670]">Evidencias</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="px-5 py-3.5 bg-slate-50 border-t flex flex-wrap gap-2">
                        ${pEvidencias ? `
                            <button onclick="abrirEvidencias('${esc(ev.id)}')" class="flex-1 bg-[#122652] hover:bg-[#193773] text-white text-xs font-bold py-2 px-3 rounded-xl transition">
                                <i class="fa-solid fa-images text-[#F57C00] mr-1"></i> Evidencias
                            </button>` : ''}
                        ${pAsistencia ? `
                            <button onclick="verAsistentesEvento('${esc(ev.id)}')" class="flex-1 bg-slate-700 hover:bg-slate-800 text-white text-xs font-bold py-2 px-3 rounded-xl transition">
                                <i class="fa-solid fa-users mr-1"></i> Asistentes
                            </button>
                            <button onclick="descargarPDFDe('${esc(ev.id)}')" class="border border-red-200 bg-white text-red-600 hover:bg-red-50 text-xs font-bold py-2 px-3 rounded-xl transition" title="Descargar Lista PDF">
                                <i class="fa-solid fa-file-pdf"></i> PDF
                            </button>` : ''}
                        ${pEstadisticas ? `
                            <button onclick="verEstadisticasDe('${esc(ev.id)}')" class="border border-slate-300 bg-white text-[#122652] hover:bg-slate-100 text-xs font-bold py-2 px-3 rounded-xl transition" title="Ver y Descargar Estadísticas">
                                <i class="fa-solid fa-chart-column text-[#F57C00] mr-1"></i> Métricas
                            </button>` : ''}
                    </div>
                </div>`;
        }).join("");

        if (!document.getElementById("modal-evidencias").classList.contains("hidden")) {
            pintarEvidencias();
        }
    } catch (err) {
        cont.innerHTML = `<p class="col-span-full text-center text-red-500 py-8">${esc(err.message)}</p>`;
    }
}

function abrirEvidencias(id) {
    if (!tienePermiso("evidencias.gestionar")) {
        toast("No tienes permiso para gestionar evidencias.", "alerta");
        return;
    }
    document.getElementById("evid-evento-id").value = id;
    pintarEvidencias();
    document.getElementById("modal-evidencias").classList.remove("hidden");
}

function cerrarModalEvidencias() {
    document.getElementById("modal-evidencias").classList.add("hidden");
}

function pintarEvidencias() {
    const ev = pasadosCache[document.getElementById("evid-evento-id").value];
    if (!ev) return;
    const evid = (ev.archivos || []).filter(a => a.tipo === "evidencia");
    document.getElementById("evid-titulo").innerText = `Evidencias: ${ev.titulo}`;
    document.getElementById("evid-sub").innerText = `${new Date(ev.fechaHora).toLocaleString("es-MX")} · ${ev.lugar} · ${evid.length} archivo(s)`;
    document.getElementById("evid-galeria").innerHTML = evid.length
        ? evid.map(a => `
            <div class="relative border rounded-xl overflow-hidden bg-slate-100 h-36 group">
                <a href="${esc(a.url)}" target="_blank" rel="noopener" class="block w-full h-full">${iconoArchivo(a)}</a>
                <button onclick="eliminarEvidencia('${esc(ev.id)}', '${esc(a.id)}')" class="absolute top-2 right-2 bg-red-600 text-white rounded-full w-7 h-7 text-xs shadow hover:bg-red-700" title="Eliminar">
                    <i class="fa-solid fa-trash"></i>
                </button>
                <span class="absolute bottom-0 inset-x-0 bg-black/60 text-white text-[10px] px-2 py-1 truncate">${esc(a.nombre)}</span>
            </div>`).join("")
        : `<p class="col-span-full text-center text-slate-400 text-sm py-6">Aún no hay evidencias cargadas para este evento.</p>`;
}

async function eliminarEvidencia(eventoId, archivoId) {
    if (!confirm("¿Eliminar esta evidencia del evento?")) return;
    try {
        await api(`/eventos/${eventoId}/archivos/${archivoId}`, { method: "DELETE" });
        await cargarPasados();
        toast("Evidencia eliminada.", "info");
    } catch (err) {
        toast(err.message, "error");
    }
}

async function subirEvidencias(lista) {
    const archivos = validarArchivos(lista);
    if (!archivos.length) return;
    const id = document.getElementById("evid-evento-id").value;
    try {
        toast(`Subiendo ${archivos.length} archivo(s)...`, "info");
        await subirArchivos(id, "evidencia", archivos);
        await cargarPasados();
        toast("Evidencias guardadas correctamente.", "exito");
    } catch (err) {
        toast(err.message, "error");
    }
}
