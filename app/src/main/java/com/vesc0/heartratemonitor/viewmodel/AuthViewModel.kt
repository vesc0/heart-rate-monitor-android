package com.vesc0.heartratemonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesc0.heartratemonitor.data.api.ApiService
import com.vesc0.heartratemonitor.data.local.PreferencesManager
import com.vesc0.heartratemonitor.data.model.AuthTokenResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val api = ApiService

    // --- Published state ---

    private val _isSignedIn = MutableStateFlow(PreferencesManager.isAuthenticated)
    val isSignedIn = _isSignedIn.asStateFlow()

    private val _currentEmail = MutableStateFlow(PreferencesManager.email)
    val currentEmail = _currentEmail.asStateFlow()

    private val _username = MutableStateFlow(PreferencesManager.username)
    val username = _username.asStateFlow()

    private val _age = MutableStateFlow(PreferencesManager.age)
    val age = _age.asStateFlow()

    private val _healthIssues = MutableStateFlow(PreferencesManager.healthIssues)
    val healthIssues = _healthIssues.asStateFlow()

    private val _gender = MutableStateFlow(PreferencesManager.gender)
    val gender = _gender.asStateFlow()

    private val _heightCm = MutableStateFlow(PreferencesManager.heightCm)
    val heightCm = _heightCm.asStateFlow()

    private val _weightKg = MutableStateFlow(PreferencesManager.weightKg)
    val weightKg = _weightKg.asStateFlow()

    // --- Auth actions ---

    suspend fun signUp(email: String, password: String) {
        validateEmail(email)
        if (password.length < 6) throw AuthError("Password should be at least 6 characters.")

        try {
            api.register(email, password)
            val response = api.login(email, password)
            applyLoginResponse(response, email)
        } catch (e: ApiService.ApiException) {
            throw AuthError(e.message ?: "Registration failed.")
        }
    }

    suspend fun signIn(email: String, password: String) {
        validateEmail(email)
        if (password.isEmpty()) throw AuthError("Password cannot be empty.")

        try {
            val response = api.login(email, password)
            applyLoginResponse(response, email)
        } catch (e: ApiService.ApiException) {
            throw AuthError(e.message ?: "Login failed.")
        }
    }

    fun signOut() {
        api.logout()
        _isSignedIn.value = false
        _currentEmail.value = null
        _username.value = null
        _age.value = null
        _healthIssues.value = null
        _gender.value = null
        _heightCm.value = null
        _weightKg.value = null
        PreferencesManager.clearProfile()
    }

    // --- Profile ---

    fun fetchProfile() {
        if (!api.isAuthenticated) return
        viewModelScope.launch {
            try {
                val profile = api.fetchProfile()
                _username.value = profile.username
                _currentEmail.value = profile.email
                _age.value = profile.age?.toString()
                _healthIssues.value = profile.healthIssues
                _gender.value = profile.gender
                _heightCm.value = profile.heightCm?.toString()
                _weightKg.value = profile.weightKg?.toString()
                persistProfile()
            } catch (_: Exception) { }
        }
    }

    suspend fun updateProfile(
        username: String? = null,
        email: String? = null,
        age: Int? = null,
        healthIssues: String? = null,
        gender: String? = null,
        heightCm: Int? = null,
        weightKg: Int? = null
    ) {
        if (!api.isAuthenticated) return
        try {
            val updated = api.updateProfile(username, email, age, healthIssues, gender, heightCm, weightKg)
            _username.value = updated.username
            _currentEmail.value = updated.email
            _age.value = updated.age?.toString()
            _healthIssues.value = updated.healthIssues
            _gender.value = updated.gender
            _heightCm.value = updated.heightCm?.toString()
            _weightKg.value = updated.weightKg?.toString()
            persistProfile()
        } catch (e: ApiService.ApiException) {
            throw AuthError(e.message ?: "Update failed.")
        }
    }

    // --- Helpers ---

    private fun applyLoginResponse(response: AuthTokenResponse, fallbackEmail: String) {
        _currentEmail.value = response.email ?: fallbackEmail
        _username.value = response.username
        _age.value = response.age?.toString()
        _healthIssues.value = response.healthIssues
        _gender.value = response.gender
        _heightCm.value = response.heightCm?.toString()
        _weightKg.value = response.weightKg?.toString()
        _isSignedIn.value = true
        persistProfile()
    }

    private fun persistProfile() {
        PreferencesManager.email = _currentEmail.value
        PreferencesManager.username = _username.value
        PreferencesManager.age = _age.value
        PreferencesManager.healthIssues = _healthIssues.value
        PreferencesManager.gender = _gender.value
        PreferencesManager.heightCm = _heightCm.value
        PreferencesManager.weightKg = _weightKg.value
    }

    private fun validateEmail(email: String) {
        if (!email.contains("@") || !email.contains(".") || email.length < 5) {
            throw AuthError("Please enter a valid email address.")
        }
    }

    class AuthError(message: String) : Exception(message)
}
