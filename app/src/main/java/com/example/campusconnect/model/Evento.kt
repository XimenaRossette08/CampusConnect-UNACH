package com.example.campusconnect.model

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

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

data class Evento(
    val id: String,
    val titulo: String,
    val descripcion: String,
    @SerializedName(value = "fechaHora", alternate = ["fecha_hora"]) val fechaHora: String,
    @SerializedName(value = "disponibleDesde", alternate = ["disponible_desde"]) val disponibleDesde: String? = null,
    @SerializedName(value = "fechaFin", alternate = ["fecha_fin"]) val fechaFin: String? = null,
    @SerializedName(value = "disponibleAhora", alternate = ["disponible_ahora"]) val disponibleAhora: Boolean? = null,
    @SerializedName(value = "finalizado") val finalizado: Boolean? = null,
    @SerializedName(value = "inscrito") val inscrito: Boolean? = null,
    val lugar: String,
    @SerializedName(value = "imagenUrl", alternate = ["imagen_url"]) val imagenUrl: String? = null,
    val categoria: String? = null,
    @SerializedName(value = "urlMaps", alternate = ["url_maps"]) val urlMaps: String? = null,
    val archivos: List<ArchivoEvento>? = null,

    // ✅ VARIABLES AGREGADAS PARA LA BARRA DINÁMICA DE CUPOS
    @SerializedName(value = "capacidadTotal", alternate = ["capacidad_total"]) val capacidadTotal: Int? = null,
    @SerializedName(value = "asientosOcupados", alternate = ["asientos_ocupados"]) val asientosOcupados: Int? = null
) {
    /** Archivos que deben ver los alumnos (las evidencias son solo para el panel de administración). */
    val materiales: List<ArchivoEvento> get() = archivos.orEmpty().filter { it.tipo == "material" }

    fun estaVigenteEnTiempoReal(): Boolean {
        if (finalizado == true) return false
        if (disponibleAhora == false) return false

        val ahora = Instant.now()

        disponibleDesde?.takeIf { it.isNotBlank() }?.let { raw ->
            parsearInstanteMx(raw)?.let { disp ->
                if (ahora.isBefore(disp)) return false
            }
        }

        fechaFin?.takeIf { it.isNotBlank() }?.let { raw ->
            parsearInstanteMx(raw)?.let { fin ->
                if (!ahora.isBefore(fin)) return false
            }
        }

        return true
    }

    private fun parsearInstanteMx(raw: String): Instant? {
        val limpio = raw.trim()
        val zoneMx = ZoneId.of("America/Mexico_City")
        return try {
            OffsetDateTime.parse(limpio).toInstant()
        } catch (_: Exception) {
            try {
                Instant.parse(limpio)
            } catch (_: Exception) {
                try {
                    val norm = limpio.replace(" ", "T").take(19)
                    val padded = if (norm.length == 16) "$norm:00" else norm
                    LocalDateTime.parse(padded).atZone(zoneMx).toInstant()
                } catch (_: Exception) {
                    null
                }
            }
        }
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