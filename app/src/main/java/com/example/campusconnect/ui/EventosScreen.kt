package com.example.campusconnect.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.campusconnect.MyFirebaseMessagingService
import com.example.campusconnect.model.ArchivoEvento
import com.example.campusconnect.model.Evento
import com.example.campusconnect.network.RetrofitClient
import com.example.campusconnect.service.ServicioUbicacionAsistencia
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Paleta Oficial ETDA - UNACH Intacta
private val EtdaNavy = Color(0xFF122652)
private val EtdaBlue = Color(0xFF0093FF)
private val EtdaOrange = Color(0xFFF57C00)
private val EtdaBg = Color(0xFFF4F7FB)
private val EtdaSuccess = Color(0xFF10B981)

private val zonaHorariaMx: TimeZone = TimeZone.getTimeZone("America/Mexico_City")
private val localeMx = Locale("es", "MX")

private fun obtenerCalendarioLocalMx(): Calendar {
    return Calendar.getInstance(zonaHorariaMx, localeMx)
}

private data class DiaMesContext(
    val dia: Int,
    val mes: Int,
    val anio: Int,
    val tieneEvento: Boolean = false,
    val seleccionado: Boolean = false
) {
    fun esValido() = dia > 0
}

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

private val zoneIdMx: ZoneId = ZoneId.of("America/Mexico_City")

private fun parsearZonedDateTimeMx(raw: String?): ZonedDateTime? {
    if (raw.isNullOrBlank()) return null
    val limpio = raw.trim()
    return try {
        OffsetDateTime.parse(limpio).atZoneSameInstant(zoneIdMx)
    } catch (_: Exception) {
        try {
            Instant.parse(limpio).atZone(zoneIdMx)
        } catch (_: Exception) {
            try {
                val norm = limpio.replace(" ", "T").take(19)
                val padded = if (norm.length == 16) "$norm:00" else norm
                LocalDateTime.parse(padded).atZone(zoneIdMx)
            } catch (_: Exception) {
                null
            }
        }
    }
}

private fun parsearInstanteEventoMx(raw: String?): Instant? {
    return parsearZonedDateTimeMx(raw)?.toInstant()
}

private fun fechaIsoLocalMx(raw: String?): String {
    val zdt = parsearZonedDateTimeMx(raw) ?: return (raw ?: "").take(10)
    return String.format(Locale.US, "%04d-%02d-%02d", zdt.year, zdt.monthValue, zdt.dayOfMonth)
}

