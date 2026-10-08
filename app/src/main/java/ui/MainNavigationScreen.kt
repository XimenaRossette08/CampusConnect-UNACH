package com.example.campusconnect.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.example.campusconnect.model.Evento
import com.example.campusconnect.network.RetrofitClient
import kotlinx.coroutines.launch

// Lo hacemos público (sin 'private') para que las otras pantallas puedan usar estos colores
object ColorUnach {
    val AzulProfundo = Color(0xFF0D3B66)
    val AzulMedio = Color(0xFF1C6DD0)
    val Dorado = Color(0xFFC9A227)
    val Fondo = Color(0xFFF5F7FA)
    val TextoSecundario = Color(0xFF5B6670)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationScreen() {
    val context = LocalContext.current
    var pestañaSeleccionada by remember { mutableIntStateOf(0) }
    var puntosUsuario by remember { mutableIntStateOf(120) }

    // Estados del backend
    var listaEventosReal by remember { mutableStateOf<List<Evento>>(emptyList()) }
    var cargandoEventos by remember { mutableStateOf(true) }
    var errorEventos by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            cargandoEventos = true
            val respuesta = RetrofitClient.obtenerApi(context).obtenerEventos()
            listaEventosReal = respuesta
            errorEventos = null
        } catch (e: Exception) {
            errorEventos = "No se pudieron conectar los eventos del servidor"
        } finally {
            cargandoEventos = false
        }
    }

    val tituloBarra = when (pestañaSeleccionada) {
        0 -> "Campus Connect"
        1 -> "Rewards"
        2 -> "Mi perfil"
        else -> "Rutas del Ocelobús"
    }

    val coloresNavItem = NavigationBarItemDefaults.colors(
        selectedIconColor = ColorUnach.AzulProfundo,
        selectedTextColor = ColorUnach.AzulProfundo,
        indicatorColor = ColorUnach.AzulProfundo.copy(alpha = 0.12f),
        unselectedIconColor = ColorUnach.TextoSecundario,
        unselectedTextColor = ColorUnach.TextoSecundario
    )

    Scaffold(
        containerColor = ColorUnach.Fondo,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(tituloBarra, fontWeight = FontWeight.Medium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ColorUnach.AzulProfundo,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = pestañaSeleccionada == 0,
                    onClick = { pestañaSeleccionada = 0 },
                    icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                    label = { Text("Eventos") },
                    colors = coloresNavItem
                )
                NavigationBarItem(
                    selected = pestañaSeleccionada == 1,
                    onClick = { pestañaSeleccionada = 1 },
                    icon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null) },
                    label = { Text("Rewards") },
                    colors = coloresNavItem
                )
                NavigationBarItem(
                    selected = pestañaSeleccionada == 2,
                    onClick = { pestañaSeleccionada = 2 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("Perfil") },
                    colors = coloresNavItem
                )
                NavigationBarItem(
                    selected = pestañaSeleccionada == 3,
                    onClick = { pestañaSeleccionada = 3 },
                    icon = { Icon(Icons.Filled.DirectionsBus, contentDescription = null) },
                    label = { Text("Rutas") },
                    colors = coloresNavItem
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Crossfade(targetState = pestañaSeleccionada, label = "cambioPestaña") { pestaña ->
                when (pestaña) {
                    0 -> EventosScreen(
                        cargando = cargandoEventos,
                        error = errorEventos,
                        eventos = listaEventosReal,
                        snackbarHostState = snackbarHostState,
                        scope = scope
                    )
                    1 -> RewardsScreen(
                        puntos = puntosUsuario,
                        onPuntosCambiados = { nuevosPuntos -> puntosUsuario = nuevosPuntos },
                        snackbarHostState = snackbarHostState,
                        scope = scope
                    )
                    2 -> PerfilScreen(puntos = puntosUsuario)
                    else -> RutasScreen()
                }
            }
        }
    }
}