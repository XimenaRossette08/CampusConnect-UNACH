// ============================================================================
// MÓDULO DE CARTELERA DE EVENTOS, FILTROS ETDA, CUPO EN VIVO Y LISTA DE ASISTENCIA
// ============================================================================

let eventosCache = {};
let eventosLista = [];
let filtroCategoriaEvento = "Todos";
let busquedaEventoTexto = "";

const STORAGE_HORARIOS_POR_EVENTO = "cc_vigencia_por_evento_v3";
const STORAGE_MAPA_ASIENTOS_WEB = "cc_mapa_asientos_v2";
let _eventosExpiradosNotificadosWeb = new Set();
let _relojVigenciaWebIniciado = false;

function _extraerMetaDescripcion(evOrDesc) {
    const raw = typeof evOrDesc === "string" ? evOrDesc : (evOrDesc?.descripcion || "");
    if (!raw) return {};
    const m = raw.match(/<!--CCMETA:([\s\S]*?)-->/);
    if (!m || !m[1]) return {};
    try {
        return JSON.parse(m[1].trim()) || {};
    } catch (_) {
        return {};
    }
}

function _limpiarDescripcionVisible(desc) {
    return String(desc || "").replace(/\s*<!--CCMETA:[\s\S]*?-->/g, "").trim();
}

function _obtenerEmailUsuarioWeb() {
    const em = (typeof userState !== "undefined" && userState?.email)
        || (typeof usuarioActual !== "undefined" && usuarioActual?.email)
        || localStorage.getItem("user_email")
        || "";
    return String(em).trim().toLowerCase();
}

function _obtenerNombreUsuarioWeb() {
    return (typeof userState !== "undefined" && userState?.nombre)
        || (typeof usuarioActual !== "undefined" && usuarioActual?.nombre)
        || localStorage.getItem("user_nombre")
        || _obtenerEmailUsuarioWeb()
        || "Usuario UNACH";
}

function _construirDescripcionConMeta(descVisible, metaObj) {
    const base = _limpiarDescripcionVisible(descVisible);
    const limpioMeta = {};
    if (metaObj && typeof metaObj === "object") {
        if (metaObj.disp) limpioMeta.disp = metaObj.disp;
        if (metaObj.fin) limpioMeta.fin = metaObj.fin;
        if (metaObj.cap) limpioMeta.cap = Number(metaObj.cap) || 100;
        if (typeof metaObj.ocup === "number") limpioMeta.ocup = Math.max(0, metaObj.ocup);
        if (metaObj.res && typeof metaObj.res === "object") limpioMeta.res = metaObj.res;
        if (metaObj.seats && typeof metaObj.seats === "object") limpioMeta.seats = metaObj.seats;
        if (metaObj.liberados && typeof metaObj.liberados === "object" && Object.keys(metaObj.liberados).length) {
            limpioMeta.liberados = metaObj.liberados;
        }
        if (Array.isArray(metaObj.malla) && metaObj.malla.length >= 3) {
            limpioMeta.malla = metaObj.malla;
        }
        if (typeof metaObj.tol === "number" && Number.isFinite(metaObj.tol)) {
            limpioMeta.tol = metaObj.tol;
        }
    }
    if (!Object.keys(limpioMeta).length) return base;
    return `${base}\n<!--CCMETA:${JSON.stringify(limpioMeta)}-->`;
}

function _leerEstadoAsientosLocal(eventoId) {
    if (!eventoId) return { res: {}, seats: {}, liberados: {}, inicializado: false };
    try {
        const todo = JSON.parse(localStorage.getItem(STORAGE_MAPA_ASIENTOS_WEB) || "{}") || {};
        const item = todo[String(eventoId)];
        if (!item) return { res: {}, seats: {}, liberados: {}, inicializado: false };
        return {
            res: item.res && typeof item.res === "object" ? item.res : {},
            seats: item.seats && typeof item.seats === "object" ? item.seats : {},
            liberados: item.liberados && typeof item.liberados === "object" ? item.liberados : {},
            inicializado: true
        };
    } catch (_) {
        return { res: {}, seats: {}, liberados: {}, inicializado: false };
    }
}

function _guardarEstadoAsientosLocal(eventoId, resMap, seatsMap, liberadosMap = null) {
    if (!eventoId) return;
    try {
        const todo = JSON.parse(localStorage.getItem(STORAGE_MAPA_ASIENTOS_WEB) || "{}") || {};
        const prev = todo[String(eventoId)] || {};
        todo[String(eventoId)] = {
            res: resMap || {},
            seats: seatsMap || {},
            liberados: liberadosMap || prev.liberados || {},
            inicializado: true
        };
        localStorage.setItem(STORAGE_MAPA_ASIENTOS_WEB, JSON.stringify(todo));
    } catch (_) {}
}

function _sincronizarEstadoAsientosConMetaEvento(ev) {
    if (!ev || !ev.id) return { res: {}, seats: {}, liberados: {} };
    const meta = _extraerMetaDescripcion(ev);
    const localState = _leerEstadoAsientosLocal(ev.id);
    const liberados = { ...(meta.liberados || {}), ...(localState.liberados || {}) };

    const resBase = localState.inicializado ? { ...(meta.res || {}), ...(localState.res || {}) } : { ...(meta.res || {}) };
    const seatsBase = {};

    // 1. Cargar primero asientos locales que NO sean auto-asignados por defecto
    Object.entries(localState.seats || {}).forEach(([em, val]) => {
        const k = em.trim().toLowerCase();
        if (val && typeof val === "object" && val.autoAsignado) return;
        seatsBase[k] = val;
    });

    // 2. Superponer SIEMPRE meta.seats (proveniente del servidor / App móvil) cuando sea más reciente o explícito
    Object.entries(meta.seats || {}).forEach(([em, valMeta]) => {
        const k = em.trim().toLowerCase();
        const prevLocal = seatsBase[k];
        const tsMeta = Number(valMeta?.ts || (valMeta?.fechaApartado ? new Date(valMeta.fechaApartado).getTime() : 0) || 0);
        const tsLocal = Number(prevLocal?.ts || (prevLocal?.fechaApartado ? new Date(prevLocal.fechaApartado).getTime() : 0) || 0);
        if (!prevLocal || tsMeta >= tsLocal || prevLocal.autoAsignado) {
            seatsBase[k] = valMeta;
        }
    });

    // 3. Eliminar de resBase y seatsBase cualquier asiento o correo que tenga marca de liberación posterior
    Object.keys(resBase).forEach(cod => {
        const tsLib = Number(liberados[cod] || 0);
        const tsCreacion = resBase[cod]?.fecha ? new Date(resBase[cod].fecha).getTime() : 0;
        if (tsLib > 0 && (!tsCreacion || tsLib >= tsCreacion)) {
            delete resBase[cod];
        }
    });

    Object.keys(seatsBase).forEach(em => {
        const item = seatsBase[em];
        const cod = typeof item === "string" ? item : item?.asiento;
        const tsLibEm = Number(liberados[em.toLowerCase()] || 0);
        const tsLibCod = cod ? Number(liberados[cod] || 0) : 0;
        const tsLib = Math.max(tsLibEm, tsLibCod);
        const tsApartado = Number(item?.ts || (item?.fechaApartado ? new Date(item.fechaApartado).getTime() : 0) || 0);
        if (tsLib > 0 && (!tsApartado || tsLib > tsApartado)) {
            delete seatsBase[em];
        }
    });

    return { res: resBase, seats: seatsBase, liberados };
}

function _leerMapaHorariosPorEvento() {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_HORARIOS_POR_EVENTO) || "{}") || {};
    } catch (_) {
        return {};
    }
}

function _guardarHorarioIndividualEvento(eventoId, disponibleDesdeIso, fechaFinIso) {
    if (!eventoId) return;
    try {
        const mapa = _leerMapaHorariosPorEvento();
        mapa[String(eventoId)] = {
            disponibleDesde: disponibleDesdeIso || null,
            fechaFin: fechaFinIso || null
        };
        localStorage.setItem(STORAGE_HORARIOS_POR_EVENTO, JSON.stringify(mapa));
    } catch (_) {}
}

function _obtenerHorarioIndividualEvento(ev) {
    if (!ev || !ev.id) return { disponibleDesde: null, fechaFin: null };
    const mapa = _leerMapaHorariosPorEvento();
    const local = mapa[String(ev.id)] || {};
    const meta = _extraerMetaDescripcion(ev);

    const disp = (ev.disponibleDesde !== undefined && ev.disponibleDesde !== null)
        ? ev.disponibleDesde
        : ((ev.disponible_desde !== undefined && ev.disponible_desde !== null)
            ? ev.disponible_desde
            : (meta.disp || local.disponibleDesde || null));

    let fin = (ev.fechaFin !== undefined && ev.fechaFin !== null)
        ? ev.fechaFin
        : ((ev.fecha_fin !== undefined && ev.fecha_fin !== null)
            ? ev.fecha_fin
            : (meta.fin || local.fechaFin || null));

    if (!fin && ev.fechaHora) {
        const tIni = new Date(ev.fechaHora).getTime();
        if (!Number.isNaN(tIni)) {
            fin = new Date(tIni + 4 * 3600 * 1000).toISOString();
        }
    }
    return { disponibleDesde: disp, fechaFin: fin };
}

