package com.example.buskrutracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.buskrutracker.ui.screens.LaporanScreen
import com.example.buskrutracker.ui.screens.LoginScreen
import com.example.buskrutracker.ui.screens.PersiapanScreen
import com.example.buskrutracker.ui.screens.TrackingScreen
import com.example.buskrutracker.utils.SharedPrefManager
import androidx.compose.ui.platform.LocalContext

object Routes {
    const val LOGIN     = "login"
    const val PERSIAPAN = "persiapan"

    // ✅ FIX: tambah {kapasitas} agar TrackingScreen punya nilai kapasitas
    // yang benar SEJAK AWAL, tidak bergantung sepenuhnya pada loadPerjalananAktif()
    const val TRACKING  = "tracking/{perjalanan_id}/{nama_bus}/{armada_nomor}/{rute_nama}/{kapasitas}"

    const val LAPORAN   = "laporan/{perjalanan_id}/{nama_bus}/{armada_nomor}/{rute_nama}" +
            "/{total_penumpang}/{total_penumpang_naik}" +
            "/{total_pendapatan}/{tarif_per_orang}" +
            "/{jarak_tempuh}/{durasi_jam}/{durasi_menit_sisa}"

    fun trackingRoute(
        perjalanId:  Int,
        namaBus:     String,
        armadaNomor: String,
        ruteNama:    String,
        kapasitas:   Int          // ✅ FIX: parameter baru
    ) = "tracking/$perjalanId/${namaBus.encode()}/${armadaNomor.encode()}/${ruteNama.encode()}/$kapasitas"

    fun laporanRoute(
        perjalanId:        Int,
        namaBus:           String,
        armadaNomor:       String,
        ruteNama:          String,
        totalPenumpang:    Int,
        penumpangNaik:     Int,
        totalPendapatan:   Double,
        tarifPerOrang:     Double,
        jarakTempuh:       Double,
        durasiJam:         Int,
        durasiMenitSisa:   Int
    ) = "laporan/$perjalanId/${namaBus.encode()}/${armadaNomor.encode()}/${ruteNama.encode()}" +
            "/$totalPenumpang/$penumpangNaik/$totalPendapatan/$tarifPerOrang" +
            "/$jarakTempuh/$durasiJam/$durasiMenitSisa"

    // Encode string agar aman sebagai path segment (spasi, /, dll)
    private fun String.encode() =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context       = LocalContext.current
    val prefManager   = SharedPrefManager.getInstance(context)

    // Tentukan start destination berdasarkan status login
    val startDestination = if (prefManager.isLoggedIn()) Routes.PERSIAPAN else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN) {
            LoginScreen(navController = navController)
        }

        composable(Routes.PERSIAPAN) {
            PersiapanScreen(navController = navController)
        }

        composable(
            route = Routes.TRACKING,
            arguments = listOf(
                navArgument("perjalanan_id")  { type = NavType.IntType },
                navArgument("nama_bus")       { type = NavType.StringType },
                navArgument("armada_nomor")   { type = NavType.StringType },
                navArgument("rute_nama")      { type = NavType.StringType },
                // ✅ FIX: argument baru untuk kapasitas
                navArgument("kapasitas")      { type = NavType.IntType; defaultValue = 40 }
            )
        ) { backStack ->
            TrackingScreen(
                navController = navController,
                perjalanId    = backStack.arguments?.getInt("perjalanan_id") ?: 0,
                namaBus       = backStack.arguments?.getString("nama_bus")?.decode() ?: "",
                armadaNomor   = backStack.arguments?.getString("armada_nomor")?.decode() ?: "",
                ruteNama      = backStack.arguments?.getString("rute_nama")?.decode() ?: "",
                // ✅ FIX: teruskan kapasitas dari argumen navigasi
                kapasitasAwal = backStack.arguments?.getInt("kapasitas") ?: 40
            )
        }

        composable(
            route = Routes.LAPORAN,
            arguments = listOf(
                navArgument("perjalanan_id")       { type = NavType.IntType },
                navArgument("nama_bus")            { type = NavType.StringType },
                navArgument("armada_nomor")        { type = NavType.StringType },
                navArgument("rute_nama")           { type = NavType.StringType },
                navArgument("total_penumpang")     { type = NavType.IntType },
                navArgument("total_penumpang_naik"){ type = NavType.IntType },
                navArgument("total_pendapatan")    { type = NavType.StringType },
                navArgument("tarif_per_orang")     { type = NavType.StringType },
                navArgument("jarak_tempuh")        { type = NavType.StringType },
                navArgument("durasi_jam")          { type = NavType.IntType },
                navArgument("durasi_menit_sisa")   { type = NavType.IntType }
            )
        ) { backStack ->
            LaporanScreen(
                navController      = navController,
                perjalanId         = backStack.arguments?.getInt("perjalanan_id") ?: 0,
                namaBus            = backStack.arguments?.getString("nama_bus")?.decode() ?: "",
                armadaNomor        = backStack.arguments?.getString("armada_nomor")?.decode() ?: "",
                ruteNama           = backStack.arguments?.getString("rute_nama")?.decode() ?: "",
                totalPenumpang     = backStack.arguments?.getInt("total_penumpang") ?: 0,
                penumpangNaik      = backStack.arguments?.getInt("total_penumpang_naik") ?: 0,
                totalPendapatan    = backStack.arguments?.getString("total_pendapatan")?.toDoubleOrNull() ?: 0.0,
                tarifPerOrang      = backStack.arguments?.getString("tarif_per_orang")?.toDoubleOrNull() ?: 0.0,
                jarakTempuh        = backStack.arguments?.getString("jarak_tempuh")?.toDoubleOrNull() ?: 0.0,
                durasiJam          = backStack.arguments?.getInt("durasi_jam") ?: 0,
                durasiMenitSisa    = backStack.arguments?.getInt("durasi_menit_sisa") ?: 0
            )
        }
    }
}

private fun String.decode() =
    java.net.URLDecoder.decode(this, "UTF-8")