package com.example.buskrutracker.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.buskrutracker.api.RetrofitClient
import com.example.buskrutracker.models.SelesaiPerjalananResponse
import com.example.buskrutracker.models.SelesaiPerjalananRequest
import com.example.buskrutracker.models.UpdateKondisiRequest
import com.example.buskrutracker.services.GpsTrackingService
import com.example.buskrutracker.utils.SharedPrefManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TrackingStats(
    val speedKmh:   Float  = 0f,
    val jarakKm:    Double = 0.0,
    val durasiMenit: Int   = 0
)

data class SelesaiResult(
    val perjalanId:      Int,
    val namaBus:         String,
    val armadaNomor:     String,
    val ruteNama:        String,
    val totalPenumpang:  Int,
    val penumpangNaik:   Int,
    val totalPendapatan: Double,
    val tarifPerOrang:   Double,
    val jarakTempuh:     Double,
    val durasiJam:       Int,
    val durasiMenitSisa: Int
)

sealed class TrackingUiState {
    object Idle       : TrackingUiState()
    object Loading    : TrackingUiState()
    data class SelesaiSuccess(val result: SelesaiResult) : TrackingUiState()
    data class Error(val message: String)  : TrackingUiState()
    data class Toast(val message: String)  : TrackingUiState()
}

class TrackingViewModel(application: Application) : AndroidViewModel(application) {

    private companion object { const val PREF_TRACKING = "tracking_state" }

