package com.example.campusconnect

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("campus_connect_prefs", Context.MODE_PRIVATE)
    }

    // --- MÉTODOS DE INSTANCIA ---
    fun guardarSesion(accessToken: String, refreshToken: String? = null, role: String? = null) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("user_role", role)
            .apply()
    }

    fun guardarToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
    }

    fun obtenerToken(): String? {
        return prefs.getString("access_token", null)
    }

    fun obtenerRefreshToken(): String? {
        return prefs.getString("refresh_token", null)
    }

    fun obtenerRole(): String? {
        return prefs.getString("user_role", null)
    }

    fun requiereRenovacion(): Boolean {
        val token = obtenerToken()
        return token.isNullOrEmpty()
    }

    fun limpiarToken() {
        prefs.edit().clear().apply()
    }

    // --- MÉTODOS ESTÁTICOS / COMPANION OBJECT ---
    // Permite que RetrofitClient y ComposeLoginActivity llamen directamente a TokenManager.<metodo>(context)
    companion object {
        fun guardarSesion(
            context: Context,
            token: String,
            refreshToken: String? = null,
            rol: String? = null
        ) {
            TokenManager(context).guardarSesion(accessToken = token, refreshToken = refreshToken, role = rol)
        }

        fun obtenerToken(context: Context): String? {
            return TokenManager(context).obtenerToken()
        }

        fun obtenerRefreshToken(context: Context): String? {
            return TokenManager(context).obtenerRefreshToken()
        }

        fun obtenerRole(context: Context): String? {
            return TokenManager(context).obtenerRole()
        }

        fun requiereRenovacion(context: Context): Boolean {
            return TokenManager(context).requiereRenovacion()
        }

        fun limpiarToken(context: Context) {
            TokenManager(context).limpiarToken()
        }
    }
}