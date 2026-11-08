package com.example.campusconnect

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
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
        // Remediación Auditoría A-4: Nunca imprimir el token FCM en Logcat.

        val jwt = TokenManager.obtenerToken(applicationContext)
        if (!jwt.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RetrofitClient.obtenerApi(applicationContext)
                        .registrarFcmToken(mapOf("fcm_token" to token))
                } catch (_: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w("FCM_SYNC", "No se pudo sincronizar el registro de notificaciones")
                    }
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val tipo = message.data["tipo"] ?: ""
        val eventoId = message.data["evento_id"] ?: ""
        val firmaFin = message.data["firma_fin"] ?: ""

        if (eventoId.isNotBlank() && tipo.isNotBlank()) {
            val prefs = applicationContext.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE)
            val clave = if (firmaFin.isNotBlank()) {
                "notificado_${tipo}_${eventoId}_$firmaFin"
            } else {
                "notificado_${tipo}_$eventoId"
            }
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

        val notifId = if (eventoId.isNotBlank() && tipo.isNotBlank()) {
            ("${tipo}_$eventoId").hashCode()
        } else {
            (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        }

        mostrarNotificacion(applicationContext, titulo, cuerpo, notifId)
    }

    companion object {
        const val CHANNEL_ID = "campus_connect_alertas_v2"

        private fun obtenerBitmapIconoApp(context: Context): Bitmap? {
            return try {
                val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
                if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    return drawable.bitmap
                }
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            } catch (_: Exception) {
                null
            }
        }

        private fun obtenerSmallIconResId(context: Context): Int {
            val idForeground = context.resources.getIdentifier(
                "ic_launcher_foreground",
                "drawable",
                context.packageName
            )
            return if (idForeground != 0) idForeground else android.R.drawable.ic_dialog_info
        }

        fun mostrarNotificacion(
            context: Context,
            titulo: String,
            cuerpo: String,
            notifId: Int = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        ) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Alertas de Eventos UNACH",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificaciones cuando aparece un evento, faltan 30 minutos o finaliza"
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

            val smallIconRes = obtenerSmallIconResId(context)
            val appLogoBitmap = obtenerBitmapIconoApp(context)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(smallIconRes)
                .setColor(0xFF122652.toInt())
                .setContentTitle(titulo)
                .setContentText(cuerpo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)

            if (appLogoBitmap != null) {
                builder.setLargeIcon(appLogoBitmap)
            }

            try {
                notificationManager.notify(notifId, builder.build())
            } catch (_: Exception) {
                try {
                    builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                    notificationManager.notify(notifId, builder.build())
                } catch (_: Exception) {
                }
            }
        }
    }
}