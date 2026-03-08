package com.vesc0.heartratemonitor.data.model

import com.google.gson.annotations.SerializedName

// --- Auth ---

data class AuthTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    val username: String?,
    val email: String?,
    val age: Int?,
    @SerializedName("health_issues") val healthIssues: String?
)

data class RegisterResponse(
    val message: String,
    val username: String
)

data class UserProfileResponse(
    val username: String,
    val email: String,
    val age: Int?,
    @SerializedName("health_issues") val healthIssues: String?
)

// --- Heart rate ---

data class HeartRateEntryResponse(
    val id: String,
    val bpm: Int,
    @SerializedName("recorded_at") val recordedAt: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("stress_level") val stressLevel: String?
)

// --- Stress prediction ---

data class StressPredictRequest(
    @SerializedName("mean_rr") val meanRR: Double,
    val sdnn: Double,
    @SerializedName("median_rr") val medianRR: Double,
    @SerializedName("cv_rr") val cvRR: Double,
    val rmssd: Double,
    val sdsd: Double,
    val pnn50: Double,
    val pnn20: Double,
    @SerializedName("mean_hr") val meanHR: Double,
    @SerializedName("std_hr") val stdHR: Double,
    @SerializedName("min_hr") val minHR: Double,
    @SerializedName("max_hr") val maxHR: Double,
    @SerializedName("hr_range") val hrRange: Double,
    @SerializedName("num_beats") val numBeats: Double
)

data class StressPredictResponse(
    @SerializedName("is_stressed") val isStressed: Boolean,
    @SerializedName("stress_level") val stressLevel: String
)

// --- Error ---

data class ApiErrorDetail(
    val detail: String
)