    private val apiService   = RetrofitClient.apiService
    private val prefManager  = SharedPrefManager.getInstance(application)
    private val context      = application
    private val trackingPrefs: SharedPreferences =
        application.getSharedPreferences(PREF_TRACKING, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<TrackingUiState>(TrackingUiState.Idle)
    val uiState: StateFlow<TrackingUiState> = _uiState

    private val _jumlahPenumpang = MutableStateFlow(0)
    val jumlahPenumpang: StateFlow<Int> = _jumlahPenumpang

    private val _kapasitas = MutableStateFlow(40)
    val kapasitas: StateFlow<Int> = _kapasitas

    private val _kondisi = MutableStateFlow("lancar")
    val kondisi: StateFlow<String> = _kondisi

    private val _stats = MutableStateFlow(TrackingStats())
    val stats: StateFlow<TrackingStats> = _stats

    // Akumulasi boarding — persist ke SharedPreferences
    private var totalPassengersBoarded = 0
    private var perjalanId = 0

    // BroadcastReceiver untuk update GPS dari service
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val speed       = intent.getFloatExtra("speed", 0f)
            val distance    = intent.getDoubleExtra("distance", 0.0)
            val updateCount = intent.getIntExtra("update_count", 0)
            _stats.value = TrackingStats(
                speedKmh    = speed,
                jarakKm     = distance,
                durasiMenit = updateCount / 12
            )
        }
    }

    // ============================================
    // INIT
    // ============================================

    // ✅ FIX: tambah parameter kapasitasAwal (default 40 agar tetap backward-compatible),
    // supaya kapasitas dari navigasi langsung terpasang sejak frame pertama,
    // tidak menunggu loadPerjalananAktif() selesai/berhasil.
    fun init(perjalanId: Int, kapasitasAwal: Int = 40) {
        this.perjalanId = perjalanId
        _kapasitas.value = kapasitasAwal
        restoreBoardedCount()
        registerReceiver()
        loadPerjalananAktif()
    }

    // ✅ FIX: di Android 13+ (API 33 / TIRAMISU), registerReceiver() untuk broadcast
    // internal wajib menyertakan flag RECEIVER_EXPORTED / RECEIVER_NOT_EXPORTED.
    // Karena broadcast "GPS_LOCATION_UPDATE" ini hanya dikirim dari dalam app sendiri
    // (GpsTrackingService), dipakai RECEIVER_NOT_EXPORTED (lebih aman, tidak bisa
    // diakses app lain).
    private fun registerReceiver() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    locationReceiver,
                    IntentFilter("GPS_LOCATION_UPDATE"),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(
                    locationReceiver,
                    IntentFilter("GPS_LOCATION_UPDATE")
                )
            }
        } catch (_: Exception) {}
    }

    fun unregisterReceiver() {
        try { context.unregisterReceiver(locationReceiver) } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        unregisterReceiver()
    }

    // ============================================
    // PERSIST BOARDED COUNT
    // ============================================

    private fun saveBoardedCount() {
        trackingPrefs.edit().putInt("boarded_$perjalanId", totalPassengersBoarded).apply()
    }

    private fun restoreBoardedCount() {
        totalPassengersBoarded = trackingPrefs.getInt("boarded_$perjalanId", 0)
    }

    private fun clearBoardedCount() {
        trackingPrefs.edit().remove("boarded_$perjalanId").apply()
    }

    // ============================================
    // LOAD AKTIF
    // ============================================

    private fun loadPerjalananAktif() {
        val token = prefManager.getToken() ?: return
        viewModelScope.launch {
            try {
                val response = apiService.getPerjalananAktif(token)
                if (response.isSuccess() && response.data != null) {
                    val p = response.data
                    _jumlahPenumpang.value = p.totalPenumpang
                    _kapasitas.value       = p.armada?.kapasitas ?: 40
                    _kondisi.value         = p.kondisiTerakhir.ifEmpty { "lancar" }
                }
            } catch (_: Exception) {}
        }
    }

    // ============================================
    // PENUMPANG COUNTER
    // ============================================

    fun tambahPenumpang() {
        if (_jumlahPenumpang.value < _kapasitas.value) {
            _jumlahPenumpang.value++
            totalPassengersBoarded++
            saveBoardedCount()
            syncPenumpang()
        } else {
            _uiState.value = TrackingUiState.Toast("⚠️ Kapasitas penuh!")
        }
    }

    fun kurangPenumpang() {
        if (_jumlahPenumpang.value > 0) {
            _jumlahPenumpang.value--
            syncPenumpang()
        } else {
            _uiState.value = TrackingUiState.Toast("⚠️ Penumpang sudah 0")
        }
    }

    private fun syncPenumpang() {
        val current = _jumlahPenumpang.value
        // Update ke service (Firebase)
        context.startService(
            GpsTrackingService.createPassengerUpdateIntent(context, perjalanId, current)
        )
        context.startService(
            GpsTrackingService.createBoardingUpdateIntent(context, perjalanId, totalPassengersBoarded)
        )
        // Update ke server (REST API)
        val token = prefManager.getToken() ?: return
        viewModelScope.launch {
            try {
                apiService.updatePenumpang(
                    token,
                    mapOf("perjalanan_id" to perjalanId, "total_penumpang" to current)
                )
            } catch (_: Exception) {}
        }
    }

    // ============================================
    // UPDATE KONDISI
    // ============================================

    fun updateKondisi(kondisi: String) {
        _kondisi.value = kondisi
        context.startService(
            GpsTrackingService.createKondisiUpdateIntent(context, perjalanId, kondisi)
        )
        val token = prefManager.getToken() ?: return
        viewModelScope.launch {
            try {
                val response = apiService.updateKondisi(
                    token,
                    UpdateKondisiRequest(perjalananId = perjalanId, kondisi = kondisi)
                )
                if (response.isSuccess()) {
                    val emoji = when (kondisi) {
                        "lancar" -> "✓"; "macet" -> "⚠"; else -> "🔧"
                    }
                    _uiState.value = TrackingUiState.Toast("$emoji Status: ${kondisi.uppercase()}")
                }
            } catch (_: Exception) {}
        }
    }

    // ============================================
    // AKHIRI PERJALANAN
    // ============================================

    fun akhiriPerjalanan(
        perjalanId:  Int,
        namaBus:     String,
        armadaNomor: String,
        ruteNama:    String
    ) {
        val token = prefManager.getToken() ?: return
        _uiState.value = TrackingUiState.Loading

        viewModelScope.launch {
            try {
                val request = SelesaiPerjalananRequest(
                    perjalananId       = perjalanId,
                    totalPenumpang     = _jumlahPenumpang.value,
                    totalPenumpangNaik = totalPassengersBoarded,
                    jarakTempuh        = _stats.value.jarakKm,
                    catatan            = "Perjalanan selesai"
                )
                val response = apiService.selesaiPerjalanan(token, request)

                if (response.isSuccess() && response.data != null) {
                    handleSelesai(response.data, perjalanId, namaBus, armadaNomor, ruteNama)
                } else {
                    _uiState.value = TrackingUiState.Error(
                        response.message.ifEmpty { "Gagal mengakhiri perjalanan" }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = TrackingUiState.Error("❌ Error: ${e.message}")
            }
        }
    }

    private fun handleSelesai(
        resp:        SelesaiPerjalananResponse,
        perjalanId:  Int,
        namaBus:     String,
        armadaNomor: String,
        ruteNama:    String
    ) {
        clearBoardedCount()
        context.startService(GpsTrackingService.createStopIntent(context))
        prefManager.clearPerjalanId()
        prefManager.setTracking(false)

        val s = resp.summary
        val dMenit = _stats.value.durasiMenit

        _uiState.value = TrackingUiState.SelesaiSuccess(
            SelesaiResult(
                perjalanId      = perjalanId,
                namaBus         = namaBus,
                armadaNomor     = armadaNomor,
                ruteNama        = ruteNama,
                totalPenumpang  = _jumlahPenumpang.value,
                penumpangNaik   = s?.penumpangNaik      ?: totalPassengersBoarded,
                totalPendapatan = s?.totalPendapatan    ?: 0.0,
                tarifPerOrang   = s?.tarifPerOrang      ?: 0.0,
                jarakTempuh     = _stats.value.jarakKm,
                durasiJam       = s?.durasiJam          ?: (dMenit / 60),
                durasiMenitSisa = s?.durasiMenit        ?: (dMenit % 60)
            )
        )
    }

    fun getBoardedCount(): Int = totalPassengersBoarded
    fun resetState() { _uiState.value = TrackingUiState.Idle }
}