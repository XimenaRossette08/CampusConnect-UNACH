package com.example.campusconnect

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campusconnect.model.RegistroRequest
import com.example.campusconnect.model.Verificar2FARequest
import com.example.campusconnect.network.AuthRepository
import com.example.campusconnect.network.RetrofitClient
import com.example.campusconnect.ui.LoginState
import com.example.campusconnect.ui.LoginViewModel
import com.example.campusconnect.ui.theme.CampusConnectTheme
import kotlinx.coroutines.launch

val NavyBlue = Color(0xFF122652)
val BrightBlue = Color(0xFF0093FF)
val VibrantOrange = Color(0xFFF57C00)
val LightBackground = Color(0xFFF4F7FB)

class ComposeLoginActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (TokenManager.haySesionActiva(this)) {
            startActivity(Intent(this, EventosDemoActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            CampusConnectTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = LightBackground) {
                    val context = LocalContext.current

                    val loginViewModel: LoginViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                val api = RetrofitClient.obtenerApi(context)
                                val repository = AuthRepository(api)
                                return LoginViewModel(repository) as T
                            }
                        }
                    )

                    LoginScreen(
                        viewModel = loginViewModel,
                        onNavigateToAlumno = {
                            val intent = Intent(this@ComposeLoginActivity, EventosDemoActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun dispositivoSoportaHuella(context: Context): Boolean {
    val bm = BiometricManager.from(context)
    return bm.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    ) == BiometricManager.BIOMETRIC_SUCCESS
}

private fun mostrarPromptHuella(
    context: Context,
    titulo: String,
    subtitulo: String,
    alAutenticarConExito: () -> Unit,
    alFallar: (String) -> Unit = {}
) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
        alFallar("No se pudo inicializar el lector.")
        return
    }
    val executor = ContextCompat.getMainExecutor(context)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            alAutenticarConExito()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
            ) {
                alFallar(errString.toString())
            }
        }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(titulo)
        .setSubtitle(subtitulo)
        .setNegativeButtonText("Usar contraseña")
        .build()

    BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
}

