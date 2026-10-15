package com.example.campusconnect

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.campusconnect.model.Verificar2FARequest
import com.example.campusconnect.network.RetrofitClient
import com.example.campusconnect.ui.theme.CampusConnectTheme
import kotlinx.coroutines.launch

class RenovarSesionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val correoGuardado = TokenManager.obtenerEmail(this)
        if (correoGuardado.isBlank()) {
            startActivity(Intent(this, ComposeLoginActivity::class.java))
            finish()
            return
        }

        setContent {
            CampusConnectTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RenovarSesionScreen(
                        correoGuardado = correoGuardado,
                        onNavigateToAlumno = {
                            startActivity(Intent(this@RenovarSesionActivity, EventosDemoActivity::class.java))
                            finish()
                        },
                        onIrALoginCompleto = {
                            TokenManager.cerrarSesion(this@RenovarSesionActivity)
                            startActivity(Intent(this@RenovarSesionActivity, ComposeLoginActivity::class.java))
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RenovarSesionScreen(
    correoGuardado: String,
    onNavigateToAlumno: () -> Unit,
    onIrALoginCompleto: () -> Unit
) {
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F7FB)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFF122652))
        ) {
            Text(
                text = "Seguridad Campus Connect UNACH",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset(y = (-40).dp),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Renovación de sesión (${TokenManager.obtenerDescripcionVigencia()})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF122652))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "El periodo de vigencia de tu verificación en 2 pasos ha concluido. Confirma tu cuenta o inicia sesión nuevamente:",
                    color = Color.Gray,
                    fontSize = 13.sp,
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

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onIrALoginCompleto() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00)),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Volver a verificar e iniciar sesión", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
