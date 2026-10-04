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
import com.example.campusconnect.model.LoginRequest
import com.example.campusconnect.model.Verificar2FARequest
import com.example.campusconnect.network.RetrofitClient
import com.example.campusconnect.ui.theme.CampusConnectTheme
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

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

    var showOtpDialog by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }

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
                text = "\u201cLa búsqueda del conocimiento transforma la sociedad\u201d",
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val correoLimpio = correo.trim()
                        val passwordLimpia = password.trim()

                        if (correoLimpio.isBlank() || passwordLimpia.isBlank()) {
                            Toast.makeText(context, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        scope.launch {
                            try {
                                // Se usan 'correo' y 'contrasena' según los parámetros de tu LoginRequest
                                val respuesta = RetrofitClient.obtenerApi(context)
                                    .loginUser(LoginRequest(correo = correoLimpio, contrasena = passwordLimpia))

                                if (respuesta.isSuccessful) {
                                    showOtpDialog = true
                                } else {
                                    Toast.makeText(context, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: HttpException) {
                                val mensaje = if (e.code() == 401) {
                                    "Correo o contraseña incorrectos"
                                } else {
                                    "Error en el servidor (${e.code()})"
                                }
                                Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
                            } catch (e: IOException) {
                                Toast.makeText(context, "Error de red. Verifica tu conexión.", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoading = false
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

        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = { /* Vacío para forzar uso de botones */ },
                title = { Text(text = "Verificación en 2 pasos") },
                text = {
                    Column {
                        Text("Hemos enviado un código de 6 dígitos a tu correo. Ingresa el código para continuar.")
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { if (it.length <= 6) otpCode = it },
                            label = { Text("Código de seguridad") },
                            singleLine = true,
                            enabled = !isVerifyingOtp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val codigoLimpio = otpCode.trim()
                            if (codigoLimpio.length != 6) {
                                Toast.makeText(context, "El código debe tener 6 dígitos", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isVerifyingOtp = true
                            scope.launch {
                                try {
                                    val respuesta2FA = RetrofitClient.obtenerApi(context)
                                        .verificar2FA(
                                            Verificar2FARequest(
                                                email = correo.trim(),
                                                codigo = codigoLimpio
                                            )
                                        )

                                    if (respuesta2FA.isSuccessful && respuesta2FA.body() != null) {
                                        val tokenData = respuesta2FA.body()!!

                                        TokenManager.guardarSesion(
                                            context = context,
                                            token = tokenData.accessToken,
                                            refreshToken = tokenData.refreshToken,
                                            rol = tokenData.role
                                        )

                                        showOtpDialog = false
                                        onNavigateToAlumno()
                                    } else {
                                        Toast.makeText(context, "Código incorrecto o expirado", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al verificar el código", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isVerifyingOtp = false
                                }
                            }
                        },
                        enabled = !isVerifyingOtp
                    ) {
                        if (isVerifyingOtp) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Verificar")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showOtpDialog = false
                            otpCode = ""
                        },
                        enabled = !isVerifyingOtp
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}