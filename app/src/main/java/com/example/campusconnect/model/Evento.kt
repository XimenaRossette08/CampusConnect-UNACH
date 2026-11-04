package com.example.campusconnect.model

import com.google.gson.annotations.SerializedName
import org.json.JSONObject
import java.time.Duration
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

    // Variables sincronizadas con el backend y el Catálogo de Auditorios para la barra y mapa de asientos tipo cine
    @SerializedName(value = "capacidadTotal", alternate = ["capacidad_total"]) val capacidadTotal: Int? = null,
    @SerializedName(value = "asientosOcupados", alternate = ["asientos_ocupados", "totalRegistrados", "total_registrados"]) val asientosOcupados: Int? = null,
    @SerializedName(value = "miAsiento", alternate = ["mi_asiento"]) val miAsiento: String? = null,
    @SerializedName(value = "asientosTomados", alternate = ["asientos_tomados"]) val asientosTomados: List<String>? = null,
    @SerializedName(value = "asientosReservadosAdmin", alternate = ["asientos_reservados_admin"]) val asientosReservadosAdmin: List<String>? = null,

    // Código QR oficial generado para el evento y estado de doble validación (1/2 QR + 2/2 GPS)
    @SerializedName(value = "qrCodigo", alternate = ["qr_codigo"]) val qrCodigo: String? = null,
    @SerializedName(value = "validacionQr", alternate = ["validacion_qr"]) val validacionQr: Boolean? = null,
    @SerializedName(value = "validacionGps", alternate = ["validacion_gps"]) val validacionGps: Boolean? = null,
    @SerializedName(value = "pasosValidacion", alternate = ["pasos_validacion"]) val pasosValidacion: Int? = null
) {
    /** Archivos que deben ver los alumnos (las evidencias son solo para el panel de administración). */
    val materiales: List<ArchivoEvento> get() = archivos.orEmpty().filter { it.tipo == "material" }

    /** Extrae metadatos embebidos `<!--CCMETA:{...}-->` sincronizados desde el panel web si existen. */
    private val metaJsonObj: JSONObject?
        get() {
            val idxIni = descripcion.indexOf("<!--CCMETA:")
            if (idxIni < 0) return null
            val idxFin = descripcion.indexOf("-->", idxIni)
            if (idxFin < 0) return null
            val crudo = descripcion.substring(idxIni + 11, idxFin).trim()
            return try {
                JSONObject(crudo)
            } catch (_: Exception) {
                null
            }
        }

    /** Descripción limpia para mostrar al usuario sin el bloque técnico de sincronización. */
    val descripcionLimpia: String
        get() = descripcion.replace(Regex("\\s*<!--CCMETA:[\\s\\S]*?-->"), "").trim()

    val disponibleDesdeEfectivo: String?
        get() = disponibleDesde?.takeIf { it.isNotBlank() }
            ?: metaJsonObj?.optString("disp", "")?.takeIf { it.isNotBlank() }

    val fechaFinEfectiva: String?
        get() = fechaFin?.takeIf { it.isNotBlank() }
            ?: metaJsonObj?.optString("fin", "")?.takeIf { it.isNotBlank() }

    val asientosReservadosAdminEfectivos: List<String>
        get() {
            val base = asientosReservadosAdmin.orEmpty().toMutableSet()
            metaJsonObj?.optJSONObject("res")?.let { resObj ->
                val keys = resObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next().trim().uppercase()
                    if (k.isNotBlank()) base.add(k)
                }
            }
            metaJsonObj?.optJSONArray("resList")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val k = arr.optString(i, "").trim().uppercase()
                    if (k.isNotBlank()) base.add(k)
                }
            }
            return base.sorted()
        }

    val asientosTomadosEfectivos: List<String>
        get() {
            val base = asientosTomados.orEmpty().toMutableSet()
            base.addAll(asientosReservadosAdminEfectivos)
            metaJsonObj?.optJSONObject("seats")?.let { seatsObj ->
                val keys = seatsObj.keys()
                while (keys.hasNext()) {
                    val emailKey = keys.next()
                    val valor = seatsObj.opt(emailKey)
                    val cod = when (valor) {
                        is JSONObject -> valor.optString("asiento", "")
                        is String -> valor
                        else -> ""
                    }.trim().uppercase()
                    if (cod.isNotBlank()) base.add(cod)
                }
            }
            if (!miAsiento.isNullOrBlank()) {
                base.add(miAsiento.trim().uppercase())
            }
            return base.sorted()
        }

    private val metaCapacidad: Int?
        get() = metaJsonObj?.optInt("cap", 0)?.takeIf { it > 0 }

    private val metaOcupados: Int
        get() = metaJsonObj?.optInt("ocup", 0)?.coerceAtLeast(0) ?: 0

    val cupoMaximo: Int
        get() = (capacidadTotal ?: metaCapacidad ?: 100).coerceAtLeast(1)

    val lugaresOcupados: Int
        get() {
            val minimoPorInscripcionPropia = if (inscrito == true || !miAsiento.isNullOrBlank()) 1 else 0
            return maxOf(
                asientosOcupados ?: 0,
                metaOcupados,
                asientosTomadosEfectivos.size,
                minimoPorInscripcionPropia
            ).coerceIn(0, cupoMaximo)
        }

    val lugaresDisponibles: Int get() = (cupoMaximo - lugaresOcupados).coerceAtLeast(0)
    val porcentajeOcupacion: Float get() = (lugaresOcupados.toFloat() / cupoMaximo.toFloat()).coerceIn(0f, 1f)
    val cupoAgotado: Boolean get() = lugaresDisponibles == 0

    fun estaVigenteEnTiempoReal(ahoraMillis: Long = System.currentTimeMillis()): Boolean {
        if (finalizado == true) return false
        if (disponibleAhora == false) return false

        val ahora = Instant.ofEpochMilli(ahoraMillis)

        disponibleDesdeEfectivo?.let { raw ->
            parsearInstanteMx(raw)?.let { disp ->
                if (ahora.isBefore(disp)) return false
            }
        }

        val finInstant = fechaFinEfectiva?.let { parsearInstanteMx(it) }
            ?: parsearInstanteMx(fechaHora)?.plus(Duration.ofHours(4))

        if (finInstant != null && !ahora.isBefore(finInstant)) {
            return false
        }

        return true
    }

    private fun parsearInstanteMx(raw: String): Instant? {
        val limpio = raw.trim()
        if (limpio.isEmpty()) return null
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
    val categoria: String? = null,
    val esGeneral: Boolean,
    val idFacultadDestino: String? = null,
    val idCarreraDestino: String? = null,
    val gruposDestino: List<String>? = null
)