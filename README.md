<div align="center">

  <img src="assets/images/logo.png" alt="Logo CampusConnect UNACH" width="120" height="120" />

  # 🎓 CampusConnect - UNACH
  **Plataforma integral móvil y web para la comunidad universitaria de la Universidad Autónoma de Chiapas**

  <!-- Insignias de Tecnologías y Estado -->
  [![Estado](https://img.shields.io/badge/Estado-En%20Desarrollo-brightgreen?style=for-the-badge)]()
  [![Institución](https://img.shields.io/badge/Instituci%C3%B3n-UNACH-003366?style=for-the-badge)]()
  [![Android](https://img.shields.io/badge/Plataforma-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
  [![Kotlin](https://img.shields.io/badge/App-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
  [![Python](https://img.shields.io/badge/Backend-Python-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://www.python.org/)
  [![JavaScript](https://img.shields.io/badge/Web-JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)](https://developer.mozilla.org/es/docs/Web/JavaScript)
  [![Licencia](https://img.shields.io/badge/Licencia-MIT-yellow?style=for-the-badge)](LICENSE)

  <br/>

  <!-- Botón de Descarga Directa del APK -->
  [![Descargar APK](https://img.shields.io/badge/Descargar-APK%20Android-success?style=for-the-badge&logo=android&logoColor=white)](URL_DIRECTA_DE_TU_RELEASE_AQUI)

  <p align="center">
    <a href="#-descripción-del-proyecto">Descripción</a> •
    <a href="#-descarga-e-instalación-del-apk">Instalación APK</a> •
    <a href="#-características-principales">Características</a> •
    <a href="#-capturas-de-pantalla">Capturas</a> •
    <a href="#-tecnologías-utilizadas">Tecnologías</a> •
    <a href="#-estructura-del-proyecto">Estructura</a> •
    <a href="#-ejecución-para-desarrolladores">Desarrollo</a> •
    <a href="#-colaboradores">Colaboradores</a>
  </p>
</div>

---

## 📖 Descripción del Proyecto

**CampusConnect - UNACH** es una solución integral diseñada para centralizar la comunicación, los servicios académicos y la interacción estudiantil dentro de la **Universidad Autónoma de Chiapas (UNACH)**.

El sistema vincula un ecosistema móvil y web para estudiantes, docentes y egresados, permitiendo consultar avisos en tiempo real, gestionar perfiles académicos, tramitar insignias y fortalecer la identidad universitaria de manera ágil y segura.

---

## 📲 Descarga e Instalación del APK

Puedes instalar directamente la versión compilada para Android en tu teléfono sin pasar por la Play Store:

1. **Descargar el archivo:** Haz clic en el botón superior **[Descargar APK Android]** o dirígete al apartado de [Releases](../../releases) del repositorio y descarga el binario `.apk` de la versión más reciente.
2. **Habilitar fuentes desconocidas:**
   * En tu dispositivo Android, dirígete a **Ajustes > Seguridad y privacidad > Instalar aplicaciones desconocidas**.
   * Activa el permiso para tu navegador o gestor de archivos.
3. **Instalación:** Abre el `.apk` descargado y confirma la instalación.

---

## ✨ Características Principales

- 🔐 **Autenticación Segura y 2FA:** Acceso para estudiantes y docentes con soporte para doble factor de autenticación (2FA) y validación biométrica.
- 🔔 **Notificaciones Push en Tiempo Real:** Alertas y comunicados urgentes sobre eventos y avisos mediante Firebase Cloud Messaging (FCM).
- 📰 **Muro y Avisos Universitarios:** Noticias oficiales, eventos y publicaciones organizadas por facultades y campus.
- 🎓 **Módulo de Egresados y Catálogos:** Registro de egresados, catálogos de facultades y sistema de insignias académicas.
- 🗺️ **Directorio y Ubicación:** Información sobre facultades, aulas, oficinas de atención y servicios universitarios.
- 👤 **Perfil Estudiantil:** Consulta de datos escolares, trayectoria académica y ajustes de cuenta.

---

## 📱 Capturas de Pantalla

> 💡 **Nota:** Guarda las imágenes dentro de `assets/screenshots/` en tu repositorio para visualizarlas en las tablas inferiores (o arrástralas directamente en el editor web de GitHub).

### Vistas Principales

<div align="center">
  <table>
    <tr>
      <td align="center"><b>1. Inicio de Sesión / Registro</b></td>
      <td align="center"><b>2. Pantalla Principal (Feed)</b></td>
      <td align="center"><b>3. Eventos y Avisos</b></td>
    </tr>
    <tr>
      <td>
        <img src="assets/screenshots/login.png" alt="Pantalla de Inicio de Sesión" width="240" />
      </td>
      <td>
        <img src="assets/screenshots/home.png" alt="Pantalla Principal" width="240" />
      </td>
      <td>
        <img src="assets/screenshots/eventos.png" alt="Pantalla de Eventos" width="240" />
      </td>
    </tr>
    <tr>
      <td align="center"><i>Acceso seguro institucional y 2FA</i></td>
      <td align="center"><i>Noticias y comunicados oficiales</i></td>
      <td align="center"><i>Agenda de actividades UNACH</i></td>
    </tr>
  </table>
</div>

<br />

### Vistas Secundarias y Gestión

<div align="center">
  <table>
    <tr>
      <td align="center"><b>4. Egresados e Insignias</b></td>
      <td align="center"><b>5. Directorio del Campus</b></td>
      <td align="center"><b>6. Perfil de Usuario</b></td>
    </tr>
    <tr>
      <td>
        <img src="assets/screenshots/egresados.png" alt="Egresados e Insignias" width="240" />
      </td>
      <td>
        <img src="assets/screenshots/mapa.png" alt="Directorio del Campus" width="240" />
      </td>
      <td>
        <img src="assets/screenshots/perfil.png" alt="Perfil de Usuario" width="240" />
      </td>
    </tr>
    <tr>
      <td align="center"><i>Catálogo e insignias académicas</i></td>
      <td align="center"><i>Ubicación de facultades y áreas</i></td>
      <td align="center"><i>Gestión de datos del alumno</i></td>
    </tr>
  </table>
</div>

---

## 🛠️ Tecnologías Utilizadas

- **Aplicación Móvil:** Android Nativo, Kotlin, Gradle (Kotlin DSL), Firebase Cloud Messaging (FCM).
- **Servicios Backend:** Python, Autenticación 2FA, Biometría, API REST.
- **Plataforma Web:** JavaScript, HTML5, CSS3 responsivo.
- **Control de Versiones y Despliegue:** Git, GitHub Releases.

---

## 📂 Estructura del Proyecto

```text
CampusConnect-UNACH/
├── app/                         # Módulo principal de la aplicación Android (Kotlin)
├── campus-connect-backend/      # API y lógica del servidor en Python (Auth, 2FA, FCM)
├── campus-connect-web/          # Interfaz web de administración y perfiles (JS/HTML/CSS)
├── gradle/                      # Archivos de configuración del wrapper de Gradle
├── assets/
│   ├── images/                  # Logotipos e identidades gráficas
│   └── screenshots/             # Capturas de pantalla para documentación
├── build.gradle.kts             # Script de construcción raíz de Gradle
├── settings.gradle.kts          # Inclusión de módulos del proyecto
└── README.md                    # Documentación principal del repositorio
