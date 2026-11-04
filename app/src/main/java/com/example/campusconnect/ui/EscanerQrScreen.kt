package com.example.campusconnect.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.campusconnect.model.Evento
import com.example.campusconnect.service.ServicioUbicacionAsistencia
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

// Paleta Oficial ETDA - UNACH Intacta
private val EtdaNavy = Color(0xFF122652)
private val EtdaBlue = Color(0xFF0093FF)
private val EtdaOrange = Color(0xFFF57C00)
private val EtdaSuccess = Color(0xFF10B981)

/**
 * Extrae el ID del evento desde el código QR oficial generado por la página web
 * (soporta formato "CCUNACH|EVENTO|<uuid>" o UUID directo).
 */
fun extraerEventoIdDesdeQr(rawQr: String): String {
    val limpio = rawQr.trim()
    if (limpio.startsWith("CCUNACH|EVENTO|", ignoreCase = true)) {
        return limpio.substringAfterLast("|").trim()
    }
    val uuidRegex = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    uuidRegex.find(limpio)?.value?.let { return it }
    return limpio
}

@Composable
fun EscanerQrScreen(
    eventoObjetivo: Evento? = null,
    onQrValidadoConExito: (eventoId: String, codigoCrudo: String) -> Unit = { _, _ -> },
    onVolver: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var tienePermisos by remember { mutableStateOf(false) }
    var qrDetectado by remember { mutableStateOf<String?>(null) }
    var idEventoExtraido by remember { mutableStateOf<String?>(null) }
    var errorQrInvalido by remember { mutableStateOf<String?>(null) }

    val pulsoMira = rememberInfiniteTransition(label = "pulsoMira")
    val escalaMira by pulsoMira.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "escalaMira"
    )

    val launcherPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        tienePermisos = permisos[Manifest.permission.CAMERA] == true &&
                (permisos[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                 permisos[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
    }

    LaunchedEffect(Unit) {
        val permisosRequeridos = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisosRequeridos.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val camaraOk = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val gpsOk = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (camaraOk && gpsOk) {
            tienePermisos = true
        } else {
            launcherPermisos.launch(permisosRequeridos.toTypedArray())
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (tienePermisos && qrDetectado == null && errorQrInvalido == null) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val executor = Executors.newSingleThreadExecutor()
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val opcionesEscaneo = BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                        val escaner = BarcodeScanning.getClient(opcionesEscaneo)

                        val analizador = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        analizador.setAnalyzer(executor) { imageProxy ->
                            procesarFotograma(imageProxy, escaner) { codigo ->
                                if (qrDetectado == null && errorQrInvalido == null) {
                                    val extraido = extraerEventoIdDesdeQr(codigo)
                                    val idEsperado = eventoObjetivo?.id

                                    if (!idEsperado.isNullOrBlank() && !extraido.equals(idEsperado, ignoreCase = true)) {
                                        errorQrInvalido = "El código QR escaneado no corresponde al evento \"${eventoObjetivo.titulo}\". Escanea el QR oficial generado en la página web para este evento."
                                    } else {
                                        val idFinal = idEsperado ?: extraido
                                        idEventoExtraido = idFinal
                                        qrDetectado = codigo

                                        // Persistir Validación 1/2 inmediatamente antes de arrancar el servicio GPS
                                        val prefsNotif = context.getSharedPreferences("cc_notificaciones_prefs", Context.MODE_PRIVATE)
                                        val setQr1 = (prefsNotif.getStringSet("eventos_qr_1de2_set", emptySet()) ?: emptySet()).toMutableSet()
                                        setQr1.add(idFinal)
                                        prefsNotif.edit().putStringSet("eventos_qr_1de2_set", setQr1).apply()

                                        onQrValidadoConExito(idFinal, codigo)

                                        val intentServicio = Intent(context, ServicioUbicacionAsistencia::class.java).apply {
                                            action = ServicioUbicacionAsistencia.ACCION_INICIAR_RASTREO_QR
                                            putExtra("EVENTO_ID", idFinal)
                                            putExtra("EVENTO_TITULO", eventoObjetivo?.titulo ?: "Evento Académico UNACH")
                                            putExtra("EVENTO_CATEGORIA", eventoObjetivo?.categoria ?: "Académicos")
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(intentServicio)
                                        } else {
                                            context.startService(intentServicio)
                                        }
                                    }
                                }
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analizador
                            )
                        } catch (e: Exception) {
                            Log.e("EscanerQr", "Fallo al iniciar cámara", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Encabezado superior con el nombre del evento que se está validando
            if (eventoObjetivo != null) {
                Surface(
                    color = EtdaNavy.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, EtdaBlue.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 104.dp, start = 24.dp, end = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "VALIDACIÓN 1 DE 2 • REGISTRO POR QR",
                            color = EtdaOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = eventoObjetivo.titulo,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Mira animada del escáner
            Box(
                modifier = Modifier
                    .size(256.dp)
                    .scale(escalaMira)
                    .align(Alignment.Center)
                    .border(3.dp, EtdaOrange, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )

            Surface(
                color = EtdaNavy.copy(alpha = 0.90f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, EtdaOrange.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp, start = 20.dp, end = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = EtdaOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Apunta al código QR oficial del evento en la web",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (!tienePermisos) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = EtdaOrange)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Se requieren permisos de Cámara y Ubicación GPS para realizar las 2 validaciones de asistencia.",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (errorQrInvalido != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier = Modifier.padding(28.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFEE2E2),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Código QR no coincide",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            color = EtdaNavy
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorQrInvalido.orEmpty(),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { errorQrInvalido = null },
                            colors = ButtonDefaults.buttonColors(containerColor = EtdaOrange),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text("Intentar de nuevo", fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }
        }

        if (qrDetectado != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.84f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier = Modifier.padding(28.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EtdaSuccess.copy(alpha = 0.15f),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EtdaSuccess,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = EtdaOrange.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                "VALIDACIÓN 1 DE 2 COMPLETADA",
                                color = EtdaOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "¡QR Oficial Verificado!",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            color = EtdaNavy,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ahora el sistema comprobará que te encuentres dentro del recinto en Campus I UNACH por GPS (Validación 2 de 2) para acreditarte +1 en ${eventoObjetivo?.categoria ?: "tu insignia"}.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Button(
                            onClick = onVolver,
                            colors = ButtonDefaults.buttonColors(containerColor = EtdaNavy),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Ver mi progreso en el evento", fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = onVolver,
            modifier = Modifier
                .padding(top = 48.dp, start = 16.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar escáner", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun procesarFotograma(
    imageProxy: ImageProxy,
    escaner: BarcodeScanner,
    onQrDetectado: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val imagen = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        escaner.process(imagen)
            .addOnSuccessListener { codigos ->
                for (codigo in codigos) {
                    codigo.rawValue?.let { valor ->
                        onQrDetectado(valor)
                        break
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}