package com.example.campusconnect.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusconnect.model.Evento
import com.example.campusconnect.network.RetrofitClient

// Paleta Oficial ETDA - UNACH (pública para todas las pantallas)
object ColorUnach {
    val AzulProfundo = Color(0xFF122652) // NavyBlue Oficial
    val AzulMedio = Color(0xFF0093FF)    // BrightBlue Oficial
    val Dorado = Color(0xFFF57C00)       // VibrantOrange Oficial
    val Fondo = Color(0xFFF4F7FB)
    val TextoSecundario = Color(0xFF5B6670)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationScreen() {
    val context = LocalContext.current
    var pestañaSeleccionada by remember { mutableIntStateOf(0) }
    var puntosUsuario by remember { mutableIntStateOf(250) }

    val puntosAnimados by animateIntAsState(
        targetValue = puntosUsuario,
        animationSpec = tween(500),
        label = "puntosTopBar"
    )

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

    val (tituloBarra, subtituloBarra) = when (pestañaSeleccionada) {
        0 -> "Campus Connect" to "ETDA • Cartelera Universitaria"
        1 -> "Mis Insignias y Rewards" to "ETDA • Sistema de Asistencias"
        2 -> "Mi Perfil Académico" to "ETDA • Credencial Digital"
        else -> "Rutas del Ocelobús" to "UNACH • Movilidad Universitaria"
    }

    val coloresNavItem = NavigationBarItemDefaults.colors(
        selectedIconColor = Color.White,
        selectedTextColor = ColorUnach.AzulProfundo,
        indicatorColor = ColorUnach.Dorado, // Píldora naranja #F57C00 al seleccionar
        unselectedIconColor = ColorUnach.TextoSecundario,
        unselectedTextColor = ColorUnach.TextoSecundario
    )

    Scaffold(
        containerColor = ColorUnach.Fondo,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = ColorUnach.Dorado,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(7.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = tituloBarra,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                text = subtituloBarra,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.78f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    // Chip rápido de puntos en la esquina superior derecha
                    Surface(
                        onClick = { pestañaSeleccionada = 1 },
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ColorUnach.Dorado.copy(alpha = 0.8f)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = "Puntos",
                                tint = ColorUnach.Dorado,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$puntosAnimados pts",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ColorUnach.AzulProfundo,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier.shadow(12.dp)
            ) {
                NavigationBarItem(
                    selected = pestañaSeleccionada == 0,
                    onClick = { pestañaSeleccionada = 0 },
                    icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                    label = {
                        Text(
                            "Eventos",
                            fontWeight = if (pestañaSeleccionada == 0) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    },
                    colors = coloresNavItem
                )
                NavigationBarItem(
                    selected = pestañaSeleccionada == 1,
                    onClick = { pestañaSeleccionada = 1 },
                    icon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null) },
                    label = {
                        Text(
                            "Insignias",
                            fontWeight = if (pestañaSeleccionada == 1) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    },
                    colors = coloresNavItem
                )
                NavigationBarItem(
                    selected = pestañaSeleccionada == 2,
                    onClick = { pestañaSeleccionada = 2 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = {
                        Text(
                            "Perfil",
                            fontWeight = if (pestañaSeleccionada == 2) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    },
                    colors = coloresNavItem
                )
                NavigationBarItem(
                    selected = pestañaSeleccionada == 3,
                    onClick = { pestañaSeleccionada = 3 },
                    icon = { Icon(Icons.Filled.DirectionsBus, contentDescription = null) },
                    label = {
                        Text(
                            "Rutas",
                            fontWeight = if (pestañaSeleccionada == 3) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    },
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