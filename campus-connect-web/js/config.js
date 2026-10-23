// ============================================================================
// CONFIGURACIÓN GLOBAL, PALETA ETDA-UNACH Y CATÁLOGO DE PERMISOS GRANULARES
// ============================================================================

const API_BASE_URL = "http://127.0.0.1:8000";

// Limpieza automática de cualquier token legado en localStorage para cumplir al 100%
// con la prohibición de guardar JWT en localStorage (prevención de XSS)
try {
    localStorage.removeItem("access_token");
    localStorage.removeItem("refresh_token");
} catch (_) {}

// Paleta Oficial sincronizada con MainNavigationScreen.kt y EventosScreen.kt
const PALETA_ETDA = [
    "#122652", // EtdaNavy (Azul Profundo Oficial)
    "#F57C00", // EtdaOrange (Dorado / Naranja Vibrante Oficial)
    "#0093FF", // EtdaBlue (Azul Medio Oficial)
    "#10B981", // EtdaSuccess (Verde Esmeralda)
    "#0284C7", // Azul Talleres
    "#EA580C", // Naranja Deporte
    "#2563EB", // Azul Culturales
    "#475569"  // Gris Pizarra Institucional
];

// Categorías oficiales sincronizadas con EventosScreen.kt
const CATEGORIAS_ETDA = [
    { nombre: "Todos", icono: "fa-layer-group", color: "#122652" },
    { nombre: "Tutorías", icono: "fa-user-graduate", color: "#0093FF" },
    { nombre: "Conferencias", icono: "fa-microphone-lines", color: "#F57C00" },
    { nombre: "Talleres", icono: "fa-screwdriver-wrench", color: "#0284C7" },
    { nombre: "Deporte", icono: "fa-trophy", color: "#EA580C" },
    { nombre: "Culturales", icono: "fa-masks-theater", color: "#2563EB" },
    { nombre: "Académicos", icono: "fa-book-open", color: "#122652" }
];

// Catálogo de Permisos Individuales del Panel Web
const CATALOGO_PERMISOS = [
    {
        id: "catalogos.gestionar",
        claveBackend: "eventos:escribir",
        grupo: "Cartelera y Difusión",
        titulo: "Configurar Facultad, Carreras y Auditorios",
        etiquetaCorta: "Catálogos Facultad",
        descripcion: "Definir carreras, grupos y auditorios con su capacidad de asientos para las listas desplegables.",
        icono: "fa-building-columns",
        colorBadge: "bg-purple-50 text-purple-800 border-purple-200"
    },
    {
        id: "eventos.publicar",
        claveBackend: "eventos:escribir",
        grupo: "Cartelera y Difusión",
        titulo: "Publicar y Editar Eventos",
        etiquetaCorta: "Publicar/Editar",
        descripcion: "Crear nuevos eventos en la cartelera, modificar detalles y subir flyers o PDFs.",
        icono: "fa-calendar-plus",
        colorBadge: "bg-sky-50 text-sky-800 border-sky-200"
    },
    {
        id: "eventos.eliminar",
        claveBackend: "eventos:eliminar",
        grupo: "Cartelera y Difusión",
        titulo: "Eliminar Eventos",
        etiquetaCorta: "Eliminar Eventos",
        descripcion: "Borrar eventos publicados y sus archivos asociados de forma permanente.",
        icono: "fa-trash-can",
        colorBadge: "bg-red-50 text-red-700 border-red-200"
    },
    {
        id: "redes.compartir",
        claveBackend: "eventos:escribir",
        grupo: "Cartelera y Difusión",
        titulo: "Difusión en Redes Sociales",
        etiquetaCorta: "Redes Sociales",
        descripcion: "Abrir el generador de publicaciones, descargar flyers y compartir eventos en Facebook e Instagram.",
        icono: "fa-share-nodes",
        colorBadge: "bg-amber-50 text-amber-800 border-amber-200"
    },
    {
        id: "asistencia.gestionar",
        claveBackend: "eventos:escribir",
        grupo: "Seguimiento y Archivo",
        titulo: "Control de Asistencia y Listas PDF",
        etiquetaCorta: "Asistencia y PDF",
        descripcion: "Consultar alumnos inscritos, confirmar asistencia en tiempo real y descargar listas en PDF.",
        icono: "fa-user-check",
        colorBadge: "bg-emerald-50 text-emerald-800 border-emerald-200"
    },
    {
        id: "pasados.ver",
        claveBackend: "eventos:leer",
        grupo: "Seguimiento y Archivo",
        titulo: "Ver Eventos Pasados",
        etiquetaCorta: "Eventos Pasados",
        descripcion: "Acceder a la pestaña de eventos finalizados y consultar su resumen de cierre.",
        icono: "fa-clock-rotate-left",
        colorBadge: "bg-indigo-50 text-indigo-800 border-indigo-200"
    },
    {
        id: "evidencias.gestionar",
        claveBackend: "eventos:escribir",
        grupo: "Seguimiento y Archivo",
        titulo: "Subir y Gestionar Evidencias",
        etiquetaCorta: "Evidencias",
        descripcion: "Subir o eliminar fotografías y documentos probatorios en eventos finalizados.",
        icono: "fa-images",
        colorBadge: "bg-blue-50 text-blue-200 text-blue-800"
    },
    {
        id: "estadisticas.ver",
        claveBackend: "estadisticas:leer",
        grupo: "Inteligencia y Seguridad",
        titulo: "Ver y Descargar Estadísticas",
        etiquetaCorta: "Estadísticas",
        descripcion: "Acceder al panel de métricas y descargar reportes en Excel/CSV, PDF o imágenes.",
        icono: "fa-chart-column",
        colorBadge: "bg-orange-50 text-orange-800 border-orange-200"
    },
    {
        id: "seguridad.auditoria",
        claveBackend: "auditoria:leer",
        grupo: "Inteligencia y Seguridad",
        titulo: "Ver Seguridad y Auditoría",
        etiquetaCorta: "Seguridad",
        descripcion: "Consultar la bitácora inmutable de accesos, direcciones IP y actividad del sistema.",
        icono: "fa-shield-halved",
        colorBadge: "bg-slate-100 text-slate-800 border-slate-300"
    },
    {
        id: "usuarios.permisos",
        claveBackend: "usuarios:gestionar",
        grupo: "Inteligencia y Seguridad",
        titulo: "Administrar Usuarios, Roles y Permisos",
        etiquetaCorta: "Admin Permisos",
        descripcion: "Gestionar cuentas registradas, crear roles en BD y configurar permisos individuales.",
        icono: "fa-user-shield",
        colorBadge: "bg-[#122652]/10 text-[#122652] border-[#122652]/20"
    }
];

