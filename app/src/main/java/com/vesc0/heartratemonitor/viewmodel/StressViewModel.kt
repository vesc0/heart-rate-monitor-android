package com.vesc0.heartratemonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesc0.heartratemonitor.data.api.ApiService
import com.vesc0.heartratemonitor.data.model.SessionPhase
import com.vesc0.heartratemonitor.data.model.StressPredictRequest
import com.vesc0.heartratemonitor.data.model.StressPredictResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Camera-based 60-second HRV stress measurement.
 * Same PPG signal processing as [AutoHeartRateViewModel] but with a longer window
 * and HRV feature computation for stress prediction via the API.
 */
class StressViewModel : ViewModel() {

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

    private val _stressResult = MutableStateFlow<StressPredictResponse?>(null)
    val stressResult = _stressResult.asStateFlow()

    private val _isPredicting = MutableStateFlow(false)
    val isPredicting = _isPredicting.asStateFlow()

    // --- Signal processing state ---
    private var ema: Double? = null
    private val window = mutableListOf<Double>()
    private val windowSize = 45
    private var lastCentered = 0.0
    private var lastPeakTS: Long? = null

    // Calibration
    private var calibrationBeats = 0
    private val calibrationBeatsRequired = 4

    // Measurement
    private var measurementStartNano: Long? = null
    private var revealTimeElapsed = false
    private val measurementIntervals = mutableListOf<Double>()

    // Constraints
    private val minInterval = 0.27
    private val maxInterval = 1.50

    // 60s measurement for HRV
    private val measureDuration = 60L
    private val bpmRevealAfter = 4L

    private var countdownJob: Job? = null
    private var phaseTimerJob: Job? = null
    private var bpmRevealJob: Job? = null

    private val api = ApiService

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

    fun resetToIdle() {
        _phase.value = SessionPhase.IDLE
    }

    /**
     * Called from the CameraX analyzer with mean brightness value (main thread).
     */
    fun processSample(brightness: Double) {
        if (_phase.value != SessionPhase.MEASURING) return

        if (ema == null) ema = brightness
        ema = 0.2 * brightness + 0.8 * (ema ?: brightness)
        val value = ema ?: brightness

        window.add(value)
        if (window.size > windowSize) window.removeAt(0)
        val mean = window.average()
        val centered = value - mean

        val variance = window.sumOf { (it - mean).pow(2) } / (window.size - 1).coerceAtLeast(1)
        val std = sqrt(variance)
        val threshold = maxOf(0.5 * std, 0.5)

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
        requestPrediction()
    }

    private fun requestPrediction() {
        val features = computeHRVFeatures() ?: run {
            _errorMessage.value = "Not enough beats to analyze. Try again."
            return
        }
        _isPredicting.value = true
        viewModelScope.launch {
            try {
                val result = api.predictStress(features)
                _stressResult.value = result
            } catch (e: Exception) {
                _errorMessage.value = "Prediction failed: ${e.localizedMessage}"
            }
            _isPredicting.value = false
        }
    }

    private fun computeHRVFeatures(): StressPredictRequest? {
        val rr = measurementIntervals.map { it * 1000.0 } // Convert s → ms
        if (rr.size < 10) return null

        val n = rr.size.toDouble()
        val meanRR = rr.average()
        val sortedRR = rr.sorted()
        val medianRR = sortedRR[rr.size / 2]

        val variance = rr.sumOf { (it - meanRR).pow(2) } / (n - 1)
        val sdnn = sqrt(variance)
        val cvRR = if (meanRR > 0) sdnn / meanRR else 0.0

        val diffs = rr.zipWithNext { a, b -> b - a }
        val rmssd = if (diffs.isNotEmpty()) {
            sqrt(diffs.sumOf { it * it } / diffs.size)
        } else 0.0

        val sdsd = if (diffs.size > 1) {
            val meanD = diffs.average()
            sqrt(diffs.sumOf { (it - meanD).pow(2) } / (diffs.size - 1))
        } else 0.0

        val pnn50 = if (diffs.isNotEmpty()) {
            diffs.count { abs(it) > 50 }.toDouble() / diffs.size * 100
        } else 0.0

        val pnn20 = if (diffs.isNotEmpty()) {
            diffs.count { abs(it) > 20 }.toDouble() / diffs.size * 100
        } else 0.0

        val hrs = rr.map { 60000.0 / it }
        val meanHR = hrs.average()
        val minHR = hrs.minOrNull() ?: 0.0
        val maxHR = hrs.maxOrNull() ?: 0.0
        val hrRange = maxHR - minHR
        val stdHR = if (hrs.size > 1) {
            sqrt(hrs.sumOf { (it - meanHR).pow(2) } / (hrs.size - 1))
        } else 0.0

        return StressPredictRequest(
            meanRR = meanRR,
            sdnn = sdnn,
            medianRR = medianRR,
            cvRR = cvRR,
            rmssd = rmssd,
            sdsd = sdsd,
            pnn50 = pnn50,
            pnn20 = pnn20,
            meanHR = meanHR,
            stdHR = stdHR,
            minHR = minHR,
            maxHR = maxHR,
            hrRange = hrRange,
            numBeats = rr.size.toDouble()
        )
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
        _stressResult.value = null
        _isPredicting.value = false
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
