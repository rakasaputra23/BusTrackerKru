package com.example.buskrutracker.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.buskrutracker.models.Kru
import com.google.gson.Gson

class SharedPrefManager private constructor(context: Context) {

    companion object {
        private const val PREF_NAME         = "BusKruTrackerPrefs"
        private const val KEY_TOKEN         = "token"
        private const val KEY_USER          = "user"
        private const val KEY_PERJALANAN_ID = "perjalanan_id"
        private const val KEY_IS_TRACKING   = "is_tracking"
        private const val KEY_IS_LOGGED_IN  = "is_logged_in"

        @Volatile
        private var instance: SharedPrefManager? = null

        fun getInstance(context: Context): SharedPrefManager =
            instance ?: synchronized(this) {
                instance ?: SharedPrefManager(context.applicationContext).also { instance = it }
            }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // ============================================
    // LOGIN & LOGOUT
    // ============================================

    fun saveLoginData(token: String, kru: Kru) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, gson.toJson(kru))
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    // ============================================
    // TOKEN
    // ============================================

    fun getToken(): String? {
        val token = prefs.getString(KEY_TOKEN, null)
        return if (token != null) "Bearer $token" else null
    }

    fun getRawToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    // ============================================
    // USER DATA
    // ============================================

    fun getUser(): Kru? {
        val userJson = prefs.getString(KEY_USER, null) ?: return null
        return gson.fromJson(userJson, Kru::class.java)
    }

    fun updateUser(kru: Kru) {
        prefs.edit().putString(KEY_USER, gson.toJson(kru)).apply()
    }

    fun getUserId(): Int      = getUser()?.id ?: 0
    fun getUsername(): String = getUser()?.username ?: ""
    fun getDriverName(): String = getUser()?.driver ?: ""

    // ============================================
    // PERJALANAN
    // ============================================

    fun savePerjalanId(perjalanId: Int) {
        prefs.edit().putInt(KEY_PERJALANAN_ID, perjalanId).apply()
    }

    fun getPerjalanId(): Int = prefs.getInt(KEY_PERJALANAN_ID, 0)

    fun clearPerjalanId() {
        prefs.edit().remove(KEY_PERJALANAN_ID).apply()
    }

    // ============================================
    // TRACKING STATUS
    // ============================================

    fun setTracking(isTracking: Boolean) {
        prefs.edit().putBoolean(KEY_IS_TRACKING, isTracking).apply()
    }

    fun isTracking(): Boolean = prefs.getBoolean(KEY_IS_TRACKING, false)

    fun hasActivePerjalanan(): Boolean = getPerjalanId() > 0 && isTracking()

    // ============================================
    // GENERIC HELPERS
    // ============================================

    fun saveString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getString(key: String, default: String = ""): String = prefs.getString(key, default) ?: default

    fun saveInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    fun getInt(key: String, default: Int = 0): Int = prefs.getInt(key, default)

    fun saveBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)
}