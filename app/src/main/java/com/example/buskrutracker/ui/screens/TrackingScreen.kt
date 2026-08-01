package com.example.buskrutracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.buskrutracker.models.Kru
import com.example.buskrutracker.ui.navigation.Routes
import com.example.buskrutracker.ui.theme.*
import com.example.buskrutracker.viewmodel.TrackingUiState
import com.example.buskrutracker.viewmodel.TrackingViewModel
import kotlinx.coroutines.delay

@Composable
fun TrackingScreen(
    navController: NavController,
    perjalanId:    Int,
    namaBus:       String,
    armadaNomor:   String,
    ruteNama:      String,
    kapasitasAwal: Int = 40,
    vm: TrackingViewModel = viewModel()
) {
    val uiState          by vm.uiState.collectAsState()
    val jumlahPenumpang  by vm.jumlahPenumpang.collectAsState()
    val kapasitas        by vm.kapasitas.collectAsState()
    val kondisi          by vm.kondisi.collectAsState()
    val stats            by vm.stats.collectAsState()
    val gpsEnabled       by vm.gpsEnabled.collectAsState()
    val networkAvailable by vm.networkAvailable.collectAsState()

    // ✅ BARU — state untuk fitur ganti driver
    val currentDriver       by vm.currentDriver.collectAsState()
    val daftarKru           by vm.daftarKru.collectAsState()
    val isLoadingKru        by vm.isLoadingKru.collectAsState()
    val isGantiDriverLoading by vm.isGantiDriverLoading.collectAsState()

    var toastMessage     by remember { mutableStateOf<String?>(null) }
    var showAkhiriDialog by remember { mutableStateOf(false) }
    var showBackDialog   by remember { mutableStateOf(false) }
    var showGantiDriverDialog by remember { mutableStateOf(false) } // ✅ BARU

    val sep    = if (ruteNama.contains("→")) "→" else "-"
    val parts  = ruteNama.split(sep)
    val origin = parts.getOrNull(0)?.trim()?.uppercase() ?: "ASAL"
    val dest   = parts.getOrNull(1)?.trim()?.uppercase() ?: "TUJUAN"

    LaunchedEffect(perjalanId) { vm.init(perjalanId, kapasitasAwal) }

    DisposableEffect(Unit) { onDispose { vm.unregisterReceiver() } }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is TrackingUiState.SelesaiSuccess -> {
                val r = state.result
                navController.navigate(
                    Routes.laporanRoute(
                        r.perjalanId, r.namaBus, r.armadaNomor, r.ruteNama,
                        r.totalPenumpang, r.penumpangNaik,
                        r.totalPendapatan, r.tarifPerOrang,
                        r.jarakTempuh, r.durasiJam, r.durasiMenitSisa
                    )
                ) { popUpTo(0) { inclusive = true } }
                vm.resetState()
            }
            is TrackingUiState.Toast  -> { toastMessage = state.message; vm.resetState() }
            is TrackingUiState.Error  -> { toastMessage = state.message; vm.resetState() }
            else -> {}
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) { delay(2000); toastMessage = null }
    }

    LaunchedEffect(gpsEnabled) {
        if (!gpsEnabled) toastMessage = "⚠️ GPS mati! Nyalakan kembali agar tracking akurat"
    }

    BackHandler { showBackDialog = true }

    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            title  = { Text("⚠️ Peringatan") },
            text   = { Text("Perjalanan masih aktif!\n\nGunakan tombol 'Akhiri Perjalanan' untuk keluar dengan aman.") },
            confirmButton = {
                TextButton(onClick = { showBackDialog = false }) {
                    Text("Mengerti", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showAkhiriDialog) {
        val busInfo = if (namaBus.isNotEmpty()) "$namaBus ($armadaNomor)" else armadaNomor
        AlertDialog(
            onDismissRequest = { showAkhiriDialog = false },
            title  = { Text("⬛ Akhiri Perjalanan?") },
            text   = {
                Text(
                    "Apakah Anda yakin ingin mengakhiri perjalanan ini?\n\n" +
                            "🚍 Bus: $busInfo\n" +
                            "👥 Penumpang naik: ${vm.getBoardedCount()} orang\n" +
                            "🪑 Saat ini di bus: $jumlahPenumpang orang\n" +
                            "📍 Jarak tempuh: ${"%.2f".format(stats.jarakKm)} km"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAkhiriDialog = false
                    vm.akhiriPerjalanan(perjalanId, namaBus, armadaNomor, ruteNama)
                }) { Text("Ya, Akhiri", color = Red500, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAkhiriDialog = false }) { Text("Batal") }
            }
        )
    }

    // ✅ BARU — dialog pilih driver pengganti
    if (showGantiDriverDialog) {
        GantiDriverDialog(
            currentDriver     = currentDriver,
            daftarKru         = daftarKru,
            isLoading         = isLoadingKru,
            isSubmitting      = isGantiDriverLoading,
            onPilih           = { kru -> vm.gantiDriver(kru) },
            onDismiss         = { showGantiDriverDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray900)
    ) {

        // ── Top Bar ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Gray800)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "RUTE AKTIF",
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Gray400,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(origin, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(" → ", fontSize = 13.sp, color = Gray500)
                        Text(dest, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // ── Status Badges (Network + GPS) ──
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(
                        label      = if (networkAvailable) "ONLINE" else "OFFLINE",
                        isActive   = networkAvailable,
                        activeBg   = Gray700,
                        activeDot  = Gray400,
                        activeText = Gray300,
                        inactiveBg = Red900,
                        inactiveDot = Red500,
                        inactiveText = Red500
                    )
                    StatusBadge(
                        label      = if (gpsEnabled) "GPS ON" else "GPS OFF",
                        isActive   = gpsEnabled,
                        activeBg   = Green800,
                        activeDot  = Green500,
                        activeText = Green500,
                        inactiveBg = Red900,
                        inactiveDot = Red500,
                        inactiveText = Red500
                    )
                }
            }

            // ✅ BARU — baris info driver, rapi & jelas bisa ditekan
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Gray700, RoundedCornerShape(10.dp))
                    .clickable {
                        vm.loadDaftarKru()
                        showGantiDriverDialog = true
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = Gray400,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "DRIVER",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Gray500,
                        letterSpacing = 1.sp
                    )
                    Text(
                        currentDriver.ifBlank { "Belum diketahui" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Ganti Driver",
                    tint = Blue600,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // ── Main Content ──
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                "TOTAL PENUMPANG",
                fontSize   = 9.sp,
                fontWeight = FontWeight.Bold,
                color      = Gray500,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "$jumlahPenumpang",
                    fontSize   = 90.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                    lineHeight = 90.sp
                )
                Text(
                    "/$kapasitas",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Gray400,
                    modifier   = Modifier.padding(top = 12.dp, start = 4.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier  = Modifier.weight(1f).fillMaxHeight(),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Gray800),
                    onClick   = { vm.kurangPenumpang() }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("−", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Red500)
                        Text("TURUN", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = Red500, letterSpacing = 1.sp)
                    }
                }
                Card(
                    modifier  = Modifier.weight(1f).fillMaxHeight(),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Blue600),
                    elevation = CardDefaults.cardElevation(8.dp),
                    onClick   = { vm.tambahPenumpang() }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("+", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("NAIK", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, letterSpacing = 1.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "LAPOR KONDISI BUS:",
                fontSize   = 9.sp,
                fontWeight = FontWeight.Bold,
                color      = Gray500,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusButton(
                    label     = "LANCAR",
                    emoji     = "✓",
                    emojiColor = Color.White,
                    labelColor = Color.White,
                    bgColor   = Green500,
                    isActive  = kondisi == "lancar",
                    modifier  = Modifier.weight(1f),
                    onClick   = { vm.updateKondisi("lancar") }
                )
                StatusButton(
                    label     = "MACET",
                    emoji     = "⚠",
                    emojiColor = Yellow400,
                    labelColor = Yellow400,
                    bgColor   = Gray800,
                    isActive  = kondisi == "macet",
                    modifier  = Modifier.weight(1f),
                    onClick   = { vm.updateKondisi("macet") }
                )
                StatusButton(
                    label     = "MOGOK",
                    emoji     = "🔧",
                    emojiColor = Red500,
                    labelColor = Red500,
                    bgColor   = Gray800,
                    isActive  = kondisi == "mogok",
                    modifier  = Modifier.weight(1f),
                    onClick   = { vm.updateKondisi("mogok") }
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Kecepatan",    "${"%.1f".format(stats.speedKmh)} km/h")
                StatItem("Jarak Tempuh", "${"%.2f".format(stats.jarakKm)} km")
                StatItem("Durasi",       "${stats.durasiMenit / 60}j ${stats.durasiMenit % 60}m")
            }
        }

        // ── Bottom Action ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Gray800)
                .padding(16.dp)
        ) {
            Column {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(12.dp),
                    colors    = CardDefaults.cardColors(containerColor = Red900),
                    onClick   = { if (uiState !is TrackingUiState.Loading) showAkhiriDialog = true }
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState is TrackingUiState.Loading) {
                            CircularProgressIndicator(
                                color = Red300, modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⬛", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "AKHIRI PERJALANAN",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = Red300,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Toast ──
        if (toastMessage != null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape  = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Gray700)
                ) {
                    Text(
                        toastMessage ?: "",
                        color    = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

/**
 * ✅ BARU — Dialog pilih driver pengganti.
 * Menampilkan daftar kru aktif dari server, dengan indikator driver yang
 * sedang aktif, loading state saat fetch, dan loading overlay saat submit.
 */
@Composable
private fun GantiDriverDialog(
    currentDriver: String,
    daftarKru:     List<Kru>,
    isLoading:     Boolean,
    isSubmitting:  Boolean,
    onPilih:       (Kru) -> Unit,
    onDismiss:     () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        containerColor = Gray800,
        title = {
            Text("Ganti Driver", fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Box(modifier = Modifier.heightIn(min = 80.dp, max = 320.dp)) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Blue600, modifier = Modifier.size(28.dp))
                        }
                    }
                    daftarKru.isEmpty() -> {
                        Text(
                            "Tidak ada data kru tersedia.",
                            color = Gray400,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(daftarKru, key = { it.id }) { kru ->
                                val isActive = kru.driver == currentDriver
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isSubmitting && !isActive) { onPilih(kru) }
                                        .background(
                                            if (isActive) Blue600.copy(alpha = 0.15f) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            kru.driver,
                                            fontSize = 14.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isActive) Blue600 else Color.White
                                        )
                                        Text(
                                            "@${kru.username}",
                                            fontSize = 11.sp,
                                            color = Gray500
                                        )
                                    }
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Driver aktif",
                                            tint = Blue600,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isSubmitting) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Gray800.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Blue600, modifier = Modifier.size(28.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Tutup", color = Gray300)
            }
        }
    )
}

/**
 * ✅ BARU — komponen badge status yang seragam (dipakai untuk Network & GPS)
 * Ukuran, padding, dot, dan tipografi disamakan agar keduanya sejajar rapi.
 */
@Composable
private fun StatusBadge(
    label: String,
    isActive: Boolean,
    activeBg: Color,
    activeDot: Color,
    activeText: Color,
    inactiveBg: Color,
    inactiveDot: Color,
    inactiveText: Color
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) activeBg else inactiveBg,
        animationSpec = tween(200), label = "badgeBg"
    )

    Card(
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .height(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(if (isActive) activeDot else inactiveDot, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize   = 9.sp,
                fontWeight = FontWeight.Bold,
                color      = if (isActive) activeText else inactiveText,
                letterSpacing = 0.3.sp
            )
        }
    }
}

@Composable
private fun StatusButton(
    label: String, emoji: String,
    emojiColor: Color, labelColor: Color,
    bgColor: Color, isActive: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.95f,
        animationSpec = tween(150), label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.6f,
        animationSpec = tween(150), label = "alpha"
    )

    Card(
        modifier  = modifier.height(56.dp).scale(scale),
        shape     = RoundedCornerShape(8.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        onClick   = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emoji, fontSize = 18.sp, color = emojiColor)
            Text(
                label,
                fontSize   = 9.sp,
                fontWeight = FontWeight.Bold,
                color      = labelColor.copy(alpha = alpha),
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = Gray500)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}