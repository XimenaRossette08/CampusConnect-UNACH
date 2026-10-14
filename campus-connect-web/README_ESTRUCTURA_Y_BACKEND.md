# Guía Paso a Paso: Campus Connect UNACH — Panel Administrativo (ETDA)

## 1. Estructura de Archivos del Proyecto Web

```text
campus-connect-web/
├── index.html                  # Estructura principal, vistas y modales (con Chart.js y ExcelJS)
├── css/
│   └── styles.css              # Paleta oficial ETDA-UNACH (#122652, #0093FF, #F57C00, #F4F7FB)
├── js/
│   ├── config.js               # URL de la API, colores, categorías y catálogo de 9 permisos granulares
│   ├── utils.js                # Cliente fetch con JWT, protección anti-XSS, fechas y dropzones
│   ├── permissions.js          # Motor de permisos personalizados por usuario y vista previa en vivo
│   ├── auth.js                 # Login, verificación 2FA, registro, recuperación y contraseña
│   ├── eventos.js              # Cartelera activa, buscador, filtros por categoría ETDA y asistencia
│   ├── pasados.js              # Eventos finalizados y galería de evidencias
│   ├── social.js               # Publicación directa en Facebook/Instagram (1 clic) y Postal Unificada
│   ├── estadisticas.js         # Generador de Excel (.xlsx con gráficas incrustadas), PDF y PNG
│   ├── usuarios.js             # Gestión de permisos a medida por usuario (sin grupos ni roles rígidos)
│   ├── auditoria.js            # Bitácora de seguridad e historial de accesos
│   └── app.js                  # Inicializador general y control de pestañas
└── README_ESTRUCTURA_Y_BACKEND.md
```

---

## 2. Cómo Funciona el Nuevo Excel de Estadísticas (`.xlsx` con Gráficas)

En la pestaña **Estadísticas y Reportes**, el botón **Descargar Excel (.xlsx con Gráficas)** ahora genera un libro nativo de Microsoft Excel (`.xlsx`) usando `ExcelJS`:

* **Hoja 1 (`Dashboard y Gráficas`)**:
  * Encabezado institucional combinado con los colores oficiales de la UNACH (`#122652` y `#F57C00`).
  * Tabla ejecutiva de indicadores clave (KPIs) en dos bloques simétricos con bordes, colores alternados y columnas amplias para que ningún texto se corte.
  * **Gráficas incrustadas directamente dentro de la hoja de Excel** en alta resolución con marco institucional blanco y título.
* **Hoja 2 (`Tablas de Datos`)**:
  * Tablas desglosadas por Mes, Categoría, Carrera, Grupo y Eventos con mayor participación, con formato de tabla ejecutiva.

---

## 3. Cómo Funciona la Publicación en Facebook e Instagram (Sin el Error `127.0.0.1`)

### ¿Por qué antes salía el cuadro gris `127.0.0.1` en Facebook e Instagram solo abría el inicio?
Porque `facebook.com/sharer/sharer.php` solo comparte enlaces públicos de internet y ya no acepta texto precargado (`quote`). Como tu servidor corre en `http://127.0.0.1:8000` (local en tu PC), los servidores de Facebook no podían entrar a tu computadora para leer el flyer ni el título, mostrando solo `127.0.0.1`. Por su parte, la web de Instagram no tiene URL para precargar publicaciones.

### Solución 1: Publicar Automáticamente en 1 Clic (Se ve exactamente igual a la vista previa)
En el modal **Compartir en Redes Sociales** ahora tienes dos columnas: a la izquierda ves la **Vista Previa Exacta** (Cabecera UNACH + Flyer + Texto con emojis) y a la derecha los botones de **Publicación Automática**:

1. Haz clic en el botón **⚙️ Configurar API Meta** en la esquina superior derecha del modal.
2. Ingresa estos 3 datos de tu cuenta de Meta for Developers:
   * **Facebook Page ID**: El ID numérico de la Página de Facebook donde publicarás.
   * **Instagram Business Account ID**: El ID de la cuenta profesional de Instagram vinculada a esa página.
   * **Page Access Token**: El token de acceso de tu página con permisos `pages_manage_posts`, `pages_read_engagement` e `instagram_content_publish`.
3. Haz clic en **Guardar Credenciales**.
4. Al presionar **Publicar en Facebook**, **Publicar en Instagram** o **Publicar en Ambas Redes al Mismo Tiempo**:
   * El sistema toma el **archivo binario del flyer** desde tu servidor local `127.0.0.1` y lo sube directamente a `graph.facebook.com/{PAGE_ID}/photos` junto con el texto exacto.
   * Para Instagram, utiliza automáticamente la URL HTTPS generada en el servidor de imágenes de Meta (`fbcdn.net`) como puente, por lo que **también publica en Instagram desde `127.0.0.1` sin necesidad de comprar dominio ni configurar `ngrok`**.

### Solución 2: Para Cuentas Personales sin API (Postal Unificada + Portapapeles)
Si en algún momento quieres compartir en un perfil personal sin configurar tokens de Meta:
* El botón **Copiar Imagen (Ctrl+V)** copia la imagen directamente a tu portapapeles y nunca envía `127.0.0.1` a Facebook.
* El botón **Bajar Postal Unificada** une en una sola imagen HD el encabezado de la UNACH, el flyer del evento y todo el texto con emojis para que puedas subirla en 1 paso a Instagram o WhatsApp.
