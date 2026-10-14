package com.example.campusconnect

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

// Paleta de Colores Institucional ETDA - UNACH
val NavyBlue = Color(0xFF122652)
val BrightBlue = Color(0xFF0093FF)
val VibrantOrange = Color(0xFFF57C00)
val LightBackground = Color(0xFFF4F7FB)

class ComposeLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampusConnectTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = LightBackground) {
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
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .verticalScroll(scrollState)
    ) {
        // ENCABEZADO SUPERIOR CON IDENTIDAD ETDA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(NavyBlue, Color(0xFF183672), BrightBlue)
                    ),
                    shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
                )
        ) {
            // Círculos decorativos tecnológicos en el fondo
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .offset(x = (-50).dp, y = (-40).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            )
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 40.dp, y = 20.dp)
                    .clip(CircleShape)
                    .background(VibrantOrange.copy(alpha = 0.15f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 48.dp, bottom = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Ícono circular destacado
                Surface(
                    shape = CircleShape,
                    color = VibrantOrange,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = "Logo UNACH",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Badge de la Escuela de Tecnologías Digitales Aplicadas
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, BrightBlue.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "ESCUELA DE TECNOLOGÍAS DIGITALES APLICADAS",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Campus Connect UNACH",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Lema oficial de la UNACH actualizado
                Text(
                    text = "\u201cPor la conciencia de la necesidad de servir\u201d",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // TARJETA DEL FORMULARIO DE ACCESO
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset(y = (-48).dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Bienvenido Estudiante",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ingresa con tu cuenta institucional",
                    color = Color.Gray,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    label = { Text("Correo institucional") },
                    placeholder = { Text("nombre.apellido##@unach.mx", fontSize = 13.sp, color = Color.LightGray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Correo",
                            tint = BrightBlue
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrightBlue,
                        unfocusedBorderColor = Color(0xFFD0D7DE),
                        focusedLabelColor = NavyBlue,
                        cursorColor = VibrantOrange
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Contraseña",
                            tint = BrightBlue
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrightBlue,
                        unfocusedBorderColor = Color(0xFFD0D7DE),
                        focusedLabelColor = NavyBlue,
                        cursorColor = VibrantOrange
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(28.dp))

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibrantOrange,
                        disabledContainerColor = VibrantOrange.copy(alpha = 0.6f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "INGRESAR",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Pie de página institucional
        Text(
            text = "Universidad Autónoma de Chiapas • ETDA",
            color = NavyBlue.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-20).dp)
                .padding(bottom = 16.dp)
        )

        // MODAL DE VERIFICACIÓN EN 2 PASOS (2FA)
        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = { /* Forzar uso de botones */ },
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.White,
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrightBlue.copy(alpha = 0.12f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "2FA",
                                tint = BrightBlue,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Verificación en 2 pasos",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = NavyBlue,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Hemos enviado un código de 6 dígitos a tu correo institucional. Ingrésalo para continuar:",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) otpCode = it },
                            label = { Text("Código de 6 dígitos") },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                letterSpacing = 6.sp,
                                color = NavyBlue
                            ),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VibrantOrange,
                                unfocusedBorderColor = BrightBlue,
                                focusedLabelColor = VibrantOrange
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
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
                                            email = correo.trim(),
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
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantOrange),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isVerifyingOtp
                    ) {
                        if (isVerifyingOtp) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Verificar", fontWeight = FontWeight.Bold, color = Color.White)
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
                        Text("Cancelar", color = NavyBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    }
}