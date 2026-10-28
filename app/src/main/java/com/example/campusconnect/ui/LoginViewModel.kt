package com.example.campusconnect.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campusconnect.model.RegistroRequest
import com.example.campusconnect.network.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Requires2FA(val correo: String) : LoginState()
    data class Success(val token: String, val refreshToken: String, val role: String, val correo: String, val pass: String) : LoginState()
    object RegisterSuccess : LoginState()
    data class Failure(val error: String) : LoginState()
}

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun ejecutarLogin(correo: String, contrasena: String) {
        val correoLimpio = correo.trim()
        val passwordLimpia = contrasena.trim()

        if (correoLimpio.isBlank() || passwordLimpia.isBlank()) {
            _loginState.value = LoginState.Failure("Completa tu correo y contraseña")
            return
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val resultado = repository.login(correoLimpio, passwordLimpia)
            resultado.fold(
                onSuccess = { response ->
                    if (response.requiere2fa) {
                        _loginState.value = LoginState.Requires2FA(correoLimpio)
                    } else {
                        _loginState.value = LoginState.Success(
                            token = response.accessToken ?: "",
                            refreshToken = response.refreshToken ?: "",
                            role = response.role ?: "Usuario Regular",
                            correo = correoLimpio,
                            pass = passwordLimpia
                        )
                    }
                },
                onFailure = { excepcion ->
                    _loginState.value = LoginState.Failure(excepcion.message ?: "Error desconocido")
                }
            )
        }
    }

    fun ejecutarRegistro(request: RegistroRequest) {
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val resultado = repository.registrar(request)
            resultado.fold(
                onSuccess = {
                    _loginState.value = LoginState.RegisterSuccess
                },
                onFailure = { excepcion ->
                    _loginState.value = LoginState.Failure(excepcion.message ?: "Error al registrar")
                }
            )
        }
    }

    fun verificar2FA(correo: String, codigo: String, contrasenaOriginal: String) {
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val resultado = repository.verificar2FA(correo.trim(), codigo.trim())
            resultado.fold(
                onSuccess = { response ->
                    _loginState.value = LoginState.Success(
                        token = response.accessToken ?: "",
                        refreshToken = response.refreshToken ?: "",
                        role = response.role ?: "Usuario Regular",
                        correo = correo.trim(),
                        pass = contrasenaOriginal
                    )
                },
                onFailure = { excepcion ->
                    _loginState.value = LoginState.Failure(excepcion.message ?: "Error 2FA")
                }
            )
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }
}