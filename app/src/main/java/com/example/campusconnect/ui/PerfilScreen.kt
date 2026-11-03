package com.example.campusconnect.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusconnect.ComposeLoginActivity
import com.example.campusconnect.TokenManager
import com.example.campusconnect.formatearEtiquetaGrupo
import com.example.campusconnect.network.RetrofitClient
import kotlinx.coroutines.launch

// Paleta Oficial ETDA - UNACH Intacta
private val EtdaNavy = Color(0xFF122652)
private val EtdaBlue = Color(0xFF0093FF)
private val EtdaOrange = Color(0xFFF57C00)
private val EtdaBg = Color(0xFFF4F7FB)
private val EtdaSuccess = Color(0xFF10B981)

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

    // Si es egresado no se asignan matrícula, licenciatura ni grupo
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

private data class ProgresoCategoriaPerfil(
    val categoria: String,
    val asistencias: Int,
    val meta: Int,
    val icono: ImageVector,
    val color: Color
)

@Composable
fun PerfilScreen(puntos: Int) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var datosAlumno by remember { mutableStateOf(obtenerDatosSesionAlumno(context)) }
    var sincronizandoExpediente by remember { mutableStateOf(false) }

    suspend fun sincronizarExpedienteDesdeServidor() {
        sincronizandoExpediente = true
        val api = RetrofitClient.obtenerApi(context)
        val prefs = context.getSharedPreferences("cc_expediente_alumno", Context.MODE_PRIVATE)
        val correoReal = TokenManager.obtenerEmail(context).trim().lowercase()

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
        } finally {
            sincronizandoExpediente = false
        }
    }

    LaunchedEffect(Unit) {
        sincronizarExpedienteDesdeServidor()
    }

    var mostrarQrDialog by remember { mutableStateOf(false) }
    var mostrarCredencialExpandida by remember { mutableStateOf(true) }

    val categoriasAsistencia = remember {
        listOf(
            ProgresoCategoriaPerfil("Talleres", 0, 5, Icons.Default.Build, Color(0xFF0284C7)),
            ProgresoCategoriaPerfil("Conferencias", 0, 5, Icons.Default.RecordVoiceOver, EtdaOrange),
            ProgresoCategoriaPerfil("Tutorías", 0, 3, Icons.Default.School, EtdaBlue),
            ProgresoCategoriaPerfil("Académicos", 0, 4, Icons.Default.MenuBook, EtdaNavy),
            ProgresoCategoriaPerfil("Deporte", 0, 3, Icons.Default.EmojiEvents, Color(0xFFEA580C)),
            ProgresoCategoriaPerfil("Culturales", 0, 3, Icons.Default.TheaterComedy, Color(0xFF2563EB))
        )
    }

    val totalEventosAsistidos = categoriasAsistencia.sumOf { it.asistencias }
    val insigniasDesbloqueadas = 0

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
                    IndicadorRapidoPerfil("$puntos pts", "Rewards", Icons.Default.Stars, EtdaNavy)
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
                        onClick = { coroutineScope.launch { sincronizarExpedienteDesdeServidor() } },
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

                        // Escuela / Facultad (Estático)
                        ItemDatoAcademico(Icons.Default.Domain, "Escuela / Facultad", datosAlumno.escuela, EtdaBlue)

                        if (datosAlumno.esEgresado) {
                            // Para egresados NO se muestra Licenciatura (Carrera), Matrícula ni Semestre/Grupo
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
                            // Datos exclusivos de Estudiante Activo
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
                            text = "Tu progreso para desbloquear recompensas",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(color = EtdaBlue.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                        Text(
                            text = "Nivel Oro",
                            color = EtdaBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                categoriasAsistencia.forEach { itemCat ->
                    val porcentaje = (itemCat.asistencias.toFloat() / itemCat.meta.toFloat()).coerceIn(0f, 1f)
                    val animProgreso by animateFloatAsState(
                        targetValue = porcentaje,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                        label = "barraCat"
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
                                Text(itemCat.categoria, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EtdaNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${itemCat.asistencias} / ${itemCat.meta} eventos", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = itemCat.color, maxLines = 1, softWrap = false)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { animProgreso },
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                            color = itemCat.color,
                            trackColor = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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
                    Text(datosAlumno.nombreCompleto, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = EtdaNavy, textAlign = TextAlign.Center)
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
                        color = EtdaBg,
                        border = BorderStroke(2.dp, EtdaBlue.copy(alpha = 0.3f)),
                        modifier = Modifier.size(190.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.QrCode2, contentDescription = "Código QR Universitario", tint = EtdaNavy, modifier = Modifier.size(150.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(datosAlumno.correo, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = EtdaNavy, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Muestra este código al organizador del evento para validar tu asistencia y sumar puntos.",
                        fontSize = 12.sp,
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