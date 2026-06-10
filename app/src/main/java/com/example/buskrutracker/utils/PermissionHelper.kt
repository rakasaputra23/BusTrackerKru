package com.example.buskrutracker.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionHelper(private val activity: Activity) {

    companion object {
        const val REQUEST_LOCATION_PERMISSION     = 100
        const val REQUEST_BACKGROUND_LOCATION     = 101
        const val REQUEST_NOTIFICATION_PERMISSION = 102
    }

    private val context: Context = activity.applicationContext

    // ============================================
    // CHECK PERMISSIONS
    // ============================================

    fun isLocationPermissionGranted(): Boolean {
        val fine   = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine && coarse
    }

    fun isBackgroundLocationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun isGpsEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }

    fun hasAllPermissions(): Boolean =
        isLocationPermissionGranted() &&
                isBackgroundLocationGranted() &&
                isNotificationPermissionGranted()

    // ============================================
    // REQUEST PERMISSIONS
    // ============================================

    fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION_PERMISSION
        )
    }

    fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isBackgroundLocationGranted()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                REQUEST_BACKGROUND_LOCATION
            )
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }

    fun requestAllPermissions() {
        when {
            !isLocationPermissionGranted()     -> requestLocationPermission()
            !isBackgroundLocationGranted()     -> requestBackgroundLocationPermission()
            !isNotificationPermissionGranted() -> requestNotificationPermission()
        }
    }

    // ============================================
    // HANDLE RESULT
    // ============================================

    fun handlePermissionResult(requestCode: Int, grantResults: IntArray): Boolean {
        return when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (granted) requestBackgroundLocationPermission()
                granted
            }
            REQUEST_BACKGROUND_LOCATION -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (granted) requestNotificationPermission()
                granted
            }
            REQUEST_NOTIFICATION_PERMISSION ->
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    // ============================================
    // SETTINGS
    // ============================================

    fun openGpsSettings() {
        activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }

    // ============================================
    // MESSAGES
    // ============================================

    fun getLocationRationaleMessage(): String =
        "Aplikasi membutuhkan izin lokasi untuk melacak posisi bus secara real-time. " +
                "Izin ini diperlukan agar penumpang bisa melihat lokasi bus Anda."

    fun getBackgroundRationaleMessage(): String =
        "Izin lokasi latar belakang diperlukan agar tracking tetap berjalan " +
                "meskipun aplikasi tidak sedang dibuka. Pilih 'Izinkan sepanjang waktu'."

    fun getGpsDisabledMessage(): String =
        "GPS tidak aktif. Silakan aktifkan GPS untuk melanjutkan tracking."
}