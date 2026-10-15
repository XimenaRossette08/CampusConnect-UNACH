package com.example.campusconnect.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("email")
    val correo: String,
    @SerializedName("password")
    val contrasena: String,
    @SerializedName("dispositivo_confiable")
    val dispositivoConfiable: Boolean = false
)

data class LoginResponse(
    @SerializedName("requiere_2fa")
    val requiere2fa: Boolean = true,
    @SerializedName("access_token")
    val accessToken: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("role")
    val role: String? = null,
    @SerializedName("mensaje")
    val mensaje: String? = null
)
