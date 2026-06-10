package com.example.buskrutracker.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
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
import com.google.android.gms.maps.model.LatLng
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
        private const val UPDATE_INTERVAL  = 5_000L
        private const val FASTEST_INTERVAL = 3_000L
        private const val MIN_DISTANCE     = 5.0f
        private const val ETA_UPDATE_INTERVAL = 30_000L

        // Actions
        const val ACTION_START_TRACKING    = "START_TRACKING"
        const val ACTION_STOP_TRACKING     = "STOP_TRACKING"
        const val ACTION_UPDATE_PASSENGERS = "UPDATE_PASSENGERS"
        const val ACTION_UPDATE_KONDISI    = "UPDATE_KONDISI"
        const val ACTION_UPDATE_BOARDED    = "UPDATE_BOARDED"

        // Extras
        const val EXTRA_PERJALANAN_ID = "perjalanan_id"

        // ============================================
        // STATIC INTENT FACTORIES
        // ============================================

        fun createStartIntent(
            context: Context,
            perjalanId: Int,
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
            putExtra(EXTRA_PERJALANAN_ID, perjalanId)
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

    private var perjalanId:  Int    = 0
    private var namaBus:     String? = null
    private var armadaNomor: String? = null
    private var kelas:       String? = null
    private var kapasitas:   Int    = 40
    private var ruteNama:    String? = null
    private var polyline:    String? = null
    private var kruNama:     String? = null
    private var tarif:       Double = 0.0
    private var destLat:     Double = 0.0
    private var destLng:     Double = 0.0

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var firebaseManager: FirebaseManager
    private lateinit var etaCalculator: ETACalculator
    private lateinit var prefManager: SharedPrefManager

    // Coroutine scope — dibatalkan saat service destroy
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var totalJarak:    Double  = 0.0
    private var lastLocation:  Location? = null
    private var startTime:     Long    = 0L
    private var lastETAUpdate: Long    = 0L
    private var updateCount:   Int     = 0
    private var isTracking:    Boolean = false

    // ============================================
    // LIFECYCLE
    // ============================================

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firebaseManager     = FirebaseManager()
        etaCalculator       = ETACalculator()
        prefManager         = SharedPrefManager.getInstance(this)
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
        serviceScope.cancel()
        if (perjalanId > 0) firebaseManager.clearBusData(perjalanId)
        prefManager.setTracking(false)
        isTracking = false
        Log.d(TAG, "Service Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ============================================
    // HANDLE ACTIONS
    // ============================================

    private fun handleStartTracking(intent: Intent) {
        perjalanId  = intent.getIntExtra(EXTRA_PERJALANAN_ID, 0)
        namaBus     = intent.getStringExtra("nama_bus")
        armadaNomor = intent.getStringExtra("armada_nomor")
        kelas       = intent.getStringExtra("kelas")
        kapasitas   = intent.getIntExtra("kapasitas", 40)
        ruteNama    = intent.getStringExtra("rute_nama")
        polyline    = intent.getStringExtra("polyline")
        kruNama     = intent.getStringExtra("kru_nama")
        tarif       = intent.getDoubleExtra("tarif", 0.0)

        if (perjalanId == 0 || polyline.isNullOrEmpty()) {
            Log.e(TAG, "Invalid data! Cannot start tracking.")
            stopSelf(); return
        }

        PolylineUtils.getDestination(polyline!!)?.let {
            destLat = it.latitude; destLng = it.longitude
        }

        totalJarak   = 0.0;  lastLocation = null
        startTime    = System.currentTimeMillis()
        lastETAUpdate = 0L;  updateCount  = 0
        isTracking   = true

        prefManager.savePerjalanId(perjalanId)
        prefManager.setTracking(true)

        firebaseManager.initializeBus(
            perjalanId, namaBus, armadaNomor, kelas,
            ruteNama, kapasitas, kruNama, polyline, tarif
        )

        startForeground(NOTIFICATION_ID, createNotification("Memulai tracking...", 0f, 0.0))
        setupLocationCallback()
        startLocationUpdates()

        Log.d(TAG, "Tracking started: $namaBus ($armadaNomor) tarif=$tarif")
    }

    private fun handleStopTracking() {
        stopLocationUpdates()
        if (perjalanId > 0) {
            firebaseManager.updateStatus(perjalanId, "completed")
            firebaseManager.clearBusData(perjalanId)
        }
        prefManager.setTracking(false)
        isTracking = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleUpdatePassengers(intent: Intent) {
        val current = intent.getIntExtra("current_passengers", 0)
        firebaseManager.updatePassengers(perjalanId, current)
    }

    private fun handleUpdateBoarded(intent: Intent) {
        val totalBoarded = intent.getIntExtra("total_boarded", 0)
        firebaseManager.updateBoardedPassengers(perjalanId, totalBoarded)
    }

    private fun handleUpdateKondisi(intent: Intent) {
        val kondisi = intent.getStringExtra("kondisi") ?: return
        if (kondisi.isNotEmpty()) firebaseManager.updateKondisi(perjalanId, kondisi)
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

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            .setMinUpdateDistanceMeters(MIN_DISTANCE)
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
        if (location.accuracy > 50) return

        val lat   = location.latitude
        val lng   = location.longitude
        val speed = location.speed * 3.6f

        lastLocation?.let { last ->
            val dist = last.distanceTo(location)
            if (dist > MIN_DISTANCE) totalJarak += dist / 1000.0
        }
        lastLocation = location
        updateCount++

        firebaseManager.updateLocationWithTrack(perjalanId, lat, lng, speed, totalJarak)

        val now = System.currentTimeMillis()
        if (now - lastETAUpdate > ETA_UPDATE_INTERVAL && destLat != 0.0 && destLng != 0.0) {
            updateETA(lat, lng, speed)
            lastETAUpdate = now
        }

        updateNotification("%.1f km/h | %.2f km".format(speed, totalJarak), speed, totalJarak)
        broadcastLocationUpdate(lat, lng, speed, totalJarak)
    }

    // ============================================
    // ETA — pakai coroutine, bukan ExecutorService
    // ============================================

    private fun updateETA(currentLat: Double, currentLng: Double, currentSpeed: Float) {
        serviceScope.launch {
            val result = etaCalculator.calculateETA(currentLat, currentLng, destLat, destLng)
            result.fold(
                onSuccess = { eta ->
                    firebaseManager.updateETA(
                        perjalanId,
                        eta.remainingDistanceKm,
                        eta.remainingTimeMinutes,
                        eta.estimatedArrival
                    )
                },
                onFailure = {
                    // Fallback ke manual
                    val eta = etaCalculator.calculateETAManual(
                        currentLat, currentLng, destLat, destLng,
                        if (currentSpeed > 0) currentSpeed else 60f
                    )
                    firebaseManager.updateETA(
                        perjalanId,
                        eta.remainingDistanceKm,
                        eta.remainingTimeMinutes,
                        eta.estimatedArrival
                    )
                }
            )
        }
    }

    // ============================================
    // BROADCAST
    // ============================================

    private fun broadcastLocationUpdate(lat: Double, lng: Double, speed: Float, jarak: Double) {
        sendBroadcast(Intent("GPS_LOCATION_UPDATE").apply {
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