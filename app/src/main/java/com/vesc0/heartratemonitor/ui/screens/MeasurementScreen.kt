package com.vesc0.heartratemonitor.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vesc0.heartratemonitor.data.model.HeartRateEntry
import com.vesc0.heartratemonitor.data.model.MeasurementState
import com.vesc0.heartratemonitor.data.model.SessionPhase
import com.vesc0.heartratemonitor.ui.components.HeartTimerView
import com.vesc0.heartratemonitor.ui.theme.buttonTextColor
import com.vesc0.heartratemonitor.viewmodel.AutoHeartRateViewModel
import com.vesc0.heartratemonitor.viewmodel.AuthViewModel
import com.vesc0.heartratemonitor.viewmodel.HeartRateViewModel
import com.vesc0.heartratemonitor.viewmodel.StressViewModel
import java.util.concurrent.Executors

private enum class MeasurementCategory { HEART_RATE, STRESS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementScreen(vm: HeartRateViewModel, auth: AuthViewModel) {
    val autoVM: AutoHeartRateViewModel = viewModel()
    val stressVM: StressViewModel = viewModel()

    var category by remember { mutableStateOf(MeasurementCategory.HEART_RATE) }
    val heartRatePagerState = rememberPagerState(initialPage = 1, pageCount = { 2 })

    fun stopTapIfNeeded() {
        if (vm.phase.value == SessionPhase.MEASURING) vm.stopSession()
    }

    fun stopCameraIfNeeded() {
        if (autoVM.phase.value == SessionPhase.MEASURING) autoVM.stopSessionEarly()
    }

    fun stopStressIfNeeded() {
        if (stressVM.phase.value == SessionPhase.MEASURING) stressVM.stopSessionEarly()
    }

    LaunchedEffect(category) {
        when (category) {
            MeasurementCategory.HEART_RATE -> stopStressIfNeeded()
            MeasurementCategory.STRESS -> {
                stopTapIfNeeded()
                stopCameraIfNeeded()
            }
        }
    }

    LaunchedEffect(category, heartRatePagerState.currentPage) {
        if (category == MeasurementCategory.HEART_RATE) {
            when (heartRatePagerState.currentPage) {
                0 -> stopCameraIfNeeded()
                else -> stopTapIfNeeded()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopTapIfNeeded()
            stopCameraIfNeeded()
            stopStressIfNeeded()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Measure") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = category == MeasurementCategory.HEART_RATE,
                    onClick = { category = MeasurementCategory.HEART_RATE },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Color.Red,
                        activeContentColor = Color.White
                    ),
                    icon = {}
                ) { Text("Heart Rate") }

                SegmentedButton(
                    selected = category == MeasurementCategory.STRESS,
                    onClick = { category = MeasurementCategory.STRESS },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Color.Red,
                        activeContentColor = Color.White
                    ),
                    icon = {}
                ) { Text("Stress") }
            }

            when (category) {
                MeasurementCategory.HEART_RATE -> {
                    HorizontalPager(
                        state = heartRatePagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (it) {
                            0 -> TapMeasurementContent(vm = vm)
                            else -> CameraMeasurementContent(vm = vm, autoVM = autoVM)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(2) { idx ->
                            val selected = heartRatePagerState.currentPage == idx
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(if (selected) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) Color.Red
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    )
                            )
                        }
                    }
                }

                MeasurementCategory.STRESS -> {
                    StressMeasurementContent(vm = vm, auth = auth, stressVM = stressVM)
                }
            }
        }
    }
}

