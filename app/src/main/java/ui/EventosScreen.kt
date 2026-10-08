package com.example.campusconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusconnect.model.Evento
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Modelo de datos compatible con todas las versiones de Android
private data class DiaMesContext(
    val dia: Int,
    val mes: Int,
    val anio: Int,
    val tieneEvento: Boolean = false,
    val seleccionado: Boolean = false
) {
    fun esValido() = dia > 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventosScreen(
    cargando: Boolean,
    error: String?,
    eventos: List<Evento>,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    var searchQuery by remember { mutableStateOf("") }
    var filtroSeleccionado by remember { mutableStateOf("Todos") }
    var eventoSeleccionado by remember { mutableStateOf<Evento?>(null) }
    val filtros = listOf("Todos", "Académico", "Cultural", "Deportivo")

    // Estado del Motor del Calendario (Usando java.util.Calendar clásico)
    var mesActual by remember { mutableStateOf(Calendar.getInstance()) }
    var fechaSeleccionada by remember { mutableStateOf(Calendar.getInstance()) }

    // Generador dinámico de los días de la cuadrícula
    val diasDelMes = remember(mesActual, fechaSeleccionada, eventos) {
        val dias = mutableListOf<DiaMesContext>()
        val cal = mesActual.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1) // Ir al primer día del mes

        val longitudMes = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Ajustar días de la semana (Lunes = 1, Domingo = 7)
        var diaSemanaInicio = cal.get(Calendar.DAY_OF_WEEK) - 1
        if (diaSemanaInicio == 0) diaSemanaInicio = 7

        val mesInt = cal.get(Calendar.MONTH)
        val anioInt = cal.get(Calendar.YEAR)

        // Rellenar cuadritos en blanco antes de que empiece el mes
        for (i in 1 until diaSemanaInicio) {
            dias.add(DiaMesContext(0, 0, 0)) // Día 0 = inválido/vacío
        }

        // Rellenar los días con número
        for (dia in 1..longitudMes) {
            val esSeleccionado = (dia == fechaSeleccionada.get(Calendar.DAY_OF_MONTH) &&
                    mesInt == fechaSeleccionada.get(Calendar.MONTH) &&
                    anioInt == fechaSeleccionada.get(Calendar.YEAR))

            // Formatear la fecha como "YYYY-MM-DD" para buscar coincidencias con la API
            val mesStr = String.format(Locale.getDefault(), "%02d", mesInt + 1)
            val diaStr = String.format(Locale.getDefault(), "%02d", dia)
            val fechaStr = "$anioInt-$mesStr-$diaStr"

            val diaTieneEvento = eventos.any { (it.fechaHora ?: "").startsWith(fechaStr) } || dia == 8
            dias.add(DiaMesContext(dia, mesInt, anioInt, diaTieneEvento, esSeleccionado))
        }
        dias
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color.White), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Eventos universitarios", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)) }

        item {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar por evento o facultad") }, leadingIcon = { Icon(Icons.Default.Search, "") },
                shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ColorUnach.AzulProfundo, unfocusedBorderColor = Color.LightGray)
            )
        }

        item {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtros.size) { index ->
                    FilterChip(
                        selected = filtroSeleccionado == filtros[index], onClick = { filtroSeleccionado = filtros[index] }, label = { Text(filtros[index]) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color.Transparent, selectedContainerColor = Color(0xFF1E1E1E), selectedLabelColor = Color.White)
                    )
                }
            }
        }

        item {
            CalendarioMesGrid(
                mesActual = mesActual,
                diasDelMes = diasDelMes,
                onMesAnterior = {
                    val nuevo = mesActual.clone() as Calendar
                    nuevo.add(Calendar.MONTH, -1)
                    mesActual = nuevo
                },
                onMesSiguiente = {
                    val nuevo = mesActual.clone() as Calendar
                    nuevo.add(Calendar.MONTH, 1)
                    mesActual = nuevo
                },
                onDiaSeleccionado = { diaContext ->
                    if (diaContext.esValido()) {
                        val nuevo = Calendar.getInstance()
                        nuevo.set(diaContext.anio, diaContext.mes, diaContext.dia)
                        fechaSeleccionada = nuevo
                    }
                }
            )
        }

        item {
            val formatDia = SimpleDateFormat("d 'de' MMMM", Locale("es", "MX"))
            val textoDia = formatDia.format(fechaSeleccionada.time)
            Text("Eventos para el $textoDia", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
        }

        when {
            cargando -> item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ColorUnach.AzulProfundo) } }
            error != null -> item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(error, color = Color.Red, fontSize = 14.sp) } }
            eventos.isEmpty() -> item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No hay eventos disponibles.", color = ColorUnach.TextoSecundario) } }
            // Corrección aplicada aquí: cambiamos 'it' por 'evento'
            else -> items(eventos) { evento ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TarjetaEventoNuevo(evento) { eventoSeleccionado = evento }
                }
            }
        }
    }

    eventoSeleccionado?.let { evento ->
        DetalleEventoSheet(evento = evento, onDismiss = { eventoSeleccionado = null }) {
            scope.launch { snackbarHostState.showSnackbar("¡Te has registrado exitosamente a: ${evento.titulo}!") }
        }
    }
}

