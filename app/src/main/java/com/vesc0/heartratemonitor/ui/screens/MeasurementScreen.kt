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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vesc0.heartratemonitor.data.model.HeartRateEntry
import com.vesc0.heartratemonitor.data.model.SessionPhase
import com.vesc0.heartratemonitor.ui.components.HeartTimerView
import com.vesc0.heartratemonitor.viewmodel.AutoHeartRateViewModel
import com.vesc0.heartratemonitor.viewmodel.HeartRateViewModel
import java.util.concurrent.Executors

private enum class MeasurementMode { MANUAL, AUTOMATIC }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementScreen(vm: HeartRateViewModel) {
    var mode by remember { mutableStateOf(MeasurementMode.AUTOMATIC) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Measure") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Segmented picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == MeasurementMode.MANUAL,
                        onClick = { mode = MeasurementMode.MANUAL },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text("Manual") }
                    SegmentedButton(
                        selected = mode == MeasurementMode.AUTOMATIC,
                        onClick = { mode = MeasurementMode.AUTOMATIC },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text("Automatic") }
                }
            }

            when (mode) {
                MeasurementMode.MANUAL -> ManualContent(vm = vm)
                MeasurementMode.AUTOMATIC -> AutoContent(vm = vm)
            }
        }
    }
}

// ─────────────────────────────── Manual ───────────────────────────────

@Composable
private fun ManualContent(vm: HeartRateViewModel) {
    val phase by vm.phase.collectAsState()
    val currentBPM by vm.currentBPM.collectAsState()
    val heartScale by vm.heartScale.collectAsState()
    val secondsLeft by vm.secondsLeft.collectAsState()
    val canShowBPM by vm.canShowBPM.collectAsState()

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
                        tint = Color.Red.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Manual Measurement", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Find your pulse on your neck or wrist, then tap the heart in rhythm for 12 seconds.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { vm.startSession() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 64.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Manual Session", fontWeight = FontWeight.SemiBold)
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

                    if (canShowBPM && currentBPM != null) {
                        Text("$currentBPM BPM", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    } else if (!vm.hasTapped) {
                        Text("Tap the heart to begin…", color = Color.Gray)
                    } else {
                        Text("Keep tapping…", color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    OutlinedButton(
                        onClick = { vm.stopSession() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) { Text("Stop") }
                }
            }

            SessionPhase.FINISHED -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (currentBPM != null) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("$currentBPM BPM", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Measurement complete", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("No data recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { vm.startNewSession() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 64.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Measurement", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────── Automatic ────────────────────────────

@Composable
private fun AutoContent(vm: HeartRateViewModel) {
    val autoVM: AutoHeartRateViewModel = viewModel()
    val phase by autoVM.phase.collectAsState()
    val currentBPM by autoVM.currentBPM.collectAsState()
    val heartScale by autoVM.heartScale.collectAsState()
    val secondsLeft by autoVM.secondsLeft.collectAsState()
    val canShowBPM by autoVM.canShowBPM.collectAsState()
    val errorMessage by autoVM.errorMessage.collectAsState()

    // Save entry when finished
    val previousPhase = remember { mutableStateOf(phase) }
    LaunchedEffect(phase) {
        if (previousPhase.value == SessionPhase.MEASURING && phase == SessionPhase.FINISHED) {
            autoVM.currentBPM.value?.let { bpm ->
                vm.addEntry(HeartRateEntry(bpm = bpm, date = System.currentTimeMillis()))
            }
        }
        previousPhase.value = phase
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview during measuring
        if (phase == SessionPhase.MEASURING) {
            CameraPreviewWithAnalysis(
                onSample = { autoVM.processSample(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.167f)
                    .align(Alignment.TopCenter)
            )
            // Gradient overlay
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
                            Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Red.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Automatic Measurement", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Place your fingertip over the camera and keep it still. The 12-second timer starts after detecting your first beats.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { autoVM.startSession() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 64.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Automatic Session", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                SessionPhase.MEASURING -> {
                    Spacer(modifier = Modifier.weight(1f))
                    HeartTimerView(
                        heartScale = heartScale,
                        secondsLeft = secondsLeft,
                        totalSeconds = 12,
                        heartSize = 96.dp,
                        color = Color.Red
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
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("$currentBPM BPM", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Measurement complete", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("No result", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { autoVM.stopSessionEarly() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 64.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Measurement", fontWeight = FontWeight.SemiBold)
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
                    onClick = { autoVM.stopSessionEarly() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) { Text("Stop") }
            }
        }
    }
}

// ──────────────────── Camera preview + analysis composable ────────────

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
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraRef?.cameraControl?.enableTorch(false)
            try { cameraFuture.get().unbindAll() } catch (_: Exception) { }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.clipToBounds())
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
