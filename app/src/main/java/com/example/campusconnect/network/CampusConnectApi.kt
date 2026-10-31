package com.example.campusconnect.network

import com.example.campusconnect.model.Evento
import com.example.campusconnect.model.EventoCreateRequest
import com.example.campusconnect.model.LoginRequest
import com.example.campusconnect.model.LoginResponse
import com.example.campusconnect.model.RegistroRequest
import com.example.campusconnect.model.TokenResponse
import com.example.campusconnect.model.Verificar2FARequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
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

    @GET("eventos/feed")
    suspend fun obtenerEventos(): List<Evento>

    @POST("eventos/")
    suspend fun crearEvento(
        @Body evento: EventoCreateRequest
    ): Evento

    @POST("eventos/{eventoId}/registro")
    suspend fun registrarAsistenciaEvento(
        @Path("eventoId") eventoId: String
    ): Response<ResponseBody>

    @DELETE("eventos/{eventoId}/registro")
    suspend fun cancelarAsistenciaEvento(
        @Path("eventoId") eventoId: String
    ): Response<ResponseBody>

    @POST("/api/login")
    suspend fun iniciarSesion(@Body request: LoginRequest): Response<LoginResponse>
}