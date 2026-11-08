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
import com.google.gson.reflect.TypeToken
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

    // Configurado directamente con el repositorio oficial donde GitHub Actions publica los Releases
    const val GITHUB_REPO = "XimenaRossette08/CampusConnect-UNACH"

    // Repositorios a consultar en orden de prioridad
    private val REPOSITORIOS_OFICIALES = listOf(
        "XimenaRossette08/CampusConnect-UNACH",
        "YasirEst/campusconnect-web"
    )

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
        return withContext(Dispatchers.IO) {
            val candidatos = if (repoPath.isNotBlank() && !repoPath.contains("TU_USUARIO")) {
                (listOf(repoPath) + REPOSITORIOS_OFICIALES).distinct()
            } else {
                REPOSITORIOS_OFICIALES
            }

            var mejorResultado: InfoActualizacion? = null

            for (repo in candidatos) {
                val release = obtenerUltimoReleaseConApk(repo) ?: continue
                val versionRemotaLimpia = release.tagName.trim().removePrefix("v").removePrefix("V")
                val versionActualLimpia = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V")

                val assetApk = release.assets.orEmpty().firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                } ?: continue

                val esSuperior = esVersionMayor(versionRemotaLimpia, versionActualLimpia)
                val info = InfoActualizacion(
                    hayNuevaVersion = esSuperior,
                    versionRemota = if (release.tagName.startsWith("v", ignoreCase = true)) release.tagName else "v${release.tagName}",
                    versionActual = "v$versionActualLimpia",
                    notasVersion = release.body?.takeIf { it.isNotBlank() }
                        ?: "Mejoras de estabilidad, rendimiento y nuevas funciones académicas.",
                    urlDescargaApk = assetApk.downloadUrl,
                    pesoBytes = assetApk.size
                )

                if (esSuperior) {
                    return@withContext info
                }
                if (mejorResultado == null) {
                    mejorResultado = info
                }
            }

            mejorResultado
        }
    }

    private fun obtenerUltimoReleaseConApk(repoPath: String): GitHubRelease? {
        val gson = Gson()

        // 1. Intentar primero con /releases/latest
        try {
            val reqLatest = Request.Builder()
                .url("https://api.github.com/repos/$repoPath/releases/latest")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "CampusConnect-Android-Updater")
                .build()

            client.newCall(reqLatest).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        val rel = gson.fromJson(bodyStr, GitHubRelease::class.java)
                        if (rel?.assets?.any { it.name.endsWith(".apk", ignoreCase = true) } == true) {
                            return rel
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Respaldo: consultar la lista /releases (incluye pre-releases o releases recientes)
        try {
            val reqList = Request.Builder()
                .url("https://api.github.com/repos/$repoPath/releases?per_page=5")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "CampusConnect-Android-Updater")
                .build()

            client.newCall(reqList).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        val tipoLista = object : TypeToken<List<GitHubRelease>>() {}.type
                        val lista: List<GitHubRelease> = gson.fromJson(bodyStr, tipoLista) ?: emptyList()
                        return lista.firstOrNull { rel ->
                            rel.assets?.any { it.name.endsWith(".apk", ignoreCase = true) } == true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Compara versiones semánticas (ej. "2.0.4" > "2.0.3").
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