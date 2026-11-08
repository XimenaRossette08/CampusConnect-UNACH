package com.example.campusconnect

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object TokenManager {
    private const val PREFS_NAME = "campus_connect_secure_prefs"
    private const val TOKEN_KEY = "access_token"
    private const val REFRESH_KEY = "refresh_token"
    private const val ROL_KEY = "user_role"
    private const val EMAIL_KEY = "user_email"
    private const val TIMESTAMP_KEY = "login_timestamp"

    // Remediación Auditoría A-4: NUNCA se guarda la contraseña del usuario.
    // En su lugar se guarda el refresh_token cifrado con una llave de AndroidKeyStore.
    private const val BIOMETRIC_ENABLED_KEY = "biometric_enabled"
    private const val BIOMETRIC_EMAIL_KEY = "biometric_email"
    private const val BIOMETRIC_REFRESH_CIPHERTEXT_KEY = "biometric_refresh_ciphertext"
    private const val BIOMETRIC_REFRESH_IV_KEY = "biometric_refresh_iv"
    private const val LEGACY_BIOMETRIC_PASSWORD_KEY = "biometric_password"

    private const val KEYSTORE_ALIAS_BIOMETRIA = "cc_unach_biometric_token_key_v2"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION_AES_GCM = "AES/GCM/NoPadding"

    // Prefijos de 2FA (desactivados para pedir el código siempre al iniciar sesión manual)
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

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Purgar inmediatamente cualquier contraseña heredada de versiones anteriores
        if (prefs.contains(LEGACY_BIOMETRIC_PASSWORD_KEY)) {
            prefs.edit().remove(LEGACY_BIOMETRIC_PASSWORD_KEY).apply()
        }
        return prefs
    }

    // Guarda la sesión activa una vez verificado el código de 6 dígitos o renovado el token
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
        val prefs = obtenerPreferencias(context)
        val editor = prefs.edit()
            .putString(TOKEN_KEY, token)
            .putString(REFRESH_KEY, refreshToken)
            .putString(EMAIL_KEY, emailKey)
            .putString(ROL_KEY, rol)
            .putLong(TIMESTAMP_KEY, ahora)
            .remove(LEGACY_BIOMETRIC_PASSWORD_KEY)
            .remove(PREFIX_2FA_TIME + emailKey)
            .remove(PREFIX_2FA_TOKEN + emailKey)
            .remove(PREFIX_2FA_REFRESH + emailKey)
            .remove(PREFIX_2FA_ROLE + emailKey)

        editor.apply()

        // Si el usuario ya tenía activada la huella para este correo, actualizamos su refresh_token cifrado
        val bioHabilitada = prefs.getBoolean(BIOMETRIC_ENABLED_KEY, false)
        val bioEmail = prefs.getString(BIOMETRIC_EMAIL_KEY, "") ?: ""
        if (bioHabilitada && bioEmail.equals(emailKey, ignoreCase = true) && refreshToken.isNotBlank()) {
            guardarTokenBiometrico(context, emailKey, refreshToken)
        }
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
        val emailSesion = obtenerPreferencias(context).getString(EMAIL_KEY, "") ?: ""
        if (emailSesion.isNotBlank()) return emailSesion
        return obtenerPreferencias(context).getString(BIOMETRIC_EMAIL_KEY, "") ?: ""
    }

    fun requiereRenovacion(context: Context): Boolean {
        val loginTime = obtenerPreferencias(context).getLong(TIMESTAMP_KEY, 0L)
        if (loginTime == 0L) return true
        val tiempoTranscurrido = System.currentTimeMillis() - loginTime
        return tiempoTranscurrido > LIMITE_VIGENCIA_MS
    }

    fun tiene2FAVigenteParaUsuario(context: Context, email: String): Boolean {
        return false
    }

    fun restaurarSesionCon2FAVigente(context: Context, email: String): Boolean {
        return false
    }

    // =========================================================================
    // BIOMETRÍA SEGURA CON ANDROID KEYSTORE + REFRESH TOKEN (SIN CONTRASEÑA)
    // =========================================================================
    private fun obtenerOCrearLlaveKeystore(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existente = keyStore.getKey(KEYSTORE_ALIAS_BIOMETRIA, null) as? SecretKey
        if (existente != null) return existente

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS_BIOMETRIA,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Crea un Cipher inicializado para descifrar el refresh_token biométrico y pasarlo
     * a BiometricPrompt.CryptoObject(cipher).
     */
    fun obtenerCipherDescifradoBiometrico(context: Context): Cipher? {
        return try {
            val prefs = obtenerPreferencias(context)
            val ivBase64 = prefs.getString(BIOMETRIC_REFRESH_IV_KEY, null) ?: return null
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, obtenerOCrearLlaveKeystore(), spec)
            cipher
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Guarda el refresh_token (jamás la contraseña) cifrado con AndroidKeyStore.
     */
    fun guardarTokenBiometrico(context: Context, email: String, refreshToken: String) {
        val tokenEffective = refreshToken.ifBlank { obtenerRefreshToken(context).orEmpty() }
        if (tokenEffective.isBlank()) return
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
            cipher.init(Cipher.ENCRYPT_MODE, obtenerOCrearLlaveKeystore())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(tokenEffective.toByteArray(Charsets.UTF_8))

            obtenerPreferencias(context).edit()
                .putBoolean(BIOMETRIC_ENABLED_KEY, true)
                .putString(BIOMETRIC_EMAIL_KEY, email.trim().lowercase())
                .putString(BIOMETRIC_REFRESH_CIPHERTEXT_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString(BIOMETRIC_REFRESH_IV_KEY, Base64.encodeToString(iv, Base64.NO_WRAP))
                .remove(LEGACY_BIOMETRIC_PASSWORD_KEY)
                .apply()
        } catch (_: Exception) {
        }
    }

    // Mantiene firma compatible pero ignora la contraseña y guarda únicamente el refresh_token actual
    @Suppress("UNUSED_PARAMETER")
    fun guardarCredencialesBiometricas(context: Context, email: String, passwordIgnorada: String = "") {
        val refreshActual = obtenerRefreshToken(context).orEmpty()
        guardarTokenBiometrico(context, email, refreshActual)
    }

    fun tieneBiometriaHabilitada(context: Context): Boolean {
        val prefs = obtenerPreferencias(context)
        val habilitada = prefs.getBoolean(BIOMETRIC_ENABLED_KEY, false)
        val email = prefs.getString(BIOMETRIC_EMAIL_KEY, null)
        val ciphertext = prefs.getString(BIOMETRIC_REFRESH_CIPHERTEXT_KEY, null)
        val iv = prefs.getString(BIOMETRIC_REFRESH_IV_KEY, null)
        return habilitada && !email.isNullOrBlank() && !ciphertext.isNullOrBlank() && !iv.isNullOrBlank()
    }

    /**
     * Devuelve (email, refreshTokenDescifrado) usando el Cipher autenticado por BiometricPrompt.
     */
    fun obtenerCredencialesBiometricas(context: Context, cipherAutenticado: Cipher? = null): Pair<String, String>? {
        if (!tieneBiometriaHabilitada(context)) return null
        return try {
            val prefs = obtenerPreferencias(context)
            val email = prefs.getString(BIOMETRIC_EMAIL_KEY, null) ?: return null
            val cipherBase64 = prefs.getString(BIOMETRIC_REFRESH_CIPHERTEXT_KEY, null) ?: return null
            val ciphertext = Base64.decode(cipherBase64, Base64.NO_WRAP)

            val cipher = cipherAutenticado ?: obtenerCipherDescifradoBiometrico(context) ?: return null
            val refreshBytes = cipher.doFinal(ciphertext)
            val refreshToken = String(refreshBytes, Charsets.UTF_8)
            if (refreshToken.isBlank()) null else Pair(email, refreshToken)
        } catch (_: Exception) {
            null
        }
    }

    fun desactivarBiometria(context: Context) {
        obtenerPreferencias(context).edit()
            .putBoolean(BIOMETRIC_ENABLED_KEY, false)
            .remove(BIOMETRIC_EMAIL_KEY)
            .remove(BIOMETRIC_REFRESH_CIPHERTEXT_KEY)
            .remove(BIOMETRIC_REFRESH_IV_KEY)
            .remove(LEGACY_BIOMETRIC_PASSWORD_KEY)
            .apply()
    }

    fun haySesionActiva(context: Context): Boolean {
        val token = obtenerToken(context)
        val email = obtenerPreferencias(context).getString(EMAIL_KEY, "") ?: ""
        return !token.isNullOrBlank() && email.isNotBlank() && !requiereRenovacion(context)
    }

    fun cerrarSesion(context: Context) {
        val prefs = obtenerPreferencias(context)
        val emailActual = (prefs.getString(EMAIL_KEY, "") ?: "").trim().lowercase()
        val editor = prefs.edit()
            .remove(TOKEN_KEY)
            .remove(REFRESH_KEY)
            .remove(ROL_KEY)
            .remove(TIMESTAMP_KEY)
            .remove(LEGACY_BIOMETRIC_PASSWORD_KEY)

        if (emailActual.isNotBlank()) {
            editor.remove(PREFIX_2FA_TIME + emailActual)
            editor.remove(PREFIX_2FA_TOKEN + emailActual)
            editor.remove(PREFIX_2FA_REFRESH + emailActual)
            editor.remove(PREFIX_2FA_ROLE + emailActual)
        }
        editor.apply()
    }
}