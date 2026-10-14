package com.example.campusconnect.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.campusconnect.model.ArchivoEvento
import com.example.campusconnect.model.Evento
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Paleta Oficial ETDA - UNACH
private val EtdaNavy = Color(0xFF122652)
private val EtdaBlue = Color(0xFF0093FF)
private val EtdaOrange = Color(0xFFF57C00)
private val EtdaBg = Color(0xFFF4F7FB)
private val EtdaSuccess = Color(0xFF10B981)

// Zona horaria oficial de México para evitar desfase UTC en el emulador
private val zonaHorariaMx: TimeZone = TimeZone.getTimeZone("America/Mexico_City")
private val localeMx = Locale("es", "MX")

private fun obtenerCalendarioLocalMx(): Calendar {
    return Calendar.getInstance(zonaHorariaMx, localeMx)
}

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

// Configuración visual por categoría conectada con el backend de Yasir y el sistema de Insignias
private data class InfoCategoria(
    val nombre: String,
    val icono: ImageVector,
    val color: Color
)

private val listaCategorias = listOf(
    InfoCategoria("Todos", Icons.Default.Apps, EtdaNavy),
    InfoCategoria("Tutorías", Icons.Default.School, EtdaBlue),
    InfoCategoria("Conferencias", Icons.Default.RecordVoiceOver, EtdaOrange),
    InfoCategoria("Talleres", Icons.Default.Build, Color(0xFF0284C7)),
    InfoCategoria("Deporte", Icons.Default.EmojiEvents, Color(0xFFEA580C)),
    InfoCategoria("Culturales", Icons.Default.TheaterComedy, Color(0xFF2563EB)),
    InfoCategoria("Académicos", Icons.Default.MenuBook, EtdaNavy)
)

// Usa primero el campo `evento.categoria` que agregó Yasir en el backend, o lo detecta del texto
private fun detectarCategoriaEvento(evento: Evento): InfoCategoria {
    val catBackend = (evento.categoria ?: "").lowercase()
    val texto = "$catBackend ${evento.titulo} ${evento.descripcion}".lowercase()
    return when {
        texto.contains("tutor") || texto.contains("asesor") -> listaCategorias[1]
        texto.contains("conferencia") || texto.contains("plática") || texto.contains("foro") || texto.contains("congreso") -> listaCategorias[2]
        texto.contains("taller") || texto.contains("workshop") || texto.contains("práctic") || texto.contains("curso") -> listaCategorias[3]
        texto.contains("deport") || texto.contains("torneo") || texto.contains("fútbol") || texto.contains("carrera") -> listaCategorias[4]
        texto.contains("cultura") || texto.contains("arte") || texto.contains("música") || texto.contains("danza") || texto.contains("festival") -> listaCategorias[5]
        else -> listaCategorias[6]
    }
}

