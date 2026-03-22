package com.vesc0.heartratemonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesc0.heartratemonitor.data.api.ApiService
import com.vesc0.heartratemonitor.data.local.PreferencesManager
import com.vesc0.heartratemonitor.data.model.HeartRateEntry
import com.vesc0.heartratemonitor.data.model.SessionPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HeartRateViewModel : ViewModel() {

    // --- UI state ---
    private val _phase = MutableStateFlow(SessionPhase.IDLE)
    val phase = _phase.asStateFlow()

    private val _currentBPM = MutableStateFlow<Int?>(null)
    val currentBPM = _currentBPM.asStateFlow()

    private val _heartScale = MutableStateFlow(1.0f)
    val heartScale = _heartScale.asStateFlow()

    private val _secondsLeft = MutableStateFlow(0)
    val secondsLeft = _secondsLeft.asStateFlow()

    private val _canShowBPM = MutableStateFlow(false)
    val canShowBPM = _canShowBPM.asStateFlow()

    private val _log = MutableStateFlow<List<HeartRateEntry>>(emptyList())
    val log = _log.asStateFlow()

    // --- Tap tracking ---
    private val tapTimes = mutableListOf<Long>()
    private val validIntervals = mutableListOf<Double>()
    private val smoothingWindow = 5

    val hasTapped: Boolean get() = tapTimes.isNotEmpty()

    // --- Durations ---
    private val measureDuration = 12L // seconds
    private val minValidInterval = 0.27 // ~220 BPM
    private val maxValidInterval = 1.50 // ~40 BPM
    private val bpmRevealAfter = 4L // seconds

    // --- Coroutine jobs ---
    private var countdownJob: Job? = null
    private var phaseTimerJob: Job? = null
    private var bpmRevealJob: Job? = null

    private val api = ApiService

    init {
        loadData()
    }

    // --- Session ---

    fun startSession() {
        cancelAllJobs()
        resetInMemory()
        _phase.value = SessionPhase.MEASURING
    }

    fun recordTap() {
        if (_phase.value != SessionPhase.MEASURING) return
        val now = System.currentTimeMillis()

        if (tapTimes.isEmpty()) {
            startCountdown(measureDuration)
            scheduleBpmReveal(bpmRevealAfter)
        }

        tapTimes.add(now)

        if (tapTimes.size >= 2) {
            val interval = (now - tapTimes[tapTimes.size - 2]) / 1000.0
            if (interval < minValidInterval || interval > maxValidInterval) return
            validIntervals.add(interval)
            updateLiveBPM()
            pulseHeart()
        }
    }

    fun stopSession() {
        cancelAllJobs()
        resetInMemory()
        _phase.value = SessionPhase.IDLE
    }

    fun startNewSession() {
        cancelAllJobs()
        resetInMemory()
        _phase.value = SessionPhase.IDLE
    }

    // --- Entry management ---

    fun addEntry(entry: HeartRateEntry) {
        _log.value = listOf(entry) + _log.value
        saveLocal()
        syncCreate(entry)
    }

    fun updateEntry(index: Int, entry: HeartRateEntry) {
        val list = _log.value.toMutableList()
        if (index in list.indices) {
            list[index] = entry
            _log.value = list
            saveLocal()
            syncCreate(entry)
        }
    }

    fun deleteEntries(ids: Set<String>) {
        _log.value = _log.value.filter { it.id !in ids }
        saveLocal()
        syncDelete(ids)
    }

    fun saveLocal() {
        PreferencesManager.saveLog(_log.value)
    }

    fun saveData() = saveLocal()

    fun clearForLogout() {
        _log.value = emptyList()
        PreferencesManager.clearLog()
    }

    fun refreshFromServer() {
        if (!api.isAuthenticated) return
        viewModelScope.launch {
            try {
                val remote = api.fetchHeartRateEntries()
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                _log.value = remote.map { r ->
                    val millis = try {
                        isoFormat.parse(r.recordedAt.take(19))?.time ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                    HeartRateEntry(
                        id = r.id,
                        bpm = r.bpm,
                        date = millis,
                        stressLevel = r.stressLevel
                    )
                }
                saveLocal()
            } catch (_: Exception) {
                // Keep local data on error
            }
        }
    }

    fun seedSampleData() {
        if (_log.value.isNotEmpty()) return
        val entries = mutableListOf<HeartRateEntry>()
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val daysBack = 135

        for (d in 0 until daysBack) {
            val dayStart = now - d * dayMs
            val count = (1..3).random()
            val baseline = 68 + (6 * kotlin.math.sin(d / 9.0)).toInt()
            for (i in 0 until count) {
                val bpm = (baseline + (-12..14).random()).coerceIn(45, 160)
                val hour = listOf(9, 14, 20).random() + i
                val minute = (0 until 60).random()
                val ts = dayStart + hour * 3_600_000L + minute * 60_000L
                val stress = if ((0..1).random() == 1) "${(18..92).random()}%" else null
                entries.add(HeartRateEntry(bpm = bpm, date = ts, stressLevel = stress))
            }
        }
        entries.sortByDescending { it.date }
        _log.value = entries
        saveData()
    }

    // --- Internal ---

    private fun finishMeasuring() {
        updateLiveBPM()
        endSession()
    }

    private fun endSession() {
        val finalBPM = computeAverageBPM(validIntervals)
        _currentBPM.value = finalBPM

        if (finalBPM != null) {
            addEntry(HeartRateEntry(bpm = finalBPM, date = System.currentTimeMillis()))
        }

        _phase.value = SessionPhase.FINISHED
        cancelAllJobs()
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
            if (_phase.value == SessionPhase.MEASURING) {
                finishMeasuring()
            }
        }
    }

    private fun scheduleBpmReveal(after: Long) {
        _canShowBPM.value = false
        bpmRevealJob?.cancel()
        bpmRevealJob = viewModelScope.launch {
            delay(after * 1000)
            _canShowBPM.value = true
        }
    }

    private fun pulseHeart() {
        viewModelScope.launch {
            _heartScale.value = 1.2f
            delay(120)
            _heartScale.value = 1.0f
        }
    }

    private fun updateLiveBPM() {
        _currentBPM.value = computeAverageBPM(validIntervals.takeLast(smoothingWindow))
    }

    private fun computeAverageBPM(intervals: List<Double>): Int? {
        if (intervals.isEmpty()) return null
        val avg = intervals.average()
        if (avg <= 0) return null
        return (60.0 / avg).toInt()
    }

    private fun resetInMemory() {
        _currentBPM.value = null
        _heartScale.value = 1.0f
        _secondsLeft.value = 0
        _canShowBPM.value = false
        tapTimes.clear()
        validIntervals.clear()
    }

    private fun cancelAllJobs() {
        countdownJob?.cancel()
        phaseTimerJob?.cancel()
        bpmRevealJob?.cancel()
    }

    // --- Persistence ---

    private fun loadData() {
        _log.value = PreferencesManager.loadLog()
        refreshFromServer()
    }

    private fun syncCreate(entry: HeartRateEntry) {
        if (!api.isAuthenticated) return
        viewModelScope.launch {
            try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                api.createHeartRateEntry(
                    id = entry.id,
                    bpm = entry.bpm,
                    recordedAt = isoFormat.format(entry.date),
                    stressLevel = entry.stressLevel
                )
            } catch (_: Exception) { }
        }
    }

    private fun syncDelete(ids: Set<String>) {
        if (!api.isAuthenticated) return
        viewModelScope.launch {
            try { api.deleteHeartRateEntries(ids.toList()) } catch (_: Exception) { }
        }
    }
}
