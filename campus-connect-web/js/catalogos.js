// ============================================================================
// MÓDULO DE CATÁLOGOS INSTITUCIONALES Y CONFIGURACIÓN DE REGISTRO DE USUARIOS
// Administra:
// 1. Facultades / Escuelas (ej. ETDA, FCA, FI)
// 2. Carreras vinculadas a cada Facultad con sus Grupos específicos
// 3. Grupos Académicos (ej. 1A, 3B, 5A, 7N, 7O)
// 4. Auditorios y Salas con su capacidad exacta de asientos (cupo máximo)
// 5. Categorías oficiales de eventos
// 6. Datos Institucionales Estáticos del Expediente (Escuela, Modalidad, Ciclo)
// 7. Políticas y Opciones de Configuración al Crear una Cuenta de Usuario
// ============================================================================

const STORAGE_CATALOGOS_KEY = "cc_unach_catalogos_institucionales_v1";

// ============================================================================
// CONFIGURACIÓN DE MEMBRETADO Y PIE DE PÁGINA OFICIAL (DISEÑO FORMATO ETDA)
// ============================================================================
const LOGO_UNACH_IZQ_DEFAULT = "data:image/png;base64,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QBRgypSplsG++uyH6UefPPe5V+a9/RkFsF347vsVFODRx59pbm13fEoB3nrnXVFCFOCjT7554KHHCwZxHD9g1ccff3bLLX948835hADPwV8ffeaLz78KLP/vvlsyZcqhr7zyRjRWIioy4jnXdykChFg4orpmESMoFnIlkXBgvliuNXT40H4Deh957NFHHHXsueefe8+fH1K1SHN7+58feLCjM0MBeAEBBl4WHv3bXw87/JDPv/5s4WcfIR47PogyF4mGeYH3KeEFTpIEz3cCNgSxIMTwnpwIRpTuet8zvggoQiibLXIYfvxx25133pHJZyZPnrx+/brXX/9g5arlXV35dCZrOzYA7Vffd8LEsWPGj1uxalWf/vWaBl0Z/fvvVwkcqJqEoDsmhBkGFribMTBs2xQhZFgOAChyyLLcYUNGRCPRL7/4evXqtYUCGIab6uzKF/yVK1e/8cYbPIdthzU2NmeyluvBX//6+HsLPiwWDU3lC0VnxfI1TzzxxMABg1evWqvrLgCsXbt2yZIlpkm6uqzPP/+8X7+KU06ZuWPHDgbAEKVBVxFFDAAoZhALhwqFHEaAMRSLRTWsmpZf26tX3779LQc+/fTzt96ZH43GH37k0dLSEt+n6WwBcSBJOBQKXTfn8vPPP7expXHJku/eXTB/87YGAEAIHMexbAuACrwQxKB6BngvVmzMIU3TXJe5rjts2DDb9yVJamxsZMTL59J9+9bV1JQDAAMGgCzbHzNm+Phxwz/5ZOl551198qyTvv7mi3whvWPHjn/2fEnCwRZIUySe5zs7O3v3rrEcSOeypmWpIZCIiDgsSLxpO8tWrKQADKGCbsTjim4AIF6SQzu2N1aWDXUd/5NPPisrrTjrrJMXL16TzeYFobStrUNRtEiEA1A8z3McIASKxWJgc/2SWltb+/TppShgWnDAAeNefe2V/ffd7+35b5911nmV1cl58+bFopLnAscBISCKuKQk0p7KRMLR2poyCjB6v31G7bdPa3PXa6+8WJpMVJWXhUKqqqgAlAExDEPTtN1/bi84YZm2osqEwPDhg/cdNZggKBQgEgHigesQWeayuXw8Fs1kMoC5kliMAhACEyeO+eCD98aPHxuOqPUD+mzfuvWfPV/kgVIa7LJ93y8Wi4IA6aypaVpgljoOmKYtCiBLys6dnatXbdx330GGbnd1+dEo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";
const LOGO_EDUCACION_DER_DEFAULT = "data:image/png;base64,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";

const MEMBRETE_ETDA_POR_DEFECTO = {
    institucion: "UNIVERSIDAD AUTÓNOMA DE CHIAPAS",
    dependencia: "ESCUELA DE TECNOLOGÍAS DIGITALES APLICADAS CAMPUS I",
    subtituloDocumento: "LISTA OFICIAL DE ASISTENCIA A EVENTO ACADÉMICO",
    direccionPie: "BOULEVARD DR. BELISARIO DOMÍNGUEZ, KM 1081, SIN NÚMERO/TERÁN TUXTLA GUTIÉRREZ, MÉXICO",
    contactoPie: "C.P. 29020 / TEL. 961 615 42 49 y 961 615 13 26 ext. 106 y 108, https://tecnologiastuxtla.unach.mx, e-mail: @unach.mx",
    dominioPie: "unach.mx",
    mostrarFirma: true,
    firmaResponsable: "Coordinación Académica ETDA — UNACH",
    cargoFirma: "",
    colorBarraSuperior: "#2B3E6B",
    colorBarraInferior: "#C49B27",
    logoIzquierdo: LOGO_UNACH_IZQ_DEFAULT,
    logoDerecho: LOGO_EDUCACION_DER_DEFAULT
};

const DATOS_INSTITUCIONALES_POR_DEFECTO = {
    escuela: "Escuela de Tecnologías Digitales Aplicadas (ETDA)",
    modalidadSede: "Escolarizada (Campus I, Tuxtla Gutiérrez)",
    cicloEscolar: "Agosto - Diciembre 2026"
};

const CONFIGURACION_REGISTRO_POR_DEFECTO = {
    permitirRegistroEgresados: true,
    requerirMatricula: true,
    longitudMinMatricula: 6,
    longitudMaxMatricula: 15,
    soloDigitosMatricula: false,
    requerirCarreraGrupo: true,
    validarContraCatalogo: true,
    restringirDominioCorreo: false,
    dominiosPermitidos: ["unach.mx", "alumnos.unach.mx"],
    longitudMinPassword: 8
};

