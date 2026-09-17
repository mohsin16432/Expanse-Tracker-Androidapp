package com.trae.expensetracker

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.trae.expensetracker.ui.AppNav
import com.trae.expensetracker.ui.theme.Bg
import com.trae.expensetracker.ui.theme.ExpenseTrackerTheme
import com.trae.expensetracker.ui.theme.Primary
import com.trae.expensetracker.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as ExpenseTrackerApp).container
        val appLock = container.appLockManager

        // Set up the system-lock challenge launcher before the UI is composed.
        var onUnlocked: (Boolean) -> Unit = {}
        val lockLauncher = appLock.createLauncher(this) { granted -> onUnlocked(granted) }

        setContent {
            ExpenseTrackerTheme {
                Surface(color = Bg) {
                    val context = LocalContext.current
                    val lockEnabled by container.settingsRepository.appLockEnabled()
                        .collectAsState(initial = false)
                    val canLock = remember { appLock.isDeviceSecure() }

                    // Starts unlocked when the feature is off or the device has no screen lock,
                    // so the user can never be locked out of their own data.
                    var unlocked by remember(lockEnabled, canLock) {
                        mutableStateOf(!lockEnabled || !canLock)
                    }

                    fun challenge() {
                        val intent = appLock.buildChallengeIntent()
                        if (intent == null) {
                            unlocked = true
                        } else {
                            onUnlocked = { granted -> unlocked = granted }
                            lockLauncher.launch(intent)
                        }
                    }

                    LaunchedEffect(lockEnabled, canLock) {
                        if (lockEnabled && canLock && !unlocked) challenge()
                    }

                    val requestPermission = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions(),
                        onResult = { }
                    )

                    LaunchedEffect(Unit) {
                        // Request SMS permissions for personal use.
                        val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PermissionChecker.PERMISSION_GRANTED
                        val receiveGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PermissionChecker.PERMISSION_GRANTED
                        if (!readGranted || !receiveGranted) {
                            requestPermission.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                        }
                    }

                    if (unlocked) {
                        AppNav()
                    } else {
                        LockedScreen(onUnlock = { challenge() })
                    }
                }
            }
        }
    }
}

/**
 * Shown instead of the app while the lock challenge is outstanding. Deliberately renders no
 * financial data so nothing is visible behind the system prompt.
 */
@Composable
private fun LockedScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Expense Tracker", style = MaterialTheme.typography.headlineMedium, color = Primary, fontWeight = FontWeight.Bold)
        Text("Locked", color = TextSecondary)
        Button(onClick = onUnlock) { Text("Unlock") }
    }
}