// Conserva el 15 de Octubre por defecto o extrae la fecha real del servidor
private fun extraerFechaVisual(fechaHora: String?): Triple<String, String, String> {
    if (fechaHora.isNullOrBlank()) return Triple("OCT", "15", "10:00")
    return try {
        val meses = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")
        val partes = fechaHora.split("T", " ")
        val fechaPartes = partes[0].split("-")
        val mesIdx = (fechaPartes.getOrNull(1)?.toIntOrNull() ?: 10) - 1
        val dia = fechaPartes.getOrNull(2)?.take(2) ?: "15"
        val hora = partes.getOrNull(1)?.take(5) ?: "10:00"
        Triple(meses.getOrElse(mesIdx) { "OCT" }, dia, hora)
    } catch (e: Exception) {
        Triple("OCT", "15", "10:00")
    }
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
    var calendarioExpandido by remember { mutableStateOf(true) }

    // 0 = Próximos Eventos, 1 = Mis Inscritos
    var tabVistaEventos by remember { mutableIntStateOf(0) }
    val eventosInscritos = remember { mutableStateListOf<String>() }

    // Estado del Motor del Calendario con zona horaria de México
    var mesActual by remember { mutableStateOf(obtenerCalendarioLocalMx()) }
    var fechaSeleccionada by remember { mutableStateOf(obtenerCalendarioLocalMx()) }

    val eventosFiltrados = remember(eventos, searchQuery, filtroSeleccionado, tabVistaEventos, eventosInscritos.size) {
        eventos.filter { ev ->
            val catInfo = detectarCategoriaEvento(ev)
            val coincideBusqueda = searchQuery.isBlank() ||
                    ev.titulo.contains(searchQuery, ignoreCase = true) ||
                    ev.lugar.contains(searchQuery, ignoreCase = true) ||
                    ev.descripcion.contains(searchQuery, ignoreCase = true) ||
                    (ev.categoria ?: "").contains(searchQuery, ignoreCase = true)

            val coincideCategoria = filtroSeleccionado == "Todos" ||
                    catInfo.nombre.equals(filtroSeleccionado, ignoreCase = true)

            val coincideTab = if (tabVistaEventos == 1) {
                eventosInscritos.contains(ev.titulo)
            } else {
                true
            }

            coincideBusqueda && coincideCategoria && coincideTab
        }
    }

    val diasDelMes = remember(mesActual, fechaSeleccionada, eventos) {
        val dias = mutableListOf<DiaMesContext>()
        val cal = mesActual.clone() as Calendar
        cal.timeZone = zonaHorariaMx
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val longitudMes = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        var diaSemanaInicio = cal.get(Calendar.DAY_OF_WEEK) - 1
        if (diaSemanaInicio == 0) diaSemanaInicio = 7

        val mesInt = cal.get(Calendar.MONTH)
        val anioInt = cal.get(Calendar.YEAR)

        for (i in 1 until diaSemanaInicio) {
            dias.add(DiaMesContext(0, 0, 0))
        }

        for (dia in 1..longitudMes) {
            val esSeleccionado = (dia == fechaSeleccionada.get(Calendar.DAY_OF_MONTH) &&
                    mesInt == fechaSeleccionada.get(Calendar.MONTH) &&
                    anioInt == fechaSeleccionada.get(Calendar.YEAR))

            val mesStr = String.format(Locale.getDefault(), "%02d", mesInt + 1)
            val diaStr = String.format(Locale.getDefault(), "%02d", dia)
            val fechaStr = "$anioInt-$mesStr-$diaStr"

            val diaTieneEvento = eventos.any { (it.fechaHora ?: "").startsWith(fechaStr) } || dia == 8 || dia == 15
            dias.add(DiaMesContext(dia, mesInt, anioInt, diaTieneEvento, esSeleccionado))
        }
        dias
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(EtdaBg),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. BANNER SUPERIOR CON BUSCADOR
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(EtdaNavy, Color(0xFF1A3673))
                        ),
                        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                color = EtdaOrange.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, EtdaOrange)
                            ) {
                                Text(
                                    text = "ESCUELA DE TECNOLOGÍAS DIGITALES APLICADAS",
                                    color = Color(0xFFFFB74D),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Cartelera de Eventos",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Participa, consulta materiales y suma asistencias",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = EtdaBlue.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, EtdaBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Eventos",
                                tint = EtdaOrange,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Buscar talleres, conferencias, tutorías...",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = EtdaBlue)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = Color.Gray)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = EtdaOrange,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = EtdaOrange
                        )
                    )
                }
            }
        }

        // 2. CARRUSEL DE CATEGORÍAS ANIMADO
        item {
            Column(modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) {
                Text(
                    text = "Categorías de participación",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = EtdaNavy.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(listaCategorias) { _, cat ->
                        val seleccionado = filtroSeleccionado == cat.nombre
                        val bgColor by animateColorAsState(
                            targetValue = if (seleccionado) EtdaOrange else Color.White,
                            animationSpec = tween(250),
                            label = "chipBg"
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (seleccionado) Color.White else EtdaNavy,
                            animationSpec = tween(250),
                            label = "chipContent"
                        )
                        val chipScale by animateFloatAsState(
                            targetValue = if (seleccionado) 1.05f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "chipScale"
                        )

                        Surface(
                            modifier = Modifier
                                .scale(chipScale)
                                .clickable { filtroSeleccionado = cat.nombre },
                            shape = RoundedCornerShape(50),
                            color = bgColor,
                            shadowElevation = if (seleccionado) 6.dp else 2.dp,
                            border = if (seleccionado) null else BorderStroke(1.dp, EtdaBlue.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = cat.icono,
                                    contentDescription = cat.nombre,
                                    tint = if (seleccionado) Color.White else EtdaBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat.nombre,
                                    color = contentColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. TARJETA DEL CALENDARIO INTERACTIVO (DESPLEGABLE)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { calendarioExpandido = !calendarioExpandido },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EtdaBlue.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Calendario",
                                    tint = EtdaBlue,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Agenda Académica",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = EtdaNavy
                                )
                                val formatDia = SimpleDateFormat("EEEE d 'de' MMMM", localeMx).apply {
                                    timeZone = zonaHorariaMx
                                }
                                Text(
                                    text = formatDia.format(fechaSeleccionada.time).replaceFirstChar { it.uppercase() },
                                    fontSize = 12.sp,
                                    color = EtdaOrange,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(onClick = { calendarioExpandido = !calendarioExpandido }) {
                            Icon(
                                imageVector = if (calendarioExpandido) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expandir/Contraer",
                                tint = EtdaNavy
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = calendarioExpandido,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = Color(0xFFEEF2F6)
                            )
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
                                        val nuevo = obtenerCalendarioLocalMx()
                                        nuevo.set(diaContext.anio, diaContext.mes, diaContext.dia)
                                        fechaSeleccionada = nuevo
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. SELECTOR DE PESTAÑAS: "PRÓXIMOS EVENTOS" VS "MIS INSCRITOS"
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, EtdaNavy.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val tab0Activa = tabVistaEventos == 0
                        val bgTab0 by animateColorAsState(
                            targetValue = if (tab0Activa) EtdaNavy else Color.Transparent,
                            animationSpec = tween(250),
                            label = "bgTab0"
                        )
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { tabVistaEventos = 0 },
                            color = bgTab0,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = null,
                                    tint = if (tab0Activa) EtdaOrange else EtdaNavy.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Próximos Eventos",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tab0Activa) Color.White else EtdaNavy.copy(alpha = 0.7f)
                                )
                            }
                        }

                        val tab1Activa = tabVistaEventos == 1
                        val bgTab1 by animateColorAsState(
                            targetValue = if (tab1Activa) EtdaNavy else Color.Transparent,
                            animationSpec = tween(250),
                            label = "bgTab1"
                        )
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { tabVistaEventos = 1 },
                            color = bgTab1,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HowToReg,
                                    contentDescription = null,
                                    tint = if (tab1Activa) EtdaOrange else EtdaBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Mis Inscritos",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tab1Activa) Color.White else EtdaNavy.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (tab1Activa) EtdaOrange else EtdaOrange.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${eventosInscritos.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (tab1Activa) Color.White else EtdaOrange,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            tabVistaEventos == 1 -> "Eventos en los que participas"
                            filtroSeleccionado == "Todos" -> "Cartelera general disponible"
                            else -> "Categoría: $filtroSeleccionado"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = EtdaNavy
                    )
                    Surface(
                        color = EtdaNavy.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (tabVistaEventos == 1) "${eventosFiltrados.size} inscritos" else "${eventosFiltrados.size} disponibles",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EtdaNavy,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 5. LISTADO DE TARJETAS CON ANIMACIÓN DE ENTRADA
        when {
            cargando -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EtdaOrange, strokeWidth = 3.dp)
                }
            }
            error != null -> item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(error, color = Color(0xFF991B1B), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            eventosFiltrados.isEmpty() -> item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EtdaBlue.copy(alpha = 0.1f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = if (tabVistaEventos == 1) Icons.Default.AssignmentLate else Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = EtdaBlue,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (tabVistaEventos == 1)
                                "Aún no te has inscrito a ningún evento"
                            else
                                "No se encontraron eventos en esta categoría",
                            color = EtdaNavy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (tabVistaEventos == 1)
                                "Explora la pestaña 'Próximos Eventos', inscríbete y aquí aparecerá tu agenda personal."
                            else
                                "Prueba cambiando el filtro de categoría o buscando otro término.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        if (tabVistaEventos == 1) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { tabVistaEventos = 0 },
                                colors = ButtonDefaults.buttonColors(containerColor = EtdaOrange),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Explorar Eventos", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            else -> itemsIndexed(
                items = eventosFiltrados,
                key = { index, ev -> "${ev.titulo}_$index" }
            ) { index, evento ->
                val yaInscrito = eventosInscritos.contains(evento.titulo)
                TarjetaEventoAnimada(
                    evento = evento,
                    index = index,
                    estaInscrito = yaInscrito,
                    onClick = { eventoSeleccionado = evento }
                )
            }
        }
    }

    // 6. MODAL BOTTOM SHEET CON FLYER + ARCHIVOS PDF DE YASIR + DISEÑO ETDA
    eventoSeleccionado?.let { evento ->
        val yaInscrito = eventosInscritos.contains(evento.titulo)
        DetalleEventoSheet(
            evento = evento,
            estaInscrito = yaInscrito,
            onDismiss = { eventoSeleccionado = null },
            onToggleInscripcion = { inscribir ->
                val cat = detectarCategoriaEvento(evento)
                if (inscribir) {
                    if (!eventosInscritos.contains(evento.titulo)) {
                        eventosInscritos.add(evento.titulo)
                    }
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "¡Te has registrado exitosamente a: ${evento.titulo}! (+1 asistencia en ${cat.nombre})"
                        )
                    }
                } else {
                    eventosInscritos.remove(evento.titulo)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "Inscripción cancelada para: ${evento.titulo}"
                        )
                    }
                }
            }
        )
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
    val hoyLocal = remember { obtenerCalendarioLocalMx() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMesAnterior,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(EtdaBg)
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Mes anterior", tint = EtdaNavy)
            }

            val formatMes = SimpleDateFormat("MMMM", localeMx).apply {
                timeZone = zonaHorariaMx
            }
            val nombreMes = formatMes.format(mesActual.time).replaceFirstChar { it.uppercase() }
            Text(
                text = "$nombreMes ${mesActual.get(Calendar.YEAR)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = EtdaNavy
            )

            IconButton(
                onClick = onMesSiguiente,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(EtdaBg)
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Mes siguiente", tint = EtdaNavy)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach { dia ->
                Text(
                    text = dia,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EtdaBlue,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        diasDelMes.chunked(7).forEach { semana ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                semana.forEach { diaContext ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (diaContext.esValido()) {
                            val esHoy = (diaContext.dia == hoyLocal.get(Calendar.DAY_OF_MONTH) &&
                                    diaContext.mes == hoyLocal.get(Calendar.MONTH) &&
                                    diaContext.anio == hoyLocal.get(Calendar.YEAR))

                            val bgDia by animateColorAsState(
                                targetValue = when {
                                    diaContext.seleccionado -> EtdaOrange
                                    esHoy -> EtdaBlue.copy(alpha = 0.12f)
                                    else -> Color.Transparent
                                },
                                label = "bgDia"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onDiaSeleccionado(diaContext) }
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(bgDia)
                                        .border(
                                            width = if (esHoy && !diaContext.seleccionado) 1.5.dp else 0.dp,
                                            color = if (esHoy) EtdaBlue else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = diaContext.dia.toString(),
                                        color = when {
                                            diaContext.seleccionado -> Color.White
                                            esHoy -> EtdaBlue
                                            else -> EtdaNavy
                                        },
                                        fontWeight = if (diaContext.seleccionado || esHoy) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }

                                if (diaContext.tieneEvento) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(if (diaContext.seleccionado) EtdaNavy else EtdaBlue)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(7.dp))
                                }
                            }
                        }
                    }
                }
                repeat(7 - semana.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TarjetaEventoAnimada(
    evento: Evento,
    index: Int,
    estaInscrito: Boolean,
    onClick: () -> Unit
) {
    var isSaved by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val alphaAnim by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = (index * 60).coerceAtMost(300)),
        label = "alphaCard"
    )
    val slideAnim by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = tween(durationMillis = 400, delayMillis = (index * 60).coerceAtMost(300)),
        label = "slideCard"
    )

    val heartScale by animateFloatAsState(
        targetValue = if (isSaved) 1.22f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "heartScale"
    )

    val catInfo = detectarCategoriaEvento(evento)
    val (mesTxt, diaTxt, horaTxt) = extraerFechaVisual(evento.fechaHora)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = if (estaInscrito) BorderStroke(1.5.dp, EtdaSuccess.copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .graphicsLayer {
                alpha = alphaAnim
                translationY = slideAnim
            }
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(EtdaNavy, EtdaBlue)
                        )
                    )
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = EtdaOrange,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = mesTxt,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = diaTxt,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = horaTxt,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = catInfo.color.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = catInfo.icono,
                                contentDescription = null,
                                tint = catInfo.color,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = (evento.categoria ?: catInfo.nombre).uppercase(),
                                color = catInfo.color,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    IconButton(
                        onClick = { isSaved = !isSaved },
                        modifier = Modifier
                            .size(28.dp)
                            .scale(heartScale)
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Guardar favorito",
                            tint = if (isSaved) EtdaOrange else Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = evento.titulo,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = EtdaNavy,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = EtdaBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = evento.lugar,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = EtdaBg,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Presencial",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EtdaNavy,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        if (evento.materiales.isNotEmpty()) {
                            Surface(
                                color = EtdaBlue.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = null,
                                        tint = EtdaBlue,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${evento.materiales.size} arch.",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EtdaBlue
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = EtdaOrange.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stars,
                                        contentDescription = null,
                                        tint = EtdaOrange,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "+1 Sello",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EtdaOrange
                                    )
                                }
                            }
                        }
                    }

                    if (estaInscrito) {
                        Surface(
                            color = EtdaSuccess.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EtdaSuccess,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Inscrito",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EtdaSuccess
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ver más",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EtdaBlue
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = EtdaBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetalleEventoSheet(
    evento: Evento,
    estaInscrito: Boolean,
    onDismiss: () -> Unit,
    onToggleInscripcion: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isInscrito by remember(estaInscrito) { mutableStateOf(estaInscrito) }
    val catInfo = detectarCategoriaEvento(evento)
    val (mesTxt, diaTxt, horaTxt) = extraerFechaVisual(evento.fechaHora)

    val progresoCupo by animateFloatAsState(
        targetValue = if (isInscrito) 0.85f else 0.80f,
        animationSpec = tween(600),
        label = "progresoCupo"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp)
        ) {
            // FLYER DEL EVENTO (Integración de Yasir con Coil AsyncImage)
            if (!evento.imagenUrl.isNullOrBlank()) {
                AsyncImage(
                    model = evento.imagenUrl,
                    contentDescription = "Flyer de ${evento.titulo}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(EtdaNavy, EtdaBlue)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = EtdaOrange,
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = catInfo.icono,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = (evento.categoria ?: catInfo.nombre).uppercase(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Surface(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "$diaTxt $mesTxt • $horaTxt hrs",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = evento.titulo,
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 26.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = EtdaBg,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = EtdaBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Ubicación", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                text = evento.lugar,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = EtdaNavy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = EtdaOrange.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = EtdaOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Recompensa", fontSize = 10.sp, color = EtdaOrange)
                            Text(
                                text = "+1 en ${catInfo.nombre}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = EtdaNavy,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Ocupación del evento", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EtdaNavy)
                Text(
                    text = if (isInscrito) "86 lugares disponibles" else "87 lugares disponibles",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EtdaBlue
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progresoCupo },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = EtdaBlue,
                trackColor = Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(18.dp))

            AnimatedContent(
                targetState = isInscrito,
                label = "estadoInscripcion"
            ) { inscrito ->
                if (inscrito) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                        border = BorderStroke(1.dp, EtdaSuccess.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EtdaSuccess, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Estás inscrito",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF065F46),
                                        fontSize = 16.sp
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        isInscrito = false
                                        onToggleInscripcion(false)
                                    }
                                ) {
                                    Text("Cancelar", color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = "El administrador confirmará tu asistencia el día del evento. Ya puedes verlo en la pestaña 'Mis Inscritos'.",
                                color = Color(0xFF047857),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    Column {
                        Text(
                            text = "Acerca del evento",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = EtdaNavy
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = evento.descripcion,
                            color = Color.DarkGray,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // SECCIÓN DE ARCHIVOS ADJUNTOS (IMÁGENES Y PDFs DE YASIR)
            ArchivosDelEvento(evento.materiales)

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, EtdaNavy),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EtdaNavy)
                ) {
                    Text("Al calendario", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (!isInscrito) {
                            isInscrito = true
                            onToggleInscripcion(true)
                        }
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isInscrito) EtdaSuccess else EtdaOrange
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isInscrito) Icons.Default.DoneAll else Icons.Default.HowToReg,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isInscrito) "Inscrito" else "Inscribirme",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/** Lista de archivos adjuntos (imágenes y PDFs) del evento creada por Yasir, estilizada con la paleta ETDA */
@Composable
private fun ArchivosDelEvento(archivos: List<ArchivoEvento>) {
    if (archivos.isEmpty()) return
    val context = LocalContext.current
    val abrir: (ArchivoEvento) -> Unit = { archivo ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(archivo.url)))
        } catch (_: ActivityNotFoundException) {
            // Sin app que abra este tipo de archivo
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = "Archivos del evento",
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        color = EtdaNavy
    )
    Spacer(modifier = Modifier.height(8.dp))

    val imagenes = archivos.filter { it.esImagen }
    if (imagenes.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(imagenes) { img ->
                AsyncImage(
                    model = img.url,
                    contentDescription = img.nombre,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, EtdaBlue.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .clickable { abrir(img) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    archivos.filter { !it.esImagen }.forEach { doc ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(EtdaBg)
                .border(1.dp, EtdaNavy.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .clickable { abrir(doc) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.PictureAsPdf,
                contentDescription = "PDF",
                tint = Color(0xFFC62828),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = doc.nombre,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = EtdaNavy
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EtdaBlue)
        }
    }
}