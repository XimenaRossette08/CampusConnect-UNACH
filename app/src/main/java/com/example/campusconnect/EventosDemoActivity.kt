package com.example.campusconnect

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.network.RetrofitClient
import com.example.campusconnect.ui.MainNavigationScreen
import com.example.campusconnect.ui.theme.CampusConnectTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EventosDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si no hay sesión activa o válida, ir de inmediato a ComposeLoginActivity
        if (!verificarSesionActivaORedirigir()) {
            return
        }

        // Crear canal de notificaciones de alta prioridad desde el inicio
        crearCanalNotificaciones()

        // Sincronizar el token FCM del teléfono con el backend en Supabase
        sincronizarTokenFcmConBackend()

        // =====================================================================
        // PEDIR PERMISO DE NOTIFICACIONES (ANDROID 13+)
        // =====================================================================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    Log.d("FCM", "Permiso de notificaciones concedido por el alumno")
                    sincronizarTokenFcmConBackend()
                } else {
                    Log.d("FCM", "El alumno rechazó las notificaciones")
                }
            }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // =====================================================================

        enableEdgeToEdge()
        setContent {
            CampusConnectTheme {
                MainNavigationScreen(validarSesion = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (verificarSesionActivaORedirigir()) {
            sincronizarTokenFcmConBackend()
        }
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = MyFirebaseMessagingService.CHANNEL_ID
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "Notificaciones Académicas UNACH",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos de nuevos eventos y recordatorios de cierre"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sincronizarTokenFcmConBackend() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "No se pudo obtener el token de Firebase", task.exception)
                return@addOnCompleteListener
            }
            val fcmToken = task.result
            if (!fcmToken.isNullOrBlank()) {
                Log.d("FCM_TOKEN", "Enviando token FCM al backend: ${fcmToken.take(16)}...")
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        RetrofitClient.obtenerApi(applicationContext)
                            .registrarFcmToken(mapOf("fcm_token" to fcmToken))
                    } catch (e: Exception) {
                        Log.e("FCM", "Error al registrar token FCM en servidor: ${e.message}")
                    }
                }
            }
        }
    }

    private fun verificarSesionActivaORedirigir(): Boolean {
        val token = TokenManager.obtenerToken(this)
        if (token.isNullOrBlank()) {
            val intent = Intent(this, ComposeLoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return false
        }
        if (TokenManager.requiereRenovacion(this)) {
            val intent = Intent(this, RenovarSesionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return false
        }
        return true
    }
}