package com.example.campusconnect.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusconnect.ui.theme.CampusConnectTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Colores de referencia en azul y dorado. Igual que la versión anterior en
 * verde, no se verificaron contra un manual de identidad oficial de la UNACH;
 * ajústalos si necesitas coincidencia exacta.
 */
private object ColorUnach {
    val AzulProfundo = Color(0xFF0D3B66)
    val AzulMedio = Color(0xFF1C6DD0)
    val Dorado = Color(0xFFC9A227)
    val Fondo = Color(0xFFF5F7FA)
    val TextoSecundario = Color(0xFF5B6670)
}

// ---------- Modelos y datos de ejemplo (solo para esta demo) ----------

private data class EventoDemo(
    val id: Int,
    val titulo: String,
    val descripcion: String,
    val esGeneral: Boolean,
    val lugar: String,
    val fechaClave: String,
    val fechaLegible: String,
    val latitud: Double,
    val longitud: Double
)

private data class FechaCalendario(val clave: String, val diaSemana: String, val diaNumero: String)

private data class Recompensa(
    val id: Int,
    val titulo: String,
    val descripcion: String,
    val costoPuntos: Int
)

private data class PerfilDemo(
    val nombre: String,
    val correo: String,
    val rol: String,
    val facultad: String,
    val carrera: String
)

private data class ParadaBus(val nombre: String, val latitud: Double, val longitud: Double)
private data class RutaBus(val id: Int, val nombre: String, val paradas: List<ParadaBus>)

private fun abrirUbicacionEnMaps(context: Context, latitud: Double, longitud: Double, etiqueta: String) {
    val geoUri = Uri.parse("geo:$latitud,$longitud?q=$latitud,$longitud(${Uri.encode(etiqueta)})")
    val intent = Intent(Intent.ACTION_VIEW, geoUri)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitud,$longitud")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}

private fun abrirRutaCompletaEnMaps(context: Context, paradas: List<ParadaBus>) {
    val origen = paradas.first()
    val destino = paradas.last()
    val intermedias = paradas.drop(1).dropLast(1)
    val parametroWaypoints = if (intermedias.isNotEmpty()) {
        "&waypoints=" + intermedias.joinToString("|") { "${it.latitud},${it.longitud}" }
    } else ""
    val uri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1" +
                "&origin=${origen.latitud},${origen.longitud}" +
                "&destination=${destino.latitud},${destino.longitud}" +
                parametroWaypoints +
                "&travelmode=driving"
    )
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}

private val fechasDemo = listOf(
    FechaCalendario("2026-09-21", "LUN", "21"),
    FechaCalendario("2026-09-22", "MAR", "22"),
    FechaCalendario("2026-09-23", "MIÉ", "23"),
    FechaCalendario("2026-09-24", "JUE", "24"),
    FechaCalendario("2026-09-25", "VIE", "25")
)

// Coordenadas de ejemplo (aprox. Tuxtla Gutiérrez), solo para la demo.
private val eventosDeEjemplo = listOf(
    EventoDemo(
        id = 1,
        titulo = "Semana de la ingeniería",
        descripcion = "Conferencias, talleres y feria de proyectos abiertos a toda la comunidad universitaria.",
        esGeneral = true,
        lugar = "Auditorio Central, Campus I",
        fechaClave = "2026-09-21",
        fechaLegible = "Lunes 21 de septiembre · 10:00 am",
        latitud = 16.7530,
        longitud = -93.1167
    ),
    EventoDemo(
        id = 2,
        titulo = "Taller de estructuras de datos",
        descripcion = "Sesión práctica para el grupo 7N, cupo limitado a 30 alumnos.",
        esGeneral = false,
        lugar = "Laboratorio de Cómputo 3",
        fechaClave = "2026-09-22",
        fechaLegible = "Martes 22 de septiembre · 4:00 pm",
        latitud = 16.7521,
        longitud = -93.1150
    ),
    EventoDemo(
        id = 3,
        titulo = "Torneo intramuros de fútbol",
        descripcion = "Inscripciones abiertas para todas las carreras en la explanada central.",
        esGeneral = true,
        lugar = "Explanada Central, Campus I",
        fechaClave = "2026-09-24",
        fechaLegible = "Jueves 24 de septiembre · 9:00 am",
        latitud = 16.7538,
        longitud = -93.1172
    )
)

