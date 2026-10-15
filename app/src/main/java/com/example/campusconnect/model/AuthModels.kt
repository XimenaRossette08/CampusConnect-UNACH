package com.example.campusconnect.model

import com.google.gson.annotations.SerializedName

/**
 * Solicitud de registro para Estudiante Activo y Alumno Egresado.
 * Nota: LoginRequest y LoginResponse están en LoginModels.kt para evitar duplicidad.
 */
data class RegistroRequest(
    @SerializedName("nombre") val nombre: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("es_egresado") val esEgresado: Boolean = false,
    @SerializedName("email_secundario") val emailSecundario: String? = null,
    @SerializedName("telefono_principal") val telefonoPrincipal: String? = null,
    @SerializedName("telefono_secundario") val telefonoSecundario: String? = null
)
