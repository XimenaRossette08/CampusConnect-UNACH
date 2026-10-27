package com.example.campusconnect.ui

import android.Manifest
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.campusconnect.service.ServicioUbicacionAsistencia
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

// Paleta ETDA
private val EtdaNavy = Color(0xFF122652)
private val EtdaOrange = Color(0xFFF57C00)
private val EtdaSuccess = Color(0xFF10B981)

@Composable
fun EscanerQrScreen(onVolver: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var tienePermisos by remember { mutableStateOf(false) }
    var qrDetectado by remember { mutableStateOf<String?>(null) }

    // Solicitar permisos de Cámara y GPS al mismo tiempo
    val launcherPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        tienePermisos = permisos[Manifest.permission.CAMERA] == true &&
                permisos[Manifest.permission.ACCESS_FINE_LOCATION] == true
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

        val todosOtorgados = permisosRequeridos.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (todosOtorgados) {
            tienePermisos = true
        } else {
            launcherPermisos.launch(permisosRequeridos.toTypedArray())
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (tienePermisos && qrDetectado == null) {
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
                                if (qrDetectado == null) {
                                    qrDetectado = codigo

                                    // 🚀 INICIAR EL SERVICIO GPS AL DETECTAR EL QR
                                    val intentServicio = Intent(context, ServicioUbicacionAsistencia::class.java).apply {
                                        putExtra("EVENTO_ID", codigo)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intentServicio)
                                    } else {
                                        context.startService(intentServicio)
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

            // Diseño de la mira del escáner
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .align(Alignment.Center)
                    .border(3.dp, EtdaOrange, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            )

            Text(
                text = "Apunta al código QR del evento",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else if (!tienePermisos) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = EtdaOrange)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Solicitando permisos...", color = Color.White)
            }
        }

        // Pantalla de éxito tras escanear
        if (qrDetectado != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = EtdaSuccess.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = EtdaSuccess, modifier = Modifier.padding(16.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("¡Código capturado!", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = EtdaNavy)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tu ubicación está siendo verificada en segundo plano para validar tu asistencia. No cierres la aplicación.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onVolver,
                            colors = ButtonDefaults.buttonColors(containerColor = EtdaNavy),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Volver al evento", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = onVolver,
            modifier = Modifier
                .padding(top = 48.dp, start = 16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
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