// ============================================================================
// INICIALIZADOR PRINCIPAL Y NAVEGACIÓN DE PESTAÑAS DEL PANEL
// ============================================================================

const PESTAÑAS = ["eventos", "pasados", "estadisticas", "catalogos", "usuarios", "auditoria"];

function cargarPerfilYSistema() {
    document.getElementById("user-email").innerText = userState.email || "";
    const av = document.getElementById("user-avatar-nav");
    if (av) av.innerText = obtenerIniciales(null, userState.email);

    sincronizarCatalogosDesdeServidor();
    mostrarVista("dashboard");
    configurarPermisosInterfaz();

    const activos = obtenerPermisosSesionActiva();
    if (activos.length === 1 && activos[0] === "seguridad.auditoria") {
        cambiarPestaña("auditoria");
    } else if (activos.length > 0 && !activos.includes("eventos.publicar") && !activos.includes("redes.compartir") && activos.includes("estadisticas.ver")) {
        cargarEventos();
        cambiarPestaña("estadisticas");
    } else {
        cambiarPestaña("eventos");
        cargarEventos();
    }
}

function cambiarPestaña(tab) {
    PESTAÑAS.forEach(t => {
        const btn = document.getElementById(`tab-btn-${t}`);
        const container = document.getElementById(`tab-${t}`);
        if (btn && container) {
            const active = t === tab;
            container.classList.toggle("hidden", !active);
            btn.classList.toggle("active", active);
        }
    });

    if (tab === "eventos") cargarEventos();
    if (tab === "pasados") cargarPasados();
    if (tab === "estadisticas") iniciarEstadisticas();
    if (tab === "catalogos") iniciarCatalogosFacultad();
    if (tab === "usuarios") cargarUsuariosAdmin();
    if (tab === "auditoria") cargarAuditoriaAdmin();
}

async function restaurarSesionDesdeCookieHttpOnly() {
    try {
        const perfil = await api("/auth/me");
        if (perfil && perfil.email) {
            userState.email = perfil.email;
            userState.role = perfil.rol || "Usuario Regular";
            if (typeof traducirPermisosBackendAPanel === "function" && Array.isArray(perfil.permisos)) {
                const mapaLocal = obtenerMapaPermisosLocal();
                const guardados = mapaLocal[`email:${perfil.email.toLowerCase().trim()}`];
                userState.permisos = Array.isArray(guardados) ? guardados : traducirPermisosBackendAPanel(perfil.permisos, userState.role);
            }
            cargarPerfilYSistema();
            return;
        }
    } catch (_) {
        /* Sin cookie activa o sesión expirada */
    }
    mostrarVista("auth");
}

window.addEventListener("DOMContentLoaded", () => {
    activarDropzone("ev-dropzone", "ev-archivos", (lista) => {
        estadoModalEvento.nuevos.push(...validarArchivos(lista));
        pintarArchivosModal();
    });
    activarDropzone("evid-dropzone", "evid-archivos", subirEvidencias);

    const gi = document.getElementById("ev-grupo-input");
    if (gi) {
        gi.addEventListener("keydown", (e) => {
            if (e.key === "Enter" || e.key === ",") {
                e.preventDefault();
                agregarGruposDesdeTexto(gi.value);
                gi.value = "";
            }
        });
        gi.addEventListener("change", () => {
            agregarGruposDesdeTexto(gi.value);
            gi.value = "";
        });
    }

    restaurarSesionDesdeCookieHttpOnly();
});
