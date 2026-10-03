package com.example.campusconnect

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// Importa tu tema (asegúrate de que el nombre coincida con tu proyecto)
import com.example.campusconnect.ui.theme.CampusConnectTheme

class RenovarSesionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Aquí simulamos que leemos el correo del TokenManager
        val correoGuardado = "alumno@unach.mx"

        setContent {
            CampusConnectTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RenovarSesionScreen(
                        correoGuardado = correoGuardado,
                        onNavigateToAlumno = {
                            startActivity(Intent(this@RenovarSesionActivity, EventosDemoActivity::class.java))
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RenovarSesionScreen(correoGuardado: String, onNavigateToAlumno: () -> Unit) {
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F7FA)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Encabezado institucional
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFF0C2340))
        ) {
            Text(
                text = "Seguridad CampusConnect",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Tarjeta principal
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset(y = (-40).dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Verificación de cuenta", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Por tu seguridad, verificamos las sesiones cada 30 días. ¿Sigues utilizando este correo?",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = correoGuardado,
                    onValueChange = { },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        // Aquí llamaremos a Retrofit para que Yasir mande el correo real
                        showOtpDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Sí, enviar código de acceso")
                }
            }
        }

        // Ventana emergente del 2FA (Reutilizada de tu Login)
        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Verificación en 2 pasos") },
                text = {
                    Column {
                        Text("Hemos enviado un código a $correoGuardado. Ingresa el código para continuar.")
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { if (it.length <= 6) otpCode = it.trim() },
                            label = { Text("Código de 6 dígitos") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (otpCode.length == 6) {
                                // Aquí enviaremos el request a la ruta /auth/verificar-2fa de Yasir
                                showOtpDialog = false
                                onNavigateToAlumno()
                            } else {
                                Toast.makeText(context, "El código debe tener 6 dígitos", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Verificar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOtpDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}