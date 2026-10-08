package com.example.campusconnect.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private data class ParadaBus(val nombre: String, val latitud: Double, val longitud: Double)
private data class RutaBus(val id: Int, val nombre: String, val paradas: List<ParadaBus>)

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

private fun abrirUbicacionEnMaps(context: Context, latitud: Double, longitud: Double, etiqueta: String) {
    val geoUri = Uri.parse("geo:$latitud,$longitud?q=$latitud,$longitud(${Uri.encode(etiqueta)})")
    val intent = Intent(Intent.ACTION_VIEW, geoUri)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitud,$longitud")))
    }
}

private fun abrirRutaCompletaEnMaps(context: Context, paradas: List<ParadaBus>) {
    val origen = paradas.first()
    val destino = paradas.last()
    val intermedias = paradas.drop(1).dropLast(1)
    val parametroWaypoints = if (intermedias.isNotEmpty()) "&waypoints=" + intermedias.joinToString("|") { "${it.latitud},${it.longitud}" } else ""
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${origen.latitud},${origen.longitud}&destination=${destino.latitud},${destino.longitud}$parametroWaypoints&travelmode=driving")
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}

@Composable
fun RutasScreen() {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Toca una parada para verla en Maps, o abre la ruta completa. El punto dorado simula la posición del autobús en tiempo real.", style = MaterialTheme.typography.bodySmall, color = ColorUnach.TextoSecundario) }
        items(rutasDeEjemplo) { ruta ->
            TarjetaRuta(ruta = ruta, onVerEnMaps = { abrirRutaCompletaEnMaps(context, it.paradas) }, onParadaClick = { parada -> abrirUbicacionEnMaps(context, parada.latitud, parada.longitud, parada.nombre) })
        }
    }
}

// SOLUCIÓN: Agregada la palabra 'private' a la función
@Composable
private fun TarjetaRuta(ruta: RutaBus, onVerEnMaps: (RutaBus) -> Unit, onParadaClick: (ParadaBus) -> Unit) {
    var indiceActual by remember(ruta.id) { mutableIntStateOf(0) }
    LaunchedEffect(ruta.id) {
        while (true) {
            delay(4000)
            indiceActual = (indiceActual + 1) % ruta.paradas.size
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0x14000000)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsBus, "", tint = ColorUnach.AzulProfundo, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(ruta.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(18.dp))
            EsquemaDeParadas(paradas = ruta.paradas, indiceActivo = indiceActual, onParadaClick = onParadaClick)
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(ColorUnach.Dorado))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Autobús cerca de ${ruta.paradas[indiceActual].nombre} · simulado", fontSize = 11.sp, color = ColorUnach.TextoSecundario)
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { onVerEnMaps(ruta) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorUnach.AzulProfundo)) {
                Icon(Icons.Filled.Map, "", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver ruta completa en Maps")
            }
        }
    }
}

// SOLUCIÓN: Agregada la palabra 'private' a la función
@Composable
private fun EsquemaDeParadas(paradas: List<ParadaBus>, indiceActivo: Int, onParadaClick: (ParadaBus) -> Unit) {
    val transicionInfinita = rememberInfiniteTransition(label = "pulso")
    val escalaPulso by transicionInfinita.animateFloat(initialValue = 1f, targetValue = 1.6f, animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse), label = "escalaPulso")

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        paradas.forEachIndexed { indice, parada ->
            val esActiva = indice == indiceActivo
            Column(modifier = Modifier.weight(1f).clickable { onParadaClick(parada) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (indice > 0) { Box(modifier = Modifier.weight(1f).height(2.dp).background(ColorUnach.AzulMedio.copy(alpha = 0.3f))) } else { Spacer(modifier = Modifier.weight(1f)) }
                    Box(modifier = Modifier.size(if (esActiva) 14.dp else 10.dp).scale(if (esActiva) escalaPulso else 1f).clip(CircleShape).background(if (esActiva) ColorUnach.Dorado else ColorUnach.AzulMedio))
                    if (indice < paradas.size - 1) { Box(modifier = Modifier.weight(1f).height(2.dp).background(ColorUnach.AzulMedio.copy(alpha = 0.3f))) } else { Spacer(modifier = Modifier.weight(1f)) }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(parada.nombre, fontSize = 10.sp, textAlign = TextAlign.Center, color = if (esActiva) ColorUnach.Dorado else ColorUnach.TextoSecundario, fontWeight = if (esActiva) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}