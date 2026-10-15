package com.example.campusconnect.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Paleta Oficial ETDA - UNACH
private val EtdaNavy = Color(0xFF122652)
private val EtdaBlue = Color(0xFF0093FF)
private val EtdaOrange = Color(0xFFF57C00)
private val EtdaBg = Color(0xFFF4F7FB)
private val EtdaSuccess = Color(0xFF10B981)

// Tonos Metálicos para las Medallas (Bronce -> Plata -> Oro)
private val BroncePrimario = Color(0xFFD97706)
private val BronceBrillo = Color(0xFFFBBF24)
private val PlataPrimario = Color(0xFF475569)
private val PlataBrillo = Color(0xFFCBD5E1)
private val OroPrimario = Color(0xFFF59E0B)
private val OroBrillo = Color(0xFFFEF08A)

private data class EscalafonMeta(
    val nivel: Int,
    val nombreRango: String,
    val etiquetaCorta: String,
    val metaAsistencias: Int,
    val puntosPremio: Int,
    val colorPrimario: Color,
    val colorBrillo: Color
)

private val nivelesProgresivos = listOf(
    EscalafonMeta(1, "Nivel I • Bronce", "BRONCE", 5, 100, BroncePrimario, BronceBrillo),
    EscalafonMeta(2, "Nivel II • Plata", "PLATA", 8, 150, PlataPrimario, PlataBrillo),
    EscalafonMeta(3, "Nivel III • Oro ETDA", "ORO", 12, 250, OroPrimario, OroBrillo)
)

private data class CategoriaEvolutiva(
    val id: String,
    val categoria: String,
    val tituloBaseInsignia: String,
    val subtituloMedalla: String,
    val descripcionBase: String,
    val asistenciasIniciales: Int,
    val nivelInicialReclamado: Int,
    val icono: ImageVector,
    val colorCategoria: Color,
    val prefijoFolio: String
)

private data class LogroDesbloqueadoModal(
    val categoria: String,
    val nombreInsignia: String,
    val nombreRango: String,
    val nivel: Int,
    val metaAlcanzada: Int,
    val siguienteMeta: Int?,
    val puntosGanados: Int,
    val folio: String,
    val icono: ImageVector,
    val colorPrimario: Color,
    val colorBrillo: Color
)

private data class RecompensaCanjeable(
    val id: Int,
    val titulo: String,
    val descripcion: String,
    val categoriaRequerida: String,
    val costoPuntos: Int,
    val icono: ImageVector
)

private val catalogoRecompensasUnach = listOf(
    RecompensaCanjeable(
        id = 1,
        titulo = "Constancia de Valor Curricular (Créditos ETDA)",
        descripcion = "Documento oficial avalado por la Coordinación Académica al acumular insignias.",
        categoriaRequerida = "Académicos y Conferencias",
        costoPuntos = 120,
        icono = Icons.Default.VerifiedUser
    ),
    RecompensaCanjeable(
        id = 2,
        titulo = "Acceso Preferente a Talleres y Congresos",
        descripcion = "Lugar reservado en primera fila y laboratorios durante los foros de la ETDA.",
        categoriaRequerida = "Talleres y Tutorías",
        costoPuntos = 100,
        icono = Icons.Default.WorkspacePremium
    ),
    RecompensaCanjeable(
        id = 3,
        titulo = "Paquete de 30 Impresiones Gratis",
        descripcion = "Válido en el Centro de Cómputo de la Escuela de Tecnologías Digitales Aplicadas.",
        categoriaRequerida = "Cualquier categoría",
        costoPuntos = 50,
        icono = Icons.Default.Print
    ),
    RecompensaCanjeable(
        id = 4,
        titulo = "Descuento en Tienda y Souvenirs UNACH",
        descripcion = "Aplica en termos oficiales Ocelotes, playeras conmemorativas y papelería.",
        categoriaRequerida = "Culturales y Deporte",
        costoPuntos = 80,
        icono = Icons.Default.CardGiftcard
    ),
    RecompensaCanjeable(
        id = 5,
        titulo = "Bebida o Combo en Cafetería Universitaria",
        descripcion = "Canjeable por café, frappé o bebida embotellada presentando tu QR estudiantil.",
        categoriaRequerida = "Cualquier categoría",
        costoPuntos = 40,
        icono = Icons.Default.LocalCafe
    )
)

