package com.example.buskrutracker.models

import com.google.gson.annotations.SerializedName

data class Kru(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("driver")
    val driver: String = "",

    @SerializedName("username")
    val username: String = "",

    @SerializedName("status")
    val status: String = "",

    @SerializedName("token")
    val token: String = ""
)