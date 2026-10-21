// ============================================================================
// UTILIDADES COMPARTIDAS, CLIENTE API REST Y MANEJO DE ARCHIVOS
// ============================================================================

const esc = (v) => String(v ?? "").replace(/[&<>"']/g, c => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
}[c]));

function mensajeDeError(data, porDefecto) {
    if (!data) return porDefecto;
    if (Array.isArray(data.detail)) {
        return (data.detail[0]?.msg || porDefecto).replace("Value error, ", "");
    }
    return data.detail || porDefecto;
}

async function api(ruta, opciones = {}) {
    const headers = {
        "Authorization": `Bearer ${userState.token}`,
        ...(opciones.headers || {})
    };
    if (opciones.json !== undefined) {
        headers["Content-Type"] = "application/json";
        opciones.body = JSON.stringify(opciones.json);
    }
    const res = await fetch(`${API_BASE_URL}${ruta}`, {
        method: opciones.method || "GET",
        headers,
        body: opciones.body
    });
    if (opciones.blob && res.ok) return res;
    let data = null;
    try {
        data = await res.json();
    } catch (_) {
        /* sin cuerpo JSON */
    }
    if (!res.ok) throw new Error(mensajeDeError(data, "No se pudo completar la acción."));
    return data;
}

function toast(mensaje, tipo = "info") {
    const colores = {
        info: "bg-[#122652] border-[#0093FF]/40 text-white",
        exito: "bg-emerald-900 border-emerald-500/40 text-white",
        alerta: "bg-amber-800 border-amber-400/40 text-white",
        error: "bg-red-900 border-red-400/40 text-white"
    };
    const iconos = {
        info: "fa-circle-info text-[#F57C00]",
        exito: "fa-circle-check text-emerald-400",
        alerta: "fa-triangle-exclamation text-amber-300",
        error: "fa-circle-exclamation text-red-300"
    };
    const el = document.createElement("div");
    el.className = `fixed bottom-5 right-5 flex items-center gap-3 px-4 py-3 rounded-xl shadow-2xl border text-sm font-medium z-[80] transition-all duration-300 ${colores[tipo] || colores.info}`;
    el.innerHTML = `<i class="fa-solid ${iconos[tipo] || iconos.info} text-base"></i><span>${esc(mensaje)}</span>`;
    document.body.appendChild(el);
    setTimeout(() => {
        el.style.opacity = "0";
        setTimeout(() => el.remove(), 250);
    }, 3600);
}

