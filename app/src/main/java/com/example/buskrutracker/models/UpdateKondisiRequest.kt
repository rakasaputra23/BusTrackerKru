package com.example.buskrutracker.models

import com.google.gson.annotations.SerializedName

data class UpdateKondisiRequest(
    @SerializedName("perjalanan_id")
    val perjalananId: Int,

    @SerializedName("kondisi")
    val kondisi: String
)
