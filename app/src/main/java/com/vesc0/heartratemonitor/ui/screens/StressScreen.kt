package com.vesc0.heartratemonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vesc0.heartratemonitor.data.model.HeartRateEntry
import com.vesc0.heartratemonitor.data.model.SessionPhase
import com.vesc0.heartratemonitor.ui.components.HeartTimerView
import com.vesc0.heartratemonitor.viewmodel.AuthViewModel
import com.vesc0.heartratemonitor.viewmodel.HeartRateViewModel
import com.vesc0.heartratemonitor.viewmodel.StressViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StressScreen(vm: HeartRateViewModel, auth: AuthViewModel) {
    StressContent(vm = vm, auth = auth, showTopBar = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StressContent(vm: HeartRateViewModel, auth: AuthViewModel, showTopBar: Boolean = false) {
    val stressVM: StressViewModel = viewModel()
    val phase by stressVM.phase.collectAsState()
    val currentBPM by stressVM.currentBPM.collectAsState()
    val heartScale by stressVM.heartScale.collectAsState()
    val secondsLeft by stressVM.secondsLeft.collectAsState()
    val canShowBPM by stressVM.canShowBPM.collectAsState()
    val errorMessage by stressVM.errorMessage.collectAsState()
    val stressResult by stressVM.stressResult.collectAsState()
    val isPredicting by stressVM.isPredicting.collectAsState()

    // Collect demographics from auth
    val userAge by auth.age.collectAsState()
    val userGender by auth.gender.collectAsState()
    val userHeightCm by auth.heightCm.collectAsState()
    val userWeightKg by auth.weightKg.collectAsState()

    // Save entry when finished
    val previousPhase = remember { mutableStateOf(phase) }
    LaunchedEffect(phase) {
        if (previousPhase.value == SessionPhase.MEASURING && phase == SessionPhase.FINISHED) {
            stressVM.currentBPM.value?.let { bpm ->
                val pct = stressVM.stressResult.value?.stressLevelPct
                val stressStr = pct?.let { "${it.toInt()}%" }
                vm.addEntry(HeartRateEntry(bpm = bpm, date = System.currentTimeMillis(), stressLevel = stressStr))
            }
        }
        previousPhase.value = phase
    }

    // Update entry when stress prediction arrives
    LaunchedEffect(stressResult?.stressLevelPct) {
        val pct = stressResult?.stressLevelPct ?: return@LaunchedEffect
        if (phase == SessionPhase.FINISHED) {
            val bpm = stressVM.currentBPM.value ?: return@LaunchedEffect
            val stressStr = "${pct.toInt()}%"
            val idx = vm.log.value.indexOfFirst { it.bpm == bpm && it.stressLevel == null }
            if (idx >= 0) {
                val old = vm.log.value[idx]
                vm.updateEntry(idx, old.copy(stressLevel = stressStr))
            }
        }
    }

    val topBarContent: @Composable () -> Unit = {
        if (showTopBar && phase == SessionPhase.IDLE) {
            TopAppBar(title = { Text("Stress") })
        }
    }

    Scaffold(
        topBar = topBarContent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Camera preview during measuring
            if (phase == SessionPhase.MEASURING) {
                CameraPreviewWithAnalysis(
                    onSample = { stressVM.processSample(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.167f)
                        .align(Alignment.TopCenter)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.167f)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (phase) {
                    SessionPhase.IDLE -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Red.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Stress Measurement", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Place your fingertip over the camera and keep it still for 60 seconds. The app will analyse your heart-rate variability and predict whether you are stressed.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    stressVM.userAge = userAge?.toDoubleOrNull()
                                    stressVM.userGender = userGender
                                    stressVM.userHeightCm = userHeightCm?.toDoubleOrNull()
                                    stressVM.userWeightKg = userWeightKg?.toDoubleOrNull()
                                    stressVM.startSession()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 64.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Stress Session", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    SessionPhase.MEASURING -> {
                        Spacer(modifier = Modifier.weight(1f))
                        HeartTimerView(
                            heartScale = heartScale,
                            secondsLeft = secondsLeft,
                            totalSeconds = 60,
                            heartSize = 96.dp,
                            color = Color(0xFF9C27B0) // Purple
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (canShowBPM && currentBPM != null) {
                            Text("$currentBPM BPM", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                "Calibrating… keep fingertip on the camera",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    SessionPhase.FINISHED -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (currentBPM != null) {
                                Text(
                                    "Heart Rate: $currentBPM BPM",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            if (isPredicting) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Analysing…")
                            } else if (stressResult != null) {
                                val result = stressResult!!
                                val pct = result.stressLevelPct
                                val color = when {
                                    pct >= 70 -> Color.Red
                                    pct >= 40 -> Color(0xFFFF9800)
                                    else -> Color(0xFF4CAF50)
                                }
                                val label = when {
                                    pct >= 70 -> "High Stress"
                                    pct >= 40 -> "Moderate Stress"
                                    else -> "Low Stress"
                                }
                                Icon(
                                    when {
                                        pct >= 70 -> Icons.Filled.Warning
                                        pct >= 40 -> Icons.Filled.Info
                                        else -> Icons.Filled.Verified
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = color
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "${pct.toInt()}%",
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                                Text(
                                    label,
                                    fontSize = 20.sp,
                                    color = color
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { stressVM.resetToIdle() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 64.dp)
                            ) {
                                Text("Done", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        errorMessage ?: "",
                        color = Color.Red,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (phase == SessionPhase.MEASURING) {
                    OutlinedButton(
                        onClick = { stressVM.stopSessionEarly() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) { Text("Stop") }
                }
            }
        }
    }
}
