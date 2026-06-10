package com.example.buskrutracker.services

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FirebaseManager {

    companion object {
        private const val TAG = "FirebaseManager"
        private const val MAX_TRACK_POINTS = 10
        private const val DATABASE_URL =
            "https://buskrutracker-default-rtdb.asia-southeast1.firebasedatabase.app/"
    }

    private val databaseRef: DatabaseReference =
        FirebaseDatabase.getInstance(DATABASE_URL).reference

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val trackHistory = mutableListOf<Map<String, Double>>()

    // ============================================
    // INITIALIZE BUS
    // ============================================

    fun initializeBus(
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
        val busKey = "bus_$perjalanId"
        val busRef = databaseRef.child("buses").child(busKey)

        val busData = mapOf(
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
            "kondisiUpdate"           to getCurrentTimestamp(),
            "location"                to mapOf(
                "latitude"   to 0.0,
                "longitude"  to 0.0,
                "speed"      to 0.0,
                "lastUpdate" to getCurrentTimestamp()
            ),
            "track"         to emptyList<Any>(),
            "eta"           to mapOf(
                "remainingDistance" to 0.0,
                "remainingTime"     to 0,
                "estimatedArrival"  to ""
            ),
            "totalDistance" to 0.0
        )

        busRef.setValue(busData)
            .addOnSuccessListener { Log.d(TAG, "Bus initialized: $busKey | $namaBus | tarif=$tarif") }
            .addOnFailureListener { Log.e(TAG, "Failed to initialize bus: ${it.message}") }

        trackHistory.clear()
    }

    // ============================================
    // UPDATE LOCATION
    // ============================================

    fun updateLocationWithTrack(
        perjalanId: Int,
        latitude: Double,
        longitude: Double,
        speed: Float,
        totalDistance: Double
    ) {
        val busRef = databaseRef.child("buses").child("bus_$perjalanId")

        busRef.child("location").setValue(
            mapOf(
                "latitude"   to latitude,
                "longitude"  to longitude,
                "speed"      to speed.toDouble(),
                "lastUpdate" to getCurrentTimestamp()
            )
        )

        trackHistory.add(mapOf("lat" to latitude, "lng" to longitude))
        if (trackHistory.size > MAX_TRACK_POINTS) trackHistory.removeAt(0)

        busRef.child("track").setValue(trackHistory.toList())
        busRef.child("totalDistance").setValue(totalDistance)
    }

    // ============================================
    // UPDATE ETA
    // ============================================

    fun updateETA(
        perjalanId: Int,
        remainingDistanceKm: Double,
        remainingTimeMinutes: Int,
        estimatedArrival: String
    ) {
        databaseRef.child("buses").child("bus_$perjalanId").child("eta").setValue(
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

    fun updatePassengers(perjalanId: Int, currentPassengers: Int) {
        databaseRef.child("buses").child("bus_$perjalanId")
            .child("currentPassengers").setValue(currentPassengers)
    }

    fun updateBoardedPassengers(perjalanId: Int, totalBoarded: Int) {
        databaseRef.child("buses").child("bus_$perjalanId")
            .child("totalPassengersBoarded").setValue(totalBoarded)
            .addOnSuccessListener { Log.d(TAG, "totalPassengersBoarded updated: $totalBoarded") }
            .addOnFailureListener { Log.e(TAG, "Failed to update boarded: ${it.message}") }
    }

    // ============================================
    // UPDATE STATUS & KONDISI
    // ============================================

    fun updateStatus(perjalanId: Int, status: String) {
        databaseRef.child("buses").child("bus_$perjalanId")
            .child("status").setValue(status)
    }

    fun updateKondisi(perjalanId: Int, kondisi: String) {
        databaseRef.child("buses").child("bus_$perjalanId").updateChildren(
            mapOf(
                "kondisi"       to kondisi,
                "kondisiUpdate" to getCurrentTimestamp()
            )
        )
            .addOnSuccessListener { Log.d(TAG, "Kondisi updated: $kondisi") }
            .addOnFailureListener { Log.e(TAG, "Failed to update kondisi: ${it.message}") }
    }

    // ============================================
    // CLEAR BUS DATA
    // ============================================

    fun clearBusData(perjalanId: Int) {
        databaseRef.child("buses").child("bus_$perjalanId").removeValue()
            .addOnSuccessListener {
                Log.d(TAG, "Bus data cleared: bus_$perjalanId")
                trackHistory.clear()
            }
            .addOnFailureListener { Log.e(TAG, "Failed to clear bus data: ${it.message}") }
    }

    private fun getCurrentTimestamp(): String = dateFormat.format(Date())
}