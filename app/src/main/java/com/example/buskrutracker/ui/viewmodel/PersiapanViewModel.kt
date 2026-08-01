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
        val ruteNama:    String,
        val kapasitas:   Int
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

    /**
     * ✅ FIX: sekarang cek dua sumber, urut prioritas:
     *
     * 1. Device SUDAH punya perjalanId tersimpan lokal (hasActivePerjalanan())
     *    → trip ini sedang berjalan DI HP INI, siapa pun driver terakhirnya
     *      (bisa beda dari kru yang sekarang login token-nya, karena ganti
     *      driver tidak memicu re-login). Query harus BY ID
     *      (getPerjalananById), bukan by kru_id — kalau tetap pakai
     *      getPerjalananAktif() yang by-kru, trip yang sudah dioper ke
     *      driver lain akan terlihat "hilang" dan device bisa memulai trip
     *      baru untuk armada yang sama → dua trip aktif dobel.
     *
     * 2. Device TIDAK punya perjalanId lokal → berarti benar-benar login
     *    fresh, cek apakah kru yang login ini punya trip aktif yang dia
     *    tinggalkan (mis. install ulang app, ganti device). Ini baru pakai
     *    getPerjalananAktif() yang lama (by kru_id).
     */
    fun checkActiveTrip() {
        val token = prefManager.getToken() ?: return
        viewModelScope.launch {
            try {
                val response = if (prefManager.hasActivePerjalanan()) {
                    apiService.getPerjalananById(token, prefManager.getPerjalanId())
                } else {
                    apiService.getPerjalananAktif(token)
                }

                if (response.isSuccess() && response.data != null) {
                    val p = response.data
                    val busInfo = p.armada?.let { a ->
                        if (a.namaBus.isNotEmpty()) "${a.namaBus} (${a.platNomor})" else a.platNomor
                    } ?: "ID ${p.armadaId}"
                    val ruteInfo = p.rute?.namaRute ?: "ID ${p.ruteId}"
                    _uiState.value = PersiapanUiState.ActiveTripDetected(
                        ActiveTripInfo(p, busInfo, ruteInfo)
                    )
                } else if (prefManager.hasActivePerjalanan()) {
                    // perjalanId tersimpan lokal tapi ternyata sudah selesai/tidak ada
                    // di server (mis. diselesaikan dari perangkat lain) → bersihkan
                    // state lokal supaya tidak nyangkut selamanya.
                    prefManager.clearPerjalanId()
                    prefManager.clearFirebaseBusId()
                    prefManager.setTracking(false)
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
                    perjalananId       = perjalanId,
                    totalPenumpang     = 0,
                    totalPenumpangNaik = 0,
                    jarakTempuh        = 0.0,
                    catatan            = "Dibatalkan otomatis oleh sistem (app restart)"
                )
                val response = apiService.selesaiPerjalanan(token, request)
                if (response.isSuccess()) {
                    prefManager.clearPerjalanId()
                    prefManager.clearFirebaseBusId()
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

    /**
     * Restart GPS service dengan data dari perjalanan aktif, meneruskan
     * kapasitas armada yang sebenarnya ke TrackingScreen.
     */
    fun resumeOldTrip(perjalanan: Perjalanan) {
        val kru = prefManager.getUser()

        val firebaseBusId = perjalanan.armada?.firebaseBusId
            ?.takeIf { it.isNotEmpty() }
            ?: prefManager.getFirebaseBusId()

        prefManager.savePerjalanId(perjalanan.id)
        prefManager.setTracking(true)

        val kapasitasArmada = perjalanan.armada?.kapasitas ?: 40

        if (firebaseBusId.isNotEmpty()) {
            prefManager.saveFirebaseBusId(firebaseBusId)

            val serviceIntent = GpsTrackingService.createStartIntent(
                context       = context,
                perjalanId    = perjalanan.id,
                firebaseBusId = firebaseBusId,
                namaBus       = perjalanan.armada?.namaBus ?: "",
                armadaNomor   = perjalanan.armada?.platNomor ?: "",
                kelas         = perjalanan.armada?.kelas ?: "",
                kapasitas     = kapasitasArmada,
                ruteNama      = perjalanan.rute?.namaRute ?: "",
                polyline      = perjalanan.rute?.polyline ?: "",
                // ✅ FIX: pakai nama driver TERKINI dari data perjalanan
                // (yang sedang tersimpan di database), bukan kru yang sedang
                // login di device ini — supaya konsisten dengan "hasil kru
                // yang terakhir" walau device dipegang driver berbeda.
                kruNama       = perjalanan.kru?.driver ?: kru?.driver ?: "",
                tarif         = perjalanan.tarifSnapshot ?: 0.0
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        _uiState.value = PersiapanUiState.PerjalananStarted(
            perjalanId  = perjalanan.id,
            namaBus     = perjalanan.armada?.namaBus     ?: "",
            armadaNomor = perjalanan.armada?.platNomor   ?: "",
            ruteNama    = perjalanan.rute?.namaRute      ?: "",
            kapasitas   = kapasitasArmada
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
                val data     = mapOf("armada_id" to armada.id, "rute_id" to rute.id)
                val response = apiService.mulaiPerjalanan(token, data)

                if (response.isSuccess() && response.data?.perjalanan != null) {
                    val perjalanan    = response.data.perjalanan!!
                    val tarif         = rute.getTarifHarga()

                    val firebaseBusId = response.data.firebaseBusId
                        .takeIf { it.isNotEmpty() }
                        ?: armada.firebaseBusId.orEmpty()

                    if (firebaseBusId.isEmpty()) {
                        _uiState.value = PersiapanUiState.Error(
                            "❌ Armada tidak memiliki Firebase Bus ID. Hubungi admin."
                        )
                        return@launch
                    }

                    prefManager.savePerjalanId(perjalanan.id)
                    prefManager.setTracking(true)
                    prefManager.saveFirebaseBusId(firebaseBusId)

                    val serviceIntent = GpsTrackingService.createStartIntent(
                        context       = context,
                        perjalanId    = perjalanan.id,
                        firebaseBusId = firebaseBusId,
                        namaBus       = armada.namaBus,
                        armadaNomor   = armada.platNomor,
                        kelas         = armada.kelas,
                        kapasitas     = armada.kapasitas,
                        ruteNama      = rute.namaRute,
                        polyline      = rute.polyline,
                        kruNama       = kru.driver,
                        tarif         = tarif
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
                        ruteNama    = rute.namaRute,
                        kapasitas   = armada.kapasitas
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