// Catálogo de permisos de la tabla 'permisos' de PostgreSQL para la creación explícita de Roles (Exigencia 6 del PDF)
const PERMISOS_BACKEND_ROLES = [
    { clave: "eventos:leer", titulo: "Leer Eventos (eventos:leer)", desc: "Consultar cartelera de eventos publicados" },
    { clave: "eventos:escribir", titulo: "Crear y Editar Eventos (eventos:escribir)", desc: "Publicar, modificar eventos, subir flyers/evidencias y gestionar asistencia" },
    { clave: "eventos:eliminar", titulo: "Eliminar Eventos (eventos:eliminar)", desc: "Borrar eventos y archivos permanentemente" },
    { clave: "estadisticas:leer", titulo: "Ver Estadísticas (estadisticas:leer)", desc: "Consultar métricas e indicadores de participación" },
    { clave: "usuarios:leer", titulo: "Ver Usuarios y Roles (usuarios:leer)", desc: "Consultar el directorio de usuarios registrados" },
    { clave: "usuarios:gestionar", titulo: "Gestionar Usuarios (usuarios:gestionar)", desc: "Asignar o revocar roles a usuarios" },
    { clave: "roles:gestionar", titulo: "Crear y Gestionar Roles (roles:gestionar)", desc: "Crear nuevos roles en BD y asignarles permisos" },
    { clave: "auditoria:leer", titulo: "Consultar Auditoría (auditoria:leer)", desc: "Ver bitácora inmutable de accesos e IPs" }
];

// Combinaciones rápidas de un clic para facilitar la asignación al administrador
const PLANTILLAS_RAPIDAS_PERMISOS = [
    {
        id: "solo_redes",
        nombre: "Solo Redes Sociales",
        icono: "fa-share-nodes",
        permisos: ["redes.compartir"]
    },
    {
        id: "solo_seguridad",
        nombre: "Solo Seguridad",
        icono: "fa-shield-halved",
        permisos: ["seguridad.auditoria"]
    },
    {
        id: "estadisticas_pasados",
        nombre: "Estadísticas + Eventos Pasados",
        icono: "fa-chart-pie",
        permisos: ["estadisticas.ver", "pasados.ver"]
    },
    {
        id: "coordinacion_eventos",
        nombre: "Auxiliar / Logística de Eventos",
        icono: "fa-calendar-check",
        permisos: ["eventos.publicar", "asistencia.gestionar", "pasados.ver", "evidencias.gestionar"]
    },
    {
        id: "acceso_total",
        nombre: "Acceso Total (Todos)",
        icono: "fa-crown",
        permisos: CATALOGO_PERMISOS.map(p => p.id)
    },
    {
        id: "ninguno",
        nombre: "Limpiar todo (Solo lectura)",
        icono: "fa-ban",
        permisos: []
    }
];

// Estado global de sesión en MEMORIA RAM (NUNCA guarda el JWT en localStorage para evitar XSS)
let userState = {
    token: null,        // Solo en RAM durante la sesión activa; la persistencia viaja en Cookie HttpOnly
    refreshToken: null, // Solo en RAM como respaldo para /auth/refresh
    email: null,
    role: null,
    permisos: null,
    simulandoPermisos: null,
    temp2FAEmail: null
};
