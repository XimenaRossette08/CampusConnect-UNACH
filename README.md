🎓 CampusConnect - UNACH

Conectando a la comunidad universitaria de la Universidad Autónoma de Chiapas

📖 Descripción del Proyecto

CampusConnect - UNACH es una aplicación diseñada para centralizar la comunicación, la información académica y la interacción estudiantil dentro de la Universidad Autónoma de Chiapas (UNACH).

El objetivo principal del proyecto es brindar a los estudiantes, docentes y personal universitario un espacio digital intuitivo donde puedan consultar avisos importantes, descubrir eventos académicos y culturales, ubicarse dentro del campus y fortalecer la comunidad estudiantil de manera rápida y accesible.

✨ Características Principales

🔐 Autenticación de Usuarios: Inicio de sesión y registro seguro para miembros de la comunidad universitaria (estudiantes y docentes).

📰 Muro y Avisos Universitarios: Visualización de noticias, comunicados oficiales y publicaciones relevantes de la facultad o campus.

📅 Calendario y Eventos: Consulta de eventos académicos, talleres, conferencias y actividades culturales o deportivas de la UNACH.

🗺️ Directorio y Ubicación: Información sobre servicios universitarios, departamentos, aulas e instalaciones del campus.

💬 Comunidad y Conexión: Espacio para interactuar con otros compañeros, compartir recursos académicos y participar en grupos de estudio.

👤 Perfil Estudiantil: Gestión de datos personales, carrera, semestre y preferencias dentro de la aplicación.



Vistas Principales

Vistas Secundarias y Perfil

🛠️ Tecnologías Utilizadas

Frontend / Móvil: React Native / Flutter / Android (Kotlin/Java) / React.js

Backend y Servicios: Node.js / Express / Firebase / Supabase

Base de Datos: PostgreSQL / MySQL / Cloud Firestore / MongoDB

Diseño y Prototipado: Figma

Control de Versiones: Git y GitHub

🚀 Instrucciones de Ejecución

Sigue estos pasos para clonar y ejecutar el proyecto en tu entorno de desarrollo local.

1. Prerrequisitos

Asegúrate de tener instaladas las siguientes herramientas en tu equipo según el entorno del proyecto:

Git

Editor de código como Visual Studio Code o Android Studio

Entorno de ejecución correspondiente (Node.js, Flutter SDK o JDK)

2. Clonar el Repositorio

Abre tu terminal y ejecuta el siguiente comando:

git clone https://github.com/XimenaRossette08/CampusConnect-UNACH.git
cd CampusConnect-UNACH


3. Configurar Variables de Entorno (Opcional)

Si el proyecto utiliza claves de API o conexión a base de datos/Firebase, crea un archivo .env en la raíz del proyecto basándote en el archivo .env.example:

API_URL=http://localhost:3000
DATABASE_URL=tu_url_de_conexion
API_KEY=tu_api_key


4. Instalación de Dependencias y Ejecución

Opción A: Si el proyecto es Web / React / React Native (Node.js / Expo)

# 1. Instalar las dependencias del proyecto
npm install

# 2. Iniciar el servidor de desarrollo
npm start
# o si utilizas Vite / Expo:
# npm run dev
# npx expo start


Opción B: Si el proyecto está hecho en Flutter

# 1. Descargar los paquetes y dependencias
flutter pub get

# 2. Ejecutar la aplicación en un emulador o dispositivo conectado
flutter run


Opción C: Si el proyecto es Android Nativo (Android Studio)

Abre Android Studio y selecciona Open an Existing Project.

Navega hasta la carpeta clonada CampusConnect-UNACH y ábrela.

Espera a que Gradle sincronice y descargue todas las dependencias.

Selecciona un emulador Android o conecta tu dispositivo físico con depuración USB activa.

Haz clic en el botón Run 'app' (Shift + F10).

📂 Estructura del Proyecto

CampusConnect-UNACH/
├── assets/
│   ├── images/             # Logos, íconos y recursos gráficos
│   └── screenshots/        # Capturas de pantalla para el README
├── src/                    # Código fuente principal de la aplicación
│   ├── components/         # Componentes reutilizables de la interfaz
│   ├── screens/            # Pantallas principales (Login, Home, Perfil, etc.)
│   ├── services/           # Conexión a API, Firebase o base de datos
│   ├── navigation/         # Configuración de rutas y navegación
│   └── utils/              # Funciones auxiliares y constantes
├── .gitignore              # Archivos ignorados por Git
└── README.md               # Documentación del proyecto


🤝 Contribuciones

¡Las contribuciones para mejorar CampusConnect - UNACH son bienvenidas! Si deseas colaborar:

Haz un Fork del proyecto.

Crea una nueva rama para tu funcionalidad (git checkout -b feature/NuevaFuncionalidad).

Realiza tus cambios y haz Commit (git commit -m 'Agrega NuevaFuncionalidad').

Sube los cambios a tu rama (git push origin feature/NuevaFuncionalidad).

Abre un Pull Request.

✒️ Autora

Ximena Rossette - Desarrollo y Documentación - @XimenaRossette08