private fun extraerFechaVisual(fechaHora: String?): Triple<String, String, String> {
    if (fechaHora.isNullOrBlank()) return Triple("OCT", "15", "10:00")
    val meses = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")
    parsearZonedDateTimeMx(fechaHora)?.let { zdt ->
        val mesTxt = meses.getOrElse(zdt.monthValue - 1) { "OCT" }
        val diaTxt = String.format(Locale.US, "%02d", zdt.dayOfMonth)
        val horaTxt = String.format(Locale.US, "%02d:%02d", zdt.hour, zdt.minute)
        return Triple(mesTxt, diaTxt, horaTxt)
    }
    return try {
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

private fun extraerHorarioInicioYFin(fechaInicioRaw: String?, fechaFinRaw: String?): Pair<String, String> {
    val meses = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    val zdtIni = parsearZonedDateTimeMx(fechaInicioRaw)
    val zdtFin = parsearZonedDateTimeMx(fechaFinRaw) ?: zdtIni?.plusHours(4)

    val txtIni = if (zdtIni != null) {
        val m = meses.getOrElse(zdtIni.monthValue - 1) { "Nov" }
        String.format(Locale.US, "%02d %s %d • %02d:%02d hrs", zdtIni.dayOfMonth, m, zdtIni.year, zdtIni.hour, zdtIni.minute)
    } else {
        "Por confirmar"
    }

    val txtFin = if (zdtFin != null) {
        val m = meses.getOrElse(zdtFin.monthValue - 1) { "Nov" }
        String.format(Locale.US, "%02d %s %d • %02d:%02d hrs", zdtFin.dayOfMonth, m, zdtFin.year, zdtFin.hour, zdtFin.minute)
    } else {
        "Al concluir la sesión"
    }

    return Pair(txtIni, txtFin)
}

private fun generarCodigoAsientoCineApp(indiceBase0: Int, cols: Int = 10): String {
    val filaIdx = indiceBase0.coerceAtLeast(0) / cols
    val colNum = (indiceBase0.coerceAtLeast(0) % cols) + 1
    val letraFila = if (filaIdx < 26) {
        ('A' + filaIdx).toString()
    } else {
        val p1 = ('A' + (filaIdx / 26) - 1)
        val p2 = ('A' + (filaIdx % 26))
        "$p1$p2"
    }
    return String.format(Locale.US, "%s-%02d", letraFila, colNum)
}

private fun verificarYDispararAviso30MinFin(context: Context, ev: Evento, inscritosIds: Set<String>) {
    ServicioUbicacionAsistencia.verificarAviso30MinUnico(
        context = context,
        eventoId = ev.id,
        titulo = ev.titulo,
        lugar = ev.lugar,
        fechaFinEfectiva = ev.fechaFinEfectiva,
        fechaHoraInicio = ev.fechaHora
    )
}

private fun dispararNotificacionEventoFinalizado(
    context: Context,
    eventoId: String,
    titulo: String,
    lugar: String,
    firmaFin: String
) {
    val prefs = context.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE)
    val clave = "notificado_evento_finalizado_${eventoId}_$firmaFin"
    if (!prefs.getBoolean(clave, false)) {
        prefs.edit().putBoolean(clave, true).apply()
        MyFirebaseMessagingService.mostrarNotificacion(
            context = context,
            titulo = "✅ Evento finalizado: $titulo",
            cuerpo = "El evento en $lugar ha concluido. ¡Gracias por tu participación en Campus Connect UNACH!",
            notifId = ("finalizado_$eventoId").hashCode()
        )
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
    val context = LocalContext.current

    var mostrarEscaner by remember { mutableStateOf(false) }
    var eventoParaEscanear by remember { mutableStateOf<Evento?>(null) }
    var eventoParaElegirAsiento by remember { mutableStateOf<Evento?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var filtroSeleccionado by remember { mutableStateOf("Todos") }
    var eventoSeleccionadoId by remember { mutableStateOf<String?>(null) }
    val mapaAuditoriosCatalogo = remember {
        mutableStateMapOf(
            "auditorio principal etda" to 180,
            "sala audiovisual 1" to 60,
            "sala audiovisual 2" to 50,
            "laboratorio de cómputo a" to 35,
            "laboratorio de computo a" to 35,
            "auditorio los constituyentes" to 350,
            "explanada central etda" to 250
        )
    }
    var calendarioExpandido by remember { mutableStateOf(true) }
    var tabVistaEventos by remember { mutableIntStateOf(0) }

    val prefsNotif = remember { context.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE) }
    val prefsLocalPiloto = remember { context.getSharedPreferences("EventosPiloto", Context.MODE_PRIVATE) }

    // Almacenamos ÚNICAMENTE los IDs de eventos vigentes en los que el alumno está inscrito
    var eventosInscritosIds by remember {
        val guardados = prefsNotif.getStringSet("eventos_inscritos_ids_v2", emptySet()) ?: emptySet()
        mutableStateOf(guardados)
    }

    // Memoria persistente de eventos activos vistos para detectar en qué momento exacto finalizan
    // incluso si el usuario cambia de pestaña o cierra y vuelve a abrir la app
    val cacheEventosVistos = remember {
        val mapa = mutableStateMapOf<String, Evento>()
        val guardados = prefsNotif.getStringSet("eventos_activos_vistos_v2", emptySet()) ?: emptySet()
        for (linea in guardados) {
            val partes = linea.split("|||")
            if (partes.size >= 5) {
                val idG = partes[0]
                val titG = partes[1]
                val lugG = partes[2]
                val finG = partes[3].ifBlank { null }
                val iniG = partes[4]
                if (idG.isNotBlank() && titG.isNotBlank() && iniG.isNotBlank()) {
                    mapa[idG] = Evento(
                        id = idG,
                        titulo = titG,
                        descripcion = "",
                        fechaHora = iniG,
                        fechaFin = finG,
                        lugar = lugG
                    )
                }
            }
        }
        mapa
    }

    fun persistirCacheEventosVistos() {
        val serializados = cacheEventosVistos.values.map { ev ->
            val titLimpio = ev.titulo.replace("|||", " ")
            val lugLimpio = ev.lugar.replace("|||", " ")
            val finStr = (ev.fechaFinEfectiva ?: "").replace("|||", "")
            val iniStr = ev.fechaHora.replace("|||", "")
            "${ev.id}|||$titLimpio|||$lugLimpio|||$finStr|||$iniStr"
        }.toSet()
        prefsNotif.edit().putStringSet("eventos_activos_vistos_v2", serializados).apply()
    }

    fun persistirInscritosActivos(nuevosIds: Set<String>) {
        eventosInscritosIds = nuevosIds
        prefsNotif.edit()
            .putStringSet("eventos_inscritos_ids_v2", nuevosIds)
            .remove("eventos_inscritos_set") // Purgar clave legada que duplicaba IDs + títulos
            .apply()
        prefsLocalPiloto.edit().remove("inscritos").apply()
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        scope.launch(Dispatchers.IO) {
            try {
                val exp = RetrofitClient.obtenerApi(context).obtenerExpedienteAlumno()
                if (exp.email.isNotBlank()) {
                    prefsNotif.edit()
                        .putString("usuario_email_actual", exp.email.trim().lowercase())
                        .putString("usuario_nombre_actual", exp.nombre.trim())
                        .apply()
                }
            } catch (_: Exception) {}
        }
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            RetrofitClient.obtenerApi(context).registrarFcmToken(mapOf("fcm_token" to token))
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    var eventosServidor by remember { mutableStateOf<List<Evento>>(emptyList()) }
    var yaSincronizoConServidor by remember { mutableStateOf(false) }
    var sincronizandoServidor by remember { mutableStateOf(false) }
    var errorServidor by remember { mutableStateOf<String?>(null) }
    var pulsoReloj by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun evaluarNotificacionesYSincronizarInscritos(remotos: List<Evento>) {
        val ahora = Instant.now()
        val vigentes = remotos.filter { it.estaVigenteEnTiempoReal() }
        val idsVigentesActuales = vigentes.map { it.id }.toSet()

        // 1. Sincronizar inscripciones reales de eventos vigentes y PURGAR inmediatamente cualquier evento finalizado
        val nuevosInscritosVigentes = mutableSetOf<String>()
        for (ev in vigentes) {
            if (ev.inscrito == true || eventosInscritosIds.contains(ev.id)) {
                nuevosInscritosVigentes.add(ev.id)
            }
        }
        if (nuevosInscritosVigentes != eventosInscritosIds) {
            persistirInscritosActivos(nuevosInscritosVigentes)
        }

        // 2. Revisar si algún evento que estaba activo en cacheEventosVistos acaba de finalizar
        var cambioCacheVistos = false
        for ((idPrevio, evPrevio) in cacheEventosVistos.entries.toList()) {
            val finInstant = parsearInstanteEventoMx(evPrevio.fechaFinEfectiva)
                ?: parsearInstanteEventoMx(evPrevio.fechaHora)?.plus(Duration.ofHours(4))
            val segundosParaFin = if (finInstant != null) Duration.between(ahora, finInstant).seconds else 0L
            val yaAlcanzoHoraFin = finInstant != null && !ahora.isBefore(finInstant)
            // Si el servidor ya lo retiró del feed activo y su hora de término ya llegó (con margen de 90s por desfase de reloj)
            val salioDelFeedPorFin = !idsVigentesActuales.contains(idPrevio) && segundosParaFin <= 90L

            if (yaAlcanzoHoraFin || salioDelFeedPorFin) {
                dispararNotificacionEventoFinalizado(
                    context = context,
                    eventoId = evPrevio.id,
                    titulo = evPrevio.titulo,
                    lugar = evPrevio.lugar,
                    firmaFin = evPrevio.fechaFinEfectiva ?: evPrevio.fechaHora
                )
                cacheEventosVistos.remove(idPrevio)
                cambioCacheVistos = true
                if (eventosInscritosIds.contains(idPrevio)) {
                    persistirInscritosActivos(eventosInscritosIds - idPrevio)
                }
            }
        }

        for (ev in vigentes) {
            cacheEventosVistos[ev.id] = ev
            cambioCacheVistos = true
        }
        if (cambioCacheVistos) {
            persistirCacheEventosVistos()
        }

        val idsConocidos = prefsNotif.getStringSet("ids_eventos_conocidos", null)
        if (idsConocidos == null) {
            val iniciales = mutableSetOf<String>()
            for (ev in vigentes) {
                val disp = parsearInstanteEventoMx(ev.disponibleDesdeEfectivo)
                val aparecioRecien = disp != null && Duration.between(disp, ahora).seconds in 0..300
                if (aparecioRecien) {
                    val clave = "notificado_evento_disponible_${ev.id}"
                    if (!prefsNotif.getBoolean(clave, false)) {
                        prefsNotif.edit().putBoolean(clave, true).apply()
                        MyFirebaseMessagingService.mostrarNotificacion(
                            context = context,
                            titulo = "¡Nuevo evento disponible: ${ev.titulo}!",
                            cuerpo = "Sede: ${ev.lugar} · Ya puedes consultar los detalles e inscribirte en Campus Connect UNACH.",
                            notifId = ("nuevo_${ev.id}").hashCode()
                        )
                    }
                }
                iniciales.add(ev.id)
                verificarYDispararAviso30MinFin(context, ev, nuevosInscritosVigentes)
            }
            prefsNotif.edit().putStringSet("ids_eventos_conocidos", iniciales).apply()
            return
        }

        val nuevosConocidos = idsConocidos.toMutableSet()
        for (ev in vigentes) {
            val claveAparicion = "notificado_evento_disponible_${ev.id}"
            val esNuevoEnFeed = !nuevosConocidos.contains(ev.id)
            val disp = parsearInstanteEventoMx(ev.disponibleDesdeEfectivo)
            val aparecioRecien = disp != null && Duration.between(disp, ahora).seconds in 0..300

            if ((esNuevoEnFeed || aparecioRecien) && !prefsNotif.getBoolean(claveAparicion, false)) {
                prefsNotif.edit().putBoolean(claveAparicion, true).apply()
                MyFirebaseMessagingService.mostrarNotificacion(
                    context = context,
                    titulo = "¡Nuevo evento disponible: ${ev.titulo}!",
                    cuerpo = "Sede: ${ev.lugar} · Ya puedes consultar los detalles e inscribirte en Campus Connect UNACH.",
                    notifId = ("nuevo_${ev.id}").hashCode()
                )
            }
            nuevosConocidos.add(ev.id)
            verificarYDispararAviso30MinFin(context, ev, nuevosInscritosVigentes)
        }
        prefsNotif.edit().putStringSet("ids_eventos_conocidos", nuevosConocidos).apply()
    }

    suspend fun sincronizarMetaAsientoEnServidor(
        evento: Evento,
        asiento: String?,
        qrValidado: Boolean,
        gpsValidado: Boolean
    ) {
        withContext(Dispatchers.IO) {
            try {
                val api = RetrofitClient.obtenerApi(context)
                var emailUsr = prefsNotif.getString("usuario_email_actual", "") ?: ""
                var nombreUsr = prefsNotif.getString("usuario_nombre_actual", "") ?: ""
                if (emailUsr.isBlank()) {
                    try {
                        val exp = api.obtenerExpedienteAlumno()
                        emailUsr = exp.email.trim().lowercase()
                        nombreUsr = exp.nombre.trim()
                        prefsNotif.edit()
                            .putString("usuario_email_actual", emailUsr)
                            .putString("usuario_nombre_actual", nombreUsr)
                            .apply()
                    } catch (_: Exception) {}
                }
                if (emailUsr.isNotBlank()) {
                    val payloadMeta = evento.construirPayloadSincronizacionMeta(
                        emailUsuario = emailUsr,
                        nombreUsuario = nombreUsr,
                        asiento = asiento,
                        qrValidado = qrValidado,
                        gpsValidado = gpsValidado,
                        asistio = gpsValidado
                    )
                    api.actualizarEvento(eventoId = evento.id, body = payloadMeta)
                }
            } catch (_: Exception) {}
        }
    }

    fun aplicarCapacidadCatalogoAEventos(lista: List<Evento>): List<Evento> {
        val emailUsr = prefsNotif.getString("usuario_email_actual", "")?.trim()?.lowercase().orEmpty()
        val setQr1 = prefsNotif.getStringSet("eventos_qr_1de2_set", emptySet()) ?: emptySet()
        val setGps2 = prefsNotif.getStringSet("eventos_gps_2de2_set", emptySet()) ?: emptySet()

        return lista.map { ev ->
            val claveLugar = ev.lugar.trim().lowercase()
            val capCat = mapaAuditoriosCatalogo[claveLugar]
                ?: mapaAuditoriosCatalogo.entries.firstOrNull { claveLugar.startsWith(it.key) || it.key in claveLugar }?.value
                ?: ev.cupoMaximo

            var asientoGuardadoPrefs = prefsNotif.getString("mi_asiento_${ev.id}", null)
            val tsApartadoLocal = prefsNotif.getLong("ts_apartado_${ev.id}", 0L)
            val tsLiberadoAdmin = ev.obtenerTimestampLiberacionAdmin(emailUsr, asientoGuardadoPrefs)

            // Si el Administrador liberó el asiento desde la Web después de que el alumno lo apartó, liberar también en la App
            if (tsLiberadoAdmin > 0L && tsLiberadoAdmin > tsApartadoLocal) {
                if (asientoGuardadoPrefs != null || eventosInscritosIds.contains(ev.id)) {
                    asientoGuardadoPrefs = null
                    prefsNotif.edit()
                        .remove("mi_asiento_${ev.id}")
                        .remove("asientos_tomados_${ev.id}")
                        .putInt("ocupados_min_${ev.id}", 0)
                        .putLong("ts_apartado_${ev.id}", tsLiberadoAdmin)
                        .apply()
                    persistirInscritosActivos(eventosInscritosIds - ev.id)
                    scope.launch(Dispatchers.IO) {
                        try {
                            RetrofitClient.obtenerApi(context).cancelarAsistenciaEvento(ev.id)
                        } catch (_: Exception) {}
                    }
                }
            }

            val asientoEnMeta = ev.obtenerAsientoDeUsuarioEnMeta(emailUsr)
            val miAsientoLocal = ev.miAsiento ?: asientoGuardadoPrefs ?: asientoEnMeta

            // Si el alumno ya eligió un asiento (ej. K-04) o ya escaneó el QR en la App y aún no figura en CCMETA del servidor, sincronizarlo automáticamente
            val qrLocal = setQr1.contains(ev.id) || setGps2.contains(ev.id) || (ev.validacionQr == true)
            val gpsLocal = setGps2.contains(ev.id) || (ev.validacionGps == true)
            if (!miAsientoLocal.isNullOrBlank() && emailUsr.isNotBlank() && asientoEnMeta != miAsientoLocal) {
                scope.launch {
                    sincronizarMetaAsientoEnServidor(
                        evento = ev.copy(capacidadTotal = capCat),
                        asiento = miAsientoLocal,
                        qrValidado = qrLocal,
                        gpsValidado = gpsLocal
                    )
                }
            }

            val tomadosLocalSet = prefsNotif.getStringSet("asientos_tomados_${ev.id}", emptySet()) ?: emptySet()
            val todosTomados = (ev.asientosTomadosEfectivos + tomadosLocalSet + listOfNotNull(miAsientoLocal)).distinct()
            val minOcupLocal = prefsNotif.getInt("ocupados_min_${ev.id}", 0)
            val estaInsc = !miAsientoLocal.isNullOrBlank() || (ev.inscrito == true && tsLiberadoAdmin <= tsApartadoLocal) || eventosInscritosIds.contains(ev.id)
            val ocupadosCalc = maxOf(
                ev.lugaresOcupados,
                todosTomados.size,
                minOcupLocal,
                if (estaInsc) 1 else 0
            ).coerceIn(0, capCat)

            ev.copy(
                capacidadTotal = capCat,
                miAsiento = miAsientoLocal,
                inscrito = estaInsc,
                asientosOcupados = ocupadosCalc,
                asientosTomados = todosTomados,
                asientosReservadosAdmin = ev.asientosReservadosAdminEfectivos,
                disponibleDesde = ev.disponibleDesdeEfectivo,
                fechaFin = ev.fechaFinEfectiva
            )
        }
    }

    fun recargarEventosDesdeServidor(mostrarMensaje: Boolean = false) {
        scope.launch {
            sincronizandoServidor = true
            try {
                withContext(Dispatchers.IO) {
                    try {
                        val catResp = RetrofitClient.obtenerApi(context).obtenerCatalogosFacultad()
                        catResp.auditorios?.forEach { aud ->
                            val nom = aud.nombre.trim().lowercase()
                            if (nom.isNotBlank() && aud.asientos > 0) {
                                mapaAuditoriosCatalogo[nom] = aud.asientos
                            }
                        }
                    } catch (_: Exception) {}
                }
                val remotosCrudos = withContext(Dispatchers.IO) {
                    RetrofitClient.obtenerApi(context).obtenerEventos()
                }
                val remotos = aplicarCapacidadCatalogoAEventos(remotosCrudos)
                evaluarNotificacionesYSincronizarInscritos(remotos)
                eventosServidor = remotos
                yaSincronizoConServidor = true
                errorServidor = null
                if (mostrarMensaje) {
                    val vigentesCount = remotos.count { it.estaVigenteEnTiempoReal() }
                    snackbarHostState.showSnackbar("Cartelera actualizada ($vigentesCount eventos activos)")
                }
            } catch (e: Exception) {
                errorServidor = e.localizedMessage
                if (mostrarMensaje) {
                    snackbarHostState.showSnackbar("No se pudo actualizar desde el servidor")
                }
            } finally {
                sincronizandoServidor = false
            }
        }
    }

    // Reloj local de alta precisión (cada 1 segundo) que actualiza pulsoReloj incondicionalmente,
    // retira eventos finalizados al instante y dispara las notificaciones de 30m y fin sin tocar la pantalla
    LaunchedEffect(Unit) {
        ServicioUbicacionAsistencia.iniciarMonitorEnSegundoPlano(context)
        while (true) {
            val ahora = Instant.now()
            var huboExpiradoLocal = false

            // 1. Revisar todos los eventos en memoria y en caché
            val todosParaRevisar = (cacheEventosVistos.values + eventosServidor + eventos).associateBy { it.id }
            for ((idEv, evObj) in todosParaRevisar) {
                val finInstant = parsearInstanteEventoMx(evObj.fechaFinEfectiva)
                    ?: parsearInstanteEventoMx(evObj.fechaHora)?.plus(Duration.ofHours(4))
                if (finInstant != null && !ahora.isBefore(finInstant)) {
                    dispararNotificacionEventoFinalizado(
                        context = context,
                        eventoId = evObj.id,
                        titulo = evObj.titulo,
                        lugar = evObj.lugar,
                        firmaFin = evObj.fechaFinEfectiva ?: evObj.fechaHora
                    )
                    if (cacheEventosVistos.containsKey(idEv)) {
                        cacheEventosVistos.remove(idEv)
                        huboExpiradoLocal = true
                    }
                    if (eventosInscritosIds.contains(idEv)) {
                        persistirInscritosActivos(eventosInscritosIds - idEv)
                    }
                } else {
                    verificarYDispararAviso30MinFin(context, evObj, eventosInscritosIds)
                }
            }
            if (huboExpiradoLocal) {
                persistirCacheEventosVistos()
                eventosServidor = eventosServidor.filter { it.estaVigenteEnTiempoReal() }
            }
            pulsoReloj = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    // Sincronización automática en segundo plano cada 6 segundos sin necesidad de refrescar manualmente
    LaunchedEffect(Unit) {
        while (true) {
            try {
                withContext(Dispatchers.IO) {
                    try {
                        val catResp = RetrofitClient.obtenerApi(context).obtenerCatalogosFacultad()
                        catResp.auditorios?.forEach { aud ->
                            val nom = aud.nombre.trim().lowercase()
                            if (nom.isNotBlank() && aud.asientos > 0) {
                                mapaAuditoriosCatalogo[nom] = aud.asientos
                            }
                        }
                    } catch (_: Exception) {}
                }
                val remotosCrudos = withContext(Dispatchers.IO) {
                    RetrofitClient.obtenerApi(context).obtenerEventos()
                }
                val remotos = aplicarCapacidadCatalogoAEventos(remotosCrudos)
                ServicioUbicacionAsistencia.procesarEventosYNotificacionesAutomaticas(context, remotos)
                evaluarNotificacionesYSincronizarInscritos(remotos)
                eventosServidor = remotos
                yaSincronizoConServidor = true
                errorServidor = null
            } catch (_: Exception) {
            }
            pulsoReloj = System.currentTimeMillis()
            delay(6_000L)
        }
    }

    // Lista de eventos vigentes en tiempo real evaluada cada segundo con pulsoReloj
    val listaEventosActiva = remember(eventosServidor, eventos, yaSincronizoConServidor, pulsoReloj) {
        val base = if (yaSincronizoConServidor) eventosServidor else aplicarCapacidadCatalogoAEventos(eventos)
        base.filter { it.estaVigenteEnTiempoReal(pulsoReloj) }
    }

    // Limpieza inmediata de eventosInscritosIds cada vez que cambia listaEventosActiva (ej. al vencer la hora de fin)
    LaunchedEffect(listaEventosActiva, yaSincronizoConServidor) {
        if (yaSincronizoConServidor || listaEventosActiva.isNotEmpty()) {
            val idsActivos = listaEventosActiva.map { it.id }.toSet()
            val depurados = eventosInscritosIds.intersect(idsActivos)
            if (depurados.size != eventosInscritosIds.size) {
                persistirInscritosActivos(depurados)
            }
            // Si el modal abierto corresponde a un evento que acaba de finalizar, cerrarlo
            if (eventoSeleccionadoId != null && eventoSeleccionadoId !in idsActivos) {
                eventoSeleccionadoId = null
            }
        }
    }

    // Contador exacto de eventos vigentes en los que el alumno está inscrito (0 si ya terminaron)
    val totalInscritosVigentes = remember(listaEventosActiva, eventosInscritosIds) {
        listaEventosActiva.count { ev ->
            ev.inscrito == true || eventosInscritosIds.contains(ev.id)
        }
    }

    var mesActual by remember { mutableStateOf(obtenerCalendarioLocalMx()) }
    var fechaSeleccionada by remember { mutableStateOf(obtenerCalendarioLocalMx()) }

    val eventosFiltrados = remember(listaEventosActiva, searchQuery, filtroSeleccionado, tabVistaEventos, eventosInscritosIds) {
        listaEventosActiva.filter { ev ->
            val catInfo = detectarCategoriaEvento(ev)
            val coincideBusqueda = searchQuery.isBlank() ||
                    ev.titulo.contains(searchQuery, ignoreCase = true) ||
                    ev.lugar.contains(searchQuery, ignoreCase = true) ||
                    ev.descripcion.contains(searchQuery, ignoreCase = true) ||
                    (ev.categoria ?: "").contains(searchQuery, ignoreCase = true)

            val coincideCategoria = filtroSeleccionado == "Todos" ||
                    catInfo.nombre.equals(filtroSeleccionado, ignoreCase = true)

            val estaInscritoEnEsteEvento = (ev.inscrito == true) || eventosInscritosIds.contains(ev.id)
            val coincideTab = if (tabVistaEventos == 1) estaInscritoEnEsteEvento else true

            coincideBusqueda && coincideCategoria && coincideTab
        }
    }

    val diasDelMes = remember(mesActual, fechaSeleccionada, listaEventosActiva) {
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

            val diaTieneEvento = listaEventosActiva.any { fechaIsoLocalMx(it.fechaHora) == fechaStr }
            dias.add(DiaMesContext(dia, mesInt, anioInt, diaTieneEvento, esSeleccionado))
        }
        dias
    }

    val eventoSeleccionadoActual = remember(eventoSeleccionadoId, listaEventosActiva) {
        eventoSeleccionadoId?.let { idSel -> listaEventosActiva.find { it.id == idSel } }
    }

    if (mostrarEscaner) {
        EscanerQrScreen(
            eventoObjetivo = eventoParaEscanear,
            onQrValidadoConExito = { idEscaneado, codigoCrudo ->
                val setQr1 = (prefsNotif.getStringSet("eventos_qr_1de2_set", emptySet()) ?: emptySet()).toMutableSet()
                setQr1.add(idEscaneado)
                prefsNotif.edit().putStringSet("eventos_qr_1de2_set", setQr1).apply()

                val nuevosInscritos = eventosInscritosIds + idEscaneado
                persistirInscritosActivos(nuevosInscritos)

                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val api = RetrofitClient.obtenerApi(context)
                            val respQr = api.validarQrEvento(
                                eventoId = idEscaneado,
                                body = mapOf("codigo_qr" to codigoCrudo)
                            )
                            if (!respQr.isSuccessful) {
                                api.registrarAsistenciaEvento(
                                    eventoId = idEscaneado,
                                    body = mapOf("codigo_qr" to codigoCrudo)
                                )
                            }
                        }
                        val evObj = eventoParaEscanear ?: listaEventosActiva.find { it.id == idEscaneado }
                        if (evObj != null) {
                            val asientoActual = evObj.miAsiento ?: prefsNotif.getString("mi_asiento_$idEscaneado", null) ?: "A-01"
                            val setGps2 = prefsNotif.getStringSet("eventos_gps_2de2_set", emptySet()) ?: emptySet()
                            sincronizarMetaAsientoEnServidor(
                                evento = evObj,
                                asiento = asientoActual,
                                qrValidado = true,
                                gpsValidado = setGps2.contains(idEscaneado)
                            )
                        }
                        recargarEventosDesdeServidor(mostrarMensaje = false)
                    } catch (_: Exception) {}
                }
            },
            onVolver = {
                mostrarEscaner = false
                recargarEventosDesdeServidor(mostrarMensaje = false)
            }
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(EtdaBg),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(EtdaNavy, Color(0xFF1A3673))
                            ),
                            shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp)
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
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.4).sp
                                )
                                Text(
                                    text = "Participa, consulta materiales y suma asistencias",
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 12.sp
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = EtdaBlue.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, EtdaBlue.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable { recargarEventosDesdeServidor(mostrarMensaje = true) }
                            ) {
                                if (sincronizandoServidor) {
                                    CircularProgressIndicator(
                                        color = EtdaOrange,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.padding(11.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Actualizar cartelera",
                                        tint = EtdaOrange,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
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

            item {
                Column(modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) {
                    Text(
                        text = "Categorías de participación",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = EtdaNavy.copy(alpha = 0.72f),
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
                                animationSpec = tween(220),
                                label = "chipBg"
                            )
                            val contentColor by animateColorAsState(
                                targetValue = if (seleccionado) Color.White else EtdaNavy,
                                animationSpec = tween(220),
                                label = "chipContent"
                            )
                            val chipScale by animateFloatAsState(
                                targetValue = if (seleccionado) 1.04f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "chipScale"
                            )

                            Surface(
                                modifier = Modifier
                                    .scale(chipScale)
                                    .clip(RoundedCornerShape(50))
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
                                        fontWeight = if (seleccionado) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = BorderStroke(1.dp, EtdaNavy.copy(alpha = 0.06f))
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
                                    shape = RoundedCornerShape(11.dp),
                                    color = EtdaBlue.copy(alpha = 0.12f),
                                    modifier = Modifier.size(38.dp)
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
                                        fontWeight = FontWeight.ExtraBold,
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
                            enter = expandVertically(animationSpec = tween(240)) + fadeIn(animationSpec = tween(220)),
                            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(180))
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
                                animationSpec = tween(220),
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
                                animationSpec = tween(220),
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
                                            text = "$totalInscritosVigentes",
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
                                tabVistaEventos == 1 -> "Eventos activos en los que participas"
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

            when {
                cargando && !yaSincronizoConServidor -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = EtdaOrange, strokeWidth = 3.dp)
                    }
                }
                error != null && !yaSincronizoConServidor -> item {
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
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, EtdaNavy.copy(alpha = 0.06f))
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
                                    "No tienes eventos inscritos activos"
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
                                    "Explora la pestaña 'Próximos Eventos', inscríbete y aquí aparecerá tu agenda personal mientras el evento esté vigente."
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
                    key = { _, ev -> ev.id }
                ) { index, evento ->
                    val yaInscrito = (evento.inscrito == true) || eventosInscritosIds.contains(evento.id)
                    TarjetaEventoAnimada(
                        evento = evento,
                        index = index,
                        estaInscrito = yaInscrito,
                        onClick = { eventoSeleccionadoId = evento.id }
                    )
                }
            }
        }

        eventoSeleccionadoActual?.let { evento ->
            val yaInscrito = (evento.inscrito == true) || eventosInscritosIds.contains(evento.id)
            val setQr1 = prefsNotif.getStringSet("eventos_qr_1de2_set", emptySet()) ?: emptySet()
            val setGps2 = prefsNotif.getStringSet("eventos_gps_2de2_set", emptySet()) ?: emptySet()
            val qrValidado = (evento.validacionQr == true) || setQr1.contains(evento.id) || setGps2.contains(evento.id)
            val gpsValidado = (evento.validacionGps == true) || setGps2.contains(evento.id)
            val asientoGuardado = evento.miAsiento ?: prefsNotif.getString("mi_asiento_${evento.id}", null)

            DetalleEventoSheet(
                evento = evento.copy(miAsiento = asientoGuardado),
                estaInscrito = yaInscrito,
                qrValidado = qrValidado,
                gpsValidado = gpsValidado,
                onDismiss = { eventoSeleccionadoId = null },
                onAbrirSelectorAsientos = {
                    eventoParaElegirAsiento = evento
                },
                onAbrirEscaner = {
                    eventoParaEscanear = evento
                    eventoSeleccionadoId = null
                    mostrarEscaner = true
                },
                onCancelarInscripcion = {
                    scope.launch {
                        try {
                            val resp = withContext(Dispatchers.IO) {
                                RetrofitClient.obtenerApi(context).cancelarAsistenciaEvento(evento.id)
                            }
                            if (resp.isSuccessful) {
                                val actualizados = eventosInscritosIds - evento.id
                                persistirInscritosActivos(actualizados)
                                val prevTomados = (prefsNotif.getStringSet("asientos_tomados_${evento.id}", emptySet()) ?: emptySet()).toMutableSet()
                                if (!asientoGuardado.isNullOrBlank()) prevTomados.remove(asientoGuardado)
                                val nuevoOcupMin = (evento.lugaresOcupados - 1).coerceAtLeast(0)
                                prefsNotif.edit()
                                    .remove("mi_asiento_${evento.id}")
                                    .putStringSet("asientos_tomados_${evento.id}", prevTomados)
                                    .putInt("ocupados_min_${evento.id}", nuevoOcupMin)
                                    .putLong("ts_apartado_${evento.id}", System.currentTimeMillis())
                                    .apply()
                                sincronizarMetaAsientoEnServidor(
                                    evento = evento,
                                    asiento = null,
                                    qrValidado = false,
                                    gpsValidado = false
                                )

                                eventosServidor = eventosServidor.map { item ->
                                    if (item.id == evento.id) {
                                        item.copy(
                                            inscrito = false,
                                            miAsiento = null,
                                            asientosOcupados = nuevoOcupMin,
                                            asientosTomados = item.asientosTomadosEfectivos.filterNot { it == asientoGuardado }
                                        )
                                    } else item
                                }

                                recargarEventosDesdeServidor(mostrarMensaje = false)
                                snackbarHostState.showSnackbar(
                                    "Inscripción cancelada y asiento liberado en: ${evento.titulo}"
                                )
                            }
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar("No se pudo cancelar la inscripción en este momento")
                        }
                    }
                }
            )
        }

        eventoParaElegirAsiento?.let { evCine ->
            ModalSelectorAsientosCine(
                evento = evCine,
                onDismiss = { eventoParaElegirAsiento = null },
                onConfirmarAsiento = { codigoElegido ->
                    scope.launch {
                        try {
                            val resp = withContext(Dispatchers.IO) {
                                RetrofitClient.obtenerApi(context).registrarAsistenciaEvento(
                                    eventoId = evCine.id,
                                    body = mapOf("asiento" to codigoElegido)
                                )
                            }
                            if (resp.isSuccessful) {
                                val actualizados = eventosInscritosIds + evCine.id
                                persistirInscritosActivos(actualizados)
                                val prevTomados = (prefsNotif.getStringSet("asientos_tomados_${evCine.id}", emptySet()) ?: emptySet()).toMutableSet()
                                prevTomados.add(codigoElegido)
                                val nuevosTomadosGlobal = (evCine.asientosTomadosEfectivos + prevTomados + codigoElegido).distinct()
                                val ocupadosNuevos = maxOf(evCine.lugaresOcupados + 1, nuevosTomadosGlobal.size)
                                val setQr1 = prefsNotif.getStringSet("eventos_qr_1de2_set", emptySet()) ?: emptySet()
                                val setGps2 = prefsNotif.getStringSet("eventos_gps_2de2_set", emptySet()) ?: emptySet()
                                prefsNotif.edit()
                                    .putString("mi_asiento_${evCine.id}", codigoElegido)
                                    .putStringSet("asientos_tomados_${evCine.id}", nuevosTomadosGlobal.toSet())
                                    .putInt("ocupados_min_${evCine.id}", ocupadosNuevos)
                                    .putLong("ts_apartado_${evCine.id}", System.currentTimeMillis())
                                    .apply()
                                sincronizarMetaAsientoEnServidor(
                                    evento = evCine,
                                    asiento = codigoElegido,
                                    qrValidado = setQr1.contains(evCine.id) || setGps2.contains(evCine.id),
                                    gpsValidado = setGps2.contains(evCine.id)
                                )

                                eventosServidor = eventosServidor.map { item ->
                                    if (item.id == evCine.id) {
                                        item.copy(
                                            inscrito = true,
                                            miAsiento = codigoElegido,
                                            asientosOcupados = ocupadosNuevos,
                                            asientosTomados = nuevosTomadosGlobal
                                        )
                                    } else item
                                }

                                eventoParaElegirAsiento = null
                                verificarYDispararAviso30MinFin(context, evCine, actualizados)
                                recargarEventosDesdeServidor(mostrarMensaje = false)
                                snackbarHostState.showSnackbar(
                                    "¡Asiento $codigoElegido apartado con éxito en ${evCine.titulo}!"
                                )
                            } else if (resp.code() == 409) {
                                snackbarHostState.showSnackbar(
                                    "El asiento $codigoElegido acaba de ser ocupado o reservado. Elige otro lugar disponible."
                                )
                            } else {
                                snackbarHostState.showSnackbar(
                                    "No fue posible apartar el asiento (${resp.code()})."
                                )
                            }
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar("Error de conexión al apartar tu asiento.")
                        }
                    }
                }
            )
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
    val context = LocalContext.current
    val prefsNotif = remember { context.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE) }
    var isSaved by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val alphaAnim by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 320, delayMillis = (index * 50).coerceAtMost(250)),
        label = "alphaCard"
    )
    val slideAnim by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "slideCard"
    )

    val heartScale by animateFloatAsState(
        targetValue = if (isSaved) 1.22f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "heartScale"
    )

    val catInfo = detectarCategoriaEvento(evento)
    val (mesTxt, diaTxt, horaTxt) = extraerFechaVisual(evento.fechaHora)
    val horaFinTxt = parsearZonedDateTimeMx(evento.fechaFinEfectiva)?.let {
        String.format(Locale.US, "%02d:%02d", it.hour, it.minute)
    }
    val miAsiento = evento.miAsiento ?: prefsNotif.getString("mi_asiento_${evento.id}", null)

    val disponibles = evento.lugaresDisponibles
    val capacidad = evento.cupoMaximo
    val agotado = evento.cupoAgotado

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (estaInscrito) Color(0xFFF8FAFC) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = if (estaInscrito) BorderStroke(1.5.dp, EtdaSuccess.copy(alpha = 0.6f)) else BorderStroke(1.dp, EtdaNavy.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .graphicsLayer {
                alpha = alphaAnim
                translationY = slideAnim
            }
            .clip(RoundedCornerShape(22.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(EtdaNavy, EtdaBlue)
                        )
                    )
                    .padding(vertical = 14.dp, horizontal = 6.dp),
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
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (horaFinTxt != null) "$horaTxt - $horaFinTxt" else "$horaTxt hrs",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
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
                    fontSize = 15.5.sp,
                    color = EtdaNavy,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(5.dp))

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
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val progresoCard by animateFloatAsState(
                    targetValue = evento.porcentajeOcupacion,
                    animationSpec = tween(250),
                    label = "progresoCard"
                )
                LinearProgressIndicator(
                    progress = { progresoCard },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        agotado -> Color(0xFFDC2626)
                        evento.porcentajeOcupacion >= 0.85f -> EtdaOrange
                        else -> EtdaBlue
                    },
                    trackColor = Color(0xFFE2E8F0)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (agotado) Color(0xFFFEF2F2) else EtdaBg,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (agotado) Color(0xFFFECACA) else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventSeat,
                                    contentDescription = null,
                                    tint = if (agotado) Color(0xFFDC2626) else EtdaBlue,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (agotado) "Agotado (0/$capacidad)" else "$disponibles/$capacidad libres",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (agotado) Color(0xFFDC2626) else EtdaNavy
                                )
                            }
                        }
                    }

                    if (estaInscrito) {
                        Surface(
                            color = EtdaSuccess.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, EtdaSuccess.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventSeat,
                                    contentDescription = null,
                                    tint = EtdaSuccess,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (!miAsiento.isNullOrBlank()) "Asiento $miAsiento" else "Inscrito",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF065F46)
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Elegir lugar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
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
    qrValidado: Boolean,
    gpsValidado: Boolean,
    onDismiss: () -> Unit,
    onAbrirSelectorAsientos: () -> Unit,
    onAbrirEscaner: () -> Unit,
    onCancelarInscripcion: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val catInfo = detectarCategoriaEvento(evento)
    val (mesTxt, diaTxt, horaTxt) = extraerFechaVisual(evento.fechaHora)
    val (horarioInicioTexto, horarioFinTexto) = remember(evento.fechaHora, evento.fechaFinEfectiva) {
        extraerHorarioInicioYFin(evento.fechaHora, evento.fechaFinEfectiva)
    }

    val capacidad = evento.cupoMaximo
    val ocupadosActuales = evento.lugaresOcupados.coerceIn(0, capacidad)
    val disponibles = (capacidad - ocupadosActuales).coerceAtLeast(0)
    val progresoObjetivo = (ocupadosActuales.toFloat() / capacidad.toFloat()).coerceIn(0f, 1f)
    val estaLleno = disponibles == 0

    val progresoAnimado by animateFloatAsState(
        targetValue = progresoObjetivo,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "progresoCupoReal"
    )

    val colorBarraCupo by animateColorAsState(
        targetValue = when {
            estaLleno -> Color(0xFFDC2626)
            progresoObjetivo >= 0.85f -> EtdaOrange
            else -> EtdaBlue
        },
        animationSpec = tween(250),
        label = "colorBarraCupo"
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            if (!evento.imagenUrl.isNullOrBlank()) {
                AsyncImage(
                    model = evento.imagenUrl,
                    contentDescription = "Flyer de ${evento.titulo}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Cabecera del Evento
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(EtdaNavy, EtdaBlue)
                        )
                    )
                    .padding(18.dp)
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
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Surface(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "$diaTxt $mesTxt | $horaTxt hrs",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = evento.titulo,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 23.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // TARJETA DE HORARIO DE INICIO Y FINALIZACIÓN CLARAMENTE LEGIBLE
            Surface(
                color = EtdaBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, EtdaBlue.copy(alpha = 0.22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = EtdaBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "HORARIO OFICIAL DEL EVENTO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EtdaNavy,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Hora de Inicio",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EtdaBlue
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = horarioInicioTexto,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EtdaNavy,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Hora de Término",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EtdaOrange
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = horarioFinTexto,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EtdaNavy,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ubicación y Recompensa
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (!evento.urlMaps.isNullOrBlank()) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(evento.urlMaps))
                                context.startActivity(intent)
                            } else {
                                val query = Uri.encode("UNACH ETDA-CI ${evento.lugar}")
                                val uri = Uri.parse("geo:0,0?q=$query")
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                } else {
                                    val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                }
                            }
                        },
                    color = EtdaBg,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = EtdaBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Ubicación / Recinto", fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, softWrap = false)
                            Text(
                                text = evento.lugar,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = EtdaNavy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Toca para abrir Maps",
                                    fontSize = 9.5.sp,
                                    color = EtdaBlue,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = EtdaBlue,
                                    modifier = Modifier.size(9.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = EtdaOrange.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EtdaOrange.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = EtdaOrange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Recompensa (QR+GPS)", fontSize = 10.sp, color = EtdaOrange, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            Text(
                                text = "+1 en ${catInfo.nombre}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = EtdaNavy,
                                maxLines = 2,
                                lineHeight = 14.sp,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // BARRA DE OCUPACIÓN DEL RECINTO REDISEÑADA (SIN TEXTO CORTADO NI ENCIMADO)
            Surface(
                color = EtdaBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, EtdaNavy.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventSeat,
                                contentDescription = null,
                                tint = EtdaOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ocupación del recinto",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = EtdaNavy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = colorBarraCupo.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (estaLleno) "Cupo lleno" else "$disponibles disponibles",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorBarraCupo,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progresoAnimado },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = colorBarraCupo,
                        trackColor = Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Asientos ocupados: $ocupadosActuales de $capacidad",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )
                        if (!evento.miAsiento.isNullOrBlank()) {
                            Text(
                                text = "Tu lugar: ${evento.miAsiento}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = EtdaSuccess
                            )
                        } else {
                            Text(
                                text = "${(progresoObjetivo * 100).toInt()}% ocupado",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EtdaNavy
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Descripción del Evento
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Acerca del evento",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = EtdaNavy
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = evento.descripcionLimpia,
                        color = Color(0xFF475569),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Estado de Asiento Apartado y Doble Validación (1/2 QR + 2/2 GPS)
            if (estaInscrito) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                    border = BorderStroke(1.5.dp, EtdaSuccess.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EtdaSuccess, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = if (!evento.miAsiento.isNullOrBlank())
                                            "Asiento Apartado: ${evento.miAsiento}"
                                        else
                                            "Lugar asegurado",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF065F46),
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Tu lugar está respetado en el mapa del recinto",
                                        color = Color(0xFF047857),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            TextButton(
                                onClick = onCancelarInscripcion,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Liberar", color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Tarjetas de Progreso 1/2 QR y 2/2 GPS
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, EtdaSuccess.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "PROGRESO DE VALIDACIÓN EN LA CONFERENCIA:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EtdaNavy,
                                    letterSpacing = 0.4.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (qrValidado) Icons.Default.CheckCircle else Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = if (qrValidado) EtdaSuccess else EtdaOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (qrValidado)
                                            "1/2 Código QR Oficial: ¡Validado!"
                                        else
                                            "1/2 Código QR Oficial: Escanea al llegar al recinto",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (qrValidado) Color(0xFF065F46) else EtdaNavy
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (gpsValidado) Icons.Default.CheckCircle else Icons.Default.ShareLocation,
                                        contentDescription = null,
                                        tint = if (gpsValidado) EtdaSuccess else EtdaBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            gpsValidado -> "2/2 Permanencia GPS: ¡Confirmada! (+1 en ${catInfo.nombre})"
                                            qrValidado -> "2/2 Permanencia GPS: Verificando tu ubicación en la sala..."
                                            else -> "2/2 Permanencia GPS: Se activará tras escanear el QR"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (gpsValidado) Color(0xFF065F46) else Color(0xFF475569)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onAbrirEscaner,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF059669),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (qrValidado) "QR 1/2 Validado • Volver a Escanear" else "Escanear QR Oficial (Validación 1/2)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            ArchivosDelEvento(evento.materiales)

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, EtdaNavy),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EtdaNavy),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Cerrar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = EtdaNavy,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = {
                        if (estaInscrito) {
                            onAbrirSelectorAsientos()
                        } else if (!estaLleno) {
                            onAbrirSelectorAsientos()
                        }
                    },
                    enabled = !estaLleno || estaInscrito,
                    modifier = Modifier
                        .weight(1.45f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (estaInscrito) EtdaNavy else EtdaOrange,
                        contentColor = Color.White,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.45f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EventSeat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            estaInscrito -> if (!evento.miAsiento.isNullOrBlank()) "Ver Sala (Asiento ${evento.miAsiento})" else "Ver Mapa de Asientos"
                            estaLleno -> "CUPO AGOTADO"
                            else -> "Inscribirme • Elegir Asiento"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Modal interactivo estilo Sala de Cine para elegir 1 asiento del recinto,
 * sincronizado en tiempo real con el Catálogo de Auditorios y las reservas de Administración.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModalSelectorAsientosCine(
    evento: Evento,
    onDismiss: () -> Unit,
    onConfirmarAsiento: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var cargandoMapa by remember { mutableStateOf(true) }
    val prefsCine = remember { context.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE) }
    val miAsientoInicial = evento.miAsiento ?: prefsCine.getString("mi_asiento_${evento.id}", null)
    val reservadosInicial = evento.asientosReservadosAdminEfectivos.toSet()
    val tomadosInicial = remember(evento, miAsientoInicial) {
        val base = (evento.asientosTomadosEfectivos + (prefsCine.getStringSet("asientos_tomados_${evento.id}", emptySet()) ?: emptySet())).toMutableSet()
        base.removeAll(reservadosInicial)
        val faltantes = (evento.lugaresOcupados - (base.size + reservadosInicial.size)).coerceAtLeast(0)
        var idx = 0
        var agregados = 0
        while (agregados < faltantes && idx < evento.cupoMaximo) {
            val cod = generarCodigoAsientoCineApp(idx, 10)
            if (!base.contains(cod) && !reservadosInicial.contains(cod) && cod != miAsientoInicial) {
                base.add(cod)
                agregados++
            }
            idx++
        }
        base.toSet()
    }
    var capacidadRecinto by remember { mutableIntStateOf(evento.cupoMaximo) }
    var miAsientoActual by remember { mutableStateOf(miAsientoInicial) }
    var asientosOcupadosAlumnos by remember { mutableStateOf(tomadosInicial) }
    var asientosReservadosMaestros by remember { mutableStateOf(reservadosInicial) }
    var asientoElegido by remember { mutableStateOf<String?>(miAsientoInicial) }

    LaunchedEffect(evento.id) {
        cargandoMapa = true
        try {
            val remoto = withContext(Dispatchers.IO) {
                RetrofitClient.obtenerApi(context).obtenerMapaAsientosEvento(evento.id)
            }
            if (remoto.capacidadTotal > 0) {
                capacidadRecinto = maxOf(evento.cupoMaximo, remoto.capacidadTotal)
            }
            if (!remoto.miAsiento.isNullOrBlank()) {
                miAsientoActual = remoto.miAsiento
                asientoElegido = remoto.miAsiento
            }
            val resAdmin = remoto.asientosReservadosAdmin.orEmpty().toSet()
            val tomados = remoto.asientosTomados.orEmpty().toSet()
            asientosReservadosMaestros = resAdmin
            asientosOcupadosAlumnos = tomados - resAdmin
        } catch (_: Exception) {
        } finally {
            cargandoMapa = false
        }
    }

    val colsPorFila = 10
    val totalFilas = (capacidadRecinto + colsPorFila - 1) / colsPorFila
    val totalOcupados = (asientosOcupadosAlumnos + asientosReservadosMaestros).size.coerceAtMost(capacidadRecinto)
    val totalLibres = (capacidadRecinto - totalOcupados).coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            // Encabezado del Selector de Asientos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = EtdaOrange.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "SELECCIÓN DE LUGAR • 1 ASIENTO POR ALUMNO",
                            color = EtdaOrange,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = evento.lugar,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = EtdaNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$totalLibres disponibles de $capacidadRecinto asientos en el recinto",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = EtdaNavy)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Leyenda de Colores
            Surface(
                color = EtdaBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LeyendaAsientoItem(colorFondo = Color.White, colorBorde = EtdaNavy.copy(alpha = 0.4f), texto = "Libre")
                    LeyendaAsientoItem(colorFondo = EtdaSuccess, colorBorde = EtdaSuccess, texto = "Tu Lugar")
                    LeyendaAsientoItem(colorFondo = Color(0xFF94A3B8), colorBorde = Color(0xFF64748B), texto = "Ocupado")
                    LeyendaAsientoItem(colorFondo = EtdaOrange, colorBorde = EtdaOrange, texto = "Reservado VIP")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Contenedor oscuro estilo Sala de Cine
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 380.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pantalla / Estrado Principal
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(EtdaBlue.copy(alpha = 0.4f), EtdaOrange, EtdaBlue.copy(alpha = 0.4f))
                                )
                            )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ESTRADO / ESCENARIO PRINCIPAL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFB74D),
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (cargandoMapa) {
                        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EtdaOrange, strokeWidth = 2.5.dp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(totalFilas) { filaIdx ->
                                val letraFila = if (filaIdx < 26) {
                                    ('A' + filaIdx).toString()
                                } else {
                                    "${('A' + (filaIdx / 26) - 1)}${('A' + (filaIdx % 26))}"
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = letraFila,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.width(18.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    for (col in 0 until colsPorFila) {
                                        if (col == 5) {
                                            Spacer(modifier = Modifier.width(10.dp))
                                        }
                                        val seatIdx = filaIdx * colsPorFila + col
                                        if (seatIdx < capacidadRecinto) {
                                            val codigo = generarCodigoAsientoCineApp(seatIdx, colsPorFila)
                                            val esMioActual = codigo == miAsientoActual
                                            val esReservadoAdmin = asientosReservadosMaestros.contains(codigo)
                                            val esOcupadoOtro = !esMioActual && (asientosOcupadosAlumnos.contains(codigo) || esReservadoAdmin)
                                            val esSeleccionado = asientoElegido == codigo

                                            val bgButaca = when {
                                                esSeleccionado || esMioActual -> EtdaSuccess
                                                esReservadoAdmin -> EtdaOrange
                                                esOcupadoOtro -> Color(0xFF475569)
                                                else -> Color(0xFF1E293B)
                                            }
                                            val bordeButaca = when {
                                                esSeleccionado || esMioActual -> Color.White
                                                esReservadoAdmin -> Color(0xFFFFCC80)
                                                esOcupadoOtro -> Color(0xFF334155)
                                                else -> EtdaBlue.copy(alpha = 0.65f)
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 1.5.dp)
                                                    .size(width = 24.dp, height = 24.dp)
                                                    .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                                    .background(bgButaca)
                                                    .border(
                                                        width = if (esSeleccionado) 1.8.dp else 1.dp,
                                                        color = bordeButaca,
                                                        shape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
                                                    )
                                                    .clickable(enabled = !esOcupadoOtro) {
                                                        asientoElegido = codigo
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (esReservadoAdmin) "★" else "${col + 1}",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (esOcupadoOtro && !esReservadoAdmin) Color(0xFF94A3B8) else Color.White,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.width(27.dp))
                                        }
                                    }

                                    Text(
                                        text = letraFila,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.width(18.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Botón de confirmación o cambio del asiento elegido
            if (!miAsientoActual.isNullOrBlank() && (asientoElegido == null || asientoElegido == miAsientoActual)) {
                Surface(
                    color = Color(0xFFECFDF5),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EtdaSuccess.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = EtdaSuccess, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tu lugar apartado es $miAsientoActual (toca otro asiento libre si deseas cambiarlo)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF065F46)
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        asientoElegido?.let { onConfirmarAsiento(it) }
                    },
                    enabled = !asientoElegido.isNullOrBlank() && (totalLibres > 0 || !miAsientoActual.isNullOrBlank()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EtdaOrange,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFCBD5E1)
                    )
                ) {
                    Icon(Icons.Default.EventSeat, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            !miAsientoActual.isNullOrBlank() && !asientoElegido.isNullOrBlank() ->
                                "Cambiar mi lugar a Asiento $asientoElegido"
                            !asientoElegido.isNullOrBlank() ->
                                "Confirmar y Apartar Asiento $asientoElegido"
                            else ->
                                "Toca un asiento libre en el mapa para elegirlo"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun LeyendaAsientoItem(colorFondo: Color, colorBorde: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colorFondo)
                .border(1.dp, colorBorde, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = texto,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = EtdaNavy
        )
    }
}

@Composable
private fun ArchivosDelEvento(archivos: List<ArchivoEvento>) {
    if (archivos.isEmpty()) return
    val context = LocalContext.current
    val abrir: (ArchivoEvento) -> Unit = { archivo ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(archivo.url)))
        } catch (_: ActivityNotFoundException) {
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