const CATALOGO_POR_DEFECTO = {
    membrete: JSON.parse(JSON.stringify(MEMBRETE_ETDA_POR_DEFECTO)),
    datosInstitucionales: JSON.parse(JSON.stringify(DATOS_INSTITUCIONALES_POR_DEFECTO)),
    configuracionRegistro: JSON.parse(JSON.stringify(CONFIGURACION_REGISTRO_POR_DEFECTO)),
    facultadActiva: "ETDA",
    facultades: [
        { id: "ETDA", nombre: "Escuela de Tecnologías Digitales Aplicadas (ETDA)" },
        { id: "FCA", nombre: "Facultad de Contaduría y Administración (FCA)" },
        { id: "FI", nombre: "Facultad de Ingeniería (FI)" }
    ],
    carreras: [
        {
            id: "LIDTS",
            nombre: "Lic. en Ingeniería en Desarrollo y Tecnologías de Software",
            facultadId: "ETDA",
            grupos: ["1A", "1B", "3A", "3B", "5A", "5B", "7N", "7O", "8A"]
        },
        {
            id: "LSC",
            nombre: "Lic. en Sistemas Computacionales",
            facultadId: "FCA",
            grupos: ["1A", "1B", "3A", "5A", "7A", "9A"]
        },
        {
            id: "IC",
            nombre: "Ingeniería Civil",
            facultadId: "FI",
            grupos: ["1A", "2A", "3A", "4A", "5A", "6A", "7A", "8A"]
        }
    ],
    grupos: ["1A", "1B", "2A", "3A", "3B", "4A", "5A", "5B", "6A", "7A", "7N", "7O", "8A", "9A"],
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

// ============================================================================
// FUNCIONES DE SANITIZACIÓN CONTRA XSS Y NORMALIZACIÓN
// ============================================================================
function sanitizarUrlImagenSegura(urlRaw, fallback) {
    if (!urlRaw || typeof urlRaw !== "string") return fallback;
    const limpio = urlRaw.trim();
    if (/^data:image\/(png|jpeg|jpg|webp|gif);base64,[A-Za-z0-9+/=\s]+$/i.test(limpio)) {
        return limpio;
    }
    if (/^https?:\/\/[^\s"'<>]+$/i.test(limpio)) {
        return limpio;
    }
    return fallback;
}

function sanitizarColorHex(colorRaw, fallback = "#2B3E6B") {
    if (!colorRaw || typeof colorRaw !== "string") return fallback;
    const limpio = colorRaw.trim();
    return /^#[0-9A-Fa-f]{6}$/.test(limpio) ? limpio : fallback;
}

function normalizarConfiguracionRegistro(cfgRaw) {
    const base = { ...CONFIGURACION_REGISTRO_POR_DEFECTO, ...(cfgRaw || {}) };
    let dominios = base.dominiosPermitidos;
    if (typeof dominios === "string") {
        dominios = dominios.split(",").map(d => d.trim().toLowerCase().replace(/^@+/, "")).filter(Boolean);
    } else if (Array.isArray(dominios)) {
        dominios = dominios.map(d => String(d).trim().toLowerCase().replace(/^@+/, "")).filter(Boolean);
    } else {
        dominios = [...CONFIGURACION_REGISTRO_POR_DEFECTO.dominiosPermitidos];
    }
    const minMat = Math.max(1, Math.min(25, parseInt(base.longitudMinMatricula, 10) || 6));
    const maxMat = Math.max(minMat, Math.min(30, parseInt(base.longitudMaxMatricula, 10) || 15));
    const minPwd = Math.max(6, Math.min(64, parseInt(base.longitudMinPassword, 10) || 8));

    return {
        permitirRegistroEgresados: base.permitirRegistroEgresados !== false,
        requerirMatricula: base.requerirMatricula !== false,
        longitudMinMatricula: minMat,
        longitudMaxMatricula: maxMat,
        soloDigitosMatricula: Boolean(base.soloDigitosMatricula),
        requerirCarreraGrupo: base.requerirCarreraGrupo !== false,
        validarContraCatalogo: base.validarContraCatalogo !== false,
        restringirDominioCorreo: Boolean(base.restringirDominioCorreo),
        dominiosPermitidos: dominios.length ? Array.from(new Set(dominios)) : ["unach.mx"],
        longitudMinPassword: minPwd
    };
}

function normalizarCarrerasConGrupos(carrerasRaw, gruposGlobales) {
    const fallbackGrupos = Array.isArray(gruposGlobales) && gruposGlobales.length
        ? gruposGlobales
        : [...CATALOGO_POR_DEFECTO.grupos];
    const lista = Array.isArray(carrerasRaw) && carrerasRaw.length
        ? carrerasRaw
        : JSON.parse(JSON.stringify(CATALOGO_POR_DEFECTO.carreras));
    return lista.map(c => {
        const def = CATALOGO_POR_DEFECTO.carreras.find(
            d => d.nombre.toLowerCase() === (c.nombre || "").toLowerCase()
        );
        const gruposCarrera = Array.isArray(c.grupos) && c.grupos.length
            ? c.grupos.map(g => String(g).trim().toUpperCase()).filter(Boolean)
            : (def && Array.isArray(def.grupos) ? [...def.grupos] : [...fallbackGrupos]);
        return {
            id: c.id || (c.nombre || "CARRERA").slice(0, 12).toUpperCase().replace(/\s+/g, "_"),
            nombre: String(c.nombre || "").trim(),
            facultadId: String(c.facultadId || "ETDA").trim().toUpperCase(),
            grupos: Array.from(new Set(gruposCarrera))
        };
    }).filter(c => Boolean(c.nombre));
}

function sincronizarGruposGlobalesDesdeCarreras(catalogoObj) {
    const setGrupos = new Set((catalogoObj.grupos || []).map(g => String(g).trim().toUpperCase()).filter(Boolean));
    (catalogoObj.carreras || []).forEach(c => {
        (c.grupos || []).forEach(g => {
            const limpio = String(g).trim().toUpperCase();
            if (limpio) setGrupos.add(limpio);
        });
    });
    catalogoObj.grupos = Array.from(setGrupos).sort();
}

let estadoCatalogos = cargarCatalogosLocal();

function cargarCatalogosLocal() {
    try {
        const raw = localStorage.getItem(STORAGE_CATALOGOS_KEY);
        if (!raw) return JSON.parse(JSON.stringify(CATALOGO_POR_DEFECTO));
        const parsed = JSON.parse(raw);
        const gruposBase = Array.isArray(parsed.grupos) && parsed.grupos.length ? parsed.grupos : [...CATALOGO_POR_DEFECTO.grupos];
        const carrerasNorm = normalizarCarrerasConGrupos(parsed.carreras, gruposBase);
        const obj = {
            facultadActiva: parsed.facultadActiva || CATALOGO_POR_DEFECTO.facultadActiva,
            facultades: Array.isArray(parsed.facultades) && parsed.facultades.length ? parsed.facultades : [...CATALOGO_POR_DEFECTO.facultades],
            carreras: carrerasNorm,
            grupos: gruposBase,
            auditorios: Array.isArray(parsed.auditorios) && parsed.auditorios.length ? parsed.auditorios : [...CATALOGO_POR_DEFECTO.auditorios],
            categorias: Array.isArray(parsed.categorias) && parsed.categorias.length ? parsed.categorias : [...CATALOGO_POR_DEFECTO.categorias],
            datosInstitucionales: {
                ...DATOS_INSTITUCIONALES_POR_DEFECTO,
                ...(parsed.datosInstitucionales || {})
            },
            configuracionRegistro: normalizarConfiguracionRegistro(parsed.configuracionRegistro),
            membrete: { ...MEMBRETE_ETDA_POR_DEFECTO, ...(parsed.membrete || {}) }
        };
        sincronizarGruposGlobalesDesdeCarreras(obj);
        return obj;
    } catch (_) {
        return JSON.parse(JSON.stringify(CATALOGO_POR_DEFECTO));
    }
}

async function sincronizarCatalogosDesdeServidor() {
    try {
        const remoto = await api("/catalogos/facultad");
        if (remoto && typeof remoto === "object" && (Array.isArray(remoto.auditorios) || Array.isArray(remoto.carreras))) {
            const gruposRemotos = remoto.grupos?.length ? remoto.grupos : estadoCatalogos.grupos;
            estadoCatalogos = {
                facultadActiva: remoto.facultadActiva || estadoCatalogos.facultadActiva,
                facultades: remoto.facultades?.length ? remoto.facultades : estadoCatalogos.facultades,
                carreras: normalizarCarrerasConGrupos(remoto.carreras?.length ? remoto.carreras : estadoCatalogos.carreras, gruposRemotos),
                grupos: gruposRemotos,
                auditorios: remoto.auditorios?.length ? remoto.auditorios : estadoCatalogos.auditorios,
                categorias: remoto.categorias?.length ? remoto.categorias : estadoCatalogos.categorias,
                datosInstitucionales: {
                    ...DATOS_INSTITUCIONALES_POR_DEFECTO,
                    ...(estadoCatalogos.datosInstitucionales || {}),
                    ...(remoto.datosInstitucionales || {})
                },
                configuracionRegistro: normalizarConfiguracionRegistro({
                    ...(estadoCatalogos.configuracionRegistro || {}),
                    ...(remoto.configuracionRegistro || {})
                }),
                membrete: { ...MEMBRETE_ETDA_POR_DEFECTO, ...(estadoCatalogos.membrete || {}), ...(remoto.membrete || {}) }
            };
            sincronizarGruposGlobalesDesdeCarreras(estadoCatalogos);
            localStorage.setItem(STORAGE_CATALOGOS_KEY, JSON.stringify(estadoCatalogos));
        }
    } catch (_) {
        /* Si el servidor no está disponible aún, se mantiene el catálogo local */
    }
    poblarSelectoresRegistroUsuario();
}

async function persistirCatalogos(mensajeToast = "Catálogo institucional actualizado.") {
    sincronizarGruposGlobalesDesdeCarreras(estadoCatalogos);
    estadoCatalogos.configuracionRegistro = normalizarConfiguracionRegistro(estadoCatalogos.configuracionRegistro);
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
    poblarSelectoresRegistroUsuario();
    if (mensajeToast && typeof toast === "function") toast(mensajeToast, "exito");
}

function buscarAuditorioPorNombre(nombreLugar) {
    if (!nombreLugar) return null;
    const limpio = nombreLugar.trim().toLowerCase();
    return (estadoCatalogos.auditorios || []).find(a =>
        a.nombre.toLowerCase() === limpio ||
        limpio.startsWith(a.nombre.toLowerCase())
    ) || null;
}

// ============================================================================
// SINCRONIZACIÓN DE OPCIONES DE CREACIÓN DE CUENTA EN EL FORMULARIO WEB (#form-registro)
// ============================================================================
function formatearEtiquetaSemestreGrupoWeb(codigoGrupo) {
    const limpio = String(codigoGrupo || "").trim().toUpperCase();
    const match = /^([0-9]+)([A-Z]+)$/.exec(limpio);
    if (match) {
        return `${match[1]}° Semestre • Grupo ${match[2]} (${limpio})`;
    }
    return limpio;
}

function poblarSelectoresRegistroUsuario() {
    const cfg = normalizarConfiguracionRegistro(estadoCatalogos.configuracionRegistro);
    const carreras = estadoCatalogos.carreras || [];

    // Mostrar u ocultar selector de Alumno Egresado según política administrativa
    const bloqueTipoCuenta = document.getElementById("reg-bloque-tipo-cuenta");
    const chkEgresado = document.getElementById("reg-es-egresado");
    if (bloqueTipoCuenta) {
        bloqueTipoCuenta.classList.toggle("hidden", !cfg.permitirRegistroEgresados);
    }
    if (!cfg.permitirRegistroEgresados && chkEgresado) {
        chkEgresado.value = "activo";
        alternarTipoUsuarioRegistroWeb("activo");
    }

    // Configurar restricciones de Matrícula y Contraseña en el formulario de registro
    const inpMatricula = document.getElementById("reg-matricula");
    const lblMatricula = document.getElementById("reg-label-matricula");
    const contMatricula = document.getElementById("reg-contenedor-matricula");
    if (inpMatricula) {
        inpMatricula.required = cfg.requerirMatricula;
        inpMatricula.minLength = cfg.longitudMinMatricula;
        inpMatricula.maxLength = cfg.longitudMaxMatricula;
        inpMatricula.placeholder = cfg.soloDigitosMatricula
            ? `Ej. 100016853 (${cfg.longitudMinMatricula}-${cfg.longitudMaxMatricula} dígitos)`
            : `Ej. 100016853 (${cfg.longitudMinMatricula}-${cfg.longitudMaxMatricula} caracteres)`;
    }
    if (lblMatricula) {
        lblMatricula.textContent = cfg.requerirMatricula ? "Matrícula Estudiantil *" : "Matrícula Estudiantil (Opcional)";
    }
    if (contMatricula) {
        contMatricula.classList.remove("hidden");
    }

    // Configurar etiqueta de correo institucional según dominios permitidos
    const lblEmail = document.getElementById("reg-label-email");
    const inpEmail = document.getElementById("reg-email");
    if (lblEmail) {
        if (cfg.restringirDominioCorreo && cfg.dominiosPermitidos.length) {
            lblEmail.textContent = `Correo Institucional (@${cfg.dominiosPermitidos.join(", @")})`;
        } else {
            lblEmail.textContent = "Correo Institucional o Personal";
        }
    }
    if (inpEmail && cfg.dominiosPermitidos.length) {
        inpEmail.placeholder = `usuario@${cfg.dominiosPermitidos[0]}`;
    }

    const inpPassword = document.getElementById("reg-password");
    if (inpPassword) {
        inpPassword.minLength = cfg.longitudMinPassword;
        inpPassword.placeholder = `Mínimo ${cfg.longitudMinPassword} caracteres`;
    }

    // Poblar selector de Programa Educativo (Carrera)
    const selCarrera = document.getElementById("reg-carrera");
    const contCarreraGrupo = document.getElementById("reg-contenedor-carrera-grupo");
    if (contCarreraGrupo) {
        contCarreraGrupo.classList.toggle("hidden", !cfg.requerirCarreraGrupo && carreras.length === 0);
    }
    if (selCarrera) {
        const valorPrevio = selCarrera.value;
        selCarrera.required = cfg.requerirCarreraGrupo;
        selCarrera.innerHTML = carreras.map(c =>
            `<option value="${esc(c.nombre)}" data-facultad="${esc(c.facultadId || "ETDA")}" ${c.nombre === valorPrevio ? "selected" : ""}>
                ${esc(c.nombre)} (${esc(c.facultadId || "ETDA")})
            </option>`
        ).join("");
        actualizarGruposRegistroSegunCarrera();
    }
    const tipoActual = document.getElementById("reg-es-egresado")?.value === "egresado" ? "egresado" : "activo";
    alternarTipoUsuarioRegistroWeb(tipoActual);
}

function actualizarGruposRegistroSegunCarrera() {
    const selCarrera = document.getElementById("reg-carrera");
    const selGrupo = document.getElementById("reg-semestre-grupo");
    const inpFacultad = document.getElementById("reg-id-facultad");
    if (!selCarrera || !selGrupo) return;

    const nombreCarrera = selCarrera.value;
    const carreraObj = (estadoCatalogos.carreras || []).find(
        c => c.nombre.toLowerCase() === (nombreCarrera || "").toLowerCase()
    ) || (estadoCatalogos.carreras || [])[0];

    if (inpFacultad) {
        inpFacultad.value = carreraObj?.facultadId || estadoCatalogos.facultadActiva || "ETDA";
    }

    const gruposDisponibles = Array.isArray(carreraObj?.grupos) && carreraObj.grupos.length
        ? carreraObj.grupos
        : (estadoCatalogos.grupos || ["1A"]);

    const grupoPrevio = selGrupo.value;
    selGrupo.innerHTML = gruposDisponibles.map(g =>
        `<option value="${esc(g)}" ${g === grupoPrevio ? "selected" : ""}>${esc(formatearEtiquetaSemestreGrupoWeb(g))}</option>`
    ).join("");
}

function alternarTipoUsuarioRegistroWeb(tipo) {
    const esEgresado = tipo === "egresado";
    const cfg = normalizarConfiguracionRegistro(estadoCatalogos.configuracionRegistro);
    const carreras = estadoCatalogos.carreras || [];

    const inpHidden = document.getElementById("reg-es-egresado");
    if (inpHidden) inpHidden.value = esEgresado ? "egresado" : "activo";

    const btnActivo = document.getElementById("reg-tab-activo");
    const btnEgresado = document.getElementById("reg-tab-egresado");
    if (btnActivo && btnEgresado) {
        btnActivo.className = esEgresado
            ? "flex-1 py-2 rounded-xl text-xs font-extrabold text-[#122652] bg-transparent transition"
            : "flex-1 py-2 rounded-xl text-xs font-extrabold text-white bg-[#122652] shadow-sm transition";
        btnEgresado.className = esEgresado
            ? "flex-1 py-2 rounded-xl text-xs font-extrabold text-white bg-[#0093FF] shadow-sm transition"
            : "flex-1 py-2 rounded-xl text-xs font-extrabold text-[#122652] bg-transparent transition";
    }

    // Ocultar Matrícula, Licenciatura (Carrera) y Grado/Grupo cuando es Alumno Egresado
    const contMatricula = document.getElementById("reg-contenedor-matricula");
    const inpMatricula = document.getElementById("reg-matricula");
    if (contMatricula) {
        contMatricula.classList.toggle("hidden", esEgresado);
    }
    if (inpMatricula) {
        inpMatricula.required = !esEgresado && cfg.requerirMatricula;
        if (esEgresado) inpMatricula.value = "";
    }

    const contCarreraGrupo = document.getElementById("reg-contenedor-carrera-grupo");
    const selCarrera = document.getElementById("reg-carrera");
    const selGrupo = document.getElementById("reg-semestre-grupo");
    if (contCarreraGrupo) {
        contCarreraGrupo.classList.toggle("hidden", esEgresado || (!cfg.requerirCarreraGrupo && carreras.length === 0));
    }
    if (selCarrera) {
        selCarrera.required = !esEgresado && cfg.requerirCarreraGrupo;
    }
    if (selGrupo) {
        selGrupo.required = !esEgresado && cfg.requerirCarreraGrupo;
    }

    const lblEmail = document.getElementById("reg-label-email");
    if (lblEmail) {
        if (esEgresado) {
            lblEmail.textContent = "Correo Electrónico Principal *";
        } else if (cfg.restringirDominioCorreo && cfg.dominiosPermitidos.length) {
            lblEmail.textContent = `Correo Institucional (@${cfg.dominiosPermitidos.join(", @")})`;
        } else {
            lblEmail.textContent = "Correo Institucional o Personal";
        }
    }

    const camposEgresado = document.getElementById("reg-campos-egresado");
    if (camposEgresado) {
        camposEgresado.classList.toggle("hidden", !esEgresado);
        camposEgresado.querySelectorAll("input").forEach(inp => {
            inp.required = esEgresado;
            if (!esEgresado) inp.value = "";
        });
    }
}

// ============================================================================
// RENDERIZADO DE LA PESTAÑA "CATÁLOGOS DE FACULTAD" Y MODAL DE AUDITORIOS
// ============================================================================
async function iniciarCatalogosFacultad() {
    await sincronizarCatalogosDesdeServidor();
    renderizarVistaCatalogos();
}

function generarHtmlListaAuditorios() {
    return (estadoCatalogos.auditorios || []).map((a, idx) => `
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

function renderizarVistaCatalogos() {
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

    // 2. Lista de Auditorios tanto en la pestaña principal como en el modal rápido (sin IDs duplicados)
    const htmlAuditorios = generarHtmlListaAuditorios();
    const contAuditoriosTab = document.getElementById("lista-cat-auditorios");
    if (contAuditoriosTab) contAuditoriosTab.innerHTML = htmlAuditorios;
    const contAuditoriosModal = document.getElementById("modal-lista-cat-auditorios");
    if (contAuditoriosModal) contAuditoriosModal.innerHTML = htmlAuditorios;

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

    // 4. Lista de Carreras / Programas Educativos CON SUS GRUPOS ESPECÍFICOS
    const contCarreras = document.getElementById("lista-cat-carreras");
    if (contCarreras) {
        contCarreras.innerHTML = (estadoCatalogos.carreras || []).map((c, idx) => {
            const gruposCarrera = Array.isArray(c.grupos) ? c.grupos : [];
            const chipsGruposHtml = gruposCarrera.map((g, gIdx) => `
                <span class="inline-flex items-center gap-1 bg-white border border-slate-300 text-[#122652] text-[10px] font-extrabold px-2 py-0.5 rounded-lg">
                    <span>${esc(g)}</span>
                    <button type="button" onclick="eliminarGrupoDeCarreraCatalogo(${idx}, ${gIdx})" class="text-slate-400 hover:text-red-600 font-bold ml-0.5" title="Quitar grupo de esta carrera">×</button>
                </span>
            `).join("");
            return `
            <div class="p-3 rounded-xl border border-slate-200 bg-slate-50 space-y-2">
                <div class="flex items-center justify-between gap-2">
                    <div>
                        <span class="inline-block text-[10px] font-extrabold bg-sky-100 text-[#0093FF] px-2 py-0.5 rounded mr-1.5">${esc(c.facultadId || "UNACH")}</span>
                        <span class="text-xs font-bold text-[#122652]">${esc(c.nombre)}</span>
                    </div>
                    <div class="flex items-center gap-1 shrink-0">
                        <button type="button" onclick="editarGruposDeCarreraCatalogo(${idx})"
                            class="text-[11px] font-bold text-[#0093FF] bg-white hover:bg-sky-50 border border-sky-200 px-2 py-1 rounded-lg transition"
                            title="Agregar o editar grupos de esta carrera">
                            <i class="fa-solid fa-users-rectangle mr-1"></i>+ Grupos
                        </button>
                        <button type="button" onclick="eliminarCarreraCatalogo(${idx})" class="text-red-500 hover:text-red-700 text-xs px-2 py-1" title="Eliminar carrera">
                            <i class="fa-solid fa-trash-can"></i>
                        </button>
                    </div>
                </div>
                <div class="flex flex-wrap items-center gap-1.5 pt-1 border-t border-slate-200/70">
                    <span class="text-[10px] font-extrabold text-slate-500 mr-1"><i class="fa-solid fa-layer-group text-[#F57C00] mr-1"></i>Grupos de la carrera:</span>
                    ${chipsGruposHtml || `<span class="text-[10px] text-slate-400 italic">Sin grupos asignados (haz clic en + Grupos)</span>`}
                </div>
            </div>
            `;
        }).join("");
    }

    // 5. Chips de Grupos Académicos Globales
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

    // 5b. Datos Institucionales Estáticos (Escuela, Modalidad/Sede, Ciclo Escolar)
    const datosInst = {
        ...DATOS_INSTITUCIONALES_POR_DEFECTO,
        ...(estadoCatalogos.datosInstitucionales || {})
    };
    const inpEsc = document.getElementById("cat-inst-escuela");
    const inpMod = document.getElementById("cat-inst-modalidad");
    const inpCiclo = document.getElementById("cat-inst-ciclo");
    if (inpEsc) inpEsc.value = datosInst.escuela || "";
    if (inpMod) inpMod.value = datosInst.modalidadSede || "";
    if (inpCiclo) inpCiclo.value = datosInst.cicloEscolar || "";

    // 5c. Configuración de Opciones y Reglas al Crear una Cuenta de Usuario
    const cfgReg = normalizarConfiguracionRegistro(estadoCatalogos.configuracionRegistro);
    const setChk = (id, val) => { const el = document.getElementById(id); if (el) el.checked = Boolean(val); };
    const setVal = (id, val) => { const el = document.getElementById(id); if (el) el.value = val; };
    setChk("cat-cfg-permitir-egresados", cfgReg.permitirRegistroEgresados);
    setChk("cat-cfg-requerir-matricula", cfgReg.requerirMatricula);
    setChk("cat-cfg-solo-digitos-matricula", cfgReg.soloDigitosMatricula);
    setChk("cat-cfg-requerir-carrera-grupo", cfgReg.requerirCarreraGrupo);
    setChk("cat-cfg-validar-catalogo", cfgReg.validarContraCatalogo);
    setChk("cat-cfg-restringir-dominio", cfgReg.restringirDominioCorreo);
    setVal("cat-cfg-dominios", (cfgReg.dominiosPermitidos || []).join(", "));
    setVal("cat-cfg-min-matricula", cfgReg.longitudMinMatricula);
    setVal("cat-cfg-max-matricula", cfgReg.longitudMaxMatricula);
    setVal("cat-cfg-min-password", cfgReg.longitudMinPassword);

    renderizarResumenMembreteEnCatalogos();

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
function agregarAuditorioCatalogo(e, origen = "tab") {
    e.preventDefault();
    const prefijo = origen === "modal" ? "modal-cat-nuevo-aud" : "cat-nuevo-aud";
    const elNombre = document.getElementById(`${prefijo}-nombre`);
    const elAsientos = document.getElementById(`${prefijo}-asientos`);
    const elUbicacion = document.getElementById(`${prefijo}-ubicacion`);

    const nombre = (elNombre?.value || "").trim();
    const asientos = parseInt(elAsientos?.value || "", 10);
    const ubicacion = (elUbicacion?.value || "").trim() || "Campus I UNACH";

    if (!nombre || isNaN(asientos) || asientos <= 0 || asientos > 20000) {
        toast("Indica el nombre del auditorio/sala y un número válido de asientos (1 a 20,000).", "alerta");
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

    if (elNombre) elNombre.value = "";
    if (elAsientos) elAsientos.value = "";
    if (elUbicacion) elUbicacion.value = "";
    persistirCatalogos(`Auditorio "${nombre}" (${asientos} asientos) guardado en el catálogo.`);
}

function editarAsientosAuditorio(idx) {
    const aud = estadoCatalogos.auditorios[idx];
    if (!aud) return;
    const nuevo = prompt(`¿Cuántos asientos tiene "${aud.nombre}"?`, String(aud.asientos));
    if (nuevo === null) return;
    const num = parseInt(nuevo, 10);
    if (isNaN(num) || num <= 0 || num > 20000) {
        toast("Ingresa un número de asientos válido entre 1 y 20,000.", "alerta");
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
    const existente = estadoCatalogos.facultades.find(f => f.id.toLowerCase() === siglas.toLowerCase());
    if (existente) {
        existente.nombre = nombre;
    } else {
        estadoCatalogos.facultades.push({ id: siglas, nombre });
    }
    document.getElementById("cat-nueva-fac-id").value = "";
    document.getElementById("cat-nueva-fac-nombre").value = "";
    persistirCatalogos(`Facultad "${siglas}" guardada.`);
}

function eliminarFacultadCatalogo(idx) {
    if (estadoCatalogos.facultades.length <= 1) {
        toast("Debe existir al menos una facultad activa en el catálogo.", "alerta");
        return;
    }
    estadoCatalogos.facultades.splice(idx, 1);
    persistirCatalogos("Facultad eliminada del catálogo.");
}

function agregarCarreraCatalogo(e) {
    e.preventDefault();
    const facultadId = document.getElementById("cat-nueva-carrera-facultad").value;
    const nombre = document.getElementById("cat-nueva-carrera-nombre").value.trim();
    const rawGrupos = (document.getElementById("cat-nueva-carrera-grupos")?.value || "").trim();
    if (!nombre) return;

    const gruposIngresados = rawGrupos
        ? rawGrupos.split(",").map(g => g.trim().toUpperCase()).filter(Boolean)
        : [...(estadoCatalogos.grupos || ["1A", "3A", "5A", "7N"])];

    const existente = estadoCatalogos.carreras.find(c => c.nombre.toLowerCase() === nombre.toLowerCase());
    if (existente) {
        existente.facultadId = facultadId;
        existente.grupos = Array.from(new Set([...(existente.grupos || []), ...gruposIngresados]));
    } else {
        estadoCatalogos.carreras.push({
            id: nombre.slice(0, 12).toUpperCase().replace(/\s+/g, "_"),
            nombre,
            facultadId,
            grupos: Array.from(new Set(gruposIngresados))
        });
    }

    document.getElementById("cat-nueva-carrera-nombre").value = "";
    const inpGrupos = document.getElementById("cat-nueva-carrera-grupos");
    if (inpGrupos) inpGrupos.value = "";
    persistirCatalogos(`Carrera "${nombre}" guardada con sus grupos en ${facultadId}.`);
}

function editarGruposDeCarreraCatalogo(idx) {
    const carrera = estadoCatalogos.carreras[idx];
    if (!carrera) return;
    const actuales = Array.isArray(carrera.grupos) ? carrera.grupos.join(", ") : "";
    const respuesta = prompt(
        `Configura los grupos de la carrera "${carrera.nombre}" (separados por coma):`,
        actuales || "1A, 1B, 3A, 3B, 5A, 5B, 7N, 7O, 8A"
    );
    if (respuesta === null) return;
    const nuevosGrupos = respuesta
        .split(",")
        .map(g => g.trim().toUpperCase())
        .filter(Boolean);
    if (!nuevosGrupos.length) {
        toast("La carrera debe tener al menos un grupo asignado.", "alerta");
        return;
    }
    carrera.grupos = Array.from(new Set(nuevosGrupos));
    persistirCatalogos(`Grupos de "${carrera.nombre}" actualizados.`);
}

function eliminarGrupoDeCarreraCatalogo(carreraIdx, grupoIdx) {
    const carrera = estadoCatalogos.carreras[carreraIdx];
    if (!carrera || !Array.isArray(carrera.grupos)) return;
    carrera.grupos.splice(grupoIdx, 1);
    persistirCatalogos(`Grupo eliminado de "${carrera.nombre}".`);
}

function eliminarCarreraCatalogo(idx) {
    if (estadoCatalogos.carreras.length <= 1) {
        toast("Debe existir al menos un programa educativo (carrera) para el registro de alumnos.", "alerta");
        return;
    }
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

function guardarDatosInstitucionalesCatalogo(e) {
    if (e) e.preventDefault();
    const escuela = (document.getElementById("cat-inst-escuela")?.value || "").trim()
        || DATOS_INSTITUCIONALES_POR_DEFECTO.escuela;
    const modalidadSede = (document.getElementById("cat-inst-modalidad")?.value || "").trim()
        || DATOS_INSTITUCIONALES_POR_DEFECTO.modalidadSede;
    const cicloEscolar = (document.getElementById("cat-inst-ciclo")?.value || "").trim()
        || DATOS_INSTITUCIONALES_POR_DEFECTO.cicloEscolar;

    estadoCatalogos.datosInstitucionales = {
        escuela,
        modalidadSede,
        cicloEscolar
    };
    persistirCatalogos("Datos institucionales estáticos del expediente guardados y sincronizados con la App.");
}

function guardarConfiguracionRegistroCatalogo(e) {
    if (e) e.preventDefault();
    const getChk = id => Boolean(document.getElementById(id)?.checked);
    const getVal = id => (document.getElementById(id)?.value || "").trim();

    const minMat = parseInt(getVal("cat-cfg-min-matricula"), 10) || 6;
    const maxMat = parseInt(getVal("cat-cfg-max-matricula"), 10) || 15;
    const minPwd = parseInt(getVal("cat-cfg-min-password"), 10) || 8;

    if (minMat > maxMat) {
        toast("La longitud mínima de la matrícula no puede ser mayor que la longitud máxima.", "alerta");
        return;
    }

    estadoCatalogos.configuracionRegistro = normalizarConfiguracionRegistro({
        permitirRegistroEgresados: getChk("cat-cfg-permitir-egresados"),
        requerirMatricula: getChk("cat-cfg-requerir-matricula"),
        soloDigitosMatricula: getChk("cat-cfg-solo-digitos-matricula"),
        requerirCarreraGrupo: getChk("cat-cfg-requerir-carrera-grupo"),
        validarContraCatalogo: getChk("cat-cfg-validar-catalogo"),
        restringirDominioCorreo: getChk("cat-cfg-restringir-dominio"),
        dominiosPermitidos: getVal("cat-cfg-dominios"),
        longitudMinMatricula: minMat,
        longitudMaxMatricula: maxMat,
        longitudMinPassword: minPwd
    });

    persistirCatalogos("Opciones y reglas de creación de cuentas de usuario guardadas y sincronizadas.");
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

    const selFac = document.getElementById("ev-facultad");
    if (selFac) {
        const facPrevia = evActual?.idFacultadDestino || estadoCatalogos.facultadActiva || "";
        selFac.innerHTML = `<option value="">Todas las facultades</option>` +
            (estadoCatalogos.facultades || []).map(f =>
                `<option value="${esc(f.id)}" ${f.id.toLowerCase() === facPrevia.toLowerCase() ? "selected" : ""}>${esc(f.id)} — ${esc(f.nombre)}</option>`
            ).join("");
    }

    actualizarCarrerasSegunFacultadModal(evActual?.idCarreraDestino || "");
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
    if (typeof estadoModalEvento === "undefined" || !Array.isArray(estadoModalEvento.grupos)) return;
    const idx = estadoModalEvento.grupos.findIndex(x => x.toLowerCase() === grupo.toLowerCase());
    if (idx >= 0) {
        estadoModalEvento.grupos.splice(idx, 1);
    } else {
        estadoModalEvento.grupos.push(grupo);
    }
    renderizarSelectorVisualGruposModal();
    if (typeof pintarChipsGrupos === "function") pintarChipsGrupos();
}

function seleccionarTodosLosGruposModal() {
    if (typeof estadoModalEvento === "undefined") return;
    estadoModalEvento.grupos = [...estadoCatalogos.grupos];
    renderizarSelectorVisualGruposModal();
    if (typeof pintarChipsGrupos === "function") pintarChipsGrupos();
}

function limpiarGruposModal() {
    if (typeof estadoModalEvento === "undefined") return;
    estadoModalEvento.grupos = [];
    renderizarSelectorVisualGruposModal();
    if (typeof pintarChipsGrupos === "function") pintarChipsGrupos();
}

function renderizarSelectorVisualGruposModal() {
    const cont = document.getElementById("ev-selector-botones-grupos");
    if (!cont) return;
    const gruposActivos = (typeof estadoModalEvento !== "undefined" && Array.isArray(estadoModalEvento.grupos))
        ? estadoModalEvento.grupos
        : [];
    const seleccionados = gruposActivos.map(g => g.toUpperCase());

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

// ============================================================================
// GESTIÓN Y VISTA PREVIA DEL MEMBRETADO Y PIE DE PÁGINA (FORMATO ETDA)
// ============================================================================
function obtenerMembreteActivo() {
    if (!estadoCatalogos.membrete) {
        estadoCatalogos.membrete = JSON.parse(JSON.stringify(MEMBRETE_ETDA_POR_DEFECTO));
    }
    return {
        ...MEMBRETE_ETDA_POR_DEFECTO,
        ...estadoCatalogos.membrete,
        colorBarraSuperior: sanitizarColorHex(estadoCatalogos.membrete.colorBarraSuperior, "#2B3E6B"),
        colorBarraInferior: sanitizarColorHex(estadoCatalogos.membrete.colorBarraInferior, "#C49B27"),
        logoIzquierdo: sanitizarUrlImagenSegura(estadoCatalogos.membrete.logoIzquierdo, LOGO_UNACH_IZQ_DEFAULT),
        logoDerecho: sanitizarUrlImagenSegura(estadoCatalogos.membrete.logoDerecho, LOGO_EDUCACION_DER_DEFAULT)
    };
}

function abrirModalMembreteAsistencia() {
    const m = obtenerMembreteActivo();
    const setVal = (id, v) => { const el = document.getElementById(id); if (el) el.value = v || ""; };
    setVal("mem-institucion", m.institucion);
    setVal("mem-dependencia", m.dependencia);
    setVal("mem-subtitulo", m.subtituloDocumento);
    setVal("mem-direccion-pie", m.direccionPie);
    setVal("mem-contacto-pie", m.contactoPie);
    setVal("mem-dominio-pie", m.dominioPie);
    const chkFirma = document.getElementById("mem-mostrar-firma");
    if (chkFirma) chkFirma.checked = m.mostrarFirma !== false;
    setVal("mem-firma-responsable", m.firmaResponsable);
    setVal("mem-firma-cargo", m.cargoFirma || "");
    setVal("mem-color-sup", m.colorBarraSuperior || "#2B3E6B");
    setVal("mem-color-inf", m.colorBarraInferior || "#C49B27");
    alternarVisibilidadCamposFirmaModal();

    actualizarVistaPreviaMembreteEnVivo();
    document.getElementById("modal-membrete-etda")?.classList.remove("hidden");
}

function cerrarModalMembreteAsistencia() {
    document.getElementById("modal-membrete-etda")?.classList.add("hidden");
}

function leerCamposModalMembrete() {
    const mActual = obtenerMembreteActivo();
    const getVal = (id, fallback) => {
        const el = document.getElementById(id);
        return el ? el.value : fallback;
    };
    return {
        institucion: getVal("mem-institucion", mActual.institucion).trim() || MEMBRETE_ETDA_POR_DEFECTO.institucion,
        dependencia: getVal("mem-dependencia", mActual.dependencia).trim(),
        subtituloDocumento: getVal("mem-subtitulo", mActual.subtituloDocumento).trim(),
        direccionPie: getVal("mem-direccion-pie", mActual.direccionPie).trim(),
        contactoPie: getVal("mem-contacto-pie", mActual.contactoPie).trim(),
        dominioPie: getVal("mem-dominio-pie", mActual.dominioPie).trim(),
        mostrarFirma: document.getElementById("mem-mostrar-firma") ? document.getElementById("mem-mostrar-firma").checked : (mActual.mostrarFirma !== false),
        firmaResponsable: getVal("mem-firma-responsable", mActual.firmaResponsable).trim(),
        cargoFirma: getVal("mem-firma-cargo", mActual.cargoFirma || "").trim(),
        colorBarraSuperior: sanitizarColorHex(getVal("mem-color-sup", mActual.colorBarraSuperior), "#2B3E6B"),
        colorBarraInferior: sanitizarColorHex(getVal("mem-color-inf", mActual.colorBarraInferior), "#C49B27"),
        logoIzquierdo: sanitizarUrlImagenSegura(mActual.logoIzquierdo, LOGO_UNACH_IZQ_DEFAULT),
        logoDerecho: sanitizarUrlImagenSegura(mActual.logoDerecho, LOGO_EDUCACION_DER_DEFAULT)
    };
}

function actualizarVistaPreviaMembreteEnVivo() {
    const m = leerCamposModalMembrete();
    const contHeader = document.getElementById("preview-membrete-header");
    const contFooter = document.getElementById("preview-membrete-footer");
    if (contHeader) {
        contHeader.innerHTML = `
            <div style="display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 14px;background:#fff;">
                <div style="display:flex;align-items:center;gap:12px;">
                    <img src="${esc(m.logoIzquierdo)}" alt="Logo UNACH" style="height:48px;width:auto;object-fit:contain;">
                    <div style="line-height:1.25;">
                        <div style="font-size:13px;font-weight:800;color:#243763;letter-spacing:0.2px;">${esc(m.institucion)}</div>
                        <div style="font-size:10.5px;font-weight:500;color:#243763;margin-top:2px;">${esc(m.dependencia)}</div>
                    </div>
                </div>
                <img src="${esc(m.logoDerecho)}" alt="Educación que transforma" style="height:44px;width:auto;object-fit:contain;">
            </div>
            <div style="height:5px;width:100%;background:${esc(m.colorBarraSuperior)};"></div>
        `;
    }
    const contFirma = document.getElementById("preview-membrete-firma");
    if (contFirma) {
        if (m.mostrarFirma && (m.firmaResponsable || m.cargoFirma)) {
            contFirma.classList.remove("hidden");
            contFirma.innerHTML = `
                <div style="padding:10px 14px 12px 14px;background:#fff;text-align:center;">
                    <div style="width:210px;border-top:1.5px solid #243763;margin:0 auto 4px auto;"></div>
                    ${m.firmaResponsable ? `<div style="font-size:10.5px;font-weight:800;color:#243763;">${esc(m.firmaResponsable)}</div>` : ""}
                    ${m.cargoFirma ? `<div style="font-size:9.5px;color:#5B6670;">${esc(m.cargoFirma)}</div>` : ""}
                </div>
            `;
        } else {
            contFirma.classList.add("hidden");
            contFirma.innerHTML = "";
        }
    }
    if (contFooter) {
        contFooter.innerHTML = `
            <div style="height:5px;width:100%;background:${esc(m.colorBarraInferior)};"></div>
            <div style="padding:10px 14px;background:#fff;color:#243763;font-size:9.5px;line-height:1.4;">
                <div style="display:flex;justify-content:space-between;align-items:baseline;gap:10px;">
                    <span style="font-weight:500;">${esc(m.direccionPie)}</span>
                    <strong style="font-size:14px;font-weight:800;color:#243763;white-space:nowrap;">${esc(m.dominioPie)}</strong>
                </div>
                <div style="margin-top:3px;color:#243763;">${esc(m.contactoPie)}</div>
            </div>
        `;
    }
    renderizarResumenMembreteEnCatalogos();
}

function cambiarLogoMembrete(lado, inputEl) {
    const file = inputEl?.files?.[0];
    if (!file) return;
    const tiposPermitidos = ["image/png", "image/jpeg", "image/webp", "image/gif"];
    if (!tiposPermitidos.includes(file.type)) {
        toast("Selecciona un archivo de imagen válido (PNG, JPG, WEBP o GIF).", "alerta");
        inputEl.value = "";
        return;
    }
    if (file.size > 1024 * 1024) {
        toast("El archivo de logo no debe superar 1 MB.", "alerta");
        inputEl.value = "";
        return;
    }
    const reader = new FileReader();
    reader.onload = function(evt) {
        if (!estadoCatalogos.membrete) {
            estadoCatalogos.membrete = JSON.parse(JSON.stringify(MEMBRETE_ETDA_POR_DEFECTO));
        }
        const resultadoSeguro = sanitizarUrlImagenSegura(
            evt.target.result,
            lado === "izquierdo" ? LOGO_UNACH_IZQ_DEFAULT : LOGO_EDUCACION_DER_DEFAULT
        );
        if (lado === "izquierdo") {
            estadoCatalogos.membrete.logoIzquierdo = resultadoSeguro;
        } else {
            estadoCatalogos.membrete.logoDerecho = resultadoSeguro;
        }
        actualizarVistaPreviaMembreteEnVivo();
        toast("Logo actualizado en la vista previa. Recuerda hacer clic en Guardar Membretado.", "info");
    };
    reader.readAsDataURL(file);
}

async function guardarMembreteAsistencia(e) {
    if (e) e.preventDefault();
    estadoCatalogos.membrete = leerCamposModalMembrete();
    await persistirCatalogos("Membretado y pie de página (Formato ETDA) guardados correctamente.");
    renderizarResumenMembreteEnCatalogos();
    cerrarModalMembreteAsistencia();
}

async function restaurarMembreteETDAPorDefecto() {
    if (!confirm("¿Restaurar el membretado, logos y pie de página originales del Formato ETDA?")) return;
    estadoCatalogos.membrete = JSON.parse(JSON.stringify(MEMBRETE_ETDA_POR_DEFECTO));
    abrirModalMembreteAsistencia();
    await persistirCatalogos("Membretado restaurado al Formato ETDA oficial.");
}

function renderizarResumenMembreteEnCatalogos() {
    const cont = document.getElementById("resumen-membrete-catalogos");
    if (!cont) return;
    const m = obtenerMembreteActivo();
    cont.innerHTML = `
        <div class="border border-slate-200 rounded-2xl overflow-hidden bg-white shadow-sm">
            <div class="p-3 flex items-center justify-between gap-3 bg-white">
                <div class="flex items-center gap-3">
                    <img src="${esc(m.logoIzquierdo)}" alt="UNACH" class="h-10 w-auto object-contain">
                    <div>
                        <p class="text-xs font-extrabold text-[#243763]">${esc(m.institucion)}</p>
                        <p class="text-[11px] text-[#243763]">${esc(m.dependencia)}</p>
                    </div>
                </div>
                <img src="${esc(m.logoDerecho)}" alt="Educación que transforma" class="h-9 w-auto object-contain">
            </div>
            <div style="height:4px;background:${esc(m.colorBarraSuperior)};"></div>
            <div class="px-4 py-2 bg-[#F4F7FB] text-[11px] text-[#5B6670] flex items-center justify-between">
                <span><b>Subtítulo:</b> ${esc(m.subtituloDocumento)}</span>
                <span><b>Línea de firma:</b> ${m.mostrarFirma !== false ? esc(m.firmaResponsable || "Activa") : "Oculta (Desactivada)"}</span>
            </div>
            <div style="height:4px;background:${esc(m.colorBarraInferior)};"></div>
            <div class="p-2.5 bg-white text-[10px] text-[#243763] flex items-center justify-between gap-2">
                <div class="truncate">
                    <p class="font-medium truncate">${esc(m.direccionPie)}</p>
                    <p class="truncate text-slate-600">${esc(m.contactoPie)}</p>
                </div>
                <strong class="text-xs font-extrabold text-[#243763] shrink-0">${esc(m.dominioPie)}</strong>
            </div>
        </div>
    `;
}

function alternarVisibilidadCamposFirmaModal() {
    const chk = document.getElementById("mem-mostrar-firma");
    const box = document.getElementById("contenedor-campos-firma-membrete");
    if (chk && box) {
        box.classList.toggle("opacity-50", !chk.checked);
        box.querySelectorAll("input").forEach(inp => { inp.disabled = !chk.checked; });
    }
    actualizarVistaPreviaMembreteEnVivo();
}

document.addEventListener("DOMContentLoaded", () => {
    poblarSelectoresRegistroUsuario();
    sincronizarCatalogosDesdeServidor();
});