package com.vesc0.heartratemonitor.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vesc0.heartratemonitor.data.local.PreferencesManager
import com.vesc0.heartratemonitor.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiService {

    private const val BASE_URL = "http://127.0.0.1:8000"

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private var token: String?
        get() = PreferencesManager.token
        set(value) { PreferencesManager.token = value }

    val isAuthenticated: Boolean get() = token != null

    // --- Auth ---

    suspend fun register(email: String, password: String): RegisterResponse {
        val body = mapOf("email" to email, "password" to password)
        return post("/register", body, authenticated = false)
    }

    suspend fun login(email: String, password: String): AuthTokenResponse {
        val body = mapOf("email" to email, "password" to password)
        val response: AuthTokenResponse = post("/login", body, authenticated = false)
        token = response.accessToken
        return response
    }

    fun logout() {
        token = null
    }

    // --- Profile ---

    suspend fun fetchProfile(): UserProfileResponse =
        get("/me", authenticated = true)

    suspend fun updateProfile(
        username: String? = null,
        email: String? = null,
        age: Int? = null,
        healthIssues: String? = null,
        gender: String? = null,
        heightCm: Int? = null,
        weightKg: Int? = null
    ): UserProfileResponse {
        val body = mutableMapOf<String, Any>()
        username?.let { body["username"] = it }
        email?.let { body["email"] = it }
        age?.let { body["age"] = it }
        healthIssues?.let { body["heart_issues"] = it }
        gender?.let { body["gender"] = it }
        heightCm?.let { body["height_cm"] = it }
        weightKg?.let { body["weight_kg"] = it }
        return put("/me", body, authenticated = true)
    }

    // --- Heart rate ---

    suspend fun createHeartRateEntry(
        id: String? = null,
        bpm: Int,
        recordedAt: String, // ISO8601
        stressLevel: String? = null
    ): HeartRateEntryResponse {
        val body = mutableMapOf<String, Any>("bpm" to bpm, "recorded_at" to recordedAt)
        id?.let { body["id"] = it }
        stressLevel?.let { body["stress_level"] = it }
        return post("/heart-rate", body, authenticated = true)
    }

    suspend fun fetchHeartRateEntries(
        limit: Int = 5000,
        offset: Int = 0
    ): List<HeartRateEntryResponse> =
        get("/heart-rate?limit=$limit&offset=$offset", authenticated = true)

    suspend fun deleteHeartRateEntries(ids: List<String>) {
        val body = mapOf("ids" to ids)
        post<Map<String, Any>>("/heart-rate/batch-delete", body, authenticated = true)
    }

    // --- Stress predict ---

    suspend fun predictStress(features: StressPredictRequest): StressPredictResponse {
        val bodyMap = gson.fromJson<Map<String, Any>>(
            gson.toJson(features),
            object : TypeToken<Map<String, Any>>() {}.type
        )
        return post("/stress-predict-llm", bodyMap, authenticated = true)
    }

    // --- Internal ---

    private suspend inline fun <reified T> get(
        path: String,
        authenticated: Boolean = false
    ): T = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(BASE_URL + path).get()
        if (authenticated) {
            val t = token ?: throw ApiException("Session expired. Please log in again.")
            requestBuilder.addHeader("Authorization", "Bearer $t")
        }
        requestBuilder.addHeader("Content-Type", "application/json")
        execute(requestBuilder.build())
    }

    private suspend inline fun <reified T> post(
        path: String,
        body: Any,
        authenticated: Boolean = false
    ): T = withContext(Dispatchers.IO) {
        val json = gson.toJson(body)
        val requestBuilder = Request.Builder()
            .url(BASE_URL + path)
            .post(json.toRequestBody(JSON_MEDIA))
        if (authenticated) {
            val t = token ?: throw ApiException("Session expired. Please log in again.")
            requestBuilder.addHeader("Authorization", "Bearer $t")
        }
        requestBuilder.addHeader("Content-Type", "application/json")
        execute(requestBuilder.build())
    }

    private suspend inline fun <reified T> put(
        path: String,
        body: Any,
        authenticated: Boolean = false
    ): T = withContext(Dispatchers.IO) {
        val json = gson.toJson(body)
        val requestBuilder = Request.Builder()
            .url(BASE_URL + path)
            .put(json.toRequestBody(JSON_MEDIA))
        if (authenticated) {
            val t = token ?: throw ApiException("Session expired. Please log in again.")
            requestBuilder.addHeader("Authorization", "Bearer $t")
        }
        requestBuilder.addHeader("Content-Type", "application/json")
        execute(requestBuilder.build())
    }

    private inline fun <reified T> execute(request: Request): T {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException(e.localizedMessage ?: "Network error")
        }

        val bodyString = response.body?.string() ?: ""

        when (response.code) {
            in 200..299 -> {
                return try {
                    gson.fromJson(bodyString, T::class.java)
                } catch (e: Exception) {
                    // Try as TypeToken for generic types (List, Map)
                    try {
                        val type = object : TypeToken<T>() {}.type
                        gson.fromJson(bodyString, type)
                    } catch (_: Exception) {
                        throw ApiException("Failed to process server response.")
                    }
                }
            }
            401 -> {
                token = null
                throw ApiException("Session expired. Please log in again.")
            }
            else -> {
                val detail = try {
                    gson.fromJson(bodyString, ApiErrorDetail::class.java)?.detail
                } catch (_: Exception) { null }
                throw ApiException(detail ?: "Server error (${response.code})")
            }
        }
    }

    class ApiException(message: String) : Exception(message)
}
