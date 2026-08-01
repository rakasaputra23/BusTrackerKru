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
    val waktuMulai: String = "", // aman — kolom NOT NULL di DB

    // ✅ FIX: kolom "waktu_selesai" nullable (datetime DEFAULT NULL) — pasti null
    // selagi perjalanan masih berstatus "aktif"
    @SerializedName("waktu_selesai")
    val waktuSelesai: String? = null,

    @SerializedName("total_penumpang")
    val totalPenumpang: Int = 0, // aman — NOT NULL DEFAULT 0

    // ✅ FIX: kolom "jarak_tempuh" nullable (decimal DEFAULT NULL)
    @SerializedName("jarak_tempuh")
    val jarakTempuh: String? = null,

    // ✅ FIX: kolom "durasi_menit" nullable (smallint DEFAULT NULL)
    @SerializedName("durasi_menit")
    val durasiMenit: Int? = null,

    @SerializedName("status")
    val status: String = "", // aman — NOT NULL DEFAULT 'aktif'

    @SerializedName("kondisi_terakhir")
    val kondisiTerakhir: String = "lancar", // aman — NOT NULL DEFAULT 'lancar'

    // ✅ FIX: kolom "catatan" nullable (varchar DEFAULT NULL)
    @SerializedName("catatan")
    val catatan: String? = null,

    // Field pendapatan
    // ✅ FIX: kolom "tarif_snapshot" nullable (decimal DEFAULT NULL) — bisa null
    // kalau tarif tidak ditemukan saat mulaiPerjalanan()
    @SerializedName("tarif_snapshot")
    val tarifSnapshot: Double? = null,

    @SerializedName("total_penumpang_naik")
    val totalPenumpangNaik: Int = 0, // aman — NOT NULL DEFAULT 0

    @SerializedName("total_pendapatan")
    val totalPendapatan: Double = 0.0, // aman — NOT NULL DEFAULT 0.00

    // Relations
    @SerializedName("kru")
    val kru: Kru? = null,

    @SerializedName("armada")
    val armada: Armada? = null,

    @SerializedName("rute")
    val rute: Rute? = null
)