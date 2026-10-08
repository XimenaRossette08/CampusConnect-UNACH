package com.example.campusconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.campusconnect.ui.MainNavigationScreen
import com.example.campusconnect.ui.theme.CampusConnectTheme

/**
 * Actividad principal que carga la navegación con la nueva arquitectura modular.
 */
class EventosDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampusConnectTheme {
                // Aquí llamamos al nuevo cascarón principal que creaste
                MainNavigationScreen()
            }
        }
    }
}