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
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

class ServicioUbicacionAsistencia : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var eventoId: String? = null
    private var eventoTitulo: String = "Evento Académico"
    private var eventoLugar: String = "Auditorio Principal ETDA"
    private var eventoCategoria: String = "Académicos"
    private var contadorPingsEnRecinto: Int = 0
    private var rastreoActivo: Boolean = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val idActual = eventoId ?: continue

                    // 1. BLOQUEO ANTI-FAKE GPS (Ubicación simulada / Mock Location)
                    if (esUbicacionFalsaOSimulada(location)) {
                        contadorPingsEnRecinto = 0
                        guardarEstadoMallaEnVivo(
                            context = applicationContext,
                            eventoId = idActual,
                            estado = "🚫 Ubicación simulada (Fake GPS) detectada. Desactiva apps de ubicación falsa para validar tu asistencia."
                        )
                        actualizarNotificacionProgresoGps(
                            titulo = "🚫 Ubicación simulada bloqueada",
                            texto = "Por seguridad anti-suplantación no se permite usar Fake GPS en '$eventoTitulo'."
                        )
                        continue
                    }

                    // 2. FILTRO DE PRECISIÓN DE SEÑAL GPS (Evita rebotes de antena > 85 m)
                    if (location.hasAccuracy() && location.accuracy > 85f) {
                        guardarEstadoMallaEnVivo(
                            context = applicationContext,
                            eventoId = idActual,
                            estado = "📡 Ajustando precisión GPS (±${location.accuracy.roundToInt()} m)... Acércate a una ventana o entrada del recinto."
                        )
                        continue
                    }

                    val lat = location.latitude
                    val lon = location.longitude

                    serviceScope.launch {
                        // 3. SINCRONIZAR MALLA DE 4 ESQUINAS EN TIEMPO REAL DESDE EL SERVIDOR
                        // Permite que si el Admin edita las 4 esquinas en la Web mientras el alumno tiene la app abierta,
                        // en el siguiente ciclo (4 s) el teléfono descargue la malla nueva y valide automáticamente.
                        val (mallaActual, toleranciaMetros) = obtenerMallaActualizadaDeRecinto(
                            context = applicationContext,
                            eventoId = idActual,
                            nombreAuditorio = eventoLugar
                        )

                        val resultadoMalla = verificarPuntoEnMallaPoligonal(
                            lat = lat,
                            lon = lon,
                            vertices = mallaActual,
                            toleranciaMetros = toleranciaMetros
                        )

                        if (!resultadoMalla.dentroDeArea) {
                            // El dispositivo está FUERA de la malla de 4 esquinas (+ margen de pared)
                            // Por privacidad NO se envían las coordenadas privadas del alumno al servidor.
                            contadorPingsEnRecinto = 0
                            val distBordeInt = resultadoMalla.distanciaAlBordeMetros.roundToInt()
                            val msgFuera = "📍 Fuera de la malla de '$eventoLugar' (a $distBordeInt m del borde · Tolerancia ${toleranciaMetros.roundToInt()} m). Si editas la malla en la web para incluir tu ubicación, se validará en tiempo real."
                            guardarEstadoMallaEnVivo(applicationContext, idActual, msgFuera)

                            Log.w(
                                "AsistenciaGPS",
                                "Fuera de malla poligonal ($distBordeInt m del borde, tol=${toleranciaMetros.roundToInt()} m) -> Evento: $idActual"
                            )
                            actualizarNotificacionProgresoGps(
                                titulo = "📍 Fuera del área delimitada ($distBordeInt m)",
                                texto = "Estás a $distBordeInt m de la malla de '$eventoLugar' (tolerancia ${toleranciaMetros.roundToInt()} m). Ingresa al recinto o actualiza el polígono en la web."
                            )
                            return@launch
                        }

                        // 4. El dispositivo SÍ está DENTRO de la malla de 4 esquinas (o dentro de los 12 m de tolerancia de pared)
                        contadorPingsEnRecinto += 1
                        val completarSegundoPing = contadorPingsEnRecinto >= 2
                        val detalleInterior = if (resultadoMalla.dentroPoligonoExacto) {
                            "dentro de las 4 esquinas"
                        } else {
                            "en margen de pared (${resultadoMalla.distanciaAlBordeMetros.roundToInt()} m ≤ ${toleranciaMetros.roundToInt()} m)"
                        }

                        if (!completarSegundoPing) {
                            val msgPing1 = "✅ Ubicación dentro de la malla ($detalleInterior) • Confirmando permanencia (Ping 1/2)..."
                            guardarEstadoMallaEnVivo(applicationContext, idActual, msgPing1)
                            actualizarNotificacionProgresoGps(
                                titulo = "Validación 1/2 lista • Dentro de la malla",
                                texto = "Ubicación verificada ($detalleInterior) en '$eventoTitulo'. Confirmando permanencia (Ping 1/2)..."
                            )
                        }

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
                            }

                            if (completarSegundoPing) {
                                // Respaldo en caso de que el backend en la nube aún no tenga la malla nueva sincronizada en su tabla local
                                if (!acreditadoRemoto) {
                                    val respReg = api.registrarAsistenciaEvento(
                                        eventoId = idActual,
                                        body = mapOf(
                                            "latitud" to lat.toString(),
                                            "longitud" to lon.toString(),
                                            "codigo_qr" to "CCUNACH|EVENTO|$idActual",
                                            "asistio" to "true"
                                        )
                                    )
                                    if (respReg.isSuccessful) {
                                        acreditadoRemoto = true
                                    }
                                }

                                // Sincronizar también en la lista de asistencia de PostgreSQL (PATCH /asistentes/{id}) y en CCMETA del evento
                                try {
                                    val prefsN = applicationContext.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE)
                                    val emailActual = prefsN.getString("usuario_email_actual", "")?.trim()?.lowercase().orEmpty()
                                    val nombreActual = prefsN.getString("usuario_nombre_actual", "")?.trim().orEmpty()
                                    val asientoGuardado = prefsN.getString("mi_asiento_$idActual", null) ?: "A-01"
                                    if (emailActual.isNotBlank()) {
                                        try {
                                            val asisResp = api.obtenerAsistentesEvento(idActual)
                                            if (asisResp.isSuccessful) {
                                                val arr = JSONArray(asisResp.body()?.string() ?: "[]")
                                                for (i in 0 until arr.length()) {
                                                    val item = arr.optJSONObject(i) ?: continue
                                                    if (item.optString("email", "").trim().lowercase() == emailActual) {
                                                        val asisId = item.optInt("id", -1)
                                                        if (asisId > 0) {
                                                            api.marcarAsistenciaAlumno(idActual, asisId, mapOf("asistio" to true))
                                                            acreditadoRemoto = true
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (_: Exception) {}

                                        val listaEv = api.obtenerEventos()
                                        val evObj = listaEv.find { it.id == idActual }
                                        if (evObj != null) {
                                            val payloadMeta = evObj.construirPayloadSincronizacionMeta(
                                                emailUsuario = emailActual,
                                                nombreUsuario = nombreActual,
                                                asiento = asientoGuardado,
                                                qrValidado = true,
                                                gpsValidado = true,
                                                asistio = true
                                            )
                                            api.actualizarEvento(idActual, payloadMeta)
                                            acreditadoRemoto = true
                                        } else {
                                            acreditadoRemoto = true
                                        }
                                    } else {
                                        acreditadoRemoto = true
                                    }
                                } catch (_: Exception) {
                                    acreditadoRemoto = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("AsistenciaGPS", "Aviso al sincronizar ping GPS con servidor: ${e.message}")
                            if (completarSegundoPing) acreditadoRemoto = true
                        }

                        // ÚNICAMENTE acreditar cuando el dispositivo está físicamente dentro de la malla de 4 esquinas y cumplió los 2 pings
                        if (resultadoMalla.dentroDeArea && completarSegundoPing && acreditadoRemoto) {
                            guardarEstadoMallaEnVivo(
                                context = applicationContext,
                                eventoId = idActual,
                                estado = "🏅 ¡Ubicación validada dentro de la malla ($detalleInterior)! Asistencia 2/2 completada."
                            )
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
        eventoLugar = intent.getStringExtra("EVENTO_LUGAR") ?: "Auditorio Principal ETDA"
        eventoCategoria = intent.getStringExtra("EVENTO_CATEGORIA") ?: "Académicos"
        contadorPingsEnRecinto = 0
        rastreoActivo = true

        guardarEstadoMallaEnVivo(
            context = applicationContext,
            eventoId = idRecibido,
            estado = "🛰️ Verificando ubicación contra la malla de 4 esquinas de '$eventoLugar'..."
        )

        crearCanalNotificacion()

        val notificacion = NotificationCompat.Builder(this, "ASISTENCIA_CHANNEL")
            .setContentTitle("Validación 1/2 lista • Verificando malla GPS")
            .setContentText("Comprobando polígono de 4 esquinas en '$eventoLugar' para '$eventoTitulo'.")
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
        // Intervalo rápido de 4 segundos para que al editar la malla en la página web se refleje en tiempo real
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4_000L)
            .setMinUpdateIntervalMillis(3_000L)
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

    data class ResultadoVerificacionMalla(
        val dentroDeArea: Boolean,
        val dentroPoligonoExacto: Boolean,
        val distanciaAlBordeMetros: Float,
        val toleranciaMetros: Float
    )

    companion object {
        const val ACCION_INICIAR_RASTREO_QR = "com.example.campusconnect.INICIAR_RASTREO_QR"
        const val ACCION_SOLO_MONITOR = "com.example.campusconnect.SOLO_MONITOR"
        private const val NOTIF_FOREGROUND_ID = 1001
        const val TOLERANCIA_PARED_DEFECTO_METROS = 12f

        // Mallas poligonales de 4 esquinas por defecto para los auditorios de UNACH Campus I / ETDA
        private val MALLAS_AUDITORIOS_DEFECTO: Map<String, List<Pair<Double, Double>>> = mapOf(
            "auditorio principal etda" to listOf(
                16.754420 to -93.154050,
                16.754420 to -93.153550,
                16.753980 to -93.153550,
                16.753980 to -93.154050
            ),
            "sala audiovisual 1" to listOf(
                16.754360 to -93.153960,
                16.754360 to -93.153680,
                16.754100 to -93.153680,
                16.754100 to -93.153960
            ),
            "sala audiovisual 2" to listOf(
                16.754340 to -93.153920,
                16.754340 to -93.153660,
                16.754080 to -93.153660,
                16.754080 to -93.153920
            ),
            "laboratorio de cómputo a" to listOf(
                16.754480 to -93.154120,
                16.754480 to -93.153820,
                16.754220 to -93.153820,
                16.754220 to -93.154120
            ),
            "auditorio los constituyentes" to listOf(
                16.753250 to -93.152950,
                16.753250 to -93.152250,
                16.752550 to -93.152250,
                16.752550 to -93.152950
            ),
            "explanada central etda" to listOf(
                16.754600 to -93.154200,
                16.754600 to -93.153400,
                16.753850 to -93.153400,
                16.753850 to -93.154200
            )
        )

        private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile
        private var monitorJob: Job? = null

        /**
         * Detecta si la ubicación proviene de una aplicación de Fake GPS / Mock Location.
         */
        @Suppress("DEPRECATION")
        fun esUbicacionFalsaOSimulada(location: Location): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    location.isMock || location.isFromMockProvider
                } else {
                    location.isFromMockProvider
                }
            } catch (_: Exception) {
                false
            }
        }

        fun guardarEstadoMallaEnVivo(context: Context, eventoId: String, estado: String) {
            try {
                val prefs = context.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("estado_malla_$eventoId", estado)
                    .putLong("estado_malla_ts_$eventoId", System.currentTimeMillis())
                    .apply()
            } catch (_: Exception) {}
        }

        /**
         * Obtiene en tiempo real la malla de 4 esquinas `(lat, lon)` y la tolerancia en metros para el recinto del evento:
         * 1. Primero revisa el evento actualizado en `/eventos/feed` (`ev.mallaPoligonoEfectiva` en `CCMETA`) y `/catalogos/facultad` (`aud.malla`).
         * 2. Guarda en caché local para respuesta inmediata y devuelve los 4 vértices + tolerancia (por defecto 12 m).
         */
        suspend fun obtenerMallaActualizadaDeRecinto(
            context: Context,
            eventoId: String,
            nombreAuditorio: String
        ): Pair<List<Pair<Double, Double>>, Float> {
            val prefs = context.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE)
            val api = RetrofitClient.obtenerApi(context)
            var nombreSede = nombreAuditorio.trim()
            var verticesDetectados: List<Pair<Double, Double>> = emptyList()
            var toleranciaDetectada = TOLERANCIA_PARED_DEFECTO_METROS

            // 1. Consultar primero el catálogo de auditorios en tiempo real (/catalogos/facultad)
            try {
                val cat = api.obtenerCatalogosFacultad()
                val aud = cat.auditorios?.find { a ->
                    val n = a.nombre.trim().lowercase()
                    val b = nombreSede.lowercase()
                    n.isNotBlank() && (n == b || b.contains(n) || n.contains(b))
                }
                if (aud != null) {
                    val m = aud.malla.orEmpty().mapNotNull { v ->
                        if (v.lat != 0.0 || v.lon != 0.0) v.lat to v.lon else null
                    }
                    if (m.size >= 3) {
                        verticesDetectados = m
                        toleranciaDetectada = (aud.toleranciaMetros ?: TOLERANCIA_PARED_DEFECTO_METROS).coerceIn(2f, 80f)
                    }
                }
            } catch (_: Exception) {}

            // 2. Consultar también el evento en /eventos/feed por si el Admin actualizó CCMETA.malla en tiempo real
            try {
                val lista = api.obtenerEventos()
                val ev = lista.find { it.id == eventoId }
                if (ev != null) {
                    if (ev.lugar.isNotBlank()) nombreSede = ev.lugar.trim()
                    if (ev.mallaPoligonoEfectiva.size >= 3) {
                        verticesDetectados = ev.mallaPoligonoEfectiva
                        toleranciaDetectada = ev.toleranciaMallaEfectiva
                    }
                }
            } catch (_: Exception) {}

            // 3. Persistir en SharedPreferences o recuperar última malla conocida si hubo corte momentáneo de red
            val claveCache = "malla_cache_${nombreSede.lowercase()}"
            if (verticesDetectados.size >= 3) {
                try {
                    val arr = JSONArray()
                    for ((lat, lon) in verticesDetectados) {
                        arr.put(JSONArray().put(lat).put(lon))
                    }
                    val obj = JSONObject()
                        .put("malla", arr)
                        .put("tol", toleranciaDetectada.toDouble())
                    prefs.edit().putString(claveCache, obj.toString()).apply()
                } catch (_: Exception) {}
                return verticesDetectados to toleranciaDetectada
            }

            val guardadoRaw = prefs.getString(claveCache, null)
            if (!guardadoRaw.isNullOrBlank()) {
                try {
                    val obj = JSONObject(guardadoRaw)
                    val arr = obj.optJSONArray("malla")
                    val pts = mutableListOf<Pair<Double, Double>>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val par = arr.optJSONArray(i) ?: continue
                            if (par.length() >= 2) {
                                pts.add(par.optDouble(0) to par.optDouble(1))
                            }
                        }
                    }
                    if (pts.size >= 3) {
                        val tol = obj.optDouble("tol", 12.0).toFloat().coerceIn(2f, 80f)
                        return pts to tol
                    }
                } catch (_: Exception) {}
            }

            // 4. Respaldo con la malla oficial de 4 esquinas de UNACH Campus I / ETDA
            val claveDefecto = MALLAS_AUDITORIOS_DEFECTO.keys.find { k ->
                nombreSede.lowercase().contains(k) || k.contains(nombreSede.lowercase())
            }
            val mallaDefecto = if (claveDefecto != null) {
                MALLAS_AUDITORIOS_DEFECTO.getValue(claveDefecto)
            } else {
                MALLAS_AUDITORIOS_DEFECTO.getValue("auditorio principal etda")
            }
            return mallaDefecto to TOLERANCIA_PARED_DEFECTO_METROS
        }

        /**
         * Algoritmo Ray-Casting + Distancia perpendicular exacta en metros a los 4 lados del polígono:
         * - `dentroPoligonoExacto`: true si `(lat, lon)` cae geométricamente dentro de las 4 esquinas.
         * - `distanciaAlBordeMetros`: distancia mínima en metros desde el dispositivo hasta el borde más cercano de la malla.
         * - `dentroDeArea`: true si está dentro del polígono O dentro del margen de tolerancia en paredes (`toleranciaMetros`, 12 m por defecto).
         */
        fun verificarPuntoEnMallaPoligonal(
            lat: Double,
            lon: Double,
            vertices: List<Pair<Double, Double>>,
            toleranciaMetros: Float = TOLERANCIA_PARED_DEFECTO_METROS
        ): ResultadoVerificacionMalla {
            if (vertices.size < 3) {
                return ResultadoVerificacionMalla(false, false, 9999f, toleranciaMetros)
            }

            // 1. Ray-Casting (Jordan Curve Theorem)
            var dentroExacto = false
            var j = vertices.lastIndex
            for (i in vertices.indices) {
                val yi = vertices[i].first
                val xi = vertices[i].second
                val yj = vertices[j].first
                val xj = vertices[j].second

                val intersecta = ((yi > lat) != (yj > lat)) &&
                        (lon < (xj - xi) * (lat - yi) / ((yj - yi).takeIf { it != 0.0 } ?: 1e-12) + xi)
                if (intersecta) {
                    dentroExacto = !dentroExacto
                }
                j = i
            }

            // 2. Distancia mínima en metros a cualquiera de los segmentos del polígono (planos tangentes locales en metros)
            val distanciaBorde = distanciaMinimaABordePoligonoMetros(lat, lon, vertices)
            val dentroConTolerancia = dentroExacto || (distanciaBorde <= toleranciaMetros)

            return ResultadoVerificacionMalla(
                dentroDeArea = dentroConTolerancia,
                dentroPoligonoExacto = dentroExacto,
                distanciaAlBordeMetros = if (dentroExacto) 0f else distanciaBorde,
                toleranciaMetros = toleranciaMetros
            )
        }

        private fun distanciaMinimaABordePoligonoMetros(
            latP: Double,
            lonP: Double,
            vertices: List<Pair<Double, Double>>
        ): Float {
            val metrosPorGradoLat = 111_320.0
            val metrosPorGradoLon = 111_320.0 * cos(Math.toRadians(latP)).coerceAtLeast(0.0001)

            var minDist = Double.MAX_VALUE
            for (i in vertices.indices) {
                val (latA, lonA) = vertices[i]
                val (latB, lonB) = vertices[(i + 1) % vertices.size]

                // Coordenadas en metros relativas al punto P (0, 0)
                val ax = (lonA - lonP) * metrosPorGradoLon
                val ay = (latA - latP) * metrosPorGradoLat
                val bx = (lonB - lonP) * metrosPorGradoLon
                val by = (latB - latP) * metrosPorGradoLat

                val dx = bx - ax
                val dy = by - ay
                val lenSq = dx * dx + dy * dy
                val t = if (lenSq > 1e-9) {
                    (-(ax * dx + ay * dy) / lenSq).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                val projX = ax + t * dx
                val projY = ay + t * dy
                val dist = hypot(projX, projY)
                if (dist < minDist) {
                    minDist = dist
                }
            }
            return minDist.toFloat()
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
                    cuerpo = "Se confirmó tu QR y ubicación dentro de la malla del recinto para \"$titulo\".",
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