private val categoriasIncidencia = listOf("Infraestructura", "Limpieza", "Seguridad", "Mobiliario", "Otro")

private val recompensasDeEjemplo = listOf(
    Recompensa(1, "10% de descuento en la tienda UNACH", "Aplica en artículos oficiales y papelería.", 100),
    Recompensa(2, "20 impresiones gratis", "Válido en el centro de cómputo de tu facultad.", 50),
    Recompensa(3, "Bebida gratis en cafetería central", "Café, té o agua embotellada.", 40),
    Recompensa(4, "Acceso preferente a eventos", "Entrada prioritaria a la Semana de la Ingeniería.", 150)
)

private val perfilDeEjemplo = PerfilDemo(
    nombre = "Ximena Rossette",
    correo = "luz.rossete95@unach.mx",
    rol = "Usuario Regular",
    facultad = "Facultad de Ingeniería",
    carrera = "Ingeniería en Sistemas Computacionales"
)

// Coordenadas aproximadas dentro de Tuxtla Gutiérrez, NO verificadas contra el
// trazado real del Ocelobús. Reemplázalas por las coordenadas exactas de cada
// parada (clic derecho en Google Maps sobre el punto real -> copiar coordenadas).
private val paradaDianaCazadora = ParadaBus("Diana Cazadora", 16.7502, -93.1198)
private val paradaNovenaSur = ParadaBus("Novena Sur", 16.7460, -93.1210)
private val paradaTribunalDelEstado = ParadaBus("Tribunal del Estado", 16.7545, -93.1150)
private val paradaLibNorte = ParadaBus("Lib. Norte", 16.7600, -93.1230)
private val paradaAvCentral = ParadaBus("Av. Central", 16.7521, -93.1180)
private val paradaCampusI = ParadaBus("Campus I", 16.7395, -93.1055)
private val paradaCiudadUniversitaria = ParadaBus("Ciudad Universitaria", 16.7405, -93.1040)

private val rutasDeEjemplo = listOf(
    RutaBus(1, "Diana Cazadora – Novena Sur – Campus I", listOf(paradaDianaCazadora, paradaNovenaSur, paradaCampusI)),
    RutaBus(2, "Tribunal del Estado – Lib. Norte – Campus I", listOf(paradaTribunalDelEstado, paradaLibNorte, paradaCampusI)),
    RutaBus(3, "Diana Cazadora – Av. Central – Campus I", listOf(paradaDianaCazadora, paradaAvCentral, paradaCampusI)),
    RutaBus(4, "Campus I – Ciudad Universitaria", listOf(paradaCampusI, paradaCiudadUniversitaria))
)

