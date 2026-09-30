package com.example.campusconnect

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object TokenManager {
    private const val PREFS_NAME = "seguridad_campus_prefs"
    private const val TOKEN_KEY = "jwt_token"
    private const val REFRESH_KEY = "jwt_refresh_token"
    private const val ROL_KEY = "usuario_rol"

    private fun obtenerPreferencias(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Guarda todo lo que deja el login: token de acceso, de refresco y el rol. */
    fun guardarSesion(context: Context, token: String, refreshToken: String, rol: String) {
        obtenerPreferencias(context).edit()
            .putString(TOKEN_KEY, token)
            .putString(REFRESH_KEY, refreshToken)
            .putString(ROL_KEY, rol)
            .apply()
    }

    // Se conserva por si algo más del proyecto ya llamaba a esta función.
    fun guardarToken(context: Context, token: String) {
        obtenerPreferencias(context).edit().putString(TOKEN_KEY, token).apply()
    }

    fun obtenerToken(context: Context): String? =
        obtenerPreferencias(context).getString(TOKEN_KEY, null)

    fun obtenerRefreshToken(context: Context): String? =
        obtenerPreferencias(context).getString(REFRESH_KEY, null)

    fun obtenerRol(context: Context): String? =
        obtenerPreferencias(context).getString(ROL_KEY, null)

    fun haySesionActiva(context: Context): Boolean = obtenerToken(context) != null

    fun cerrarSesion(context: Context) {
        obtenerPreferencias(context).edit().clear().apply()
    }
}