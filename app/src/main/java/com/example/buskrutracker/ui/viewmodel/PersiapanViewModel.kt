package com.example.buskrutracker.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.buskrutracker.api.RetrofitClient
import com.example.buskrutracker.models.Armada
import com.example.buskrutracker.models.Perjalanan
import com.example.buskrutracker.models.Rute
import com.example.buskrutracker.models.SelesaiPerjalananRequest
import com.example.buskrutracker.services.GpsTrackingService
import com.example.buskrutracker.utils.SharedPrefManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ActiveTripInfo(
    val perjalanan: Perjalanan,
    val busInfo:    String,
    val ruteInfo:   String
)

sealed class PersiapanUiState {
    object Idle        : PersiapanUiState()
    object Loading     : PersiapanUiState()
    data class ActiveTripDetected(val info: ActiveTripInfo) : PersiapanUiState()
    data class PerjalananStarted(
        val perjalanId:  Int,
        val namaBus:     String,
        val armadaNomor: String,
        val ruteNama:    String
    ) : PersiapanUiState()
    data class Error(val message: String)   : PersiapanUiState()
    data class Toast(val message: String)   : PersiapanUiState()
}

class PersiapanViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService  = RetrofitClient.apiService
    private val prefManager = SharedPrefManager.getInstance(application)
    private val context     = application

    private val _uiState      = MutableStateFlow<PersiapanUiState>(PersiapanUiState.Idle)
    val uiState: StateFlow<PersiapanUiState> = _uiState

    private val _armadaList   = MutableStateFlow<List<Armada>>(emptyList())
    val armadaList: StateFlow<List<Armada>> = _armadaList

    private val _ruteList     = MutableStateFlow<List<Rute>>(emptyList())
    val ruteList: StateFlow<List<Rute>> = _ruteList

    private val _isLoadingArmada = MutableStateFlow(false)
    val isLoadingArmada: StateFlow<Boolean> = _isLoadingArmada

    private val _isLoadingRute   = MutableStateFlow(false)
    val isLoadingRute: StateFlow<Boolean> = _isLoadingRute

    // ============================================
    // INIT DATA
    // ============================================

    fun loadInitialData() {
        loadArmada()
        loadRute()
        checkActiveTrip()
    }

    fun getGreeting(): String {
        val kru = prefManager.getUser()
        return if (kru != null) "Halo, ${getNamaSapaan(kru.driver)}!" else "Halo, Kru!"
    }

    private fun getNamaSapaan(namaLengkap: String): String {
        if (namaLengkap.isBlank()) return "Kru"
        return "Pak ${namaLengkap.split(" ").first()}"
    }

    // ============================================
    // LOAD DATA
    // ============================================

    private fun loadArmada() {
        val token = prefManager.getToken() ?: return
        viewModelScope.launch {
            _isLoadingArmada.value = true
            try {
                val response = apiService.getArmada(token)
                if (response.isSuccess() && response.data != null) {
                    _armadaList.value = response.data
                } else {
                    _uiState.value = PersiapanUiState.Toast("⚠️ Data armada kosong")
                }
            } catch (e: Exception) {
                _uiState.value = PersiapanUiState.Toast("❌ Koneksi error: ${e.message}")
            } finally {
                _isLoadingArmada.value = false
            }
        }
    }

    private fun loadRute() {
        val token = prefManager.getToken() ?: return
        viewModelScope.launch {
            _isLoadingRute.value = true
            try {
                val response = apiService.getRute(token)
                if (response.isSuccess() && response.data != null) {
                    _ruteList.value = response.data
                } else {
                    _uiState.value = PersiapanUiState.Toast("⚠️ Data rute kosong")
                }
            } catch (e: Exception) {
                _uiState.value = PersiapanUiState.Toast("❌ Koneksi error: ${e.message}")
            } finally {
                _isLoadingRute.value = false
            }
        }
    }

    // ============================================
    // CHECK ACTIVE TRIP
    // ============================================

    fun checkActiveTrip() {
        val token = prefManager.getToken() ?: return
        viewModelScope.launch {
            try {
                val response = apiService.getPerjalananAktif(token)
                if (response.isSuccess() && response.data != null) {
                    val p = response.data
                    val busInfo = p.armada?.let { a ->
                        if (a.namaBus.isNotEmpty()) "${a.namaBus} (${a.platNomor})" else a.platNomor
                    } ?: "ID ${p.armadaId}"
                    val ruteInfo = p.rute?.namaRute ?: "ID ${p.ruteId}"
                    _uiState.value = PersiapanUiState.ActiveTripDetected(
                        ActiveTripInfo(p, busInfo, ruteInfo)
                    )
                }
            } catch (_: Exception) { /* silent */ }
        }
    }

    fun cancelOldTrip(perjalanId: Int) {
        val token = prefManager.getToken() ?: return
        viewModelScope.launch {
            _uiState.value = PersiapanUiState.Loading
            try {
                val request = SelesaiPerjalananRequest(
                    perjalananId = perjalanId,
                    totalPenumpang = 0,
                    totalPenumpangNaik = 0,
                    jarakTempuh = 0.0,
                    catatan = "Dibatalkan otomatis oleh sistem (app restart)"
                )

                val response = apiService.selesaiPerjalanan(
                    token,
                    request
                )
                if (response.isSuccess()) {
                    prefManager.clearPerjalanId()
                    prefManager.setTracking(false)
                    _uiState.value = PersiapanUiState.Toast("✓ Perjalanan lama dibatalkan.")
                } else {
                    _uiState.value = PersiapanUiState.Toast("❌ Gagal membatalkan. Hubungi admin.")
                }
            } catch (e: Exception) {
                _uiState.value = PersiapanUiState.Toast("❌ Error: ${e.message}")
            }
        }
    }

    fun resumeOldTrip(perjalanan: Perjalanan) {
        prefManager.savePerjalanId(perjalanan.id)
        prefManager.setTracking(true)
        _uiState.value = PersiapanUiState.PerjalananStarted(
            perjalanId  = perjalanan.id,
            namaBus     = perjalanan.armada?.namaBus     ?: "",
            armadaNomor = perjalanan.armada?.platNomor   ?: "",
            ruteNama    = perjalanan.rute?.namaRute      ?: ""
        )
    }

    // ============================================
    // MULAI PERJALANAN
    // ============================================

    fun mulaiPerjalanan(armada: Armada, rute: Rute) {
        val token = prefManager.getToken() ?: return
        val kru   = prefManager.getUser()  ?: return

        viewModelScope.launch {
            _uiState.value = PersiapanUiState.Loading
            try {
                val data = mapOf("armada_id" to armada.id, "rute_id" to rute.id)
                val response = apiService.mulaiPerjalanan(token, data)

                if (response.isSuccess() && response.data?.perjalanan != null) {
                    val perjalanan = response.data.perjalanan!!
                    val tarif      = rute.getTarifHarga()

                    prefManager.savePerjalanId(perjalanan.id)
                    prefManager.setTracking(true)

                    // Start GPS Service
                    val serviceIntent = GpsTrackingService.createStartIntent(
                        context,
                        perjalanan.id,
                        armada.namaBus,
                        armada.platNomor,
                        armada.kelas,
                        armada.kapasitas,
                        rute.namaRute,
                        rute.polyline,
                        kru.driver,
                        tarif
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }

                    _uiState.value = PersiapanUiState.PerjalananStarted(
                        perjalanId  = perjalanan.id,
                        namaBus     = armada.namaBus,
                        armadaNomor = armada.platNomor,
                        ruteNama    = rute.namaRute
                    )
                } else {
                    _uiState.value = PersiapanUiState.Error(
                        response.message.ifEmpty { "❌ Gagal memulai perjalanan" }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = PersiapanUiState.Error("❌ Error: ${e.message}")
            }
        }
    }

    fun logout() {
        prefManager.logout()
    }

    fun resetState() { _uiState.value = PersiapanUiState.Idle }
}