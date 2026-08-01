package com.example.buskrutracker.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.buskrutracker.api.RetrofitClient
import com.example.buskrutracker.models.GantiDriverRequest
import com.example.buskrutracker.models.Kru
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

    // status GPS & jaringan device (murni UI, tidak dikirim ke Firebase/API)
    private val _gpsEnabled = MutableStateFlow(true)
    val gpsEnabled: StateFlow<Boolean> = _gpsEnabled

    private val _networkAvailable = MutableStateFlow(true)
    val networkAvailable: StateFlow<Boolean> = _networkAvailable

    // ✅ state driver aktif & daftar kru untuk fitur ganti driver
    private val _currentDriver = MutableStateFlow("")
    val currentDriver: StateFlow<String> = _currentDriver

    // ✅ BARU — simpan ID driver aktif (bukan cuma nama), untuk perbandingan yang akurat
    private val _currentDriverId = MutableStateFlow<Int?>(null)
    val currentDriverId: StateFlow<Int?> = _currentDriverId

    private val _daftarKru = MutableStateFlow<List<Kru>>(emptyList())
    val daftarKru: StateFlow<List<Kru>> = _daftarKru

    private val _isLoadingKru = MutableStateFlow(false)
    val isLoadingKru: StateFlow<Boolean> = _isLoadingKru

    private val _isGantiDriverLoading = MutableStateFlow(false)
    val isGantiDriverLoading: StateFlow<Boolean> = _isGantiDriverLoading

    private var totalPassengersBoarded = 0
    private var perjalanId = 0

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

    private val gpsStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _gpsEnabled.value = intent.getBooleanExtra(
                GpsTrackingService.EXTRA_GPS_ENABLED, true
            )
        }
    }

    private val networkStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _networkAvailable.value = intent.getBooleanExtra(
                GpsTrackingService.EXTRA_NETWORK_AVAILABLE, true
            )
        }
    }

    // ============================================
    // INIT
    // ============================================

    fun init(perjalanId: Int, kapasitasAwal: Int = 40) {
        this.perjalanId = perjalanId
        _kapasitas.value = kapasitasAwal
        restoreBoardedCount()
        registerReceiver()
        loadPerjalananAktif()
    }

    private fun registerReceiver() {
        try {
            ContextCompat.registerReceiver(
                context,
                locationReceiver,
                IntentFilter("GPS_LOCATION_UPDATE"),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            ContextCompat.registerReceiver(
                context,
                gpsStatusReceiver,
                IntentFilter(GpsTrackingService.ACTION_GPS_STATUS_UPDATE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            ContextCompat.registerReceiver(
                context,
                networkStatusReceiver,
                IntentFilter(GpsTrackingService.ACTION_NETWORK_STATUS_UPDATE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    fun unregisterReceiver() {
        try { context.unregisterReceiver(locationReceiver) } catch (_: Exception) {}
        try { context.unregisterReceiver(gpsStatusReceiver) } catch (_: Exception) {}
        try { context.unregisterReceiver(networkStatusReceiver) } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        unregisterReceiver()
    }

    // ============================================
    // PERSIST BOARDED COUNT
    // ============================================

    private fun saveBoardedCount() {
        trackingPrefs.edit { putInt("boarded_$perjalanId", totalPassengersBoarded) }
    }

    private fun restoreBoardedCount() {
        totalPassengersBoarded = trackingPrefs.getInt("boarded_$perjalanId", 0)
    }

    private fun clearBoardedCount() {
        trackingPrefs.edit { remove("boarded_$perjalanId") }
    }

    // ============================================
    // LOAD AKTIF
    // ============================================

    // ✅ FIX: pakai getPerjalananById(this.perjalanId), BUKAN getPerjalananAktif().
    // ViewModel ini sudah tahu perjalanId-nya sendiri lewat init() — query
    // "trip milik kru yang sedang login" (getPerjalananAktif) salah dipakai
    // di sini, karena kalau device sudah oper ke driver lain lewat
    // gantiDriver(), token yang tersimpan bukan lagi kru_id yang sekarang
    // tercatat di baris perjalanan → data akan gagal ditemukan / salah.
    private fun loadPerjalananAktif() {
        val token = prefManager.getToken() ?: return
        viewModelScope.launch {
            try {
                val response = apiService.getPerjalananById(token, perjalanId)
                if (response.isSuccess() && response.data != null) {
                    val p = response.data
                    _jumlahPenumpang.value = p.totalPenumpang
                    _kapasitas.value       = p.armada?.kapasitas ?: 40
                    _kondisi.value         = p.kondisiTerakhir.ifEmpty { "lancar" }
                    _currentDriver.value   = p.kru?.driver.orEmpty()
                    _currentDriverId.value = p.kru?.id // ✅ BARU — simpan id juga
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
        context.startService(
            GpsTrackingService.createPassengerUpdateIntent(context, perjalanId, current)
        )
        context.startService(
            GpsTrackingService.createBoardingUpdateIntent(context, perjalanId, totalPassengersBoarded)
        )
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
    // ✅ GANTI DRIVER (dioptimasi)
    // ============================================

    /** Ambil daftar kru aktif dari server untuk ditampilkan di dialog pilihan. */
    fun loadDaftarKru() {
        val token = prefManager.getToken() ?: return
        viewModelScope.launch {
            _isLoadingKru.value = true
            try {
                val response = apiService.getDaftarKru(token)
                if (response.isSuccess() && response.data != null) {
                    _daftarKru.value = response.data
                } else {
                    _uiState.value = TrackingUiState.Toast("⚠️ Gagal ambil daftar kru")
                }
            } catch (e: Exception) {
                _uiState.value = TrackingUiState.Toast("❌ Koneksi error: ${e.message}")
            } finally {
                _isLoadingKru.value = false
            }
        }
    }

    /**
     * Ganti driver aktif ke kru yang dipilih. Update dilakukan ke:
     * 1. Backend Laravel (kolom kru_id di tabel perjalanan)
     * 2. Firebase Realtime Database (field driver, real-time untuk app penumpang & web)
     *
     * Perbaikan dari versi sebelumnya:
     * - Guard `_isGantiDriverLoading`: mencegah user nge-tap 2 kru berbeda secara
     *   cepat berturut-turut, yang sebelumnya memicu 2 request paralel dan response
     *   yang datang belakangan bisa "menimpa" pilihan yang lebih baru (race condition
     *   -> terlihat seperti salah pilih / nge-bug).
     * - Perbandingan "tidak ada perubahan" sekarang pakai ID kru (`kruBaru.id`),
     *   bukan nama (`driver: String`) yang rawan false-positive kalau ada nama sama.
     * - Kalau user memilih driver yang sama, sekarang dikasih toast feedback
     *   ("driver ini sudah aktif") alih-alih diam-diam return tanpa reaksi apa pun.
     * - Optimistic update: nama driver di UI langsung diganti saat request dikirim,
     *   supaya dialog/tombol terasa responsif; kalau request gagal, otomatis di-rollback
     *   ke driver & id sebelumnya.
     */
    fun gantiDriver(kruBaru: Kru) {
        // Guard: cegah request ganda kalau masih ada proses ganti driver berjalan
        if (_isGantiDriverLoading.value) {
            _uiState.value = TrackingUiState.Toast("⏳ Tunggu proses sebelumnya selesai")
            return
        }

        // Bandingkan pakai ID, bukan nama, biar akurat
        if (kruBaru.id == _currentDriverId.value) {
            _uiState.value = TrackingUiState.Toast("ℹ️ Driver ini sudah aktif")
            return
        }

        val token = prefManager.getToken() ?: return

        // Simpan state lama untuk rollback jika gagal
        val previousDriverName = _currentDriver.value
        val previousDriverId   = _currentDriverId.value

        // Optimistic update — biar dialog langsung terasa responsif
        _currentDriver.value   = kruBaru.driver
        _currentDriverId.value = kruBaru.id

        viewModelScope.launch {
            _isGantiDriverLoading.value = true
            try {
                val response = apiService.gantiDriver(
                    token,
                    GantiDriverRequest(perjalananId = perjalanId, kruIdBaru = kruBaru.id)
                )
                if (response.isSuccess() && response.data != null) {
                    // Sinkronkan dengan nilai final dari server (jaga-jaga beda format)
                    _currentDriver.value   = response.data.driverBaru
                    _currentDriverId.value = kruBaru.id

                    context.startService(
                        GpsTrackingService.createDriverUpdateIntent(
                            context, perjalanId, response.data.driverBaru
                        )
                    )
                    _uiState.value = TrackingUiState.Toast("✓ Driver diganti: ${response.data.driverBaru}")
                } else {
                    // Rollback optimistic update kalau backend menolak
                    _currentDriver.value   = previousDriverName
                    _currentDriverId.value = previousDriverId
                    _uiState.value = TrackingUiState.Toast(
                        response.message.ifEmpty { "❌ Gagal ganti driver" }
                    )
                }
            } catch (e: Exception) {
                // Rollback optimistic update kalau ada exception (network dsb)
                _currentDriver.value   = previousDriverName
                _currentDriverId.value = previousDriverId
                _uiState.value = TrackingUiState.Toast("❌ Error: ${e.message}")
            } finally {
                _isGantiDriverLoading.value = false
            }
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