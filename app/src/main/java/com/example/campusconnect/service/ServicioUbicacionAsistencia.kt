package com.example.campusconnect.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.campusconnect.MyFirebaseMessagingService
import com.example.campusconnect.TokenManager
import com.example.campusconnect.model.Evento
import com.example.campusconnect.network.RetrofitClient
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

class ServicioUbicacionAsistencia : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var eventoId: String? = null
    private var eventoTitulo: String = "Evento Académico"
    private var eventoCategoria: String = "Académicos"
    private var contadorPingsEnRecinto: Int = 0
    private var rastreoActivo: Boolean = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val lat = location.latitude
                    val lon = location.longitude
                    val idActual = eventoId ?: continue

                    // 1. Verificar distancia física real al recinto UNACH Campus I / ETDA
                    val (dentroDelRecinto, distanciaMetros) = verificarDistanciaRecintoUnach(lat, lon)

                    if (!dentroDelRecinto) {
                        // Si el alumno NO está físicamente en el recinto, NO contar ping ni validar asistencia
                        contadorPingsEnRecinto = 0
                        Log.w(
                            "AsistenciaGPS",
                            "Ubicación fuera del recinto UNACH (${distanciaMetros.roundToInt()} m) -> Lat: $lat, Lon: $lon"
                        )
                        actualizarNotificacionProgresoGps(
                            titulo = "📍 Fuera del recinto (${distanciaMetros.roundToInt()} m)",
                            texto = "Acércate a la sede de '$eventoTitulo' en Campus I UNACH para validar tu permanencia GPS (2/2)."
                        )
                        continue
                    }

                    // 2. El alumno SÍ se encuentra físicamente dentro del recinto UNACH
                    contadorPingsEnRecinto += 1
                    val completarSegundoPing = contadorPingsEnRecinto >= 2

                    Log.d(
                        "AsistenciaGPS",
                        "Ping en recinto #$contadorPingsEnRecinto (${distanciaMetros.roundToInt()} m) -> Lat: $lat, Lon: $lon, Evento: $idActual"
                    )

                    if (!completarSegundoPing) {
                        actualizarNotificacionProgresoGps(
                            titulo = "Validación 1/2 lista • En el recinto (${distanciaMetros.roundToInt()} m)",
                            texto = "Verificando permanencia en '$eventoTitulo' (Ping 1/2 confirmado). Mantén tu GPS activo."
                        )
                    }

                    serviceScope.launch {
                        var acreditadoRemoto = false
                        var categoriaAcreditada = normalizarCategoriaRecompensa(eventoCategoria)

                        try {
                            val api = RetrofitClient.obtenerApi(applicationContext)
                            val respuesta = api.enviarPingGpsEvento(
                                eventoId = idActual,
                                body = mapOf(
                                    "latitud" to lat,
                                    "longitud" to lon,
                                    "finalizar" to completarSegundoPing
                                )
                            )
                            if (respuesta.isSuccessful) {
                                val raw = respuesta.body()?.string().orEmpty()
                                if (raw.isNotBlank()) {
                                    val json = JSONObject(raw)
                                    val gpsVal = json.optBoolean("validacionGps", false) || json.optBoolean("asistio", false)
                                    val catSrv = json.optString("categoria", "")
                                    if (catSrv.isNotBlank()) {
                                        categoriaAcreditada = normalizarCategoriaRecompensa(catSrv)
                                    }
                                    if (gpsVal) {
                                        acreditadoRemoto = true
                                    }
                                }
                            } else if (completarSegundoPing) {
                                // Respaldo en caso de que el servidor remoto use POST /eventos/{id}/registro
                                val respReg = api.registrarAsistenciaEvento(
                                    eventoId = idActual,
                                    body = mapOf(
                                        "latitud" to lat,
                                        "longitud" to lon,
                                        "codigo_qr" to "CCUNACH|EVENTO|$idActual",
                                        "asistio" to true
                                    )
                                )
                                if (respReg.isSuccessful) {
                                    acreditadoRemoto = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("AsistenciaGPS", "Aviso al sincronizar ping GPS con servidor: ${e.message}")
                        }

                        // ÚNICAMENTE acreditar cuando el alumno está físicamente en el recinto y cumplió los 2 pings
                        if (dentroDelRecinto && completarSegundoPing && acreditadoRemoto) {
                            acreditarValidacionGps2De2(
                                context = applicationContext,
                                eventoId = idActual,
                                titulo = eventoTitulo,
                                categoria = categoriaAcreditada
                            )
                            detenerRastreoGPS()
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Asegurar que el monitor automático de cartelera y notificaciones esté activo
        iniciarMonitorEnSegundoPlano(applicationContext)

        val accion = intent?.action ?: ACCION_INICIAR_RASTREO_QR
        if (accion == ACCION_SOLO_MONITOR) {
            if (!rastreoActivo) {
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val idRecibido = intent?.getStringExtra("EVENTO_ID")
        if (idRecibido.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Verificar que el alumno realmente haya escaneado el QR (Validación 1/2) antes de rastrear GPS
        val prefsNotif = getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE)
        val setQr1 = prefsNotif.getStringSet("eventos_qr_1de2_set", emptySet()) ?: emptySet()
        if (!setQr1.contains(idRecibido)) {
            Log.w("AsistenciaGPS", "Se ignoró inicio de GPS sin escaneo QR previo para evento $idRecibido")
            stopSelf()
            return START_NOT_STICKY
        }

        eventoId = idRecibido
        eventoTitulo = intent.getStringExtra("EVENTO_TITULO") ?: "Evento Académico UNACH"
        eventoCategoria = intent.getStringExtra("EVENTO_CATEGORIA") ?: "Académicos"
        contadorPingsEnRecinto = 0
        rastreoActivo = true

        crearCanalNotificacion()

        val notificacion = NotificationCompat.Builder(this, "ASISTENCIA_CHANNEL")
            .setContentTitle("Validación 1/2 lista • Verificando recinto por GPS")
            .setContentText("Comprobando ubicación en Campus I UNACH para '$eventoTitulo' (+1 en $eventoCategoria).")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_FOREGROUND_ID, notificacion)
        iniciarRastreoGPS()

        return START_REDELIVER_INTENT
    }

    private fun actualizarNotificacionProgresoGps(titulo: String, texto: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notif = NotificationCompat.Builder(this, "ASISTENCIA_CHANNEL")
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build()
            manager.notify(NOTIF_FOREGROUND_ID, notif)
        } catch (_: Exception) {}
    }

    private fun iniciarRastreoGPS() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(20_000L)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("AsistenciaGPS", "Permiso de ubicación denegado", e)
        }
    }

    private fun detenerRastreoGPS() {
        rastreoActivo = false
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        detenerRastreoGPS()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ASISTENCIA_CHANNEL",
                "Verificación de Asistencia ETDA",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACCION_INICIAR_RASTREO_QR = "com.example.campusconnect.INICIAR_RASTREO_QR"
        const val ACCION_SOLO_MONITOR = "com.example.campusconnect.SOLO_MONITOR"
        private const val NOTIF_FOREGROUND_ID = 1001

        // Coordenadas oficiales de UNACH Campus I / ETDA en Tuxtla Gutiérrez, Chiapas
        private val SEDES_UNACH_COORDS = listOf(
            16.7542 to -93.1538, // ETDA / Edificio Principal Campus I
            16.7529 to -93.1526, // Auditorio Los Constituyentes / Biblioteca Central Campus I
            16.7551 to -93.1549  // Zona Norte Campus I UNACH
        )
        private const val RADIO_PERMITIDO_METROS = 280f

        private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile
        private var monitorJob: Job? = null

        /**
         * Calcula la distancia física en metros hasta el punto más cercano de Campus I UNACH / ETDA.
         * Devuelve (true, distanciaMetros) únicamente si el alumno se encuentra dentro del radio permitido.
         */
        fun verificarDistanciaRecintoUnach(lat: Double, lon: Double): Pair<Boolean, Float> {
            var menorDistancia = Float.MAX_VALUE
            for ((sedeLat, sedeLon) in SEDES_UNACH_COORDS) {
                val res = FloatArray(1)
                Location.distanceBetween(lat, lon, sedeLat, sedeLon, res)
                if (res[0] < menorDistancia) {
                    menorDistancia = res[0]
                }
            }
            return (menorDistancia <= RADIO_PERMITIDO_METROS) to menorDistancia
        }

        fun normalizarCategoriaRecompensa(raw: String?): String {
            val limpio = (raw ?: "").trim().lowercase()
            return when {
                limpio.contains("tutor") -> "Tutorías"
                limpio.contains("conferenc") || limpio.contains("ponenc") -> "Conferencias"
                limpio.contains("taller") -> "Talleres"
                limpio.contains("deport") || limpio.contains("torneo") -> "Deporte"
                limpio.contains("cultur") || limpio.contains("arte") -> "Culturales"
                else -> "Académicos"
            }
        }

        fun acreditarValidacionGps2De2(
            context: Context,
            eventoId: String,
            titulo: String,
            categoria: String
        ) {
            val catNorm = normalizarCategoriaRecompensa(categoria)
            val prefsNotif = context.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE)
            val prefsRecompensas = context.getSharedPreferences("cc_recompensas_validadas", Context.MODE_PRIVATE)

            val setGps2 = (prefsNotif.getStringSet("eventos_gps_2de2_set", emptySet()) ?: emptySet()).toMutableSet()
            setGps2.add(eventoId)
            prefsNotif.edit().putStringSet("eventos_gps_2de2_set", setGps2).apply()

            val claveAcreditado = "acreditado_evento_$eventoId"
            if (!prefsRecompensas.getBoolean(claveAcreditado, false)) {
                val actualCat = prefsRecompensas.getInt("asistencias_cat_$catNorm", 0)
                val eventosCatSet = (prefsRecompensas.getStringSet("eventos_cat_$catNorm", emptySet()) ?: emptySet()).toMutableSet()
                eventosCatSet.add(eventoId)

                prefsRecompensas.edit()
                    .putBoolean(claveAcreditado, true)
                    .putInt("asistencias_cat_$catNorm", maxOf(actualCat + 1, eventosCatSet.size))
                    .putStringSet("eventos_cat_$catNorm", eventosCatSet)
                    .apply()

                MyFirebaseMessagingService.mostrarNotificacion(
                    context = context,
                    titulo = "🏅 ¡Asistencia 2/2 Validada (+1 en $catNorm)!",
                    cuerpo = "Se confirmó tu QR y permanencia en el recinto para \"$titulo\". Revisa tu progreso en Insignias.",
                    notifId = ("recompensa_$eventoId").hashCode()
                )
            }
        }

        /**
         * Monitor automático en segundo plano que corre independientemente de qué pantalla esté abierta
         * o si el usuario no toca ni refresca la cartelera:
         * - Verifica cada 2 segundos si algún evento entró en sus últimos 30 minutos o acaba de finalizar.
         * - Sincroniza con el servidor cada 8 segundos para detectar nuevos eventos o cambios de horario.
         */
        fun iniciarMonitorEnSegundoPlano(context: Context) {
            val appCtx = context.applicationContext
            if (monitorJob?.isActive == true) return

            monitorJob = monitorScope.launch {
                var ultimoSyncRedMs = 0L
                while (isActive) {
                    try {
                        val ahoraMs = System.currentTimeMillis()
                        if (ahoraMs - ultimoSyncRedMs >= 8_000L) {
                            ultimoSyncRedMs = ahoraMs
                            val jwt = TokenManager.obtenerToken(appCtx)
                            if (!jwt.isNullOrBlank()) {
                                try {
                                    val remotos = RetrofitClient.obtenerApi(appCtx).obtenerEventos()
                                    procesarEventosYNotificacionesAutomaticas(appCtx, remotos)
                                } catch (_: Exception) {}
                            }
                        }
                        verificarRelojLocalEventosEnCache(appCtx)
                    } catch (_: Exception) {}
                    delay(2_000L)
                }
            }
        }

        private fun parsearInstanteMx(raw: String?): Instant? {
            if (raw.isNullOrBlank()) return null
            val limpio = raw.trim()
            val zoneMx = ZoneId.of("America/Mexico_City")
            return try {
                OffsetDateTime.parse(limpio).toInstant()
            } catch (_: Exception) {
                try {
                    Instant.parse(limpio)
                } catch (_: Exception) {
                    try {
                        val norm = limpio.replace(" ", "T").take(19)
                        val padded = if (norm.length == 16) "$norm:00" else norm
                        LocalDateTime.parse(padded).atZone(zoneMx).toInstant()
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }

        fun procesarEventosYNotificacionesAutomaticas(context: Context, remotos: List<Evento>) {
            val prefsNotif = context.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE)
            val ahora = Instant.now()
            val vigentes = remotos.filter { it.estaVigenteEnTiempoReal() }
            val idsVigentes = vigentes.map { it.id }.toSet()

            val inscritosGuardados = prefsNotif.getStringSet("eventos_inscritos_ids_v2", emptySet()) ?: emptySet()
            val nuevosInscritos = mutableSetOf<String>()
            for (ev in vigentes) {
                if (ev.inscrito == true || inscritosGuardados.contains(ev.id)) {
                    nuevosInscritos.add(ev.id)
                }
            }

            // Cargar caché previa de eventos vistos
            val guardadosCache = prefsNotif.getStringSet("eventos_activos_vistos_v2", emptySet()) ?: emptySet()
            val mapaCache = mutableMapOf<String, Array<String>>()
            for (linea in guardadosCache) {
                val p = linea.split("|||")
                if (p.size >= 5 && p[0].isNotBlank()) {
                    mapaCache[p[0]] = arrayOf(p[0], p[1], p[2], p[3], p[4])
                }
            }

            // Verificar si algún evento que estaba en caché acaba de finalizar o salió del feed por término
            for ((idPrevio, datosPrevios) in mapaCache.entries.toList()) {
                val titPrevio = datosPrevios[1]
                val lugPrevio = datosPrevios[2]
                val finPrevioStr = datosPrevios[3].ifBlank { null }
                val iniPrevioStr = datosPrevios[4]
                val finInstant = parsearInstanteMx(finPrevioStr)
                    ?: parsearInstanteMx(iniPrevioStr)?.plus(Duration.ofHours(4))
                val segRestantes = if (finInstant != null) Duration.between(ahora, finInstant).seconds else 0L
                val yaTermino = finInstant != null && !ahora.isBefore(finInstant)
                val salioPorFin = !idsVigentes.contains(idPrevio) && segRestantes <= 90L

                if (yaTermino || salioPorFin) {
                    dispararNotifFinalizadoUnica(
                        context = context,
                        eventoId = idPrevio,
                        titulo = titPrevio,
                        lugar = lugPrevio,
                        firmaFin = finPrevioStr ?: iniPrevioStr
                    )
                    mapaCache.remove(idPrevio)
                    nuevosInscritos.remove(idPrevio)
                }
            }

            // Revisar eventos vigentes: nuevos eventos, aviso de 30 minutos y actualizar caché
            val idsConocidos = (prefsNotif.getStringSet("ids_eventos_conocidos", emptySet()) ?: emptySet()).toMutableSet()
            val primeraVez = !prefsNotif.contains("ids_eventos_conocidos")

            for (ev in vigentes) {
                val finEf = ev.fechaFinEfectiva
                val dispEf = ev.disponibleDesdeEfectivo
                mapaCache[ev.id] = arrayOf(
                    ev.id,
                    ev.titulo.replace("|||", " "),
                    ev.lugar.replace("|||", " "),
                    (finEf ?: "").replace("|||", ""),
                    ev.fechaHora.replace("|||", "")
                )

                val dispInst = parsearInstanteMx(dispEf)
                val aparecioRecien = dispInst != null && Duration.between(dispInst, ahora).seconds in 0..600
                val esNuevo = !primeraVez && !idsConocidos.contains(ev.id)
                val claveAparicion = "notificado_evento_disponible_${ev.id}"

                if ((esNuevo || aparecioRecien) && !prefsNotif.getBoolean(claveAparicion, false)) {
                    prefsNotif.edit().putBoolean(claveAparicion, true).apply()
                    MyFirebaseMessagingService.mostrarNotificacion(
                        context = context,
                        titulo = "¡Nuevo evento disponible: ${ev.titulo}!",
                        cuerpo = "Sede: ${ev.lugar} · Ya puedes consultar los detalles y apartar tu asiento en Campus Connect UNACH.",
                        notifId = ("nuevo_${ev.id}").hashCode()
                    )
                }
                idsConocidos.add(ev.id)

                // Verificar si faltan 30 minutos o menos para que termine
                verificarAviso30MinUnico(
                    context = context,
                    eventoId = ev.id,
                    titulo = ev.titulo,
                    lugar = ev.lugar,
                    fechaFinEfectiva = finEf,
                    fechaHoraInicio = ev.fechaHora
                )
            }

            // Revisar también si algún evento de la lista cruda viene marcado como recién finalizado
            for (ev in remotos) {
                if (!ev.estaVigenteEnTiempoReal()) {
                    val finInst = parsearInstanteMx(ev.fechaFinEfectiva)
                        ?: parsearInstanteMx(ev.fechaHora)?.plus(Duration.ofHours(4))
                    val haceCuantoTermino = if (finInst != null) Duration.between(finInst, ahora).seconds else 999999L
                    if (haceCuantoTermino in 0..3600) {
                        dispararNotifFinalizadoUnica(
                            context = context,
                            eventoId = ev.id,
                            titulo = ev.titulo,
                            lugar = ev.lugar,
                            firmaFin = ev.fechaFinEfectiva ?: ev.fechaHora
                        )
                    }
                }
            }

            val serializados = mapaCache.values.map {
                "${it[0]}|||${it[1]}|||${it[2]}|||${it[3]}|||${it[4]}"
            }.toSet()

            prefsNotif.edit()
                .putStringSet("eventos_activos_vistos_v2", serializados)
                .putStringSet("eventos_inscritos_ids_v2", nuevosInscritos)
                .putStringSet("ids_eventos_conocidos", idsConocidos)
                .apply()
        }

        fun verificarRelojLocalEventosEnCache(context: Context) {
            val prefsNotif = context.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE)
            val guardadosCache = prefsNotif.getStringSet("eventos_activos_vistos_v2", emptySet()) ?: emptySet()
            if (guardadosCache.isEmpty()) return

            val ahora = Instant.now()
            val nuevosSerializados = mutableSetOf<String>()
            val inscritosActuales = (prefsNotif.getStringSet("eventos_inscritos_ids_v2", emptySet()) ?: emptySet()).toMutableSet()
            var huboCambio = false

            for (linea in guardadosCache) {
                val p = linea.split("|||")
                if (p.size < 5) continue
                val idEv = p[0]
                val titEv = p[1]
                val lugEv = p[2]
                val finStr = p[3].ifBlank { null }
                val iniStr = p[4]

                val finInstant = parsearInstanteMx(finStr)
                    ?: parsearInstanteMx(iniStr)?.plus(Duration.ofHours(4))

                if (finInstant != null && !ahora.isBefore(finInstant)) {
                    dispararNotifFinalizadoUnica(
                        context = context,
                        eventoId = idEv,
                        titulo = titEv,
                        lugar = lugEv,
                        firmaFin = finStr ?: iniStr
                    )
                    inscritosActuales.remove(idEv)
                    huboCambio = true
                } else {
                    verificarAviso30MinUnico(
                        context = context,
                        eventoId = idEv,
                        titulo = titEv,
                        lugar = lugEv,
                        fechaFinEfectiva = finStr,
                        fechaHoraInicio = iniStr
                    )
                    nuevosSerializados.add(linea)
                }
            }

            if (huboCambio) {
                prefsNotif.edit()
                    .putStringSet("eventos_activos_vistos_v2", nuevosSerializados)
                    .putStringSet("eventos_inscritos_ids_v2", inscritosActuales)
                    .putLong("ultimo_cambio_cartelera_ms", System.currentTimeMillis())
                    .apply()
            }
        }

        fun verificarAviso30MinUnico(
            context: Context,
            eventoId: String,
            titulo: String,
            lugar: String,
            fechaFinEfectiva: String?,
            fechaHoraInicio: String
        ) {
            val ahora = Instant.now()
            val finInstant = parsearInstanteMx(fechaFinEfectiva)
                ?: parsearInstanteMx(fechaHoraInicio)?.plus(Duration.ofHours(4))
                ?: return

            val segundosRestantes = Duration.between(ahora, finInstant).seconds
            if (segundosRestantes in 1..1800) {
                val prefs = context.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE)
                val firmaFin = fechaFinEfectiva ?: fechaHoraInicio
                val clave = "notificado_aviso_30m_fin_${eventoId}_$firmaFin"
                if (!prefs.getBoolean(clave, false)) {
                    prefs.edit().putBoolean(clave, true).apply()
                    val minRestantes = ((segundosRestantes + 59) / 60).coerceAtLeast(1)
                    MyFirebaseMessagingService.mostrarNotificacion(
                        context = context,
                        titulo = "⏳ Últimos $minRestantes min: $titulo",
                        cuerpo = "El evento en $lugar está por finalizar. ¡No olvides confirmar tu asistencia!",
                        notifId = ("fin30_$eventoId").hashCode()
                    )
                }
            }
        }

        fun dispararNotifFinalizadoUnica(
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
    }
}