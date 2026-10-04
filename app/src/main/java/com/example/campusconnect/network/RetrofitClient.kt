package com.example.campusconnect.network

import android.content.Context
import com.example.campusconnect.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Si Yasir sube el backend a un servidor real después, solo cambiaremos esta línea
    // Usa 10.0.2.2 para que el emulador apunte a tu PC
// Cambia 10.0.2.2 por 127.0.0.1 para usar el túnel de ADB reverse
    private const val BASE_URL = "http://127.0.0.1:8000/"
    @Volatile
    private var instancia: CampusConnectApi? = null

    /**
     * Antes esto era "val apiService by lazy { ... }" sin contexto. Se necesita
     * un Context para que el interceptor pueda leer el token guardado en
     * TokenManager y mandarlo en cada petición protegida.
     */
    fun obtenerApi(context: Context): CampusConnectApi {
        return instancia ?: synchronized(this) {
            instancia ?: crearApi(context.applicationContext).also { instancia = it }
        }
    }

    private fun crearApi(context: Context): CampusConnectApi {
        val interceptorAutorizacion = Interceptor { chain ->
            val token = TokenManager.obtenerToken(context)
            val peticion = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(peticion)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val cliente = OkHttpClient.Builder()
            // Tiempos de espera de 30 segundos
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(interceptorAutorizacion)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CampusConnectApi::class.java)
    }
}