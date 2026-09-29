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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusconnect.ui.theme.CampusConnectTheme
import kotlinx.coroutines.launch
import com.example.campusconnect.model.LoginRequest
import com.example.campusconnect.network.RetrofitClient

class ComposeLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampusConnectTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoginScreen(
                        onNavigateToAlumno = {
                            startActivity(Intent(this@ComposeLoginActivity, EventosDemoActivity::class.java))
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onNavigateToAlumno: () -> Unit) {
    val scope = rememberCoroutineScope()
    var correo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Variables para el 2FA
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F7FA))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(Color(0xFF0C2340))
        ) {
            Text(
                text = "“La búsqueda del conocimiento transforma la sociedad”",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp).align(Alignment.Center)
            )
        }

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
                Text("Bienvenido a la UNACH", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Accede a tu cuenta de estudiante", color = Color.Gray, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    label = { Text("Correo institucional") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        // Atajo temporal: Mostramos el cuadro del código de 6 dígitos
                        // saltándonos la conexión al servidor de Yasir por ahora.
                        showOtpDialog = true

                        /* NOTA: Tu código original de Retrofit está seguro,
                           lo reintegraremos cuando levantemos el servidor local. */
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Ingresar")
                    }
                }
            }
        }

        // AQUÍ ESTÁ EL CUADRO EMERGENTE DEL 2FA (OTP)
        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = { /* Vacío para obligar a usar los botones */ },
                title = { Text(text = "Verificación en 2 pasos") },
                text = {
                    Column {
                        Text("Hemos enviado un código de 6 dígitos a tu correo institucional. Ingresa el código para continuar.")
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { if (it.length <= 6) otpCode = it },
                            label = { Text("Código de seguridad") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (otpCode.length == 6) {
                                showOtpDialog = false
                                // ¡Pasa a la pantalla principal de Eventos!
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