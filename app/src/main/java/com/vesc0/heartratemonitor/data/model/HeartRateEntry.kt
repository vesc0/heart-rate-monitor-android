package com.vesc0.heartratemonitor.data.model

import java.util.UUID

data class HeartRateEntry(
    val id: String = UUID.randomUUID().toString(),
    val bpm: Int,
    val date: Long, // epoch millis
    val stressLevel: String? = null
)
