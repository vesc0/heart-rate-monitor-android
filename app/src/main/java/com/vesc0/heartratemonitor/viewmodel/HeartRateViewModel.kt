package com.vesc0.heartratemonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesc0.heartratemonitor.data.api.ApiService
import com.vesc0.heartratemonitor.data.local.PreferencesManager
import com.vesc0.heartratemonitor.data.model.HeartRateEntry
import com.vesc0.heartratemonitor.data.model.MeasurementState
import com.vesc0.heartratemonitor.data.model.SessionPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.sin

class HeartRateViewModel : ViewModel() {

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

    private val tapTimes = mutableListOf<Long>()
    private val validIntervals = mutableListOf<Double>()
    private val smoothingWindow = 5

    val hasTapped: Boolean
        get() = tapTimes.isNotEmpty()

    private val measureDuration = 12L
    private val minValidInterval = 0.27
    private val maxValidInterval = 1.50
    private val bpmRevealAfter = 4L

    private var countdownJob: Job? = null
    private var phaseTimerJob: Job? = null
    private var bpmRevealJob: Job? = null

    private val api = ApiService

    init {
        loadData()
    }

    fun startSession() {
        cancelAllJobs()
        resetInMemoryOnly()
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

        if (tapTimes.size < 2) return

        val interval = (now - tapTimes[tapTimes.size - 2]) / 1000.0
        if (interval < minValidInterval || interval > maxValidInterval) return

        validIntervals.add(interval)
        updateLiveBpm()
        pulseHeart()
    }

    fun stopSession() {
        cancelAllJobs()
        resetInMemoryOnly()
        _phase.value = SessionPhase.IDLE
    }

    fun startNewSession() {
        cancelAllJobs()
        resetInMemoryOnly()
        _phase.value = SessionPhase.IDLE
    }

    fun addEntry(entry: HeartRateEntry) {
        _log.value = listOf(entry) + _log.value
        saveLocal()
        syncCreate(entry)
    }

    fun updateEntry(entry: HeartRateEntry) {
        val idx = _log.value.indexOfFirst { it.id == entry.id }
        if (idx < 0) return

        val updated = _log.value.toMutableList()
        updated[idx] = entry
        _log.value = updated
        saveLocal()
        syncCreate(entry)
    }

    // Legacy overload retained for existing call sites.
    fun updateEntry(index: Int, entry: HeartRateEntry) {
        val list = _log.value.toMutableList()
        if (index !in list.indices) return
        list[index] = entry
        _log.value = list
        saveLocal()
        syncCreate(entry)
    }

    fun deleteEntries(ids: Set<String>) {
        _log.value = _log.value.filter { it.id !in ids }
        saveLocal()
        syncDelete(ids)
    }

    fun saveLocal() {
        PreferencesManager.saveLog(_log.value)
    }

    fun saveData() {
        saveLocal()
    }

    fun clearForLogout() {
        _log.value = emptyList()
        PreferencesManager.clearLog()
    }

    fun refreshFromServer() {
        if (!api.isAuthenticated) return

        viewModelScope.launch {
            try {
                val allRemote = mutableListOf<com.vesc0.heartratemonitor.data.model.HeartRateEntryResponse>()
                var offset = 0
                val pageSize = 500

                while (true) {
                    val page = api.fetchHeartRateEntries(limit = pageSize, offset = offset)
                    allRemote.addAll(page)
                    if (page.size < pageSize) break
                    offset += pageSize
                }

                _log.value = allRemote.map { remote ->
                    HeartRateEntry(
                        id = remote.id,
                        bpm = remote.bpm,
                        date = parseIsoToMillis(remote.recordedAt),
                        stressLevel = remote.stressLevel,
                        activityState = remote.activityState
                    )
                }
                saveLocal()
            } catch (_: Exception) {
                // Keep local cache on refresh errors.
            }
        }
    }

    fun seedSampleData() {
        if (_log.value.isNotEmpty()) return

        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val daysBack = 135
        val entries = mutableListOf<HeartRateEntry>()
        val states = MeasurementState.entries

        repeat(daysBack) { d ->
            val dayStart = now - d * dayMs
            val count = (1..3).random()
            val baseline = 68 + (6 * sin(d / 9.0)).toInt()

            repeat(count) { i ->
                val bpm = (baseline + (-12..14).random()).coerceIn(45, 160)
                val hour = listOf(9, 14, 20).random() + i
                val minute = (0..59).random()
                val ts = dayStart + hour * 3_600_000L + minute * 60_000L
                val stress = if ((0..1).random() == 1) "${(18..92).random()}%" else null
                val state = states.random()

                entries.add(
                    HeartRateEntry(
                        id = UUID.randomUUID().toString(),
                        bpm = bpm,
                        date = ts,
                        stressLevel = stress,
                        activityState = state
                    )
                )
            }
        }

        entries.sortByDescending { it.date }
        _log.value = entries
        saveLocal()
    }

    private fun finishMeasuring() {
        updateLiveBpm()
        endSession()
    }

    private fun endSession() {
        _currentBPM.value = computeAverageBpm(validIntervals)
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
                remaining -= 1
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

    private fun updateLiveBpm() {
        _currentBPM.value = computeAverageBpm(validIntervals.takeLast(smoothingWindow))
    }

    private fun computeAverageBpm(intervals: List<Double>): Int? {
        if (intervals.isEmpty()) return null
        val avg = intervals.average()
        if (avg <= 0) return null
        return (60.0 / avg).toInt()
    }

    private fun resetInMemoryOnly() {
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

    private fun loadData() {
        _log.value = PreferencesManager.loadLog()
        refreshFromServer()
    }

    private fun syncCreate(entry: HeartRateEntry) {
        if (!api.isAuthenticated) return

        viewModelScope.launch {
            try {
                val isoDate = Instant.ofEpochMilli(entry.date).toString()
                api.createHeartRateEntry(
                    id = entry.id,
                    bpm = entry.bpm,
                    recordedAt = isoDate,
                    stressLevel = entry.stressLevel,
                    activityState = entry.activityState
                )
            } catch (_: Exception) {
                // Fire-and-forget sync.
            }
        }
    }

    private fun syncDelete(ids: Set<String>) {
        if (!api.isAuthenticated) return

        viewModelScope.launch {
            try {
                api.deleteHeartRateEntries(ids.toList())
            } catch (_: Exception) {
                // Fire-and-forget sync.
            }
        }
    }

    private fun parseIsoToMillis(raw: String): Long {
        return try {
            Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
                } catch (_: Exception) {
                    try {
                        LocalDateTime.parse(raw.substringBeforeLast("."), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            .toInstant(ZoneOffset.UTC)
                            .toEpochMilli()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                }
            }
        }
    }
}
