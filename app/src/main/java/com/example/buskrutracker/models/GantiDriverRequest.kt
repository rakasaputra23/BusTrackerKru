package com.example.buskrutracker.models

import com.google.gson.annotations.SerializedName

data class GantiDriverRequest(
    @SerializedName("perjalanan_id") val perjalananId: Int,
    @SerializedName("kru_id_baru") val kruIdBaru: Int
)