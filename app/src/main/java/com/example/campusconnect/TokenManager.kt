package com.example.campusconnect

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {

    // 1. Mantenemos tu configuración de alta seguridad (Criptografía)
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "campus_connect_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // 2. Guardamos los datos MÁS la marca de tiempo exacta de hoy
    fun guardarSesion(token: String, refreshToken: String, email: String, role: String) {
        val editor = sharedPreferences.edit()
        editor.putString("access_token", token)
        editor.putString("refresh_token", refreshToken)
        editor.putString("user_email", email)
        editor.putString("user_role", role)
        // Guardamos el momento exacto en el que inició sesión
        editor.putLong("login_timestamp", System.currentTimeMillis())
        editor.apply()
    }

    fun obtenerToken(): String? {
        return sharedPreferences.getString("access_token", null)
    }

    fun obtenerEmail(): String {
        // Si por alguna razón no lo encuentra, devuelve un correo por defecto
        return sharedPreferences.getString("user_email", "alumno@unach.mx") ?: "alumno@unach.mx"
    }

    // 3. El cerebro del tiempo: ¿Ya pasaron 30 días?
    fun requiereRenovacion(): Boolean {
        val loginTime = sharedPreferences.getLong("login_timestamp", 0L)
        // Si es 0, significa que nunca ha iniciado sesión
        if (loginTime == 0L) return true

        val tiempoTranscurrido = System.currentTimeMillis() - loginTime

        // Matemáticas: 30 días * 24 horas * 60 minutos * 60 segundos * 1000 milisegundos
        val limite30Dias = 30L * 24L * 60L * 60L * 1000L

        return tiempoTranscurrido > limite30Dias
    }

    // 4. Luz verde para dejarlo pasar directo
    fun tieneSesionActiva(): Boolean {
        return obtenerToken() != null && !requiereRenovacion()
    }

    // 5. Botón de emergencia para borrar todo (Cerrar sesión)
    fun cerrarSesion() {
        sharedPreferences.edit().clear().apply()
    }
}