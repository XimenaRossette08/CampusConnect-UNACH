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

    // Claves para Biometría (Huella Dactilar) — persisten al cerrar sesión normal
    private const val BIOMETRIC_ENABLED_KEY = "biometric_enabled"
    private const val BIOMETRIC_EMAIL_KEY = "biometric_email"
    private const val BIOMETRIC_PASSWORD_KEY = "biometric_password"

    // Prefijos para recordar la verificación 2FA durante 1 mes por usuario en este dispositivo
    private const val PREFIX_2FA_TIME = "trusted_2fa_time_"
    private const val PREFIX_2FA_TOKEN = "trusted_2fa_token_"
    private const val PREFIX_2FA_REFRESH = "trusted_2fa_refresh_"
    private const val PREFIX_2FA_ROLE = "trusted_2fa_role_"

    // =========================================================================
    // CONFIGURACIÓN DE TIEMPO DE SESIÓN Y 2FA (PRODUCCIÓN VS PRUEBAS)
    // =========================================================================
    // Pon MODO_PRUEBAS_TIEMPO = true para probar cómo expira el 2FA en pocos
    // segundos/minutos. Ponlo en false para dejar el rango oficial de 1 MES (30 días).
    const val MODO_PRUEBAS_TIEMPO = false

    // Duración cuando MODO_PRUEBAS_TIEMPO = true (Ej: 1 minuto = 60,000 ms; o 30 seg = 30,000 ms)
    private const val DURACION_PRUEBA_MS = 1L * 60L * 1000L

    // Duración oficial cuando MODO_PRUEBAS_TIEMPO = false (30 días = 1 mes)
    private const val DURACION_UN_MES_MS = 30L * 24L * 60L * 60L * 1000L

    // Límite activo según el modo seleccionado
    val LIMITE_VIGENCIA_MS: Long
        get() = if (MODO_PRUEBAS_TIEMPO) DURACION_PRUEBA_MS else DURACION_UN_MES_MS

    fun obtenerDescripcionVigencia(): String {
        return if (MODO_PRUEBAS_TIEMPO) {
            val segs = DURACION_PRUEBA_MS / 1000L
            "Modo Pruebas (${segs}s)"
        } else {
            "30 días (1 mes)"
        }
    }

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

    // Guarda la sesión activa y registra la marca de tiempo de validación 2FA por 30 días
    fun guardarSesion(
        context: Context,
        token: String,
        refreshToken: String,
        email: String,
        rol: String,
        actualizarMarca2FA: Boolean = true
    ) {
        val emailKey = email.trim().lowercase()
        val ahora = System.currentTimeMillis()
        val editor = obtenerPreferencias(context).edit()
            .putString(TOKEN_KEY, token)
            .putString(REFRESH_KEY, refreshToken)
            .putString(EMAIL_KEY, emailKey)
            .putString(ROL_KEY, rol)
            .putLong(TIMESTAMP_KEY, ahora)

        if (actualizarMarca2FA) {
            editor.putLong(PREFIX_2FA_TIME + emailKey, ahora)
        }
        // Guardamos también el token vigente asociado a este usuario para no pedir 2FA en 30 días
        editor.putString(PREFIX_2FA_TOKEN + emailKey, token)
        editor.putString(PREFIX_2FA_REFRESH + emailKey, refreshToken)
        editor.putString(PREFIX_2FA_ROLE + emailKey, rol)
        editor.apply()
    }

    fun guardarToken(context: Context, token: String) {
        obtenerPreferencias(context).edit().putString(TOKEN_KEY, token).apply()
    }

    fun obtenerToken(context: Context): String? {
        val t = obtenerPreferencias(context).getString(TOKEN_KEY, null)
        return if (t.isNullOrBlank()) null else t
    }

    fun obtenerRefreshToken(context: Context): String? =
        obtenerPreferencias(context).getString(REFRESH_KEY, null)

    fun obtenerRol(context: Context): String? =
        obtenerPreferencias(context).getString(ROL_KEY, null)

    fun obtenerEmail(context: Context): String {
        return obtenerPreferencias(context).getString(EMAIL_KEY, "") ?: ""
    }

    // ¿Ya pasó el tiempo límite (30 días en producción, o 1 min en modo pruebas)?
    fun requiereRenovacion(context: Context): Boolean {
        val loginTime = obtenerPreferencias(context).getLong(TIMESTAMP_KEY, 0L)
        if (loginTime == 0L) return true
        val tiempoTranscurrido = System.currentTimeMillis() - loginTime
        return tiempoTranscurrido > LIMITE_VIGENCIA_MS
    }

    // Verifica si un correo específico ya validó su código 2FA dentro del último mes (o tiempo de prueba)
    fun tiene2FAVigenteParaUsuario(context: Context, email: String): Boolean {
        val emailKey = email.trim().lowercase()
        val prefs = obtenerPreferencias(context)
        val ultimo2FA = prefs.getLong(PREFIX_2FA_TIME + emailKey, 0L)
        val tokenGuardado = prefs.getString(PREFIX_2FA_TOKEN + emailKey, null)
        if (ultimo2FA == 0L || tokenGuardado.isNullOrBlank()) return false

        val transcurrido = System.currentTimeMillis() - ultimo2FA
        return transcurrido <= LIMITE_VIGENCIA_MS
    }

    // Restaura la sesión de un usuario cuyo 2FA de 30 días sigue vigente sin pedir nuevo código OTP
    fun restaurarSesionCon2FAVigente(context: Context, email: String): Boolean {
        if (!tiene2FAVigenteParaUsuario(context, email)) return false
        val emailKey = email.trim().lowercase()
        val prefs = obtenerPreferencias(context)
        val token = prefs.getString(PREFIX_2FA_TOKEN + emailKey, null) ?: return false
        val refresh = prefs.getString(PREFIX_2FA_REFRESH + emailKey, "") ?: ""
        val rol = prefs.getString(PREFIX_2FA_ROLE + emailKey, "Estudiante") ?: "Estudiante"

        prefs.edit()
            .putString(TOKEN_KEY, token)
            .putString(REFRESH_KEY, refresh)
            .putString(EMAIL_KEY, emailKey)
            .putString(ROL_KEY, rol)
            .putLong(TIMESTAMP_KEY, prefs.getLong(PREFIX_2FA_TIME + emailKey, System.currentTimeMillis()))
            .apply()
        return true
    }

    // =========================================================================
    // FUNCIONES PARA HUELLA DACTILAR (BIOMETRÍA)
    // =========================================================================
    fun guardarCredencialesBiometricas(context: Context, email: String, password: String) {
        obtenerPreferencias(context).edit()
            .putBoolean(BIOMETRIC_ENABLED_KEY, true)
            .putString(BIOMETRIC_EMAIL_KEY, email.trim())
            .putString(BIOMETRIC_PASSWORD_KEY, password)
            .apply()
    }

    fun tieneBiometriaHabilitada(context: Context): Boolean {
        val prefs = obtenerPreferencias(context)
        val habilitada = prefs.getBoolean(BIOMETRIC_ENABLED_KEY, false)
        val email = prefs.getString(BIOMETRIC_EMAIL_KEY, null)
        val pass = prefs.getString(BIOMETRIC_PASSWORD_KEY, null)
        return habilitada && !email.isNullOrBlank() && !pass.isNullOrBlank()
    }

    fun obtenerCredencialesBiometricas(context: Context): Pair<String, String>? {
        if (!tieneBiometriaHabilitada(context)) return null
        val prefs = obtenerPreferencias(context)
        val email = prefs.getString(BIOMETRIC_EMAIL_KEY, null) ?: return null
        val pass = prefs.getString(BIOMETRIC_PASSWORD_KEY, null) ?: return null
        return Pair(email, pass)
    }

    fun desactivarBiometria(context: Context) {
        obtenerPreferencias(context).edit()
            .putBoolean(BIOMETRIC_ENABLED_KEY, false)
            .remove(BIOMETRIC_EMAIL_KEY)
            .remove(BIOMETRIC_PASSWORD_KEY)
            .apply()
    }

    // Verifica si existe una sesión activa y vigente para dejar pasar al usuario
    fun haySesionActiva(context: Context): Boolean {
        val token = obtenerToken(context)
        val email = obtenerEmail(context)
        return !token.isNullOrBlank() && email.isNotBlank() && !requiereRenovacion(context)
    }

    // Cierra la sesión activa pero CONSERVA la configuración de huella dactilar y el registro de 30 días de 2FA
    fun cerrarSesion(context: Context) {
        obtenerPreferencias(context).edit()
            .remove(TOKEN_KEY)
            .remove(REFRESH_KEY)
            .remove(ROL_KEY)
            .remove(TIMESTAMP_KEY)
            .apply()
    }
}
