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

    private val _isSignedIn = MutableStateFlow(api.isAuthenticated)
    val isSignedIn = _isSignedIn.asStateFlow()

    private val _currentEmail = MutableStateFlow(if (api.isAuthenticated) PreferencesManager.email else null)
    val currentEmail = _currentEmail.asStateFlow()

    private val _name = MutableStateFlow(if (api.isAuthenticated) PreferencesManager.name else null)
    val name = _name.asStateFlow()

    private val _age = MutableStateFlow(if (api.isAuthenticated) PreferencesManager.age else null)
    val age = _age.asStateFlow()

    private val _gender = MutableStateFlow(if (api.isAuthenticated) PreferencesManager.gender else null)
    val gender = _gender.asStateFlow()

    private val _heightCm = MutableStateFlow(if (api.isAuthenticated) PreferencesManager.heightCm else null)
    val heightCm = _heightCm.asStateFlow()

    private val _weightKg = MutableStateFlow(if (api.isAuthenticated) PreferencesManager.weightKg else null)
    val weightKg = _weightKg.asStateFlow()

    private val _healthIssues = MutableStateFlow(if (api.isAuthenticated) PreferencesManager.healthIssues else null)
    val healthIssues = _healthIssues.asStateFlow()

    init {
        ApiService.onAuthTokenExpired = {
            handleTokenExpired()
        }
    }

    suspend fun signUp(email: String, password: String) {
        validateEmail(email)
        validatePassword(password)

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
        if (password.isBlank()) throw AuthError("Password cannot be empty.")

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
        _name.value = null
        _age.value = null
        _gender.value = null
        _heightCm.value = null
        _weightKg.value = null
        _healthIssues.value = null
        clearPersistedProfile()
    }

    fun fetchProfile() {
        if (!api.isAuthenticated) return
        viewModelScope.launch {
            try {
                val profile = api.fetchProfile()
                _name.value = profile.resolvedName
                _currentEmail.value = profile.email
                _age.value = profile.age?.toString()
                _gender.value = profile.gender
                _heightCm.value = profile.heightCm?.toString()
                _weightKg.value = profile.weightKg?.toString()
                _healthIssues.value = profile.healthIssues
                persistProfile()
            } catch (_: Exception) {
                // Keep current local profile if the request fails.
            }
        }
    }

    suspend fun updateProfile(
        name: String? = null,
        email: String? = null,
        age: Int? = null,
        gender: String? = null,
        heightCm: Int? = null,
        weightKg: Int? = null,
        healthIssues: String? = null
    ) {
        if (!api.isAuthenticated) return

        try {
            val updated = api.updateProfile(
                name = name,
                email = email,
                age = age,
                gender = gender,
                heightCm = heightCm,
                weightKg = weightKg,
                healthIssues = healthIssues
            )
            _name.value = updated.resolvedName
            _currentEmail.value = updated.email
            _age.value = updated.age?.toString()
            _gender.value = updated.gender
            _heightCm.value = updated.heightCm?.toString()
            _weightKg.value = updated.weightKg?.toString()
            _healthIssues.value = updated.healthIssues
            persistProfile()
        } catch (e: ApiService.ApiException) {
            throw AuthError(e.message ?: "Update failed.")
        }
    }

    private fun applyLoginResponse(response: AuthTokenResponse, fallbackEmail: String) {
        _currentEmail.value = response.email ?: fallbackEmail
        _name.value = response.resolvedName
        _age.value = response.age?.toString()
        _gender.value = response.gender
        _heightCm.value = response.heightCm?.toString()
        _weightKg.value = response.weightKg?.toString()
        _healthIssues.value = response.healthIssues
        _isSignedIn.value = true
        persistProfile()
    }

    private fun persistProfile() {
        PreferencesManager.email = _currentEmail.value
        PreferencesManager.name = _name.value
        PreferencesManager.age = _age.value
        PreferencesManager.gender = _gender.value
        PreferencesManager.heightCm = _heightCm.value
        PreferencesManager.weightKg = _weightKg.value
        PreferencesManager.healthIssues = _healthIssues.value
    }

    private fun clearPersistedProfile() {
        PreferencesManager.clearProfile()
    }

    private fun handleTokenExpired() {
        if (_isSignedIn.value) {
            signOut()
        }
    }

    private fun validateEmail(email: String) {
        if (!email.contains("@") || !email.contains(".") || email.length < 5) {
            throw AuthError("Please enter a valid email address.")
        }
    }

    private fun validatePassword(password: String) {
        if (password.length < 8) {
            throw AuthError("Password must be at least 8 characters and include uppercase, lowercase, and a number.")
        }
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        if (!(hasUpper && hasLower && hasDigit)) {
            throw AuthError("Password must be at least 8 characters and include uppercase, lowercase, and a number.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        ApiService.onAuthTokenExpired = null
    }

    class AuthError(message: String) : Exception(message)
}
