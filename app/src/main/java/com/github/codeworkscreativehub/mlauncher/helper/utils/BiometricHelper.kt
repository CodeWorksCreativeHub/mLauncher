package com.github.codeworkscreativehub.mlauncher.helper.utils

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.AppListItem

class BiometricHelper(private val activity: FragmentActivity) {
    private lateinit var callbackApp: CallbackApp
    private lateinit var callbackSettings: CallbackSettings

    interface CallbackApp {
        fun onAuthenticationSucceeded(appListItem: AppListItem)
        fun onAuthenticationFailed()
        fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?)
    }

    interface CallbackSettings {
        fun onAuthenticationSucceeded()
        fun onAuthenticationFailed()
        fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?)
    }

    fun startBiometricAuth(appListItem: AppListItem, callbackApp: CallbackApp) {
        this.callbackApp = callbackApp

        val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                callbackApp.onAuthenticationSucceeded(appListItem)
            }

            override fun onAuthenticationFailed() {
                callbackApp.onAuthenticationFailed()
            }

            override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence) {
                callbackApp.onAuthenticationError(errorCode, errorMessage)
            }
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor, authenticationCallback)

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuthenticate =
            BiometricManager.from(activity).canAuthenticate(authenticators)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getLocalizedString(R.string.text_biometric_login))
            .setSubtitle(getLocalizedString(R.string.text_biometric_login_app, appListItem.activityLabel))
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    fun startBiometricSettingsAuth(callbackApp: CallbackSettings) {
        this.callbackSettings = callbackApp

        val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                callbackSettings.onAuthenticationSucceeded()
            }

            override fun onAuthenticationFailed() {
                callbackSettings.onAuthenticationFailed()
            }

            override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence) {
                callbackSettings.onAuthenticationError(errorCode, errorMessage)
            }
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor, authenticationCallback)

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuthenticate =
            BiometricManager.from(activity).canAuthenticate(authenticators)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getLocalizedString(R.string.text_biometric_login))
            .setSubtitle(getLocalizedString(R.string.text_biometric_login_sub))
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPrompt.authenticate(promptInfo)
        }
    }
}