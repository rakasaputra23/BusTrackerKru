package com.example.buskrutracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.buskrutracker.models.Armada
import com.example.buskrutracker.models.Rute
import com.example.buskrutracker.ui.navigation.Routes
import com.example.buskrutracker.ui.theme.*
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

    LaunchedEffect(Unit) { vm.loadInitialData() }

    // Handle state
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is PersiapanUiState.PerjalananStarted -> {
                navController.navigate(
                    Routes.trackingRoute(
                        state.perjalanId, state.namaBus,
                        state.armadaNomor, state.ruteNama
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

    // Auto-select first item when lists load
    LaunchedEffect(armadaList) { if (selectedArmada == null) selectedArmada = armadaList.firstOrNull() }
    LaunchedEffect(ruteList)   { if (selectedRute == null)   selectedRute   = ruteList.firstOrNull() }

    val isLoading = uiState is PersiapanUiState.Loading

    // Back = logout dialog
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

    // ── Main UI ──
    Box(modifier = Modifier.fillMaxSize().background(Gray50)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ──
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(0.dp),
                colors    = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        modifier = Modifier.size(48.dp),
                        shape    = CircleShape,
                        colors   = CardDefaults.cardColors(containerColor = Gray200)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("👤", fontSize = 24.sp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            vm.getGreeting(),
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Gray900
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp)
                                    .background(Green500, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Online", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Green500)
                        }
                    }
                }
            }

            // ── Content ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // ── Armada Selector ──
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(16.dp),
                        colors    = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "PILIH ARMADA BUS",
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Gray400,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            ExposedDropdownMenuBox(
                                expanded        = armadaExpanded,
                                onExpandedChange = { armadaExpanded = it }
                            ) {
                                Card(
                                    modifier  = Modifier.fillMaxWidth().menuAnchor(),
                                    shape     = RoundedCornerShape(12.dp),
                                    colors    = CardDefaults.cardColors(containerColor = Blue100)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Card(
                                            modifier = Modifier.size(32.dp),
                                            shape    = RoundedCornerShape(8.dp),
                                            colors   = CardDefaults.cardColors(containerColor = Blue600)
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("🚌", fontSize = 16.sp)
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            selectedArmada?.displayName() ?: "Pilih Armada...",
                                            modifier   = Modifier.weight(1f),
                                            fontWeight = FontWeight.Bold,
                                            color      = Gray700,
                                            fontSize   = 14.sp
                                        )
                                        Text("▼", fontSize = 10.sp, color = Blue500)
                                    }
                                }
                                ExposedDropdownMenu(
                                    expanded        = armadaExpanded,
                                    onDismissRequest = { armadaExpanded = false }
                                ) {
                                    armadaList.forEach { armada ->
                                        DropdownMenuItem(
                                            text    = { Text(armada.displayName(), fontWeight = FontWeight.Medium) },
                                            onClick = { selectedArmada = armada; armadaExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Rute Selector ──
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(16.dp),
                        colors    = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "PILIH RUTE TRAYEK",
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Gray400,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            ExposedDropdownMenuBox(
                                expanded        = ruteExpanded,
                                onExpandedChange = { ruteExpanded = it }
                            ) {
                                Card(
                                    modifier  = Modifier.fillMaxWidth().menuAnchor(),
                                    shape     = RoundedCornerShape(12.dp),
                                    colors    = CardDefaults.cardColors(containerColor = Gray100)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Card(
                                            modifier = Modifier.size(32.dp),
                                            shape    = RoundedCornerShape(8.dp),
                                            colors   = CardDefaults.cardColors(containerColor = Gray300)
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("🗺️", fontSize = 16.sp)
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            selectedRute?.namaRute ?: "Pilih Rute...",
                                            modifier   = Modifier.weight(1f),
                                            fontWeight = FontWeight.Bold,
                                            color      = Gray700,
                                            fontSize   = 14.sp
                                        )
                                        Text("▼", fontSize = 10.sp, color = Gray400)
                                    }
                                }
                                ExposedDropdownMenu(
                                    expanded        = ruteExpanded,
                                    onDismissRequest = { ruteExpanded = false }
                                ) {
                                    ruteList.forEach { rute ->
                                        DropdownMenuItem(
                                            text    = { Text(rute.namaRute, fontWeight = FontWeight.Medium) },
                                            onClick = { selectedRute = rute; ruteExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Mulai Button ──
                Column {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(16.dp),
                        colors    = CardDefaults.cardColors(
                            containerColor = if (isLoading) Green500.copy(alpha = 0.6f) else Green500
                        ),
                        elevation = CardDefaults.cardElevation(8.dp),
                        onClick   = {
                            if (!isLoading) {
                                when {
                                    selectedArmada == null -> toastMessage = "⚠️ Pilih armada terlebih dahulu"
                                    selectedRute   == null -> toastMessage = "⚠️ Pilih rute terlebih dahulu"
                                    else -> showConfirmDialog = true
                                }
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(56.dp),
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
                }
            }
        }

        // ── Toast ──
        AnimatedVisibility(
            visible  = toastMessage != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
        ) {
            Card(
                shape  = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Gray800)
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