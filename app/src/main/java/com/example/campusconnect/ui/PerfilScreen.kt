package com.example.campusconnect.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusconnect.BuildConfig
import com.example.campusconnect.ComposeLoginActivity
import com.example.campusconnect.TokenManager
import com.example.campusconnect.formatearEtiquetaGrupo
import com.example.campusconnect.network.RetrofitClient
import com.example.campusconnect.update.AppUpdateChecker
import com.example.campusconnect.update.DialogoActualizacionFlexible
import com.example.campusconnect.update.InfoActualizacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

// Paleta Oficial ETDA - UNACH Intacta
private val EtdaNavy = Color(0xFF122652)
private val EtdaBlue = Color(0xFF0093FF)
private val EtdaOrange = Color(0xFFF57C00)
private val EtdaBg = Color(0xFFF4F7FB)
private val EtdaSuccess = Color(0xFF10B981)

// Tonos de Rango Sincronizados con RewardsScreen.kt
private val RangoBronceColor = Color(0xFFD97706)
private val RangoPlataColor = Color(0xFF475569)
private val RangoOroColor = Color(0xFFF59E0B)

private data class DatosAcademicosAlumno(
    val nombreCompleto: String,
    val iniciales: String,
    val correo: String,
    val rol: String,
    val esEgresado: Boolean,
    val correoSecundario: String,
    val telefonoPrincipal: String,
    val telefonoSecundario: String,
    val matricula: String,
    val escuela: String,
    val carrera: String,
    val semestreGrupo: String,
    val modalidad: String,
    val cicloEscolar: String,
    val tiene2faActivo: Boolean
)

private fun calcularInicialesNombre(nombreCompleto: String): String {
    val partes = nombreCompleto.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        partes.size >= 2 -> "${partes[0].first()}${partes[1].first()}".uppercase()
        partes.size == 1 -> partes[0].take(2).uppercase()
        else -> "UN"
    }
}

private fun formatearRolAlumno(rolGuardado: String?, esEgresado: Boolean = false): String {
    if (esEgresado) return "Alumno Egresado"
    return when (rolGuardado?.lowercase()) {
        "admin", "administrador", "superadmin" -> "Administrador ETDA"
        "organizador", "docente", "editor" -> "Organizador Académico"
        "auxiliar" -> "Auxiliar Académico"
        "alumno", "estudiante", "usuario_regular", "usuario regular" -> "Estudiante Activo"
        else -> if (!rolGuardado.isNullOrBlank()) rolGuardado else "Estudiante Activo"
    }
}

private fun derivarNombreDesdeCorreo(correoReal: String): String {
    val usuarioCorreo = correoReal.substringBefore("@").trim()
    val partesNombre = usuarioCorreo
        .replace(Regex("[0-9]"), "")
        .split(".", "_", "-")
        .filter { it.isNotBlank() }
        .map { it.replaceFirstChar { char -> char.uppercase() } }
    return when {
        partesNombre.size >= 2 -> partesNombre.joinToString(" ")
        partesNombre.size == 1 -> partesNombre[0]
        else -> "Estudiante UNACH"
    }
}

private fun obtenerDatosSesionAlumno(context: Context): DatosAcademicosAlumno {
    val correoReal = TokenManager.obtenerEmail(context).trim().lowercase()
    val rolGuardado = TokenManager.obtenerRol(context)
    val prefs = context.getSharedPreferences("cc_expediente_alumno", Context.MODE_PRIVATE)

    val nombreGuardado = prefs.getString("nombre_$correoReal", null)?.takeIf { it.isNotBlank() }
    val esEgresadoGuardado = prefs.getBoolean("egresado_$correoReal", false)
    val matriculaGuardada = prefs.getString("matricula_$correoReal", null)?.takeIf { it.isNotBlank() }
    val carreraGuardada = prefs.getString("carrera_$correoReal", null)?.takeIf { it.isNotBlank() }
    val grupoGuardado = prefs.getString("grupo_$correoReal", null)?.takeIf { it.isNotBlank() }
    val escuelaUsuario = prefs.getString("escuela_$correoReal", null)?.takeIf { it.isNotBlank() }
    val correoSecGuardado = prefs.getString("email_sec_$correoReal", null).orEmpty()
    val tel1Guardado = prefs.getString("tel1_$correoReal", null).orEmpty()
    val tel2Guardado = prefs.getString("tel2_$correoReal", null).orEmpty()
    val tiene2faGuardado = prefs.getBoolean("tiene_2fa_$correoReal", true)

    val escuelaEstatica = escuelaUsuario
        ?: prefs.getString("inst_escuela", null)?.takeIf { it.isNotBlank() }
        ?: "Escuela de Tecnologías Digitales Aplicadas (ETDA)"
    val modalidadEstatica = prefs.getString("inst_modalidad", null)?.takeIf { it.isNotBlank() }
        ?: "Escolarizada (Campus I, Tuxtla Gutiérrez)"
    val cicloEstatico = prefs.getString("inst_ciclo", null)?.takeIf { it.isNotBlank() }
        ?: "Agosto - Diciembre 2026"

    val nombreFinal = nombreGuardado ?: derivarNombreDesdeCorreo(correoReal)
    val inicialesFinal = calcularInicialesNombre(nombreFinal)

    val matriculaFinal = if (esEgresadoGuardado) "" else (matriculaGuardada ?: "Sin registrar")
    val carreraFinal = if (esEgresadoGuardado) "" else (carreraGuardada ?: "Lic. en Ingeniería en Desarrollo y Tecnologías de Software")
    val semestreGrupoFinal = if (esEgresadoGuardado) {
        ""
    } else if (!grupoGuardado.isNullOrBlank()) {
        formatearEtiquetaGrupo(grupoGuardado)
    } else {
        "Sin asignar"
    }

    return DatosAcademicosAlumno(
        nombreCompleto = nombreFinal,
        iniciales = inicialesFinal,
        correo = correoReal.ifBlank { "usuario@unach.mx" },
        rol = formatearRolAlumno(rolGuardado, esEgresadoGuardado),
        esEgresado = esEgresadoGuardado,
        correoSecundario = correoSecGuardado,
        telefonoPrincipal = tel1Guardado,
        telefonoSecundario = tel2Guardado,
        matricula = matriculaFinal,
        escuela = escuelaEstatica,
        carrera = carreraFinal,
        semestreGrupo = semestreGrupoFinal,
        modalidad = modalidadEstatica,
        cicloEscolar = cicloEstatico,
        tiene2faActivo = tiene2faGuardado
    )
}