// COMPONENTE GRÁFICO PERSONALIZADO: Dibuja una Medalla / Roseta Estrellada con listones
@Composable
private fun MedallaInsigniaETDA(
    icono: ImageVector,
    colorPrincipal: Color,
    colorSecundario: Color,
    colorListon: Color,
    nivelEstrellas: Int,
    bloqueada: Boolean = false,
    destacada: Boolean = false,
    tamano: Dp = 64.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "medallaAnim")
    val giroBrillo by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "giroMedalla"
    )

    val cPrimario = if (bloqueada) Color(0xFF94A3B8) else colorPrincipal
    val cBrillo = if (bloqueada) Color(0xFFCBD5E1) else colorSecundario
    val cCinta = if (bloqueada) Color(0xFF64748B) else colorListon

    Box(
        modifier = Modifier
            .size(tamano)
            .graphicsLayer {
                if (destacada) rotationZ = giroBrillo
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * 0.44f
            val radioExterior = w * 0.40f
            val radioInterior = w * 0.33f

            // 1. Listones inferiores de la medalla (Condecoración)
            val listonIzq = Path().apply {
                moveTo(cx - radioInterior * 0.55f, cy + radioInterior * 0.5f)
                lineTo(cx - radioInterior * 0.85f, h * 0.96f)
                lineTo(cx - radioInterior * 0.35f, h * 0.86f)
                lineTo(cx - radioInterior * 0.10f, h * 0.98f)
                lineTo(cx - radioInterior * 0.05f, cy + radioInterior * 0.6f)
                close()
            }
            val listonDer = Path().apply {
                moveTo(cx + radioInterior * 0.55f, cy + radioInterior * 0.5f)
                lineTo(cx + radioInterior * 0.85f, h * 0.96f)
                lineTo(cx + radioInterior * 0.35f, h * 0.86f)
                lineTo(cx + radioInterior * 0.10f, h * 0.98f)
                lineTo(cx + radioInterior * 0.05f, cy + radioInterior * 0.6f)
                close()
            }
            drawPath(listonIzq, color = cCinta)
            drawPath(listonDer, color = cCinta.copy(alpha = 0.85f))

            // 2. Escudo estrellado de 12 puntas (Roseta de insignia)
            val puntas = 24
            val estrellaPath = Path()
            for (i in 0 until puntas) {
                val r = if (i % 2 == 0) radioExterior else radioInterior
                val angulo = (i * 2 * PI / puntas) - (PI / 2)
                val x = cx + (r * cos(angulo)).toFloat()
                val y = cy + (r * sin(angulo)).toFloat()
                if (i == 0) estrellaPath.moveTo(x, y) else estrellaPath.lineTo(x, y)
            }
            estrellaPath.close()

            drawPath(
                path = estrellaPath,
                brush = Brush.linearGradient(
                    colors = listOf(cBrillo, cPrimario, cBrillo),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )
            drawPath(
                path = estrellaPath,
                color = Color.White.copy(alpha = 0.7f),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 3. Círculo central con profundidad
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(cPrimario, EtdaNavy),
                    center = Offset(cx, cy),
                    radius = radioInterior * 0.88f
                ),
                radius = radioInterior * 0.82f,
                center = Offset(cx, cy)
            )

            // Anillo dorado/brillante interno
            drawCircle(
                color = cBrillo,
                radius = radioInterior * 0.78f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.8.dp.toPx())
            )
        }

        // Ícono central y estrellitas de rango
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-tamano.value * 0.05f).dp)
        ) {
            Icon(
                imageVector = if (bloqueada) Icons.Default.Lock else icono,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(tamano * 0.34f)
            )
            if (nivelEstrellas > 0) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(nivelEstrellas.coerceAtMost(3)) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFE082),
                            modifier = Modifier.size(tamano * 0.11f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RewardsScreen(
    puntos: Int,
    onPuntosCambiados: (Int) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    var tabActiva by remember { mutableIntStateOf(0) }

    val listaCategorias = remember {
        listOf(
            CategoriaEvolutiva(
                id = "cat_tutorias",
                categoria = "Tutorías",
                tituloBaseInsignia = "Mentor Ocelote",
                subtituloMedalla = "Compromiso Tutorial",
                descripcionBase = "Seguimiento académico y sesiones de tutoría en la ETDA.",
                asistenciasIniciales = 5,
                nivelInicialReclamado = 0,
                icono = Icons.Default.School,
                colorCategoria = EtdaBlue,
                prefijoFolio = "ETDA-TUT-2026"
            ),
            CategoriaEvolutiva(
                id = "cat_conferencias",
                categoria = "Conferencias",
                tituloBaseInsignia = "Vanguardia Tech",
                subtituloMedalla = "Líder en Ponencias",
                descripcionBase = "Participación en congresos, foros y pláticas tecnológicas.",
                asistenciasIniciales = 7,
                nivelInicialReclamado = 1,
                icono = Icons.Default.RecordVoiceOver,
                colorCategoria = EtdaOrange,
                prefijoFolio = "ETDA-CONF-2026"
            ),
            CategoriaEvolutiva(
                id = "cat_talleres",
                categoria = "Talleres",
                tituloBaseInsignia = "Arquitecto Dev",
                subtituloMedalla = "Master en Talleres",
                descripcionBase = "Talleres prácticos de software, redes, IA y ciberseguridad.",
                asistenciasIniciales = 4,
                nivelInicialReclamado = 0,
                icono = Icons.Default.Terminal,
                colorCategoria = Color(0xFF0284C7),
                prefijoFolio = "ETDA-TAL-2026"
            ),
            CategoriaEvolutiva(
                id = "cat_academicos",
                categoria = "Académicos",
                tituloBaseInsignia = "Mente Brillante",
                subtituloMedalla = "Excelencia Académica",
                descripcionBase = "Coloquios, ferias de proyectos y presentaciones de semestre.",
                asistenciasIniciales = 5,
                nivelInicialReclamado = 1,
                icono = Icons.Default.AutoStories,
                colorCategoria = EtdaNavy,
                prefijoFolio = "ETDA-ACA-2026"
            ),
            CategoriaEvolutiva(
                id = "cat_culturales",
                categoria = "Culturales",
                tituloBaseInsignia = "Legado Chiapas",
                subtituloMedalla = "Embajador Cultural",
                descripcionBase = "Muestras culturales, arte, música y festivales UNACH.",
                asistenciasIniciales = 4,
                nivelInicialReclamado = 0,
                icono = Icons.Default.TheaterComedy,
                colorCategoria = Color(0xFF2563EB),
                prefijoFolio = "ETDA-CUL-2026"
            ),
            CategoriaEvolutiva(
                id = "cat_deporte",
                categoria = "Deporte",
                tituloBaseInsignia = "Fuerza Ocelote",
                subtituloMedalla = "Espíritu Deportivo",
                descripcionBase = "Torneos deportivos, activación física y carreras atléticas.",
                asistenciasIniciales = 2,
                nivelInicialReclamado = 0,
                icono = Icons.Default.EmojiEvents,
                colorCategoria = Color(0xFFEA580C),
                prefijoFolio = "ETDA-DEP-2026"
            )
        )
    }

    val asistenciasMap = remember {
        mutableStateMapOf<String, Int>().apply {
            listaCategorias.forEach { put(it.categoria, it.asistenciasIniciales) }
        }
    }

    val nivelesReclamadosMap = remember {
        mutableStateMapOf<String, Int>().apply {
            listaCategorias.forEach { put(it.categoria, it.nivelInicialReclamado) }
        }
    }

    val recompensasCanjeadas = remember { mutableStateListOf<Int>() }
    var modalLogro by remember { mutableStateOf<LogroDesbloqueadoModal?>(null) }

    val totalInsigniasGanadas = nivelesReclamadosMap.values.sum()
    val totalAsistenciasAcumuladas = asistenciasMap.values.sum()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(EtdaBg),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. ENCABEZADO HERO + VITRINA DE MEDALLAS COLECCIONABLES
        item {
            TarjetaEncabezadoYVitrina(
                puntos = puntos,
                totalInsignias = totalInsigniasGanadas,
                totalAsistencias = totalAsistenciasAcumuladas,
                categorias = listaCategorias,
                nivelesReclamadosMap = nivelesReclamadosMap,
                onClickMedallaVitrina = { cat ->
                    val niv = nivelesReclamadosMap[cat.categoria] ?: 0
                    if (niv > 0) {
                        val esc = nivelesProgresivos[(niv - 1).coerceAtLeast(0)]
                        modalLogro = LogroDesbloqueadoModal(
                            categoria = cat.categoria,
                            nombreInsignia = "Medalla ${cat.tituloBaseInsignia}",
                            nombreRango = esc.nombreRango,
                            nivel = niv,
                            metaAlcanzada = esc.metaAsistencias,
                            siguienteMeta = nivelesProgresivos.getOrNull(niv)?.metaAsistencias,
                            puntosGanados = esc.puntosPremio,
                            folio = "${cat.prefijoFolio}-N$niv",
                            icono = cat.icono,
                            colorPrimario = esc.colorPrimario,
                            colorBrillo = esc.colorBrillo
                        )
                    }
                }
            )
        }

        // 2. SELECTOR DE PESTAÑAS (100% Responsivo en 1 sola línea)
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, EtdaNavy.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tab0 = tabActiva == 0
                    val bg0 by animateColorAsState(
                        targetValue = if (tab0) EtdaNavy else Color.Transparent,
                        animationSpec = tween(250),
                        label = "tab0Bg"
                    )
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { tabActiva = 0 },
                        color = bg0,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MilitaryTech,
                                contentDescription = null,
                                tint = if (tab0) EtdaOrange else EtdaNavy.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Insignias (5•8•12)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tab0) Color.White else EtdaNavy.copy(alpha = 0.7f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val tab1 = tabActiva == 1
                    val bg1 by animateColorAsState(
                        targetValue = if (tab1) EtdaNavy else Color.Transparent,
                        animationSpec = tween(250),
                        label = "tab1Bg"
                    )
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { tabActiva = 1 },
                        color = bg1,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Redeem,
                                contentDescription = null,
                                tint = if (tab1) EtdaOrange else EtdaBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Canjear Puntos",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tab1) Color.White else EtdaNavy.copy(alpha = 0.7f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 3. CONTENIDO SEGÚN PESTAÑA
        if (tabActiva == 0) {
            item {
                // ✅ CORREGIDO: En Column para que el globo de Bronce • Plata • Oro nunca se aplaste
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Medallas por Categoría de Evento",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = EtdaNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Surface(
                        color = EtdaOrange.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "★ Bronce 5   •   ★★ Plata 8   •   ★★★ Oro 12",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EtdaOrange,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "Gana tu primera medalla al cumplir 5 asistencias y evoluciona tu insignia hasta el rango Oro.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
            }

            itemsIndexed(
                items = listaCategorias,
                key = { _, item -> item.id }
            ) { index, catItem ->
                val asistencias = asistenciasMap[catItem.categoria] ?: catItem.asistenciasIniciales
                val nivelReclamado = nivelesReclamadosMap[catItem.categoria] ?: catItem.nivelInicialReclamado

                val indiceEscalafon = nivelReclamado.coerceAtMost(nivelesProgresivos.lastIndex)
                val escalafonActual = nivelesProgresivos[indiceEscalafon]
                val maximoAlcanzado = nivelReclamado >= nivelesProgresivos.size

                TarjetaCategoriaConMedalla(
                    item = catItem,
                    asistenciasActuales = asistencias,
                    nivelReclamado = nivelReclamado,
                    escalafonActivo = escalafonActual,
                    maximoCompletado = maximoAlcanzado,
                    index = index,
                    onSimularAsistencia = {
                        if (asistencias < 12) {
                            val nuevo = asistencias + 1
                            asistenciasMap[catItem.categoria] = nuevo
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "+1 asistencia en ${catItem.categoria} ($nuevo/${escalafonActual.metaAsistencias})"
                                )
                            }
                        }
                    },
                    onReclamarNivel = {
                        if (!maximoAlcanzado && asistencias >= escalafonActual.metaAsistencias) {
                            val nuevoNivelReclamado = nivelReclamado + 1
                            nivelesReclamadosMap[catItem.categoria] = nuevoNivelReclamado
                            onPuntosCambiados(puntos + escalafonActual.puntosPremio)

                            val siguienteMeta = nivelesProgresivos.getOrNull(nuevoNivelReclamado)?.metaAsistencias
                            modalLogro = LogroDesbloqueadoModal(
                                categoria = catItem.categoria,
                                nombreInsignia = "Medalla ${catItem.tituloBaseInsignia}",
                                nombreRango = escalafonActual.nombreRango,
                                nivel = nuevoNivelReclamado,
                                metaAlcanzada = escalafonActual.metaAsistencias,
                                siguienteMeta = siguienteMeta,
                                puntosGanados = escalafonActual.puntosPremio,
                                folio = "${catItem.prefijoFolio}-N${escalafonActual.nivel}",
                                icono = catItem.icono,
                                colorPrimario = escalafonActual.colorPrimario,
                                colorBrillo = escalafonActual.colorBrillo
                            )
                        } else if (maximoAlcanzado) {
                            modalLogro = LogroDesbloqueadoModal(
                                categoria = catItem.categoria,
                                nombreInsignia = "Medalla ${catItem.tituloBaseInsignia}",
                                nombreRango = "Nivel III • Oro ETDA",
                                nivel = 3,
                                metaAlcanzada = 12,
                                siguienteMeta = null,
                                puntosGanados = 250,
                                folio = "${catItem.prefijoFolio}-ORO",
                                icono = catItem.icono,
                                colorPrimario = OroPrimario,
                                colorBrillo = OroBrillo
                            )
                        }
                    }
                )
            }
        } else {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        text = "Recompensas Disponibles",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = EtdaNavy
                    )
                    Text(
                        text = "Canjea los puntos acumulados con tus medallas de asistencia",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            itemsIndexed(
                items = catalogoRecompensasUnach,
                key = { _, rec -> rec.id }
            ) { index, recompensa ->
                val yaCanjeada = recompensasCanjeadas.contains(recompensa.id)
                TarjetaRecompensaEstilizada(
                    recompensa = recompensa,
                    puntosDisponibles = puntos,
                    yaCanjeada = yaCanjeada,
                    index = index,
                    onCanjear = {
                        if (puntos >= recompensa.costoPuntos && !yaCanjeada) {
                            recompensasCanjeadas.add(recompensa.id)
                            onPuntosCambiados(puntos - recompensa.costoPuntos)
                            scope.launch {
                                snackbarHostState.showSnackbar("¡Canjeaste exitosamente: ${recompensa.titulo}!")
                            }
                        } else if (!yaCanjeada) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Te faltan ${recompensa.costoPuntos - puntos} puntos para esta recompensa")
                            }
                        }
                    }
                )
            }
        }
    }

    // MODAL CON LA MEDALLA EN GRANDE AL RECLAMAR
    modalLogro?.let { logro ->
        ModalMedallaDesbloqueada(
            logro = logro,
            onDismiss = { modalLogro = null }
        )
    }
}

