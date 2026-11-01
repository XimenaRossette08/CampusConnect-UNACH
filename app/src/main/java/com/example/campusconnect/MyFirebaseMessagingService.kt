package com.example.campusconnect

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.campusconnect.network.RetrofitClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Nuevo token generado: $token")

        val jwt = TokenManager.obtenerToken(applicationContext)
        if (!jwt.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RetrofitClient.obtenerApi(applicationContext)
                        .registrarFcmToken(mapOf("fcm_token" to token))
                } catch (e: Exception) {
                    Log.e("FCM_TOKEN", "No se pudo subir el nuevo token al backend: ${e.message}")
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val tipo = message.data["tipo"] ?: ""
        val eventoId = message.data["evento_id"] ?: ""

        // Evitar duplicados si EventosScreen ya mostró la notificación localmente
        if (eventoId.isNotBlank() && tipo.isNotBlank()) {
            val prefs = applicationContext.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE)
            val clave = "notificado_${tipo}_$eventoId"
            if (prefs.getBoolean(clave, false)) {
                return
            }
            prefs.edit().putBoolean(clave, true).apply()
        }

        val titulo = message.notification?.title
            ?: message.data["title"]
            ?: message.data["titulo"]
            ?: "Campus Connect UNACH"

        val cuerpo = message.notification?.body
            ?: message.data["body"]
            ?: message.data["cuerpo"]
            ?: "Tienes una nueva notificación académica"

        mostrarNotificacion(applicationContext, titulo, cuerpo)
    }

    companion object {
        const val CHANNEL_ID = "campus_connect_alertas_v2"

        fun mostrarNotificacion(context: Context, titulo: String, cuerpo: String, notifId: Int = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Alertas de Eventos UNACH",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificaciones cuando aparece un evento o faltan 30 minutos para finalizar"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, SplashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // IMPORTANTE: En Android 8+ (targetSdk 37) NO se debe usar R.mipmap.ic_launcher_round (adaptive icon)
            // en setSmallIcon porque Android bloquea la notificación. Usamos un ícono vectorial válido.
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(titulo)
                .setContentText(cuerpo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)

            try {
                notificationManager.notify(notifId, builder.build())
            } catch (e: SecurityException) {
                Log.e("FCM", "Falta permiso POST_NOTIFICATIONS para mostrar alerta: ${e.message}")
            } catch (e: Exception) {
                Log.e("FCM", "Error al mostrar notificación: ${e.message}")
            }
        }
    }
}