private data class CategoriaBasePerfil(
    val categoria: String,
    val icono: ImageVector,
    val color: Color
)

private val CATEGORIAS_PERFIL_CONFIG = listOf(
    CategoriaBasePerfil("Talleres", Icons.Default.Terminal, Color(0xFF0284C7)),
    CategoriaBasePerfil("Conferencias", Icons.Default.RecordVoiceOver, EtdaOrange),
    CategoriaBasePerfil("Tutorías", Icons.Default.School, EtdaBlue),
    CategoriaBasePerfil("Académicos", Icons.Default.AutoStories, EtdaNavy),
    CategoriaBasePerfil("Deporte", Icons.Default.EmojiEvents, Color(0xFFEA580C)),
    CategoriaBasePerfil("Culturales", Icons.Default.TheaterComedy, Color(0xFF2563EB))
)

/**
 * Calcula la meta evolutiva (5 = Bronce, 8 = Plata, 12 = Oro) y la etiqueta de nivel para cada categoría.
 */
private fun obtenerMetaYEtiquetaEvolutiva(asistencias: Int, nivelReclamado: Int): Triple<Int, String, Color> {
    val nivelAlcanzado = maxOf(
        nivelReclamado,
        when {
            asistencias >= 12 -> 3
            asistencias >= 8 -> 2
            asistencias >= 5 -> 1
            else -> 0
        }
    )
    return when {
        asistencias >= 12 || nivelAlcanzado >= 3 -> Triple(12, "★★★ Oro", RangoOroColor)
        asistencias >= 8 || nivelAlcanzado == 2 -> Triple(12, "★★ Plata ➔ Oro", RangoPlataColor)
        asistencias >= 5 || nivelAlcanzado == 1 -> Triple(8, "★ Bronce ➔ Plata", RangoBronceColor)
        else -> Triple(5, "Meta Bronce", EtdaBlue)
    }
}

