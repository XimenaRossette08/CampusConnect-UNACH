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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campusconnect.model.CarreraCatalogoDto
import com.example.campusconnect.model.ConfiguracionRegistroDto
import com.example.campusconnect.model.FacultadCatalogoDto
import com.example.campusconnect.model.RegistroRequest
import com.example.campusconnect.network.AuthRepository
import com.example.campusconnect.network.RetrofitClient
import com.example.campusconnect.ui.LoginState
import com.example.campusconnect.ui.LoginViewModel
import com.example.campusconnect.ui.theme.CampusConnectTheme

// Paleta Institucional Intacta
val NavyBlue = Color(0xFF122652)
val BrightBlue = Color(0xFF0093FF)
val VibrantOrange = Color(0xFFF57C00)
val LightBackground = Color(0xFFF4F7FB)

fun formatearEtiquetaGrupo(codigoGrupo: String): String {
    val limpio = codigoGrupo.trim()
    val regex = Regex("^([0-9]+)([A-Za-z]+)$")
    val match = regex.matchEntire(limpio)
    return if (match != null) {
        val sem = match.groupValues[1]
        val grp = match.groupValues[2].uppercase()
        "${sem}° Semestre • Grupo $grp"
    } else {
        limpio
    }
}

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
                            @Suppress("UNCHECKED_CAST")
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
    alAutenticarConExito: (javax.crypto.Cipher?) -> Unit,
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
            alAutenticarConExito(result.cryptoObject?.cipher)
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

    val biometricPrompt = BiometricPrompt(activity, executor, callback)
    val cipherKeystore = TokenManager.obtenerCipherDescifradoBiometrico(context)
    if (cipherKeystore != null) {
        try {
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipherKeystore))
            return
        } catch (_: Exception) {
            // Fallback en lectores Clase 2 que no admiten CryptoObject directo
        }
    }
    biometricPrompt.authenticate(promptInfo)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: LoginViewModel, onNavigateToAlumno: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var modoRegistro by remember { mutableStateOf(false) }

    var correo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val estadoLogin by viewModel.loginState.collectAsState()
    val isLoading = estadoLogin is LoginState.Loading

    var showOtpDialog by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }

    var biometriaDisponible by remember {
        mutableStateOf(TokenManager.tieneBiometriaHabilitada(context) && dispositivoSoportaHuella(context))
    }
    var mostrarModalOfrecerHuella by remember { mutableStateOf(false) }

    var esAlumnoEgresado by remember { mutableStateOf(false) }
    var regNombre by remember { mutableStateOf("") }
    var regCorreoPrincipal by remember { mutableStateOf("") }
    var regMatricula by remember { mutableStateOf("") }
    var regCorreoSecundario by remember { mutableStateOf("") }
    var regTelefonoPrincipal by remember { mutableStateOf("") }
    var regTelefonoSecundario by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var aceptoAvisoPrivacidad by remember { mutableStateOf(false) }
    var mostrarModalAvisoPrivacidad by remember { mutableStateOf(false) }
    var mensajeErrorValidacion by remember { mutableStateOf<String?>(null) }

    // Catálogo dinámico de Facultades, Carreras, Grupos y Políticas de Registro sincronizado con Administración
    val facultadesPorDefecto = remember {
        listOf(
            FacultadCatalogoDto("ETDA", "Escuela de Tecnologías Digitales Aplicadas (ETDA)"),
            FacultadCatalogoDto("FCA", "Facultad de Contaduría y Administración (FCA)"),
            FacultadCatalogoDto("FI", "Facultad de Ingeniería (FI)")
        )
    }

    val carrerasPorDefecto = remember {
        listOf(
            CarreraCatalogoDto(
                id = "LIDTS",
                nombre = "Lic. en Ingeniería en Desarrollo y Tecnologías de Software",
                facultadId = "ETDA",
                grupos = listOf("1A", "1B", "3A", "3B", "5A", "5B", "7N", "7O", "8A")
            ),
            CarreraCatalogoDto(
                id = "LSC",
                nombre = "Lic. en Sistemas Computacionales",
                facultadId = "FCA",
                grupos = listOf("1A", "1B", "3A", "5A", "7A", "9A")
            ),
            CarreraCatalogoDto(
                id = "IC",
                nombre = "Ingeniería Civil",
                facultadId = "FI",
                grupos = listOf("1A", "2A", "3A", "4A", "5A", "6A", "7A", "8A")
            )
        )
    }

    var listaFacultadesCatalogo by remember { mutableStateOf(facultadesPorDefecto) }
    var listaCarrerasCatalogo by remember { mutableStateOf(carrerasPorDefecto) }
    var configRegistro by remember { mutableStateOf(ConfiguracionRegistroDto()) }
    var carreraSeleccionada by remember { mutableStateOf<CarreraCatalogoDto?>(carrerasPorDefecto.firstOrNull()) }
    var grupoSeleccionado by remember {
        mutableStateOf(carrerasPorDefecto.firstOrNull()?.grupos?.firstOrNull() ?: "1A")
    }
    var menuCarreraExpandido by remember { mutableStateOf(false) }
    var menuGrupoExpandido by remember { mutableStateOf(false) }

    fun obtenerNombreFacultad(facultadId: String?): String {
        if (facultadId.isNullOrBlank()) return "ETDA"
        return listaFacultadesCatalogo.find { it.id.equals(facultadId, ignoreCase = true) }?.nombre ?: facultadId
    }

    LaunchedEffect(modoRegistro) {
        mensajeErrorValidacion = null
        try {
            val api = RetrofitClient.obtenerApi(context)
            val remoto = api.obtenerCatalogosFacultad()
            val prefs = context.getSharedPreferences("cc_expediente_alumno", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            remoto.facultades?.takeIf { it.isNotEmpty() }?.let { facs ->
                listaFacultadesCatalogo = facs
            }

            remoto.configuracionRegistro?.let { cfgRemota ->
                configRegistro = cfgRemota
                if (!cfgRemota.permitirRegistroEgresados && esAlumnoEgresado) {
                    esAlumnoEgresado = false
                }
            }

            val gruposGlobales = remoto.grupos?.takeIf { it.isNotEmpty() }
                ?: listOf("1A", "1B", "3A", "3B", "5A", "5B", "7N", "7O", "8A")
            val carrerasRemotas = remoto.carreras?.map { c ->
                val gruposDeCarrera = c.grupos?.takeIf { it.isNotEmpty() } ?: gruposGlobales
                c.copy(grupos = gruposDeCarrera)
            }?.takeIf { it.isNotEmpty() }

            if (carrerasRemotas != null) {
                listaCarrerasCatalogo = carrerasRemotas
                val coincide = carrerasRemotas.find {
                    it.nombre.equals(carreraSeleccionada?.nombre, ignoreCase = true)
                } ?: carrerasRemotas.first()
                carreraSeleccionada = coincide
                val gruposDisponibles = coincide.grupos.orEmpty()
                if (grupoSeleccionado !in gruposDisponibles) {
                    grupoSeleccionado = gruposDisponibles.firstOrNull() ?: ""
                }
            }

            remoto.datosInstitucionales?.let { inst ->
                editor.putString("inst_escuela", inst.escuela ?: "Escuela de Tecnologías Digitales Aplicadas (ETDA)")
                editor.putString("inst_modalidad", inst.modalidadSede ?: "Escolarizada (Campus I, Tuxtla Gutiérrez)")
                editor.putString("inst_ciclo", inst.cicloEscolar ?: "Agosto - Diciembre 2026")
            }
            editor.apply()
        } catch (_: Exception) {
        }
    }

    fun finalizarAccesoOVincularHuella(emailAutenticado: String, refreshTokenNuevo: String) {
        val credsGuardadas = TokenManager.obtenerCredencialesBiometricas(context)
        val yaVinculadoMismoUsuario = credsGuardadas?.first?.equals(emailAutenticado, ignoreCase = true) == true

        if (!yaVinculadoMismoUsuario && refreshTokenNuevo.isNotBlank() && dispositivoSoportaHuella(context)) {
            mostrarModalOfrecerHuella = true
        } else {
            onNavigateToAlumno()
        }
    }

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
                    email = estado.correo.trim().lowercase(),
                    rol = estado.role,
                    actualizarMarca2FA = true
                )
                showOtpDialog = false
                finalizarAccesoOVincularHuella(estado.correo.trim().lowercase(), estado.refreshToken)
                viewModel.resetState()
            }
            is LoginState.RegisterSuccess -> {
                val emailKey = regCorreoPrincipal.trim().lowercase()
                val facIdElegida = carreraSeleccionada?.facultadId ?: "ETDA"
                val prefs = context.getSharedPreferences("cc_expediente_alumno", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                    .putString("nombre_$emailKey", regNombre.trim())
                    .putBoolean("egresado_$emailKey", esAlumnoEgresado)
                    .putString("email_sec_$emailKey", if (esAlumnoEgresado) regCorreoSecundario.trim().lowercase() else "")
                    .putString("tel1_$emailKey", if (esAlumnoEgresado) regTelefonoPrincipal.trim() else "")
                    .putString("tel2_$emailKey", if (esAlumnoEgresado) regTelefonoSecundario.trim() else "")

                if (esAlumnoEgresado) {
                    // Para egresados no se guarda matrícula, licenciatura ni grupo
                    editor.remove("matricula_$emailKey")
                    editor.remove("carrera_$emailKey")
                    editor.remove("grupo_$emailKey")
                } else {
                    editor.putString("matricula_$emailKey", regMatricula.trim().uppercase())
                    editor.putString("carrera_$emailKey", carreraSeleccionada?.nombre ?: "")
                    editor.putString("grupo_$emailKey", grupoSeleccionado.trim().uppercase())
                    editor.putString("facultad_$emailKey", facIdElegida)
                    editor.putString("escuela_$emailKey", obtenerNombreFacultad(facIdElegida))
                }
                editor.apply()

                correo = emailKey
                password = ""
                mensajeErrorValidacion = null
                Toast.makeText(context, "Cuenta creada correctamente. Ahora inicia sesión.", Toast.LENGTH_LONG).show()
                modoRegistro = false
                viewModel.resetState()
            }
            is LoginState.Failure -> {
                mensajeErrorValidacion = estado.error
                Toast.makeText(context, estado.error, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    fun iniciarConHuellaDactilar() {
        if (!TokenManager.tieneBiometriaHabilitada(context)) {
            Toast.makeText(context, "Primero inicia sesión para vincular tu huella.", Toast.LENGTH_SHORT).show()
            return
        }
        val correoVinculado = TokenManager.obtenerEmail(context)

        mostrarPromptHuella(
            context = context,
            titulo = "Iniciar sesión con Huella Dactilar",
            subtitulo = "Coloca tu huella para ingresar con $correoVinculado",
            alAutenticarConExito = { cipherAutenticado ->
                val creds = TokenManager.obtenerCredencialesBiometricas(context, cipherAutenticado)
                if (creds == null) {
                    Toast.makeText(
                        context,
                        "Tu vínculo biométrico expiró. Inicia sesión con tu contraseña.",
                        Toast.LENGTH_LONG
                    ).show()
                    TokenManager.desactivarBiometria(context)
                    biometriaDisponible = false
                    return@mostrarPromptHuella
                }
                correo = creds.first.trim().lowercase()
                Toast.makeText(context, "Huella verificada. Renovando sesión segura...", Toast.LENGTH_SHORT).show()
                viewModel.ejecutarLoginBiometrico(correo, creds.second)
            },
            alFallar = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    fun validarYEnviarRegistro() {
        mensajeErrorValidacion = null
        val nombreLimpio = regNombre.trim().replace(Regex("\\s+"), " ")
        val correoLimpio = regCorreoPrincipal.trim().lowercase()
        val matriculaLimpia = regMatricula.trim().uppercase()
        val carreraNombre = carreraSeleccionada?.nombre?.trim().orEmpty()
        val grupoLimpio = grupoSeleccionado.trim().uppercase()
        val passLimpia = regPassword

        if (nombreLimpio.length < 2) {
            mensajeErrorValidacion = "Ingresa tu nombre completo (mínimo 2 caracteres)."
            return
        }
        if (correoLimpio.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(correoLimpio).matches()) {
            mensajeErrorValidacion = "Ingresa un correo electrónico válido."
            return
        }

        if (!esAlumnoEgresado) {
            // Validaciones exclusivas para Estudiante Activo
            if (configRegistro.restringirDominioCorreo) {
                val dominios = configRegistro.dominiosPermitidos?.map { it.trim().lowercase().removePrefix("@") }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                    .ifEmpty { listOf("unach.mx") }
                val dominioUsuario = correoLimpio.substringAfterLast("@", "")
                if (dominioUsuario !in dominios) {
                    val listaDominios = dominios.joinToString(", ") { "@$it" }
                    mensajeErrorValidacion = "Usa tu correo institucional autorizado ($listaDominios)."
                    return
                }
            }

            if (configRegistro.requerirMatricula || matriculaLimpia.isNotEmpty()) {
                val minMat = configRegistro.longitudMinMatricula.coerceAtLeast(1)
                val maxMat = configRegistro.longitudMaxMatricula.coerceAtLeast(minMat)
                if (matriculaLimpia.isBlank()) {
                    mensajeErrorValidacion = "La matrícula estudiantil es obligatoria."
                    return
                }
                if (matriculaLimpia.length !in minMat..maxMat) {
                    mensajeErrorValidacion = "La matrícula debe tener entre $minMat y $maxMat caracteres."
                    return
                }
                if (configRegistro.soloDigitosMatricula && !matriculaLimpia.all { it.isDigit() }) {
                    mensajeErrorValidacion = "La matrícula solo debe contener números."
                    return
                }
                if (!Regex("^[A-Z0-9\\-]+$").matches(matriculaLimpia)) {
                    mensajeErrorValidacion = "La matrícula solo puede contener letras, números o guiones."
                    return
                }
            }

            if (configRegistro.requerirCarreraGrupo) {
                if (carreraNombre.isBlank() || grupoLimpio.isBlank()) {
                    mensajeErrorValidacion = "Selecciona tu Programa Educativo (Carrera) y tu Grado/Grupo."
                    return
                }
            }
        }

        // Validaciones específicas para Alumno Egresado (sin pedir matrícula, licenciatura ni grupo)
        val correoSecLimpio = regCorreoSecundario.trim().lowercase()
        val tel1Limpio = regTelefonoPrincipal.trim().replace(Regex("[\\s\\-()]"), "")
        val tel2Limpio = regTelefonoSecundario.trim().replace(Regex("[\\s\\-()]"), "")

        if (esAlumnoEgresado) {
            if (correoSecLimpio.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(correoSecLimpio).matches()) {
                mensajeErrorValidacion = "Los egresados deben ingresar un correo alternativo válido."
                return
            }
            if (correoSecLimpio == correoLimpio) {
                mensajeErrorValidacion = "El correo principal y el correo alternativo deben ser diferentes."
                return
            }
            if (!Regex("^\\d{10}$").matches(tel1Limpio) || !Regex("^\\d{10}$").matches(tel2Limpio)) {
                mensajeErrorValidacion = "Ingresa dos números telefónicos válidos de 10 dígitos numéricos."
                return
            }
            if (tel1Limpio == tel2Limpio) {
                mensajeErrorValidacion = "El teléfono principal y el secundario deben ser diferentes."
                return
            }
        }

        val minPass = configRegistro.longitudMinPassword.coerceAtLeast(8)
        if (passLimpia.length < minPass) {
            mensajeErrorValidacion = "La contraseña debe tener al menos $minPass caracteres."
            return
        }
        if (passLimpia.toByteArray(Charsets.UTF_8).size > 72) {
            mensajeErrorValidacion = "La contraseña no debe exceder 72 caracteres."
            return
        }
        if (!aceptoAvisoPrivacidad) {
            mensajeErrorValidacion = "Debes leer y aceptar el Aviso de Privacidad Institucional para registrarte."
            return
        }

        val req = RegistroRequest(
            nombre = nombreLimpio,
            email = correoLimpio,
            password = passLimpia,
            esEgresado = esAlumnoEgresado,
            emailSecundario = correoSecLimpio.takeIf { esAlumnoEgresado && it.isNotBlank() },
            telefonoPrincipal = tel1Limpio.takeIf { esAlumnoEgresado && it.isNotBlank() },
            telefonoSecundario = tel2Limpio.takeIf { esAlumnoEgresado && it.isNotBlank() },
            matricula = if (esAlumnoEgresado) null else matriculaLimpia.takeIf { it.isNotBlank() },
            carrera = if (esAlumnoEgresado) null else carreraNombre.takeIf { it.isNotBlank() },
            semestreGrupo = if (esAlumnoEgresado) null else grupoLimpio.takeIf { it.isNotBlank() },
            idFacultad = if (esAlumnoEgresado) null else (carreraSeleccionada?.facultadId ?: "ETDA")
        )
        viewModel.ejecutarRegistro(req)
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = BrightBlue,
        unfocusedBorderColor = Color(0xFFCBD5E1),
        focusedLabelColor = NavyBlue,
        unfocusedLabelColor = Color(0xFF64748B),
        focusedContainerColor = Color(0xFFF8FAFC),
        unfocusedContainerColor = Color(0xFFF8FAFC),
        cursorColor = VibrantOrange
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .verticalScroll(scrollState)
    ) {
        // Hero Institucional con altura adaptable para que el lema nunca quede tapado por la tarjeta
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(
                    brush = Brush.verticalGradient(listOf(NavyBlue, Color(0xFF183672), BrightBlue)),
                    shape = RoundedCornerShape(bottomStart = 42.dp, bottomEnd = 42.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 18.dp, bottom = 58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = VibrantOrange,
                    shadowElevation = 10.dp,
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.size(62.dp)
                ) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = "Logo UNACH",
                        tint = Color.White,
                        modifier = Modifier.padding(14.dp).fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "ESCUELA DE TECNOLOGÍAS DIGITALES APLICADAS",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Campus Connect UNACH",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "“Por la conciencia de la necesidad de servir”",
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .offset(y = (-28).dp),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, NavyBlue.copy(alpha = 0.06f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Selector de pestañas Iniciar Sesión / Crear Cuenta con transiciones suaves
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = LightBackground,
                    border = BorderStroke(1.dp, NavyBlue.copy(alpha = 0.06f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val bgTabLogin by animateColorAsState(
                            targetValue = if (!modoRegistro) NavyBlue else Color.Transparent,
                            animationSpec = tween(220),
                            label = "bgTabLogin"
                        )
                        val bgTabReg by animateColorAsState(
                            targetValue = if (modoRegistro) NavyBlue else Color.Transparent,
                            animationSpec = tween(220),
                            label = "bgTabReg"
                        )

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    modoRegistro = false
                                    mensajeErrorValidacion = null
                                },
                            color = bgTabLogin,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Iniciar Sesión",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (!modoRegistro) Color.White else NavyBlue.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 11.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    modoRegistro = true
                                    mensajeErrorValidacion = null
                                },
                            color = bgTabReg,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Crear Cuenta",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (modoRegistro) Color.White else NavyBlue.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 11.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                AnimatedVisibility(
                    visible = !mensajeErrorValidacion.isNullOrBlank(),
                    enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                    exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
                ) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = mensajeErrorValidacion.orEmpty(),
                                color = Color(0xFFB91C1C),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                if (!modoRegistro) {
                    Text(
                        text = "Bienvenido a Campus Connect",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NavyBlue,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ingresa con tu cuenta universitaria o huella dactilar",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )

                    if (biometriaDisponible) {
                        val correoGuardado = TokenManager.obtenerEmail(context)
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { iniciarConHuellaDactilar() },
                            shape = RoundedCornerShape(16.dp),
                            color = BrightBlue.copy(alpha = 0.08f),
                            border = BorderStroke(1.5.dp, BrightBlue.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = NavyBlue,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Huella",
                                            tint = VibrantOrange,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = "Ingresar con Huella Dactilar",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = NavyBlue
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Acceso rápido para ${if (correoGuardado.isNotEmpty()) correoGuardado else "tu cuenta"}",
                                            fontSize = 11.sp,
                                            color = NavyBlue.copy(alpha = 0.72f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = BrightBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    OutlinedTextField(
                        value = correo,
                        onValueChange = { correo = it; mensajeErrorValidacion = null },
                        label = { Text("Correo institucional") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = BrightBlue) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        colors = textFieldColors
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; mensajeErrorValidacion = null },
                        label = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = BrightBlue
                            )
                        },
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            val description = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"

                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description, tint = NavyBlue)
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors,
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = {
                            val correoLimpio = correo.trim().lowercase()
                            val passLimpia = password.trim()
                            if (correoLimpio.isBlank() || passLimpia.isBlank()) {
                                mensajeErrorValidacion = "Ingresa tu correo institucional y contraseña."
                                return@Button
                            }
                            correo = correoLimpio
                            viewModel.ejecutarLogin(correoLimpio, passLimpia)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantOrange),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        } else {
                            Text("INGRESAR", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 0.5.sp)
                        }
                    }
                } else {
                    Text(
                        text = if (esAlumnoEgresado) "Registro de Alumno Egresado" else "Registro de Estudiante Activo",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NavyBlue,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (esAlumnoEgresado)
                            "Registra tus datos de contacto como egresado UNACH"
                        else
                            "Completa tu expediente académico para participar en eventos",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Selector Activo / Egresado
                    if (configRegistro.permitirRegistroEgresados) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = LightBackground,
                            border = BorderStroke(1.dp, NavyBlue.copy(alpha = 0.06f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val bgActivo by animateColorAsState(
                                    targetValue = if (!esAlumnoEgresado) BrightBlue else Color.Transparent,
                                    animationSpec = tween(200),
                                    label = "bgActivo"
                                )
                                val bgEgresado by animateColorAsState(
                                    targetValue = if (esAlumnoEgresado) BrightBlue else Color.Transparent,
                                    animationSpec = tween(200),
                                    label = "bgEgresado"
                                )

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .clickable {
                                            esAlumnoEgresado = false
                                            mensajeErrorValidacion = null
                                        },
                                    color = bgActivo,
                                    shape = RoundedCornerShape(11.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.School,
                                            contentDescription = null,
                                            tint = if (!esAlumnoEgresado) Color.White else NavyBlue.copy(alpha = 0.75f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Estudiante Activo",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (!esAlumnoEgresado) Color.White else NavyBlue,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .clickable {
                                            esAlumnoEgresado = true
                                            mensajeErrorValidacion = null
                                        },
                                    color = bgEgresado,
                                    shape = RoundedCornerShape(11.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WorkspacePremium,
                                            contentDescription = null,
                                            tint = if (esAlumnoEgresado) Color.White else NavyBlue.copy(alpha = 0.75f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Alumno Egresado",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (esAlumnoEgresado) Color.White else NavyBlue,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    OutlinedTextField(
                        value = regNombre,
                        onValueChange = { if (it.length <= 120) regNombre = it; mensajeErrorValidacion = null },
                        label = { Text("Nombre completo") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = BrightBlue) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        colors = textFieldColors
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val dominiosHint = configRegistro.dominiosPermitidos
                        ?.takeIf { configRegistro.restringirDominioCorreo && !esAlumnoEgresado && it.isNotEmpty() }
                        ?.joinToString(", ") { "@${it.removePrefix("@")}" }

                    OutlinedTextField(
                        value = regCorreoPrincipal,
                        onValueChange = { regCorreoPrincipal = it; mensajeErrorValidacion = null },
                        label = {
                            Text(
                                when {
                                    esAlumnoEgresado -> "Correo electrónico principal"
                                    !dominiosHint.isNullOrBlank() -> "Correo institucional ($dominiosHint)"
                                    else -> "Correo institucional"
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = BrightBlue) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        colors = textFieldColors
                    )

                    // =========================================================================
                    // CAMPOS EXCLUSIVOS PARA ESTUDIANTE ACTIVO (SE OCULTAN EN ALUMNO EGRESADO)
                    // Matrícula, Licenciatura (Carrera) y Grado/Grupo NO se piden a egresados
                    // =========================================================================
                    AnimatedVisibility(
                        visible = !esAlumnoEgresado,
                        enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(220)),
                        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(180))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(12.dp))

                            // 1. Matrícula
                            val maxLenMat = configRegistro.longitudMaxMatricula.coerceIn(4, 30)
                            val minLenMat = configRegistro.longitudMinMatricula.coerceIn(1, maxLenMat)
                            OutlinedTextField(
                                value = regMatricula,
                                onValueChange = { nuevo ->
                                    val filtrado = if (configRegistro.soloDigitosMatricula) {
                                        nuevo.filter { it.isDigit() }
                                    } else {
                                        nuevo.filter { it.isLetterOrDigit() || it == '-' }
                                    }
                                    if (filtrado.length <= maxLenMat) {
                                        regMatricula = filtrado
                                        mensajeErrorValidacion = null
                                    }
                                },
                                label = {
                                    Text(
                                        if (configRegistro.requerirMatricula)
                                            "Matrícula ($minLenMat-$maxLenMat caracteres)"
                                        else
                                            "Matrícula (Opcional)"
                                    )
                                },
                                placeholder = { Text("Ej. 100016853") },
                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = BrightBlue) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (configRegistro.soloDigitosMatricula) KeyboardType.Number else KeyboardType.Ascii
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading,
                                colors = textFieldColors
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. Programa Educativo (Carrera / Licenciatura)
                            ExposedDropdownMenuBox(
                                expanded = menuCarreraExpandido,
                                onExpandedChange = { if (!isLoading) menuCarreraExpandido = !menuCarreraExpandido },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = carreraSeleccionada?.nombre ?: "Selecciona tu carrera",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Programa Educativo (Carrera)") },
                                    leadingIcon = { Icon(Icons.Default.School, contentDescription = null, tint = VibrantOrange) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuCarreraExpandido) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    enabled = !isLoading,
                                    colors = textFieldColors
                                )

                                ExposedDropdownMenu(
                                    expanded = menuCarreraExpandido,
                                    onDismissRequest = { menuCarreraExpandido = false },
                                    modifier = Modifier.background(Color.White)
                                ) {
                                    listaCarrerasCatalogo.forEach { opcionCarrera ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = opcionCarrera.nombre,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = NavyBlue,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (!opcionCarrera.facultadId.isNullOrBlank()) {
                                                        Text(
                                                            text = obtenerNombreFacultad(opcionCarrera.facultadId),
                                                            fontSize = 11.sp,
                                                            color = Color.Gray,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                carreraSeleccionada = opcionCarrera
                                                val gruposDeEstaCarrera = opcionCarrera.grupos.orEmpty()
                                                grupoSeleccionado = gruposDeEstaCarrera.firstOrNull() ?: ""
                                                menuCarreraExpandido = false
                                                mensajeErrorValidacion = null
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 3. Grado y Grupo (Semestre y Grupo)
                            val gruposDisponiblesCarrera = carreraSeleccionada?.grupos.orEmpty()
                            ExposedDropdownMenuBox(
                                expanded = menuGrupoExpandido,
                                onExpandedChange = {
                                    if (!isLoading && gruposDisponiblesCarrera.isNotEmpty()) {
                                        menuGrupoExpandido = !menuGrupoExpandido
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = if (grupoSeleccionado.isNotBlank()) formatearEtiquetaGrupo(grupoSeleccionado) else "Selecciona tu grado y grupo",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Grado y Grupo (Semestre y Grupo)") },
                                    leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null, tint = BrightBlue) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuGrupoExpandido) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    enabled = !isLoading,
                                    colors = textFieldColors
                                )

                                ExposedDropdownMenu(
                                    expanded = menuGrupoExpandido,
                                    onDismissRequest = { menuGrupoExpandido = false },
                                    modifier = Modifier.background(Color.White)
                                ) {
                                    gruposDisponiblesCarrera.forEach { codigoGrupo ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${formatearEtiquetaGrupo(codigoGrupo)} ($codigoGrupo)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = NavyBlue
                                                )
                                            },
                                            onClick = {
                                                grupoSeleccionado = codigoGrupo
                                                menuGrupoExpandido = false
                                                mensajeErrorValidacion = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // CAMPOS EXCLUSIVOS PARA ALUMNO EGRESADO (2 CORREOS Y 2 TELÉFONOS)
                    // =========================================================================
                    AnimatedVisibility(
                        visible = esAlumnoEgresado,
                        enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(220)),
                        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(180))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = BrightBlue.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, BrightBlue.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = BrightBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Como egresado solo necesitas registrar un correo alternativo y dos números de contacto.",
                                        fontSize = 11.sp,
                                        color = NavyBlue,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = regCorreoSecundario,
                                onValueChange = { regCorreoSecundario = it; mensajeErrorValidacion = null },
                                label = { Text("Correo alternativo (distinto al principal)") },
                                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = BrightBlue) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading,
                                colors = textFieldColors
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = regTelefonoPrincipal,
                                onValueChange = { nuevo ->
                                    val soloDigitos = nuevo.filter { it.isDigit() }
                                    if (soloDigitos.length <= 10) {
                                        regTelefonoPrincipal = soloDigitos
                                        mensajeErrorValidacion = null
                                    }
                                },
                                label = { Text("Teléfono principal (10 dígitos)") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = BrightBlue) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading,
                                colors = textFieldColors
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = regTelefonoSecundario,
                                onValueChange = { nuevo ->
                                    val soloDigitos = nuevo.filter { it.isDigit() }
                                    if (soloDigitos.length <= 10) {
                                        regTelefonoSecundario = soloDigitos
                                        mensajeErrorValidacion = null
                                    }
                                },
                                label = { Text("Teléfono secundario (10 dígitos)") },
                                leadingIcon = { Icon(Icons.Default.PhoneCallback, contentDescription = null, tint = BrightBlue) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading,
                                colors = textFieldColors
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = regPassword,
                        onValueChange = { regPassword = it; mensajeErrorValidacion = null },
                        label = { Text("Contraseña (mín. ${configRegistro.longitudMinPassword} caracteres)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = BrightBlue) },
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            val description = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description, tint = NavyBlue)
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        colors = textFieldColors
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Casilla de Aviso de Privacidad y Protección de Datos Personales UNACH (Auditoría 7.3.1)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (aceptoAvisoPrivacidad) BrightBlue.copy(alpha = 0.45f) else Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = aceptoAvisoPrivacidad,
                                onCheckedChange = {
                                    aceptoAvisoPrivacidad = it
                                    mensajeErrorValidacion = null
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = NavyBlue,
                                    uncheckedColor = Color(0xFF64748B)
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "He leído y acepto el Aviso de Privacidad y el tratamiento de mis datos académicos.",
                                    fontSize = 11.sp,
                                    color = NavyBlue,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Consultar Aviso de Privacidad y Derechos ARCO",
                                    fontSize = 11.sp,
                                    color = BrightBlue,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clickable { mostrarModalAvisoPrivacidad = true }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { validarYEnviarRegistro() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantOrange),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        } else {
                            Text("REGISTRARME", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 0.5.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "UNACH • ETDA",
            color = NavyBlue.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )

        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = {},
                shape = RoundedCornerShape(26.dp),
                containerColor = Color.White,
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrightBlue.copy(alpha = 0.12f),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = BrightBlue,
                                modifier = Modifier.padding(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Verificación en 2 pasos",
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyBlue,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Ingresa el código de 6 dígitos enviado a tu correo institucional para verificar tu identidad e iniciar sesión.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(bottom = 18.dp)
                        )
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = {
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    otpCode = it
                                }
                            },
                            placeholder = { Text("000000", color = Color.LightGray) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NavyBlue,
                                textAlign = TextAlign.Center,
                                letterSpacing = 8.sp
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrightBlue,
                                unfocusedBorderColor = BrightBlue.copy(alpha = 0.5f),
                                focusedContainerColor = BrightBlue.copy(alpha = 0.05f),
                                unfocusedContainerColor = BrightBlue.copy(alpha = 0.05f)
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.verificar2FA(correo.trim().lowercase(), otpCode.trim(), password) },
                        enabled = !isLoading && otpCode.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .padding(start = 8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
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
                        enabled = !isLoading,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
            )
        }

        if (mostrarModalOfrecerHuella) {
            AlertDialog(
                onDismissRequest = { mostrarModalOfrecerHuella = false; onNavigateToAlumno() },
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.White,
                title = {
                    Text("¿Activar Huella Dactilar?", fontWeight = FontWeight.ExtraBold, color = NavyBlue)
                },
                text = {
                    Text(
                        "¿Quieres vincular tu huella dactilar para iniciar sesión más rápido la próxima vez?",
                        color = Color(0xFF475569),
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val refreshActual = TokenManager.obtenerRefreshToken(context).orEmpty()
                            TokenManager.guardarTokenBiometrico(context, correo.trim().lowercase(), refreshActual)
                            password = ""
                            biometriaDisponible = TokenManager.tieneBiometriaHabilitada(context)
                            mostrarModalOfrecerHuella = false
                            onNavigateToAlumno()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantOrange)
                    ) { Text("Sí, activar", color = Color.White, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { password = ""; mostrarModalOfrecerHuella = false; onNavigateToAlumno() }) {
                        Text("Ahora no", color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        if (mostrarModalAvisoPrivacidad) {
            AlertDialog(
                onDismissRequest = { mostrarModalAvisoPrivacidad = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.White,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = NavyBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Aviso de Privacidad UNACH",
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyBlue,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "1. Responsable y Finalidades:",
                            fontWeight = FontWeight.Bold,
                            color = NavyBlue,
                            fontSize = 12.sp
                        )
                        Text(
                            "Campus Connect UNACH (Escuela de Tecnologías Digitales Aplicadas - ETDA) recaba tu nombre, correo institucional, matrícula, carrera y grupo exclusivamente para gestionar tu inscripción a eventos académicos, asignar asientos, validar tu asistencia y emitir constancias o insignias.",
                            color = Color(0xFF475569),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Text(
                            "2. Uso de Ubicación (Geocerca):",
                            fontWeight = FontWeight.Bold,
                            color = NavyBlue,
                            fontSize = 12.sp
                        )
                        Text(
                            "Cuando inicias el Paso 2/2 de validación de permanencia en un evento presencial, la aplicación verifica en tiempo real que te encuentres dentro del polígono de la sede universitaria. Tus coordenadas GPS exactas NO se almacenan en la base de datos; únicamente se registra el conteo de validaciones exitosas. Puedes detener el monitoreo o revocar el permiso en cualquier momento.",
                            color = Color(0xFF475569),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Text(
                            "3. Derechos ARCO (Acceso, Rectificación, Cancelación y Oposición):",
                            fontWeight = FontWeight.Bold,
                            color = NavyBlue,
                            fontSize = 12.sp
                        )
                        Text(
                            "Conforme a la Ley General de Protección de Datos Personales en Posesión de Sujetos Obligados, puedes solicitar el acceso, rectificación, cancelación u oposición al tratamiento de tus datos personales ante la coordinación de ETDA UNACH o desde tu perfil académico.",
                            color = Color(0xFF475569),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            aceptoAvisoPrivacidad = true
                            mostrarModalAvisoPrivacidad = false
                            mensajeErrorValidacion = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                    ) {
                        Text("Aceptar aviso", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarModalAvisoPrivacidad = false }) {
                        Text("Cerrar", color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    }
}