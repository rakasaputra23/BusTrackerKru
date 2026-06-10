package com.example.buskrutracker.models

import com.google.gson.annotations.SerializedName

data class SelesaiPerjalananResponse(
    @SerializedName("perjalanan")
    val perjalanan: Perjalanan? = null,

    @SerializedName("summary")
    val summary: Summary? = null
) {
    data class Summary(
        @SerializedName("durasi_jam")
        val durasiJam: Int = 0,

        @SerializedName("durasi_menit")
        val durasiMenit: Int = 0,

        @SerializedName("total_penumpang")
        val totalPenumpang: Int = 0,

        @SerializedName("penumpang_naik")
        val penumpangNaik: Int = 0,

        @SerializedName("jarak_km")
        val jarakKm: Double = 0.0,

        @SerializedName("tarif_per_orang")
        val tarifPerOrang: Double = 0.0,

        @SerializedName("total_pendapatan")
        val totalPendapatan: Double = 0.0
    )
}