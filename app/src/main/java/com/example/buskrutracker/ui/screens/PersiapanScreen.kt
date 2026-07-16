package com.example.buskrutracker.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.buskrutracker.models.Armada
import com.example.buskrutracker.models.Rute
import com.example.buskrutracker.ui.navigation.Routes
import com.example.buskrutracker.ui.theme.*
import com.example.buskrutracker.utils.PermissionHelper
import com.example.buskrutracker.viewmodel.PersiapanUiState
import com.example.buskrutracker.viewmodel.PersiapanViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersiapanScreen(
    navController: NavController,
    vm: PersiapanViewModel = viewModel()
) {
    val uiState     by vm.uiState.collectAsState()
    val armadaList  by vm.armadaList.collectAsState()
    val ruteList    by vm.ruteList.collectAsState()

    var selectedArmada by remember { mutableStateOf<Armada?>(null) }
    var selectedRute   by remember { mutableStateOf<Rute?>(null) }
    var armadaExpanded by remember { mutableStateOf(false) }
    var ruteExpanded   by remember { mutableStateOf(false) }

    var toastMessage   by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog       by remember { mutableStateOf(false) }
    var showConfirmDialog      by remember { mutableStateOf(false) }
    var showActiveTripDialog   by remember { mutableStateOf(false) }

    // state & helper untuk cek izin lokasi + GPS sebelum mulai perjalanan
    val context = LocalContext.current
    val activity = context as? Activity
    val permissionHelper = remember { activity?.let { PermissionHelper(it) } }
    var showGpsOffDialog by remember { mutableStateOf(false) }
    var gpsCheckTrigger by remember { mutableStateOf(0) } // trigger re-check setelah balik dari settings

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            toastMessage = "⚠️ Izin lokasi wajib diberikan untuk memulai perjalanan"
        } else {
            gpsCheckTrigger++ // re-check GPS setelah izin diberikan
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            toastMessage = "⚠️ Izin notifikasi dibutuhkan agar tracking tetap terlihat"
        }
    }

    LaunchedEffect(Unit) { vm.loadInitialData() }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is PersiapanUiState.PerjalananStarted -> {
                navController.navigate(
                    Routes.trackingRoute(
                        state.perjalanId, state.namaBus,
                        state.armadaNomor, state.ruteNama,
                        state.kapasitas
                    )
                ) { popUpTo(Routes.PERSIAPAN) { inclusive = true } }
                vm.resetState()
            }
            is PersiapanUiState.ActiveTripDetected -> {
                showActiveTripDialog = true
            }
            is PersiapanUiState.Toast -> {
                toastMessage = state.message; vm.resetState()
            }
            is PersiapanUiState.Error -> {
                toastMessage = state.message; vm.resetState()
            }
            else -> {}
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) { delay(2500); toastMessage = null }
    }

    LaunchedEffect(armadaList) { if (selectedArmada == null) selectedArmada = armadaList.firstOrNull() }
    LaunchedEffect(ruteList)   { if (selectedRute == null)   selectedRute   = ruteList.firstOrNull() }

    val isLoading = uiState is PersiapanUiState.Loading

    BackHandler { showLogoutDialog = true }

    // ── Active Trip Dialog ──
    if (showActiveTripDialog && uiState is PersiapanUiState.ActiveTripDetected) {
        val info = (uiState as PersiapanUiState.ActiveTripDetected).info
        AlertDialog(
            onDismissRequest = {},
            title  = { Text("🚍 Ada Perjalanan Aktif") },
            text   = {
                Text(
                    "Ditemukan perjalanan yang belum diselesaikan:\n\n" +
                            "🚌 Bus  : ${info.busInfo}\n" +
                            "📍 Rute : ${info.ruteInfo}\n\n" +
                            "Pilih tindakan:"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showActiveTripDialog = false
                    vm.resumeOldTrip(info.perjalanan)
                }) { Text("▶ Lanjutkan", color = Green500, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showActiveTripDialog = false
                    vm.cancelOldTrip(info.perjalanan.id)
                    vm.resetState()
                }) { Text("✖ Batalkan", color = Red500) }
            }
        )
    }

    // ── Logout Dialog ──
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title  = { Text("Logout?") },
            text   = { Text("Apakah Anda ingin keluar dari aplikasi?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }) { Text("Ya, Logout", color = Red500, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Batal") }
            }
        )
    }

    // ── Confirm Start Dialog ──
    if (showConfirmDialog && selectedArmada != null && selectedRute != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title  = { Text("🚀 Mulai Perjalanan?") },
            text   = {
                Text(
                    "Bus   : ${selectedArmada!!.displayName()}\n" +
                            "Kelas : ${selectedArmada!!.kelas}\n" +
                            "Rute  : ${selectedRute!!.namaRute}"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    vm.mulaiPerjalanan(selectedArmada!!, selectedRute!!)
                }) { Text("Ya, Mulai", color = Green500, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Batal") }
            }
        )
    }

    // ── Dialog GPS mati ──
    if (showGpsOffDialog) {
        AlertDialog(
            onDismissRequest = { showGpsOffDialog = false },
            title  = { Text("📍 GPS Tidak Aktif") },
            text   = { Text(permissionHelper?.getGpsDisabledMessage() ?: "GPS tidak aktif. Silakan aktifkan GPS untuk melanjutkan.") },
            confirmButton = {
                TextButton(onClick = {
                    showGpsOffDialog = false
                    permissionHelper?.openGpsSettings()
                }) { Text("Aktifkan GPS", color = Green500, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showGpsOffDialog = false }) { Text("Batal") }
            }
        )
    }

    // ── Main UI ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray50)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(White, Gray50))
                    )
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                Brush.linearGradient(listOf(Blue500, Blue600)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👤", fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            vm.getGreeting(),
                            fontSize   = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Gray900
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(Green500, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Siap bertugas",
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = Gray500
                            )
                        }
                    }
                }
            }

            // ── Content ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                    Spacer(Modifier.height(2.dp))

                    Text(
                        "PERSIAPAN PERJALANAN",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Gray400,
                        letterSpacing = 1.2.sp
                    )

                    // ── Armada Selector ──
                    SelectorCard(
                        label     = "Armada Bus",
                        iconEmoji = "🚌",
                        iconBg    = Blue600,
                        chipBg    = Blue50,
                        chevronColor = Blue500,
                        valueText = selectedArmada?.displayName() ?: "Pilih Armada...",
                        expanded  = armadaExpanded,
                        onExpandedChange = { armadaExpanded = it }
                    ) {
                        armadaList.forEach { armada ->
                            DropdownMenuItem(
                                text    = { Text(armada.displayName(), fontWeight = FontWeight.Medium) },
                                onClick = { selectedArmada = armada; armadaExpanded = false }
                            )
                        }
                    }

                    // ── Rute Selector ──
                    SelectorCard(
                        label     = "Rute Trayek",
                        iconEmoji = "🗺️",
                        iconBg    = Gray700,
                        chipBg    = Gray100,
                        chevronColor = Gray400,
                        valueText = selectedRute?.namaRute ?: "Pilih Rute...",
                        expanded  = ruteExpanded,
                        onExpandedChange = { ruteExpanded = it }
                    ) {
                        ruteList.forEach { rute ->
                            DropdownMenuItem(
                                text    = { Text(rute.namaRute, fontWeight = FontWeight.Medium) },
                                onClick = { selectedRute = rute; ruteExpanded = false }
                            )
                        }
                    }
                }

                // ── Mulai Button ──
                Column {
                    Spacer(Modifier.height(16.dp))

                    val buttonScale by animateFloatAsState(
                        targetValue = if (isLoading) 0.98f else 1f,
                        animationSpec = tween(150), label = "buttonScale"
                    )

                    Card(
                        modifier  = Modifier
                            .fillMaxWidth()
                            .scale(buttonScale),
                        shape     = RoundedCornerShape(18.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(0.dp),
                        onClick   = {
                            if (!isLoading) {
                                when {
                                    selectedArmada == null -> toastMessage = "⚠️ Pilih armada terlebih dahulu"
                                    selectedRute   == null -> toastMessage = "⚠️ Pilih rute terlebih dahulu"
                                    permissionHelper == null -> toastMessage = "❌ Terjadi kesalahan sistem"

                                    // cek izin lokasi dulu
                                    !permissionHelper.isLocationPermissionGranted() -> {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }

                                    // cek izin notifikasi (Android 13+)
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            !permissionHelper.isNotificationPermissionGranted() -> {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }

                                    // cek GPS aktif, wajib nyala sebelum mulai
                                    !permissionHelper.isGpsEnabled() -> {
                                        showGpsOffDialog = true
                                    }

                                    else -> showConfirmDialog = true
                                }
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .background(
                                    if (isLoading) {
                                        Brush.linearGradient(listOf(Green500.copy(alpha = 0.6f), Green500.copy(alpha = 0.6f)))
                                    } else {
                                        Brush.linearGradient(listOf(Green500, Green600))
                                    },
                                    RoundedCornerShape(18.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Memproses...", color = White, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🚀", fontSize = 18.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "MULAI PERJALANAN",
                                        color         = White,
                                        fontWeight    = FontWeight.Bold,
                                        fontSize      = 14.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        "Pastikan GPS & koneksi internet aktif sebelum memulai",
                        fontSize   = 11.sp,
                        color      = Gray400,
                        modifier   = Modifier.fillMaxWidth(),
                        textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // ── Toast ──
        AnimatedVisibility(
            visible  = toastMessage != null,
            enter    = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
            exit     = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Card(
                shape     = RoundedCornerShape(24.dp),
                colors    = CardDefaults.cardColors(containerColor = Gray800),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Text(
                    toastMessage ?: "",
                    color    = White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

/**
 * ✅ BARU — komponen selector generik yang dipakai untuk Armada & Rute.
 * Menyeragamkan padding, radius, elevation, dan tipografi supaya kedua
 * selector terlihat konsisten dan lebih clean dibanding sebelumnya.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorCard(
    label: String,
    iconEmoji: String,
    iconBg: Color,
    chipBg: Color,
    chevronColor: Color,
    valueText: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    dropdownContent: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label.uppercase(),
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                color      = Gray400,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            ExposedDropdownMenuBox(
                expanded        = expanded,
                onExpandedChange = onExpandedChange
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .background(chipBg, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(iconBg, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(iconEmoji, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        valueText,
                        modifier   = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        color      = Gray700,
                        fontSize   = 14.sp
                    )
                    Text("▾", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = chevronColor)
                }
                ExposedDropdownMenu(
                    expanded        = expanded,
                    onDismissRequest = { onExpandedChange(false) },
                    content = dropdownContent
                )
            }
        }
    }
}