function aInputLocal(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    const p = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

// Extrae mes, día y hora igual que extraerFechaVisual() en EventosScreen.kt
function extraerFechaVisualWeb(fechaHora) {
    if (!fechaHora) return { mes: "OCT", dia: "15", hora: "10:00", completo: "Próximamente" };
    try {
        const meses = ["ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC"];
        const d = new Date(fechaHora);
        if (isNaN(d.getTime())) return { mes: "OCT", dia: "15", hora: "10:00", completo: fechaHora };
        const mes = meses[d.getMonth()] || "OCT";
        const dia = String(d.getDate()).padStart(2, "0");
        const hora = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
        return {
            mes,
            dia,
            hora,
            completo: d.toLocaleString("es-MX", { dateStyle: "medium", timeStyle: "short" })
        };
    } catch (_) {
        return { mes: "OCT", dia: "15", hora: "10:00", completo: "Próximamente" };
    }
}

// Detecta categoría visual igual que detectarCategoriaEvento() en EventosScreen.kt
function detectarCategoriaEventoWeb(ev) {
    const texto = `${ev.categoria || ""} ${ev.titulo || ""} ${ev.descripcion || ""}`.toLowerCase();
    if (texto.includes("tutor") || texto.includes("asesor")) return CATEGORIAS_ETDA[1];
    if (texto.includes("conferencia") || texto.includes("plática") || texto.includes("foro") || texto.includes("congreso")) return CATEGORIAS_ETDA[2];
    if (texto.includes("taller") || texto.includes("workshop") || texto.includes("práctic") || texto.includes("curso")) return CATEGORIAS_ETDA[3];
    if (texto.includes("deport") || texto.includes("torneo") || texto.includes("fútbol") || texto.includes("carrera")) return CATEGORIAS_ETDA[4];
    if (texto.includes("cultura") || texto.includes("arte") || texto.includes("música") || texto.includes("danza") || texto.includes("festival")) return CATEGORIAS_ETDA[5];
    return CATEGORIAS_ETDA[6];
}

function obtenerIniciales(nombre, email) {
    const base = (nombre || email || "UN").trim();
    const partes = base.replace(/[0-9]/g, "").split(/[\s._-]+/).filter(Boolean);
    if (partes.length >= 2) return (partes[0][0] + partes[1][0]).toUpperCase();
    return base.slice(0, 2).toUpperCase();
}

const formatoBytes = (b) => b > 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`;
const TIPOS_OK = ["image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf"];
const MAX_MB = 10;

function iconoArchivo(a) {
    return a.esImagen
        ? `<img src="${esc(a.url)}" alt="${esc(a.nombre || '')}" class="w-full h-full object-cover">`
        : `<div class="w-full h-full flex flex-col items-center justify-center bg-red-50 text-red-600"><i class="fa-solid fa-file-pdf text-2xl"></i><span class="text-[9px] font-bold mt-1">PDF</span></div>`;
}

function activarDropzone(zonaId, inputId, alRecibir) {
    const zona = document.getElementById(zonaId);
    const input = document.getElementById(inputId);
    if (!zona || !input) return;
    zona.addEventListener("click", () => input.click());
    input.addEventListener("change", () => {
        alRecibir([...input.files]);
        input.value = "";
    });
    ["dragenter", "dragover"].forEach(ev =>
        zona.addEventListener(ev, (e) => {
            e.preventDefault();
            zona.classList.add("border-[#0093FF]", "bg-sky-50/60");
        })
    );
    ["dragleave", "drop"].forEach(ev =>
        zona.addEventListener(ev, (e) => {
            e.preventDefault();
            zona.classList.remove("border-[#0093FF]", "bg-sky-50/60");
        })
    );
    zona.addEventListener("drop", (e) => alRecibir([...e.dataTransfer.files]));
}

function validarArchivos(lista) {
    const validos = [];
    for (const f of lista) {
        if (!TIPOS_OK.includes(f.type)) {
            toast(`"${f.name}": formato no permitido`, "alerta");
            continue;
        }
        if (f.size > MAX_MB * 1024 * 1024) {
            toast(`"${f.name}": supera ${MAX_MB} MB`, "alerta");
            continue;
        }
        validos.push(f);
    }
    return validos;
}

async function subirArchivos(eventoId, tipo, archivos) {
    const fd = new FormData();
    fd.append("tipo", tipo);
    archivos.forEach(f => fd.append("archivos", f));
    return api(`/eventos/${eventoId}/archivos`, { method: "POST", body: fd });
}

let opcionesSegmentacion = { facultades: [], carreras: [], grupos: [] };
async function cargarOpcionesSegmentacion() {
    try {
        opcionesSegmentacion = await api("/eventos/opciones-segmentacion");
    } catch (_) {
        /* opcional */
    }
    const llenar = (id, vals) => {
        const el = document.getElementById(id);
        if (el) el.innerHTML = (vals || []).map(v => `<option value="${esc(v)}">`).join("");
    };
    llenar("dl-facultades", opcionesSegmentacion.facultades);
    llenar("dl-carreras", opcionesSegmentacion.carreras);
    llenar("dl-grupos", opcionesSegmentacion.grupos);
}

function escaparCeldaCSV(valor) {
    const str = String(valor ?? "").replace(/"/g, '""');
    return /[",\n\r]/.test(str) ? `"${str}"` : str;
}
