package com.example.campusconnect

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.campusconnect.ui.MainNavigationScreen
import com.example.campusconnect.ui.theme.CampusConnectTheme

class EventosDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si no hay sesión activa o válida, ir de inmediato a ComposeLoginActivity
        if (!verificarSesionActivaORedirigir()) {
            return
        }

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
