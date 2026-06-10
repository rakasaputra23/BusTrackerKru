package com.example.buskrutracker

import android.app.Application
import com.google.firebase.FirebaseApp

class BusTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}