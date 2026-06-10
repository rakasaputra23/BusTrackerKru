package com.example.buskrutracker.models

import com.google.gson.annotations.SerializedName

// ✅ Request body untuk selesaiPerjalanan
data class SelesaiPerjalananRequest(
    @SerializedName("perjalanan_id")
    val perjalananId: Int,

    @SerializedName("total_penumpang")
    val totalPenumpang: Int,

    @SerializedName("total_penumpang_naik")
    val totalPenumpangNaik: Int,

    @SerializedName("jarak_tempuh")
    val jarakTempuh: Double,

    @SerializedName("catatan")
    val catatan: String = "Perjalanan selesai"
)