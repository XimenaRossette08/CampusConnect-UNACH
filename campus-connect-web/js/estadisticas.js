// ============================================================================
// MÓDULO DE ESTADÍSTICAS Y EXPORTACIÓN A EXCEL (.XLSX CON GRÁFICAS), PDF Y PNG
// ============================================================================

let graficas = [];
let estadoEstadisticasActual = {
    modo: "general", // "general" | "evento"
    eventoId: null,
    datos: null,
    fechaConsulta: null
};

function tarjetaKPI(titulo, valor, icono, color = "text-[#122652]", bgIcono = "bg-[#122652]/10") {
    return `
        <div class="etda-card p-4 flex items-center gap-3.5">
            <div class="w-11 h-11 rounded-xl ${bgIcono} flex items-center justify-center shrink-0">
                <i class="fa-solid ${icono} text-lg ${color}"></i>
            </div>
            <div>
                <p class="text-2xl font-extrabold text-[#122652] leading-tight">${esc(valor)}</p>
                <p class="text-xs font-medium text-[#5B6670]">${esc(titulo)}</p>
            </div>
        </div>`;
}

function cajaGrafica(id, titulo, ancho = "") {
    return `
        <div class="etda-card p-5 ${ancho}">
            <div class="flex items-center justify-between mb-3 pb-2 border-b border-slate-100">
                <h3 class="text-sm font-extrabold text-[#122652] flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full bg-[#F57C00]"></span>
                    ${esc(titulo)}
                </h3>
                <button type="button" onclick="descargarGraficaPNG('${esc(id)}', '${esc(titulo)}')"
                    class="text-[11px] font-semibold text-[#5B6670] hover:text-[#122652] bg-slate-100 hover:bg-slate-200 px-2.5 py-1 rounded-lg transition flex items-center gap-1"
                    title="Descargar esta gráfica como imagen PNG">
                    <i class="fa-solid fa-download text-[#0093FF]"></i> PNG
                </button>
            </div>
            <div class="relative h-64">
                <canvas id="${id}"></canvas>
            </div>
        </div>`;
}

function dibujar(id, config) {
    if (typeof Chart === "undefined") return;
    const canvas = document.getElementById(id);
    if (!canvas) return;
    graficas.push(new Chart(canvas, {
        ...config,
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: { duration: 400 },
            ...(config.options || {})
        }
    }));
}

function limpiarGraficas() {
    graficas.forEach(g => g.destroy());
    graficas = [];
}

async function iniciarEstadisticas() {
    if (!tienePermiso("estadisticas.ver")) {
        toast("No tienes permiso para consultar estadísticas.", "alerta");
        return;
    }
    if (!eventosLista.length) {
        try {
            eventosLista = await api("/eventos/feed");
            eventosLista.forEach(e => { eventosCache[e.id] = e; });
        } catch (_) {
            /* continuará */
        }
    }
    const sel = document.getElementById("stats-selector");
    const actual = sel.value;
    sel.innerHTML = `<option value="general">Todos los eventos (Vista General Institucional)</option>` +
        [...eventosLista]
            .sort((a, b) => new Date(b.fechaHora) - new Date(a.fechaHora))
            .map(e => `<option value="${esc(e.id)}">${esc(e.titulo)} — ${esc(new Date(e.fechaHora).toLocaleDateString("es-MX"))}</option>`)
            .join("");
    if ([...sel.options].some(o => o.value === actual)) sel.value = actual;
    cargarEstadisticas();
}

function verEstadisticasDe(eventoId) {
    if (!tienePermiso("estadisticas.ver")) {
        toast("No tienes permiso para ver estadísticas.", "alerta");
        return;
    }
    cambiarPestaña("estadisticas");
    setTimeout(() => {
        const sel = document.getElementById("stats-selector");
        if (sel) {
            sel.value = eventoId;
            cargarEstadisticas();
        }
    }, 60);
}

async function cargarEstadisticas() {
    const cont = document.getElementById("stats-contenido");
    const valor = document.getElementById("stats-selector").value;
    cont.innerHTML = `
        <div class="etda-card p-10 text-center text-slate-400">
            <i class="fa-solid fa-spinner fa-spin text-2xl text-[#F57C00] mb-2"></i>
            <p class="text-sm font-medium text-[#122652]">Calculando métricas e indicadores institucionales...</p>
        </div>`;
    limpiarGraficas();
    try {
        if (typeof Chart === "undefined") {
            throw new Error("No se pudo cargar la librería de gráficas (revisa tu conexión a internet).");
        }
        if (valor === "general") {
            await pintarEstadisticasGenerales(cont);
        } else {
            await pintarEstadisticasEvento(cont, valor);
        }
    } catch (err) {
        cont.innerHTML = `<div class="etda-card p-6 text-center text-red-600 font-semibold">${esc(err.message)}</div>`;
    }
}

