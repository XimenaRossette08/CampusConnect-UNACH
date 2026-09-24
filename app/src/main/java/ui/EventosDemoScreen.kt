package com.example.campusconnect.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusconnect.ui.theme.CampusConnectTheme
import androidx.core.net.toUri
import android.content.ActivityNotFoundException

/**
 * Colores institucionales de referencia (verde y dorado, tradicionales del
 * escudo de la UNACH). No se encontraron los hex EXACTOS del manual de
 * identidad vigente; si necesitas coincidencia exacta para la presentación,
 * reemplaza estos valores por los oficiales.
 */
private object ColorUnach {
    val VerdeProfundo = Color(0xFF14532D)
    val VerdeMedio = Color(0xFF1E7A46)
    val Dorado = Color(0xFFC9A227)
    val Fondo = Color(0xFFF7F8F4)
    val TextoSecundario = Color(0xFF5B6660)
}

/** Modelo mínimo solo para esta demo (no es la clase Evento real del proyecto). */
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

private val fechasDemo = listOf(
    FechaCalendario("2026-09-21", "LUN", "21"),
    FechaCalendario("2026-09-22", "MAR", "22"),
    FechaCalendario("2026-09-23", "MIÉ", "23"),
    FechaCalendario("2026-09-24", "JUE", "24"),
    FechaCalendario("2026-09-25", "VIE", "25")
)

// Coordenadas de ejemplo (aprox. Tuxtla Gutiérrez) solo para la demo.
// Reemplázalas por las coordenadas reales de cada lugar cuando conectes datos de verdad.
private val eventosDeEjemplo = listOf(
    EventoDemo(
        id = 1,
        titulo = "Semana de la ingeniería",
        descripcion = "Conferencias, talleres y feria de proyectos abiertos a toda la comunidad universitaria. Incluye stands de Ingeniería en Sistemas, Civil e Industrial.",
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
        descripcion = "Sesión práctica para el grupo 7N, cupo limitado a 30 alumnos. Trae tu laptop con Kotlin instalado.",
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
        descripcion = "Inscripciones abiertas para todas las carreras en la explanada central. Trae tu equipo completo.",
        esGeneral = true,
        lugar = "Explanada Central, Campus I",
        fechaClave = "2026-09-24",
        fechaLegible = "Jueves 24 de septiembre · 9:00 am",
        latitud = 16.7538,
        longitud = -93.1172
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventosDemoScreen() {
    var fechaSeleccionada by remember { mutableStateOf<String?>(null) }
    var eventoSeleccionado by remember { mutableStateOf<EventoDemo?>(null) }

    val eventosFiltrados = remember(fechaSeleccionada) {
        if (fechaSeleccionada == null) eventosDeEjemplo
        else eventosDeEjemplo.filter { it.fechaClave == fechaSeleccionada }
    }

    Scaffold(
        containerColor = ColorUnach.Fondo,
        topBar = {
            TopAppBar(
                title = { Text("Campus Connect", fontWeight = FontWeight.Medium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ColorUnach.VerdeProfundo,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* Demo: aquí iría la navegación a Crear Evento */ },
                containerColor = ColorUnach.Dorado,
                contentColor = ColorUnach.VerdeProfundo
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Crear evento")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CalendarioStrip(
                fechas = fechasDemo,
                seleccionada = fechaSeleccionada,
                onSeleccionar = { clave ->
                    fechaSeleccionada = if (fechaSeleccionada == clave) null else clave
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(eventosFiltrados, key = { it.id }) { evento ->
                    TarjetaEventoDemo(
                        evento = evento,
                        modifier = Modifier.animateItem(),
                        onClick = { eventoSeleccionado = evento }
                    )
                }
            }
        }
    }

    eventoSeleccionado?.let { evento ->
        DetalleEventoSheet(evento = evento, onDismiss = { eventoSeleccionado = null })
    }
}

@Suppress("SameParameterValue")
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
                targetValue = if (estaSeleccionada) ColorUnach.VerdeProfundo else Color.White,
                label = "fondoChipFecha"
            )
            val textoColor by animateColorAsState(
                targetValue = if (estaSeleccionada) Color.White else ColorUnach.VerdeProfundo,
                label = "textoChipFecha"
            )
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(fondo)
                    .border(1.dp, ColorUnach.VerdeMedio.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
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
                    tint = ColorUnach.VerdeMedio,
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
            val etiquetaColor = if (evento.esGeneral) ColorUnach.VerdeMedio else ColorUnach.Dorado
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Imagen referencial (placeholder). Sustituye este Box por
            // Image(painter = painterResource(R.drawable.tu_imagen), ...) cuando tengas fotos reales.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(listOf(ColorUnach.VerdeProfundo, ColorUnach.VerdeMedio))
                    ),
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
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = ColorUnach.VerdeMedio,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(evento.fechaLegible, fontSize = 13.sp, color = ColorUnach.TextoSecundario)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(evento.descripcion, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(20.dp))

            Text("Ubicación", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))

            // Vista tipo mapa (solo visual/placeholder). Un mapa interactivo real
            // requiere la librería com.google.maps.android:maps-compose + una API key de Google Maps.
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
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = ColorUnach.Dorado,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(evento.lugar, fontSize = 12.sp, color = ColorUnach.TextoSecundario)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    val geoUri = "geo:${evento.latitud},${evento.longitud}?q=${evento.latitud},${evento.longitud}(${Uri.encode(evento.lugar)})".toUri()
                    val intent = Intent(Intent.ACTION_VIEW, geoUri)

                    try {
                        // Intenta abrir una aplicación de mapas instalada
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        // Si falla (no hay app de mapas), abre Google Maps en el navegador web
                        val webUri = "https://www.google.com/maps/search/?api=1&query=${evento.latitud},${evento.longitud}".toUri()
                        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ColorUnach.VerdeProfundo)
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver ubicación")
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