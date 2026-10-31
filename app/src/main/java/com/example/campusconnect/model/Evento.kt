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

// El backend (FastAPI) responde con alias camelCase: fechaHora, disponibleDesde, fechaFin, imagenUrl...
// Se aceptan también las variantes snake_case por compatibilidad.
// NOTA IMPORTANTE PARA GSON: Los booleanos opcionales deben ser Boolean? = null para que Gson
// no los inicialice en false cuando el JSON no incluya el campo.
data class Evento(
    val id: String,
    val titulo: String,
    val descripcion: String,
    @SerializedName(value = "fechaHora", alternate = ["fecha_hora"]) val fechaHora: String,
    @SerializedName(value = "disponibleDesde", alternate = ["disponible_desde"]) val disponibleDesde: String? = null,
    @SerializedName(value = "fechaFin", alternate = ["fecha_fin"]) val fechaFin: String? = null,
    @SerializedName(value = "disponibleAhora", alternate = ["disponible_ahora"]) val disponibleAhora: Boolean? = null,
    @SerializedName(value = "finalizado") val finalizado: Boolean? = null,
    val lugar: String,
    @SerializedName(value = "imagenUrl", alternate = ["imagen_url"]) val imagenUrl: String? = null,
    val categoria: String? = null,
    @SerializedName(value = "urlMaps", alternate = ["url_maps"]) val urlMaps: String? = null,
    val archivos: List<ArchivoEvento>? = null
) {
    /** Archivos que deben ver los alumnos (las evidencias son solo para el panel de administración). */
    val materiales: List<ArchivoEvento> get() = archivos.orEmpty().filter { it.tipo == "material" }

    /**
     * El servidor (GET /eventos/feed) ya filtra la vigencia en hora oficial de Chiapas.
     * En la app solo descartamos si el servidor marcó explícitamente finalizado=true o disponibleAhora=false.
     */
    fun estaVigenteEnTiempoReal(): Boolean {
        if (finalizado == true) return false
        if (disponibleAhora == false) return false
        return true
    }
}

/** Lo que se manda al crear un evento (POST /eventos/); sin id ni organizador. */
data class EventoCreateRequest(
    val titulo: String,
    val descripcion: String,
    val fechaHora: String,
    val disponibleDesde: String? = null,
    val fechaFin: String? = null,
    val lugar: String,
    val esGeneral: Boolean,
    val idFacultadDestino: String? = null,
    val idCarreraDestino: String? = null,
    val gruposDestino: List<String>? = null
)