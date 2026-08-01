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
    val polyline: String = "", // aman — kolom NOT NULL di DB

    // ✅ FIX: kolom "track_coordinates" nullable (json DEFAULT NULL) di DB
    @SerializedName("track_coordinates")
    val trackCoordinates: List<TrackCoordinate>? = null,

    // ✅ FIX: kolom "jarak" nullable (decimal DEFAULT NULL) di DB
    @SerializedName("jarak")
    val jarak: String? = null,

    // ✅ FIX: kolom "estimasi_waktu" nullable (smallint DEFAULT NULL) di DB
    @SerializedName("estimasi_waktu")
    val estimasiWaktu: Int? = null,

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