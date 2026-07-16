package com.example.buskrutracker.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.buskrutracker.MainActivity
import com.example.buskrutracker.R
import com.example.buskrutracker.utils.ETACalculator
import com.example.buskrutracker.utils.PolylineUtils
import com.example.buskrutracker.utils.SharedPrefManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GpsTrackingService : Service() {

    companion object {
        private const val TAG              = "GpsTrackingService"
        private const val CHANNEL_ID       = "gps_tracking_channel"
        private const val NOTIFICATION_ID  = 1001

        // ✅ UBAH — update murni berbasis waktu, tiap 5 detik, TIDAK peduli jarak/tempat.
        private const val UPDATE_INTERVAL  = 5_000L
        private const val FASTEST_INTERVAL = 5_000L
        // ✅ UBAH — jarak minimum di-nolkan, supaya update tetap masuk walau device diam
        // (macet, lampu merah, nunggu penumpang). Jarak minimum HANYA dipakai nanti untuk
        // filter akumulasi total jarak tempuh (lihat handleLocationUpdate), bukan untuk trigger update.
        private const val MIN_UPDATE_DISTANCE = 0f
        // Dipakai hanya untuk mengabaikan noise GPS saat menghitung akumulasi jarak total,
        // BUKAN untuk menahan update speed/durasi.
        private const val MIN_DISTANCE_FOR_ACCUMULATION = 5.0f

        private const val ETA_UPDATE_INTERVAL = 30_000L
        private const val GPS_STATUS_CHECK_INTERVAL = 3_000L

        // Actions
        const val ACTION_START_TRACKING    = "START_TRACKING"
        const val ACTION_STOP_TRACKING     = "STOP_TRACKING"
        const val ACTION_UPDATE_PASSENGERS = "UPDATE_PASSENGERS"
        const val ACTION_UPDATE_KONDISI    = "UPDATE_KONDISI"
        const val ACTION_UPDATE_BOARDED    = "UPDATE_BOARDED"

        // Extras
        const val EXTRA_PERJALANAN_ID    = "perjalanan_id"
        const val EXTRA_FIREBASE_BUS_ID  = "firebase_bus_id"

        // broadcast actions untuk status device (LOCAL saja, tidak menyentuh Firebase)
        const val ACTION_GPS_STATUS_UPDATE     = "GPS_STATUS_UPDATE"
        const val ACTION_NETWORK_STATUS_UPDATE = "NETWORK_STATUS_UPDATE"
        const val EXTRA_GPS_ENABLED            = "gps_enabled"
        const val EXTRA_NETWORK_AVAILABLE      = "network_available"

        fun createStartIntent(
            context: Context,
            perjalanId: Int,
            firebaseBusId: String,
            namaBus: String?,
            armadaNomor: String?,
            kelas: String?,
            kapasitas: Int,
            ruteNama: String?,
            polyline: String?,
            kruNama: String?,
            tarif: Double
        ): Intent = Intent(context, GpsTrackingService::class.java).apply {
            action = ACTION_START_TRACKING
            putExtra(EXTRA_PERJALANAN_ID,   perjalanId)
            putExtra(EXTRA_FIREBASE_BUS_ID, firebaseBusId)
            putExtra("nama_bus",     namaBus)
            putExtra("armada_nomor", armadaNomor)
            putExtra("kelas",        kelas)
            putExtra("kapasitas",    kapasitas)
            putExtra("rute_nama",    ruteNama)
            putExtra("polyline",     polyline)
            putExtra("kru_nama",     kruNama)
            putExtra("tarif",        tarif)
        }

        fun createStopIntent(context: Context): Intent =
            Intent(context, GpsTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }

        fun createPassengerUpdateIntent(
            context: Context, perjalanId: Int, currentPassengers: Int
        ): Intent = Intent(context, GpsTrackingService::class.java).apply {
            action = ACTION_UPDATE_PASSENGERS
            putExtra(EXTRA_PERJALANAN_ID,  perjalanId)
            putExtra("current_passengers", currentPassengers)
        }

        fun createBoardingUpdateIntent(
            context: Context, perjalanId: Int, totalBoarded: Int
        ): Intent = Intent(context, GpsTrackingService::class.java).apply {
            action = ACTION_UPDATE_BOARDED
            putExtra(EXTRA_PERJALANAN_ID, perjalanId)
            putExtra("total_boarded",     totalBoarded)
        }

        fun createKondisiUpdateIntent(
            context: Context, perjalanId: Int, kondisi: String
        ): Intent = Intent(context, GpsTrackingService::class.java).apply {
            action = ACTION_UPDATE_KONDISI
            putExtra(EXTRA_PERJALANAN_ID, perjalanId)
            putExtra("kondisi",           kondisi)
        }
    }

    // ============================================
    // FIELDS
    // ============================================

    private var perjalanId:    Int     = 0
    private var firebaseBusId: String  = ""
    private var namaBus:       String? = null
    private var armadaNomor:   String? = null
    private var kelas:         String? = null
    private var kapasitas:     Int     = 40
    private var ruteNama:      String? = null
    private var polyline:      String? = null
    private var kruNama:       String? = null
    private var tarif:         Double  = 0.0
    private var destLat:       Double  = 0.0
    private var destLng:       Double  = 0.0

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback:    LocationCallback
    private lateinit var firebaseManager:     FirebaseManager
    private lateinit var etaCalculator:       ETACalculator
    private lateinit var prefManager:         SharedPrefManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var totalJarak:    Double  = 0.0
    private var lastLocation:  Location? = null
    private var startTime:     Long    = 0L
    private var lastETAUpdate: Long    = 0L
    private var updateCount:   Int     = 0
    private var isTracking:    Boolean = false

    // monitor status GPS device (bukan data lokasi, cuma status on/off)
    private val gpsStatusHandler = Handler(Looper.getMainLooper())
    private var gpsStatusRunnable: Runnable? = null
    private var lastGpsStatus: Boolean? = null

    // monitor status jaringan internet
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetworkStatus: Boolean? = null

    // ============================================
    // LIFECYCLE
    // ============================================

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firebaseManager     = FirebaseManager()
        etaCalculator       = ETACalculator()
        prefManager         = SharedPrefManager.getInstance(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        createNotificationChannel()
        Log.d(TAG, "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING    -> handleStartTracking(intent)
            ACTION_STOP_TRACKING     -> handleStopTracking()
            ACTION_UPDATE_PASSENGERS -> handleUpdatePassengers(intent)
            ACTION_UPDATE_KONDISI    -> handleUpdateKondisi(intent)
            ACTION_UPDATE_BOARDED    -> handleUpdateBoarded(intent)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        stopGpsStatusMonitor()
        stopNetworkMonitor()
        serviceScope.cancel()
        if (firebaseBusId.isNotEmpty()) firebaseManager.clearBusData(firebaseBusId)
        prefManager.setTracking(false)
        isTracking = false
        Log.d(TAG, "Service Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ============================================
    // HANDLE ACTIONS
    // ============================================

    private fun handleStartTracking(intent: Intent) {
        perjalanId    = intent.getIntExtra(EXTRA_PERJALANAN_ID, 0)
        firebaseBusId = intent.getStringExtra(EXTRA_FIREBASE_BUS_ID)
            ?.takeIf { it.isNotEmpty() }
            ?: prefManager.getFirebaseBusId()

        namaBus     = intent.getStringExtra("nama_bus")
        armadaNomor = intent.getStringExtra("armada_nomor")
        kelas       = intent.getStringExtra("kelas")
        kapasitas   = intent.getIntExtra("kapasitas", 40)
        ruteNama    = intent.getStringExtra("rute_nama")
        polyline    = intent.getStringExtra("polyline")
        kruNama     = intent.getStringExtra("kru_nama")
        tarif       = intent.getDoubleExtra("tarif", 0.0)

        if (perjalanId == 0 || polyline.isNullOrEmpty() || firebaseBusId.isEmpty()) {
            Log.e(TAG, "Invalid data! perjalanId=$perjalanId firebaseBusId=$firebaseBusId polyline=${polyline?.length}")
            stopSelf(); return
        }

        val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            Log.e(TAG, "Izin lokasi belum granted, service dihentikan sebelum startForeground()")
            stopSelf()
            return
        }

        Log.d(TAG, "=== CEK POLYLINE === panjang=${polyline?.length} isi=$polyline")

        PolylineUtils.getDestination(polyline!!)?.let {
            destLat = it.latitude; destLng = it.longitude
        }

        Log.d(TAG, "=== HASIL DECODE === destLat=$destLat destLng=$destLng")

        totalJarak    = 0.0; lastLocation = null
        startTime     = System.currentTimeMillis()
        lastETAUpdate = 0L; updateCount   = 0
        isTracking    = true

        prefManager.savePerjalanId(perjalanId)
        prefManager.setTracking(true)
        prefManager.saveFirebaseBusId(firebaseBusId)

        firebaseManager.initializeBus(
            firebaseBusId = firebaseBusId,
            perjalanId    = perjalanId,
            namaBus       = namaBus,
            plateNumber   = armadaNomor,
            busClass      = kelas,
            route         = ruteNama,
            capacity      = kapasitas,
            driver        = kruNama,
            routePolyline = polyline,
            tarif         = tarif
        )

        startForeground(NOTIFICATION_ID, createNotification("Memulai tracking...", 0f, 0.0))
        setupLocationCallback()
        startLocationUpdates()
        startGpsStatusMonitor()
        startNetworkMonitor()

        Log.d(TAG, "Tracking started: $namaBus ($armadaNomor) firebaseBusId=$firebaseBusId tarif=$tarif")
    }

    private fun handleStopTracking() {
        stopLocationUpdates()
        stopGpsStatusMonitor()
        stopNetworkMonitor()
        if (firebaseBusId.isNotEmpty()) {
            firebaseManager.updateStatus(firebaseBusId, "completed")
            firebaseManager.clearBusData(firebaseBusId)
        }
        prefManager.setTracking(false)
        prefManager.clearFirebaseBusId()
        isTracking = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleUpdatePassengers(intent: Intent) {
        val current = intent.getIntExtra("current_passengers", 0)
        if (firebaseBusId.isNotEmpty()) firebaseManager.updatePassengers(firebaseBusId, current)
    }

    private fun handleUpdateBoarded(intent: Intent) {
        val totalBoarded = intent.getIntExtra("total_boarded", 0)
        if (firebaseBusId.isNotEmpty()) firebaseManager.updateBoardedPassengers(firebaseBusId, totalBoarded)
    }

    private fun handleUpdateKondisi(intent: Intent) {
        val kondisi = intent.getStringExtra("kondisi") ?: return
        if (kondisi.isNotEmpty() && firebaseBusId.isNotEmpty()) {
            firebaseManager.updateKondisi(firebaseBusId, kondisi)
        }
    }

    // ============================================
    // LOCATION
    // ============================================

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isTracking) return
                result.locations.forEach { if (it != null) handleLocationUpdate(it) }
            }
        }
    }

    // ✅ UBAH — setMinUpdateDistanceMeters(0f): update WAJIB masuk tiap 5 detik
    // walau device diam di tempat yang sama (macet, lampu merah, dsb).
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission error: ${e.message}"); stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun handleLocationUpdate(location: Location) {
        if (!isTracking) return

        Log.d(TAG, "=== LOCATION MASUK === accuracy=${location.accuracy}m lat=${location.latitude} lng=${location.longitude}")

        if (location.accuracy > 50) {
            Log.d(TAG, "=== LOCATION DIBUANG karena accuracy > 50 ===")
            return
        }

        val lat   = location.latitude
        val lng   = location.longitude
        val speed = location.speed * 3.6f

        // Akumulasi jarak tetap pakai filter noise (MIN_DISTANCE_FOR_ACCUMULATION),
        // tapi ini TIDAK menahan pengiriman update speed/durasi tiap 5 detik.
        lastLocation?.let { last ->
            val dist = last.distanceTo(location)
            if (dist > MIN_DISTANCE_FOR_ACCUMULATION) totalJarak += dist / 1000.0
        }
        lastLocation = location
        updateCount++

        firebaseManager.updateLocationWithTrack(firebaseBusId, lat, lng, speed, totalJarak)

        val now = System.currentTimeMillis()
        Log.d(TAG, "=== CEK KONDISI ETA === destLat=$destLat destLng=$destLng selisihWaktu=${now - lastETAUpdate}")

        if (now - lastETAUpdate > ETA_UPDATE_INTERVAL && destLat != 0.0 && destLng != 0.0) {
            Log.d(TAG, "=== MEMANGGIL updateETA() ===")
            updateETA(lat, lng, speed)
            lastETAUpdate = now
        } else {
            Log.d(TAG, "=== updateETA() DILEWATI karena kondisi belum terpenuhi ===")
        }

        updateNotification("%.1f km/h | %.2f km".format(speed, totalJarak), speed, totalJarak)
        broadcastLocationUpdate(lat, lng, speed, totalJarak)
    }

    // ============================================
    // ETA
    // ============================================

    private fun updateETA(currentLat: Double, currentLng: Double, currentSpeed: Float) {
        serviceScope.launch {
            val result = etaCalculator.calculateETA(currentLat, currentLng, destLat, destLng)
            result.fold(
                onSuccess = { eta ->
                    Log.d(TAG, "=== DIRECTIONS API SUKSES === jarak=${eta.remainingDistanceKm}km waktu=${eta.remainingTimeMinutes}menit")
                    firebaseManager.updateETA(
                        firebaseBusId,
                        eta.remainingDistanceKm,
                        eta.remainingTimeMinutes,
                        eta.estimatedArrival
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "=== DIRECTIONS API GAGAL: ${error.message} — pakai fallback Haversine ===")
                    val eta = etaCalculator.calculateETAManual(
                        currentLat, currentLng, destLat, destLng,
                        if (currentSpeed > 0) currentSpeed else 60f
                    )
                    Log.d(TAG, "=== FALLBACK HASIL === jarak=${eta.remainingDistanceKm}km waktu=${eta.remainingTimeMinutes}menit")
                    firebaseManager.updateETA(
                        firebaseBusId,
                        eta.remainingDistanceKm,
                        eta.remainingTimeMinutes,
                        eta.estimatedArrival
                    )
                }
            )
        }
    }

    // ============================================
    // GPS DEVICE STATUS MONITOR (lokal, tidak masuk Firebase)
    // ============================================

    private fun startGpsStatusMonitor() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        gpsStatusRunnable = object : Runnable {
            override fun run() {
                val gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                if (gpsOn != lastGpsStatus) {
                    lastGpsStatus = gpsOn
                    sendBroadcast(Intent(ACTION_GPS_STATUS_UPDATE).apply {
                        setPackage(packageName)
                        putExtra(EXTRA_GPS_ENABLED, gpsOn)
                    })
                    Log.d(TAG, "GPS status changed: $gpsOn")
                }
                gpsStatusHandler.postDelayed(this, GPS_STATUS_CHECK_INTERVAL)
            }
        }
        gpsStatusHandler.post(gpsStatusRunnable!!)
    }

    private fun stopGpsStatusMonitor() {
        gpsStatusRunnable?.let { gpsStatusHandler.removeCallbacks(it) }
        gpsStatusRunnable = null
        lastGpsStatus = null
    }

    // ============================================
    // NETWORK STATUS MONITOR (lokal, tidak masuk Firebase)
    // ============================================

    private fun startNetworkMonitor() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                broadcastNetworkStatus(true)
            }
            override fun onLost(network: Network) {
                broadcastNetworkStatus(false)
            }
            override fun onUnavailable() {
                broadcastNetworkStatus(false)
            }
        }
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal register network callback: ${e.message}")
        }
    }

    private fun broadcastNetworkStatus(available: Boolean) {
        if (available == lastNetworkStatus) return
        lastNetworkStatus = available
        sendBroadcast(Intent(ACTION_NETWORK_STATUS_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_NETWORK_AVAILABLE, available)
        })
        Log.d(TAG, "Network status changed: $available")
    }

    private fun stopNetworkMonitor() {
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        networkCallback = null
        lastNetworkStatus = null
    }

    // ============================================
    // BROADCAST LOKASI
    // ============================================

    private fun broadcastLocationUpdate(lat: Double, lng: Double, speed: Float, jarak: Double) {
        sendBroadcast(Intent("GPS_LOCATION_UPDATE").apply {
            setPackage(packageName)
            putExtra("latitude",     lat)
            putExtra("longitude",    lng)
            putExtra("speed",        speed)
            putExtra("distance",     jarak)
            putExtra("update_count", updateCount)
        })
    }

    // ============================================
    // NOTIFICATION
    // ============================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "GPS Tracking", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background GPS tracking untuk bus"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String, speed: Float, jarak: Double): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPendingIntent = PendingIntent.getService(
            this, 0,
            Intent(this, GpsTrackingService::class.java).apply { action = ACTION_STOP_TRACKING },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚍 Bus Tracker Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(content: String, speed: Float, jarak: Double) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, createNotification(content, speed, jarak))
    }
}