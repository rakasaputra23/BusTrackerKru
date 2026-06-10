package com.example.buskrutracker.models

import com.google.gson.annotations.SerializedName

data class Perjalanan(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("kru_id")
    val kruId: Int = 0,

    @SerializedName("armada_id")
    val armadaId: Int = 0,

    @SerializedName("rute_id")
    val ruteId: Int = 0,

    @SerializedName("waktu_mulai")
    val waktuMulai: String = "",

    @SerializedName("waktu_selesai")
    val waktuSelesai: String = "",

    @SerializedName("total_penumpang")
    val totalPenumpang: Int = 0,

    @SerializedName("jarak_tempuh")
    val jarakTempuh: String = "",

    @SerializedName("durasi_menit")
    val durasiMenit: Int = 0,

    @SerializedName("status")
    val status: String = "",

    @SerializedName("kondisi_terakhir")
    val kondisiTerakhir: String = "lancar",

    @SerializedName("catatan")
    val catatan: String = "",

    // Field pendapatan
    @SerializedName("tarif_snapshot")
    val tarifSnapshot: Double = 0.0,

    @SerializedName("total_penumpang_naik")
    val totalPenumpangNaik: Int = 0,

    @SerializedName("total_pendapatan")
    val totalPendapatan: Double = 0.0,

    // Relations
    @SerializedName("kru")
    val kru: Kru? = null,

    @SerializedName("armada")
    val armada: Armada? = null,

    @SerializedName("rute")
    val rute: Rute? = null
)