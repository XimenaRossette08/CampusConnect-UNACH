package com.example.campusconnect.network

import com.example.campusconnect.model.LoginRequest
import com.example.campusconnect.model.LoginResponse
import com.example.campusconnect.model.RegistroRequest
import com.example.campusconnect.model.TokenResponse
import com.example.campusconnect.model.Verificar2FARequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(private val api: CampusConnectApi) {

    suspend fun login(correo: String, contrasena: String): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = LoginRequest(correo, contrasena)
                val response = api.loginUser(request)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else if (response.code() == 401) {
                    Result.failure(Exception("Correo o contraseña incorrectos"))
                } else if (response.code() == 429) {
                    Result.failure(Exception("Demasiados intentos. Espera unos minutos antes de reintentar."))
                } else {
                    Result.failure(Exception("Error en servidor: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Error de conexión"))
            }
        }
    }

    suspend fun registrar(request: RegistroRequest): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.registrarUsuario(request)
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("No se pudo completar el registro. Verifica tus datos."))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Error de conexión al registrar usuario"))
            }
        }
    }

    suspend fun verificar2FA(correo: String, codigo: String): Result<TokenResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Verificar2FARequest(correo, codigo)
                val response = api.verificar2FA(request)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Código incorrecto o expirado"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Error al verificar el código"))
            }
        }
    }

    /**
     * Remediación Auditoría A-4: Renueva la sesión usando el refresh_token protegido
     * por Android Keystore y BiometricPrompt, sin guardar ni enviar jamás la contraseña.
     */
    suspend fun renovarSesionBiometrica(refreshToken: String): Result<TokenResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.refrescarToken(mapOf("refresh_token" to refreshToken))
                val body = response.body()
                if (response.isSuccessful && body != null && body.accessToken.isNotBlank()) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("Tu sesión biométrica expiró. Inicia sesión con tu contraseña para renovarla."))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Error de conexión al validar tu sesión biométrica"))
            }
        }
    }
}