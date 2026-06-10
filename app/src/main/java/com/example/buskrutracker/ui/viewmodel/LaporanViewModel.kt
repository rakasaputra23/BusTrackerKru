package com.example.buskrutracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel

// LaporanScreen hanya display data — tidak perlu API call
// ViewModel tetap dibuat untuk konsistensi arsitektur
class LaporanViewModel(application: Application) : AndroidViewModel(application)