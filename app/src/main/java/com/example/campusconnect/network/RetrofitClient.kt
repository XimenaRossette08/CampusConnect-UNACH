package com.example.campusconnect.network

import android.content.Context
import android.content.Intent
import com.example.campusconnect.BuildConfig
import com.example.campusconnect.ComposeLoginActivity
import com.example.campusconnect.RenovarSesionActivity
import com.example.campusconnect.TokenManager
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://campusconnect-unach-api.onrender.com/"

    @Volatile
    private var instancia: CampusConnectApi? = null

    private val lockRefresh = Any()

    fun obtenerApi(context: Context): CampusConnectApi {
        return instancia ?: synchronized(this) {
            instancia ?: crearApi(context.applicationContext).also { instancia = it }
        }
    }

    private fun crearApi(context: Context): CampusConnectApi {
        val interceptorAutorizacion = Interceptor { chain ->
            val original = chain.request()
            val path = original.url.encodedPath

            // No adjuntar Authorization caducado en endpoints públicos de login/refresh
            if (path.endsWith("/auth/login") || path.endsWith("/auth/refresh") || path.endsWith("/auth/verificar-2fa")) {
                return@Interceptor chain.proceed(original)
            }

            val token = TokenManager.obtenerToken(context)
            val peticion = if (!token.isNullOrBlank()) {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                original
            }
            chain.proceed(peticion)
        }

        // Authenticator que renueva el access_token en segundo plano cuando vence (401 Unauthorized)
        val tokenAuthenticator = Authenticator { _: Route?, response: Response ->
            val path = response.request.url.encodedPath
            if (path.endsWith("/auth/login") ||
                path.endsWith("/auth/verificar-2fa") ||
                path.endsWith("/auth/refresh") ||
                contarReintentos(response) >= 2
            ) {
                return@Authenticator null
            }

            synchronized(lockRefresh) {
                val tokenActualEnPrefs = TokenManager.obtenerToken(context)
                val tokenUsadoEnPeticion = response.request.header("Authorization")
                    ?.removePrefix("Bearer ")
                    ?.trim()

                // Si otro hilo ya renovó el token mientras esperábamos, reintentar con el nuevo token
                if (!tokenActualEnPrefs.isNullOrBlank() && tokenActualEnPrefs != tokenUsadoEnPeticion) {
                    return@Authenticator response.request.newBuilder()
                        .header("Authorization", "Bearer $tokenActualEnPrefs")
                        .build()
                }

                val refreshToken = TokenManager.obtenerRefreshToken(context)
                if (refreshToken.isNullOrBlank()) {
                    redirigirARenovarSesion(context)
                    return@Authenticator null
                }

                try {
                    val clienteRefresh = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()

                    val apiRefresh = Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .client(clienteRefresh)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(CampusConnectApi::class.java)

                    val respRefresh = apiRefresh
                        .refrescarTokenSync(mapOf("refresh_token" to refreshToken))
                        .execute()

                    val body = respRefresh.body()
                    if (respRefresh.isSuccessful && body != null && body.accessToken.isNotBlank()) {
                        val emailGuardado = TokenManager.obtenerEmail(context)
                        val rolFinal = body.role.ifBlank { TokenManager.obtenerRol(context) ?: "Usuario Regular" }
                        val refreshFinal = body.refreshToken.ifBlank { refreshToken }

                        TokenManager.guardarSesion(
                            context = context,
                            token = body.accessToken,
                            refreshToken = refreshFinal,
                            email = emailGuardado,
                            rol = rolFinal,
                            actualizarMarca2FA = true
                        )

                        return@Authenticator response.request.newBuilder()
                            .header("Authorization", "Bearer ${body.accessToken}")
                            .build()
                    } else {
                        // El refresh_token también expiró o fue revocado: redirigir a RenovarSesionActivity
                        redirigirARenovarSesion(context)
                        return@Authenticator null
                    }
                } catch (_: Exception) {
                    // Fallo temporal de red: no cerrar la sesión del usuario
                    return@Authenticator null
                }
            }
        }

        // Remediación Auditoría A-4: Nunca registrar cuerpos (contraseñas, OTPs, tokens) en Logcat;
        // en Release se desactiva por completo (NONE) y en Debug solo nivel BASIC ocultando cabeceras sensibles.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

        val cliente = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(interceptorAutorizacion)
            .authenticator(tokenAuthenticator)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CampusConnectApi::class.java)
    }

    private fun contarReintentos(response: Response): Int {
        var resultado = 1
        var previa = response.priorResponse
        while (previa != null) {
            resultado++
            previa = previa.priorResponse
        }
        return resultado
    }

    private fun redirigirARenovarSesion(context: Context) {
        val emailGuardado = TokenManager.obtenerEmail(context)
        // Limpiamos el access_token vencido conservando el correo para RenovarSesionActivity
        TokenManager.guardarToken(context, "")

        val destino = if (emailGuardado.isNotBlank()) {
            RenovarSesionActivity::class.java
        } else {
            ComposeLoginActivity::class.java
        }

        val intent = Intent(context, destino).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}