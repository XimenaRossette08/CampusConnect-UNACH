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
import com.example.campusconnect.model.LoginRequest
import com.example.campusconnect.model.RegistroRequest
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
val EmeraldSuccess = Color(0xFF10B981)

// Nota: Extendemos de FragmentActivity para dar soporte nativo a BiometricPrompt
class ComposeLoginActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si ya tiene una sesión activa y vigente (dentro de los 30 días), pasar directo
        if (TokenManager.haySesionActiva(this)) {
            startActivity(Intent(this, EventosDemoActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            CampusConnectTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = LightBackground) {
                    LoginScreen(
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

// Auxiliar para obtener FragmentActivity desde el Context de Compose
private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// Verifica si el dispositivo/emulador tiene lector de huella configurado
private fun dispositivoSoportaHuella(context: Context): Boolean {
    val bm = BiometricManager.from(context)
    val res = bm.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    )
    return res == BiometricManager.BIOMETRIC_SUCCESS
}

// Lanza el diálogo nativo de huella dactilar de Android
private fun mostrarPromptHuella(
    context: Context,
    titulo: String,
    subtitulo: String,
    alAutenticarConExito: () -> Unit,
    alFallar: (String) -> Unit = {}
) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
        alFallar("No se pudo inicializar el lector de huella en esta pantalla.")
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
fun LoginScreen(onNavigateToAlumno: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Modo principal: false = Iniciar Sesión, true = Crear Cuenta (Registro)
    var modoRegistro by remember { mutableStateOf(false) }

    // Estados de Inicio de Sesión
    var correo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Estados de 2FA
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var isVerifyingOtp by remember { mutableStateOf(false) }

    // Estados de Biometría (Huella Dactilar)
    var biometriaDisponible by remember { mutableStateOf(TokenManager.tieneBiometriaHabilitada(context)) }
    var mostrarModalOfrecerHuella by remember { mutableStateOf(false) }

    // Estados de Registro (Estudiante Activo vs Alumno Egresado)
    var esAlumnoEgresado by remember { mutableStateOf(false) }
    var regNombre by remember { mutableStateOf("") }
    var regCorreoPrincipal by remember { mutableStateOf("") }
    var regCorreoSecundario by remember { mutableStateOf("") }
    var regTelefonoPrincipal by remember { mutableStateOf("") }
    var regTelefonoSecundario by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }

    // Paso final tras validar login/2FA: ofrecer activar huella si aún no la tiene activa
    fun finalizarAccesoOVincularHuella(emailAutenticado: String, passAutenticada: String) {
        val credsGuardadas = TokenManager.obtenerCredencialesBiometricas(context)
        val yaVinculadoMismoUsuario = credsGuardadas?.first?.equals(emailAutenticado, ignoreCase = true) == true

        if (!yaVinculadoMismoUsuario && passAutenticada.isNotBlank()) {
            mostrarModalOfrecerHuella = true
        } else {
            onNavigateToAlumno()
        }
    }

    // Función centralizada para iniciar sesión (llamada por botón INGRESAR o por Huella Dactilar)
    fun ejecutarLogin(correoInput: String, passwordInput: String) {
        val correoLimpio = correoInput.trim()
        val passwordLimpia = passwordInput.trim()

        if (correoLimpio.isBlank() || passwordLimpia.isBlank()) {
            Toast.makeText(context, "Completa tu correo y contraseña", Toast.LENGTH_SHORT).show()
            return
        }

        val yaTiene2FAVigente = TokenManager.tiene2FAVigenteParaUsuario(context, correoLimpio)
        isLoading = true

        scope.launch {
            try {
                val respuesta = RetrofitClient.obtenerApi(context).loginUser(
                    LoginRequest(
                        correo = correoLimpio,
                        contrasena = passwordLimpia,
                        dispositivoConfiable = yaTiene2FAVigente
                    )
                )

                if (respuesta.isSuccessful) {
                    val body = respuesta.body()
                    // Caso 1: El backend ya devuelve access_token directo (no requiere 2FA)
                    val tokenDirecto = body?.accessToken
                    if (body != null && !body.requiere2fa && !tokenDirecto.isNullOrBlank()) {
                        TokenManager.guardarSesion(
                            context = context,
                            token = tokenDirecto,
                            refreshToken = body.refreshToken ?: "",
                            email = correoLimpio,
                            rol = body.role ?: "Estudiante",
                            actualizarMarca2FA = false
                        )
                        Toast.makeText(
                            context,
                            "Sesión iniciada (2FA vigente por ${TokenManager.obtenerDescripcionVigencia()})",
                            Toast.LENGTH_SHORT
                        ).show()
                        finalizarAccesoOVincularHuella(correoLimpio, passwordLimpia)
                    }
                    // Caso 2: El usuario ya verificó su 2FA en este dispositivo hace menos de 1 mes (o tiempo de prueba)
                    else if (TokenManager.restaurarSesionCon2FAVigente(context, correoLimpio)) {
                        Toast.makeText(
                            context,
                            "Acceso rápido: tu verificación de 2 pasos sigue activa (${TokenManager.obtenerDescripcionVigencia()})",
                            Toast.LENGTH_SHORT
                        ).show()
                        finalizarAccesoOVincularHuella(correoLimpio, passwordLimpia)
                    }
                    // Caso 3: Primera vez o ya pasó 1 mes -> pedir código de 6 dígitos
                    else {
                        showOtpDialog = true
                    }
                } else {
                    Toast.makeText(context, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                }
            } catch (e: HttpException) {
                val mensaje = if (e.code() == 401) "Correo o contraseña incorrectos" else "Error en el servidor (${e.code()})"
                Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Toast.makeText(context, "Error de red. Verifica que el servidor FastAPI esté activo.", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // Acción al tocar "Usar Huella Dactilar": autentica huella, rellena datos automáticamente e inicia sesión
    fun iniciarConHuellaDactilar() {
        val creds = TokenManager.obtenerCredencialesBiometricas(context)
        if (creds == null) {
            Toast.makeText(context, "Primero inicia sesión con tu correo y contraseña para vincular tu huella.", Toast.LENGTH_SHORT).show()
            return
        }

        mostrarPromptHuella(
            context = context,
            titulo = "Iniciar sesión con Huella Dactilar",
            subtitulo = "Coloca tu huella para autorrellenar tu cuenta (${creds.first}) e ingresar",
            alAutenticarConExito = {
                // Autorrellenamos los campos en pantalla y disparamos el inicio de sesión
                correo = creds.first
                password = creds.second
                Toast.makeText(context, "Huella reconocida. Iniciando sesión...", Toast.LENGTH_SHORT).show()
                ejecutarLogin(creds.first, creds.second)
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
        // ENCABEZADO SUPERIOR CON IDENTIDAD ETDA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(NavyBlue, Color(0xFF183672), BrightBlue)
                    ),
                    shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
                )
        ) {
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
                    .padding(top = 40.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = VibrantOrange,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = "Logo UNACH",
                        tint = Color.White,
                        modifier = Modifier.padding(14.dp).fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Campus Connect UNACH",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "“Por la conciencia de la necesidad de servir”",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center
                )
            }
        }

        // TARJETA PRINCIPAL DE ACCESO / REGISTRO
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
                // SELECTOR SUPERIOR: INICIAR SESIÓN VS CREAR CUENTA
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LightBackground,
                    border = BorderStroke(1.dp, NavyBlue.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { modoRegistro = false },
                            color = if (!modoRegistro) NavyBlue else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Iniciar Sesión",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!modoRegistro) Color.White else NavyBlue.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { modoRegistro = true },
                            color = if (modoRegistro) NavyBlue else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Crear Cuenta",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (modoRegistro) Color.White else NavyBlue.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (!modoRegistro) {
                    // =========================================================
                    // VISTA A: INICIAR SESIÓN (CON OPCIÓN DE HUELLA DACTILAR)
                    // =========================================================
                    Text(
                        text = "Bienvenido a Campus Connect",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NavyBlue
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ingresa con tu cuenta universitaria o huella dactilar",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    // Botón destacado de Huella Dactilar si el usuario ya aceptó vincularla antes
                    if (biometriaDisponible) {
                        Spacer(modifier = Modifier.height(16.dp))
                        val emailVinculado = TokenManager.obtenerCredencialesBiometricas(context)?.first ?: ""
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { iniciarConHuellaDactilar() },
                            shape = RoundedCornerShape(16.dp),
                            color = BrightBlue.copy(alpha = 0.08f),
                            border = BorderStroke(1.5.dp, BrightBlue)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = NavyBlue,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Huella Dactilar",
                                        tint = VibrantOrange,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ingresar con Huella Dactilar",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = NavyBlue
                                    )
                                    Text(
                                        text = "Autorrellenar datos de $emailVinculado",
                                        fontSize = 11.sp,
                                        color = Color(0xFF5B6670)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = BrightBlue
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = correo,
                        onValueChange = { correo = it },
                        label = { Text("Correo institucional") },
                        placeholder = { Text("nombre.apellido@unach.mx", fontSize = 13.sp, color = Color.LightGray) },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = "Correo", tint = BrightBlue)
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

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "Contraseña", tint = BrightBlue)
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

                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = { ejecutarLogin(correo, password) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
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
                } else {
                    // =========================================================
                    // VISTA B: CREAR CUENTA (ESTUDIANTE ACTIVO Y ALUMNO EGRESADO)
                    // =========================================================
                    Text(
                        text = if (esAlumnoEgresado) "Registro de Alumno Egresado" else "Registro de Estudiante",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NavyBlue
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (esAlumnoEgresado)
                            "Completa tus datos y proporciona 2 correos y 2 teléfonos distintos de contacto"
                        else
                            "Crea tu cuenta institucional para inscribirte a eventos de la ETDA",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Selector de Tipo de Alumno: Estudiante Activo vs Alumno Egresado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !esAlumnoEgresado,
                            onClick = { esAlumnoEgresado = false },
                            label = {
                                Text(
                                    "Estudiante Activo",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VibrantOrange.copy(alpha = 0.16f),
                                selectedLabelColor = NavyBlue,
                                selectedLeadingIconColor = VibrantOrange
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = esAlumnoEgresado,
                            onClick = { esAlumnoEgresado = true },
                            label = {
                                Text(
                                    "Alumno Egresado",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VibrantOrange.copy(alpha = 0.16f),
                                selectedLabelColor = NavyBlue,
                                selectedLeadingIconColor = VibrantOrange
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Nombre completo (común para ambos)
                    OutlinedTextField(
                        value = regNombre,
                        onValueChange = { regNombre = it },
                        label = { Text("Nombre completo") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = BrightBlue) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRegistering
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Correo electrónico 1 (Principal / Institucional)
                    OutlinedTextField(
                        value = regCorreoPrincipal,
                        onValueChange = { regCorreoPrincipal = it },
                        label = {
                            Text(if (esAlumnoEgresado) "Correo electrónico 1 (Principal)" else "Correo institucional")
                        },
                        placeholder = { Text("ejemplo@unach.mx", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = BrightBlue) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRegistering
                    )

                    // Campos adicionales exclusivos para ALUMNOS EGRESADOS (2do correo + 2 teléfonos distintos)
                    AnimatedVisibility(visible = esAlumnoEgresado) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = regCorreoSecundario,
                                onValueChange = { regCorreoSecundario = it },
                                label = { Text("Correo electrónico 2 (Personal / Alternativo)") },
                                placeholder = { Text("Debe ser diferente al correo 1", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = VibrantOrange) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isRegistering
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = regTelefonoPrincipal,
                                onValueChange = { input ->
                                    if (input.length <= 10 && input.all { it.isDigit() }) {
                                        regTelefonoPrincipal = input
                                    }
                                },
                                label = { Text("Número telefónico 1 (Principal - 10 dígitos)") },
                                placeholder = { Text("Ej. 9611234567", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = BrightBlue) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isRegistering
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = regTelefonoSecundario,
                                onValueChange = { input ->
                                    if (input.length <= 10 && input.all { it.isDigit() }) {
                                        regTelefonoSecundario = input
                                    }
                                },
                                label = { Text("Número telefónico 2 (Recados / Alternativo)") },
                                placeholder = { Text("Debe ser diferente al teléfono 1", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.ContactPhone, contentDescription = null, tint = VibrantOrange) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isRegistering
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Contraseña
                    OutlinedTextField(
                        value = regPassword,
                        onValueChange = { regPassword = it },
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = BrightBlue) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRegistering
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val nombre = regNombre.trim()
                            val email1 = regCorreoPrincipal.trim().lowercase()
                            val email2 = regCorreoSecundario.trim().lowercase()
                            val tel1 = regTelefonoPrincipal.trim()
                            val tel2 = regTelefonoSecundario.trim()
                            val pass = regPassword.trim()

                            if (nombre.isBlank() || email1.isBlank() || pass.isBlank()) {
                                Toast.makeText(context, "Completa nombre, correo y contraseña", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!Patterns.EMAIL_ADDRESS.matcher(email1).matches()) {
                                Toast.makeText(context, "Ingresa un correo electrónico principal válido", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // Validaciones estrictas para Alumnos Egresados
                            if (esAlumnoEgresado) {
                                if (email2.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email2).matches()) {
                                    Toast.makeText(context, "Ingresa el segundo correo electrónico válido para egresados", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (email1 == email2) {
                                    Toast.makeText(context, "Los dos correos electrónicos deben ser diferentes entre sí", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (tel1.length < 10 || tel2.length < 10) {
                                    Toast.makeText(context, "Ambos números telefónicos deben tener 10 dígitos", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (tel1 == tel2) {
                                    Toast.makeText(context, "Los dos números telefónicos deben ser diferentes entre sí", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                            }

                            isRegistering = true
                            scope.launch {
                                try {
                                    val req = RegistroRequest(
                                        nombre = nombre,
                                        email = email1,
                                        password = pass,
                                        esEgresado = esAlumnoEgresado,
                                        emailSecundario = if (esAlumnoEgresado) email2 else null,
                                        telefonoPrincipal = if (esAlumnoEgresado) tel1 else null,
                                        telefonoSecundario = if (esAlumnoEgresado) tel2 else null
                                    )
                                    val res = RetrofitClient.obtenerApi(context).registrarUsuario(req)
                                    if (res.isSuccessful) {
                                        Toast.makeText(
                                            context,
                                            if (esAlumnoEgresado) "¡Cuenta de egresado registrada con éxito! Inicia sesión."
                                            else "¡Cuenta registrada con éxito! Inicia sesión.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        correo = email1
                                        password = pass
                                        modoRegistro = false
                                    } else {
                                        Toast.makeText(context, "No se pudo completar el registro. Verifica tus datos.", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error de conexión al registrar usuario.", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isRegistering = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                        enabled = !isRegistering
                    ) {
                        if (isRegistering) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = if (esAlumnoEgresado) "REGISTRARME COMO EGRESADO" else "CREAR CUENTA",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Pie de página institucional con indicador del modo de vigencia 2FA
        Text(
            text = "UNACH • ETDA  |  Vigencia 2FA: ${TokenManager.obtenerDescripcionVigencia()}",
            color = NavyBlue.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-20).dp)
                .padding(bottom = 16.dp)
        )

        // =====================================================================
        // MODAL 1: VERIFICACIÓN EN 2 PASOS (2FA)
        // =====================================================================
        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = { /* Uso de botones */ },
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
                            text = "Ingresa el código de 6 dígitos enviado a tu correo. Una vez verificado, tu sesión se mantendrá activa por ${TokenManager.obtenerDescripcionVigencia()} sin volver a pedirlo:",
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

                                        // Guardamos sesión y activamos el sello de confianza 2FA por 30 días
                                        TokenManager.guardarSesion(
                                            context = context,
                                            token = tokenData.accessToken,
                                            refreshToken = tokenData.refreshToken,
                                            email = correo.trim(),
                                            rol = tokenData.role,
                                            actualizarMarca2FA = true
                                        )

                                        showOtpDialog = false
                                        otpCode = ""
                                        finalizarAccesoOVincularHuella(correo.trim(), password.trim())
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

        // =====================================================================
        // MODAL 2: INVITACIÓN A ACTIVAR HUELLA DACTILAR DESPUÉS DEL PRIMER LOGIN
        // =====================================================================
        if (mostrarModalOfrecerHuella) {
            AlertDialog(
                onDismissRequest = {
                    mostrarModalOfrecerHuella = false
                    onNavigateToAlumno()
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.White,
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = VibrantOrange.copy(alpha = 0.14f),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Activar Huella",
                                tint = VibrantOrange,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "¿Activar inicio con huella dactilar?",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            color = NavyBlue,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                text = {
                    Text(
                        text = "¿Quieres usar tu huella dactilar para iniciar sesión más rápido la próxima vez? Al aceptarlo, la app rellenará tus datos automáticamente e iniciará sesión al colocar tu huella.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (dispositivoSoportaHuella(context)) {
                                mostrarPromptHuella(
                                    context = context,
                                    titulo = "Confirmar Huella Dactilar",
                                    subtitulo = "Coloca tu huella para vincular tu cuenta UNACH",
                                    alAutenticarConExito = {
                                        TokenManager.guardarCredencialesBiometricas(
                                            context = context,
                                            email = correo.trim(),
                                            password = password.trim()
                                        )
                                        biometriaDisponible = true
                                        mostrarModalOfrecerHuella = false
                                        Toast.makeText(
                                            context,
                                            "¡Huella dactilar activada para tus próximos inicios de sesión!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onNavigateToAlumno()
                                    },
                                    alFallar = {
                                        // Si en el emulador aún no han registrado dedo en Settings, igual permitimos guardarlo
                                        TokenManager.guardarCredencialesBiometricas(
                                            context = context,
                                            email = correo.trim(),
                                            password = password.trim()
                                        )
                                        biometriaDisponible = true
                                        mostrarModalOfrecerHuella = false
                                        Toast.makeText(
                                            context,
                                            "Acceso rápido activado. (Configura la huella en Ajustes del emulador si deseas validar sensor).",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onNavigateToAlumno()
                                    }
                                )
                            } else {
                                // Si el emulador no tiene huella configurada aún, guardamos las credenciales listas para usar
                                TokenManager.guardarCredencialesBiometricas(
                                    context = context,
                                    email = correo.trim(),
                                    password = password.trim()
                                )
                                biometriaDisponible = true
                                mostrarModalOfrecerHuella = false
                                Toast.makeText(
                                    context,
                                    "¡Inicio rápido con huella activado!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onNavigateToAlumno()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = VibrantOrange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sí, usar huella", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            mostrarModalOfrecerHuella = false
                            onNavigateToAlumno()
                        }
                    ) {
                        Text("Ahora no", color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    }
}