async function pintarEstadisticasGenerales(cont) {
    const d = await api("/estadisticas/general");
    estadoEstadisticasActual = {
        modo: "general",
        eventoId: null,
        datos: d,
        fechaConsulta: new Date().toLocaleString("es-MX")
    };

    cont.innerHTML = `
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
            ${tarjetaKPI("Eventos totales", d.totalEventos, "fa-calendar-days", "text-[#122652]", "bg-[#122652]/10")}
            ${tarjetaKPI("Próximos / Finalizados", `${d.proximos} / ${d.finalizados}`, "fa-clock", "text-[#F57C00]", "bg-[#F57C00]/15")}
            ${tarjetaKPI("Registros totales", d.totalRegistros, "fa-user-check", "text-emerald-600", "bg-emerald-50")}
            ${tarjetaKPI("Tasa de asistencia", `${d.tasaAsistencia}%`, "fa-circle-check", "text-emerald-600", "bg-emerald-50")}
            ${tarjetaKPI("Promedio por evento", d.promedioRegistrosPorEvento, "fa-chart-line", "text-[#0093FF]", "bg-sky-50")}
            ${tarjetaKPI("Generales / Segmentados", `${d.eventosGenerales} / ${d.eventosSegmentados}`, "fa-bullseye", "text-indigo-600", "bg-indigo-50")}
            ${tarjetaKPI("Con evidencia", d.finalizadosConEvidencia, "fa-images", "text-[#122652]", "bg-slate-100")}
            ${tarjetaKPI("Sin evidencia", d.finalizadosSinEvidencia, "fa-triangle-exclamation", "text-red-500", "bg-red-50")}
        </div>
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            ${cajaGrafica("g-mes", "Eventos y registros por mes")}
            ${cajaGrafica("g-categoria", "Distribución de eventos por categoría")}
            ${cajaGrafica("g-carrera", "Registros y asistencia por programa educativo (carrera)")}
            ${cajaGrafica("g-top", "Eventos con mayor participación estudiantil")}
        </div>`;

    dibujar("g-mes", {
        type: "bar",
        data: {
            labels: (d.porMes || []).map(m => m.mes),
            datasets: [
                { label: "Eventos", data: (d.porMes || []).map(m => m.eventos), backgroundColor: PALETA_ETDA[0], borderRadius: 6 },
                { label: "Registros", data: (d.porMes || []).map(m => m.registros), backgroundColor: PALETA_ETDA[1], borderRadius: 6 }
            ]
        },
        options: { scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
    });

    dibujar("g-categoria", {
        type: "doughnut",
        data: {
            labels: (d.porCategoria || []).map(c => c.categoria),
            datasets: [{ data: (d.porCategoria || []).map(c => c.eventos), backgroundColor: PALETA_ETDA }]
        }
    });

    dibujar("g-carrera", {
        type: "bar",
        data: {
            labels: (d.porCarrera || []).map(c => c.carrera),
            datasets: [
                { label: "Registros", data: (d.porCarrera || []).map(c => c.registros), backgroundColor: PALETA_ETDA[2], borderRadius: 6 },
                { label: "Asistieron", data: (d.porCarrera || []).map(c => c.asistencias), backgroundColor: PALETA_ETDA[3], borderRadius: 6 }
            ]
        },
        options: { indexAxis: "y", scales: { x: { beginAtZero: true, ticks: { precision: 0 } } } }
    });

    dibujar("g-top", {
        type: "bar",
        data: {
            labels: (d.topEventos || []).map(t => t.titulo.length > 28 ? t.titulo.slice(0, 27) + "…" : t.titulo),
            datasets: [
                { label: "Registrados", data: (d.topEventos || []).map(t => t.registrados), backgroundColor: PALETA_ETDA[0], borderRadius: 6 },
                { label: "Asistieron", data: (d.topEventos || []).map(t => t.asistieron), backgroundColor: PALETA_ETDA[3], borderRadius: 6 }
            ]
        },
        options: { indexAxis: "y", scales: { x: { beginAtZero: true, ticks: { precision: 0 } } } }
    });
}

async function pintarEstadisticasEvento(cont, id) {
    const d = await api(`/estadisticas/evento/${id}`);
    estadoEstadisticasActual = {
        modo: "evento",
        eventoId: id,
        datos: d,
        fechaConsulta: new Date().toLocaleString("es-MX")
    };

    cont.innerHTML = `
        <div class="etda-card p-5 flex flex-wrap items-center justify-between gap-4 border-l-4 border-l-[#F57C00]">
            <div>
                <span class="text-[10px] font-extrabold uppercase tracking-wider text-[#0093FF]">Reporte Individual de Evento</span>
                <h3 class="text-lg font-extrabold text-[#122652]">${esc(d.evento.titulo)}</h3>
                <p class="text-xs text-[#5B6670] mt-0.5">
                    <i class="fa-regular fa-calendar mr-1 text-[#F57C00]"></i>${esc(d.evento.fecha)} ·
                    <i class="fa-solid fa-location-dot mx-1 text-[#0093FF]"></i>${esc(d.evento.lugar)} ·
                    <span class="font-bold">${d.evento.finalizado ? "Evento Finalizado" : "Evento Próximo"}</span>
                </p>
            </div>
            <div class="flex items-center gap-2">
                ${tienePermiso("asistencia.gestionar") ? `
                    <button onclick="descargarPDFDe('${esc(id)}')" class="bg-red-50 hover:bg-red-100 text-red-700 border border-red-200 text-xs font-bold px-3 py-2 rounded-xl transition">
                        <i class="fa-solid fa-file-pdf mr-1"></i> Lista de Asistencia PDF
                    </button>` : ''}
            </div>
        </div>
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
            ${tarjetaKPI("Alumnos registrados", d.registrados, "fa-user-check", "text-[#0093FF]", "bg-sky-50")}
            ${tarjetaKPI("Asistencias confirmadas", d.asistieron, "fa-circle-check", "text-emerald-600", "bg-emerald-50")}
            ${tarjetaKPI("Tasa de asistencia", `${d.tasaAsistencia}%`, "fa-percent", "text-[#F57C00]", "bg-amber-50")}
            ${tarjetaKPI("Evidencias / Materiales", `${d.archivosEvidencia} / ${d.archivosMaterial}`, "fa-images", "text-[#122652]", "bg-slate-100")}
        </div>
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            ${cajaGrafica("g-ev-carrera", "Participación por Carrera")}
            ${cajaGrafica("g-ev-grupo", "Participación por Grupo")}
            ${cajaGrafica("g-ev-dia", "Evolución de inscripciones por día", "lg:col-span-2")}
        </div>`;

    const barras = (id2, filas, clave) => dibujar(id2, {
        type: "bar",
        data: {
            labels: (filas || []).map(f => f[clave]),
            datasets: [
                { label: "Registros", data: (filas || []).map(f => f.registros), backgroundColor: PALETA_ETDA[2], borderRadius: 6 },
                { label: "Asistieron", data: (filas || []).map(f => f.asistencias), backgroundColor: PALETA_ETDA[3], borderRadius: 6 }
            ]
        },
        options: { scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
    });

    barras("g-ev-carrera", d.porCarrera, "carrera");
    barras("g-ev-grupo", d.porGrupo, "grupo");
    dibujar("g-ev-dia", {
        type: "line",
        data: {
            labels: (d.inscripcionesPorDia || []).map(x => x.fecha),
            datasets: [{
                label: "Inscripciones",
                data: (d.inscripcionesPorDia || []).map(x => x.registros),
                borderColor: PALETA_ETDA[0],
                backgroundColor: "rgba(18, 38, 82, 0.12)",
                fill: true,
                tension: 0.3
            }]
        },
        options: { scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
    });
}

// ============================================================================
// AUXILIAR: RENDERIZAR CADA GRÁFICA CON TARJETA BLANCA Y TÍTULO PARA EXCEL/PNG
// ============================================================================
function renderizarCanvasConMarcoInstitucional(canvasOrig, tituloGrafica) {
    const tempCanvas = document.createElement("canvas");
    const pad = 28;
    const headerH = 54;
    tempCanvas.width = Math.max(canvasOrig.width, 520) + pad * 2;
    tempCanvas.height = Math.max(canvasOrig.height, 260) + pad * 2 + headerH;

    const ctx = tempCanvas.getContext("2d");
    // Fondo blanco limpio
    ctx.fillStyle = "#FFFFFF";
    ctx.fillRect(0, 0, tempCanvas.width, tempCanvas.height);

    // Borde exterior gris suave
    ctx.strokeStyle = "#CBD5E1";
    ctx.lineWidth = 2;
    ctx.strokeRect(1, 1, tempCanvas.width - 2, tempCanvas.height - 2);

    // Franja superior Azul Profundo UNACH + detalle Naranja ETDA
    ctx.fillStyle = "#122652";
    ctx.fillRect(0, 0, tempCanvas.width, 10);
    ctx.fillStyle = "#F57C00";
    ctx.fillRect(0, 10, 140, 4);

    // Título de la gráfica
    ctx.fillStyle = "#122652";
    ctx.font = "bold 16px Inter, Segoe UI, Arial, sans-serif";
    ctx.fillText(tituloGrafica, pad, 38);

    ctx.fillStyle = "#5B6670";
    ctx.font = "11px Inter, Segoe UI, Arial, sans-serif";
    ctx.fillText(`Campus Connect UNACH • ETDA (${new Date().toLocaleDateString("es-MX")})`, pad, 56);

    // Dibujar gráfica centrada
    ctx.drawImage(canvasOrig, pad, pad + headerH, tempCanvas.width - pad * 2, tempCanvas.height - pad * 2 - headerH);
    return tempCanvas.toDataURL("image/png");
}

// ============================================================================
// 1. DESCARGAR LIBRO DE EXCEL REAL (.XLSX) ESTILIZADO Y CON GRÁFICAS INCRUSTADAS
// ============================================================================
async function descargarEstadisticasExcel() {
    const est = estadoEstadisticasActual;
    if (!est || !est.datos) {
        toast("Primero espera a que se carguen las estadísticas.", "alerta");
        return;
    }

    if (typeof ExcelJS === "undefined") {
        toast("Cargando motor de Excel, intenta de nuevo en un segundo...", "alerta");
        return;
    }

    try {
        toast("Generando libro de Excel (.xlsx) con gráficas y formato institucional...", "info");
        const d = est.datos;
        const wb = new ExcelJS.Workbook();
        wb.creator = "Campus Connect UNACH - ETDA";
        wb.created = new Date();

        // Estilos reutilizables
        const borderThin = {
            top: { style: "thin", color: { argb: "FFCBD5E1" } },
            left: { style: "thin", color: { argb: "FFCBD5E1" } },
            bottom: { style: "thin", color: { argb: "FFCBD5E1" } },
            right: { style: "thin", color: { argb: "FFCBD5E1" } }
        };
        const fillNavy = { type: "pattern", pattern: "solid", fgColor: { argb: "FF122652" } };
        const fillNavyMid = { type: "pattern", pattern: "solid", fgColor: { argb: "FF193773" } };
        const fillOrange = { type: "pattern", pattern: "solid", fgColor: { argb: "FFF57C00" } };
        const fillZebra = { type: "pattern", pattern: "solid", fgColor: { argb: "FFF4F7FB" } };
        const fillWhite = { type: "pattern", pattern: "solid", fgColor: { argb: "FFFFFFFF" } };

        // --------------------------------------------------------------------
        // HOJA 1: DASHBOARD VISUAL, KPIS Y GRÁFICAS INCRUSTADAS
        // --------------------------------------------------------------------
        const ws1 = wb.addWorksheet("Dashboard y Gráficas", {
            views: [{ showGridLines: false }]
        });

        ws1.columns = [
            { key: "margin", width: 3 },
            { key: "colB", width: 34 },
            { key: "colC", width: 18 },
            { key: "spacer", width: 4 },
            { key: "colE", width: 34 },
            { key: "colF", width: 18 },
            { key: "marginR", width: 3 }
        ];

        // Encabezado Institucional UNACH (Filas 2 y 3)
        ws1.mergeCells("B2:F2");
        const titleCell = ws1.getCell("B2");
        titleCell.value = "UNIVERSIDAD AUTÓNOMA DE CHIAPAS (UNACH) • ESCUELA DE TECNOLOGÍAS DIGITALES APLICADAS";
        titleCell.fill = fillNavy;
        titleCell.font = { name: "Calibri", size: 13, bold: true, color: { argb: "FFFFFFFF" } };
        titleCell.alignment = { vertical: "middle", horizontal: "center" };
        ws1.getRow(2).height = 28;

        ws1.mergeCells("B3:F3");
        const subTitleCell = ws1.getCell("B3");
        subTitleCell.value = est.modo === "general"
            ? `REPORTE EJECUTIVO DE ESTADÍSTICAS GENERALES — Emitido: ${est.fechaConsulta}`
            : `REPORTE DE EVENTO: ${(d.evento?.titulo || "").toUpperCase()} — Emitido: ${est.fechaConsulta}`;
        subTitleCell.fill = fillOrange;
        subTitleCell.font = { name: "Calibri", size: 11, bold: true, color: { argb: "FFFFFFFF" } };
        subTitleCell.alignment = { vertical: "middle", horizontal: "center" };
        ws1.getRow(3).height = 22;

        // Sección 1: Indicadores Clave (KPIs) en dos bloques paralelos (B:C y E:F)
        ws1.mergeCells("B5:F5");
        const sec1 = ws1.getCell("B5");
        sec1.value = "1. RESUMEN EJECUTIVO DE INDICADORES CLAVE (KPIs)";
        sec1.fill = fillNavyMid;
        sec1.font = { name: "Calibri", size: 11, bold: true, color: { argb: "FFFFFFFF" } };
        sec1.alignment = { vertical: "middle", horizontal: "left", indent: 1 };
        ws1.getRow(5).height = 22;

        // Encabezados de tabla KPI
        ["B6", "C6", "E6", "F6"].forEach((coord, idx) => {
            const c = ws1.getCell(coord);
            c.value = idx % 2 === 0 ? "Indicador Institucional" : "Valor";
            c.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FFE2E8F0" } };
            c.font = { name: "Calibri", size: 10, bold: true, color: { argb: "FF122652" } };
            c.alignment = { vertical: "middle", horizontal: idx % 2 === 0 ? "left" : "center" };
            c.border = borderThin;
        });
        ws1.getRow(6).height = 20;

        const paresKPI = est.modo === "general"
            ? [
                ["Eventos Totales Registrados", d.totalEventos, "Promedio de Registros por Evento", d.promedioRegistrosPorEvento],
                ["Eventos Próximos / Activos", d.proximos, "Eventos Generales (Toda la comunidad)", d.eventosGenerales],
                ["Eventos Finalizados", d.finalizados, "Eventos Segmentados (Carrera/Grupo)", d.eventosSegmentados],
                ["Inscripciones Totales de Alumnos", d.totalRegistros, "Finalizados con Evidencia Fotográfica", d.finalizadosConEvidencia],
                ["Tasa de Asistencia Confirmada", `${d.tasaAsistencia}%`, "Finalizados sin Evidencia", d.finalizadosSinEvidencia]
            ]
            : [
                ["Evento Académico", d.evento?.titulo || "", "Alumnos Registrados", d.registrados],
                ["Fecha y Hora", d.evento?.fecha || "", "Asistencias Confirmadas", d.asistieron],
                ["Sede / Lugar", d.evento?.lugar || "", "Tasa Efectiva de Asistencia", `${d.tasaAsistencia}%`],
                ["Estado del Evento", d.evento?.finalizado ? "Finalizado" : "Próximo", "Evidencias / Materiales Adjuntos", `${d.archivosEvidencia} / ${d.archivosMaterial}`]
            ];

        paresKPI.forEach((fila, idx) => {
            const rNum = 7 + idx;
            const row = ws1.getRow(rNum);
            row.height = 21;
            const bg = idx % 2 === 0 ? fillWhite : fillZebra;

            const cB = ws1.getCell(`B${rNum}`);
            const cC = ws1.getCell(`C${rNum}`);
            const cE = ws1.getCell(`E${rNum}`);
            const cF = ws1.getCell(`F${rNum}`);

            cB.value = fila[0];
            cC.value = fila[1];
            cE.value = fila[2];
            cF.value = fila[3];

            [cB, cE].forEach(c => {
                c.fill = bg;
                c.font = { name: "Calibri", size: 10, bold: true, color: { argb: "FF334155" } };
                c.alignment = { vertical: "middle", horizontal: "left" };
                c.border = borderThin;
            });
            [cC, cF].forEach(c => {
                c.fill = bg;
                c.font = { name: "Calibri", size: 11, bold: true, color: { argb: "FF122652" } };
                c.alignment = { vertical: "middle", horizontal: "center" };
                c.border = borderThin;
            });
        });

        // Sección 2: Incrustar las Gráficas de Chart.js directamente en la Hoja 1
        const filaTituloGraficas = 7 + paresKPI.length + 1;
        ws1.mergeCells(`B${filaTituloGraficas}:F${filaTituloGraficas}`);
        const sec2 = ws1.getCell(`B${filaTituloGraficas}`);
        sec2.value = "2. GRÁFICAS VISUALES DE PARTICIPACIÓN Y RENDIMIENTO";
        sec2.fill = fillNavyMid;
        sec2.font = { name: "Calibri", size: 11, bold: true, color: { argb: "FFFFFFFF" } };
        sec2.alignment = { vertical: "middle", horizontal: "left", indent: 1 };
        ws1.getRow(filaTituloGraficas).height = 22;

        const canvases = [...document.querySelectorAll("#stats-contenido canvas")];
        let filaBaseImg = filaTituloGraficas + 1;

        canvases.forEach((cv, idx) => {
            const titulo = cv.closest(".etda-card")?.querySelector("h3")?.innerText?.trim() || `Gráfica ${idx + 1}`;
            const dataUrl = renderizarCanvasConMarcoInstitucional(cv, titulo);
            const imgId = wb.addImage({
                base64: dataUrl,
                extension: "png"
            });

            // Colocar 2 gráficas por fila (Columna B y Columna E)
            const esColDerecha = idx % 2 === 1;
            const filaActual = filaBaseImg + Math.floor(idx / 2) * 15;

            ws1.addImage(imgId, {
                tl: { col: esColDerecha ? 4 : 1, row: filaActual },
                ext: { width: 430, height: 255 }
            });
        });

        // --------------------------------------------------------------------
        // HOJA 2: TABLAS DESGLOSADAS CON FORMATO ESTÉTICO
        // --------------------------------------------------------------------
        const ws2 = wb.addWorksheet("Tablas de Datos", {
            views: [{ showGridLines: true }]
        });

        ws2.columns = [
            { key: "m", width: 3 },
            { key: "c1", width: 42 },
            { key: "c2", width: 22 },
            { key: "c3", width: 24 }
        ];

        ws2.mergeCells("B2:D2");
        const h2Title = ws2.getCell("B2");
        h2Title.value = "DESGLOSE DE TABLAS ESTADÍSTICAS — CAMPUS CONNECT UNACH";
        h2Title.fill = fillNavy;
        h2Title.font = { name: "Calibri", size: 12, bold: true, color: { argb: "FFFFFFFF" } };
        h2Title.alignment = { vertical: "middle", horizontal: "center" };
        ws2.getRow(2).height = 26;

        let cursorFila = 4;

        function agregarTablaEstilizada(tituloSeccion, encabezados, filasDatos) {
            ws2.mergeCells(`B${cursorFila}:D${cursorFila}`);
            const cellSec = ws2.getCell(`B${cursorFila}`);
            cellSec.value = tituloSeccion;
            cellSec.fill = fillNavyMid;
            cellSec.font = { name: "Calibri", size: 11, bold: true, color: { argb: "FFFFFFFF" } };
            cellSec.alignment = { vertical: "middle", horizontal: "left", indent: 1 };
            ws2.getRow(cursorFila).height = 22;
            cursorFila++;

            const cols = ["B", "C", "D"];
            encabezados.forEach((textoHeader, i) => {
                const c = ws2.getCell(`${cols[i]}${cursorFila}`);
                c.value = textoHeader;
                c.fill = fillOrange;
                c.font = { name: "Calibri", size: 10, bold: true, color: { argb: "FFFFFFFF" } };
                c.alignment = { vertical: "middle", horizontal: i === 0 ? "left" : "center" };
                c.border = borderThin;
            });
            ws2.getRow(cursorFila).height = 20;
            cursorFila++;

            if (!filasDatos || filasDatos.length === 0) {
                ws2.mergeCells(`B${cursorFila}:D${cursorFila}`);
                const cVacio = ws2.getCell(`B${cursorFila}`);
                cVacio.value = "Sin registros disponibles en este rubro.";
                cVacio.font = { name: "Calibri", size: 10, italic: true, color: { argb: "FF64748B" } };
                cVacio.alignment = { vertical: "middle", horizontal: "center" };
                cVacio.border = borderThin;
                ws2.getRow(cursorFila).height = 20;
                cursorFila += 2;
                return;
            }

            filasDatos.forEach((arr, rIdx) => {
                const bg = rIdx % 2 === 0 ? fillWhite : fillZebra;
                arr.forEach((val, cIdx) => {
                    const c = ws2.getCell(`${cols[cIdx]}${cursorFila}`);
                    c.value = val;
                    c.fill = bg;
                    c.font = { name: "Calibri", size: 10, bold: cIdx === 0, color: { argb: "FF1E293B" } };
                    c.alignment = { vertical: "middle", horizontal: cIdx === 0 ? "left" : "center" };
                    c.border = borderThin;
                });
                ws2.getRow(cursorFila).height = 20;
                cursorFila++;
            });

            cursorFila += 2; // Espacio entre tablas
        }

        if (est.modo === "general") {
            agregarTablaEstilizada(
                "1. EVENTOS Y REGISTROS POR MES",
                ["Mes", "Eventos Realizados", "Alumnos Registrados"],
                (d.porMes || []).map(m => [m.mes, m.eventos, m.registros])
            );
            agregarTablaEstilizada(
                "2. DISTRIBUCIÓN DE EVENTOS POR CATEGORÍA",
                ["Categoría Académica", "Total de Eventos", "Porcentaje del Total"],
                (d.porCategoria || []).map(c => [
                    c.categoria,
                    c.eventos,
                    d.totalEventos ? `${Math.round((c.eventos / d.totalEventos) * 100)}%` : "0%"
                ])
            );
            agregarTablaEstilizada(
                "3. PARTICIPACIÓN POR PROGRAMA EDUCATIVO (CARRERA)",
                ["Programa Educativo / Carrera", "Alumnos Inscritos", "Asistencias Confirmadas"],
                (d.porCarrera || []).map(c => [c.carrera, c.registros, c.asistencias])
            );
            agregarTablaEstilizada(
                "4. EVENTOS CON MAYOR PARTICIPACIÓN ESTUDIANTIL",
                ["Título del Evento", "Alumnos Registrados", "Asistencias Confirmadas"],
                (d.topEventos || []).map(t => [t.titulo, t.registrados, t.asistieron])
            );
        } else {
            agregarTablaEstilizada(
                "1. PARTICIPACIÓN POR PROGRAMA EDUCATIVO (CARRERA)",
                ["Carrera", "Alumnos Registrados", "Asistencias Confirmadas"],
                (d.porCarrera || []).map(c => [c.carrera, c.registros, c.asistencias])
            );
            agregarTablaEstilizada(
                "2. PARTICIPACIÓN POR GRUPO ACADÉMICO",
                ["Grupo", "Alumnos Registrados", "Asistencias Confirmadas"],
                (d.porGrupo || []).map(g => [g.grupo, g.registros, g.asistencias])
            );
            agregarTablaEstilizada(
                "3. EVOLUCIÓN DE INSCRIPCIONES POR DÍA",
                ["Fecha de Inscripción", "Nuevos Registros", "Estado"],
                (d.inscripcionesPorDia || []).map(x => [x.fecha, x.registros, "Registrado"])
            );
        }

        const buffer = await wb.xlsx.writeBuffer();
        const blob = new Blob([buffer], {
            type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        });
        const url = URL.createObjectURL(blob);
        const sufijo = est.modo === "general" ? "General" : `Evento_${est.eventoId}`;
        const a = Object.assign(document.createElement("a"), {
            href: url,
            download: `Reporte_Estadisticas_UNACH_${sufijo}.xlsx`
        });
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 5000);
        toast("¡Excel (.xlsx) con formato institucional y gráficas descargado!", "exito");
    } catch (err) {
        toast("Error al construir el archivo Excel: " + err.message, "error");
    }
}

// 2. Descargar gráfica individual en PNG con marco institucional
function descargarGraficaPNG(canvasId, tituloGrafica = "grafica") {
    const canvasOrig = document.getElementById(canvasId);
    if (!canvasOrig) {
        toast("No se encontró la gráfica para descargar.", "alerta");
        return;
    }
    const url = renderizarCanvasConMarcoInstitucional(canvasOrig, tituloGrafica);
    const slug = tituloGrafica.toLowerCase().replace(/[^a-z0-9]+/g, "_");
    const a = Object.assign(document.createElement("a"), {
        href: url,
        download: `grafica_unach_${slug}.png`
    });
    document.body.appendChild(a);
    a.click();
    a.remove();
    toast("Gráfica descargada en formato PNG.", "exito");
}

// 3. Generar y descargar Reporte Institucional en PDF (con gráficas incrustadas y tablas)
function descargarReporteEstadisticasPDF() {
    const est = estadoEstadisticasActual;
    if (!est || !est.datos) {
        toast("Primero espera a que se carguen las estadísticas.", "alerta");
        return;
    }

    const d = est.datos;
    const imagenesGraficas = [];
    document.querySelectorAll("#stats-contenido canvas").forEach(cv => {
        const titulo = cv.closest(".etda-card")?.querySelector("h3")?.innerText || "Gráfica";
        try {
            imagenesGraficas.push({ titulo, dataUrl: cv.toDataURL("image/png") });
        } catch (_) {
            /* ignorar */
        }
    });

    const kpisHtml = est.modo === "general"
        ? `
            <div class="kpi-grid">
                <div class="kpi"><span>Eventos Totales</span><strong>${esc(d.totalEventos)}</strong></div>
                <div class="kpi"><span>Próximos / Finalizados</span><strong>${esc(d.proximos)} / ${esc(d.finalizados)}</strong></div>
                <div class="kpi"><span>Registros Totales</span><strong>${esc(d.totalRegistros)}</strong></div>
                <div class="kpi"><span>Tasa de Asistencia</span><strong>${esc(d.tasaAsistencia)}%</strong></div>
                <div class="kpi"><span>Promedio / Evento</span><strong>${esc(d.promedioRegistrosPorEvento)}</strong></div>
                <div class="kpi"><span>Generales / Segmentados</span><strong>${esc(d.eventosGenerales)} / ${esc(d.eventosSegmentados)}</strong></div>
                <div class="kpi"><span>Con Evidencia</span><strong>${esc(d.finalizadosConEvidencia)}</strong></div>
                <div class="kpi"><span>Sin Evidencia</span><strong>${esc(d.finalizadosSinEvidencia)}</strong></div>
            </div>`
        : `
            <div class="evento-banner">
                <h2>${esc(d.evento?.titulo)}</h2>
                <p>${esc(d.evento?.fecha)} · ${esc(d.evento?.lugar)} · ${d.evento?.finalizado ? "Finalizado" : "Próximo"}</p>
            </div>
            <div class="kpi-grid">
                <div class="kpi"><span>Alumnos Registrados</span><strong>${esc(d.registrados)}</strong></div>
                <div class="kpi"><span>Asistencias Confirmadas</span><strong>${esc(d.asistieron)}</strong></div>
                <div class="kpi"><span>Tasa de Asistencia</span><strong>${esc(d.tasaAsistencia)}%</strong></div>
                <div class="kpi"><span>Evidencias / Materiales</span><strong>${esc(d.archivosEvidencia)} / ${esc(d.archivosMaterial)}</strong></div>
            </div>`;

    const tablasHtml = est.modo === "general"
        ? `
            <div class="tables-row">
                <div class="table-box">
                    <h4>Participación por Programa Educativo (Carrera)</h4>
                    <table>
                        <thead><tr><th>Carrera</th><th>Registros</th><th>Asistencias</th></tr></thead>
                        <tbody>
                            ${(d.porCarrera || []).map(c => `<tr><td>${esc(c.carrera)}</td><td>${esc(c.registros)}</td><td>${esc(c.asistencias)}</td></tr>`).join("") || '<tr><td colspan="3">Sin registros</td></tr>'}
                        </tbody>
                    </table>
                </div>
                <div class="table-box">
                    <h4>Eventos con Mayor Participación</h4>
                    <table>
                        <thead><tr><th>Evento</th><th>Registrados</th><th>Asistieron</th></tr></thead>
                        <tbody>
                            ${(d.topEventos || []).map(t => `<tr><td>${esc(t.titulo)}</td><td>${esc(t.registrados)}</td><td>${esc(t.asistieron)}</td></tr>`).join("") || '<tr><td colspan="3">Sin registros</td></tr>'}
                        </tbody>
                    </table>
                </div>
            </div>`
        : `
            <div class="tables-row">
                <div class="table-box">
                    <h4>Desglose por Carrera</h4>
                    <table>
                        <thead><tr><th>Carrera</th><th>Registrados</th><th>Asistieron</th></tr></thead>
                        <tbody>
                            ${(d.porCarrera || []).map(c => `<tr><td>${esc(c.carrera)}</td><td>${esc(c.registros)}</td><td>${esc(c.asistencias)}</td></tr>`).join("") || '<tr><td colspan="3">Sin datos</td></tr>'}
                        </tbody>
                    </table>
                </div>
                <div class="table-box">
                    <h4>Desglose por Grupo</h4>
                    <table>
                        <thead><tr><th>Grupo</th><th>Registrados</th><th>Asistieron</th></tr></thead>
                        <tbody>
                            ${(d.porGrupo || []).map(g => `<tr><td>${esc(g.grupo)}</td><td>${esc(g.registros)}</td><td>${esc(g.asistencias)}</td></tr>`).join("") || '<tr><td colspan="3">Sin datos</td></tr>'}
                        </tbody>
                    </table>
                </div>
            </div>`;

    const ventana = window.open("", "_blank", "width=980,height=800");
    if (!ventana) {
        toast("Permite las ventanas emergentes en tu navegador para generar el reporte PDF.", "alerta");
        return;
    }

    ventana.document.write(`<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <title>Reporte Institucional de Estadísticas - Campus Connect UNACH</title>
    <style>
        body { font-family: 'Segoe UI', Roboto, Arial, sans-serif; color: #122652; margin: 0; padding: 28px; background: #fff; }
        .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 3px solid #F57C00; padding-bottom: 14px; margin-bottom: 20px; }
        .header h1 { margin: 0; font-size: 20px; color: #122652; }
        .header p { margin: 3px 0 0; font-size: 12px; color: #5B6670; }
        .badge { background: #122652; color: #fff; font-size: 11px; font-weight: bold; padding: 6px 12px; border-radius: 6px; text-align: right; }
        .evento-banner { background: #F4F7FB; border-left: 4px solid #0093FF; padding: 12px 16px; border-radius: 8px; margin-bottom: 16px; }
        .evento-banner h2 { margin: 0; font-size: 16px; }
        .evento-banner p { margin: 4px 0 0; font-size: 12px; color: #5B6670; }
        .kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 22px; }
        .kpi { border: 1px solid #CBD5E1; border-radius: 8px; padding: 10px 12px; background: #F8FAFC; }
        .kpi span { display: block; font-size: 11px; color: #5B6670; }
        .kpi strong { font-size: 18px; color: #122652; }
        .charts-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 22px; }
        .chart-card { border: 1px solid #E2E8F0; border-radius: 8px; padding: 12px; page-break-inside: avoid; }
        .chart-card h4 { margin: 0 0 8px; font-size: 13px; color: #122652; }
        .chart-card img { width: 100%; height: auto; max-height: 220px; object-fit: contain; }
        .tables-row { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
        .table-box { page-break-inside: avoid; }
        .table-box h4 { margin: 0 0 8px; font-size: 13px; color: #122652; }
        table { width: 100%; border-collapse: collapse; font-size: 11px; }
        th, td { border: 1px solid #CBD5E1; padding: 6px 8px; text-align: left; }
        th { background: #122652; color: #fff; }
        .footer { margin-top: 28px; padding-top: 12px; border-top: 1px solid #CBD5E1; font-size: 10px; color: #5B6670; display: flex; justify-content: space-between; }
    </style>
</head>
<body>
    <div class="header">
        <div>
            <h1>UNIVERSIDAD AUTÓNOMA DE CHIAPAS • ETDA</h1>
            <p>Campus Connect UNACH — Reporte Oficial de Estadísticas de Eventos Académicos</p>
        </div>
        <div class="badge">
            Emisión: ${esc(est.fechaConsulta)}<br>
            Usuario: ${esc(userState.email || "Coordinación")}
        </div>
    </div>
    ${kpisHtml}
    <div class="charts-grid">
        ${imagenesGraficas.map(g => `
            <div class="chart-card">
                <h4>${esc(g.titulo)}</h4>
                <img src="${g.dataUrl}" alt="${esc(g.titulo)}">
            </div>
        `).join("")}
    </div>
    ${tablasHtml}
    <div class="footer">
        <span>“Por la conciencia de la necesidad de servir” — UNACH · Campus I, Tuxtla Gutiérrez</span>
        <span>Documento generado por Campus Connect ETDA</span>
    </div>
    <script>
        window.onload = function() { setTimeout(function() { window.print(); }, 350); };
    <\/script>
</body>
</html>`);
    ventana.document.close();
}

// 4. Descargar datos crudos en JSON
function descargarEstadisticasJSON() {
    const est = estadoEstadisticasActual;
    if (!est || !est.datos) {
        toast("Primero espera a que se carguen las estadísticas.", "alerta");
        return;
    }
    const blob = new Blob([JSON.stringify(est, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const sufijo = est.modo === "general" ? "general" : `evento_${est.eventoId}`;
    const a = Object.assign(document.createElement("a"), {
        href: url,
        download: `estadisticas_unach_${sufijo}.json`
    });
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
    toast("Datos estadísticos descargados en JSON.", "exito");
}
