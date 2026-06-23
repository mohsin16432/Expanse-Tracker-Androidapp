package com.trae.expensetracker

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.trae.expensetracker.ui.AppNav
import com.trae.expensetracker.ui.theme.Bg
import com.trae.expensetracker.ui.theme.ExpenseTrackerTheme
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ExpenseTrackerTheme {
                Surface(color = Bg) {
                    val context = LocalContext.current
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

                    AppNav()
                }
            }
        }
    }
}
