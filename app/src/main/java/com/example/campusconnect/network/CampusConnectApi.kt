package com.example.campusconnect.network

import com.example.campusconnect.model.Evento
import com.example.campusconnect.model.EventoCreateRequest
import com.example.campusconnect.model.LoginRequest
import com.example.campusconnect.model.TokenResponse
import com.example.campusconnect.model.Verificar2FARequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface CampusConnectApi {

    @POST("auth/login")
    suspend fun loginUser(
        @Body request: LoginRequest
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
}