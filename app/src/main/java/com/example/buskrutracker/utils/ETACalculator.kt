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
        private const val GOOGLE_API_KEY = "AIzaSyDDDvRiEfPqb4fUMJQ2KSxAlwm5UJa4kxs"
        private const val DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json"
    }

    data class ETAResult(
        val remainingDistanceKm: Double,
        val remainingTimeMinutes: Int,
        val estimatedArrival: String
    )

    // ============================================
    // CALCULATE ETA — suspend, tidak perlu Executor
    // ============================================

    suspend fun calculateETA(
        currentLat: Double,
        currentLng: Double,
        destLat: Double,
        destLng: Double
    ): Result<ETAResult> = withContext(Dispatchers.IO) {
        try {
            val urlString = String.format(
                Locale.US,
                "%s?origin=%f,%f&destination=%f,%f&key=%s&mode=driving",
                DIRECTIONS_API_URL, currentLat, currentLng, destLat, destLng, GOOGLE_API_KEY
            )

            val url  = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod  = "GET"
                connectTimeout = 10_000
                readTimeout    = 10_000
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                parseETAResponse(response)
            } else {
                conn.disconnect()
                Result.failure(Exception("HTTP Error: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating ETA: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseETAResponse(jsonResponse: String): Result<ETAResult> {
        return try {
            val json   = JSONObject(jsonResponse)
            val status = json.getString("status")

            if (status != "OK") return Result.failure(Exception("Directions API Error: $status"))

            val leg            = json.getJSONArray("routes").getJSONObject(0)
                .getJSONArray("legs").getJSONObject(0)
            val distanceKm     = leg.getJSONObject("distance").getDouble("value") / 1000.0
            val durationSec    = leg.getJSONObject("duration").getInt("value")
            val durationMin    = durationSec / 60
            val arrivalMillis  = System.currentTimeMillis() + (durationSec * 1000L)

            Result.success(ETAResult(distanceKm, durationMin, formatTimestamp(arrivalMillis)))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ETA: ${e.message}")
            Result.failure(e)
        }
    }

    // ============================================
    // FALLBACK — Haversine manual
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