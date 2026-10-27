package com.example.campusconnect.model

import com.google.gson.annotations.SerializedName

/** Archivo adjunto a un evento (flyer, PDF, etc.) que el organizador subió desde el panel web. */
data class ArchivoEvento(
    val id: String,
    val tipo: String,          // "material" (lo ven los alumnos) o "evidencia"
    val nombre: String,
    val mime: String,
    val tamano: Long = 0,
    val url: String,           // URL absoluta; el servidor la arma con el host que usó la app
    val esImagen: Boolean = false
)

// El backend (FastAPI) responde con alias camelCase: fechaHora, imagenUrl... Se aceptan también
// las variantes snake_case por compatibilidad.
data class Evento(
    val id: String,
    val titulo: String,
    val descripcion: String,
    @SerializedName(value = "fechaHora", alternate = ["fecha_hora"]) val fechaHora: String,
    val lugar: String,
    @SerializedName(value = "imagenUrl", alternate = ["imagen_url"]) val imagenUrl: String? = null,
    val categoria: String? = null,
    // NUEVO CAMPO PARA GOOGLE MAPS
    @SerializedName(value = "urlMaps", alternate = ["url_maps"]) val urlMaps: String? = null,
    val archivos: List<ArchivoEvento>? = null
) {
    /** Archivos que deben ver los alumnos (las evidencias son solo para el panel de administración). */
    val materiales: List<ArchivoEvento> get() = archivos.orEmpty().filter { it.tipo == "material" }
}

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