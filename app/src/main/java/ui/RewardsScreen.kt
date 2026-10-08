package com.example.campusconnect.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class Recompensa(val id: Int, val titulo: String, val descripcion: String, val costoPuntos: Int)

private val categoriasIncidencia = listOf("Infraestructura", "Limpieza", "Seguridad", "Mobiliario", "Otro")
private val recompensasDeEjemplo = listOf(
    Recompensa(1, "10% de descuento en la tienda UNACH", "Aplica en artículos oficiales y papelería.", 100),
    Recompensa(2, "20 impresiones gratis", "Válido en el centro de cómputo de tu facultad.", 50),
    Recompensa(3, "Bebida gratis en cafetería central", "Café, té o agua embotellada.", 40),
    Recompensa(4, "Acceso preferente a eventos", "Entrada prioritaria a la Semana de la Ingeniería.", 150)
)

// Esta es la ÚNICA función que debe ser pública
@Composable
fun RewardsScreen(puntos: Int, onPuntosCambiados: (Int) -> Unit, snackbarHostState: SnackbarHostState, scope: CoroutineScope) {
    var mostrarFormularioReporte by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { TarjetaPuntos(puntos = puntos, onReportarClick = { mostrarFormularioReporte = true }) }
        item { Text("Recompensas disponibles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium) }
        items(recompensasDeEjemplo) { recompensa ->
            TarjetaRecompensa(recompensa = recompensa, puntosDisponibles = puntos, onCanjear = {
                if (puntos >= recompensa.costoPuntos) {
                    onPuntosCambiados(puntos - recompensa.costoPuntos)
                    scope.launch { snackbarHostState.showSnackbar("Canjeaste: ${recompensa.titulo}") }
                } else {
                    scope.launch { snackbarHostState.showSnackbar("Te faltan puntos para esta recompensa") }
                }
            })
        }
    }

    if (mostrarFormularioReporte) {
        ReportarIncidenciaSheet(
            onDismiss = { mostrarFormularioReporte = false },
            onEnviar = { _, _ ->
                onPuntosCambiados(puntos + 15)
                mostrarFormularioReporte = false
                scope.launch { snackbarHostState.showSnackbar("¡Gracias por tu reporte! +15 puntos") }
            }
        )
    }
}

// Ahora son privadas
@Composable
private fun TarjetaPuntos(puntos: Int, onReportarClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = ColorUnach.AzulProfundo)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Tus puntos", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, "", tint = ColorUnach.Dorado, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("$puntos", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text("Gana puntos reportando fallas o problemas en el campus: fugas de agua, luminarias dañadas, mobiliario roto y más.", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = onReportarClick, colors = ButtonDefaults.buttonColors(containerColor = ColorUnach.Dorado, contentColor = ColorUnach.AzulProfundo), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.ReportProblem, "", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reportar un problema")
            }
        }
    }
}

@Composable
private fun TarjetaRecompensa(recompensa: Recompensa, puntosDisponibles: Int, onCanjear: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0x14000000)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(ColorUnach.Dorado.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.CardGiftcard, "", tint = ColorUnach.Dorado) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(recompensa.titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(recompensa.descripcion, style = MaterialTheme.typography.bodySmall, color = ColorUnach.TextoSecundario, maxLines = 2)
                Spacer(modifier = Modifier.height(6.dp))
                Text("${recompensa.costoPuntos} pts", fontSize = 12.sp, color = ColorUnach.AzulMedio, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onCanjear, enabled = puntosDisponibles >= recompensa.costoPuntos, colors = ButtonDefaults.buttonColors(containerColor = ColorUnach.AzulProfundo), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("Canjear", fontSize = 12.sp) }
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Reportar un problema", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Ganas 15 puntos por cada reporte.", style = MaterialTheme.typography.bodySmall, color = ColorUnach.TextoSecundario)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Categoría", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categoriasIncidencia) { categoria ->
                    FilterChip(
                        selected = categoria == categoriaSeleccionada, onClick = { categoriaSeleccionada = categoria }, label = { Text(categoria) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color.Transparent, selectedContainerColor = ColorUnach.AzulProfundo, selectedLabelColor = Color.White)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Descripción", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = descripcion, onValueChange = { descripcion = it }, modifier = Modifier.fillMaxWidth().height(110.dp), placeholder = { Text("Describe brevemente qué encontraste y dónde") })
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = { onEnviar(categoriaSeleccionada, descripcion) }, enabled = descripcion.isNotBlank(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ColorUnach.AzulProfundo)) { Text("Enviar reporte") }
        }
    }
}