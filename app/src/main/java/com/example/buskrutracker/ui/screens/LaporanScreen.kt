package com.example.buskrutracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.buskrutracker.ui.navigation.Routes
import com.example.buskrutracker.ui.theme.*

@Composable
fun LaporanScreen(
    navController:   NavController,
    perjalanId:      Int,
    namaBus:         String,
    armadaNomor:     String,
    ruteNama:        String,
    totalPenumpang:  Int,
    penumpangNaik:   Int,
    totalPendapatan: Double,
    tarifPerOrang:   Double,
    jarakTempuh:     Double,
    durasiJam:       Int,
    durasiMenitSisa: Int
) {
    // ── Entrance animations ──
    val successScale by animateFloatAsState(
        targetValue    = 1f,
        animationSpec  = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label          = "successScale"
    )
    var cardVisible   by remember { mutableStateOf(false) }
    var buttonVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        cardVisible = true
        kotlinx.coroutines.delay(200)
        buttonVisible = true
    }

    val cardAlpha by animateFloatAsState(
        targetValue   = if (cardVisible) 1f else 0f,
        animationSpec = tween(600), label = "cardAlpha"
    )
    val btnAlpha by animateFloatAsState(
        targetValue   = if (buttonVisible) 1f else 0f,
        animationSpec = tween(600), label = "btnAlpha"
    )

    fun kembaliKeHome() {
        navController.navigate(Routes.PERSIAPAN) {
            popUpTo(0) { inclusive = true }
        }
    }

    BackHandler { kembaliKeHome() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ── Success Icon ──
        Card(
            modifier  = Modifier.size(80.dp).scale(successScale),
            shape     = CircleShape,
            colors    = CardDefaults.cardColors(containerColor = Green100),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("✓", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Green500)
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Perjalanan Selesai",
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold,
            color      = Gray900
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Status armada kini: Non-Aktif / Parkir",
            fontSize = 12.sp,
            color    = Gray500
        )

        Spacer(Modifier.height(32.dp))

        // ── Summary Card ──
        Card(
            modifier  = Modifier.fillMaxWidth().alpha(cardAlpha),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = Gray50),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {

                SummaryRow(
                    icon  = "⏱",
                    label = "Durasi",
                    value = "$durasiJam Jam $durasiMenitSisa Mnt"
                )
                Divider(color = Gray200, modifier = Modifier.padding(vertical = 16.dp))

                SummaryRow(
                    icon  = "👥",
                    label = "Penumpang Naik",
                    value = "$penumpangNaik Orang"
                )
                Divider(color = Gray200, modifier = Modifier.padding(vertical = 16.dp))

                SummaryRow(
                    icon  = "🪑",
                    label = "Total di Bus",
                    value = "$totalPenumpang Orang"
                )
                Divider(color = Gray200, modifier = Modifier.padding(vertical = 16.dp))

                SummaryRow(
                    icon  = "🛣",
                    label = "Jarak Tempuh",
                    value = "${"%.0f".format(jarakTempuh)} KM"
                )

                if (totalPendapatan > 0) {
                    Divider(color = Gray200, modifier = Modifier.padding(vertical = 16.dp))
                    SummaryRow(
                        icon      = "💰",
                        label     = "Total Pendapatan",
                        value     = "Rp ${"%.0f".format(totalPendapatan)}",
                        valueColor = Green500
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Kembali Button ──
        Button(
            onClick   = { kembaliKeHome() },
            modifier  = Modifier.fillMaxWidth().height(56.dp).alpha(btnAlpha),
            shape     = RoundedCornerShape(12.dp),
            colors    = ButtonDefaults.buttonColors(containerColor = Gray900),
            elevation = ButtonDefaults.buttonElevation(8.dp)
        ) {
            Text(
                "KEMBALI KE BERANDA",
                fontWeight    = FontWeight.Bold,
                fontSize      = 14.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun SummaryRow(
    icon:       String,
    label:      String,
    value:      String,
    valueColor: Color = Gray900
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 18.sp, modifier = Modifier.width(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = Gray500,
            modifier   = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Bold,
            color      = valueColor
        )
    }
}