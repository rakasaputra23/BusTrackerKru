package com.example.buskrutracker.models

import com.google.gson.annotations.SerializedName

data class MulaiPerjalananResponse(
    @SerializedName("perjalanan")
    val perjalanan: Perjalanan? = null,

    @SerializedName("firebase_bus_id")
    val firebaseBusId: String = "",

    @SerializedName("tarif_berlaku")
    val tarifBerlaku: Double = 0.0
)