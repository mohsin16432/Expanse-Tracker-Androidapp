package com.trae.expensetracker.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Gates app entry behind the device's own lock screen (PIN, pattern, password or biometric).
 *
 * Uses the platform KeyguardManager rather than androidx.biometric so the app needs no extra
 * dependency, and it inherits whatever the user has already enrolled on the device.
 */
class AppLockManager(
    private val appContext: Context,
) {
    private val keyguard: KeyguardManager? =
        appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    /**
     * True when the device can actually challenge the user. If no screen lock is configured
     * there is nothing to delegate to, so the app must not lock the user out.
     */
    fun isDeviceSecure(): Boolean = keyguard?.isDeviceSecure == true

    fun createLauncher(
        activity: ComponentActivity,
        onResult: (Boolean) -> Unit,
    ): ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onResult(result.resultCode == Activity.RESULT_OK)
        }

    /** The system lock challenge, or null when no screen lock is enrolled. */
    fun buildChallengeIntent(): Intent? {
        val manager = keyguard ?: return null
        if (!manager.isDeviceSecure) return null
        @Suppress("DEPRECATION")
        return manager.createConfirmDeviceCredentialIntent(
            "Unlock Expense Tracker",
            "Confirm your screen lock to open your financial data.",
        )
    }
}
