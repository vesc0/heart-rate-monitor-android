package com.vesc0.heartratemonitor.data.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

enum class MeasurementState(
    val displayName: String,
    val contextDescription: String,
    val normalRange: IntRange
) {
    @SerializedName("resting")
    RESTING(
        displayName = "Resting",
        contextDescription = "at rest",
        normalRange = 60..100
    ),
    @SerializedName("activity")
    ACTIVITY(
        displayName = "Activity",
        contextDescription = "during activity",
        normalRange = 90..160
    ),
    @SerializedName("recovery")
    RECOVERY(
        displayName = "Recovery",
        contextDescription = "during recovery",
        normalRange = 60..120
    );

    fun assessment(bpm: Int): HeartRateRangeAssessment {
        val low = normalRange.first
        val high = normalRange.last
        return if (bpm in normalRange) {
            HeartRateRangeAssessment(
                isNormal = true,
                title = "Normal for $displayName",
                detail = "Expected range $low-$high BPM $contextDescription."
            )
        } else {
            val relation = if (bpm < low) "Below" else "Above"
            HeartRateRangeAssessment(
                isNormal = false,
                title = "$relation normal for $displayName",
                detail = "Expected range $low-$high BPM $contextDescription."
            )
        }
    }
}

data class HeartRateRangeAssessment(
    val isNormal: Boolean,
    val title: String,
    val detail: String
)

data class HeartRateEntry(
    val id: String = UUID.randomUUID().toString(),
    val bpm: Int,
    val date: Long, // epoch millis
    val stressLevel: String? = null,
    val activityState: MeasurementState? = null
)
