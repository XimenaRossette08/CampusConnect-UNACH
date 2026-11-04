package com.example.campusconnect.network

import com.example.campusconnect.model.CatalogoFacultadResponse
import com.example.campusconnect.model.Evento
import com.example.campusconnect.model.EventoCreateRequest
import com.example.campusconnect.model.ExpedienteAlumnoResponse
import com.example.campusconnect.model.LoginRequest
import com.example.campusconnect.model.LoginResponse
import com.example.campusconnect.model.MapaAsientosEventoResponse
import com.example.campusconnect.model.MisRecompensasResponse
import com.example.campusconnect.model.RegistroRequest
import com.example.campusconnect.model.TokenResponse
import com.example.campusconnect.model.Verificar2FARequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface CampusConnectApi {

    @POST("auth/login")
    suspend fun loginUser(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("auth/registro")
    suspend fun registrarUsuario(
        @Body request: RegistroRequest
    ): Response<ResponseBody>

    @POST("auth/verificar-2fa")
    suspend fun verificar2FA(
        @Body request: Verificar2FARequest
    ): Response<TokenResponse>

    @POST("auth/logout")
    suspend fun cerrarSesionRemota(): Response<ResponseBody>

    @POST("auth/fcm-token")
    suspend fun registrarFcmToken(
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @GET("auth/expediente")
    suspend fun obtenerExpedienteAlumno(): ExpedienteAlumnoResponse

    @GET("auth/configuracion-registro")
    suspend fun obtenerConfiguracionRegistro(): CatalogoFacultadResponse

    @GET("catalogos/facultad")
    suspend fun obtenerCatalogosFacultad(): CatalogoFacultadResponse

    @GET("eventos/feed")
    suspend fun obtenerEventos(): List<Evento>

    @GET("eventos/mis-recompensas")
    suspend fun obtenerMisRecompensas(): MisRecompensasResponse

    @GET("eventos/{eventoId}/mapa-asientos")
    suspend fun obtenerMapaAsientosEvento(
        @Path("eventoId") eventoId: String
    ): MapaAsientosEventoResponse

    @POST("eventos/")
    suspend fun crearEvento(
        @Body evento: EventoCreateRequest
    ): Evento

    @PUT("eventos/{eventoId}")
    suspend fun actualizarEvento(
        @Path("eventoId") eventoId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ResponseBody>

    @GET("eventos/{eventoId}/asistentes")
    suspend fun obtenerAsistentesEvento(
        @Path("eventoId") eventoId: String
    ): Response<ResponseBody>

    @PATCH("eventos/{eventoId}/asistentes/{asistenciaId}")
    suspend fun marcarAsistenciaAlumno(
        @Path("eventoId") eventoId: String,
        @Path("asistenciaId") asistenciaId: Int,
        @Body body: Map<String, Boolean>
    ): Response<ResponseBody>

    @POST("eventos/{eventoId}/registro")
    suspend fun registrarAsistenciaEvento(
        @Path("eventoId") eventoId: String,
        @Body body: Map<String, String> = emptyMap()
    ): Response<ResponseBody>

    @POST("eventos/{eventoId}/validar-qr")
    suspend fun validarQrEvento(
        @Path("eventoId") eventoId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("eventos/{eventoId}/ping-gps")
    suspend fun enviarPingGpsEvento(
        @Path("eventoId") eventoId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    @DELETE("eventos/{eventoId}/registro")
    suspend fun cancelarAsistenciaEvento(
        @Path("eventoId") eventoId: String
    ): Response<ResponseBody>

    @Deprecated(
        message = "Usar loginUser(request) que apunta a auth/login",
        replaceWith = ReplaceWith("loginUser(request)")
    )
    @POST("auth/login")
    suspend fun iniciarSesion(@Body request: LoginRequest): Response<LoginResponse>
}