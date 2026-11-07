package com.example.campusconnect.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
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
import kotlinx.coroutines.launch
import java.util.Locale

private val NavyBlue = Color(0xFF122652)
private val VibrantOrange = Color(0xFFF57C00)
private val BrightBlue = Color(0xFF0093FF)

@Composable
fun DialogoActualizacionFlexible(
    info: InfoActualizacion,
    onDescartar: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var descargando by remember { mutableStateOf(false) }
    var progresoPorcentaje by remember { mutableIntStateOf(0) }
    var descargadoMb by remember { mutableFloatStateOf(0f) }
    var totalMb by remember { mutableFloatStateOf(0f) }
    var errorDescarga by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!descargando) onDescartar() },
        shape = RoundedCornerShape(26.dp),
        containerColor = Color.White,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = VibrantOrange.copy(alpha = 0.15f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (descargando) Icons.Default.CloudDownload else Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = VibrantOrange,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "¡Nueva versión disponible!",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NavyBlue,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = BrightBlue.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "${info.versionActual}  ➔  ${info.versionRemota}",
                        color = BrightBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (descargando) {
                    Text(
                        text = "Descargando actualización...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyBlue
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progresoPorcentaje / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = VibrantOrange,
                        trackColor = Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$progresoPorcentaje%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = VibrantOrange
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f MB / %.1f MB", descargadoMb, totalMb),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                } else {
                    Text(
                        text = "Novedades de esta versión:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NavyBlue
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = info.notasVersion,
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                errorDescarga?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = err, color = Color(0xFFDC2626), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            if (!descargando) {
                Button(
                    onClick = {
                        descargando = true
                        errorDescarga = null
                        coroutineScope.launch {
                            val apkFile = AppUpdateChecker.descargarApk(
                                context = context,
                                urlApk = info.urlDescargaApk,
                                alProgresar = { porc, desc, tot ->
                                    progresoPorcentaje = porc
                                    descargadoMb = desc
                                    totalMb = tot
                                }
                            )
                            descargando = false
                            if (apkFile != null) {
                                AppUpdateChecker.instalarApk(context, apkFile)
                                onDescartar()
                            } else {
                                errorDescarga = "Error al descargar el instalador. Revisa tu conexión e intenta de nuevo."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VibrantOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Actualizar ahora", fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        },
        dismissButton = {
            if (!descargando) {
                TextButton(onClick = onDescartar) {
                    Text("Más tarde", color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}