package com.example.campusconnect.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusconnect.ComposeLoginActivity
import com.example.campusconnect.TokenManager

private data class PerfilDemo(val nombre: String, val correo: String, val rol: String, val facultad: String, val carrera: String)

private val perfilDeEjemplo = PerfilDemo(
    nombre = "Estudiante UNACH",
    correo = "estudiante@unach.mx",
    rol = "Usuario Regular",
    facultad = "Facultad de Ingeniería",
    carrera = "Ingeniería en Sistemas Computacionales"
)

@Composable
fun PerfilScreen(puntos: Int) {
    // Necesitamos el contexto para poder navegar y borrar los datos guardados
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(96.dp).clip(CircleShape).background(ColorUnach.AzulProfundo), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(perfilDeEjemplo.nombre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Text(perfilDeEjemplo.correo, style = MaterialTheme.typography.bodySmall, color = ColorUnach.TextoSecundario)
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(ColorUnach.Dorado.copy(alpha = 0.15f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(perfilDeEjemplo.rol, color = ColorUnach.Dorado, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0x14000000)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                FilaDatoPerfil(etiqueta = "Facultad", valor = perfilDeEjemplo.facultad)
                Spacer(modifier = Modifier.height(10.dp))
                FilaDatoPerfil(etiqueta = "Carrera", valor = perfilDeEjemplo.carrera)
                Spacer(modifier = Modifier.height(10.dp))
                FilaDatoPerfil(etiqueta = "Puntos Rewards", valor = "$puntos pts")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = {
                // 1. Borramos el token guardado
                TokenManager.cerrarSesion(context)

                // 2. Navegamos de regreso a la pantalla de Login
                val intent = Intent(context, ComposeLoginActivity::class.java)
                // Esto borra el historial para que no pueda regresar al presionar "Atrás"
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cerrar sesión")
        }
    }
}

@Composable
private fun FilaDatoPerfil(etiqueta: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, color = ColorUnach.TextoSecundario, fontSize = 13.sp)
        Text(valor, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}