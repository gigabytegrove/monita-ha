package com.gigabytegrove.monita.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal object DeviceAuthMode {
    const val BIOMETRIC = "biometric"
    const val CREDENTIAL = "credential"
    const val EITHER = "either"

    fun authenticators(mode: String): Int =
        when (mode) {
            BIOMETRIC -> BiometricManager.Authenticators.BIOMETRIC_WEAK
            CREDENTIAL -> BiometricManager.Authenticators.DEVICE_CREDENTIAL
            EITHER ->
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            else -> error("Unknown device authentication mode: $mode")
        }
}

internal class DeviceAuthenticationException(message: String) : Exception(message)

internal object DeviceAuthenticator {
    fun canAuthenticate(
        activity: FragmentActivity,
        mode: String
    ): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(DeviceAuthMode.authenticators(mode)) ==
            BiometricManager.BIOMETRIC_SUCCESS

    suspend fun authenticate(
        activity: FragmentActivity,
        mode: String,
        title: String,
        subtitle: String
    ): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val authenticators = DeviceAuthMode.authenticators(mode)
                val status = BiometricManager.from(activity).canAuthenticate(authenticators)
                if (status != BiometricManager.BIOMETRIC_SUCCESS) {
                    continuation.resumeWithException(
                        DeviceAuthenticationException(
                            "The selected authentication method is not available on this device."
                        )
                    )
                    return@suspendCancellableCoroutine
                }

                val executor = ContextCompat.getMainExecutor(activity)
                lateinit var prompt: BiometricPrompt
                prompt =
                    BiometricPrompt(
                        activity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(
                                result: BiometricPrompt.AuthenticationResult
                            ) {
                                if (continuation.isActive) continuation.resume(true)
                            }

                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence
                            ) {
                                if (!continuation.isActive) return
                                if (
                                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                    errorCode == BiometricPrompt.ERROR_CANCELED
                                ) {
                                    continuation.resume(false)
                                } else {
                                    continuation.resumeWithException(
                                        DeviceAuthenticationException(errString.toString())
                                    )
                                }
                            }

                            override fun onAuthenticationFailed() {
                                // Android keeps the prompt open so the user can retry.
                            }
                        }
                    )

                val builder =
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setAllowedAuthenticators(authenticators)

                if (mode == DeviceAuthMode.BIOMETRIC) {
                    builder.setNegativeButtonText("Cancel")
                }

                prompt.authenticate(builder.build())
                continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            }
        }
}

internal object AppUnlockSession {
    @Volatile
    private var unlocked = false

    fun isUnlocked(): Boolean = unlocked

    fun unlock() {
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }
}
