// ============================================================================
// MÓDULO DE SEGURIDAD Y BITÁCORA DE AUDITORÍA DEL SISTEMA
// ============================================================================

let auditoriaLista = [];

async function cargarAuditoriaAdmin() {
    if (!tienePermiso("seguridad.auditoria")) return;
    const tbody = document.getElementById("tabla-auditoria-body");
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="5" class="p-8 text-center text-slate-400"><i class="fa-solid fa-spinner fa-spin mr-2 text-[#F57C00]"></i> Cargando registros de seguridad y auditoría...</td></tr>`;

    try {
        const registros = await api("/admin/auditoria");
        auditoriaLista = registros || [];
        renderizarTablaAuditoria();
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="5" class="p-6 text-center text-red-500 font-semibold">${esc(err.message || "Error al cargar el registro de auditoría.")}</td></tr>`;
    }
}

function renderizarTablaAuditoria() {
    const tbody = document.getElementById("tabla-auditoria-body");
    if (!tbody) return;
    const busqueda = (document.getElementById("buscar-auditoria-input")?.value || "").trim().toLowerCase();

    const filtrados = auditoriaLista.filter(r => {
        if (!busqueda) return true;
        const txt = `${r.usuario_email || ""} ${r.direccion_ip || ""} ${r.accion || ""}`.toLowerCase();
        return txt.includes(busqueda);
    });

    if (filtrados.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="p-6 text-center text-slate-500">No hay registros de auditoría que coincidan con el filtro.</td></tr>`;
        return;
    }

    tbody.innerHTML = filtrados.map(r => `
        <tr class="border-b border-slate-100 hover:bg-slate-50 transition">
            <td class="p-3.5 text-slate-500 font-mono text-xs">${esc(new Date(r.fecha_hora).toLocaleString("es-MX"))}</td>
            <td class="p-3.5 font-bold text-[#122652]">${esc(r.usuario_email || "Anónimo")}</td>
            <td class="p-3.5 font-mono text-xs text-[#0093FF]">${esc(r.direccion_ip)}</td>
            <td class="p-3.5 font-semibold text-slate-700">${esc(r.accion)}</td>
            <td class="p-3.5">
                <span class="px-2.5 py-0.5 rounded-full text-[10px] font-extrabold ${
                    r.exitoso
                        ? "bg-emerald-100 text-emerald-800 border border-emerald-200"
                        : "bg-red-100 text-red-800 border border-red-200"
                }">
                    ${r.exitoso ? "ÉXITO" : "FALLO"}
                </span>
            </td>
        </tr>
    `).join("");
}

function descargarAuditoriaCSV() {
    if (!auditoriaLista.length) {
        toast("No hay registros de auditoría cargados para exportar.", "alerta");
        return;
    }
    const filas = [
        ["Fecha y Hora", "Usuario", "Dirección IP", "Acción", "Resultado"],
        ...auditoriaLista.map(r => [
            new Date(r.fecha_hora).toLocaleString("es-MX"),
            r.usuario_email || "Anónimo",
            r.direccion_ip || "",
            r.accion || "",
            r.exitoso ? "ÉXITO" : "FALLO"
        ])
    ];
    const csv = "\uFEFF" + filas.map(f => f.map(escaparCeldaCSV).join(",")).join("\r\n");
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8;" }));
    const a = Object.assign(document.createElement("a"), { href: url, download: "bitacora_seguridad_unach.csv" });
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
    toast("Bitácora de auditoría exportada en CSV.", "exito");
}
