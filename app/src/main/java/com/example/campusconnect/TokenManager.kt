package com.example.campusconnect

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object TokenManager {
    private const val PREFS_NAME = "seguridad_campus_prefs"
    private const val TOKEN_KEY = "jwt_token"

    fun guardarToken(context: Context, token: String) {
        // 1. Creamos la llave maestra (usando la sintaxis de la versión 1.0.0)
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        // 2. Creamos el archivo de preferencias (el orden correcto para 1.0.0)
        val sharedPreferences = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // 3. Guardamos el token bajo llave
        sharedPreferences.edit().putString(TOKEN_KEY, token).apply()
    }
}