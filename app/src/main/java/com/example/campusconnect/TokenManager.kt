package com.example.campusconnect

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object TokenManager {
    private const val PREFS_NAME = "campus_connect_secure_prefs"
    private const val TOKEN_KEY = "access_token"
    private const val REFRESH_KEY = "refresh_token"
    private const val ROL_KEY = "user_role"
    private const val EMAIL_KEY = "user_email"
    private const val TIMESTAMP_KEY = "login_timestamp"

    // Mantenemos tu configuración de alta seguridad moderna (MasterKey)
    private fun obtenerPreferencias(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Guardamos los datos MÁS la marca de tiempo exacta de hoy
    fun guardarSesion(context: Context, token: String, refreshToken: String, email: String, rol: String) {
        obtenerPreferencias(context).edit()
            .putString(TOKEN_KEY, token)
            .putString(REFRESH_KEY, refreshToken)
            .putString(EMAIL_KEY, email)
            .putString(ROL_KEY, rol)
            .putLong(TIMESTAMP_KEY, System.currentTimeMillis())
            .apply()
    }

    // Se conserva por si algo más del proyecto ya llamaba a esta función
    fun guardarToken(context: Context, token: String) {
        obtenerPreferencias(context).edit().putString(TOKEN_KEY, token).apply()
    }

    fun obtenerToken(context: Context): String? =
        obtenerPreferencias(context).getString(TOKEN_KEY, null)

    fun obtenerRefreshToken(context: Context): String? =
        obtenerPreferencias(context).getString(REFRESH_KEY, null)

    fun obtenerRol(context: Context): String? =
        obtenerPreferencias(context).getString(ROL_KEY, null)

    fun obtenerEmail(context: Context): String {
        return obtenerPreferencias(context).getString(EMAIL_KEY, "alumno@unach.mx") ?: "alumno@unach.mx"
    }

    // El cerebro del tiempo: ¿Ya pasaron 30 días?
    fun requiereRenovacion(context: Context): Boolean {
        val loginTime = obtenerPreferencias(context).getLong(TIMESTAMP_KEY, 0L)
        // Si es 0, significa que nunca ha iniciado sesión
        if (loginTime == 0L) return true

        val tiempoTranscurrido = System.currentTimeMillis() - loginTime

        // Matemáticas: 30 días * 24 horas * 60 minutos * 60 segundos * 1000 milisegundos
        val limite30Dias = 30L * 24L * 60L * 60L * 1000L

        return tiempoTranscurrido > limite30Dias
    }

    // Luz verde para dejarlo pasar directo
    fun haySesionActiva(context: Context): Boolean {
        return obtenerToken(context) != null && !requiereRenovacion(context)
    }

    // Botón de emergencia para borrar todo (Cerrar sesión)
    fun cerrarSesion(context: Context) {
        obtenerPreferencias(context).edit().clear().apply()
    }
}