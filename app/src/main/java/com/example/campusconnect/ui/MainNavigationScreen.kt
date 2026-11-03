package com.example.campusconnect.ui

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusconnect.ComposeLoginActivity
import com.example.campusconnect.TokenManager
import com.example.campusconnect.model.Evento
import com.example.campusconnect.network.RetrofitClient
import retrofit2.HttpException

// Paleta Oficial ETDA - UNACH Intacta (pública para todas las pantallas)
object ColorUnach {
    val AzulProfundo = Color(0xFF122652) // NavyBlue Oficial
    val AzulMedio = Color(0xFF0093FF)    // BrightBlue Oficial
    val Dorado = Color(0xFFF57C00)       // VibrantOrange Oficial
    val Fondo = Color(0xFFF4F7FB)
    val TextoSecundario = Color(0xFF5B6670)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationScreen(validarSesion: Boolean = true) {
    val context = LocalContext.current
    var pestañaSeleccionada by remember { mutableIntStateOf(0) }

    var puntosUsuario by remember { mutableIntStateOf(0) }

    val puntosAnimados by animateIntAsState(
        targetValue = puntosUsuario,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
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
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) {
                TokenManager.cerrarSesion(context)
                val intent = Intent(context, ComposeLoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(intent)
            } else {
                errorEventos = "No se pudieron conectar los eventos del servidor (${e.code()})"
            }
        } catch (e: Exception) {
            errorEventos = "No se pudieron conectar los eventos del servidor"
        } finally {
            cargandoEventos = false
        }
    }

    val (tituloBarra, subtituloBarra) = when (pestañaSeleccionada) {
        0 -> "Campus Connect" to "ETDA • Cartelera Universitaria"
        1 -> "Mis Insignias y Rewards" to "ETDA • Sistema de Asistencias"
        else -> "Mi Perfil Académico" to "ETDA • Credencial Digital"
    }

    val coloresNavItem = NavigationBarItemDefaults.colors(
        selectedIconColor = Color.White,
        selectedTextColor = ColorUnach.AzulProfundo,
        indicatorColor = ColorUnach.Dorado,
        unselectedIconColor = ColorUnach.TextoSecundario,
        unselectedTextColor = ColorUnach.TextoSecundario
    )

    val currentDensity = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val factorEscalaPantalla = if (screenWidthDp < 390) (screenWidthDp / 390f).coerceAtLeast(0.85f) else 1f

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density * factorEscalaPantalla,
            fontScale = 1.0f
        )
    ) {
        Scaffold(
            containerColor = ColorUnach.Fondo,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ColorUnach.Dorado,
                                shadowElevation = 4.dp,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = "Institución UNACH",
                                    tint = Color.White,
                                    modifier = Modifier.padding(7.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = tituloBarra,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color.White,
                                    letterSpacing = (-0.3).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = subtituloBarra,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.80f),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        Surface(
                            onClick = { pestañaSeleccionada = 1 },
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, ColorUnach.Dorado.copy(alpha = 0.85f)),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
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
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
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
                    modifier = Modifier.shadow(16.dp)
                ) {
                    NavigationBarItem(
                        selected = pestañaSeleccionada == 0,
                        onClick = { pestañaSeleccionada = 0 },
                        icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "Cartelera de Eventos") },
                        label = {
                            Text(
                                "Eventos",
                                fontWeight = if (pestañaSeleccionada == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = coloresNavItem
                    )
                    NavigationBarItem(
                        selected = pestañaSeleccionada == 1,
                        onClick = { pestañaSeleccionada = 1 },
                        icon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = "Insignias y Rewards") },
                        label = {
                            Text(
                                "Insignias",
                                fontWeight = if (pestañaSeleccionada == 1) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = coloresNavItem
                    )
                    NavigationBarItem(
                        selected = pestañaSeleccionada == 2,
                        onClick = { pestañaSeleccionada = 2 },
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Mi Perfil") },
                        label = {
                            Text(
                                "Perfil",
                                fontWeight = if (pestañaSeleccionada == 2) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = coloresNavItem
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Crossfade(
                    targetState = pestañaSeleccionada,
                    animationSpec = tween(220),
                    label = "cambioPestaña"
                ) { pestaña ->
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
                        else -> PerfilScreen(puntos = puntosUsuario)
                    }
                }
            }
        }
    }
}