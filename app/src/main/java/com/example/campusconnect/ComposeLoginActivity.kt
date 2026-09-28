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
                            startActivity(Intent(this@ComposeLoginActivity, AlumnoFeedActivity::class.java))
                            // finish() evita que el usuario regrese al login si presiona el botón de "Atrás" en su celular
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

    // Novedad: Controla si se muestra la ruedita de carga
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
                    enabled = !isLoading // Bloquea el campo mientras carga
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
                        // Validación rápida antes de ir a internet
                        if (correo.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Llena todos los campos", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true // Encendemos la animación

                        scope.launch {
                            try {
                                val peticion = LoginRequest(correo, password)
                                val respuesta = RetrofitClient.apiService.loginUser(peticion)

                                if (respuesta.isSuccessful) {
                                    val token = respuesta.body()?.token
                                    val rol = respuesta.body()?.rol

                                    if (rol == "alumno" && token != null) {
                                        // 1. Guardamos el token encriptado
                                        TokenManager.guardarToken(context, token)
                                        // 2. Mensaje de éxito
                                        Toast.makeText(context, "¡Sesión iniciada!", Toast.LENGTH_SHORT).show()
                                        // 3. Cambiamos de pantalla
                                        onNavigateToAlumno()
                                    } else {
                                        Toast.makeText(context, "Cuenta de administrador. Accede desde el portal web.", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error de red. Intenta más tarde.", Toast.LENGTH_LONG).show()
                            } finally {
                                isLoading = false // Apagamos la animación sin importar qué pase
                            }
                        }
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
    }
}