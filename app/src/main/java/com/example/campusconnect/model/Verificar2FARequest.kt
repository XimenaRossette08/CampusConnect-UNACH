package com.example.campusconnect.model

import com.google.gson.annotations.SerializedName

data class Verificar2FARequest(
    @SerializedName("email") val email: String,
    @SerializedName("codigo") val codigo: String
)