package com.example.buskrutracker.api

import com.example.buskrutracker.models.ApiResponse
import com.example.buskrutracker.models.Armada
import com.example.buskrutracker.models.MulaiPerjalananResponse
import com.example.buskrutracker.models.Perjalanan
import com.example.buskrutracker.models.Rute
import com.example.buskrutracker.models.SelesaiPerjalananRequest  // ← tambah import
import com.example.buskrutracker.models.SelesaiPerjalananResponse
import com.example.buskrutracker.models.UpdateKondisiRequest       // ← tambah import
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @POST("api/kru/login")
    suspend fun login(
        @Body credentials: Map<String, String>
    ): ApiResponse<Map<String, Any>>

    @GET("api/kru/armada")
    suspend fun getArmada(
        @Header("Authorization") token: String
    ): ApiResponse<List<Armada>>

    @GET("api/kru/rute")
    suspend fun getRute(
        @Header("Authorization") token: String
    ): ApiResponse<List<Rute>>

    @POST("api/kru/perjalanan/mulai")
    suspend fun mulaiPerjalanan(
        @Header("Authorization") token: String,
        @Body data: Map<String, Int>
    ): ApiResponse<MulaiPerjalananResponse>

    // ✅ FIX: ganti Map<String, Any> → UpdateKondisiRequest
    @POST("api/kru/perjalanan/kondisi")
    suspend fun updateKondisi(
        @Header("Authorization") token: String,
        @Body data: UpdateKondisiRequest
    ): ApiResponse<Perjalanan>

    @POST("api/kru/perjalanan/penumpang")
    suspend fun updatePenumpang(
        @Header("Authorization") token: String,
        @Body data: Map<String, Int>
    ): ApiResponse<Perjalanan>

    @GET("api/kru/perjalanan/aktif")
    suspend fun getPerjalananAktif(
        @Header("Authorization") token: String
    ): ApiResponse<Perjalanan>

    // ✅ FIX: ganti Map<String, Any> → SelesaiPerjalananRequest
    @POST("api/kru/perjalanan/selesai")
    suspend fun selesaiPerjalanan(
        @Header("Authorization") token: String,
        @Body data: SelesaiPerjalananRequest
    ): ApiResponse<SelesaiPerjalananResponse>

    @POST("api/kru/logout")
    suspend fun logout(
        @Header("Authorization") token: String
    ): ApiResponse<Any>
}