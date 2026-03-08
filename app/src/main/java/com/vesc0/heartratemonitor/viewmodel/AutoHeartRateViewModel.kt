package com.vesc0.heartratemonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesc0.heartratemonitor.data.model.SessionPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Camera-based automatic heart rate measurement (12 s window).
 *
 * Signal processing: EMA smoothing → rolling baseline → dynamic threshold → peak detection.
 * The composable is responsible for CameraX setup and calling [processSample] with mean brightness.
 */
class AutoHeartRateViewModel : ViewModel() {

    // --- UI state ---
    private val _phase = MutableStateFlow(SessionPhase.IDLE)
    val phase = _phase.asStateFlow()

    private val _currentBPM = MutableStateFlow<Int?>(null)
    val currentBPM = _currentBPM.asStateFlow()

    private val _secondsLeft = MutableStateFlow(0)
    val secondsLeft = _secondsLeft.asStateFlow()

    private val _heartScale = MutableStateFlow(1.0f)
    val heartScale = _heartScale.asStateFlow()

    private val _canShowBPM = MutableStateFlow(false)
    val canShowBPM = _canShowBPM.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    // --- Signal processing state ---
    private var ema: Double? = null
    private val window = mutableListOf<Double>()
    private val windowSize = 45
    private var lastCentered = 0.0
    private var lastPeakTS: Long? = null // System.nanoTime()

    // Calibration
    private var calibrationBeats = 0
    private val calibrationBeatsRequired = 4

    // Measurement
    private var measurementStartNano: Long? = null
    private var revealTimeElapsed = false
    private val measurementIntervals = mutableListOf<Double>()

    // Constraints
    private val minInterval = 0.27 // ~220 BPM
    private val maxInterval = 1.50 // ~40  BPM

    // Durations
    private val measureDuration = 12L // seconds
    private val bpmRevealAfter = 4L

    // Coroutine jobs
    private var countdownJob: Job? = null
    private var phaseTimerJob: Job? = null
    private var bpmRevealJob: Job? = null

    // --- Session lifecycle ---

    fun startSession() {
        if (_phase.value != SessionPhase.IDLE && _phase.value != SessionPhase.FINISHED) return
        reset()
        _phase.value = SessionPhase.MEASURING
    }

    fun stopSessionEarly() {
        _phase.value = SessionPhase.IDLE
        cancelJobs()
    }

    /**
     * Called from the CameraX ImageAnalysis analyzer with mean brightness value.
     * Must be called on the main thread.
     */
    fun processSample(brightness: Double) {
        if (_phase.value != SessionPhase.MEASURING) return

        // 1) EMA smoothing
        if (ema == null) ema = brightness
        ema = 0.2 * brightness + 0.8 * (ema ?: brightness)
        val value = ema ?: brightness

        // 2) Rolling baseline
        window.add(value)
        if (window.size > windowSize) window.removeAt(0)
        val mean = window.average()
        val centered = value - mean

        // 3) Dynamic threshold
        val variance = window.sumOf { (it - mean) * (it - mean) } / (window.size - 1).coerceAtLeast(1)
        val std = kotlin.math.sqrt(variance)
        val threshold = maxOf(0.5 * std, 0.5)

        // 4) Peak detection (positive-to-negative zero-crossing above threshold)
        val derivative = centered - lastCentered
        val isLocalMax = derivative <= 0 && lastCentered > threshold

        if (isLocalMax) {
            val now = System.nanoTime()
            lastPeakTS?.let { lastNano ->
                val dt = (now - lastNano) / 1_000_000_000.0
                if (dt in minInterval..maxInterval) {
                    val startNano = measurementStartNano
                    if (startNano != null && now >= startNano) {
                        measurementIntervals.add(dt)
                        _currentBPM.value = computeBPM(measurementIntervals.takeLast(5))
                    } else {
                        calibrationBeats++
                        if (calibrationBeats >= calibrationBeatsRequired && measurementStartNano == null) {
                            measurementStartNano = now
                            measurementIntervals.clear()
                            _currentBPM.value = null
                            _canShowBPM.value = false
                            revealTimeElapsed = false
                            startCountdown(measureDuration)
                            scheduleBpmReveal(bpmRevealAfter)
                        }
                    }
                    pulseHeart()
                }
            }
            lastPeakTS = now
        }
        lastCentered = centered
    }

    // --- Internal ---

    private fun endSession() {
        _currentBPM.value = computeBPM(measurementIntervals)
        _phase.value = SessionPhase.FINISHED
        cancelJobs()
    }

    private fun startCountdown(duration: Long) {
        _secondsLeft.value = duration.toInt()
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = duration.toInt()
            while (remaining > 0) {
                delay(1000)
                remaining--
                _secondsLeft.value = remaining
            }
        }
        phaseTimerJob?.cancel()
        phaseTimerJob = viewModelScope.launch {
            delay(duration * 1000)
            endSession()
        }
    }

    private fun scheduleBpmReveal(after: Long) {
        _canShowBPM.value = false
        revealTimeElapsed = false
        bpmRevealJob?.cancel()
        bpmRevealJob = viewModelScope.launch {
            delay(after * 1000)
            revealTimeElapsed = true
            _canShowBPM.value = measurementStartNano != null && revealTimeElapsed
        }
    }

    private fun pulseHeart() {
        viewModelScope.launch {
            _heartScale.value = 1.2f
            delay(120)
            _heartScale.value = 1.0f
        }
    }

    private fun computeBPM(intervals: List<Double>): Int? {
        if (intervals.isEmpty()) return null
        val avg = intervals.average()
        if (avg <= 0) return null
        return (60.0 / avg).toInt()
    }

    private fun reset() {
        _currentBPM.value = null
        _secondsLeft.value = 0
        _heartScale.value = 1.0f
        _errorMessage.value = null
        _canShowBPM.value = false

        ema = null
        window.clear()
        lastCentered = 0.0
        lastPeakTS = null

        calibrationBeats = 0
        measurementStartNano = null
        revealTimeElapsed = false
        measurementIntervals.clear()
    }

    private fun cancelJobs() {
        countdownJob?.cancel()
        phaseTimerJob?.cancel()
        bpmRevealJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        cancelJobs()
    }
}
