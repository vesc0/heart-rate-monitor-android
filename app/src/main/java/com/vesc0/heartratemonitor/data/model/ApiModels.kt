package com.vesc0.heartratemonitor.data.model

import com.google.gson.annotations.SerializedName

// --- Auth ---

data class AuthTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    val username: String?,
    val email: String?,
    val age: Int?,
    @SerializedName("health_issues") val healthIssues: String?,
    val gender: String?,
    @SerializedName("height_cm") val heightCm: Int?,
    @SerializedName("weight_kg") val weightKg: Int?
)

data class RegisterResponse(
    val message: String,
    val username: String
)

data class UserProfileResponse(
    val username: String,
    val email: String,
    val age: Int?,
    @SerializedName("health_issues") val healthIssues: String?,
    val gender: String?,
    @SerializedName("height_cm") val heightCm: Int?,
    @SerializedName("weight_kg") val weightKg: Int?
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
    // Time-domain HRV (11)
    val sdnn: Double,
    @SerializedName("median_rr") val medianRR: Double,
    @SerializedName("cv_rr") val cvRR: Double,
    val rmssd: Double,
    val pnn50: Double,
    val pnn20: Double,
    @SerializedName("mean_hr") val meanHR: Double,
    @SerializedName("std_hr") val stdHR: Double,
    @SerializedName("min_hr") val minHR: Double,
    @SerializedName("max_hr") val maxHR: Double,
    @SerializedName("hr_range") val hrRange: Double,
    // Frequency-domain HRV (5)
    @SerializedName("lf_power") val lfPower: Double = 0.0,
    @SerializedName("hf_power") val hfPower: Double = 0.0,
    @SerializedName("lf_hf_ratio") val lfHfRatio: Double = 0.0,
    @SerializedName("total_power") val totalPower: Double = 0.0,
    @SerializedName("lf_norm") val lfNorm: Double = 0.0,
    // Nonlinear HRV (3)
    val sd1: Double = 0.0,
    val sd2: Double = 0.0,
    @SerializedName("sd_ratio") val sdRatio: Double = 0.0,
    // Demographics (optional)
    val age: Double? = null,
    @SerializedName("gender_male") val genderMale: Double? = null,
    @SerializedName("height_cm") val heightCm: Double? = null,
    @SerializedName("weight_kg") val weightKg: Double? = null
)

data class StressPredictResponse(
    @SerializedName("stress_level_pct") val stressLevelPct: Double,
    @SerializedName("is_stressed") val isStressed: Boolean
)

// --- Error ---

data class ApiErrorDetail(
    val detail: String
)
