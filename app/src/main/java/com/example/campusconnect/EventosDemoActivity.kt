package com.example.campusconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.campusconnect.ui.EventosDemoScreen
import com.example.campusconnect.ui.theme.CampusConnectTheme

/**
 * Actividad solo para presentar la maqueta de Eventos, sin depender
 * del flujo de login. No la borres del manifest hasta terminar la demo.
 */
class EventosDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampusConnectTheme {
                EventosDemoScreen()
            }
        }
    }
}