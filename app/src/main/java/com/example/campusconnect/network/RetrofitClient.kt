package com.example.campusconnect.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Si Yasir sube el backend a un servidor real después, solo cambiaremos esta línea
    private const val BASE_URL = "http://10.0.2.2:8000/"

    val apiService: CampusConnectApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CampusConnectApi::class.java)
    }
}