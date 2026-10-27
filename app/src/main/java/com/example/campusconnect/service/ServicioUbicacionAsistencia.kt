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
import com.google.android.gms.location.*

class ServicioUbicacionAsistencia : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var eventoId: String? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val lat = location.latitude
                    val lon = location.longitude

                    // Aquí enviaremos la ubicación a FastAPI usando Retrofit
                    Log.d("AsistenciaGPS", "Ping enviado a Yasir: Lat: $lat, Lon: $lon, Evento: $eventoId")

                    // TODO: Llamar al endpoint de Yasir -> api.enviarPingAsistencia(eventoId, lat, lon)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        eventoId = intent?.getStringExtra("EVENTO_ID")

        crearCanalNotificacion()

        val notificacion = NotificationCompat.Builder(this, "ASISTENCIA_CHANNEL")
            .setContentTitle("Verificando asistencia")
            .setContentText("Campus Connect está validando tu ubicación en el evento.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        // Inicia el servicio protegido en primer plano
        startForeground(1, notificacion)
        iniciarRastreoGPS()

        return START_STICKY
    }

    private fun iniciarRastreoGPS() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 600000) // Cada 10 minutos (600,000 ms)
            .setMinUpdateIntervalMillis(300000) // Mínimo 5 minutos
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
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // No necesitamos enlazar vistas directamente
    }

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
}