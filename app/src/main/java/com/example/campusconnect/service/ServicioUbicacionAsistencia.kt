package com.example.campusconnect.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.campusconnect.MyFirebaseMessagingService
import com.example.campusconnect.network.RetrofitClient
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

class ServicioUbicacionAsistencia : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var eventoId: String? = null
    private var eventoTitulo: String = "Evento Académico"
    private var eventoCategoria: String = "Académicos"
    private var contadorPingsLocal: Int = 0

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val lat = location.latitude
                    val lon = location.longitude
                    val idActual = eventoId ?: continue

                    contadorPingsLocal += 1
                    val completarSegundoPing = contadorPingsLocal >= 2

                    Log.d(
                        "AsistenciaGPS",
                        "Ping #$contadorPingsLocal enviado -> Lat: $lat, Lon: $lon, Evento: $idActual, Categoría: $eventoCategoria"
                    )

                    serviceScope.launch {
                        var acreditadoRemoto = false
                        var categoriaAcreditada = normalizarCategoriaRecompensa(eventoCategoria)

                        try {
                            val respuesta = RetrofitClient.obtenerApi(applicationContext)
                                .enviarPingGpsEvento(
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
                        } catch (e: Exception) {
                            Log.w("AsistenciaGPS", "Aviso al sincronizar ping GPS con servidor: ${e.message}")
                        }

                        // Al cumplirse el segundo ping de permanencia en el recinto (o confirmación del servidor),
                        // se completa la Validación 2/2 y se acredita +1 en la categoría correspondiente
                        if (acreditadoRemoto || completarSegundoPing) {
                            acreditarValidacionGps2De2(
                                context = applicationContext,
                                eventoId = idActual,
                                titulo = eventoTitulo,
                                categoria = categoriaAcreditada
                            )
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        eventoId = intent?.getStringExtra("EVENTO_ID")
        eventoTitulo = intent?.getStringExtra("EVENTO_TITULO") ?: "Evento Académico UNACH"
        eventoCategoria = intent?.getStringExtra("EVENTO_CATEGORIA") ?: "Académicos"

        crearCanalNotificacion()

        val notificacion = NotificationCompat.Builder(this, "ASISTENCIA_CHANNEL")
            .setContentTitle("Validación 1/2 lista • Verificando permanencia GPS")
            .setContentText("Mantén tu ubicación activa en '$eventoTitulo' para acreditar +1 en $eventoCategoria.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        startForeground(1, notificacion)
        iniciarRastreoGPS()

        return START_STICKY
    }

    private fun iniciarRastreoGPS() {
        // Verifica el primer ping al iniciar y el segundo ping de permanencia a los 45 segundos
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 45_000L)
            .setMinUpdateIntervalMillis(30_000L)
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
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

            // Marcar evento con Validación 2/2 completada
            val setGps2 = (prefsNotif.getStringSet("eventos_gps_2de2_set", emptySet()) ?: emptySet()).toMutableSet()
            setGps2.add(eventoId)
            prefsNotif.edit().putStringSet("eventos_gps_2de2_set", setGps2).apply()

            // Acreditar +1 en la categoría únicamente una vez por eventoId
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
                    cuerpo = "Se confirmó tu QR y permanencia GPS en \"$titulo\". Revisa tu progreso en la pestaña de Insignias.",
                    notifId = ("recompensa_$eventoId").hashCode()
                )
            }
        }
    }
}