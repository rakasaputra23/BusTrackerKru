package com.example.buskrutracker.models

import com.google.gson.annotations.SerializedName

data class GantiDriverResponse(
    @SerializedName("perjalanan") val perjalanan: Perjalanan? = null,
    @SerializedName("firebase_bus_id") val firebaseBusId: String = "",
    @SerializedName("driver_baru") val driverBaru: String = "",
    @SerializedName("kru_id_baru") val kruIdBaru: Int = 0
)