package com.example.buskrutracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    kapasitasAwal: Int = 40,   // ✅ FIX: kapasitas dari navigasi, bukan cuma default hardcode
    vm: TrackingViewModel = viewModel()
) {
    val uiState         by vm.uiState.collectAsState()
    val jumlahPenumpang by vm.jumlahPenumpang.collectAsState()
    val kapasitas       by vm.kapasitas.collectAsState()
    val kondisi         by vm.kondisi.collectAsState()
    val stats           by vm.stats.collectAsState()

    var toastMessage    by remember { mutableStateOf<String?>(null) }
    var showAkhiriDialog by remember { mutableStateOf(false) }
    var showBackDialog  by remember { mutableStateOf(false) }

    // Parse origin/destination dari ruteNama
    val sep    = if (ruteNama.contains("→")) "→" else "-"
    val parts  = ruteNama.split(sep)
    val origin = parts.getOrNull(0)?.trim()?.uppercase() ?: "ASAL"
    val dest   = parts.getOrNull(1)?.trim()?.uppercase() ?: "TUJUAN"

    // ✅ FIX: kapasitasAwal diteruskan ke ViewModel.init() supaya nilai sudah benar
    // sejak frame pertama, tidak menunggu loadPerjalananAktif() selesai/berhasil
    LaunchedEffect(perjalanId) { vm.init(perjalanId, kapasitasAwal) }

    DisposableEffect(Unit) { onDispose { vm.unregisterReceiver() } }

    // Handle state
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

    BackHandler { showBackDialog = true }

    // ── Back Warning Dialog ──
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

    // ── Akhiri Dialog ──
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

    // ── Main UI ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray900)
    ) {

        // ── Top Bar ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Gray800)
                .padding(20.dp)
        ) {
            Column {
                Text(
                    "RUTE AKTIF",
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Gray400,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(origin, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(" → ", fontSize = 12.sp, color = Gray500)
                    Text(dest, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            // GPS Badge
            Card(
                modifier = Modifier.align(Alignment.CenterEnd),
                shape    = RoundedCornerShape(20.dp),
                colors   = CardDefaults.cardColors(containerColor = Green800)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).background(Green500, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text("GPS ON", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Green500)
                }
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

            // Label
            Text(
                "TOTAL PENUMPANG",
                fontSize   = 9.sp,
                fontWeight = FontWeight.Bold,
                color      = Gray500,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(24.dp))

            // ── Big Counter ──
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

            // ── Penumpang Buttons ──
            Row(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Kurang (Turun)
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
                // Tambah (Naik)
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

            // ── Status Kondisi ──
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

            // ── Stats Row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Kecepatan", "${"%.1f".format(stats.speedKmh)} km/h")
                StatItem("Jarak",     "${"%.2f".format(stats.jarakKm)} km")
                StatItem("Durasi",    "${stats.durasiMenit / 60}j ${stats.durasiMenit % 60}m")
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
            modifier = Modifier.fillMaxSize().then(if (!isActive) Modifier else Modifier),
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