@Composable
private fun TapMeasurementContent(vm: HeartRateViewModel) {
    val phase by vm.phase.collectAsState()
    val currentBpm by vm.currentBPM.collectAsState()
    val heartScale by vm.heartScale.collectAsState()
    val secondsLeft by vm.secondsLeft.collectAsState()
    val canShowBpm by vm.canShowBPM.collectAsState()

    var selectedState by remember { mutableStateOf<MeasurementState?>(null) }

    LaunchedEffect(phase) {
        if (phase != SessionPhase.FINISHED) selectedState = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            SessionPhase.IDLE -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Red.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Tap Measurement", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Find your pulse on your neck or wrist, then tap the heart in rhythm for 12 seconds.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { vm.startSession() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 56.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Tap Session", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            SessionPhase.MEASURING -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { vm.recordTap() }
                    ) {
                        HeartTimerView(
                            heartScale = heartScale,
                            secondsLeft = secondsLeft,
                            totalSeconds = 12,
                            heartSize = 96.dp,
                            color = Color.Red
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (canShowBpm && currentBpm != null) {
                        Text("$currentBpm BPM", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    } else if (!vm.hasTapped) {
                        Text("Tap the heart to begin...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Keep tapping...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    OutlinedButton(
                        onClick = { vm.stopSession() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonTextColor()),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        Text("Stop")
                    }
                }
            }

            SessionPhase.FINISHED -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val bpm = currentBpm
                    if (bpm != null) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("$bpm BPM", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(14.dp))

                        MeasurementStateSelectorCard(
                            bpm = bpm,
                            selectedState = selectedState,
                            onStateSelected = { selectedState = it }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val state = selectedState ?: return@Button
                                vm.addEntry(
                                    HeartRateEntry(
                                        bpm = bpm,
                                        date = System.currentTimeMillis(),
                                        activityState = state
                                    )
                                )
                                selectedState = null
                                vm.startNewSession()
                            },
                            enabled = selectedState != null,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 56.dp)
                        ) {
                            Text("Save Measurement", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text("No data recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { vm.startNewSession() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 56.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Try Again", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraMeasurementContent(
    vm: HeartRateViewModel,
    autoVM: AutoHeartRateViewModel
) {
    val phase by autoVM.phase.collectAsState()
    val currentBpm by autoVM.currentBPM.collectAsState()
    val heartScale by autoVM.heartScale.collectAsState()
    val secondsLeft by autoVM.secondsLeft.collectAsState()
    val canShowBpm by autoVM.canShowBPM.collectAsState()
    val errorMessage by autoVM.errorMessage.collectAsState()

    var selectedState by remember { mutableStateOf<MeasurementState?>(null) }

    LaunchedEffect(phase) {
        if (phase != SessionPhase.FINISHED) selectedState = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Red.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Camera Measurement", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Place your fingertip over the camera and keep it still. The 12-second timer starts after detecting your first beats.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { autoVM.startSession() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 56.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Camera Session", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                SessionPhase.MEASURING -> {
                    Spacer(modifier = Modifier.weight(1f))

                    Box(contentAlignment = Alignment.Center) {
                        CameraPreviewWithAnalysis(
                            onSample = { autoVM.processSample(it) },
                            modifier = Modifier
                                .size(180.dp)
                                .clip(CircleShape)
                        )

                        HeartTimerView(
                            heartScale = heartScale,
                            secondsLeft = secondsLeft,
                            totalSeconds = 12,
                            heartSize = 126.dp,
                            color = Color.Red,
                            showHeart = secondsLeft > 0,
                            heartIconScale = 0.78f
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (canShowBpm && currentBpm != null) {
                        Text("$currentBpm BPM", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            "Calibrating... keep fingertip on camera",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }

                SessionPhase.FINISHED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val bpm = currentBpm
                        if (bpm != null) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("$bpm BPM", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(14.dp))

                            MeasurementStateSelectorCard(
                                bpm = bpm,
                                selectedState = selectedState,
                                onStateSelected = { selectedState = it }
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val state = selectedState ?: return@Button
                                    vm.addEntry(
                                        HeartRateEntry(
                                            bpm = bpm,
                                            date = System.currentTimeMillis(),
                                            activityState = state
                                        )
                                    )
                                    selectedState = null
                                    autoVM.stopSessionEarly()
                                },
                                enabled = selectedState != null,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 56.dp)
                            ) {
                                Text("Save Measurement", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Text("No result", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { autoVM.stopSessionEarly() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 56.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Try Again", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (phase == SessionPhase.MEASURING) {
                OutlinedButton(
                    onClick = { autoVM.stopSessionEarly() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonTextColor()),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun StressMeasurementContent(
    vm: HeartRateViewModel,
    auth: AuthViewModel,
    stressVM: StressViewModel
) {
    val phase by stressVM.phase.collectAsState()
    val currentBpm by stressVM.currentBPM.collectAsState()
    val heartScale by stressVM.heartScale.collectAsState()
    val secondsLeft by stressVM.secondsLeft.collectAsState()
    val canShowBpm by stressVM.canShowBPM.collectAsState()
    val errorMessage by stressVM.errorMessage.collectAsState()
    val stressResult by stressVM.stressResult.collectAsState()
    val isPredicting by stressVM.isPredicting.collectAsState()

    val age by auth.age.collectAsState()
    val gender by auth.gender.collectAsState()
    val heightCm by auth.heightCm.collectAsState()
    val weightKg by auth.weightKg.collectAsState()

    var selectedState by remember { mutableStateOf<MeasurementState?>(null) }

    LaunchedEffect(phase) {
        if (phase != SessionPhase.FINISHED) selectedState = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            tint = Color.Red.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Stress Measurement", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Place your fingertip over the camera and keep it still for 60 seconds. The app analyzes heart-rate variability to estimate stress.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                stressVM.userAge = age?.toDoubleOrNull()
                                stressVM.userGender = gender
                                stressVM.userHeightCm = heightCm?.toDoubleOrNull()
                                stressVM.userWeightKg = weightKg?.toDoubleOrNull()
                                stressVM.startSession()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 56.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Stress Session", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                SessionPhase.MEASURING -> {
                    Spacer(modifier = Modifier.weight(1f))

                    Box(contentAlignment = Alignment.Center) {
                        CameraPreviewWithAnalysis(
                            onSample = { stressVM.processSample(it) },
                            modifier = Modifier
                                .size(180.dp)
                                .clip(CircleShape)
                        )

                        HeartTimerView(
                            heartScale = heartScale,
                            secondsLeft = secondsLeft,
                            totalSeconds = 60,
                            heartSize = 126.dp,
                            color = Color(0xFF9C27B0),
                            showHeart = secondsLeft > 0,
                            heartIconScale = 0.78f
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (canShowBpm && currentBpm != null) {
                        Text("$currentBpm BPM", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            "Calibrating... keep fingertip on camera",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }

                SessionPhase.FINISHED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val bpm = currentBpm
                        if (bpm != null) {
                            Text(
                                text = "Heart Rate: $bpm BPM",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            MeasurementStateSelectorCard(
                                bpm = bpm,
                                selectedState = selectedState,
                                onStateSelected = { selectedState = it }
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (isPredicting) {
                            androidx.compose.material3.CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Analysing...")
                        } else if (stressResult != null) {
                            val pct = stressResult?.stressLevelPct ?: 0.0
                            val resultColor = when {
                                pct >= 70.0 -> Color.Red
                                pct >= 40.0 -> Color(0xFFFF9800)
                                else -> Color(0xFF4CAF50)
                            }
                            val label = when {
                                pct >= 70.0 -> "High Stress"
                                pct >= 40.0 -> "Moderate Stress"
                                else -> "Low Stress"
                            }

                            Icon(
                                imageVector = if (pct >= 50.0) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = resultColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${pct.toInt()}%",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = resultColor
                            )
                            Text(label, color = resultColor)
                        } else if (!errorMessage.isNullOrBlank() && currentBpm != null) {
                            Text(
                                text = errorMessage.orEmpty(),
                                color = Color(0xFFFF9800),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                val state = selectedState ?: return@Button
                                val bpm = currentBpm ?: return@Button
                                val stress = stressResult?.let { "${it.stressLevelPct.toInt()}%" }

                                vm.addEntry(
                                    HeartRateEntry(
                                        bpm = bpm,
                                        date = System.currentTimeMillis(),
                                        stressLevel = stress,
                                        activityState = state
                                    )
                                )

                                selectedState = null
                                stressVM.resetToIdle()
                            },
                            enabled = selectedState != null && currentBpm != null && !isPredicting,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 56.dp)
                        ) {
                            Text("Save Measurement", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (!errorMessage.isNullOrBlank() && phase != SessionPhase.FINISHED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (phase == SessionPhase.MEASURING) {
                OutlinedButton(
                    onClick = { stressVM.stopSessionEarly() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonTextColor()),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementStateSelectorCard(
    bpm: Int,
    selectedState: MeasurementState?,
    onStateSelected: (MeasurementState?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val assessment = selectedState?.assessment(bpm)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = "What is your current state?",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonTextColor()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedState?.displayName ?: "Select state")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Select state") },
                    onClick = {
                        onStateSelected(null)
                        expanded = false
                    }
                )
                MeasurementState.entries.forEach { state ->
                    DropdownMenuItem(
                        text = { Text(state.displayName) },
                        onClick = {
                            onStateSelected(state)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (assessment != null) {
            Text(
                text = assessment.title,
                color = if (assessment.isNormal) Color(0xFF2E7D32) else Color(0xFFFF9800),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = assessment.detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        } else {
            Text(
                text = "Choose a state to check whether your BPM is in a normal range.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun CameraPreviewWithAnalysis(
    onSample: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val cameraFuture = ProcessCameraProvider.getInstance(context)
        var cameraRef: androidx.camera.core.Camera? = null

        cameraFuture.addListener({
            try {
                val provider = cameraFuture.get()
                provider.unbindAll()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { ia ->
                        ia.setAnalyzer(executor) { proxy ->
                            val mean = computeMeanBrightness(proxy)
                            proxy.close()
                            mainHandler.post { onSample(mean) }
                        }
                    }

                cameraRef = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                cameraRef?.cameraControl?.enableTorch(true)
            } catch (_: Exception) {
                // Camera startup failures are surfaced by missing samples in the measurement VM.
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraRef?.cameraControl?.enableTorch(false)
            try {
                cameraFuture.get().unbindAll()
            } catch (_: Exception) {
                // Ignore cleanup failures.
            }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun computeMeanBrightness(proxy: androidx.camera.core.ImageProxy): Double {
    val yPlane = proxy.planes[0]
    val buffer = yPlane.buffer
    val rowStride = yPlane.rowStride
    val width = proxy.width
    val height = proxy.height

    var sum = 0L
    var count = 0

    for (y in 0 until height step 8) {
        for (x in 0 until width step 8) {
            val index = y * rowStride + x
            if (index < buffer.capacity()) {
                sum += (buffer[index].toInt() and 0xFF)
                count++
            }
        }
    }

    return if (count > 0) sum.toDouble() / count else 0.0
}
