package com.example.buskrutracker.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ETACalculator {

    companion object {
        private const val TAG = "ETACalculator"
        private const val GOOGLE_API_KEY = "AIzaSyDtP56h8vDFWJ5jSL9c4bFoIRwG6gTp2u8"
        // ✅ FIX: ganti dari Directions API (legacy, sudah tidak aktif) ke Routes API (baru)
        private const val ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
    }

    data class ETAResult(
        val remainingDistanceKm: Double,
        val remainingTimeMinutes: Int,
        val estimatedArrival: String
    )

    // ============================================
    // CALCULATE ETA — pakai Routes API (POST + JSON body)
    // ============================================

    suspend fun calculateETA(
        currentLat: Double,
        currentLng: Double,
        destLat: Double,
        destLng: Double
    ): Result<ETAResult> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("origin", JSONObject().apply {
                    put("location", JSONObject().apply {
                        put("latLng", JSONObject().apply {
                            put("latitude", currentLat)
                            put("longitude", currentLng)
                        })
                    })
                })
                put("destination", JSONObject().apply {
                    put("location", JSONObject().apply {
                        put("latLng", JSONObject().apply {
                            put("latitude", destLat)
                            put("longitude", destLng)
                        })
                    })
                })
                put("travelMode", "DRIVE")
                put("routingPreference", "TRAFFIC_AWARE")
            }

            val url  = URL(ROUTES_API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod  = "POST"
                connectTimeout = 10_000
                readTimeout    = 10_000
                doOutput       = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Goog-Api-Key", GOOGLE_API_KEY)
                // FieldMask WAJIB di Routes API, kalau tidak ada response akan kosong/ditolak
                setRequestProperty("X-Goog-FieldMask", "routes.duration,routes.distanceMeters")
            }

            conn.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                parseRoutesResponse(response)
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                conn.disconnect()
                Log.e(TAG, "HTTP Error ${conn.responseCode}: $errorBody")
                Result.failure(Exception("HTTP Error: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating ETA: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseRoutesResponse(jsonResponse: String): Result<ETAResult> {
        return try {
            val json   = JSONObject(jsonResponse)
            val routes = json.optJSONArray("routes")

            if (routes == null || routes.length() == 0) {
                return Result.failure(Exception("Routes API: tidak ada rute ditemukan"))
            }

            val route = routes.getJSONObject(0)
            val distanceMeters = route.getInt("distanceMeters")
            val distanceKm     = distanceMeters / 1000.0

            // duration dari Routes API formatnya string, misal "125s"
            val durationStr = route.getString("duration")
            val durationSec = durationStr.removeSuffix("s").toIntOrNull() ?: 0
            val durationMin = durationSec / 60

            val arrivalMillis = System.currentTimeMillis() + (durationSec * 1000L)

            Result.success(ETAResult(distanceKm, durationMin, formatTimestamp(arrivalMillis)))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Routes API response: ${e.message}")
            Result.failure(e)
        }
    }

    // ============================================
    // FALLBACK — Haversine manual (tidak berubah)
    // ============================================

    fun calculateETAManual(
        currentLat: Double, currentLng: Double,
        destLat: Double,    destLng: Double,
        averageSpeedKmh: Float = 60f
    ): ETAResult {
        val distance      = calculateDistance(currentLat, currentLng, destLat, destLng)
        val speed         = if (averageSpeedKmh > 0) averageSpeedKmh else 60f
        val durationMin   = ((distance / speed) * 60).toInt()
        val arrivalMillis = System.currentTimeMillis() + (durationMin * 60_000L)
        return ETAResult(distance, durationMin, formatTimestamp(arrivalMillis))
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r    = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2).let { it * it }
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(millis))
}