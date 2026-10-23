// ============================================================================
// MÓDULO DE AUTENTICACIÓN: LOGIN, 2FA, COOKIES HTTPONLY Y REFRESH TOKEN
// ============================================================================

function mostrarVista(vista) {
    document.getElementById("view-auth")?.classList.toggle("hidden", vista !== "auth");
    document.getElementById("view-dashboard")?.classList.toggle("hidden", vista !== "dashboard");
    document.getElementById("navbar")?.classList.toggle("hidden", vista !== "dashboard");
    document.getElementById("footer-institucional")?.classList.toggle("hidden", vista !== "dashboard");
}

function toggleAuthForm(form) {
    document.getElementById("form-login")?.classList.toggle("hidden", form !== "login");
    document.getElementById("form-registro")?.classList.toggle("hidden", form !== "registro");
    document.getElementById("form-recuperar")?.classList.toggle("hidden", form !== "recuperar");
    ocultarAlertaAuth();
}

function mostrarAlertaAuth(mensaje, esError = true) {
    const el = document.getElementById("auth-alert");
    if (!el) return;
    el.className = `mb-4 p-3.5 rounded-xl text-xs font-semibold flex items-center gap-2 ${
        esError
            ? "bg-red-50 text-red-700 border border-red-200"
            : "bg-emerald-50 text-emerald-700 border border-emerald-200"
    }`;
    el.innerHTML = `<i class="fa-solid ${esError ? "fa-circle-exclamation" : "fa-circle-check"} text-sm"></i><span>${esc(mensaje)}</span>`;
    el.classList.remove("hidden");
}

function ocultarAlertaAuth() {
    document.getElementById("auth-alert")?.classList.add("hidden");
}

async function procesarLogin(e) {
    e.preventDefault();
    ocultarAlertaAuth();
    const email = document.getElementById("login-email").value.trim();
    const password = document.getElementById("login-password").value.trim();
    const btn = document.getElementById("btn-login");

    btn.disabled = true;
    btn.classList.add("opacity-70");
    try {
        const res = await fetch(`${API_BASE_URL}/auth/login`, {
            method: "POST",
            credentials: "include", // Recibe la Cookie HttpOnly si el 2FA sigue vigente
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, password })
        });

        const data = await res.json();
        if (!res.ok) throw new Error(data.detail || "Credenciales incorrectas.");

        if (data.requiere_2fa) {
            userState.temp2FAEmail = email;
            document.getElementById("modal-2fa").classList.remove("hidden");
            document.getElementById("otp-code")?.focus();
        } else if (data.access_token) {
            establecerSesionAutenticada(email, data);
        }
    } catch (err) {
        mostrarAlertaAuth(err.message, true);
    } finally {
        btn.disabled = false;
        btn.classList.remove("opacity-70");
    }
}

async function validarCodigo2FA() {
    const codigo = document.getElementById("otp-code").value.trim();
    const errDiv = document.getElementById("modal-2fa-error");
    errDiv.classList.add("hidden");

    try {
        const res = await fetch(`${API_BASE_URL}/auth/verificar-2fa`, {
            method: "POST",
            credentials: "include", // Recibe y almacena las Cookies HttpOnly 'access_token' y 'refresh_token'
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email: userState.temp2FAEmail, codigo })
        });

        const data = await res.json();
        if (!res.ok) throw new Error(data.detail || "Código incorrecto o expirado.");

        cerrarModal2FA();
        establecerSesionAutenticada(userState.temp2FAEmail, data);
    } catch (err) {
        errDiv.innerText = err.message;
        errDiv.classList.remove("hidden");
    }
}

function establecerSesionAutenticada(email, data) {
    // Exigencia 2 del PDF: PROHIBIDO guardar access_token o refresh_token en localStorage.
    // Solo se mantienen en memoria RAM de la pestaña actual y en la Cookie HttpOnly del navegador.
    localStorage.removeItem("access_token");
    localStorage.removeItem("refresh_token");

    userState.token = data.access_token || null;
    userState.refreshToken = data.refresh_token || null;
    userState.role = data.role || data.rol || "Usuario Regular";
    userState.email = email;
    userState.simulandoPermisos = null;

    if (Array.isArray(data.permisos) && data.permisos.length > 0) {
        // Traducir claves de backend (ej. eventos:escribir) o conservar permisos granulares del usuario
        const mapaLocal = obtenerMapaPermisosLocal();
        const guardados = email ? mapaLocal[`email:${email.toLowerCase().trim()}`] : null;
        userState.permisos = Array.isArray(guardados) ? guardados : traducirPermisosBackendAPanel(data.permisos, userState.role);
    } else {
        userState.permisos = null;
    }

    cargarPerfilYSistema();
}

