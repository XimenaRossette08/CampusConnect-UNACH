package com.example.campusconnect.model

import com.google.gson.annotations.SerializedName

/**
 * Solicitud de registro para Estudiante Activo y Alumno Egresado,
 * incluyendo Matrícula, Programa Educativo (Carrera), Grado/Grupo y Facultad.
 */
data class RegistroRequest(
    @SerializedName("nombre") val nombre: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("es_egresado") val esEgresado: Boolean = false,
    @SerializedName("email_secundario") val emailSecundario: String? = null,
    @SerializedName("telefono_principal") val telefonoPrincipal: String? = null,
    @SerializedName("telefono_secundario") val telefonoSecundario: String? = null,
    @SerializedName("matricula") val matricula: String? = null,
    @SerializedName("carrera") val carrera: String? = null,
    @SerializedName("semestre_grupo") val semestreGrupo: String? = null,
    @SerializedName("id_facultad") val idFacultad: String? = "ETDA"
)

data class FacultadCatalogoDto(
    @SerializedName("id") val id: String = "ETDA",
    @SerializedName("nombre") val nombre: String = "Escuela de Tecnologías Digitales Aplicadas (ETDA)"
)

data class CarreraCatalogoDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("nombre") val nombre: String = "",
    @SerializedName("facultadId") val facultadId: String? = "ETDA",
    @SerializedName("grupos") val grupos: List<String>? = emptyList()
)

data class DatosInstitucionalesDto(
    @SerializedName("escuela") val escuela: String? = null,
    @SerializedName("modalidadSede") val modalidadSede: String? = null,
    @SerializedName("cicloEscolar") val cicloEscolar: String? = null
)

/**
 * Opciones y políticas configuradas por el Administrador en el Panel Web
 * para controlar los campos y validaciones al crear una cuenta de usuario.
 */
data class ConfiguracionRegistroDto(
    @SerializedName("permitirRegistroEgresados") val permitirRegistroEgresados: Boolean = true,
    @SerializedName("requerirMatricula") val requerirMatricula: Boolean = true,
    @SerializedName("longitudMinMatricula") val longitudMinMatricula: Int = 6,
    @SerializedName("longitudMaxMatricula") val longitudMaxMatricula: Int = 15,
    @SerializedName("soloDigitosMatricula") val soloDigitosMatricula: Boolean = false,
    @SerializedName("requerirCarreraGrupo") val requerirCarreraGrupo: Boolean = true,
    @SerializedName("validarContraCatalogo") val validarContraCatalogo: Boolean = true,
    @SerializedName("restringirDominioCorreo") val restringirDominioCorreo: Boolean = false,
    @SerializedName("dominiosPermitidos") val dominiosPermitidos: List<String>? = listOf("unach.mx", "alumnos.unach.mx"),
    @SerializedName("longitudMinPassword") val longitudMinPassword: Int = 8
)

data class CatalogoFacultadResponse(
    @SerializedName("facultadActiva") val facultadActiva: String? = "ETDA",
    @SerializedName("facultades") val facultades: List<FacultadCatalogoDto>? = emptyList(),
    @SerializedName("carreras") val carreras: List<CarreraCatalogoDto>? = emptyList(),
    @SerializedName("grupos") val grupos: List<String>? = emptyList(),
    @SerializedName("datosInstitucionales") val datosInstitucionales: DatosInstitucionalesDto? = null,
    @SerializedName("configuracionRegistro") val configuracionRegistro: ConfiguracionRegistroDto? = null
)

data class ExpedienteAlumnoResponse(
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("rol") val rol: String? = null,
    @SerializedName("es_egresado") val esEgresado: Boolean? = false,
    @SerializedName("email_secundario") val emailSecundario: String? = null,
    @SerializedName("telefono_principal") val telefonoPrincipal: String? = null,
    @SerializedName("telefono_secundario") val telefonoSecundario: String? = null,
    @SerializedName("matricula") val matricula: String? = null,
    @SerializedName("carrera") val carrera: String? = null,
    @SerializedName("semestre_grupo") val semestreGrupo: String? = null,
    @SerializedName("id_facultad") val idFacultad: String? = null,
    @SerializedName("escuela") val escuela: String? = null,
    @SerializedName("modalidad_sede") val modalidadSede: String? = null,
    @SerializedName("ciclo_escolar") val cicloEscolar: String? = null,
    @SerializedName("tiene_2fa_activo") val tiene2faActivo: Boolean? = true
)