package com.example.phoneagent.auth

import kotlinx.coroutines.flow.StateFlow

sealed interface AppAuthStatus {
    data object Restoring : AppAuthStatus

    data class SignedOut(
        val sessionExpired: Boolean = false,
    ) : AppAuthStatus

    data object SigningIn : AppAuthStatus

    data class DeviceCode(
        val userCode: String,
        val verificationUrl: String,
    ) : AppAuthStatus

    data object SignedIn : AppAuthStatus

    data class Failure(
        val reason: AuthErrorReason,
    ) : AppAuthStatus
}

enum class AuthErrorReason {
    INVALID_API_KEY,
    LOOPBACK_PORT_UNAVAILABLE,
    TIMEOUT,
    NETWORK,
    GENERIC,
}

interface AuthCoordinator {
    val state: StateFlow<AppAuthStatus>

    suspend fun restore()

    suspend fun signInWithBrowser()

    suspend fun signInWithDeviceCode()

    suspend fun signInWithApiKey(key: String)

    suspend fun signOut()

    fun openDeviceVerificationPage()
}
