package com.example.buskrutracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.buskrutracker.api.RetrofitClient
import com.example.buskrutracker.models.Kru
import com.example.buskrutracker.utils.SharedPrefManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle    : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val driverName: String) : LoginUiState()
    data class Error(val message: String)      : LoginUiState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService  = RetrofitClient.apiService
    private val prefManager = SharedPrefManager.getInstance(application)

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun isAlreadyLoggedIn(): Boolean = prefManager.isLoggedIn()

    fun login(username: String, password: String) {
        if (username.isBlank()) {
            _uiState.value = LoginUiState.Error("⚠️ Masukkan ID Pengemudi"); return
        }
        if (password.isBlank()) {
            _uiState.value = LoginUiState.Error("⚠️ Masukkan Kata Sandi"); return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val response = apiService.login(
                    mapOf("username" to username, "password" to password)
                )
                if (response.isSuccess() && response.data != null) {
                    handleLoginSuccess(response.data)
                } else {
                    _uiState.value = LoginUiState.Error("❌ ${response.message}")
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error("❌ Koneksi gagal. Coba lagi.")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleLoginSuccess(data: Map<String, Any>) {
        try {
            val kruData = data["kru"] as? Map<String, Any>
                ?: run { _uiState.value = LoginUiState.Error("❌ Data kru tidak ditemukan"); return }

            val token = data["token"]?.toString() ?: ""

            val id = when (val raw = kruData["id"]) {
                is Double  -> raw.toInt()
                is Int     -> raw
                else       -> raw?.toString()?.toIntOrNull() ?: 0
            }

            val kru = Kru(
                id       = id,
                driver   = kruData["driver"]?.toString()   ?: "",
                username = kruData["username"]?.toString() ?: "",
                status   = kruData["status"]?.toString()   ?: "aktif",
                token    = token
            )

            prefManager.saveLoginData(token, kru)
            _uiState.value = LoginUiState.Success(kru.driver)
        } catch (e: Exception) {
            _uiState.value = LoginUiState.Error("❌ Error: ${e.message}")
        }
    }

    fun resetState() { _uiState.value = LoginUiState.Idle }
}