// ---------- Pantalla principal con navegación inferior ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventosDemoScreen() {
    var pestañaSeleccionada by remember { mutableIntStateOf(0) }
    var fechaSeleccionada by remember { mutableStateOf<String?>(null) }
    var eventoSeleccionado by remember { mutableStateOf<EventoDemo?>(null) }
    var mostrarFormularioReporte by remember { mutableStateOf(false) }
    var puntosUsuario by remember { mutableIntStateOf(120) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val eventosFiltrados = remember(fechaSeleccionada) {
        if (fechaSeleccionada == null) eventosDeEjemplo
        else eventosDeEjemplo.filter { it.fechaClave == fechaSeleccionada }
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
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = pestañaSeleccionada == 0,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { /* Demo: aquí iría la navegación a Crear Evento */ },
                    containerColor = ColorUnach.Dorado,
                    contentColor = ColorUnach.AzulProfundo
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Crear evento")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Crossfade(targetState = pestañaSeleccionada, label = "cambioPestaña") { pestaña ->
                when (pestaña) {
                    0 -> EventosContenido(
                        fechas = fechasDemo,
                        fechaSeleccionada = fechaSeleccionada,
                        onSeleccionarFecha = { clave ->
                            fechaSeleccionada = if (fechaSeleccionada == clave) null else clave
                        },
                        eventos = eventosFiltrados,
                        onEventoClick = { eventoSeleccionado = it }
                    )
                    1 -> RewardsContenido(
                        puntos = puntosUsuario,
                        onReportarClick = { mostrarFormularioReporte = true },
                        onCanjear = { recompensa ->
                            if (puntosUsuario >= recompensa.costoPuntos) {
                                puntosUsuario -= recompensa.costoPuntos
                                scope.launch { snackbarHostState.showSnackbar("Canjeaste: ${recompensa.titulo}") }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("Te faltan puntos para esta recompensa") }
                            }
                        }
                    )
                    2 -> PerfilContenido(perfil = perfilDeEjemplo, puntos = puntosUsuario)
                    else -> RutasContenido()
                }
            }
        }
    }

    eventoSeleccionado?.let { evento ->
        DetalleEventoSheet(evento = evento, onDismiss = { eventoSeleccionado = null })
    }

    if (mostrarFormularioReporte) {
        ReportarIncidenciaSheet(
            onDismiss = { mostrarFormularioReporte = false },
            onEnviar = { _, _ ->
                puntosUsuario += 15
                mostrarFormularioReporte = false
                scope.launch { snackbarHostState.showSnackbar("¡Gracias por tu reporte! +15 puntos") }
            }
        )
    }
}

// ---------- Pestaña: Eventos ----------

@Composable
private fun EventosContenido(
    fechas: List<FechaCalendario>,
    fechaSeleccionada: String?,
    onSeleccionarFecha: (String) -> Unit,
    eventos: List<EventoDemo>,
    onEventoClick: (EventoDemo) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CalendarioStrip(fechas = fechas, seleccionada = fechaSeleccionada, onSeleccionar = onSeleccionarFecha)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(eventos, key = { it.id }) { evento ->
                TarjetaEventoDemo(
                    evento = evento,
                    modifier = Modifier.animateItem(),
                    onClick = { onEventoClick(evento) }
                )
            }
        }
    }
}

@Composable
private fun CalendarioStrip(
    fechas: List<FechaCalendario>,
    seleccionada: String?,
    onSeleccionar: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(fechas, key = { it.clave }) { fecha ->
            val estaSeleccionada = fecha.clave == seleccionada
            val fondo by animateColorAsState(
                targetValue = if (estaSeleccionada) ColorUnach.AzulProfundo else Color.White,
                label = "fondoChipFecha"
            )
            val textoColor by animateColorAsState(
                targetValue = if (estaSeleccionada) Color.White else ColorUnach.AzulProfundo,
                label = "textoChipFecha"
            )
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(fondo)
                    .border(1.dp, ColorUnach.AzulMedio.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .clickable { onSeleccionar(fecha.clave) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(fecha.diaSemana, fontSize = 12.sp, color = textoColor)
                Text(fecha.diaNumero, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textoColor)
            }
        }
    }
}