@Composable
private fun TarjetaEncabezadoYVitrina(
    puntos: Int,
    totalInsignias: Int,
    totalAsistencias: Int,
    categorias: List<CategoriaEvolutiva>,
    nivelesReclamadosMap: Map<String, Int>,
    onClickMedallaVitrina: (CategoriaEvolutiva) -> Unit
) {
    val puntosAnimados by animateIntAsState(
        targetValue = puntos,
        animationSpec = tween(durationMillis = 600),
        label = "puntosAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(EtdaNavy, Color(0xFF193773), EtdaBlue)
                ),
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .padding(vertical = 18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = EtdaOrange.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, EtdaOrange)
                    ) {
                        Text(
                            text = "VITRINA DE HONOR • ETDA UNACH",
                            color = Color(0xFFFFB74D),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.7.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Colección de Insignias",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$totalInsignias medallas ganadas • $totalAsistencias asistencias registradas",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = EtdaOrange,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$puntosAnimados",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Text(
                            text = "PUNTOS",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CARRUSEL DE MEDALLAS COLECCIONABLES (VITRINA VISUAL)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(categorias) { _, cat ->
                    val nivelGanado = nivelesReclamadosMap[cat.categoria] ?: 0
                    val desbloqueada = nivelGanado > 0
                    val escalafonMedalla = if (desbloqueada) {
                        nivelesProgresivos[(nivelGanado - 1).coerceAtLeast(0)]
                    } else {
                        nivelesProgresivos[0]
                    }

                    Surface(
                        modifier = Modifier
                            .width(106.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onClickMedallaVitrina(cat) },
                        color = if (desbloqueada) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.07f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(
                            1.dp,
                            if (desbloqueada) escalafonMedalla.colorBrillo.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MedallaInsigniaETDA(
                                icono = cat.icono,
                                colorPrincipal = escalafonMedalla.colorPrimario,
                                colorSecundario = escalafonMedalla.colorBrillo,
                                colorListon = cat.colorCategoria,
                                nivelEstrellas = nivelGanado,
                                bloqueada = !desbloqueada,
                                destacada = desbloqueada,
                                tamano = 56.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cat.tituloBaseInsignia,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (desbloqueada) escalafonMedalla.etiquetaCorta else "En curso",
                                color = if (desbloqueada) Color(0xFFFFE082) else Color.White.copy(alpha = 0.55f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaCategoriaConMedalla(
    item: CategoriaEvolutiva,
    asistenciasActuales: Int,
    nivelReclamado: Int,
    escalafonActivo: EscalafonMeta,
    maximoCompletado: Boolean,
    index: Int,
    onSimularAsistencia: () -> Unit,
    onReclamarNivel: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val alphaAnim by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = index * 60),
        label = "alphaProg"
    )
    val slideAnim by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = tween(durationMillis = 400, delayMillis = index * 60),
        label = "slideProg"
    )

    val metaActual = escalafonActivo.metaAsistencias
    val metaAlcanzada = asistenciasActuales >= metaActual && !maximoCompletado

    val infiniteTransition = rememberInfiniteTransition(label = "pulsoNivel")
    val pulsoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (metaAlcanzada) 1.07f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "escalaNivel"
    )

    val progresoAnimado by animateFloatAsState(
        targetValue = (asistenciasActuales.toFloat() / metaActual.toFloat()).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "barraNivel"
    )

    val faltantes = (metaActual - asistenciasActuales).coerceAtLeast(0)
    val siguienteMetaTexto = when (metaActual) {
        5 -> "8 asistencias (Plata)"
        8 -> "12 asistencias (Oro)"
        else -> "Nivel Máximo"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .graphicsLayer {
                alpha = alphaAnim
                translationY = slideAnim
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (metaAlcanzada) 6.dp else 3.dp),
        border = when {
            metaAlcanzada -> BorderStroke(2.dp, EtdaOrange)
            maximoCompletado -> BorderStroke(1.5.dp, OroPrimario)
            nivelReclamado > 0 -> BorderStroke(1.dp, EtdaSuccess.copy(alpha = 0.45f))
            else -> null
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Medalla personalizada dibujada con Canvas
                    Box(modifier = Modifier.scale(pulsoScale)) {
                        MedallaInsigniaETDA(
                            icono = item.icono,
                            colorPrincipal = escalafonActivo.colorPrimario,
                            colorSecundario = escalafonActivo.colorBrillo,
                            colorListon = item.colorCategoria,
                            nivelEstrellas = if (maximoCompletado) 3 else (nivelReclamado + 1),
                            bloqueada = false,
                            destacada = metaAlcanzada,
                            tamano = 60.dp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Surface(
                                color = item.colorCategoria.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = item.categoria.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = item.colorCategoria,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                color = escalafonActivo.colorPrimario.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (maximoCompletado) "★★★ ORO" else escalafonActivo.nombreRango.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = escalafonActivo.colorPrimario,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Medalla ${item.tituloBaseInsignia}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EtdaNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.subtituloMedalla,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EtdaBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Contador dinámico 5/5 -> 5/8 -> 8/12 (Protegido con softWrap = false)
                AnimatedContent(
                    targetState = "$asistenciasActuales / $metaActual",
                    label = "contadorMeta"
                ) { textoContador ->
                    Surface(
                        color = when {
                            maximoCompletado -> OroPrimario.copy(alpha = 0.18f)
                            metaAlcanzada -> EtdaOrange.copy(alpha = 0.16f)
                            else -> EtdaBg
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = textoContador,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                maximoCompletado -> BroncePrimario
                                metaAlcanzada -> EtdaOrange
                                else -> EtdaNavy
                            },
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (nivelReclamado > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = EtdaSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    val rangosGanados = nivelesProgresivos.take(nivelReclamado).joinToString(" + ") {
                        "${it.etiquetaCorta} (${it.metaAsistencias})"
                    }
                    Text(
                        text = "Insignias ganadas: $rangosGanados",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EtdaSuccess,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = item.descripcionBase,
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // SELLOS DE ASISTENCIA (5, luego 8, luego 12)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (numSello in 1..metaActual) {
                    val selloCompletado = numSello <= asistenciasActuales
                    val esHito = numSello == 5 || numSello == 8 || numSello == 12

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    selloCompletado && metaAlcanzada -> EtdaOrange
                                    selloCompletado -> item.colorCategoria
                                    else -> EtdaBg
                                }
                            )
                            .border(
                                width = if (esHito) 1.5.dp else 1.dp,
                                color = when {
                                    selloCompletado -> Color.Transparent
                                    esHito -> EtdaOrange.copy(alpha = 0.6f)
                                    else -> Color(0xFFCBD5E1)
                                },
                                shape = RoundedCornerShape(6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selloCompletado) {
                            Icon(
                                imageVector = if (esHito) Icons.Default.Star else Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            Text(
                                text = "$numSello",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (esHito) EtdaOrange else Color.Gray,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progresoAnimado },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    maximoCompletado -> OroPrimario
                    metaAlcanzada -> EtdaOrange
                    else -> item.colorCategoria
                },
                trackColor = Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        maximoCompletado -> "🏆 ¡Colección Oro completada (12/12)!"
                        metaAlcanzada -> "¡Meta de $metaActual lograda! Reclama +${escalafonActivo.puntosPremio} pts y sube a $siguienteMetaTexto"
                        faltantes == 1 -> "¡A solo 1 asistencia de ganar la medalla ${escalafonActivo.etiquetaCorta}!"
                        else -> "Faltan $faltantes asistencias para medalla ${escalafonActivo.etiquetaCorta} (+${escalafonActivo.puntosPremio} pts)"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        maximoCompletado -> BroncePrimario
                        metaAlcanzada -> EtdaOrange
                        else -> EtdaNavy.copy(alpha = 0.75f)
                    },
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (metaAlcanzada || maximoCompletado) {
                    Button(
                        onClick = onReclamarNivel,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (maximoCompletado) EtdaNavy else EtdaOrange
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .scale(pulsoScale)
                    ) {
                        Icon(
                            imageVector = if (maximoCompletado) Icons.Default.WorkspacePremium else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (maximoCompletado) "Ver Medalla" else "¡Reclamar!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onSimularAsistencia,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EtdaBlue),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EtdaBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddTask,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+1 Asist.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaRecompensaEstilizada(
    recompensa: RecompensaCanjeable,
    puntosDisponibles: Int,
    yaCanjeada: Boolean,
    index: Int,
    onCanjear: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val alphaAnim by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 380, delayMillis = index * 60),
        label = "alphaRec"
    )

    val alcanza = puntosDisponibles >= recompensa.costoPuntos

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .graphicsLayer { alpha = alphaAnim },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = if (yaCanjeada) BorderStroke(1.dp, EtdaSuccess.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (yaCanjeada) EtdaSuccess.copy(alpha = 0.12f) else EtdaBlue.copy(alpha = 0.1f),
                modifier = Modifier.size(54.dp)
            ) {
                Icon(
                    imageVector = recompensa.icono,
                    contentDescription = null,
                    tint = if (yaCanjeada) EtdaSuccess else EtdaBlue,
                    modifier = Modifier.padding(13.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = EtdaNavy.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = recompensa.categoriaRequerida,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EtdaNavy,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = recompensa.titulo,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = EtdaNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = recompensa.descripcion,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = EtdaOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${recompensa.costoPuntos} pts",
                            fontSize = 14.sp,
                            color = EtdaOrange,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = onCanjear,
                        enabled = yaCanjeada || alcanza,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                yaCanjeada -> EtdaSuccess
                                alcanza -> EtdaNavy
                                else -> Color(0xFFE2E8F0)
                            },
                            disabledContainerColor = Color(0xFFE2E8F0)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = when {
                                yaCanjeada -> "✓ Canjeado"
                                alcanza -> "Canjear"
                                else -> "Faltan ${recompensa.costoPuntos - puntosDisponibles} pts"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (yaCanjeada || alcanza) Color.White else Color.Gray,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModalMedallaDesbloqueada(
    logro: LogroDesbloqueadoModal,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MedallaInsigniaETDA(
                    icono = logro.icono,
                    colorPrincipal = logro.colorPrimario,
                    colorSecundario = logro.colorBrillo,
                    colorListon = EtdaOrange,
                    nivelEstrellas = logro.nivel,
                    bloqueada = false,
                    destacada = true,
                    tamano = 104.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = EtdaSuccess.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "¡INSIGNIA ${logro.nombreRango.uppercase()}!",
                        color = EtdaSuccess,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = logro.nombreInsignia,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = EtdaNavy,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Has cumplido ${logro.metaAlcanzada} asistencias verificadas en la categoría de ${logro.categoria}.",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = EtdaBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, EtdaBlue.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "FOLIO DE INSIGNIA OFICIAL ETDA",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = logro.folio,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = EtdaNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+${logro.puntosGanados} Puntos ETDA acreditados",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EtdaOrange
                        )
                    }
                }

                if (logro.siguienteMeta != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = EtdaOrange.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = EtdaOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "¡Siguiente evolución: ${logro.siguienteMeta} asistencias!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = EtdaOrange
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EtdaOrange),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = if (logro.siguienteMeta != null) "¡Ir por las ${logro.siguienteMeta} asistencias!" else "¡Excelente!",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    )
}