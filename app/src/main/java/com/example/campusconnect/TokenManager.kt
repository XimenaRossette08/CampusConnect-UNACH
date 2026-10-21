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

    // Prefijos de 2FA (desactivados para pedir el código siempre al iniciar sesión)
    private const val PREFIX_2FA_TIME = "trusted_2fa_time_"
    private const val PREFIX_2FA_TOKEN = "trusted_2fa_token_"
    private const val PREFIX_2FA_REFRESH = "trusted_2fa_refresh_"
    private const val PREFIX_2FA_ROLE = "trusted_2fa_role_"

    // =========================================================================
    // CONFIGURACIÓN DE TIEMPO DE SESIÓN ACTIVA
    // =========================================================================
    const val MODO_PRUEBAS_TIEMPO = false

    private const val DURACION_PRUEBA_MS = 1L * 60L * 1000L
    private const val DURACION_UN_MES_MS = 30L * 24L * 60L * 60L * 1000L

    val LIMITE_VIGENCIA_MS: Long
        get() = if (MODO_PRUEBAS_TIEMPO) DURACION_PRUEBA_MS else DURACION_UN_MES_MS

    fun obtenerDescripcionVigencia(): String {
        return "En cada inicio de sesión"
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

    // Guarda la sesión activa una vez verificado el código de 6 dígitos
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
        obtenerPreferencias(context).edit()
            .putString(TOKEN_KEY, token)
            .putString(REFRESH_KEY, refreshToken)
            .putString(EMAIL_KEY, emailKey)
            .putString(ROL_KEY, rol)
            .putLong(TIMESTAMP_KEY, ahora)
            .remove(PREFIX_2FA_TIME + emailKey)
            .remove(PREFIX_2FA_TOKEN + emailKey)
            .remove(PREFIX_2FA_REFRESH + emailKey)
            .remove(PREFIX_2FA_ROLE + emailKey)
            .apply()
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

    fun requiereRenovacion(context: Context): Boolean {
        val loginTime = obtenerPreferencias(context).getLong(TIMESTAMP_KEY, 0L)
        if (loginTime == 0L) return true
        val tiempoTranscurrido = System.currentTimeMillis() - loginTime
        return tiempoTranscurrido > LIMITE_VIGENCIA_MS
    }

    // Devuelve siempre false para exigir el código de 6 dígitos en cada inicio de sesión
    fun tiene2FAVigenteParaUsuario(context: Context, email: String): Boolean {
        return false
    }

    // Devuelve siempre false para que nunca se salte el código OTP de 6 dígitos al iniciar sesión
    fun restaurarSesionCon2FAVigente(context: Context, email: String): Boolean {
        return false
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

    // Cierra la sesión activa (al volver a iniciar sesión pedirá el código de 6 dígitos)
    fun cerrarSesion(context: Context) {
        val prefs = obtenerPreferencias(context)
        val emailActual = (prefs.getString(EMAIL_KEY, "") ?: "").trim().lowercase()
        val editor = prefs.edit()
            .remove(TOKEN_KEY)
            .remove(REFRESH_KEY)
            .remove(ROL_KEY)
            .remove(TIMESTAMP_KEY)

        if (emailActual.isNotBlank()) {
            editor.remove(PREFIX_2FA_TIME + emailActual)
            editor.remove(PREFIX_2FA_TOKEN + emailActual)
            editor.remove(PREFIX_2FA_REFRESH + emailActual)
            editor.remove(PREFIX_2FA_ROLE + emailActual)
        }
        editor.apply()
    }
}