package com.example.campusconnect.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.campusconnect.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class GitHubReleaseAsset(
    val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    val size: Long
)

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    val assets: List<GitHubReleaseAsset>?
)

data class InfoActualizacion(
    val hayNuevaVersion: Boolean,
    val versionRemota: String,
    val versionActual: String,
    val notasVersion: String,
    val urlDescargaApk: String,
    val pesoBytes: Long
)

object AppUpdateChecker {

    // ⚠️ IMPORTANTE: Reemplaza con tu usuario y nombre de repositorio en GitHub
    // Ejemplo: "juanperez/CampusConnect-UNACH"
    const val GITHUB_REPO = "TU_USUARIO/TU_REPOSITORIO"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Consulta la API pública de GitHub Releases para verificar si existe un Release con versión mayor.
     */
    suspend fun verificarActualizacion(repoPath: String = GITHUB_REPO): InfoActualizacion? {
        if (repoPath.isBlank() || repoPath.contains("TU_USUARIO")) {
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$repoPath/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .addHeader("User-Agent", "CampusConnect-Android-Updater")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null

                    val bodyString = response.body?.string() ?: return@withContext null
                    val release = Gson().fromJson(bodyString, GitHubRelease::class.java) ?: return@withContext null

                    val versionRemotaLimpia = release.tagName.trim().removePrefix("v").removePrefix("V")
                    val versionActualLimpia = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V")

                    val assetApk = release.assets.orEmpty().firstOrNull {
                        it.name.endsWith(".apk", ignoreCase = true)
                    } ?: return@withContext null

                    val esSuperior = esVersionMayor(versionRemotaLimpia, versionActualLimpia)

                    InfoActualizacion(
                        hayNuevaVersion = esSuperior,
                        versionRemota = if (release.tagName.startsWith("v", ignoreCase = true)) release.tagName else "v${release.tagName}",
                        versionActual = "v$versionActualLimpia",
                        notasVersion = release.body?.takeIf { it.isNotBlank() }
                            ?: "Mejoras de estabilidad, rendimiento y nuevas funciones académicas.",
                        urlDescargaApk = assetApk.downloadUrl,
                        pesoBytes = assetApk.size
                    )
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Compara versiones semánticas (ej. "1.2.0" > "1.1.0").
     */
    private fun esVersionMayor(versionRemota: String, versionActual: String): Boolean {
        try {
            val partesRemotas = versionRemota.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val partesActuales = versionActual.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }

            val maxLong = maxOf(partesRemotas.size, partesActuales.size)
            for (i in 0 until maxLong) {
                val r = partesRemotas.getOrElse(i) { 0 }
                val a = partesActuales.getOrElse(i) { 0 }
                if (r > a) return true
                if (r < a) return false
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Descarga el archivo APK en el almacenamiento caché de la aplicación y reporta el progreso.
     */
    suspend fun descargarApk(
        context: Context,
        urlApk: String,
        alProgresar: (porcentaje: Int, descargadoMb: Float, totalMb: Float) -> Unit
    ): File? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(urlApk)
                    .addHeader("User-Agent", "CampusConnect-Android-Updater")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null

                    val body = response.body ?: return@withContext null
                    val totalBytes = body.contentLength()
                    val totalMb = if (totalBytes > 0) totalBytes.toFloat() / (1024f * 1024f) else 0f

                    val dirDescargas = File(context.cacheDir, "actualizaciones").apply { mkdirs() }
                    val archivoApk = File(dirDescargas, "campus_connect_update.apk")
                    if (archivoApk.exists()) archivoApk.delete()

                    body.byteStream().use { inputStream ->
                        FileOutputStream(archivoApk).use { outputStream ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesLeidos: Int
                            var bytesAcumulados = 0L

                            while (inputStream.read(buffer).also { bytesLeidos = it } != -1) {
                                outputStream.write(buffer, 0, bytesLeidos)
                                bytesAcumulados += bytesLeidos
                                val descargadoMb = bytesAcumulados.toFloat() / (1024f * 1024f)
                                val progreso = if (totalBytes > 0) {
                                    ((bytesAcumulados * 100) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    50
                                }
                                withContext(Dispatchers.Main) {
                                    alProgresar(progreso, descargadoMb, totalMb)
                                }
                            }
                            outputStream.flush()
                        }
                    }
                    archivoApk
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Lanza el instalador de paquetes de Android usando FileProvider.
     */
    fun instalarApk(context: Context, archivoApk: File) {
        if (!archivoApk.exists()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intentPermiso = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intentPermiso)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            archivoApk
        )

        val intentInstalar = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intentInstalar)
    }
}