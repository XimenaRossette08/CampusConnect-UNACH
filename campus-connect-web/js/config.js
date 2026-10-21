// ============================================================================
// CONFIGURACIÓN GLOBAL, PALETA ETDA-UNACH Y CATÁLOGO DE PERMISOS GRANULARES
// ============================================================================

const API_BASE_URL = "http://127.0.0.1:8000";

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

// Catálogo de Permisos Individuales (Reemplaza a los roles rígidos)
const CATALOGO_PERMISOS = [
    {
        id: "catalogos.gestionar",
        grupo: "Cartelera y Difusión",
        titulo: "Configurar Facultad, Carreras y Auditorios",
        etiquetaCorta: "Catálogos Facultad",
        descripcion: "Definir carreras, grupos y auditorios con su capacidad de asientos para las listas desplegables.",
        icono: "fa-building-columns",
        colorBadge: "bg-purple-50 text-purple-800 border-purple-200"
    },
    {
        id: "eventos.publicar",
        grupo: "Cartelera y Difusión",
        titulo: "Publicar y Editar Eventos",
        etiquetaCorta: "Publicar/Editar",
        descripcion: "Crear nuevos eventos en la cartelera, modificar detalles y subir flyers o PDFs.",
        icono: "fa-calendar-plus",
        colorBadge: "bg-sky-50 text-sky-800 border-sky-200"
    },
    {
        id: "eventos.eliminar",
        grupo: "Cartelera y Difusión",
        titulo: "Eliminar Eventos",
        etiquetaCorta: "Eliminar Eventos",
        descripcion: "Borrar eventos publicados y sus archivos asociados de forma permanente.",
        icono: "fa-trash-can",
        colorBadge: "bg-red-50 text-red-700 border-red-200"
    },
    {
        id: "redes.compartir",
        grupo: "Cartelera y Difusión",
        titulo: "Difusión en Redes Sociales",
        etiquetaCorta: "Redes Sociales",
        descripcion: "Abrir el generador de publicaciones, descargar flyers y compartir eventos en Facebook e Instagram.",
        icono: "fa-share-nodes",
        colorBadge: "bg-amber-50 text-amber-800 border-amber-200"
    },
    {
        id: "asistencia.gestionar",
        grupo: "Seguimiento y Archivo",
        titulo: "Control de Asistencia y Listas PDF",
        etiquetaCorta: "Asistencia y PDF",
        descripcion: "Consultar alumnos inscritos, confirmar asistencia en tiempo real y descargar listas en PDF.",
        icono: "fa-user-check",
        colorBadge: "bg-emerald-50 text-emerald-800 border-emerald-200"
    },
    {
        id: "pasados.ver",
        grupo: "Seguimiento y Archivo",
        titulo: "Ver Eventos Pasados",
        etiquetaCorta: "Eventos Pasados",
        descripcion: "Acceder a la pestaña de eventos finalizados y consultar su resumen de cierre.",
        icono: "fa-clock-rotate-left",
        colorBadge: "bg-indigo-50 text-indigo-800 border-indigo-200"
    },
    {
        id: "evidencias.gestionar",
        grupo: "Seguimiento y Archivo",
        titulo: "Subir y Gestionar Evidencias",
        etiquetaCorta: "Evidencias",
        descripcion: "Subir o eliminar fotografías y documentos probatorios en eventos finalizados.",
        icono: "fa-images",
        colorBadge: "bg-blue-50 text-blue-800 border-blue-200"
    },
    {
        id: "estadisticas.ver",
        grupo: "Inteligencia y Seguridad",
        titulo: "Ver y Descargar Estadísticas",
        etiquetaCorta: "Estadísticas",
        descripcion: "Acceder al panel de métricas y descargar reportes en Excel/CSV, PDF o imágenes.",
        icono: "fa-chart-column",
        colorBadge: "bg-orange-50 text-orange-800 border-orange-200"
    },
    {
        id: "seguridad.auditoria",
        grupo: "Inteligencia y Seguridad",
        titulo: "Ver Seguridad y Auditoría",
        etiquetaCorta: "Seguridad",
        descripcion: "Consultar la bitácora inmutable de accesos, direcciones IP y actividad del sistema.",
        icono: "fa-shield-halved",
        colorBadge: "bg-slate-100 text-slate-800 border-slate-300"
    },
    {
        id: "usuarios.permisos",
        grupo: "Inteligencia y Seguridad",
        titulo: "Administrar Usuarios y Permisos",
        etiquetaCorta: "Admin Permisos",
        descripcion: "Gestionar cuentas registradas y configurar los permisos individuales de cada usuario.",
        icono: "fa-user-shield",
        colorBadge: "bg-[#122652]/10 text-[#122652] border-[#122652]/20"
    }
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
        nombre: "Logística de Eventos y Evidencias",
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

// Estado global de sesión del usuario autenticado
let userState = {
    token: localStorage.getItem("access_token") || null,
    email: localStorage.getItem("user_email") || null,
    role: localStorage.getItem("user_role") || null,
    permisos: JSON.parse(localStorage.getItem("user_permisos") || "null"),
    simulandoPermisos: null, // Permite al admin previsualizar cómo ve el sistema otro usuario
    temp2FAEmail: null
};
