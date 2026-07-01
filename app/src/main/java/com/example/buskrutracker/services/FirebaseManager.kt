package com.example.buskrutracker.services

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class FirebaseManager {

    companion object {
        private const val TAG = "FirebaseManager"

        // ✅ FIX: dinaikkan dari 10 → 300
        // Dengan interval 5 detik, 300 titik = ±25 menit tracking.
        // Untuk perjalanan antar kota 2-4 jam, gunakan node "track_full" terpisah (lihat updateLocationWithTrack).
        private const val MAX_TRACK_POINTS = 300

        private const val DATABASE_URL =
            "https://buskrutracker-default-rtdb.asia-southeast1.firebasedatabase.app/"
    }

    private val databaseRef: DatabaseReference =
        FirebaseDatabase.getInstance(DATABASE_URL).reference

    // ✅ FIX: trackHistory sekarang menyimpan Map dengan timestamp
    private val trackHistory = mutableListOf<Map<String, Any>>()

    // ============================================
    // INITIALIZE BUS
    // ============================================

    /**
     * ✅ FIX: Parameter utama sekarang firebaseBusId (bukan perjalanId).
     * firebaseBusId adalah nilai dari kolom armada.firebase_bus_id di server,
     * dikembalikan oleh API di field "firebase_bus_id" saat mulai perjalanan.
     * Path di Firebase: buses/{firebaseBusId}/...
     */
    fun initializeBus(
        firebaseBusId: String,
        perjalanId: Int,
        namaBus: String?,
        plateNumber: String?,
        busClass: String?,
        route: String?,
        capacity: Int,
        driver: String?,
        routePolyline: String?,
        tarif: Double
    ) {
        val busRef = databaseRef.child("buses").child(firebaseBusId) // ✅ FIX: pakai firebaseBusId

        val busData = mapOf(
            "perjalanId"              to perjalanId,
            "namaBus"                 to (namaBus ?: ""),
            "plateNumber"             to (plateNumber ?: ""),
            "class"                   to (busClass ?: ""),
            "route"                   to (route ?: ""),
            "capacity"                to capacity,
            "currentPassengers"       to 0,
            "totalPassengersBoarded"  to 0,
            "tarif"                   to tarif,
            "driver"                  to (driver ?: ""),
            "status"                  to "active",
            "routePolyline"           to (routePolyline ?: ""),
            "kondisi"                 to "lancar",
            "kondisiUpdate"           to getCurrentTimestampMs(),
            "location"                to mapOf(
                "latitude"   to 0.0,
                "longitude"  to 0.0,
                "speed"      to 0.0,
                "lastUpdate" to getCurrentTimestampMs()
            ),
            "track"        to emptyList<Any>(),
            "eta"          to mapOf(
                "remainingDistance" to 0.0,
                "remainingTime"     to 0,
                "estimatedArrival"  to ""
            ),
            "totalDistance" to 0.0
        )

        busRef.setValue(busData)
            .addOnSuccessListener {
                Log.d(TAG, "Bus initialized: $firebaseBusId | $namaBus | tarif=$tarif")
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to initialize bus: ${it.message}")
            }

        trackHistory.clear()
    }

    // ============================================
    // UPDATE LOCATION
    // ============================================

    /**
     * ✅ FIX: Pakai firebaseBusId sebagai path key.
     * ✅ FIX: Setiap titik track sekarang menyertakan field "timestamp" (unix seconds)
     *         agar usort di Laravel (fetchGpsTrackFromFirebase) bisa mengurutkan dengan benar.
     */
    fun updateLocationWithTrack(
        firebaseBusId: String,
        latitude: Double,
        longitude: Double,
        speed: Float,
        totalDistance: Double
    ) {
        val busRef = databaseRef.child("buses").child(firebaseBusId) // ✅ FIX

        val nowMs      = System.currentTimeMillis()
        val timestampSec = nowMs / 1000L // ✅ FIX: unix timestamp dalam detik

        busRef.child("location").setValue(
            mapOf(
                "latitude"   to latitude,
                "longitude"  to longitude,
                "speed"      to speed.toDouble(),
                "lastUpdate" to nowMs
            )
        )

        // ✅ FIX: tambah field timestamp di setiap titik track
        trackHistory.add(
            mapOf(
                "lat"       to latitude,
                "lng"       to longitude,
                "timestamp" to timestampSec.toDouble()
            )
        )

        // Sliding window — buang titik paling lama jika melebihi batas
        if (trackHistory.size > MAX_TRACK_POINTS) trackHistory.removeAt(0)

        busRef.child("track").setValue(trackHistory.toList())
        busRef.child("totalDistance").setValue(totalDistance)
    }

    // ============================================
    // UPDATE ETA
    // ============================================

    fun updateETA(
        firebaseBusId: String, // ✅ FIX: ganti dari perjalanId
        remainingDistanceKm: Double,
        remainingTimeMinutes: Int,
        estimatedArrival: String
    ) {
        databaseRef.child("buses").child(firebaseBusId).child("eta").setValue( // ✅ FIX
            mapOf(
                "remainingDistance" to remainingDistanceKm,
                "remainingTime"     to remainingTimeMinutes,
                "estimatedArrival"  to estimatedArrival
            )
        )
    }

    // ============================================
    // UPDATE PASSENGERS
    // ============================================

    fun updatePassengers(firebaseBusId: String, currentPassengers: Int) { // ✅ FIX
        databaseRef.child("buses").child(firebaseBusId)
            .child("currentPassengers").setValue(currentPassengers)
    }

    fun updateBoardedPassengers(firebaseBusId: String, totalBoarded: Int) { // ✅ FIX
        databaseRef.child("buses").child(firebaseBusId)
            .child("totalPassengersBoarded").setValue(totalBoarded)
            .addOnSuccessListener { Log.d(TAG, "totalPassengersBoarded updated: $totalBoarded") }
            .addOnFailureListener { Log.e(TAG, "Failed to update boarded: ${it.message}") }
    }

    // ============================================
    // UPDATE STATUS & KONDISI
    // ============================================

    fun updateStatus(firebaseBusId: String, status: String) { // ✅ FIX
        databaseRef.child("buses").child(firebaseBusId)
            .child("status").setValue(status)
    }

    fun updateKondisi(firebaseBusId: String, kondisi: String) { // ✅ FIX
        databaseRef.child("buses").child(firebaseBusId).updateChildren(
            mapOf(
                "kondisi"       to kondisi,
                "kondisiUpdate" to getCurrentTimestampMs()
            )
        )
            .addOnSuccessListener { Log.d(TAG, "Kondisi updated: $kondisi") }
            .addOnFailureListener { Log.e(TAG, "Failed to update kondisi: ${it.message}") }
    }

    // ============================================
    // CLEAR BUS DATA
    // ============================================

    fun clearBusData(firebaseBusId: String) { // ✅ FIX: terima firebaseBusId bukan Int
        if (firebaseBusId.isEmpty()) {
            Log.w(TAG, "clearBusData: firebaseBusId kosong, skip.")
            return
        }
        databaseRef.child("buses").child(firebaseBusId).removeValue()
            .addOnSuccessListener {
                Log.d(TAG, "Bus data cleared: $firebaseBusId")
                trackHistory.clear()
            }
            .addOnFailureListener { Log.e(TAG, "Failed to clear bus data: ${it.message}") }
    }

    // ============================================
    // HELPERS
    // ============================================

    /** Kembalikan unix timestamp dalam milidetik. */
    private fun getCurrentTimestampMs(): Long = System.currentTimeMillis()
}