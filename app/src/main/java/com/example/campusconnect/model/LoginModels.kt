package com.example.campusconnect.model

import com.google.gson.annotations.SerializedName

// Este es el "sobre" que tú le envías a la API de Yasir
data class LoginRequest(
    @SerializedName("email")
    val correo: String,
    @SerializedName("password")
    val contrasena: String
)

// Este es el "sobre" que Yasir te responde
data class LoginResponse(
    @SerializedName("access_token")
    val token: String,
    @SerializedName("role")
    val rol: String
)