@Composable
fun PerfilScreen(puntos: Int) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var datosAlumno by remember { mutableStateOf(obtenerDatosSesionAlumno(context)) }
    var sincronizandoExpediente by remember { mutableStateOf(false) }

    val prefsRecompensas = remember {
        context.getSharedPreferences("cc_recompensas_validadas", Context.MODE_PRIVATE)
    }

    // Mapa reactivo con las asistencias reales por categoría sincronizadas con RewardsScreen y el servidor
    val asistenciasPorCategoria = remember {
        mutableStateMapOf<String, Int>().apply {
            CATEGORIAS_PERFIL_CONFIG.forEach { cat ->
                put(cat.categoria, prefsRecompensas.getInt("asistencias_cat_${cat.categoria}", 0))
            }
        }
    }

    val nivelesReclamadosPorCategoria = remember {
        mutableStateMapOf<String, Int>().apply {
            CATEGORIAS_PERFIL_CONFIG.forEach { cat ->
                put(cat.categoria, prefsRecompensas.getInt("nivel_reclamado_${cat.categoria}", 0))
            }
        }
    }

    // Estados para verificación manual de actualización
    var buscandoActualizacion by remember { mutableStateOf(false) }
    var infoActManual by remember { mutableStateOf<InfoActualizacion?>(null) }

    suspend fun sincronizarExpedienteYRecompensasDesdeServidor() {
        sincronizandoExpediente = true
        val api = RetrofitClient.obtenerApi(context)
        val prefs = context.getSharedPreferences("cc_expediente_alumno", Context.MODE_PRIVATE)
        val correoReal = TokenManager.obtenerEmail(context).trim().lowercase()

        // 1. Sincronizar expediente académico
        try {
            val exp = api.obtenerExpedienteAlumno()
            val esEgresadoRemoto = exp.esEgresado == true
            val editor = prefs.edit()
            if (!exp.nombre.isNullOrBlank()) editor.putString("nombre_$correoReal", exp.nombre)
            editor.putBoolean("egresado_$correoReal", esEgresadoRemoto)

            if (esEgresadoRemoto) {
                editor.remove("matricula_$correoReal")
                editor.remove("carrera_$correoReal")
                editor.remove("grupo_$correoReal")
            } else {
                if (!exp.matricula.isNullOrBlank()) editor.putString("matricula_$correoReal", exp.matricula)
                if (!exp.carrera.isNullOrBlank()) editor.putString("carrera_$correoReal", exp.carrera)
                if (!exp.semestreGrupo.isNullOrBlank()) editor.putString("grupo_$correoReal", exp.semestreGrupo)
            }

            if (!exp.idFacultad.isNullOrBlank()) editor.putString("facultad_$correoReal", exp.idFacultad)
            if (!exp.escuela.isNullOrBlank()) {
                editor.putString("inst_escuela", exp.escuela)
                editor.putString("escuela_$correoReal", exp.escuela)
            }
            if (!exp.modalidadSede.isNullOrBlank()) editor.putString("inst_modalidad", exp.modalidadSede)
            if (!exp.cicloEscolar.isNullOrBlank()) editor.putString("inst_ciclo", exp.cicloEscolar)
            if (!exp.emailSecundario.isNullOrBlank()) editor.putString("email_sec_$correoReal", exp.emailSecundario)
            if (!exp.telefonoPrincipal.isNullOrBlank()) editor.putString("tel1_$correoReal", exp.telefonoPrincipal)
            if (!exp.telefonoSecundario.isNullOrBlank()) editor.putString("tel2_$correoReal", exp.telefonoSecundario)
            editor.putBoolean("tiene_2fa_$correoReal", exp.tiene2faActivo != false)
            editor.apply()

            val actualizado = obtenerDatosSesionAlumno(context)
            datosAlumno = actualizado.copy(
                rol = formatearRolAlumno(exp.rol ?: TokenManager.obtenerRol(context), esEgresadoRemoto)
            )
        } catch (_: Exception) {
            try {
                val cat = api.obtenerCatalogosFacultad()
                cat.datosInstitucionales?.let { inst ->
                    prefs.edit()
                        .putString("inst_escuela", inst.escuela ?: datosAlumno.escuela)
                        .putString("inst_modalidad", inst.modalidadSede ?: datosAlumno.modalidad)
                        .putString("inst_ciclo", inst.cicloEscolar ?: datosAlumno.cicloEscolar)
                        .apply()
                    datosAlumno = obtenerDatosSesionAlumno(context)
                }
            } catch (_: Exception) {}
        }

        // 2. Sincronizar asistencias reales validadas por categoría (GET /eventos/mis-recompensas)
        try {
            val remotas = withContext(Dispatchers.IO) {
                api.obtenerMisRecompensas()
            }
            val mapaServidor = remotas.asistenciasPorCategoria.orEmpty()
            val editorRec = prefsRecompensas.edit()
            CATEGORIAS_PERFIL_CONFIG.forEach { cat ->
                val conteoRemoto = mapaServidor[cat.categoria] ?: 0
                val conteoLocal = prefsRecompensas.getInt("asistencias_cat_${cat.categoria}", 0)
                val sincronizado = maxOf(conteoRemoto, conteoLocal).coerceAtMost(12)
                asistenciasPorCategoria[cat.categoria] = sincronizado
                nivelesReclamadosPorCategoria[cat.categoria] = prefsRecompensas.getInt("nivel_reclamado_${cat.categoria}", 0)
                editorRec.putInt("asistencias_cat_${cat.categoria}", sincronizado)
            }
            editorRec.apply()
        } catch (_: Exception) {
            CATEGORIAS_PERFIL_CONFIG.forEach { cat ->
                asistenciasPorCategoria[cat.categoria] = prefsRecompensas.getInt("asistencias_cat_${cat.categoria}", 0)
                nivelesReclamadosPorCategoria[cat.categoria] = prefsRecompensas.getInt("nivel_reclamado_${cat.categoria}", 0)
            }
        } finally {
            sincronizandoExpediente = false
        }
    }

    LaunchedEffect(Unit) {
        sincronizarExpedienteYRecompensasDesdeServidor()
        while (true) {
            delay(4_000L)
            CATEGORIAS_PERFIL_CONFIG.forEach { cat ->
                val localAsis = prefsRecompensas.getInt("asistencias_cat_${cat.categoria}", 0)
                val localNiv = prefsRecompensas.getInt("nivel_reclamado_${cat.categoria}", 0)
                if (localAsis != asistenciasPorCategoria[cat.categoria]) {
                    asistenciasPorCategoria[cat.categoria] = localAsis
                }
                if (localNiv != nivelesReclamadosPorCategoria[cat.categoria]) {
                    nivelesReclamadosPorCategoria[cat.categoria] = localNiv
                }
            }
        }
    }

    var mostrarQrDialog by remember { mutableStateOf(false) }
    var mostrarCredencialExpandida by remember { mutableStateOf(true) }

    val totalEventosAsistidos = CATEGORIAS_PERFIL_CONFIG.sumOf { cat ->
        asistenciasPorCategoria[cat.categoria] ?: 0
    }

    // Insignias desbloqueadas / reclamadas reales (Bronce=5, Plata=8, Oro=12 en cada categoría)
    val insigniasDesbloqueadas = CATEGORIAS_PERFIL_CONFIG.sumOf { cat ->
        val asis = asistenciasPorCategoria[cat.categoria] ?: 0
        val nivReclamado = nivelesReclamadosPorCategoria[cat.categoria] ?: 0
        val nivPorAsistencia = when {
            asis >= 12 -> 3
            asis >= 8 -> 2
            asis >= 5 -> 1
            else -> 0
        }
        maxOf(nivReclamado, nivPorAsistencia)
    }

    val maximoNivelCategoria = CATEGORIAS_PERFIL_CONFIG.maxOfOrNull { cat ->
        val asis = asistenciasPorCategoria[cat.categoria] ?: 0
        val nivReclamado = nivelesReclamadosPorCategoria[cat.categoria] ?: 0
        maxOf(
            nivReclamado,
            when {
                asis >= 12 -> 3
                asis >= 8 -> 2
                asis >= 5 -> 1
                else -> 0
            }
        )
    } ?: 0

    val (etiquetaRangoGlobal, colorRangoGlobal) = when (maximoNivelCategoria) {
        3 -> "Nivel Oro ★★★" to RangoOroColor
        2 -> "Nivel Plata ★★" to RangoPlataColor
        1 -> "Nivel Bronce ★" to RangoBronceColor
        else -> "Sin rango aún" to EtdaBlue
    }

    val puntosCalculados = CATEGORIAS_PERFIL_CONFIG.sumOf { cat ->
        val asis = asistenciasPorCategoria[cat.categoria] ?: 0
        val niv = maxOf(
            nivelesReclamadosPorCategoria[cat.categoria] ?: 0,
            when {
                asis >= 12 -> 3
                asis >= 8 -> 2
                asis >= 5 -> 1
                else -> 0
            }
        )
        when (niv) {
            1 -> 100
            2 -> 250
            3 -> 500
            else -> 0
        }
    }
    val puntosEfectivos = maxOf(puntos, puntosCalculados)

    // Payload institucional para el código QR de la Credencial Digital
    val qrPayloadCredencial = remember(datosAlumno) {
        if (datosAlumno.esEgresado) {
            "CCUNACH|EGRESADO|${datosAlumno.correo}|${datosAlumno.nombreCompleto}"
        } else {
            val matLimpia = datosAlumno.matricula.takeIf { it.isNotBlank() && it != "Sin registrar" } ?: "S/M"
            "CCUNACH|ALUMNO|$matLimpia|${datosAlumno.correo}|${datosAlumno.nombreCompleto}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EtdaBg)
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(listOf(EtdaNavy, Color(0xFF193773), EtdaBlue)),
                    shape = RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp)
                )
                .padding(top = 24.dp, bottom = 52.dp, start = 20.dp, end = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = EtdaOrange, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (datosAlumno.esEgresado) "CREDENCIAL DE EGRESADO • ETDA UNACH" else "CREDENCIAL DIGITAL • ETDA UNACH",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(
                        modifier = Modifier.size(96.dp),
                        shape = CircleShape,
                        color = EtdaNavy,
                        border = BorderStroke(3.dp, EtdaOrange),
                        shadowElevation = 10.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(datosAlumno.iniciales, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = EtdaSuccess,
                        border = BorderStroke(2.dp, Color.White),
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Activo", tint = Color.White, modifier = Modifier.padding(4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = datosAlumno.nombreCompleto,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.3).sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = datosAlumno.correo,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = EtdaOrange, shape = RoundedCornerShape(50), shadowElevation = 4.dp) {
                        Text(
                            text = datosAlumno.rol.uppercase(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = if (datosAlumno.tiene2faActivo) EtdaSuccess.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (datosAlumno.tiene2faActivo) EtdaSuccess else Color.White.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (datosAlumno.tiene2faActivo) "2FA Activo" else "2FA Protegido",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-28).dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(1.dp, EtdaNavy.copy(alpha = 0.06f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IndicadorRapidoPerfil("$totalEventosAsistidos", "Asistencias", Icons.Default.EventAvailable, EtdaBlue)
                }
                VerticalDivider(modifier = Modifier.height(36.dp), color = Color(0xFFE2E8F0))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IndicadorRapidoPerfil("$insigniasDesbloqueadas", "Insignias", Icons.Default.WorkspacePremium, EtdaOrange)
                }
                VerticalDivider(modifier = Modifier.height(36.dp), color = Color(0xFFE2E8F0))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IndicadorRapidoPerfil("$puntosEfectivos pts", "Rewards", Icons.Default.Stars, EtdaNavy)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-12).dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            border = BorderStroke(1.dp, EtdaNavy.copy(alpha = 0.06f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { mostrarCredencialExpandida = !mostrarCredencialExpandida },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = EtdaNavy.copy(alpha = 0.08f), modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (datosAlumno.esEgresado) Icons.Default.WorkspacePremium else Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = EtdaNavy,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (datosAlumno.esEgresado) "Ficha de Egresado UNACH" else "Expediente Académico",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = EtdaNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Sincronizado con Administración UNACH",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { coroutineScope.launch { sincronizarExpedienteYRecompensasDesdeServidor() } },
                        enabled = !sincronizandoExpediente,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Sincronizar expediente",
                            tint = if (sincronizandoExpediente) Color.Gray else EtdaBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Surface(
                        onClick = { mostrarQrDialog = true },
                        color = EtdaOrange.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = "Mi QR", tint = EtdaOrange, modifier = Modifier.size(17.dp))
                            Text("Mi QR", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = EtdaOrange, maxLines = 1, softWrap = false)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = mostrarCredencialExpandida,
                    enter = expandVertically(animationSpec = tween(240)) + fadeIn(animationSpec = tween(220)),
                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(180))
                ) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        HorizontalDivider(color = Color(0xFFEEF2F6))
                        Spacer(modifier = Modifier.height(14.dp))

                        ItemDatoAcademico(Icons.Default.Domain, "Escuela / Facultad", datosAlumno.escuela, EtdaBlue)

                        if (datosAlumno.esEgresado) {
                            Spacer(modifier = Modifier.height(14.dp))
                            ItemDatoAcademico(
                                icono = Icons.Default.WorkspacePremium,
                                titulo = "Condición Académica",
                                valor = "Comunidad de Egresados UNACH",
                                colorIcono = EtdaOrange
                            )

                            if (datosAlumno.correoSecundario.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                ItemDatoAcademico(
                                    icono = Icons.Default.AlternateEmail,
                                    titulo = "Correo Alternativo de Contacto",
                                    valor = datosAlumno.correoSecundario,
                                    colorIcono = EtdaBlue
                                )
                            }

                            if (datosAlumno.telefonoPrincipal.isNotBlank() || datosAlumno.telefonoSecundario.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                val tels = listOf(datosAlumno.telefonoPrincipal, datosAlumno.telefonoSecundario)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                ItemDatoAcademico(
                                    icono = Icons.Default.Phone,
                                    titulo = "Teléfonos de Contacto",
                                    valor = tels,
                                    colorIcono = EtdaNavy
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(14.dp))
                            ItemDatoAcademico(Icons.Default.Computer, "Programa Educativo (Carrera)", datosAlumno.carrera, EtdaOrange)
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ItemDatoAcademico(Icons.Default.Badge, "Matrícula", datosAlumno.matricula, EtdaNavy)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    ItemDatoAcademico(Icons.Default.Groups, "Semestre y Grupo", datosAlumno.semestreGrupo, EtdaBlue)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        ItemDatoAcademico(Icons.Default.LocationOn, "Modalidad y Sede", datosAlumno.modalidad, EtdaSuccess)
                        Spacer(modifier = Modifier.height(14.dp))
                        ItemDatoAcademico(Icons.Default.DateRange, "Ciclo Escolar Vigente", datosAlumno.cicloEscolar, EtdaOrange)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // TARJETA DE ASISTENCIAS REALES POR CATEGORÍA (METAS EVOLUTIVAS 5 ➔ 8 ➔ 12 Y RANGO DINÁMICO)
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            border = BorderStroke(1.dp, EtdaNavy.copy(alpha = 0.06f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = EtdaOrange.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Insights, contentDescription = null, tint = EtdaOrange, modifier = Modifier.padding(9.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Asistencias por Categoría",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = EtdaNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Metas: ★ Bronce (5) • ★★ Plata (8) • ★★★ Oro (12)",
                            fontSize = 10.5.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        color = colorRangoGlobal.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, colorRangoGlobal.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = etiquetaRangoGlobal,
                            color = colorRangoGlobal,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                CATEGORIAS_PERFIL_CONFIG.forEach { itemCat ->
                    val asistenciasCat = asistenciasPorCategoria[itemCat.categoria] ?: 0
                    val nivelRecCat = nivelesReclamadosPorCategoria[itemCat.categoria] ?: 0
                    val (metaEvolutiva, etiquetaNivelCat, colorBadgeCat) = obtenerMetaYEtiquetaEvolutiva(asistenciasCat, nivelRecCat)

                    val porcentaje = (asistenciasCat.toFloat() / metaEvolutiva.toFloat()).coerceIn(0f, 1f)
                    val animProgreso by animateFloatAsState(
                        targetValue = porcentaje,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                        label = "barraCat_${itemCat.categoria}"
                    )
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(itemCat.icono, contentDescription = null, tint = itemCat.color, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = itemCat.categoria,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EtdaNavy,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = colorBadgeCat.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = etiquetaNivelCat,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colorBadgeCat,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                            Text(
                                text = "$asistenciasCat / $metaEvolutiva eventos",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (asistenciasCat >= metaEvolutiva) EtdaSuccess else itemCat.color,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { animProgreso },
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                            color = if (asistenciasCat >= 12) RangoOroColor else itemCat.color,
                            trackColor = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tarjeta de Versión y Búsqueda Manual de Actualizaciones (GitHub Releases)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(enabled = !buscandoActualizacion) {
                    coroutineScope.launch {
                        buscandoActualizacion = true
                        val info = AppUpdateChecker.verificarActualizacion()
                        buscandoActualizacion = false
                        if (info != null && info.hayNuevaVersion) {
                            infoActManual = info
                        } else if (info != null) {
                            Toast.makeText(
                                context,
                                "Ya tienes la versión más reciente instalada (v${BuildConfig.VERSION_NAME}).",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "No se encontraron actualizaciones pendientes en GitHub Releases.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, EtdaNavy.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = EtdaBlue.copy(alpha = 0.12f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Buscar actualización",
                            tint = EtdaBlue,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Versión de la aplicación",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EtdaNavy
                        )
                        Text(
                            text = "Versión instalada: v${BuildConfig.VERSION_NAME} (Código ${BuildConfig.VERSION_CODE})",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                if (buscandoActualizacion) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = EtdaOrange
                    )
                } else {
                    Surface(
                        color = EtdaOrange.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Buscar actualización",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EtdaOrange,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        RetrofitClient.obtenerApi(context).cerrarSesionRemota()
                    } catch (_: Exception) {}
                    TokenManager.cerrarSesion(context)
                    val intent = Intent(context, ComposeLoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEF2F2), contentColor = Color(0xFFDC2626)),
            border = BorderStroke(1.dp, Color(0xFFFECACA)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cerrar sesión segura", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, softWrap = false)
        }
    }

    infoActManual?.let { info ->
        DialogoActualizacionFlexible(
            info = info,
            onDescartar = { infoActManual = null }
        )
    }

    // MODAL DE CREDENCIAL DIGITAL CON CÓDIGO QR REAL ESCANEABLE (ISO/IEC 18004)
    if (mostrarQrDialog) {
        AlertDialog(
            onDismissRequest = { mostrarQrDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = Color.White,
            title = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(color = EtdaNavy, shape = RoundedCornerShape(50)) {
                        Text(
                            text = "PASE DE ASISTENCIA ETDA",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = datosAlumno.nombreCompleto,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = EtdaNavy,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (datosAlumno.esEgresado) "Comunidad de Egresados UNACH" else "Matrícula: ${datosAlumno.matricula}",
                        fontSize = 12.sp,
                        color = EtdaOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        border = BorderStroke(2.dp, EtdaBlue.copy(alpha = 0.35f)),
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(214.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize().padding(12.dp)
                        ) {
                            CodigoQrCredencialCanvas(
                                contenido = qrPayloadCredencial,
                                tamano = 186.dp,
                                colorOscuro = EtdaNavy
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = datosAlumno.correo,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = EtdaNavy,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = EtdaBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Text(
                            text = qrPayloadCredencial,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Muestra este código QR oficial al organizador del evento para validar tu identidad y registrar tu asistencia.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { mostrarQrDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EtdaOrange),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("Entendido", fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun IndicadorRapidoPerfil(valor: String, etiqueta: String, icono: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.12f), modifier = Modifier.size(36.dp)) {
            Icon(icono, contentDescription = null, tint = color, modifier = Modifier.padding(8.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(valor, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = EtdaNavy, maxLines = 1, softWrap = false)
        Text(etiqueta, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ItemDatoAcademico(icono: ImageVector, titulo: String, valor: String, colorIcono: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(11.dp), color = colorIcono.copy(alpha = 0.12f), modifier = Modifier.size(36.dp)) {
            Icon(icono, contentDescription = null, tint = colorIcono, modifier = Modifier.padding(8.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(valor, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EtdaNavy, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ============================================================================
// GENERADOR NATIVO DE CÓDIGO QR ESTÁNDAR ISO/IEC 18004 (SIN LIBRERÍAS EXTERNAS)
// Codifica cualquier cadena UTF-8 en modo Byte con corrección Reed-Solomon
// ============================================================================
@Composable
private fun CodigoQrCredencialCanvas(
    contenido: String,
    tamano: Dp = 186.dp,
    colorOscuro: Color = EtdaNavy
) {
    val matriz = remember(contenido) { QrIsoGenerator.generarMatriz(contenido) }
    Canvas(modifier = Modifier.size(tamano)) {
        val n = matriz.size
        if (n == 0) return@Canvas
        val margenModulos = 2
        val totalModulos = n + margenModulos * 2
        val celdaPx = size.width / totalModulos.toFloat()

        drawRect(color = Color.White, size = size)

        for (r in 0 until n) {
            for (c in 0 until n) {
                if (matriz[r][c]) {
                    drawRect(
                        color = colorOscuro,
                        topLeft = Offset((c + margenModulos) * celdaPx, (r + margenModulos) * celdaPx),
                        size = Size(celdaPx * 1.02f, celdaPx * 1.02f)
                    )
                }
            }
        }
    }
}

private object QrIsoGenerator {
    private val EXP_TABLE = IntArray(256)
    private val LOG_TABLE = IntArray(256)

    init {
        var v = 1
        for (i in 0 until 255) {
            EXP_TABLE[i] = v
            LOG_TABLE[v] = i
            v = v shl 1
            if ((v and 0x100) != 0) {
                v = v xor 0x11D
            }
        }
        EXP_TABLE[255] = EXP_TABLE[0]
    }

    private fun gfMul(x: Int, y: Int): Int {
        if (x == 0 || y == 0) return 0
        return EXP_TABLE[(LOG_TABLE[x] + LOG_TABLE[y]) % 255]
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val root = EXP_TABLE[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], root)
            }
            poly = next
        }
        return poly
    }

    private fun rsEncode(data: IntArray, ecLen: Int): IntArray {
        val gen = rsGeneratorPoly(ecLen)
        val res = IntArray(data.size + ecLen)
        System.arraycopy(data, 0, res, 0, data.size)
        for (i in data.indices) {
            val coef = res[i]
            if (coef != 0) {
                for (j in 1 until gen.size) {
                    res[i + j] = res[i + j] xor gfMul(gen[j], coef)
                }
            }
        }
        return res.copyOfRange(data.size, res.size)
    }

    private class VersionSpec(
        val version: Int,
        val dataBytesPerBlock: Int,
        val ecBytesPerBlock: Int,
        val numBlocks: Int,
        val alignCenters: IntArray
    )

    private val SPECS_LEVEL_L = listOf(
        VersionSpec(1, 19, 7, 1, intArrayOf()),
        VersionSpec(2, 34, 10, 1, intArrayOf(6, 18)),
        VersionSpec(3, 55, 15, 1, intArrayOf(6, 22)),
        VersionSpec(4, 80, 20, 1, intArrayOf(6, 26)),
        VersionSpec(5, 108, 26, 1, intArrayOf(6, 30)),
        VersionSpec(6, 68, 18, 2, intArrayOf(6, 34)),
        VersionSpec(7, 78, 20, 2, intArrayOf(6, 22, 38)),
        VersionSpec(8, 97, 24, 2, intArrayOf(6, 24, 42))
    )

    fun generarMatriz(texto: String): Array<BooleanArray> {
        val rawBytes = texto.toByteArray(Charsets.UTF_8)
        val spec = SPECS_LEVEL_L.firstOrNull {
            rawBytes.size + 2 <= it.dataBytesPerBlock * it.numBlocks
        } ?: SPECS_LEVEL_L.last()

        val maxLen = spec.dataBytesPerBlock * spec.numBlocks - 2
        val payload = if (rawBytes.size > maxLen) rawBytes.copyOfRange(0, maxLen) else rawBytes
        val totalDataBytes = spec.dataBytesPerBlock * spec.numBlocks

        val bits = ArrayList<Int>(totalDataBytes * 8)
        fun appendBits(value: Int, length: Int) {
            for (i in length - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        appendBits(0b0100, 4) // Modo Byte
        appendBits(payload.size, 8)
        for (b in payload) {
            appendBits(b.toInt() and 0xFF, 8)
        }
        val remBits = totalDataBytes * 8 - bits.size
        appendBits(0, minOf(4, remBits.coerceAtLeast(0)))
        while (bits.size % 8 != 0) {
            bits.add(0)
        }

        val dataBytes = IntArray(totalDataBytes)
        val actuales = bits.size / 8
        for (i in 0 until actuales) {
            var v = 0
            for (j in 0 until 8) {
                v = (v shl 1) or bits[i * 8 + j]
            }
            dataBytes[i] = v
        }
        val pads = intArrayOf(0xEC, 0x11)
        for (i in actuales until totalDataBytes) {
            dataBytes[i] = pads[(i - actuales) % 2]
        }

        val dataBlocks = Array(spec.numBlocks) { b ->
            val start = b * spec.dataBytesPerBlock
            dataBytes.copyOfRange(start, start + spec.dataBytesPerBlock)
        }
        val ecBlocks = Array(spec.numBlocks) { b ->
            rsEncode(dataBlocks[b], spec.ecBytesPerBlock)
        }

        val finalBytes = ArrayList<Int>(totalDataBytes + spec.ecBytesPerBlock * spec.numBlocks)
        for (i in 0 until spec.dataBytesPerBlock) {
            for (b in 0 until spec.numBlocks) {
                finalBytes.add(dataBlocks[b][i])
            }
        }
        for (i in 0 until spec.ecBytesPerBlock) {
            for (b in 0 until spec.numBlocks) {
                finalBytes.add(ecBlocks[b][i])
            }
        }

        val size = 17 + 4 * spec.version
        val modules = Array(size) { BooleanArray(size) }
        val isFunc = Array(size) { BooleanArray(size) }

        fun setFunc(r: Int, c: Int, dark: Boolean) {
            if (r in 0 until size && c in 0 until size) {
                modules[r][c] = dark
                isFunc[r][c] = true
            }
        }

        fun drawFinder(r: Int, c: Int) {
            for (dy in -1..7) {
                for (dx in -1..7) {
                    val rr = r + dy
                    val cc = c + dx
                    if (rr in 0 until size && cc in 0 until size) {
                        val inOuter = (dy in 0..6 && (dx == 0 || dx == 6)) || (dx in 0..6 && (dy == 0 || dy == 6))
                        val inInner = (dy in 2..4 && dx in 2..4)
                        setFunc(rr, cc, inOuter || inInner)
                    }
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        for (i in 8 until size - 8) {
            setFunc(6, i, i % 2 == 0)
            setFunc(i, 6, i % 2 == 0)
        }

        for (ay in spec.alignCenters) {
            for (ax in spec.alignCenters) {
                if ((ay == 6 && ax == 6) || (ay == 6 && ax == size - 7) || (ay == size - 7 && ax == 6)) {
                    continue
                }
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        setFunc(ay + dy, ax + dx, max(abs(dx), abs(dy)) != 1)
                    }
                }
            }
        }

        for (i in 0..8) {
            if (!isFunc[8][i]) setFunc(8, i, false)
            if (!isFunc[i][8]) setFunc(i, 8, false)
        }
        for (i in 0..7) {
            if (!isFunc[8][size - 1 - i]) setFunc(8, size - 1 - i, false)
            if (!isFunc[size - 1 - i][8]) setFunc(size - 1 - i, 8, false)
        }
        setFunc(size - 8, 8, true)

        if (spec.version >= 7) {
            val vInfo = if (spec.version == 7) 0x07C94 else 0x085BC
            for (i in 0 until 18) {
                val bit = ((vInfo ushr i) and 1) == 1
                val a = size - 11 + (i % 3)
                val b = i / 3
                setFunc(a, b, bit)
                setFunc(b, a, bit)
            }
        }

        val allBits = BooleanArray(finalBytes.size * 8)
        for (i in finalBytes.indices) {
            val b = finalBytes[i]
            for (j in 0 until 8) {
                allBits[i * 8 + j] = ((b ushr (7 - j)) and 1) == 1
            }
        }

        var bitIdx = 0
        var up = true
        var col = size - 1
        while (col > 0) {
            if (col == 6) col -= 1
            for (i in 0 until size) {
                val row = if (up) (size - 1 - i) else i
                for (dcOff in 0..1) {
                    val c = col - dcOff
                    if (!isFunc[row][c]) {
                        var dark = if (bitIdx < allBits.size) allBits[bitIdx] else false
                        bitIdx++
                        if ((row + c) % 2 == 0) {
                            dark = !dark
                        }
                        modules[row][c] = dark
                    }
                }
            }
            up = !up
            col -= 2
        }

        // Format info Level L (01) + Mask 0 (000) = 0x77C4
        val fmt = 0x77C4
        for (i in 0..5) setFunc(8, i, ((fmt ushr (14 - i)) and 1) == 1)
        setFunc(8, 7, ((fmt ushr 8) and 1) == 1)
        setFunc(8, 8, ((fmt ushr 7) and 1) == 1)
        setFunc(7, 8, ((fmt ushr 6) and 1) == 1)
        for (i in 9..14) setFunc(14 - i, 8, ((fmt ushr (14 - i)) and 1) == 1)

        for (i in 0..7) setFunc(8, size - 1 - i, ((fmt ushr i) and 1) == 1)
        for (i in 8..14) setFunc(size - 15 + i, 8, ((fmt ushr i) and 1) == 1)

        return modules
    }
}