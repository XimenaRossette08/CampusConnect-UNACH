package com.example.campusconnect

import android.Manifest
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
import com.example.campusconnect.ui.MainNavigationScreen
import com.example.campusconnect.ui.theme.CampusConnectTheme

class EventosDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si no hay sesión activa o válida, ir de inmediato a ComposeLoginActivity
        if (!verificarSesionActivaORedirigir()) {
            return
        }

        // =====================================================================
        // PEDIR PERMISO DE NOTIFICACIONES (SOLO SI LA SESIÓN ES VÁLIDA)
        // =====================================================================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    Log.d("FCM", "Permiso de notificaciones concedido por el alumno")
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
        verificarSesionActivaORedirigir()
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