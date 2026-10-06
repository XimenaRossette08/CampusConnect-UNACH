package com.example.campusconnect.model

import com.google.gson.annotations.SerializedName

data class Evento(
    val id: String,
    val titulo: String,
    val descripcion: String,
    @SerializedName("fecha_hora") val fechaHora: String,
    val lugar: String,
    @SerializedName("imagen_url") val imagenUrl: String? = null
)

/** Lo que se manda al crear un evento (POST /eventos/); sin id ni organizador. */
data class EventoCreateRequest(
    val titulo: String,
    val descripcion: String,
    val fechaHora: String,
    val lugar: String,
    val esGeneral: Boolean,
    val idFacultadDestino: String? = null,
    val idCarreraDestino: String? = null,
    val gruposDestino: List<String>? = null
)