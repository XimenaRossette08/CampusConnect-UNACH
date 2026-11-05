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
    @SerializedName(value = "esGeneral", alternate = ["es_general"]) val esGeneral: Boolean? = true,
    @SerializedName(value = "idFacultadDestino", alternate = ["id_facultad_destino"]) val idFacultadDestino: String? = null,
    @SerializedName(value = "idCarreraDestino", alternate = ["id_carrera_destino"]) val idCarreraDestino: String? = null,
    @SerializedName(value = "gruposDestino", alternate = ["grupos_destino"]) val gruposDestino: List<String>? = null,
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

    /** Extrae metadatos embebidos `<!--CCMETA:{...}-->` sincronizados entre la App y el Panel Web. */
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

    /**
     * Extrae la malla poligonal de 4 esquinas `(lat, lon)` sincronizada en tiempo real
     * en el bloque `<!--CCMETA:{...}-->` cuando el administrador delimita el auditorio en la web.
     */
    val mallaPoligonoEfectiva: List<Pair<Double, Double>>
        get() {
            val arr = metaJsonObj?.optJSONArray("malla") ?: return emptyList()
            val puntos = mutableListOf<Pair<Double, Double>>()
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                when (item) {
                    is org.json.JSONArray -> {
                        if (item.length() >= 2) {
                            val lat = item.optDouble(0, Double.NaN)
                            val lon = item.optDouble(1, Double.NaN)
                            if (!lat.isNaN() && !lon.isNaN()) puntos.add(lat to lon)
                        }
                    }
                    is JSONObject -> {
                        val lat = item.optDouble("lat", Double.NaN)
                        val lon = item.optDouble("lon", Double.NaN)
                        if (!lat.isNaN() && !lon.isNaN()) puntos.add(lat to lon)
                    }
                }
            }
            return if (puntos.size >= 3) puntos else emptyList()
        }

    /** Margen de tolerancia en paredes del edificio en metros (por defecto 12 m). */
    val toleranciaMallaEfectiva: Float
        get() = metaJsonObj?.optDouble("tol", 12.0)?.toFloat()?.coerceIn(2f, 80f) ?: 12f

    /** Devuelve el asiento exacto registrado para `emailUsuario` dentro de CCMETA (ej. "K-04"). */
    fun obtenerAsientoDeUsuarioEnMeta(emailUsuario: String?): String? {
        if (emailUsuario.isNullOrBlank()) return null
        val seatsObj = metaJsonObj?.optJSONObject("seats") ?: return null
        val clave = emailUsuario.trim().lowercase()
        val item = seatsObj.opt(clave) ?: return null
        return when (item) {
            is JSONObject -> item.optString("asiento", "").trim().uppercase().takeIf { it.isNotBlank() }
            is String -> item.trim().uppercase().takeIf { it.isNotBlank() }
            else -> null
        }
    }

    /** Devuelve el timestamp (ms) en que se actualizó por última vez el asiento de `emailUsuario` en CCMETA. */
    fun obtenerTimestampAsientoEnMeta(emailUsuario: String?): Long {
        if (emailUsuario.isNullOrBlank()) return 0L
        val seatsObj = metaJsonObj?.optJSONObject("seats") ?: return 0L
        val item = seatsObj.optJSONObject(emailUsuario.trim().lowercase()) ?: return 0L
        return item.optLong("ts", 0L)
    }

    /** Verifica si el administrador liberó explícitamente el asiento del usuario desde el Panel Web. */
    fun obtenerTimestampLiberacionAdmin(emailUsuario: String?, asiento: String?): Long {
        val libObj = metaJsonObj?.optJSONObject("liberados") ?: return 0L
        var ts = 0L
        if (!emailUsuario.isNullOrBlank()) {
            ts = maxOf(ts, libObj.optLong(emailUsuario.trim().lowercase(), 0L))
        }
        if (!asiento.isNullOrBlank()) {
            ts = maxOf(ts, libObj.optLong(asiento.trim().uppercase(), 0L))
        }
        return ts
    }

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
            val base = mutableSetOf<String>()
            base.addAll(asientosReservadosAdminEfectivos)
            val seatsObj = metaJsonObj?.optJSONObject("seats")
            if (seatsObj != null && seatsObj.length() > 0) {
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
            } else {
                base.addAll(asientosTomados.orEmpty())
            }
            if (!miAsiento.isNullOrBlank()) {
                base.add(miAsiento.trim().uppercase())
            }
            return base.sorted()
        }

    private val metaCapacidad: Int?
        get() = metaJsonObj?.optInt("cap", 0)?.takeIf { it > 0 }

    private val metaOcupados: Int?
        get() = if (metaJsonObj?.has("ocup") == true) metaJsonObj?.optInt("ocup", 0)?.coerceAtLeast(0) else null

    val cupoMaximo: Int
        get() = (capacidadTotal ?: metaCapacidad ?: 100).coerceAtLeast(1)

    val lugaresOcupados: Int
        get() {
            val minimoPorInscripcionPropia = if (inscrito == true || !miAsiento.isNullOrBlank()) 1 else 0
            val ocupadosMeta = metaOcupados
            if (metaJsonObj != null && (metaJsonObj?.has("seats") == true || metaJsonObj?.has("res") == true || ocupadosMeta != null)) {
                return maxOf(
                    ocupadosMeta ?: 0,
                    asientosTomadosEfectivos.size,
                    minimoPorInscripcionPropia
                ).coerceIn(0, cupoMaximo)
            }
            return maxOf(
                asientosOcupados ?: 0,
                asientosTomadosEfectivos.size,
                minimoPorInscripcionPropia
            ).coerceIn(0, cupoMaximo)
        }

    val lugaresDisponibles: Int get() = (cupoMaximo - lugaresOcupados).coerceAtLeast(0)
    val porcentajeOcupacion: Float get() = (lugaresOcupados.toFloat() / cupoMaximo.toFloat()).coerceIn(0f, 1f)
    val cupoAgotado: Boolean get() = lugaresDisponibles == 0

    /**
     * Construye la nueva cadena `descripcion` con el bloque `<!--CCMETA:{...}-->` actualizado
     * y el mapa para `PUT /eventos/{id}`.
     */
    fun construirDescripcionYPayloadMeta(
        emailUsuario: String,
        nombreUsuario: String,
        asiento: String?,
        qrValidado: Boolean,
        gpsValidado: Boolean,
        asistio: Boolean,
        timestampMs: Long = System.currentTimeMillis()
    ): Pair<String, Map<String, Any?>> {
        val actualMeta = metaJsonObj ?: JSONObject()
        val resObj = actualMeta.optJSONObject("res") ?: JSONObject()
        val seatsObj = actualMeta.optJSONObject("seats") ?: JSONObject()
        val liberadosObj = actualMeta.optJSONObject("liberados") ?: JSONObject()

        val emailKey = emailUsuario.trim().lowercase()
        val prevEntry = seatsObj.optJSONObject(emailKey)
        val prevCod = prevEntry?.optString("asiento", "")?.trim()?.uppercase().orEmpty()

        if (asiento.isNullOrBlank()) {
            seatsObj.remove(emailKey)
            liberadosObj.put(emailKey, timestampMs)
            if (prevCod.isNotBlank()) liberadosObj.put(prevCod, timestampMs)
        } else {
            val codLimpio = asiento.trim().uppercase()
            liberadosObj.remove(emailKey)
            liberadosObj.remove(codLimpio)
            if (prevCod.isNotBlank() && prevCod != codLimpio) {
                liberadosObj.put(prevCod, timestampMs)
            }
            // Si algún registro fantasma tenía ese mismo código de asiento bajo otro alias del mismo usuario, limpiarlo
            val kIter = seatsObj.keys().asSequence().toList()
            for (k in kIter) {
                val v = seatsObj.opt(k)
                val c = when (v) {
                    is JSONObject -> v.optString("asiento", "")
                    is String -> v
                    else -> ""
                }.trim().uppercase()
                if (c == codLimpio && k != emailKey) {
                    seatsObj.remove(k)
                }
            }
            val itemSeat = JSONObject().apply {
                put("asiento", codLimpio)
                put("nombre", nombreUsuario.ifBlank { "Alumno UNACH" })
                put("email", emailKey)
                put("qrValidado", qrValidado || asistio)
                put("gpsValidado", gpsValidado || asistio)
                put("asistio", asistio)
                put("fechaApartado", Instant.ofEpochMilli(timestampMs).toString())
                put("ts", timestampMs)
            }
            seatsObj.put(emailKey, itemSeat)
        }

        val codigosUnicos = mutableSetOf<String>()
        val rk = resObj.keys()
        while (rk.hasNext()) codigosUnicos.add(rk.next().trim().uppercase())
        val sk = seatsObj.keys()
        while (sk.hasNext()) {
            val k = sk.next()
            val v = seatsObj.opt(k)
            val c = when (v) {
                is JSONObject -> v.optString("asiento", "")
                is String -> v
                else -> ""
            }.trim().uppercase()
            if (c.isNotBlank()) codigosUnicos.add(c)
        }

        val nuevoMeta = JSONObject().apply {
            disponibleDesdeEfectivo?.let { put("disp", it) }
            fechaFinEfectiva?.let { put("fin", it) }
            put("cap", cupoMaximo)
            put("ocup", codigosUnicos.size)
            put("res", resObj)
            put("seats", seatsObj)
            put("liberados", liberadosObj)
            actualMeta.optJSONArray("malla")?.let { put("malla", it) }
            if (actualMeta.has("tol")) {
                put("tol", actualMeta.optDouble("tol", 12.0))
            }
        }

        val baseLimpia = descripcionLimpia.ifBlank { titulo }
        val nuevaDescripcion = "$baseLimpia\n<!--CCMETA:$nuevoMeta-->"

        val payload = mutableMapOf<String, Any?>(
            "titulo" to titulo,
            "descripcion" to nuevaDescripcion,
            "fechaHora" to fechaHora,
            "lugar" to lugar,
            "esGeneral" to (esGeneral ?: true)
        )
        fechaFinEfectiva?.let { payload["fechaFin"] = it }
        disponibleDesdeEfectivo?.let { payload["disponibleDesde"] = it }
        categoria?.takeIf { it.isNotBlank() }?.let { payload["categoria"] = it }
        idFacultadDestino?.takeIf { it.isNotBlank() }?.let { payload["idFacultadDestino"] = it }
        idCarreraDestino?.takeIf { it.isNotBlank() }?.let { payload["idCarreraDestino"] = it }
        gruposDestino?.takeIf { it.isNotEmpty() }?.let { payload["gruposDestino"] = it }

        return nuevaDescripcion to payload
    }

    fun construirPayloadSincronizacionMeta(
        emailUsuario: String,
        nombreUsuario: String,
        asiento: String?,
        qrValidado: Boolean,
        gpsValidado: Boolean,
        asistio: Boolean
    ): Map<String, Any?> {
        return construirDescripcionYPayloadMeta(
            emailUsuario = emailUsuario,
            nombreUsuario = nombreUsuario,
            asiento = asiento,
            qrValidado = qrValidado,
            gpsValidado = gpsValidado,
            asistio = asistio
        ).second
    }

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