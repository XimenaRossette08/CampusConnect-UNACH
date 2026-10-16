package com.example.campusconnect

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Esto captura el Token único del celular. Yasir lo necesitará después para saber a quién enviarle el mensaje.
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Nuevo token generado: $token")
    }

    // Esto se ejecuta cuando llega un mensaje desde FastAPI
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val titulo = message.notification?.title ?: "Nuevo Evento ETDA"
        val cuerpo = message.notification?.body ?: "Tienes una nueva notificación"

        mostrarNotificacion(titulo, cuerpo)
    }

    private fun mostrarNotificacion(titulo: String, cuerpo: String) {
        val channelId = "campus_connect_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // En Android 8.0 o superior se requiere crear un "Canal" de notificaciones
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Notificaciones Académicas", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // Al tocar la notificación, abrirá el SplashActivity
        val intent = Intent(this, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Tu ícono de la app
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}