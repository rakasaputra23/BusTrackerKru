package com.example.buskrutracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.buskrutracker.ui.navigation.AppNavigation
import com.example.buskrutracker.ui.theme.BusKruTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BusKruTrackerTheme {
                AppNavigation()
            }
        }
    }
}