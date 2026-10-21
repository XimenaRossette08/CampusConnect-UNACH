package com.example.campusconnect

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = TokenManager.obtenerToken(this)
        val email = TokenManager.obtenerEmail(this)

        val intent = when {
            // 1. Si no hay token válido o no hay correo guardado -> Ir directo a Iniciar Sesión
            token.isNullOrBlank() || email.isBlank() -> {
                Intent(this, ComposeLoginActivity::class.java)
            }
            // 2. Si ya pasó el límite de tiempo configurado (30 días o tiempo de prueba) -> Renovar Sesión
            TokenManager.requiereRenovacion(this) -> {
                Intent(this, RenovarSesionActivity::class.java)
            }
            // 3. Sesión válida y dentro de los 30 días -> Entrar a la Cartelera de Eventos
            else -> {
                Intent(this, EventosDemoActivity::class.java)
            }
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