@Composable
private fun TarjetaEventoDemo(
    evento: EventoDemo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0x14000000))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = ColorUnach.AzulMedio,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(evento.fechaLegible, fontSize = 12.sp, color = ColorUnach.TextoSecundario)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(evento.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                evento.descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = ColorUnach.TextoSecundario,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(10.dp))
            val etiquetaColor = if (evento.esGeneral) ColorUnach.AzulMedio else ColorUnach.Dorado
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(etiquetaColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (evento.esGeneral) "General · Toda la UNACH" else "Evento de facultad",
                    fontSize = 11.sp,
                    color = etiquetaColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetalleEventoSheet(evento: EventoDemo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(ColorUnach.AzulProfundo, ColorUnach.AzulMedio))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = "Imagen referencial del evento",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(evento.titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = ColorUnach.AzulMedio, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(evento.fechaLegible, fontSize = 13.sp, color = ColorUnach.TextoSecundario)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(evento.descripcion, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(20.dp))

            Text("Ubicación", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ColorUnach.Fondo)
                    .border(1.dp, Color(0x1A000000), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ColorUnach.Dorado, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(evento.lugar, fontSize = 12.sp, color = ColorUnach.TextoSecundario)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { abrirUbicacionEnMaps(context, evento.latitud, evento.longitud, evento.lugar) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ColorUnach.AzulProfundo)
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver ubicación")
            }
        }
    }
}

// ---------- Pestaña: Rewards ----------

@Composable
private fun RewardsContenido(
    puntos: Int,
    onReportarClick: () -> Unit,
    onCanjear: (Recompensa) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { TarjetaPuntos(puntos = puntos, onReportarClick = onReportarClick) }

        item {
            Text("Recompensas disponibles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }

        items(recompensasDeEjemplo, key = { it.id }) { recompensa ->
            TarjetaRecompensa(
                recompensa = recompensa,
                puntosDisponibles = puntos,
                onCanjear = { onCanjear(recompensa) }
            )
        }
    }
}

@Composable
private fun TarjetaPuntos(puntos: Int, onReportarClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ColorUnach.AzulProfundo)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Tus puntos", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = ColorUnach.Dorado, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("$puntos", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Gana puntos reportando fallas o problemas en el campus: fugas de agua, luminarias dañadas, mobiliario roto y más.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onReportarClick,
                colors = ButtonDefaults.buttonColors(containerColor = ColorUnach.Dorado, contentColor = ColorUnach.AzulProfundo),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reportar un problema")
            }
        }
    }
}

@Composable
private fun TarjetaRecompensa(recompensa: Recompensa, puntosDisponibles: Int, onCanjear: () -> Unit) {
    val alcanzaPuntos = puntosDisponibles >= recompensa.costoPuntos
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0x14000000)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorUnach.Dorado.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = ColorUnach.Dorado)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(recompensa.titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    recompensa.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorUnach.TextoSecundario,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("${recompensa.costoPuntos} pts", fontSize = 12.sp, color = ColorUnach.AzulMedio, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCanjear,
                enabled = alcanzaPuntos,
                colors = ButtonDefaults.buttonColors(containerColor = ColorUnach.AzulProfundo),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Canjear", fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportarIncidenciaSheet(onDismiss: () -> Unit, onEnviar: (String, String) -> Unit) {
    var categoriaSeleccionada by remember { mutableStateOf(categoriasIncidencia.first()) }
    var descripcion by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Reportar un problema", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Ganas 15 puntos por cada reporte.",
                style = MaterialTheme.typography.bodySmall,
                color = ColorUnach.TextoSecundario
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Categoría", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categoriasIncidencia) { categoria ->
                    FilterChip(
                        selected = categoria == categoriaSeleccionada,
                        onClick = { categoriaSeleccionada = categoria },
                        label = { Text(categoria) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorUnach.AzulProfundo,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Descripción", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                placeholder = { Text("Describe brevemente qué encontraste y dónde") }
            )

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { onEnviar(categoriaSeleccionada, descripcion) },
                enabled = descripcion.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ColorUnach.AzulProfundo)
            ) {
                Text("Enviar reporte")
            }
        }
    }
}

// ---------- Pestaña: Perfil ----------

