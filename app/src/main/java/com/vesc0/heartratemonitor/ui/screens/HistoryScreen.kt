package com.vesc0.heartratemonitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vesc0.heartratemonitor.data.model.HeartRateEntry
import com.vesc0.heartratemonitor.viewmodel.HeartRateViewModel
import java.text.SimpleDateFormat
import java.util.*

private enum class RangeMode { WEEKLY, MONTHLY }

private data class DailyBpmRange(
    val dayMillis: Long,
    val min: Int,
    val max: Int,
    val avg: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HeartRateViewModel) {
    val log by vm.log.collectAsState()
    var rangeMode by remember { mutableStateOf(RangeMode.WEEKLY) }
    var weekOffset by remember { mutableIntStateOf(0) }
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDay by remember { mutableStateOf<Long?>(null) }
    var visibleCount by remember { mutableIntStateOf(5) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedEntries by remember { mutableStateOf(setOf<String>()) }

    val calendar = remember {
        Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY }
    }

    // --- Period computations ---

    fun startOfDay(millis: Long): Long {
        val cal = calendar.clone() as Calendar
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val todayStart = startOfDay(System.currentTimeMillis())

    fun weekRange(): Pair<Long, Long> {
        val cal = calendar.clone() as Calendar
        cal.timeInMillis = todayStart
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val mondayOfCurrent = cal.timeInMillis
        val start = mondayOfCurrent + weekOffset * 7L * 86_400_000L
        val end = start + 6L * 86_400_000L
        return start to end
    }

    fun monthRange(): Pair<Long, Long> {
        val cal = calendar.clone() as Calendar
        cal.timeInMillis = todayStart
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, monthOffset)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.DAY_OF_MONTH, -1)
        val end = cal.timeInMillis
        return start to end
    }

    fun weekDays(): List<Long> {
        val (start, _) = weekRange()
        return (0..6).map { start + it * 86_400_000L }
    }

    fun monthDays(): List<Long> {
        val (start, end) = monthRange()
        val days = mutableListOf<Long>()
        var cur = start
        while (cur <= end) {
            days.add(cur)
            cur += 86_400_000L
        }
        return days
    }

    // --- Aggregation ---

    val dailyRangesAll = remember(log) {
        log.groupBy { startOfDay(it.date) }
            .map { (day, entries) ->
                DailyBpmRange(
                    dayMillis = day,
                    min = entries.minOf { it.bpm },
                    max = entries.maxOf { it.bpm },
                    avg = entries.sumOf { it.bpm } / entries.size
                )
            }
            .sortedBy { it.dayMillis }
    }

    val dailyRangesMap = remember(dailyRangesAll) {
        dailyRangesAll.associateBy { it.dayMillis }
    }

    val currentDailyRanges = remember(rangeMode, weekOffset, monthOffset, dailyRangesMap) {
        val days = if (rangeMode == RangeMode.WEEKLY) weekDays() else monthDays()
        days.mapNotNull { dailyRangesMap[it] }
    }

    // --- Stats ---

    fun periodStats(ranges: List<DailyBpmRange>): Triple<Int, Int, Int>? {
        if (ranges.isEmpty()) return null
        val mins = ranges.map { it.min }.filter { it > 0 }
        val maxs = ranges.map { it.max }.filter { it > 0 }
        val avgs = ranges.map { it.avg }.filter { it > 0 }
        if (mins.isEmpty()) return null
        return Triple(mins.min(), avgs.average().toInt(), maxs.max())
    }

    val stats = remember(currentDailyRanges, selectedDay, rangeMode) {
        if (rangeMode == RangeMode.WEEKLY && selectedDay != null) {
            dailyRangesMap[selectedDay]?.let { Triple(it.min, it.avg, it.max) }
        } else {
            periodStats(currentDailyRanges)
        }
    }

    // --- Filtering measurements ---

    val filteredMeasurements = remember(log, rangeMode, weekOffset, monthOffset, selectedDay) {
        val (start, end) = if (rangeMode == RangeMode.WEEKLY) weekRange() else monthRange()
        val endOfDay = end + 86_400_000L - 1
        if (rangeMode == RangeMode.WEEKLY && selectedDay != null) {
            log.filter { it.date >= selectedDay!! && it.date <= selectedDay!! + 86_400_000L - 1 }
        } else {
            log.filter { it.date in start..endOfDay }
        }
    }

    val pagedLog = remember(filteredMeasurements, visibleCount) {
        filteredMeasurements.sortedByDescending { it.date }.take(visibleCount)
    }

    val hasMore = visibleCount < filteredMeasurements.size

    // --- Period title ---

    val periodTitle = remember(rangeMode, weekOffset, monthOffset) {
        val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
        val yearFmt = SimpleDateFormat("yyyy", Locale.getDefault())
        if (rangeMode == RangeMode.WEEKLY) {
            val (start, end) = weekRange()
            "${fmt.format(Date(start))} – ${fmt.format(Date(end))}, ${yearFmt.format(Date(end))}"
        } else {
            val (start, _) = monthRange()
            SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(start))
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("History") }) }
    ) { padding ->
        if (log.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No Records", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Records will appear here after you complete a measurement",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { vm.seedSampleData() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Load Demo Data") }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                // Range mode picker
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        SegmentedButton(
                            selected = rangeMode == RangeMode.WEEKLY,
                            onClick = {
                                rangeMode = RangeMode.WEEKLY; weekOffset = 0; selectedDay = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) { Text("Weekly") }
                        SegmentedButton(
                            selected = rangeMode == RangeMode.MONTHLY,
                            onClick = {
                                rangeMode = RangeMode.MONTHLY; monthOffset = 0; selectedDay = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) { Text("Monthly") }
                    }
                }

                // Period navigation
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (rangeMode == RangeMode.WEEKLY) weekOffset-- else monthOffset--
                            selectedDay = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(periodTitle, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        val canGoForward = if (rangeMode == RangeMode.WEEKLY) weekOffset < 0 else monthOffset < 0
                        IconButton(
                            onClick = {
                                if (rangeMode == RangeMode.WEEKLY) weekOffset++ else monthOffset++
                                selectedDay = null
                            },
                            enabled = canGoForward
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next",
                                tint = if (canGoForward) LocalContentColor.current
                                else LocalContentColor.current.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                // Chart
                item {
                    BpmRangeChart(
                        data = currentDailyRanges,
                        allDays = if (rangeMode == RangeMode.WEEKLY) weekDays() else monthDays(),
                        selectedDay = selectedDay,
                        onDaySelected = { day ->
                            if (rangeMode == RangeMode.WEEKLY) {
                                selectedDay = if (selectedDay == day) null else day
                                visibleCount = 5
                            }
                        },
                        isWeekly = rangeMode == RangeMode.WEEKLY,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(vertical = 8.dp)
                    )
                }

                // Stats
                if (stats != null) {
                    item {
                        val (min, avg, max) = stats!!
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatPill("Min", min)
                            StatPill("Avg", avg)
                            StatPill("Max", max)
                        }
                    }
                }

                // Measurements header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Measurements", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        if (isSelectionMode) {
                            TextButton(onClick = {
                                isSelectionMode = false; selectedEntries = emptySet()
                            }) { Text("Cancel") }
                            TextButton(onClick = {
                                selectedEntries = filteredMeasurements.map { it.id }.toSet()
                            }) { Text("Select All") }
                            TextButton(
                                onClick = {
                                    vm.deleteEntries(selectedEntries)
                                    isSelectionMode = false; selectedEntries = emptySet()
                                },
                                enabled = selectedEntries.isNotEmpty()
                            ) { Text("Delete", color = if (selectedEntries.isNotEmpty()) Color.Red else Color.Gray) }
                        } else {
                            TextButton(onClick = { isSelectionMode = true }) { Text("Select") }
                        }
                    }
                }

                // Measurement list
                itemsIndexed(pagedLog, key = { _, e -> e.id }) { _, entry ->
                    MeasurementRow(
                        entry = entry,
                        isSelectionMode = isSelectionMode,
                        isSelected = entry.id in selectedEntries,
                        onClick = {
                            if (isSelectionMode) {
                                selectedEntries = if (entry.id in selectedEntries)
                                    selectedEntries - entry.id else selectedEntries + entry.id
                            }
                        }
                    )
                    HorizontalDivider()
                }

                if (hasMore) {
                    item {
                        Button(
                            onClick = { visibleCount += 5 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) { Text("Show more", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

// ──────────────────────────── Chart ────────────────────────────

@Composable
private fun BpmRangeChart(
    data: List<DailyBpmRange>,
    allDays: List<Long>,
    selectedDay: Long?,
    onDaySelected: (Long) -> Unit,
    isWeekly: Boolean,
    modifier: Modifier = Modifier
) {
    val daysFmt = remember { SimpleDateFormat("EEE", Locale.getDefault()) }

    Canvas(
        modifier = modifier.pointerInput(data, isWeekly) {
            if (!isWeekly) return@pointerInput
            detectTapGestures { offset ->
                val leftPad = 8f
                val rightPad = 48f
                val chartW = size.width - leftPad - rightPad
                if (allDays.isEmpty()) return@detectTapGestures
                val barSpacing = chartW / allDays.size
                val tappedIdx = ((offset.x - leftPad) / barSpacing).toInt()
                if (tappedIdx in allDays.indices) {
                    onDaySelected(allDays[tappedIdx])
                }
            }
        }
    ) {
        val leftPad = 8f
        val rightPad = 48f
        val topPad = 8f
        val bottomPad = 28f
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad

        if (data.isEmpty() || allDays.isEmpty()) return@Canvas

        // Y-axis range
        val allMin = data.minOf { it.min }.coerceAtLeast(40)
        val allMax = data.maxOf { it.max }.coerceAtMost(200)
        val yMin = (allMin - 10).coerceAtLeast(30)
        val yMax = allMax + 10

        fun yPos(bpm: Int): Float =
            topPad + chartH * (1 - (bpm - yMin).toFloat() / (yMax - yMin))

        val barWidth = if (isWeekly) 14f else 6f
        val dataMap = data.associateBy { it.dayMillis }

        allDays.forEachIndexed { i, dayMs ->
            val range = dataMap[dayMs] ?: return@forEachIndexed
            val x = leftPad + chartW * (i + 0.5f) / allDays.size
            val isSelected = selectedDay == dayMs
            val alpha = if (isWeekly && selectedDay != null && !isSelected) 0.35f else 1f
            val barColor = if (isSelected) Color.Red else Color.Red.copy(alpha = 0.85f)
            val width = if (isWeekly && isSelected) 18f else barWidth

            drawLine(
                color = barColor.copy(alpha = alpha),
                start = Offset(x, yPos(range.max)),
                end = Offset(x, yPos(range.min)),
                strokeWidth = width,
                cap = StrokeCap.Round
            )
        }

        // Y-axis labels
        val paint = android.graphics.Paint().apply {
            textSize = 28f
            color = android.graphics.Color.GRAY
            textAlign = android.graphics.Paint.Align.LEFT
        }
        listOf(yMin, (yMin + yMax) / 2, yMax).forEach { bpm ->
            drawContext.canvas.nativeCanvas.drawText(
                "$bpm", size.width - rightPad + 4, yPos(bpm) + 10, paint
            )
        }

        // X-axis labels
        val xPaint = android.graphics.Paint().apply {
            textSize = 24f
            color = android.graphics.Color.GRAY
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val labelStep = if (isWeekly) 1 else maxOf(1, allDays.size / 4)
        for (i in allDays.indices step labelStep) {
            val x = leftPad + chartW * (i + 0.5f) / allDays.size
            val label = if (isWeekly) {
                daysFmt.format(Date(allDays[i]))
            } else {
                val cal = Calendar.getInstance().apply { timeInMillis = allDays[i] }
                "${cal.get(Calendar.DAY_OF_MONTH)}"
            }
            drawContext.canvas.nativeCanvas.drawText(
                label, x, size.height - 2, xPaint
            )
        }
    }
}

// ──────────────────────────── Helpers ──────────────────────────

@Composable
private fun StatPill(title: String, value: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("$value BPM", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MeasurementRow(
    entry: HeartRateEntry,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isSelectionMode) { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Icon(
                if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text("${entry.bpm} BPM", fontWeight = FontWeight.SemiBold)
            entry.stressLevel?.let {
                Text(
                    stressDisplayTextFor(it),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = stressColorFor(it)
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(dateFmt.format(Date(entry.date)), fontSize = 14.sp)
            Text(timeFmt.format(Date(entry.date)), fontSize = 12.sp, color = Color.Gray)
        }
    }
}

private fun stressColorFor(stress: String): Color {
    val pct = stress.replace("%", "").trim().toIntOrNull()
    if (pct != null) {
        return when {
            pct >= 70 -> Color.Red
            pct >= 40 -> Color(0xFFFF9800)
            else -> Color(0xFF4CAF50)
        }
    }

    val normalized = stress.lowercase(Locale.getDefault())
    return when {
        normalized.contains("high") || normalized.contains("stressed") -> Color.Red
        normalized.contains("medium") || normalized.contains("moderate") -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }
}

private fun stressDisplayTextFor(stress: String): String {
    val pct = stress.replace("%", "").trim().toIntOrNull()
    return if (pct != null) {
        "$pct% stressed"
    } else {
        stress
    }
}