function traducirPermisosBackendAPanel(clavesBackend, nombreRol) {
    const r = String(nombreRol || "").toLowerCase().trim();
    if (r === "administrador") return CATALOGO_PERMISOS.map(p => p.id);
    if (!Array.isArray(clavesBackend)) return permisosPorDefectoSegunRol(nombreRol);

    const resultado = new Set();
    if (clavesBackend.includes("eventos:escribir")) {
        resultado.add("eventos.publicar");
        resultado.add("redes.compartir");
        resultado.add("asistencia.gestionar");
        resultado.add("pasados.ver");
        resultado.add("evidencias.gestionar");
        resultado.add("catalogos.gestionar");
    }
    if (clavesBackend.includes("eventos:eliminar")) {
        resultado.add("eventos.eliminar");
    }
    if (clavesBackend.includes("estadisticas:leer")) {
        resultado.add("estadisticas.ver");
        resultado.add("pasados.ver");
    }
    if (clavesBackend.includes("auditoria:leer")) {
        resultado.add("seguridad.auditoria");
    }
    if (clavesBackend.includes("usuarios:gestionar") || clavesBackend.includes("roles:gestionar")) {
        resultado.add("usuarios.permisos");
    }
    return [...resultado];
}

function cerrarModal2FA() {
    document.getElementById("modal-2fa").classList.add("hidden");
    document.getElementById("otp-code").value = "";
}

async function procesarRegistro(e) {
    e.preventDefault();
    const nombre = document.getElementById("reg-nombre").value.trim();
    const email = document.getElementById("reg-email").value.trim();
    const password = document.getElementById("reg-password").value.trim();

    try {
        const res = await fetch(`${API_BASE_URL}/auth/registro`, {
            method: "POST",
            credentials: "include",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ nombre, email, password })
        });

        const data = await res.json();
        if (!res.ok) {
            let mensajeError = "Error al completar el registro.";
            if (Array.isArray(data.detail)) {
                mensajeError = data.detail[0].msg.replace("Value error, ", "");
            } else if (data.detail) {
                mensajeError = data.detail;
            }
            throw new Error(mensajeError);
        }

        mostrarAlertaAuth("¡Registro exitoso! Ya puedes iniciar sesión.", false);
        toggleAuthForm("login");
    } catch (err) {
        mostrarAlertaAuth(err.message, true);
    }
}

async function procesarRecuperacion(e) {
    e.preventDefault();
    const email = document.getElementById("rec-email").value.trim();

    try {
        const res = await fetch(`${API_BASE_URL}/auth/recuperar-password`, {
            method: "POST",
            credentials: "include",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email })
        });
        const data = await res.json();
        mostrarAlertaAuth(data.mensaje || "Se han enviado las instrucciones a tu correo.", false);
        toggleAuthForm("login");
    } catch (_) {
        mostrarAlertaAuth("No se pudo procesar la solicitud.", true);
    }
}

function abrirModalCambiarPassword() {
    document.getElementById("modal-password").classList.remove("hidden");
}

function cerrarModalPassword() {
    document.getElementById("modal-password").classList.add("hidden");
    document.getElementById("pass-actual").value = "";
    document.getElementById("pass-nueva").value = "";
}

async function procesarCambioPassword(e) {
    e.preventDefault();
    const password_actual = document.getElementById("pass-actual").value;
    const password_nueva = document.getElementById("pass-nueva").value;

    try {
        await api("/auth/cambiar-password", {
            method: "POST",
            json: { password_actual, password_nueva }
        });
        toast("Contraseña actualizada exitosamente.", "exito");
        cerrarModalPassword();
    } catch (err) {
        toast(err.message, "error");
    }
}

async function cerrarSesion() {
    // Solicitar al backend borrar las Cookies HttpOnly
    try {
        await fetch(`${API_BASE_URL}/auth/logout`, {
            method: "POST",
            credentials: "include"
        });
    } catch (_) {}

    localStorage.removeItem("access_token");
    localStorage.removeItem("refresh_token");
    localStorage.removeItem("user_email");
    localStorage.removeItem("user_role");
    localStorage.removeItem("user_permisos");

    userState = {
        token: null,
        refreshToken: null,
        email: null,
        role: null,
        permisos: null,
        simulandoPermisos: null,
        temp2FAEmail: null
    };
    mostrarVista("auth");
    toggleAuthForm("login");
}