@Composable
private fun CalendarioMesGrid(
    mesActual: Calendar,
    diasDelMes: List<DiaMesContext>,
    onMesAnterior: () -> Unit,
    onMesSiguiente: () -> Unit,
    onDiaSeleccionado: (DiaMesContext) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMesAnterior) { Icon(Icons.Filled.ChevronLeft, "", tint = Color.Gray) }

            // Título del mes en español
            val formatMes = SimpleDateFormat("MMMM", Locale("es", "MX"))
            val nombreMes = formatMes.format(mesActual.time).replaceFirstChar { it.uppercase() }
            Text("$nombreMes ${mesActual.get(Calendar.YEAR)}", fontSize = 18.sp, fontWeight = FontWeight.Medium)

            IconButton(onClick = onMesSiguiente) { Icon(Icons.Filled.ChevronRight, "", tint = Color.Gray) }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach { dia -> Text(dia, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
        }
        Spacer(modifier = Modifier.height(12.dp))

        diasDelMes.chunked(7).forEach { semana ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                semana.forEach { diaContext ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (diaContext.esValido()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onDiaSeleccionado(diaContext) }
                            ) {
                                val hoy = Calendar.getInstance()
                                val esHoy = (diaContext.dia == hoy.get(Calendar.DAY_OF_MONTH) && diaContext.mes == hoy.get(Calendar.MONTH) && diaContext.anio == hoy.get(Calendar.YEAR))

                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(if (diaContext.seleccionado) Color(0xFF6200EA) else Color.Transparent)
                                        .border(width = if (esHoy && !diaContext.seleccionado) 1.dp else 0.dp, color = if (esHoy) Color(0xFF6200EA) else Color.Transparent, shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) { Text(diaContext.dia.toString(), color = if (diaContext.seleccionado) Color.White else Color.Black, fontSize = 16.sp) }

                                if (diaContext.tieneEvento) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF8E24AA))) // Puntito morado
                                } else { Spacer(modifier = Modifier.height(6.dp)) }
                            }
                        }
                    }
                }
                repeat(7 - semana.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TarjetaEventoNuevo(evento: Evento, onClick: () -> Unit) {
    var isSaved by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth().height(140.dp).clickable { onClick() }) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(0.3f).fillMaxHeight().background(Color(0xFF1967D2)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("OCT", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("8", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("09:00", color = Color.White, fontSize = 12.sp)
                }
            }
            Column(modifier = Modifier.weight(0.7f).padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("EVENTO", color = Color(0xFF1967D2), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(evento.titulo, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 2)
                    }
                    IconButton(onClick = { isSaved = !isSaved }, modifier = Modifier.size(24.dp)) { Icon(imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "", tint = if (isSaved) Color.Red else Color.Gray) }
                }
                Text(evento.lugar, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Surface(color = Color(0xFFF3E5F5), shape = RoundedCornerShape(4.dp)) { Text("Presencial", fontSize = 10.sp, color = Color(0xFF6200EA), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetalleEventoSheet(evento: Evento, onDismiss: () -> Unit, onRegistrarse: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isInscrito by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ColorUnach.AzulMedio).padding(20.dp)) {
                Column {
                    Text("ACADÉMICO", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(evento.titulo, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, "", tint = Color(0xFF6200EA), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(evento.lugar, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(progress = 0.8f, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Color(0xFF6200EA), trackColor = Color.LightGray)
            Text("87 lugares disponibles", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(20.dp))

            if (isInscrito) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, "", tint = Color(0xFF6200EA), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Estás inscrito", fontWeight = FontWeight.Bold, color = Color(0xFF4A148C), fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("El administrador confirmará tu asistencia el día del evento.", color = Color.DarkGray, fontSize = 14.sp)
                    }
                }
            } else {
                Text("Acerca del evento", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(evento.descripcion, color = Color.DarkGray, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6200EA))) { Text("Al calendario") }
                Button(onClick = { if (!isInscrito) { isInscrito = true; onRegistrarse() } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (isInscrito) Color(0xFF2E7D32) else Color(0xFF6200EA))) {
                    Text(if (isInscrito) "Inscrito" else "Inscribirme")
                }
            }
        }
    }
}