@Composable
fun LoginScreen(viewModel: LoginViewModel, onNavigateToAlumno: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var modoRegistro by remember { mutableStateOf(false) }

    var correo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Observamos el estado del ViewModel (Fase 3 completada)
    val estadoLogin by viewModel.loginState.collectAsState()
    val isLoading = estadoLogin is LoginState.Loading

    var showOtpDialog by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }

    var biometriaDisponible by remember { mutableStateOf(TokenManager.tieneBiometriaHabilitada(context)) }
    var mostrarModalOfrecerHuella by remember { mutableStateOf(false) }

    var esAlumnoEgresado by remember { mutableStateOf(false) }
    var regNombre by remember { mutableStateOf("") }
    var regCorreoPrincipal by remember { mutableStateOf("") }
    var regCorreoSecundario by remember { mutableStateOf("") }
    var regTelefonoPrincipal by remember { mutableStateOf("") }
    var regTelefonoSecundario by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }

    fun finalizarAccesoOVincularHuella(emailAutenticado: String, passAutenticada: String) {
        val credsGuardadas = TokenManager.obtenerCredencialesBiometricas(context)
        val yaVinculadoMismoUsuario = credsGuardadas?.first?.equals(emailAutenticado, ignoreCase = true) == true

        if (!yaVinculadoMismoUsuario && passAutenticada.isNotBlank()) {
            mostrarModalOfrecerHuella = true
        } else {
            onNavigateToAlumno()
        }
    }

    // Escuchador actualizado con el estado RegisterSuccess
    LaunchedEffect(estadoLogin) {
        when (val estado = estadoLogin) {
            is LoginState.Requires2FA -> {
                otpCode = ""
                showOtpDialog = true
                viewModel.resetState()
            }
            is LoginState.Success -> {
                TokenManager.guardarSesion(
                    context = context,
                    token = estado.token,
                    refreshToken = estado.refreshToken,
                    email = estado.correo,
                    rol = estado.role,
                    actualizarMarca2FA = false
                )
                showOtpDialog = false
                finalizarAccesoOVincularHuella(estado.correo, estado.pass)
                viewModel.resetState()
            }
            is LoginState.RegisterSuccess -> {
                Toast.makeText(context, "Registro exitoso. Inicia sesión.", Toast.LENGTH_LONG).show()
                modoRegistro = false
                viewModel.resetState()
            }
            is LoginState.Failure -> {
                Toast.makeText(context, estado.error, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    fun iniciarConHuellaDactilar() {
        val creds = TokenManager.obtenerCredencialesBiometricas(context)
        if (creds == null) {
            Toast.makeText(context, "Primero inicia sesión para vincular tu huella.", Toast.LENGTH_SHORT).show()
            return
        }

        mostrarPromptHuella(
            context = context,
            titulo = "Iniciar sesión con Huella Dactilar",
            subtitulo = "Coloca tu huella para ingresar con ${creds.first}",
            alAutenticarConExito = {
                correo = creds.first
                password = creds.second
                Toast.makeText(context, "Huella reconocida. Iniciando...", Toast.LENGTH_SHORT).show()
                viewModel.ejecutarLogin(creds.first, creds.second)
            },
            alFallar = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .verticalScroll(scrollState)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    brush = Brush.verticalGradient(listOf(NavyBlue, Color(0xFF183672), BrightBlue)),
                    shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 40.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(shape = CircleShape, color = VibrantOrange, shadowElevation = 8.dp, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Default.AccountBalance, contentDescription = "Logo UNACH", tint = Color.White, modifier = Modifier.padding(14.dp).fillMaxSize())
                }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, BrightBlue.copy(alpha = 0.6f))) {
                    Text("ESCUELA DE TECNOLOGÍAS DIGITALES APLICADAS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Campus Connect UNACH", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .offset(y = (-42).dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = LightBackground, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { modoRegistro = false }, color = if (!modoRegistro) NavyBlue else Color.Transparent) {
                            Text("Iniciar Sesión", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (!modoRegistro) Color.White else NavyBlue, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
                        }
                        Surface(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { modoRegistro = true }, color = if (modoRegistro) NavyBlue else Color.Transparent) {
                            Text("Crear Cuenta", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (modoRegistro) Color.White else NavyBlue, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (!modoRegistro) {
                    Text("Bienvenido a Campus Connect", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = NavyBlue)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Ingresa con tu cuenta universitaria o huella dactilar", color = Color.Gray, fontSize = 12.sp)

                    if (biometriaDisponible) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { iniciarConHuellaDactilar() }, color = BrightBlue.copy(alpha = 0.08f), border = BorderStroke(1.5.dp, BrightBlue)) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = NavyBlue, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.Fingerprint, contentDescription = null, tint = VibrantOrange, modifier = Modifier.padding(8.dp)) }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) { Text("Ingresar con Huella", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = NavyBlue) }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BrightBlue)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    OutlinedTextField(value = correo, onValueChange = { correo = it }, label = { Text("Correo institucional") }, leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = BrightBlue) }, singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Contraseña") }, leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = BrightBlue) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = { viewModel.ejecutarLogin(correo, password) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantOrange),
                        enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp) else Text("INGRESAR", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    Text(if (esAlumnoEgresado) "Registro de Alumno Egresado" else "Registro de Estudiante", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = NavyBlue)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !esAlumnoEgresado, onClick = { esAlumnoEgresado = false }, label = { Text("Estudiante Activo") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = esAlumnoEgresado, onClick = { esAlumnoEgresado = true }, label = { Text("Alumno Egresado") }, modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = regNombre, onValueChange = { regNombre = it }, label = { Text("Nombre completo") }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = regCorreoPrincipal, onValueChange = { regCorreoPrincipal = it }, label = { Text(if (esAlumnoEgresado) "Correo 1" else "Correo institucional") }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading)

                    AnimatedVisibility(visible = esAlumnoEgresado) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(value = regCorreoSecundario, onValueChange = { regCorreoSecundario = it }, label = { Text("Correo 2") }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(value = regTelefonoPrincipal, onValueChange = { regTelefonoPrincipal = it }, label = { Text("Teléfono 1") }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(value = regTelefonoSecundario, onValueChange = { regTelefonoSecundario = it }, label = { Text("Teléfono 2") }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = regPassword, onValueChange = { regPassword = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
                    Spacer(modifier = Modifier.height(20.dp))

                    // ¡Botón de Registro completamente limpio!
                    Button(
                        onClick = {
                            if (regNombre.isBlank() || regCorreoPrincipal.isBlank() || regPassword.isBlank()) return@Button
                            val req = RegistroRequest(regNombre, regCorreoPrincipal, regPassword, esAlumnoEgresado, regCorreoSecundario, regTelefonoPrincipal, regTelefonoSecundario)
                            viewModel.ejecutarRegistro(req)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                        enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp)) else Text("REGISTRARME")
                    }
                }
            }
        }

        Text("UNACH • ETDA", color = NavyBlue.copy(alpha = 0.55f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))

        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Verificación en 2 pasos") },
                text = { OutlinedTextField(value = otpCode, onValueChange = { otpCode = it }, label = { Text("Código de 6 dígitos") }) },
                confirmButton = {
                    // ¡Botón de 2FA completamente limpio!
                    Button(
                        onClick = { viewModel.verificar2FA(correo, otpCode, password) },
                        enabled = !isLoading
                    ) {
                        Text("Verificar")
                    }
                }
            )
        }

        if (mostrarModalOfrecerHuella) {
            AlertDialog(
                onDismissRequest = { mostrarModalOfrecerHuella = false; onNavigateToAlumno() },
                title = { Text("¿Activar Huella?") },
                text = { Text("¿Quieres usar tu huella dactilar para iniciar sesión más rápido la próxima vez?") },
                confirmButton = {
                    Button(onClick = {
                        TokenManager.guardarCredencialesBiometricas(context, correo.trim(), password.trim())
                        mostrarModalOfrecerHuella = false
                        onNavigateToAlumno()
                    }) { Text("Sí, activar") }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarModalOfrecerHuella = false; onNavigateToAlumno() }) { Text("Ahora no") }
                }
            )
        }
    }
}