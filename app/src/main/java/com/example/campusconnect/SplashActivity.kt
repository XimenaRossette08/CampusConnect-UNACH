package com.example.campusconnect

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lógica del Director de Tráfico:
        val intent = if (TokenManager.obtenerToken(this) == null) {
            // Luz Roja: Nunca ha iniciado sesión o la cerró. Va al Login completo.
            Intent(this, ComposeLoginActivity::class.java)
        } else if (TokenManager.requiereRenovacion(this)) {
            // Luz Amarilla: Ya pasaron 30 días. Va a la pantalla nueva de Renovar.
            Intent(this, RenovarSesionActivity::class.java)
        } else {
            // Luz Verde: Todo está en orden. Pasa directo a la app.
            Intent(this, EventosDemoActivity::class.java)
        }

        // Arrancamos la pantalla decidida
        startActivity(intent)

        // Cerramos el director de tráfico para que el usuario no pueda "regresar" a él con el botón de atrás
        finish()
    }
}