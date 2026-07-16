package com.example.buskrutracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.buskrutracker.ui.navigation.Routes
import com.example.buskrutracker.ui.theme.*
import com.example.buskrutracker.viewmodel.LoginUiState
import com.example.buskrutracker.viewmodel.LoginViewModel
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    navController: NavController,
    vm: LoginViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // Redirect jika sudah login
    LaunchedEffect(Unit) {
        if (vm.isAlreadyLoggedIn()) {
            navController.navigate(Routes.PERSIAPAN) {
                popUpTo(Routes.LOGIN) { inclusive = true }
            }
        }
    }

    // Handle state changes
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is LoginUiState.Success -> {
                toastMessage = "✓ Selamat datang, ${state.driverName}!"
                delay(600)
                navController.navigate(Routes.PERSIAPAN) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
                vm.resetState()
            }
            is LoginUiState.Error -> {
                toastMessage = state.message
                vm.resetState()
            }
            else -> {}
        }
    }

    // Auto-clear toast
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) { delay(2500); toastMessage = null }
    }

    val isLoading = uiState is LoginUiState.Loading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(Color(0xFFF0F4FF), Color(0xFFE8F5E9)))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Logo Section ──
            Card(
                modifier  = Modifier.size(96.dp).rotate(3f),
                shape     = RoundedCornerShape(24.dp),
                colors    = CardDefaults.cardColors(containerColor = Blue600),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("🚌", fontSize = 48.sp, modifier = Modifier.rotate(-3f))
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "STJ Kru Tracker",
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = Gray900
            )
            Text(
                "APLIKASI KRU & OPERASIONAL",
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                color      = Gray400,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(48.dp))

            // ── Username Field ──
            Text(
                "ID PENGEMUDI",
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                color      = Gray400,
                letterSpacing = 1.sp,
                modifier   = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🪪", fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    TextField(
                        value         = username,
                        onValueChange = { username = it },
                        placeholder   = { Text("Masukkan ID", color = Gray300) },
                        singleLine    = true,
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontWeight = FontWeight.Bold,
                            color      = Gray700
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Password Field ──
            Text(
                "KATA SANDI",
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                color      = Gray400,
                letterSpacing = 1.sp,
                modifier   = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔒", fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    TextField(
                        value         = password,
                        onValueChange = { password = it },
                        placeholder   = { Text("Masukkan password", color = Gray300) },
                        singleLine    = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontWeight = FontWeight.Bold,
                            color      = Gray700
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                vm.login(username, password)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Login Button ──
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = if (isLoading) Blue600.copy(alpha = 0.6f) else Blue600
                ),
                elevation = CardDefaults.cardElevation(8.dp),
                onClick   = {
                    if (!isLoading) {
                        focusManager.clearFocus()
                        vm.login(username, password)
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
                                color  = White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Memproses...",
                                color      = White,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 14.sp
                            )
                        }
                    } else {
                        Text(
                            "MASUK SISTEM",
                            color         = White,
                            fontWeight    = FontWeight.Bold,
                            fontSize      = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // ── Toast Message ──
        AnimatedVisibility(
            visible  = toastMessage != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
        ) {
            Card(
                shape  = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Gray800)
            ) {
                Text(
                    text      = toastMessage ?: "",
                    color     = White,
                    fontSize  = 14.sp,
                    modifier  = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}