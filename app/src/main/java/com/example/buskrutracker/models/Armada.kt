package com.example.buskrutracker.models

import com.google.gson.annotations.SerializedName

data class Armada(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("nama_bus")
    val namaBus: String = "",

    @SerializedName("plat_nomor")
    val platNomor: String = "",

    @SerializedName("kelas")
    val kelas: String = "",

    @SerializedName("kapasitas")
    val kapasitas: Int = 40,

    @SerializedName("status")
    val status: String = "",

    // ✅ FIX: kolom "firebase_bus_id" nullable di DB (DEFAULT NULL untuk armada
    // yang belum diassign ke Firebase). Non-null String sebelumnya berisiko
    // null-crash saat deserialisasi Gson.
    @SerializedName("firebase_bus_id")
    val firebaseBusId: String? = null,

    // ✅ FIX: timestamp nullable di DB
    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null
) {
    // Untuk Dropdown Compose — menggantikan toString() di Spinner
    fun displayName(): String =
        if (namaBus.isNotEmpty()) "$namaBus ($platNomor)" else platNomor
}