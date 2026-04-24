package com.vesc0.heartratemonitor.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vesc0.heartratemonitor.data.model.HeartRateEntry

object PreferencesManager {
    private const val PREFS_NAME = "heart_rate_monitor"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Auth token ---

    var token: String?
        get() = prefs.getString("auth_token", null)
        set(value) {
            prefs.edit().apply {
                if (value != null) putString("auth_token", value) else remove("auth_token")
            }.apply()
        }

    val isAuthenticated: Boolean get() = token != null

    // --- Profile ---

    var email: String?
        get() = prefs.getString("auth_email", null)
        set(value) = prefs.edit().putString("auth_email", value).apply()

    var name: String?
        get() = prefs.getString("auth_name", null) ?: prefs.getString("auth_username", null)
        set(value) {
            prefs.edit()
                .putString("auth_name", value)
                .putString("auth_username", value)
                .apply()
        }

    var age: String?
        get() = prefs.getString("auth_age", null)
        set(value) = prefs.edit().putString("auth_age", value).apply()

    var healthIssues: String?
        get() = prefs.getString("auth_health_issues", null)
        set(value) = prefs.edit().putString("auth_health_issues", value).apply()

    var gender: String?
        get() = prefs.getString("auth_gender", null)
        set(value) = prefs.edit().putString("auth_gender", value).apply()

    var heightCm: String?
        get() = prefs.getString("auth_height_cm", null)
        set(value) = prefs.edit().putString("auth_height_cm", value).apply()

    var weightKg: String?
        get() = prefs.getString("auth_weight_kg", null)
        set(value) = prefs.edit().putString("auth_weight_kg", value).apply()

    fun clearProfile() {
        prefs.edit()
            .remove("auth_email")
            .remove("auth_name")
            .remove("auth_username")
            .remove("auth_age")
            .remove("auth_health_issues")
            .remove("auth_gender")
            .remove("auth_height_cm")
            .remove("auth_weight_kg")
            .apply()
    }

    // --- App appearance ---

    var appTheme: String
        get() = prefs.getString("app_theme", "system") ?: "system"
        set(value) {
            prefs.edit().putString("app_theme", value).apply()
        }

    var profileUnitSystem: String
        get() = prefs.getString("profile_unit_system", "metric") ?: "metric"
        set(value) {
            prefs.edit().putString("profile_unit_system", value).apply()
        }

    // --- Heart rate log ---

    fun saveLog(entries: List<HeartRateEntry>) {
        prefs.edit().putString("heart_rate_log", gson.toJson(entries)).apply()
    }

    fun loadLog(): List<HeartRateEntry> {
        val json = prefs.getString("heart_rate_log", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<HeartRateEntry>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearLog() {
        prefs.edit().remove("heart_rate_log").apply()
    }

    // --- Onboarding ---

    var hasSeenWelcome: Boolean
        get() = prefs.getBoolean("has_seen_welcome", false)
        set(value) {
            prefs.edit().putBoolean("has_seen_welcome", value).apply()
        }
}