function _formatearFechaInputLocal(isoStr) {
    if (!isoStr) return "";
    const d = new Date(isoStr);
    if (Number.isNaN(d.getTime())) return "";
    const pad = n => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function autocompletarFechaFinEvento() {
    const inpInicio = document.getElementById("ev-fecha-hora");
    const inpFin = document.getElementById("ev-fecha-fin");
    if (!inpInicio || !inpFin || !inpInicio.value) return;
    const dInicio = new Date(inpInicio.value);
    if (Number.isNaN(dInicio.getTime())) return;
    if (!inpFin.value || new Date(inpFin.value) <= dInicio) {
        const dFin = new Date(dInicio.getTime() + 2 * 60 * 60 * 1000);
        inpFin.value = _formatearFechaInputLocal(dFin.toISOString());
    }
}

function _sincronizarInputsFechasModal(ev) {
    const inpInicio = document.getElementById("ev-fecha-hora");
    const inpFin = document.getElementById("ev-fecha-fin");
    const inpDisp = document.getElementById("ev-disponible-desde");

    if (!ev) {
        if (inpInicio) inpInicio.value = "";
        if (inpFin) inpFin.value = "";
        if (inpDisp) inpDisp.value = "";
        return;
    }

    const { disponibleDesde, fechaFin } = _obtenerHorarioIndividualEvento(ev);
    if (inpInicio) inpInicio.value = _formatearFechaInputLocal(ev.fechaHora);
    if (inpFin) inpFin.value = _formatearFechaInputLocal(fechaFin);
    if (inpDisp) inpDisp.value = _formatearFechaInputLocal(disponibleDesde);
}

function _eventoDisponibleAhoraWeb(ev) {
    if (!ev) return false;
    const { disponibleDesde } = _obtenerHorarioIndividualEvento(ev);
    if (!disponibleDesde) return true;
    const t = new Date(disponibleDesde).getTime();
    return Number.isNaN(t) ? true : Date.now() >= t;
}

function _eventoExpiradoAhoraWeb(ev) {
    if (!ev) return false;
    if (ev.finalizado === true) return true;
    const { fechaFin } = _obtenerHorarioIndividualEvento(ev);
    if (fechaFin) {
        const tFin = new Date(fechaFin).getTime();
        if (!Number.isNaN(tFin)) return Date.now() >= tFin;
    }
    return false;
}

function _calcularEstadoAsientosEvento(ev) {
    if (!ev) return { capacidad: 100, ocupados: 0, disponibles: 100, porcentaje: 0, agotado: false };
    const meta = _extraerMetaDescripcion(ev);
    const syncState = _sincronizarEstadoAsientosConMetaEvento(ev);
    const infoAud = typeof buscarAuditorioPorNombre === "function" ? buscarAuditorioPorNombre(ev.lugar) : null;
    const capacidad = Number(infoAud?.asientos ?? ev.capacidadTotal ?? ev.capacidad_total ?? meta.cap ?? 100) || 100;

    const codigosUnicos = new Set([
        ...Object.keys(syncState.res || {}),
        ...Object.values(syncState.seats || {}).map(x => typeof x === "string" ? x : x?.asiento).filter(Boolean)
    ]);
    if (ev.miAsiento && !syncState.liberados[ev.miAsiento]) codigosUnicos.add(ev.miAsiento);

    const tieneControlSincronizado = Object.keys(syncState.liberados || {}).length > 0 || Object.keys(syncState.res || {}).length > 0 || Object.keys(syncState.seats || {}).length > 0;
    const ocupadosDirecto = Number(ev.asientosOcupados ?? ev.asientos_ocupados ?? ev.totalRegistrados ?? ev.total_registrados ?? meta.ocup ?? 0) || 0;
    const ocupados = tieneControlSincronizado
        ? Math.min(capacidad, codigosUnicos.size)
        : Math.min(capacidad, Math.max(0, ocupadosDirecto, codigosUnicos.size));
    const disponibles = Math.max(0, capacidad - ocupados);
    const porcentaje = capacidad > 0 ? Math.min(100, Math.round((ocupados / capacidad) * 100)) : 0;
    const agotado = disponibles <= 0;
    return { capacidad, ocupados, disponibles, porcentaje, agotado };
}

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

async function _enriquecerOcupacionEventosWeb(lista) {
    const emailYo = _obtenerEmailUsuarioWeb();
    const puedeVerAsistentes = typeof tienePermiso === "function" && (tienePermiso("asistencia.gestionar") || tienePermiso("eventos.publicar"));

    await Promise.all((lista || []).map(async (ev) => {
        if (!ev || !ev.id) return;
        const syncState = _sincronizarEstadoAsientosConMetaEvento(ev);
        const resCombinado = { ...syncState.res };
        const seatsCombinado = { ...syncState.seats };
        const liberadosCombinado = { ...syncState.liberados };

        if (emailYo && seatsCombinado[emailYo]) {
            const miCod = typeof seatsCombinado[emailYo] === "string" ? seatsCombinado[emailYo] : seatsCombinado[emailYo]?.asiento;
            if (miCod) {
                ev.miAsiento = miCod;
                ev.inscrito = true;
            }
        } else if (emailYo && liberadosCombinado[emailYo]) {
            ev.miAsiento = null;
            ev.inscrito = false;
        }

        if (puedeVerAsistentes && !_eventoExpiradoAhoraWeb(ev)) {
            try {
                const asis = await api(`/eventos/${ev.id}/asistentes`);
                if (Array.isArray(asis)) {
                    const usados = new Set(Object.keys(resCombinado));
                    // Primero reservar todos los códigos explícitos que ya vienen en seatsCombinado (ej. K-04 desde la App)
                    Object.values(seatsCombinado).forEach(val => {
                        const c = typeof val === "string" ? val : val?.asiento;
                        if (c) usados.add(c);
                    });

                    asis.forEach((a, idx) => {
                        const em = (a.email || "").toLowerCase();
                        const tsLibEm = Number(liberadosCombinado[em] || 0);
                        const tsReg = a.fecha_registro ? new Date(a.fecha_registro).getTime() : 0;
                        // Si el administrador liberó a este usuario y no hay un nuevo registro posterior, omitirlo
                        if (tsLibEm > 0 && (!tsReg || tsLibEm >= tsReg)) {
                            return;
                        }

                        const prevInfo = seatsCombinado[em];
                        const codExplicitoMeta = (prevInfo && !prevInfo.autoAsignado)
                            ? (typeof prevInfo === "string" ? prevInfo : prevInfo?.asiento)
                            : null;
                        const codServidor = (a.asiento && a.asiento !== "—") ? a.asiento : null;
                        let codFinal = codExplicitoMeta || codServidor;
                        let fueAutoAsignado = false;
                        if (!codFinal) {
                            fueAutoAsignado = true;
                            let cIdx = idx;
                            while (cIdx < 500) {
                                const cand = generarCodigoAsientoCine(cIdx, 10);
                                if (!usados.has(cand) && !liberadosCombinado[cand]) { codFinal = cand; break; }
                                cIdx++;
                            }
                        }
                        if (codFinal) {
                            usados.add(codFinal);
                            if (em) {
                                seatsCombinado[em] = {
                                    asiento: codFinal,
                                    autoAsignado: fueAutoAsignado,
                                    ts: prevInfo?.ts || (a.fecha_registro ? new Date(a.fecha_registro).getTime() : 0),
                                    asistenciaId: a.id,
                                    nombre: a.nombre || prevInfo?.nombre,
                                    email: a.email || em,
                                    carrera: a.carrera || prevInfo?.carrera,
                                    grupos: a.grupos || prevInfo?.grupos,
                                    asistio: Boolean(a.asistio || prevInfo?.asistio),
                                    qrValidado: Boolean(a.qr_validado || a.asistio || prevInfo?.qrValidado),
                                    gpsValidado: Boolean(a.gps_validado || a.asistio || prevInfo?.gpsValidado),
                                    fechaApartado: prevInfo?.fechaApartado || a.fecha_registro
                                };
                            }
                            if (emailYo && em === emailYo) {
                                ev.miAsiento = codFinal;
                                ev.inscrito = true;
                            }
                        }
                    });
                    _guardarEstadoAsientosLocal(ev.id, resCombinado, seatsCombinado, liberadosCombinado);
                    ev.asientosOcupados = usados.size;
                    ev.totalRegistrados = usados.size;
                }
            } catch (_) {}
        }
    }));
}

function _iniciarRelojVigenciaYPollingWeb() {
    if (_relojVigenciaWebIniciado) return;
    _relojVigenciaWebIniciado = true;

    // 1. Reloj cada 2 segundos: retira automáticamente de la cartelera cualquier evento cuya fechaFin haya vencido
    setInterval(() => {
        if (!Array.isArray(eventosLista) || !eventosLista.length) return;
        let huboCambioVigencia = false;
        for (const ev of eventosLista) {
            if (!ev || !ev.id) continue;
            const yaExpiro = _eventoExpiradoAhoraWeb(ev);
            if (yaExpiro && !_eventosExpiradosNotificadosWeb.has(ev.id)) {
                _eventosExpiradosNotificadosWeb.add(ev.id);
                huboCambioVigencia = true;
                toast(`✅ El evento "${ev.titulo}" ha finalizado y se retiró de la cartelera activa.`, "info");
            }
        }
        if (huboCambioVigencia) {
            renderizarTarjetasEventos();
            if (typeof cargarPasados === "function") {
                try { cargarPasados(); } catch (_) {}
            }
        }
    }, 2000);

    // 2. Sincronización silenciosa cada 5 segundos con el servidor
    setInterval(async () => {
        if (typeof userState !== "undefined" && !userState.isAuthenticated) return;
        try {
            const esGestor = typeof tienePermiso === "function" && tienePermiso("eventos.publicar");
            const endpoint = esGestor ? "/eventos/feed?incluir_programados=true" : "/eventos/feed";
            const todos = await api(endpoint);
            if (Array.isArray(todos)) {
                await _enriquecerOcupacionEventosWeb(todos);
                eventosLista = todos;
                eventosLista.forEach(e => { eventosCache[e.id] = e; });
                renderizarTarjetasEventos();
            }
        } catch (_) {}
    }, 8000);
}

async function cargarEventos() {
    _iniciarRelojVigenciaYPollingWeb();
    const container = document.getElementById("eventos-container");
    if (!container) return;
    inicializarFiltrosCategorias();
    container.innerHTML = `
        <div class="col-span-full bg-white rounded-2xl border p-10 text-center text-slate-400">
            <i class="fa-solid fa-circle-notch fa-spin text-2xl text-[#F57C00] mb-2"></i>
            <p class="text-sm font-medium text-[#122652]">Sincronizando cartelera institucional UNACH...</p>
        </div>`;

    try {
        const esGestor = typeof tienePermiso === "function" && tienePermiso("eventos.publicar");
        const endpoint = esGestor ? "/eventos/feed?incluir_programados=true" : "/eventos/feed";
        const todos = await api(endpoint);
        eventosLista = todos || [];
        await _enriquecerOcupacionEventosWeb(eventosLista);
        eventosLista.forEach(e => {
            if (_eventoExpiradoAhoraWeb(e)) _eventosExpiradosNotificadosWeb.add(e.id);
            eventosCache[e.id] = e;
        });
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

    const esGestor = typeof tienePermiso === "function" && tienePermiso("eventos.publicar");
    const activos = eventosLista.filter(e => {
        if (_eventoExpiradoAhoraWeb(e)) return false;
        if (!esGestor && !_eventoDisponibleAhoraWeb(e)) return false;
        return true;
    });
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

    const pCompartir = tienePermiso("redes.compartir");
    const pEditar = tienePermiso("eventos.publicar");
    const pEliminar = tienePermiso("eventos.eliminar");
    const pAsistentes = tienePermiso("asistencia.gestionar");

    container.innerHTML = filtrados.map(ev => {
        const f = extraerFechaVisualWeb(ev.fechaHora);
        const catInfo = detectarCategoriaEventoWeb(ev);
        const materiales = (ev.archivos || []).filter(a => a.tipo === "material");
        const cupo = _calcularEstadoAsientosEvento(ev);
        const estaInscrito = Boolean(ev.inscrito);
        const colorBarra = cupo.agotado ? "#DC2626" : (cupo.porcentaje >= 85 ? "#F57C00" : "#0093FF");

        return `
            <article class="etda-card overflow-hidden flex flex-col justify-between ${estaInscrito ? 'ring-2 ring-[#10B981]/50' : ''}">
                <div>
                    <!-- Flyer o cabecera con insignia de fecha estilo EventosScreen.kt -->
                    <div class="relative">
                        ${ev.imagenUrl ? `
                            <div class="w-full h-44 overflow-hidden bg-slate-100 border-b border-slate-100">
                                <img src="${esc(ev.imagenUrl)}" alt="${esc(ev.titulo)}" class="w-full h-full object-cover transition-transform duration-300 hover:scale-105">
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
                        ${(pCompartir || pEditar || pEliminar || pAsistentes) ? `
                            <div class="absolute top-3 right-3 flex items-center gap-1.5 bg-white/95 backdrop-blur-sm p-1 rounded-xl shadow border border-slate-200">
                                <button onclick="abrirModalQrEvento('${esc(ev.id)}')" class="w-8 h-8 flex items-center justify-center rounded-lg text-[#122652] hover:bg-sky-50 transition" title="Ver Código QR Oficial del Evento" aria-label="Código QR del Evento">
                                    <i class="fa-solid fa-qrcode text-xs text-[#0093FF]"></i>
                                </button>
                                ${pCompartir ? `
                                    <button onclick="abrirSocial('${esc(ev.id)}')" class="w-8 h-8 flex items-center justify-center rounded-lg text-[#F57C00] hover:bg-amber-50 transition" title="Compartir en Redes Sociales" aria-label="Compartir en Redes Sociales">
                                        <i class="fa-solid fa-share-nodes text-xs"></i>
                                    </button>` : ''}
                                ${pEditar ? `
                                    <button onclick="abrirModalEvento('${esc(ev.id)}')" class="w-8 h-8 flex items-center justify-center rounded-lg text-[#122652] hover:bg-slate-100 transition" title="Editar Evento" aria-label="Editar Evento">
                                        <i class="fa-solid fa-pen-to-square text-xs"></i>
                                    </button>` : ''}
                                ${pEliminar ? `
                                    <button onclick="eliminarEvento('${esc(ev.id)}')" class="w-8 h-8 flex items-center justify-center rounded-lg text-red-600 hover:bg-red-50 transition" title="Eliminar Evento" aria-label="Eliminar Evento">
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
                        ${(!_eventoDisponibleAhoraWeb(ev) && _obtenerHorarioIndividualEvento(ev).disponibleDesde) ? `
                            <div class="mb-2 px-2.5 py-1 rounded-lg bg-amber-50 border border-amber-300 text-amber-800 text-[11px] font-bold flex items-center gap-1.5">
                                <i class="fa-regular fa-clock"></i>
                                <span>Programado · Sale en App el ${esc(new Date(_obtenerHorarioIndividualEvento(ev).disponibleDesde).toLocaleString("es-MX", { dateStyle: "short", timeStyle: "short" }))}</span>
                            </div>` : ''}

                        <h3 class="text-base font-extrabold text-[#122652] leading-snug">${esc(ev.titulo)}</h3>
                        <p class="text-xs text-[#5B6670] mt-2 line-clamp-3 leading-relaxed">${esc(_limpiarDescripcionVisible(ev.descripcion))}</p>

                        <!-- Barra de Ocupación de Asientos en Tiempo Real -->
                        <div class="mt-4 pt-3 border-t border-slate-100">
                            <div class="flex items-center justify-between text-[11px] mb-1.5">
                                <span class="font-bold text-[#122652] flex items-center gap-1.5">
                                    <i class="fa-solid fa-chair text-[#F57C00]"></i>
                                    <span>Ocupación del recinto</span>
                                </span>
                                <span class="font-extrabold ${cupo.agotado ? 'text-red-600' : 'text-[#0093FF]'}">
                                    ${cupo.agotado ? `Cupo lleno (${cupo.ocupados}/${cupo.capacidad})` : `${cupo.disponibles} disponibles (${cupo.ocupados}/${cupo.capacidad})`}
                                </span>
                            </div>
                            <div class="etda-seat-track" role="progressbar" aria-valuenow="${cupo.ocupados}" aria-valuemin="0" aria-valuemax="${cupo.capacidad}">
                                <div class="etda-seat-fill" style="width: ${cupo.porcentaje}%; background-color: ${colorBarra};"></div>
                            </div>
                        </div>

                        ${materiales.length ? `
                            <div class="mt-3 pt-2.5 border-t border-slate-100 flex items-center gap-2 text-[11px] text-[#0093FF] font-semibold">
                                <i class="fa-solid fa-paperclip"></i>
                                <span>${materiales.length} material(es) adjunto(s) para la app móvil</span>
                            </div>` : ''}
                    </div>
                </div>

                <div class="px-5 py-3.5 bg-[#F4F7FB] border-t border-slate-200/80 space-y-3">
                    <div class="space-y-1 text-xs text-[#5B6670] font-medium">
                        <div class="flex justify-between items-center gap-2">
                            <span class="truncate"><i class="fa-regular fa-clock text-[#0093FF] mr-1"></i> <b>Inicio:</b> ${esc(f.completo)}</span>
                            <span class="truncate text-right"><i class="fa-solid fa-location-dot text-[#F57C00] mr-1"></i> ${esc(ev.lugar || 'Campus I')}</span>
                        </div>
                        ${_obtenerHorarioIndividualEvento(ev).fechaFin ? `
                        <div class="text-[11px] text-slate-500">
                            <i class="fa-solid fa-flag-checkered text-[#F57C00] mr-1"></i> <b>Finaliza:</b> ${esc(new Date(_obtenerHorarioIndividualEvento(ev).fechaFin).toLocaleString("es-MX", { dateStyle: "medium", timeStyle: "short" }))}
                        </div>` : ""}
                    </div>

                    <div class="flex flex-wrap gap-2">
                        <button onclick="abrirModalQrEvento('${esc(ev.id)}')"
                            class="bg-white hover:bg-sky-50 text-[#122652] border border-slate-300 text-xs font-extrabold px-3 py-2 rounded-xl transition flex items-center justify-center gap-1.5"
                            title="Mostrar Código QR de Asistencia">
                            <i class="fa-solid fa-qrcode text-[#0093FF]"></i> QR
                        </button>
                        <button onclick="abrirModalMapaAsientos('${esc(ev.id)}')"
                            class="bg-white hover:bg-amber-50 text-[#122652] border border-slate-300 text-xs font-extrabold px-3 py-2 rounded-xl transition flex items-center justify-center gap-1.5"
                            title="Ver mapa de asientos en tiempo real y quién apartó cada lugar">
                            <i class="fa-solid fa-couch text-[#F57C00]"></i> Sala
                        </button>
                        ${estaInscrito ? `
                            <button onclick="abrirModalMapaAsientos('${esc(ev.id)}')"
                                class="flex-1 bg-[#10B981] hover:bg-emerald-600 text-white text-xs font-extrabold py-2 px-3 rounded-xl shadow-sm transition flex items-center justify-center gap-1.5"
                                title="Ver tu asiento apartado o cancelar">
                                <i class="fa-solid fa-ticket"></i> ${ev.miAsiento ? `Asiento ${esc(ev.miAsiento)}` : "Inscrito"}
                            </button>
                        ` : `
                            <button onclick="abrirModalMapaAsientos('${esc(ev.id)}')"
                                ${cupo.agotado ? "disabled" : ""}
                                class="flex-1 ${cupo.agotado ? 'bg-slate-300 text-slate-500 cursor-not-allowed' : 'bg-[#F57C00] hover:bg-amber-600 text-white shadow-sm'} text-xs font-bold py-2 px-3 rounded-xl transition flex items-center justify-center gap-1.5">
                                <i class="fa-solid ${cupo.agotado ? 'fa-ban' : 'fa-couch'}"></i> ${cupo.agotado ? "Cupo Agotado" : "Elegir Asiento"}
                            </button>
                        `}
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
    document.getElementById("ev-descripcion").value = ev ? _limpiarDescripcionVisible(ev.descripcion) : "";
    _sincronizarInputsFechasModal(ev);
    setTimeout(() => _sincronizarInputsFechasModal(ev), 40);
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

    const rawInicio = document.getElementById("ev-fecha-hora")?.value || "";
    const rawFin = document.getElementById("ev-fecha-fin")?.value || "";
    const rawDisp = document.getElementById("ev-disponible-desde")?.value || "";

    if (rawFin && rawInicio && new Date(rawFin) <= new Date(rawInicio)) {
        toast("La fecha de término/expiración debe ser posterior al inicio del evento.", "alerta");
        return;
    }

    const isoInicio = new Date(rawInicio).toISOString();
    const isoFin = rawFin ? new Date(rawFin).toISOString() : null;
    const isoDisp = rawDisp ? new Date(rawDisp).toISOString() : null;

    const lugarVal = document.getElementById("ev-lugar").value.trim();
    const infoAudGuardar = typeof buscarAuditorioPorNombre === "function" ? buscarAuditorioPorNombre(lugarVal) : null;
    const capGuardar = Number(infoAudGuardar?.asientos || 100) || 100;
    const evPrevio = id ? eventosCache[id] : null;
    const metaPrevia = _extraerMetaDescripcion(evPrevio);
    const localStatePrev = id ? _leerEstadoAsientosLocal(id) : { res: {}, seats: {} };

    const mallaGuardar = Array.isArray(infoAudGuardar?.malla) && infoAudGuardar.malla.length >= 4
        ? infoAudGuardar.malla.map(v => [Number(v.lat), Number(v.lon)])
        : (Array.isArray(metaPrevia.malla) ? metaPrevia.malla : undefined);
    const tolGuardar = Number.isFinite(Number(infoAudGuardar?.toleranciaMetros))
        ? Number(infoAudGuardar.toleranciaMetros)
        : (typeof metaPrevia.tol === "number" ? metaPrevia.tol : 12);

    const descConMeta = _construirDescripcionConMeta(
        document.getElementById("ev-descripcion").value.trim(),
        {
            disp: isoDisp,
            fin: isoFin,
            cap: capGuardar,
            ocup: metaPrevia.ocup || 0,
            res: { ...(metaPrevia.res || {}), ...(localStatePrev.res || {}) },
            seats: { ...(metaPrevia.seats || {}), ...(localStatePrev.seats || {}) },
            liberados: metaPrevia.liberados || {},
            malla: mallaGuardar,
            tol: tolGuardar
        }
    );

    const payload = {
        titulo: document.getElementById("ev-titulo").value.trim(),
        descripcion: descConMeta,
        fechaHora: isoInicio,
        fechaFin: isoFin,
        disponibleDesde: isoDisp,
        lugar: lugarVal,
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
        if (guardado && guardado.id) {
            _guardarHorarioIndividualEvento(guardado.id, isoDisp, isoFin);
        }
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
        if (!id && guardado && guardado.id) {
            abrirModalQrEvento(guardado.id);
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

// ---------- Registro y Cancelación con Actualización Instantánea de Asientos ----------
async function registrarAsistenciaEvento(eventoId) {
    try {
        const res = await api(`/eventos/${eventoId}/registro`, { method: "POST" });
        const ev = eventosCache[eventoId];
        if (ev) {
            ev.inscrito = true;
            if (res && typeof res.asientosOcupados === "number") {
                ev.asientosOcupados = res.asientosOcupados;
                ev.totalRegistrados = res.asientosOcupados;
            } else {
                const actual = Number(ev.asientosOcupados ?? ev.totalRegistrados ?? 0) || 0;
                ev.asientosOcupados = actual + 1;
                ev.totalRegistrados = actual + 1;
            }
            if (res && typeof res.capacidadTotal === "number") {
                ev.capacidadTotal = res.capacidadTotal;
            }
            renderizarTarjetasEventos();
        }
        toast("¡Te has inscrito correctamente al evento!", "exito");
    } catch (err) {
        toast(err.message, "error");
    }
}

async function cancelarInscripcionEventoWeb(eventoId) {
    try {
        const res = await api(`/eventos/${eventoId}/registro`, { method: "DELETE" });
        const ev = eventosCache[eventoId];
        if (ev) {
            ev.inscrito = false;
            if (res && typeof res.asientosOcupados === "number") {
                ev.asientosOcupados = res.asientosOcupados;
                ev.totalRegistrados = res.asientosOcupados;
            } else {
                const actual = Number(ev.asientosOcupados ?? ev.totalRegistrados ?? 1) || 1;
                ev.asientosOcupados = Math.max(0, actual - 1);
                ev.totalRegistrados = Math.max(0, actual - 1);
            }
            if (res && typeof res.capacidadTotal === "number") {
                ev.capacidadTotal = res.capacidadTotal;
            }
            renderizarTarjetasEventos();
        }
        toast("Inscripción cancelada y asiento liberado.", "info");
    } catch (err) {
        toast(err.message, "error");
    }
}

let asistentesActual = null;
let _timerAsistentesVivo = null;

async function _cargarTablaAsistentesEnVivo(eventoId, mostrarLoader = false) {
    const tbody = document.getElementById("tabla-asistentes-body");
    if (!tbody) return;
    if (mostrarLoader) {
        tbody.innerHTML = `<tr><td colspan="7" class="p-6 text-center text-slate-400"><i class="fa-solid fa-spinner fa-spin mr-2"></i> Cargando lista de inscritos...</td></tr>`;
    }
    try {
        try {
            const esGestor = typeof tienePermiso === "function" && tienePermiso("eventos.publicar");
            const feedRec = await api(esGestor ? "/eventos/feed?incluir_programados=true" : "/eventos/feed");
            const evR = (feedRec || []).find(x => String(x.id) === String(eventoId));
            if (evR) eventosCache[eventoId] = evR;
        } catch (_) {}

        const asistentesCrudos = await api(`/eventos/${eventoId}/asistentes`);
        const ev = eventosCache[eventoId] || {};
        const syncState = _sincronizarEstadoAsientosConMetaEvento(ev);
        const seatsMap = { ...syncState.seats };
        const resMap = { ...syncState.res };
        const liberadosMap = { ...syncState.liberados };

        const asistentes = (asistentesCrudos || []).filter(a => {
            const em = (a.email || "").toLowerCase();
            const tsLib = Number(liberadosMap[em] || 0);
            const tsReg = a.fecha_registro ? new Date(a.fecha_registro).getTime() : 0;
            return !(tsLib > 0 && (!tsReg || tsLib >= tsReg));
        });

        if (!asistentes || asistentes.length === 0) {
            tbody.innerHTML = `<tr><td colspan="7" class="p-6 text-center text-slate-500">Aún no hay alumnos registrados en este evento.</td></tr>`;
            return;
        }
        const usados = new Set(Object.keys(resMap));
        Object.values(seatsMap).forEach(v => {
            const c = typeof v === "string" ? v : v?.asiento;
            if (c) usados.add(c);
        });
        tbody.innerHTML = asistentes.map((a, i) => {
            const em = (a.email || "").toLowerCase();
            const infoLocal = seatsMap[em];
            const codMeta = (infoLocal && !infoLocal.autoAsignado)
                ? (typeof infoLocal === "string" ? infoLocal : infoLocal?.asiento)
                : null;
            let codAsiento = codMeta || ((a.asiento && a.asiento !== "—") ? a.asiento : null);
            if (!codAsiento) {
                let idx = i;
                while (idx < 500) {
                    const cand = generarCodigoAsientoCine(idx, 10);
                    if (!usados.has(cand)) { codAsiento = cand; break; }
                    idx++;
                }
            }
            if (codAsiento) usados.add(codAsiento);

            const asistioReal = Boolean(a.asistio || infoLocal?.asistio);
            const qrOk = Boolean(a.qr_validado || asistioReal || infoLocal?.qrValidado);
            const gpsOk = Boolean(a.gps_validado || asistioReal || infoLocal?.gpsValidado);
            const badgeVal = (qrOk && gpsOk)
                ? `<span class="px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 font-extrabold text-[10px]">2/2 QR + GPS ✓</span>`
                : qrOk
                    ? `<span class="px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 font-extrabold text-[10px]">1/2 QR ✓ · Pend. GPS</span>`
                    : `<span class="px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-semibold text-[10px]">Asiento apartado</span>`;
            return `
            <tr class="border-b hover:bg-slate-50">
                <td class="p-3 font-bold text-slate-400">${i + 1}</td>
                <td class="p-3"><span class="px-2 py-1 rounded-lg bg-[#122652] text-[#FFB74D] font-mono font-extrabold text-xs">${esc(codAsiento || "—")}</span></td>
                <td class="p-3 font-semibold text-[#122652]">${esc(a.nombre)}</td>
                <td class="p-3 text-slate-500 font-mono text-xs">${esc(a.email)}</td>
                <td class="p-3 text-slate-600">${esc([a.carrera, (a.grupos || []).join(", ")].filter(Boolean).join(" · ") || "—")}</td>
                <td class="p-3">${badgeVal}</td>
                <td class="p-3 text-center">
                    <input type="checkbox" ${asistioReal ? "checked" : ""} onchange="marcarAsistio(${Number(a.id)}, this)" class="w-4 h-4 accent-[#10B981] rounded cursor-pointer">
                </td>
            </tr>`;
        }).join("");
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="7" class="p-6 text-center text-red-500">${esc(err.message)}</td></tr>`;
    }
}

async function verAsistentesEvento(eventoId) {
    if (!tienePermiso("asistencia.gestionar")) {
        toast("No tienes permiso para gestionar listas de asistencia.", "alerta");
        return;
    }
    const ev = eventosCache[eventoId] || {};
    asistentesActual = { id: eventoId, titulo: ev.titulo || "Evento" };
    document.getElementById("modal-asistentes-titulo").innerText = `Lista de Asistencia: ${asistentesActual.titulo}`;
    document.getElementById("modal-asistentes").classList.remove("hidden");

    await _cargarTablaAsistentesEnVivo(eventoId, true);

    if (_timerAsistentesVivo) clearInterval(_timerAsistentesVivo);
    _timerAsistentesVivo = setInterval(() => {
        const visible = !document.getElementById("modal-asistentes")?.classList.contains("hidden");
        if (visible && asistentesActual?.id) {
            _cargarTablaAsistentesEnVivo(asistentesActual.id, false);
        }
    }, 5000);
}

async function marcarAsistio(asistenciaId, checkbox) {
    try {
        await api(`/eventos/${asistentesActual.id}/asistentes/${asistenciaId}`, {
            method: "PATCH",
            json: { asistio: checkbox.checked }
        });
        await _cargarTablaAsistentesEnVivo(asistentesActual.id, false);
        toast(checkbox.checked ? "Asistencia confirmada (2/2)." : "Asistencia desmarcada.", "exito");
    } catch (err) {
        checkbox.checked = !checkbox.checked;
        toast(err.message, "error");
    }
}

function cerrarModalAsistentes() {
    document.getElementById("modal-asistentes").classList.add("hidden");
    if (_timerAsistentesVivo) {
        clearInterval(_timerAsistentesVivo);
        _timerAsistentesVivo = null;
    }
}

async function descargarPDFDe(eventoId) {
    try {
        toast("Generando lista de asistencia con Formato ETDA...", "info");
        let ev = eventosCache[eventoId];
        if (!ev) {
            try {
                const todos = await api("/eventos/feed");
                (todos || []).forEach(e => { eventosCache[e.id] = e; });
                ev = eventosCache[eventoId];
            } catch (_) {}
        }
        ev = ev || { id: eventoId, titulo: asistentesActual?.titulo || "Evento Académico UNACH", lugar: "Campus I Tuxtla", fechaHora: new Date().toISOString() };

        const asistentes = await api(`/eventos/${eventoId}/asistentes`);
        const lista = Array.isArray(asistentes) ? asistentes : [];
        const confirmados = lista.filter(a => a.asistio).length;
        const m = typeof obtenerMembreteActivo === "function" ? obtenerMembreteActivo() : MEMBRETE_ETDA_POR_DEFECTO;

        const fechaEventoStr = ev.fechaHora
            ? new Date(ev.fechaHora).toLocaleString("es-MX", { dateStyle: "long", timeStyle: "short" })
            : "Fecha por confirmar";

        const audienciaStr = ev.esGeneral
            ? "Comunidad Universitaria General"
            : [ev.idFacultadDestino, ev.idCarreraDestino, (ev.gruposDestino || []).length ? "Grupos: " + ev.gruposDestino.join(", ") : null]
                .filter(Boolean)
                .join(" · ") || "Comunidad ETDA";

        const filasHtml = lista.length
            ? lista.map((a, idx) => `
                <tr>
                    <td style="text-align:center;font-weight:700;color:#243763;">${idx + 1}</td>
                    <td style="font-weight:600;color:#1E293B;">${esc(a.nombre || "—")}</td>
                    <td style="font-family:monospace;font-size:9.5pt;color:#334155;">${esc(a.email || "—")}</td>
                    <td style="color:#334155;">${esc([a.carrera, (a.grupos || []).join(", ")].filter(Boolean).join(" · ") || "—")}</td>
                    <td style="text-align:center;font-size:9pt;color:#475569;">${a.fecha_registro ? esc(new Date(a.fecha_registro).toLocaleString("es-MX")) : "Registrado"}</td>
                    <td style="text-align:center;font-weight:700;color:${a.asistio ? '#15803D' : '#64748B'};">
                        ${a.asistio ? "✓ ASISTIÓ" : "Inscrito"}
                    </td>
                </tr>
            `).join("")
            : `<tr><td colspan="6" style="text-align:center;padding:18px;color:#64748B;font-style:italic;">No hay alumnos inscritos registrados en este evento.</td></tr>`;

        const ventana = window.open("", "_blank", "width=1020,height=840");
        if (!ventana) {
            toast("Permite las ventanas emergentes en tu navegador para generar el PDF con Formato ETDA.", "alerta");
            return;
        }

        ventana.document.write(`<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <title>Lista de Asistencia - ${esc(ev.titulo)}</title>
    <style>
        @page {
            size: letter;
            margin: 0;
        }
        * {
            box-sizing: border-box;
            -webkit-print-color-adjust: exact !important;
            print-color-adjust: exact !important;
        }
        html, body {
            margin: 0;
            padding: 0;
            font-family: 'Arial', 'Segoe UI', sans-serif;
            color: #1E293B;
            background: #FFFFFF;
        }
        table.page-frame {
            width: 100%;
            border-collapse: collapse;
            min-height: 100vh;
        }
        thead.frame-header {
            display: table-header-group;
        }
        tfoot.frame-footer {
            display: table-footer-group;
        }
        .etda-header-wrap {
            padding: 22px 44px 12px 44px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
        }
        .etda-header-left {
            display: flex;
            align-items: center;
            gap: 14px;
        }
        .etda-logo-izq {
            height: 68px;
            width: auto;
            object-fit: contain;
        }
        .etda-logo-der {
            height: 62px;
            width: auto;
            object-fit: contain;
        }
        .etda-inst-title {
            font-size: 13.5pt;
            font-weight: 800;
            color: #243763;
            letter-spacing: 0.2px;
            margin: 0;
            line-height: 1.2;
        }
        .etda-dep-title {
            font-size: 10.5pt;
            font-weight: 500;
            color: #243763;
            margin: 3px 0 0 0;
            line-height: 1.25;
        }
        .etda-bar-top {
            height: 6px;
            width: 100%;
            background: ${esc(m.colorBarraSuperior || "#2B3E6B")};
        }
        .etda-body-content {
            padding: 22px 44px 110px 44px;
            vertical-align: top;
        }
        .doc-subtitle {
            text-align: center;
            font-size: 12pt;
            font-weight: 800;
            color: #243763;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            margin: 0 0 14px 0;
        }
        .meta-box {
            border: 1px solid #CBD5E1;
            background: #F8FAFC;
            border-left: 5px solid ${esc(m.colorBarraSuperior || "#2B3E6B")};
            padding: 12px 16px;
            margin-bottom: 18px;
            display: grid;
            grid-template-columns: 1.4fr 1fr;
            gap: 6px 18px;
            font-size: 9.5pt;
        }
        .meta-box div strong {
            color: #243763;
        }
        table.att-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 9.5pt;
        }
        table.att-table th {
            background: ${esc(m.colorBarraSuperior || "#2B3E6B")};
            color: #FFFFFF;
            font-weight: 700;
            text-transform: uppercase;
            font-size: 8.5pt;
            letter-spacing: 0.3px;
            padding: 8px 8px;
            border: 1px solid ${esc(m.colorBarraSuperior || "#2B3E6B")};
            text-align: left;
        }
        table.att-table td {
            padding: 7px 8px;
            border: 1px solid #CBD5E1;
        }
        table.att-table tbody tr:nth-child(even) {
            background: #F4F7FB;
        }
        .firma-box {
            margin-top: 38px;
            text-align: center;
            page-break-inside: avoid;
        }
        .firma-line {
            width: 260px;
            border-top: 1.5px solid #243763;
            margin: 0 auto 5px auto;
        }
        .firma-label {
            font-size: 9.5pt;
            font-weight: 700;
            color: #243763;
        }
        .etda-footer-fixed {
            position: fixed;
            bottom: 0;
            left: 0;
            right: 0;
            background: #FFFFFF;
        }
        .etda-bar-bottom {
            height: 6px;
            width: 100%;
            background: ${esc(m.colorBarraInferior || "#C49B27")};
        }
        .etda-footer-inner {
            padding: 10px 44px 18px 44px;
            color: #243763;
            font-size: 8.5pt;
            line-height: 1.45;
        }
        .etda-footer-row1 {
            display: flex;
            justify-content: space-between;
            align-items: baseline;
            gap: 12px;
        }
        .etda-footer-domain {
            font-size: 14pt;
            font-weight: 800;
            color: #243763;
            letter-spacing: 0.2px;
        }
    </style>
</head>
<body>
    <table class="page-frame">
        <thead class="frame-header">
            <tr>
                <td>
                    <div class="etda-header-wrap">
                        <div class="etda-header-left">
                            <img class="etda-logo-izq" src="${m.logoIzquierdo}" alt="Escudo UNACH">
                            <div>
                                <p class="etda-inst-title">${esc(m.institucion)}</p>
                                <p class="etda-dep-title">${esc(m.dependencia)}</p>
                            </div>
                        </div>
                        <img class="etda-logo-der" src="${m.logoDerecho}" alt="Educación que transforma">
                    </div>
                    <div class="etda-bar-top"></div>
                </td>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td class="etda-body-content">
                    ${m.subtituloDocumento ? `<h2 class="doc-subtitle">${esc(m.subtituloDocumento)}</h2>` : ""}
                    <div class="meta-box">
                        <div><strong>Evento Académico:</strong> ${esc(ev.titulo || "Evento")}</div>
                        <div><strong>Fecha y Hora:</strong> ${esc(fechaEventoStr)}</div>
                        <div><strong>Sede / Auditorio:</strong> ${esc(ev.lugar || "Campus I")}</div>
                        <div><strong>Inscritos / Asistencias:</strong> ${lista.length} inscritos · ${confirmados} asistencias confirmadas</div>
                        <div style="grid-column: span 2;"><strong>Dirigido a:</strong> ${esc(audienciaStr)}</div>
                    </div>

                    <table class="att-table">
                        <thead>
                            <tr>
                                <th style="width:6%;text-align:center;">No.</th>
                                <th style="width:28%;">Nombre Completo</th>
                                <th style="width:24%;">Correo Institucional</th>
                                <th style="width:18%;">Carrera / Grupo</th>
                                <th style="width:13%;text-align:center;">Fecha Registro</th>
                                <th style="width:11%;text-align:center;">Asistencia</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${filasHtml}
                        </tbody>
                    </table>

                    ${(m.mostrarFirma !== false && (m.firmaResponsable || m.cargoFirma)) ? `
                        <div class="firma-box">
                            <div class="firma-line"></div>
                            ${m.firmaResponsable ? `<div class="firma-label">${esc(m.firmaResponsable)}</div>` : ""}
                            ${m.cargoFirma ? `<div style="font-size:8.5pt;color:#475569;margin-top:2px;">${esc(m.cargoFirma)}</div>` : ""}
                        </div>
                    ` : ""}
                </td>
            </tr>
        </tbody>
    </table>

    <div class="etda-footer-fixed">
        <div class="etda-bar-bottom"></div>
        <div class="etda-footer-inner">
            <div class="etda-footer-row1">
                <span>${esc(m.direccionPie)}</span>
                <span class="etda-footer-domain">${esc(m.dominioPie)}</span>
            </div>
            <div>${esc(m.contactoPie)}</div>
        </div>
    </div>

    <script>
        window.onload = function() {
            setTimeout(function() { window.print(); }, 350);
        };
    <\/script>
</body>
</html>`);
        ventana.document.close();
    } catch (err) {
        toast("Error al generar la lista de asistencia: " + err.message, "error");
    }
}

function descargarAsistenciaPDF() {
    if (asistentesActual) descargarPDFDe(asistentesActual.id);
}

// ============================================================================
// GENERACIÓN AUTOMÁTICA DE CÓDIGO QR OFICIAL POR EVENTO (VER, DESCARGAR, COMPARTIR E IMPRIMIR)
// ============================================================================
let eventoQrActual = null;

function obtenerPayloadQrOficialEvento(ev) {
    if (!ev) return "";
    return ev.qrCodigo || ev.qr_codigo || `CCUNACH|EVENTO|${ev.id}`;
}

function abrirModalQrEvento(eventoId) {
    const ev = eventosCache[eventoId];
    if (!ev) {
        toast("No se encontró la información del evento.", "alerta");
        return;
    }
    eventoQrActual = ev;
    const codigoQr = obtenerPayloadQrOficialEvento(ev);
    const catInfo = detectarCategoriaEventoWeb(ev);
    const fInicio = extraerFechaVisualWeb(ev.fechaHora);
    const hInd = _obtenerHorarioIndividualEvento(ev);
    const textoFin = hInd.fechaFin
        ? new Date(hInd.fechaFin).toLocaleString("es-MX", { dateStyle: "medium", timeStyle: "short" })
        : "4 horas después del inicio";

    const elCat = document.getElementById("qr-modal-categoria");
    if (elCat) {
        elCat.innerText = (ev.categoria || catInfo.nombre || "ACADÉMICOS").toUpperCase();
        elCat.style.backgroundColor = catInfo.color || "#122652";
    }
    const elTit = document.getElementById("qr-modal-titulo");
    if (elTit) elTit.innerText = ev.titulo || "Evento Académico UNACH";

    const cupo = _calcularEstadoAsientosEvento(ev);
    const elLug = document.getElementById("qr-modal-lugar");
    if (elLug) elLug.innerText = `📍 ${ev.lugar || "Campus I"} (${cupo.disponibles} disponibles de ${cupo.capacidad} asientos)`;

    const elHor = document.getElementById("qr-modal-horario");
    if (elHor) elHor.innerText = `🕒 Inicio: ${fInicio.completo}  •  Fin: ${textoFin}`;

    const elCod = document.getElementById("qr-modal-codigo-texto");
    if (elCod) elCod.innerText = codigoQr;

    const lienzo = document.getElementById("qr-evento-lienzo");
    if (lienzo) {
        lienzo.innerHTML = "";
        if (typeof QRCode !== "undefined") {
            new QRCode(lienzo, {
                text: codigoQr,
                width: 210,
                height: 210,
                colorDark: "#122652",
                colorLight: "#FFFFFF",
                correctLevel: QRCode.CorrectLevel.H
            });
        } else {
            const urlApiQr = `https://api.qrserver.com/v1/create-qr-code/?size=220x220&color=122652&bgcolor=FFFFFF&data=${encodeURIComponent(codigoQr)}`;
            lienzo.innerHTML = `<img src="${urlApiQr}" alt="QR ${esc(ev.titulo)}" crossorigin="anonymous" class="w-[210px] h-[210px] object-contain">`;
        }
    }

    document.getElementById("modal-qr-evento")?.classList.remove("hidden");
}

function cerrarModalQrEvento() {
    document.getElementById("modal-qr-evento")?.classList.add("hidden");
}

function _obtenerDataUrlQrActual() {
    const lienzo = document.getElementById("qr-evento-lienzo");
    if (!lienzo) return null;
    const canvasQr = lienzo.querySelector("canvas");
    const imgQr = lienzo.querySelector("img");
    if (canvasQr) return canvasQr.toDataURL("image/png");
    if (imgQr && imgQr.src) return imgQr.src;
    return null;
}

function descargarQrEventoPNG() {
    if (!eventoQrActual) return;
    const dataUrl = _obtenerDataUrlQrActual();
    if (!dataUrl) {
        toast("No se pudo generar la imagen del QR.", "error");
        return;
    }

    const a = document.createElement("a");
    const slug = (eventoQrActual.titulo || "evento").toLowerCase().replace(/[^a-z0-9]+/g, "-").slice(0, 40);
    a.href = dataUrl;
    a.download = `qr-asistencia-${slug}.png`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    toast("Código QR descargado en formato PNG.", "exito");
}

async function compartirQrEvento() {
    if (!eventoQrActual) return;
    const dataUrl = _obtenerDataUrlQrActual();
    const codigoQr = obtenerPayloadQrOficialEvento(eventoQrActual);
    const fInicio = extraerFechaVisualWeb(eventoQrActual.fechaHora);
    const textoCompartir = `🎓 Código QR Oficial de Asistencia — ${eventoQrActual.titulo}\n📍 Sede: ${eventoQrActual.lugar || "Campus I UNACH"}\n🕒 Inicio: ${fInicio.completo}\n🔑 Código: ${codigoQr}`;

    try {
        if (dataUrl && dataUrl.startsWith("data:image/")) {
            const res = await fetch(dataUrl);
            const blob = await res.blob();
            const archivo = new File([blob], "qr-evento-unach.png", { type: "image/png" });

            if (navigator.canShare && navigator.canShare({ files: [archivo] })) {
                await navigator.share({
                    title: `QR Oficial — ${eventoQrActual.titulo}`,
                    text: textoCompartir,
                    files: [archivo]
                });
                toast("Código QR compartido con éxito.", "exito");
                return;
            }

            if (navigator.clipboard && typeof ClipboardItem !== "undefined") {
                await navigator.clipboard.write([new ClipboardItem({ "image/png": blob })]);
                toast("¡Imagen del Código QR copiada al portapapeles! Ya puedes pegarla (Ctrl+V) en WhatsApp, redes o documentos.", "exito");
                return;
            }
        }
        if (navigator.share) {
            await navigator.share({
                title: `QR Oficial — ${eventoQrActual.titulo}`,
                text: textoCompartir
            });
            return;
        }
        await navigator.clipboard.writeText(textoCompartir);
        toast("Datos del QR copiados al portapapeles.", "exito");
    } catch (_) {
        toast("Puedes descargar el QR en PNG o imprimir el cartel.", "info");
    }
}

function imprimirHojaQrEvento() {
    if (!eventoQrActual) return;
    const dataUrl = _obtenerDataUrlQrActual() || "";
    const codigoQr = obtenerPayloadQrOficialEvento(eventoQrActual);
    const fInicio = extraerFechaVisualWeb(eventoQrActual.fechaHora);
    const hInd = _obtenerHorarioIndividualEvento(eventoQrActual);
    const textoFin = hInd.fechaFin
        ? new Date(hInd.fechaFin).toLocaleString("es-MX", { dateStyle: "medium", timeStyle: "short" })
        : "Al concluir la sesión";

    const w = window.open("", "_blank", "width=850,height=950");
    if (!w) {
        toast("Permite las ventanas emergentes para imprimir el cartel QR.", "alerta");
        return;
    }
    w.document.write(`<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title>Cartel QR — ${esc(eventoQrActual.titulo)}</title>
<style>
    body { font-family: "Inter", Arial, sans-serif; text-align: center; padding: 40px; color: #122652; }
    .marco { border: 4px solid #122652; border-radius: 28px; padding: 36px; max-width: 620px; margin: 0 auto; }
    .tag { display: inline-block; background: #F57C00; color: #fff; font-weight: 800; font-size: 12px; padding: 6px 16px; border-radius: 99px; text-transform: uppercase; letter-spacing: 1px; }
    h1 { font-size: 26px; margin: 16px 0 8px; color: #122652; }
    .meta { font-size: 14px; color: #475569; margin-bottom: 24px; font-weight: 600; }
    .qr-box { display: inline-block; padding: 24px; border: 3px dashed #0093FF; border-radius: 24px; background: #fff; margin: 12px 0; }
    .pasos { margin-top: 24px; background: #F4F7FB; border-radius: 16px; padding: 16px; font-size: 13px; text-align: left; line-height: 1.6; }
</style>
</head>
<body>
    <div class="marco">
        <span class="tag">UNIVERSIDAD AUTÓNOMA DE CHIAPAS • ETDA</span>
        <h1>${esc(eventoQrActual.titulo)}</h1>
        <div class="meta">
            📍 ${esc(eventoQrActual.lugar || "Campus I")} &nbsp;|&nbsp; 🏷️ ${esc(eventoQrActual.categoria || "Académicos")}<br>
            🕒 Inicio: ${esc(fInicio.completo)} &nbsp;•&nbsp; Fin: ${esc(textoFin)}
        </div>
        <div class="qr-box">
            <img src="${dataUrl}" width="280" height="280" alt="Código QR Oficial"><br>
            <small style="font-family:monospace;color:#64748B;font-size:11px;">${esc(codigoQr)}</small>
        </div>
        <div class="pasos">
            <strong>📱 Instrucciones para acreditar tu asistencia (+1 en ${esc(eventoQrActual.categoria || "tu insignia")}):</strong><br>
            1. Abre la App <b>Campus Connect UNACH</b>, elige tu asiento en este evento.<br>
            2. Al llegar al recinto, escanea este código QR para registrar tu <b>Validación 1/2 (Llegada por QR)</b>.<br>
            3. Mantén tu ubicación activa durante la sesión para completar la <b>Validación 2/2 (Permanencia GPS)</b>.
        </div>
    </div>
    <script>window.onload = () => { setTimeout(() => window.print(), 350); };<\/script>
</body>
</html>`);
    w.document.close();
}

// ============================================================================
// MAPA INTERACTIVO DE ASIENTOS TIPO CINE EN TIEMPO REAL (ALUMNOS + RESERVAS ADMIN)
// ============================================================================
let estadoMapaCine = {
    eventoId: null,
    capacidadTotal: 100,
    miAsiento: null,
    asientoSeleccionado: null,
    ocupadosPorCodigo: {},
    timerPolling: null
};

function generarCodigoAsientoCine(indiceBase0, cols = 10) {
    const filaIdx = Math.floor(Math.max(0, indiceBase0) / cols);
    const colNum = (Math.max(0, indiceBase0) % cols) + 1;
    let letra = "";
    if (filaIdx < 26) {
        letra = String.fromCharCode(65 + filaIdx);
    } else {
        letra = String.fromCharCode(65 + Math.floor(filaIdx / 26) - 1) + String.fromCharCode(65 + (filaIdx % 26));
    }
    return `${letra}-${String(colNum).padStart(2, "0")}`;
}

async function abrirModalMapaAsientos(eventoId) {
    const ev = eventosCache[eventoId];
    if (!ev) {
        toast("No se encontró el evento.", "alerta");
        return;
    }
    estadoMapaCine.eventoId = eventoId;
    estadoMapaCine.asientoSeleccionado = null;

    const puedeAdmin = tienePermiso("eventos.publicar") || tienePermiso("asistencia.gestionar") || esAdminReal();
    document.getElementById("cine-panel-admin-reservas")?.classList.toggle("hidden", !puedeAdmin);

    document.getElementById("cine-modal-titulo").innerText = `${ev.titulo || "Evento Académico"}`;
    document.getElementById("modal-mapa-asientos")?.classList.remove("hidden");

    await refrescarMapaAsientosEnVivo(false);

    if (estadoMapaCine.timerPolling) clearInterval(estadoMapaCine.timerPolling);
    estadoMapaCine.timerPolling = setInterval(() => {
        const modalVisible = !document.getElementById("modal-mapa-asientos")?.classList.contains("hidden");
        if (modalVisible && estadoMapaCine.eventoId) {
            refrescarMapaAsientosEnVivo(false);
        }
    }, 4000);
}

function cerrarModalMapaAsientos() {
    document.getElementById("modal-mapa-asientos")?.classList.add("hidden");
    if (estadoMapaCine.timerPolling) {
        clearInterval(estadoMapaCine.timerPolling);
        estadoMapaCine.timerPolling = null;
    }
}

function _actualizarDescripcionMetaEnCacheLocal(eventoId) {
    const ev = eventosCache[eventoId];
    if (!ev) return null;
    const hInd = _obtenerHorarioIndividualEvento(ev);
    const infoAud = typeof buscarAuditorioPorNombre === "function" ? buscarAuditorioPorNombre(ev.lugar) : null;
    const cap = Number(infoAud?.asientos || estadoMapaCine.capacidadTotal || ev.capacidadTotal || 100) || 100;
    const localState = _leerEstadoAsientosLocal(eventoId);

    const codigosUnicos = new Set([
        ...Object.keys(localState.res || {}),
        ...Object.values(localState.seats || {}).map(x => typeof x === "string" ? x : x?.asiento).filter(Boolean)
    ]);
    const totOcup = codigosUnicos.size;

    const metaActual = _extraerMetaDescripcion(ev);
    const mallaCompacta = Array.isArray(infoAud?.malla) && infoAud.malla.length >= 4
        ? infoAud.malla.map(v => [Number(v.lat), Number(v.lon)])
        : (Array.isArray(metaActual.malla) ? metaActual.malla : undefined);
    const tolActual = Number.isFinite(Number(infoAud?.toleranciaMetros))
        ? Number(infoAud.toleranciaMetros)
        : (typeof metaActual.tol === "number" ? metaActual.tol : 12);

    const nuevaDesc = _construirDescripcionConMeta(ev.descripcion, {
        disp: hInd.disponibleDesde,
        fin: hInd.fechaFin,
        cap,
        ocup: totOcup,
        res: localState.res,
        seats: localState.seats,
        liberados: localState.liberados,
        malla: mallaCompacta,
        tol: tolActual
    });
    ev.descripcion = nuevaDesc;
    ev.capacidadTotal = cap;
    ev.asientosOcupados = totOcup;
    ev.totalRegistrados = totOcup;
    return { ev, hInd, nuevaDesc };
}

async function _persistirMetaAsientosEnEventoServidor(eventoId) {
    const prep = _actualizarDescripcionMetaEnCacheLocal(eventoId);
    if (!prep || !tienePermiso("eventos.publicar")) return;
    const { ev, hInd, nuevaDesc } = prep;
    try {
        await api(`/eventos/${eventoId}`, {
            method: "PUT",
            json: {
                titulo: ev.titulo,
                descripcion: nuevaDesc,
                fechaHora: ev.fechaHora,
                fechaFin: hInd.fechaFin,
                disponibleDesde: hInd.disponibleDesde,
                lugar: ev.lugar,
                categoria: ev.categoria || null,
                esGeneral: Boolean(ev.esGeneral),
                idFacultadDestino: ev.idFacultadDestino || null,
                idCarreraDestino: ev.idCarreraDestino || null,
                gruposDestino: ev.gruposDestino || null
            }
        });
    } catch (_) {}
}

async function refrescarMapaAsientosEnVivo(mostrarToast = false) {
    const eventoId = estadoMapaCine.eventoId;
    if (!eventoId) return;

    // Traer primero la versión más reciente del evento desde el feed para captar cambios hechos desde la App móvil (ej. asiento K-04 o QR 1/2)
    try {
        const esGestor = typeof tienePermiso === "function" && tienePermiso("eventos.publicar");
        const feedReciente = await api(esGestor ? "/eventos/feed?incluir_programados=true" : "/eventos/feed");
        if (Array.isArray(feedReciente)) {
            const evRemoto = feedReciente.find(x => String(x.id) === String(eventoId));
            if (evRemoto) {
                eventosCache[eventoId] = evRemoto;
            }
        }
    } catch (_) {}

    const evLocal = eventosCache[eventoId] || {};
    const meta = _extraerMetaDescripcion(evLocal);
    const syncState = _sincronizarEstadoAsientosConMetaEvento(evLocal);
    const infoAud = typeof buscarAuditorioPorNombre === "function" ? buscarAuditorioPorNombre(evLocal.lugar) : null;
    const capCatalogo = Number(infoAud?.asientos || evLocal.capacidadTotal || meta.cap || 100) || 100;
    const emailYo = _obtenerEmailUsuarioWeb();

    const ocupadosCombinados = {};
    let miAsientoDetectado = null;

    const resMap = { ...syncState.res };
    const seatsMap = { ...syncState.seats };
    const liberadosMap = { ...syncState.liberados };

    // 1. Cargar reservas de administración
    Object.entries(resMap).forEach(([cod, infoRes]) => {
        const etiqueta = typeof infoRes === "string" ? infoRes : (infoRes?.etiqueta || "Reservado: Maestro / Invitado Especial");
        ocupadosCombinados[cod] = {
            asiento: cod,
            estado: "reservado_admin",
            etiqueta,
            reservadoPor: infoRes?.reservadoPor || "Administración ETDA",
            fechaApartado: infoRes?.fecha || null
        };
    });

    // 2. Cargar asientos de alumnos sincronizados en CCMETA / localStorage (ej. K-04 elegido en la App)
    Object.entries(seatsMap).forEach(([emKey, val]) => {
        const cod = typeof val === "string" ? val : val?.asiento;
        if (!cod || ocupadosCombinados[cod]) return;
        const esMio = Boolean(emailYo && emKey.toLowerCase() === emailYo);
        if (esMio) miAsientoDetectado = cod;
        ocupadosCombinados[cod] = {
            asiento: cod,
            estado: esMio ? "mio" : "ocupado_alumno",
            esMio,
            nombre: val?.nombre || (esMio ? _obtenerNombreUsuarioWeb() : "Alumno Registrado"),
            email: val?.email || emKey,
            carrera: val?.carrera || "",
            grupos: val?.grupos || [],
            qrValidado: Boolean(val?.qrValidado || val?.asistio),
            gpsValidado: Boolean(val?.gpsValidado || val?.asistio),
            asistio: Boolean(val?.asistio),
            fechaApartado: val?.fechaApartado || null
        };
    });

    // 3. Consultar endpoint nativo /mapa-asientos si está disponible (sin sobrescribir asientos explícitos de CCMETA)
    try {
        const data = await api(`/eventos/${eventoId}/mapa-asientos`);
        if (data && Array.isArray(data.detalleAsientos)) {
            data.detalleAsientos.forEach(item => {
                if (item && item.asiento) {
                    const emItem = (item.email || "").toLowerCase();
                    if (emItem && liberadosMap[emItem]) return;
                    if (liberadosMap[item.asiento] && item.estado === "reservado_admin") return;
                    const asientoExplicitoMeta = emItem && seatsMap[emItem] && !seatsMap[emItem].autoAsignado
                        ? (typeof seatsMap[emItem] === "string" ? seatsMap[emItem] : seatsMap[emItem]?.asiento)
                        : null;
                    if (asientoExplicitoMeta && asientoExplicitoMeta !== item.asiento) {
                        return;
                    }
                    ocupadosCombinados[item.asiento] = item;
                    if (item.esMio || (emailYo && emItem === emailYo)) {
                        miAsientoDetectado = item.asiento;
                    }
                }
            });
        }
    } catch (_) {}

    // 4. Sincronizar con GET /eventos/{id}/asistentes dando prioridad #1 al asiento elegido/cambiado en CCMETA
    try {
        const listaAsis = await api(`/eventos/${eventoId}/asistentes`);
        if (Array.isArray(listaAsis)) {
            const usados = new Set(Object.keys(ocupadosCombinados));
            listaAsis.forEach((a, idx) => {
                const em = (a.email || "").toLowerCase();
                const tsLibEm = Number(liberadosMap[em] || 0);
                const tsReg = a.fecha_registro ? new Date(a.fecha_registro).getTime() : 0;
                if (tsLibEm > 0 && (!tsReg || tsLibEm >= tsReg)) {
                    return;
                }

                const prevSeat = seatsMap[em];
                const codExplicitoMeta = (prevSeat && !prevSeat.autoAsignado)
                    ? (typeof prevSeat === "string" ? prevSeat : prevSeat?.asiento)
                    : null;
                const codServidor = (a.asiento && a.asiento !== "—") ? a.asiento : null;
                let cod = codExplicitoMeta || codServidor;
                let fueAutoAsignado = false;

                if (!cod) {
                    fueAutoAsignado = true;
                    let cIdx = idx;
                    while (cIdx < capCatalogo) {
                        const cand = generarCodigoAsientoCine(cIdx, 10);
                        if (!usados.has(cand) && !liberadosMap[cand]) { cod = cand; break; }
                        cIdx++;
                    }
                }
                if (cod) {
                    // Si el alumno cambió de asiento en la App (por ejemplo de K-04 a D-07), limpiar cualquier asiento anterior de ese alumno
                    Object.keys(ocupadosCombinados).forEach(k => {
                        if (k !== cod && (ocupadosCombinados[k]?.email || "").toLowerCase() === em) {
                            delete ocupadosCombinados[k];
                        }
                    });
                    usados.add(cod);
                    const esMio = Boolean(emailYo && em === emailYo);
                    if (esMio) miAsientoDetectado = cod;
                    const itemAlumno = {
                        asiento: cod,
                        autoAsignado: fueAutoAsignado,
                        ts: prevSeat?.ts || tsReg || Date.now(),
                        asistenciaId: a.id,
                        estado: esMio ? "mio" : "ocupado_alumno",
                        esMio,
                        nombre: a.nombre || prevSeat?.nombre || "Alumno Registrado",
                        email: a.email || em,
                        matricula: a.matricula || prevSeat?.matricula || "",
                        carrera: a.carrera || prevSeat?.carrera || "",
                        grupos: a.grupos || prevSeat?.grupos || [],
                        qrValidado: Boolean(a.qr_validado || a.asistio || prevSeat?.qrValidado),
                        gpsValidado: Boolean(a.gps_validado || a.asistio || prevSeat?.gpsValidado),
                        asistio: Boolean(a.asistio || prevSeat?.asistio),
                        fechaApartado: prevSeat?.fechaApartado || a.fecha_registro || null
                    };
                    ocupadosCombinados[cod] = itemAlumno;
                    if (em) seatsMap[em] = itemAlumno;
                }
            });
            _guardarEstadoAsientosLocal(eventoId, resMap, seatsMap, liberadosMap);
        }
    } catch (_) {}

    const asientoMioAnterior = estadoMapaCine.miAsiento;
    estadoMapaCine.capacidadTotal = capCatalogo;
    estadoMapaCine.miAsiento = miAsientoDetectado;
    estadoMapaCine.ocupadosPorCodigo = ocupadosCombinados;

    if (eventosCache[eventoId]) {
        const totOcup = Object.keys(ocupadosCombinados).length;
        eventosCache[eventoId].capacidadTotal = capCatalogo;
        eventosCache[eventoId].asientosOcupados = totOcup;
        eventosCache[eventoId].totalRegistrados = totOcup;
        eventosCache[eventoId].miAsiento = miAsientoDetectado;
        eventosCache[eventoId].inscrito = Boolean(miAsientoDetectado);
        renderizarTarjetasEventos();
    }

    // Si aún no había asiento seleccionado o si el usuario cambió su asiento desde la App, apuntar el inspector al nuevo asiento
    if ((!estadoMapaCine.asientoSeleccionado || estadoMapaCine.asientoSeleccionado === asientoMioAnterior) && estadoMapaCine.miAsiento) {
        estadoMapaCine.asientoSeleccionado = estadoMapaCine.miAsiento;
    }

    renderizarGridButacasCine();
    renderizarInspectorAsientoCine();
    if (mostrarToast) toast("Mapa de asientos sincronizado en tiempo real.", "info");
}

function renderizarGridButacasCine() {
    const cont = document.getElementById("cine-grid-contenedor");
    if (!cont) return;

    const ev = eventosCache[estadoMapaCine.eventoId] || {};
    const cap = estadoMapaCine.capacidadTotal;
    const ocupadosMap = estadoMapaCine.ocupadosPorCodigo;
    const totalOcupados = Object.keys(ocupadosMap).length;
    const libres = Math.max(0, cap - totalOcupados);

    const subEl = document.getElementById("cine-modal-subtitulo");
    if (subEl) {
        subEl.innerText = `📍 ${ev.lugar || "Recinto UNACH"} · Capacidad del Catálogo: ${cap} asientos`;
    }
    const kpiEl = document.getElementById("cine-modal-resumen-kpi");
    if (kpiEl) {
        kpiEl.innerHTML = `<span class="text-emerald-700">${libres} disponibles</span> &nbsp;•&nbsp; <span class="text-[#0093FF]">${totalOcupados}/${cap} ocupados</span>`;
    }

    const cols = 10;
    const totalFilas = Math.ceil(cap / cols);
    let htmlFilas = "";

    for (let r = 0; r < totalFilas; r++) {
        const letraFila = r < 26 ? String.fromCharCode(65 + r) : String.fromCharCode(65 + Math.floor(r / 26) - 1) + String.fromCharCode(65 + (r % 26));
        let celdasIzq = "";
        let celdasDer = "";

        for (let c = 0; c < cols; c++) {
            const idx = r * cols + c;
            if (idx >= cap) break;
            const codigo = generarCodigoAsientoCine(idx, cols);
            const info = ocupadosMap[codigo];
            const esSeleccionado = estadoMapaCine.asientoSeleccionado === codigo;
            const esMio = estadoMapaCine.miAsiento === codigo || info?.esMio;

            let claseColor = "bg-slate-800 hover:bg-slate-700 text-slate-200 border-slate-600";
            let iconoHtml = `<span class="text-[9px] font-extrabold">${c + 1}</span>`;

            if (info) {
                if (info.estado === "reservado_admin") {
                    claseColor = "bg-[#F57C00] hover:bg-amber-500 text-white border-amber-300 shadow-sm";
                    iconoHtml = `<i class="fa-solid fa-crown text-[9px]"></i>`;
                } else if (esMio) {
                    claseColor = "bg-[#122652] text-[#FFB74D] border-[#F57C00] shadow-md";
                    iconoHtml = `<i class="fa-solid fa-user-check text-[9px]"></i>`;
                } else if (info.asistio) {
                    claseColor = "bg-[#10B981] hover:bg-emerald-500 text-white border-emerald-300 shadow-sm";
                    iconoHtml = `<i class="fa-solid fa-check-double text-[9px]"></i>`;
                } else {
                    claseColor = "bg-[#0093FF] hover:bg-sky-400 text-white border-sky-200 shadow-sm";
                    iconoHtml = `<i class="fa-solid fa-user text-[9px]"></i>`;
                }
            }

            if (esSeleccionado) {
                claseColor += " ring-2 ring-white scale-110 z-10";
            }

            const btnHtml = `
                <button type="button" onclick="seleccionarAsientoEnMapaCine('${codigo}')"
                    title="Asiento ${codigo}${info ? (info.nombre ? " · " + esc(info.nombre) : " · " + esc(info.etiqueta || "Reservado")) : " · Libre"}"
                    class="w-7 h-7 sm:w-8 sm:h-8 rounded-t-xl rounded-b-md border transition-all duration-150 flex flex-col items-center justify-center shrink-0 ${claseColor}">
                    ${iconoHtml}
                </button>`;

            if (c < 5) celdasIzq += btnHtml;
            else celdasDer += btnHtml;
        }

        htmlFilas += `
            <div class="flex items-center gap-2 sm:gap-3">
                <span class="w-6 text-right text-[10px] font-mono font-extrabold text-slate-400">${letraFila}</span>
                <div class="flex items-center gap-1 sm:gap-1.5">${celdasIzq}</div>
                <span class="w-5 text-center text-[9px] font-mono text-slate-500 select-none">│</span>
                <div class="flex items-center gap-1 sm:gap-1.5">${celdasDer}</div>
                <span class="w-6 text-left text-[10px] font-mono font-extrabold text-slate-400">${letraFila}</span>
            </div>`;
    }

    cont.innerHTML = htmlFilas;
}

function seleccionarAsientoEnMapaCine(codigo) {
    estadoMapaCine.asientoSeleccionado = codigo;
    renderizarGridButacasCine();
    renderizarInspectorAsientoCine();
}

function renderizarInspectorAsientoCine() {
    const panel = document.getElementById("cine-inspector-detalle");
    if (!panel) return;

    const codigo = estadoMapaCine.asientoSeleccionado;
    const puedeAdmin = tienePermiso("eventos.publicar") || tienePermiso("asistencia.gestionar") || esAdminReal();

    if (!codigo) {
        panel.innerHTML = `
            <div class="text-center py-8 px-3 space-y-2">
                <div class="w-12 h-12 rounded-2xl bg-[#122652]/10 text-[#122652] flex items-center justify-center mx-auto text-xl">
                    <i class="fa-solid fa-hand-pointer text-[#F57C00]"></i>
                </div>
                <h4 class="text-sm font-extrabold text-[#122652]">Selecciona una butaca del auditorio</h4>
                <p class="text-xs text-[#5B6670] leading-relaxed">
                    Haz clic en cualquier asiento libre para apartarlo, o toca un asiento ocupado para consultar <b>quién lo apartó</b>, su horario de registro y su validación QR/GPS.
                </p>
            </div>`;
        return;
    }

    const info = estadoMapaCine.ocupadosPorCodigo[codigo];

    if (info && info.estado === "reservado_admin") {
        panel.innerHTML = `
            <div class="bg-white p-4 rounded-2xl border-2 border-[#F57C00] space-y-3 shadow-sm">
                <div class="flex items-center justify-between">
                    <span class="px-2.5 py-1 rounded-lg bg-[#F57C00] text-white font-mono font-extrabold text-sm">Asiento ${esc(codigo)}</span>
                    <span class="text-[10px] font-extrabold uppercase tracking-wider px-2.5 py-0.5 rounded-full bg-amber-100 text-amber-800">
                        <i class="fa-solid fa-crown mr-1"></i>Lugar Reservado
                    </span>
                </div>
                <div class="space-y-1 text-xs">
                    <p class="font-extrabold text-[#122652] text-sm">${esc(info.etiqueta || "Reservado: Maestro / Invitado Especial")}</p>
                    <p class="text-[#5B6670]"><b>Asignado por:</b> ${esc(info.reservadoPor || "Administración ETDA")}</p>
                    ${info.fechaApartado ? `<p class="text-[11px] text-slate-400">Fecha: ${esc(new Date(info.fechaApartado).toLocaleString("es-MX"))}</p>` : ""}
                </div>
                ${puedeAdmin ? `
                    <button type="button" onclick="gestionarReservaIndividualAdmin('${esc(codigo)}', 'liberar')"
                        class="w-full bg-red-600 hover:bg-red-700 text-white text-xs font-extrabold py-2.5 px-3 rounded-xl transition flex items-center justify-center gap-1.5">
                        <i class="fa-solid fa-unlock"></i> Liberar este Asiento Reservado
                    </button>
                ` : `<p class="text-[11px] text-amber-700 font-semibold">Este lugar está reservado para maestros o invitados especiales.</p>`}
            </div>`;
        return;
    }

    if (info) {
        const esMio = Boolean(info.esMio || estadoMapaCine.miAsiento === codigo);
        const qrOk = Boolean(info.qrValidado || info.asistio);
        const gpsOk = Boolean(info.gpsValidado || info.asistio);

        panel.innerHTML = `
            <div class="bg-white p-4 rounded-2xl border-2 ${esMio ? "border-[#10B981]" : "border-[#0093FF]"} space-y-3 shadow-sm">
                <div class="flex items-center justify-between">
                    <span class="px-2.5 py-1 rounded-lg bg-[#122652] text-[#FFB74D] font-mono font-extrabold text-sm">Asiento ${esc(codigo)}</span>
                    <span class="text-[10px] font-extrabold uppercase tracking-wider px-2.5 py-0.5 rounded-full ${esMio ? "bg-emerald-100 text-emerald-800" : "bg-sky-100 text-sky-800"}">
                        ${esMio ? "Tu Lugar Apartado" : "Apartado por Alumno"}
                    </span>
                </div>

                <div class="space-y-1.5 text-xs border-t border-slate-100 pt-2.5">
                    <div>
                        <span class="text-[10px] font-bold uppercase text-slate-400 block">Titular del Asiento</span>
                        <p class="font-extrabold text-[#122652] text-sm">${esc(info.nombre || "Alumno Registrado")}</p>
                        ${info.email ? `<p class="font-mono text-[11px] text-[#0093FF]">${esc(info.email)}</p>` : ""}
                    </div>
                    ${(info.matricula || info.carrera) ? `
                        <div class="bg-[#F4F7FB] p-2.5 rounded-xl border border-slate-200/80 space-y-0.5">
                            ${info.matricula ? `<p><b>Matrícula:</b> <span class="font-mono">${esc(info.matricula)}</span></p>` : ""}
                            ${info.carrera ? `<p><b>Programa:</b> ${esc(info.carrera)}</p>` : ""}
                            ${(info.grupos && info.grupos.length) ? `<p><b>Grupo:</b> ${esc(info.grupos.join(", "))}</p>` : ""}
                        </div>
                    ` : ""}
                    ${info.fechaApartado ? `<p class="text-[11px] text-slate-500"><i class="fa-regular fa-clock mr-1"></i><b>Apartado el:</b> ${esc(new Date(info.fechaApartado).toLocaleString("es-MX"))}</p>` : ""}
                </div>

                <div class="bg-slate-50 p-2.5 rounded-xl border border-slate-200 space-y-1 text-[11px]">
                    <span class="font-extrabold text-[#122652] block">Estado de Validación en Conferencia:</span>
                    <div class="flex items-center justify-between">
                        <span>1/2 Escaneo QR Oficial:</span>
                        <strong class="${qrOk ? "text-emerald-600" : "text-slate-400"}">${qrOk ? "✓ Validado" : "Pendiente"}</strong>
                    </div>
                    <div class="flex items-center justify-between">
                        <span>2/2 Permanencia GPS:</span>
                        <strong class="${gpsOk ? "text-emerald-600" : "text-slate-400"}">${gpsOk ? "✓ Confirmada (+1)" : "Pendiente"}</strong>
                    </div>
                </div>

                ${esMio ? `
                    <button type="button" onclick="cancelarMiAsientoDesdeMapa()"
                        class="w-full bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 text-xs font-extrabold py-2.5 px-3 rounded-xl transition flex items-center justify-center gap-1.5">
                        <i class="fa-solid fa-xmark"></i> Cancelar mi apartado (${esc(codigo)})
                    </button>
                ` : (puedeAdmin ? `
                    <button type="button" onclick="gestionarReservaIndividualAdmin('${esc(codigo)}', 'liberar')"
                        class="w-full bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 text-xs font-extrabold py-2 px-3 rounded-xl transition flex items-center justify-center gap-1.5">
                        <i class="fa-solid fa-user-xmark"></i> Liberar Asiento (Resolver Conflicto)
                    </button>
                ` : "")}
            </div>`;
        return;
    }

    const yaTieneOtroAsiento = Boolean(estadoMapaCine.miAsiento && estadoMapaCine.miAsiento !== codigo);
    panel.innerHTML = `
        <div class="bg-white p-4 rounded-2xl border-2 border-emerald-500/40 space-y-3 shadow-sm">
            <div class="flex items-center justify-between">
                <span class="px-2.5 py-1 rounded-lg bg-[#122652] text-white font-mono font-extrabold text-sm">Asiento ${esc(codigo)}</span>
                <span class="text-[10px] font-extrabold uppercase tracking-wider px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800">
                    Disponible
                </span>
            </div>

            ${yaTieneOtroAsiento ? `
                <div class="p-2.5 rounded-xl bg-amber-50 border border-amber-200 text-[11px] text-amber-900 font-semibold">
                    Ya tienes apartado el asiento <b>${esc(estadoMapaCine.miAsiento)}</b>. Cada alumno solo tiene derecho a apartar 1 lugar.
                </div>
            ` : `
                <p class="text-xs text-[#5B6670]">
                    Aparta el asiento <b>${esc(codigo)}</b> para asegurar tu lugar el día del evento.
                </p>
                <button type="button" onclick="confirmarApartadoAsientoWeb('${esc(codigo)}')"
                    class="w-full bg-[#10B981] hover:bg-emerald-600 text-white text-xs font-extrabold py-2.5 px-3 rounded-xl shadow-sm transition flex items-center justify-center gap-1.5">
                    <i class="fa-solid fa-check-circle"></i> Apartar Asiento ${esc(codigo)} (1 lugar)
                </button>
            `}

            ${puedeAdmin ? `
                <div class="pt-2.5 border-t border-slate-100 space-y-2">
                    <span class="block text-[10px] font-extrabold uppercase text-[#F57C00]">Acción de Administrador:</span>
                    <button type="button" onclick="gestionarReservaIndividualAdmin('${esc(codigo)}', 'reservar')"
                        class="w-full bg-[#122652] hover:bg-[#193773] text-white text-xs font-extrabold py-2 px-3 rounded-xl transition flex items-center justify-center gap-1.5">
                        <i class="fa-solid fa-crown text-[#F57C00]"></i> Reservar ${esc(codigo)} para Invitado / Maestro
                    </button>
                </div>
            ` : ""}
        </div>`;
}

async function confirmarApartadoAsientoWeb(codigoAsiento) {
    const eventoId = estadoMapaCine.eventoId;
    if (!eventoId) return;
    const emailYo = _obtenerEmailUsuarioWeb() || "usuario@unach.mx";
    const nombreYo = _obtenerNombreUsuarioWeb();

    try {
        try {
            await api(`/eventos/${eventoId}/registro`, {
                method: "POST",
                json: { asiento: codigoAsiento }
            });
        } catch (errApi) {
            if (!String(errApi.message || "").toLowerCase().includes("ya est")) {
                throw errApi;
            }
        }

        const syncState = _sincronizarEstadoAsientosConMetaEvento(eventosCache[eventoId]);
        delete syncState.liberados[emailYo];
        delete syncState.liberados[codigoAsiento];
        const prev = syncState.seats[emailYo] || {};
        syncState.seats[emailYo] = {
            ...prev,
            asiento: codigoAsiento,
            autoAsignado: false,
            ts: Date.now(),
            nombre: prev.nombre || nombreYo,
            email: emailYo,
            esMio: true,
            fechaApartado: new Date().toISOString()
        };
        _guardarEstadoAsientosLocal(eventoId, syncState.res, syncState.seats, syncState.liberados);

        estadoMapaCine.miAsiento = codigoAsiento;
        estadoMapaCine.asientoSeleccionado = codigoAsiento;
        await _persistirMetaAsientosEnEventoServidor(eventoId);
        await refrescarMapaAsientosEnVivo(false);
        toast(`¡Asiento ${codigoAsiento} apartado con éxito!`, "exito");
    } catch (err) {
        toast(err.message || "No se pudo apartar el asiento.", "error");
        await refrescarMapaAsientosEnVivo(false);
    }
}

async function cancelarMiAsientoDesdeMapa() {
    const eventoId = estadoMapaCine.eventoId;
    if (!eventoId) return;
    const emailYo = _obtenerEmailUsuarioWeb();
    const miCod = estadoMapaCine.miAsiento;
    try {
        try {
            await api(`/eventos/${eventoId}/registro`, { method: "DELETE" });
        } catch (_) {}

        const syncState = _sincronizarEstadoAsientosConMetaEvento(eventosCache[eventoId]);
        if (emailYo) {
            delete syncState.seats[emailYo];
            syncState.liberados[emailYo] = Date.now();
        }
        if (miCod) {
            delete syncState.ocupados?.[miCod];
            syncState.liberados[miCod] = Date.now();
        }
        _guardarEstadoAsientosLocal(eventoId, syncState.res, syncState.seats, syncState.liberados);

        if (miCod) delete estadoMapaCine.ocupadosPorCodigo[miCod];
        estadoMapaCine.miAsiento = null;
        if (eventosCache[eventoId]) {
            eventosCache[eventoId].miAsiento = null;
            eventosCache[eventoId].inscrito = false;
        }
        await _persistirMetaAsientosEnEventoServidor(eventoId);
        await refrescarMapaAsientosEnVivo(false);
        toast("Tu asiento fue liberado correctamente.", "info");
    } catch (err) {
        toast(err.message || "Error al liberar el asiento.", "error");
    }
}

async function gestionarReservaIndividualAdmin(codigoAsiento, accion) {
    const eventoId = estadoMapaCine.eventoId;
    if (!eventoId) return;
    const etiqueta = (document.getElementById("cine-admin-etiqueta-input")?.value || "Reservado: Maestro / Invitado Especial").trim();
    const emailYo = _obtenerEmailUsuarioWeb();
    const emailAdmin = emailYo || "Administración ETDA";

    const syncState = _sincronizarEstadoAsientosConMetaEvento(eventosCache[eventoId]);
    const infoActual = estadoMapaCine.ocupadosPorCodigo[codigoAsiento];

    if (accion === "liberar") {
        const ahoraMs = Date.now();
        delete syncState.res[codigoAsiento];
        syncState.liberados[codigoAsiento] = ahoraMs;

        const emailOcupante = (infoActual?.email || "").trim().toLowerCase();
        if (emailOcupante) {
            delete syncState.seats[emailOcupante];
            syncState.liberados[emailOcupante] = ahoraMs;
            // Si el asiento liberado pertenece al usuario con sesión activa en la Web, eliminar también su registro en BD
            if (emailYo && emailOcupante === emailYo) {
                try {
                    await api(`/eventos/${eventoId}/registro`, { method: "DELETE" });
                } catch (_) {}
                estadoMapaCine.miAsiento = null;
            }
        }

        Object.keys(syncState.seats).forEach(em => {
            const c = typeof syncState.seats[em] === "string" ? syncState.seats[em] : syncState.seats[em]?.asiento;
            if (c === codigoAsiento) {
                delete syncState.seats[em];
                syncState.liberados[em.toLowerCase()] = ahoraMs;
            }
        });

        delete estadoMapaCine.ocupadosPorCodigo[codigoAsiento];
    } else {
        delete syncState.liberados[codigoAsiento];
        syncState.res[codigoAsiento] = {
            etiqueta,
            reservadoPor: emailAdmin,
            fecha: new Date().toISOString()
        };
    }

    _guardarEstadoAsientosLocal(eventoId, syncState.res, syncState.seats, syncState.liberados);

    try {
        await api(`/eventos/${eventoId}/asientos-reservados`, {
            method: "POST",
            json: { asiento: codigoAsiento, accion, etiqueta }
        });
    } catch (_) {}

    // Primero actualizar la descripción CCMETA en caché y en el servidor ANTES de refrescar el mapa
    await _persistirMetaAsientosEnEventoServidor(eventoId);
    await refrescarMapaAsientosEnVivo(false);
    toast(
        accion === "liberar"
            ? `Asiento ${codigoAsiento} liberado y actualizado en tiempo real.`
            : `Asiento ${codigoAsiento} reservado para ${etiqueta}.`,
        "exito"
    );
}

async function reservarFilaPrimeraMaestros() {
    const eventoId = estadoMapaCine.eventoId;
    if (!eventoId) return;
    const etiqueta = (document.getElementById("cine-admin-etiqueta-input")?.value || "Reservado: Maestro / Invitado Especial").trim();
    const emailAdmin = _obtenerEmailUsuarioWeb() || "Administración ETDA";
    const candidatos = ["A-01", "A-02", "A-03", "A-04", "A-05"].filter(c => !estadoMapaCine.ocupadosPorCodigo[c]);
    if (!candidatos.length) {
        toast("Los primeros 5 asientos de la Fila A ya están ocupados o reservados.", "alerta");
        return;
    }

    const syncState = _sincronizarEstadoAsientosConMetaEvento(eventosCache[eventoId]);
    candidatos.forEach(c => {
        delete syncState.liberados[c];
        syncState.res[c] = {
            etiqueta,
            reservadoPor: emailAdmin,
            fecha: new Date().toISOString()
        };
    });
    _guardarEstadoAsientosLocal(eventoId, syncState.res, syncState.seats, syncState.liberados);

    try {
        await api(`/eventos/${eventoId}/asientos-reservados`, {
            method: "POST",
            json: { asientos: candidatos, accion: "reservar", etiqueta }
        });
    } catch (_) {}

    await _persistirMetaAsientosEnEventoServidor(eventoId);
    await refrescarMapaAsientosEnVivo(false);
    toast(`Reservados ${candidatos.join(", ")} para invitados/maestros.`, "exito");
}

async function liberarTodosReservadosAdmin() {
    const eventoId = estadoMapaCine.eventoId;
    if (!eventoId) return;
    const reservados = Object.values(estadoMapaCine.ocupadosPorCodigo)
        .filter(x => x.estado === "reservado_admin")
        .map(x => x.asiento);
    if (!reservados.length) {
        toast("No hay asientos reservados por administración en este evento.", "info");
        return;
    }

    const syncState = _sincronizarEstadoAsientosConMetaEvento(eventosCache[eventoId]);
    const ahoraMs = Date.now();
    reservados.forEach(cod => {
        delete syncState.res[cod];
        syncState.liberados[cod] = ahoraMs;
        delete estadoMapaCine.ocupadosPorCodigo[cod];
    });
    syncState.res = {};
    _guardarEstadoAsientosLocal(eventoId, syncState.res, syncState.seats, syncState.liberados);

    try {
        await api(`/eventos/${eventoId}/asientos-reservados`, {
            method: "POST",
            json: { asientos: reservados, accion: "liberar" }
        });
    } catch (_) {}

    await _persistirMetaAsientosEnEventoServidor(eventoId);
    await refrescarMapaAsientosEnVivo(false);
    toast("Se liberaron todos los asientos reservados de administración.", "exito");
}

// ============================================================================
// SINCRONIZACIÓN EN TIEMPO REAL DE LA MALLA POLIGONAL DE 4 ESQUINAS EN EVENTOS ACTIVOS
// ============================================================================
async function sincronizarMallaDeAuditorioEnEventosActivos(auditorioObj) {
    if (!auditorioObj || !auditorioObj.nombre) return;
    const nombreAudNorm = String(auditorioObj.nombre).trim().toLowerCase();
    const mallaCompacta = Array.isArray(auditorioObj.malla)
        ? auditorioObj.malla.map(v => [Number(Number(v.lat).toFixed(6)), Number(Number(v.lon).toFixed(6))])
        : [];
    const tolMetros = Number.isFinite(Number(auditorioObj.toleranciaMetros))
        ? Number(auditorioObj.toleranciaMetros)
        : 12;

    try {
        const esGestor = typeof tienePermiso === "function" && tienePermiso("eventos.publicar");
        if (!esGestor) return;
        const lista = await api("/eventos/feed?incluir_programados=true");
        if (Array.isArray(lista)) {
            lista.forEach(ev => { if (ev && ev.id) eventosCache[ev.id] = ev; });
        }
    } catch (_) {}

    const todosEventos = Object.values(eventosCache || {});
    for (const ev of todosEventos) {
        if (!ev || !ev.id) continue;
        const lugNorm = String(ev.lugar || "").trim().toLowerCase();
        if (!lugNorm) continue;
        const coincide = lugNorm === nombreAudNorm || lugNorm.includes(nombreAudNorm) || nombreAudNorm.includes(lugNorm);
        if (!coincide) continue;

        const meta = _extraerMetaDescripcion(ev);
        const hInd = _obtenerHorarioIndividualEvento(ev);
        const syncState = _sincronizarEstadoAsientosConMetaEvento(ev);
        const cap = Number(auditorioObj.asientos || ev.capacidadTotal || meta.cap || 100) || 100;

        const nuevaDesc = _construirDescripcionConMeta(ev.descripcion, {
            disp: hInd.disponibleDesde,
            fin: hInd.fechaFin,
            cap,
            ocup: meta.ocup || 0,
            res: syncState.res,
            seats: syncState.seats,
            liberados: syncState.liberados,
            malla: mallaCompacta,
            tol: tolMetros
        });
        ev.descripcion = nuevaDesc;

        try {
            await api(`/eventos/${ev.id}`, {
                method: "PUT",
                json: {
                    titulo: ev.titulo,
                    descripcion: nuevaDesc,
                    fechaHora: ev.fechaHora,
                    fechaFin: hInd.fechaFin,
                    disponibleDesde: hInd.disponibleDesde,
                    lugar: ev.lugar,
                    categoria: ev.categoria || null,
                    esGeneral: Boolean(ev.esGeneral),
                    idFacultadDestino: ev.idFacultadDestino || null,
                    idCarreraDestino: ev.idCarreraDestino || null,
                    gruposDestino: ev.gruposDestino || null
                }
            });
        } catch (_) {}
    }
}