@Composable
private fun PerfilContenido(perfil: PerfilDemo, puntos: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(ColorUnach.AzulProfundo),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(perfil.nombre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Text(perfil.correo, style = MaterialTheme.typography.bodySmall, color = ColorUnach.TextoSecundario)

        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ColorUnach.Dorado.copy(alpha = 0.15f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(perfil.rol, color = ColorUnach.Dorado, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0x14000000)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                FilaDatoPerfil(etiqueta = "Facultad", valor = perfil.facultad)
                Spacer(modifier = Modifier.height(10.dp))
                FilaDatoPerfil(etiqueta = "Carrera", valor = perfil.carrera)
                Spacer(modifier = Modifier.height(10.dp))
                FilaDatoPerfil(etiqueta = "Puntos Rewards", valor = "$puntos pts")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = { /* Demo: aquí iría el cierre de sesión real */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cerrar sesión")
        }
    }
}

@Composable
private fun FilaDatoPerfil(etiqueta: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, color = ColorUnach.TextoSecundario, fontSize = 13.sp)
        Text(valor, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

// ---------- Pestaña: Rutas ----------

@Composable
private fun RutasContenido() {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Toca una parada para verla en Maps, o abre la ruta completa. El punto dorado simula la posición del autobús en tiempo real.",
                style = MaterialTheme.typography.bodySmall,
                color = ColorUnach.TextoSecundario
            )
        }

        items(rutasDeEjemplo, key = { it.id }) { ruta ->
            TarjetaRuta(
                ruta = ruta,
                onVerEnMaps = { abrirRutaCompletaEnMaps(context, it.paradas) },
                onParadaClick = { parada -> abrirUbicacionEnMaps(context, parada.latitud, parada.longitud, parada.nombre) }
            )
        }
    }
}

@Composable
private fun TarjetaRuta(
    ruta: RutaBus,
    onVerEnMaps: (RutaBus) -> Unit,
    onParadaClick: (ParadaBus) -> Unit
) {
    // Simula el avance del autobús por la ruta; no es una posición real.
    var indiceActual by remember(ruta.id) { mutableIntStateOf(0) }
    LaunchedEffect(ruta.id) {
        while (true) {
            delay(4000)
            indiceActual = (indiceActual + 1) % ruta.paradas.size
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0x14000000)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = ColorUnach.AzulProfundo, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(ruta.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(18.dp))
            EsquemaDeParadas(paradas = ruta.paradas, indiceActivo = indiceActual, onParadaClick = onParadaClick)

            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ColorUnach.Dorado)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Autobús cerca de ${ruta.paradas[indiceActual].nombre} · simulado",
                    fontSize = 11.sp,
                    color = ColorUnach.TextoSecundario
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onVerEnMaps(ruta) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorUnach.AzulProfundo)
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver ruta completa en Maps")
            }
        }
    }
}

@Composable
private fun EsquemaDeParadas(
    paradas: List<ParadaBus>,
    indiceActivo: Int,
    onParadaClick: (ParadaBus) -> Unit
) {
    val transicionInfinita = rememberInfiniteTransition(label = "pulso")
    val escalaPulso by transicionInfinita.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "escalaPulso"
    )

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        paradas.forEachIndexed { indice, parada ->
            val esActiva = indice == indiceActivo
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onParadaClick(parada) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (indice > 0) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(ColorUnach.AzulMedio.copy(alpha = 0.3f))
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Box(
                        modifier = Modifier
                            .size(if (esActiva) 14.dp else 10.dp)
                            .scale(if (esActiva) escalaPulso else 1f)
                            .clip(CircleShape)
                            .background(if (esActiva) ColorUnach.Dorado else ColorUnach.AzulMedio)
                    )
                    if (indice < paradas.size - 1) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(ColorUnach.AzulMedio.copy(alpha = 0.3f))
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    parada.nombre,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = if (esActiva) ColorUnach.Dorado else ColorUnach.TextoSecundario,
                    fontWeight = if (esActiva) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EventosDemoScreenPreview() {
    CampusConnectTheme {
        EventosDemoScreen()
    }
}