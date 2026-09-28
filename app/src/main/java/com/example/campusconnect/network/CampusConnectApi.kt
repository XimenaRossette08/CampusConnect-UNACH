package com.example.campusconnect.network

import com.example.campusconnect.model.LoginRequest
import com.example.campusconnect.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface CampusConnectApi {
    // Le indicamos a Retrofit que esta es una petición POST a la ruta "/login"
    @POST("login")
    suspend fun loginUser(
        @Body request: LoginRequest
    ): Response<LoginResponse>
}