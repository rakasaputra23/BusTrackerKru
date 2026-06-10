package com.example.buskrutracker.models

import com.google.gson.annotations.SerializedName

data class Rute(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("nama_rute")
    val namaRute: String = "",

    @SerializedName("kota_asal")
    val kotaAsal: String = "",

    @SerializedName("kota_tujuan")
    val kotaTujuan: String = "",

    @SerializedName("polyline")
    val polyline: String = "",

    @SerializedName("track_coordinates")
    val trackCoordinates: List<TrackCoordinate> = emptyList(),

    @SerializedName("jarak")
    val jarak: String = "",

    @SerializedName("estimasi_waktu")
    val estimasiWaktu: Int = 0,

    @SerializedName("tarif")
    val tarif: TarifDetail? = null
) {
    // Helper — sama dengan Java getTarifHarga()
    fun getTarifHarga(): Double = tarif?.getHargaAsDouble() ?: 0.0

    data class TarifDetail(
        @SerializedName("id")
        val id: Int = 0,

        @SerializedName("rute_id")
        val ruteId: Int = 0,

        // API mengembalikan String "85000.00"
        @SerializedName("harga")
        val harga: String = "0"
    ) {
        fun getHargaAsDouble(): Double = harga.toDoubleOrNull() ?: 0.0
    }

    data class TrackCoordinate(
        @SerializedName("lat")
        val lat: Double = 0.0,

        @SerializedName("lng")
        val lng: Double = 0.0
    )
}