package com.vesc0.heartratemonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import com.vesc0.heartratemonitor.data.local.PreferencesManager
import com.vesc0.heartratemonitor.ui.navigation.AppNavigation
import com.vesc0.heartratemonitor.ui.screens.WelcomeScreen
import com.vesc0.heartratemonitor.ui.theme.HeartRateMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var appTheme by remember { mutableStateOf(PreferencesManager.appTheme) }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appTheme) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }

            HeartRateMonitorTheme(darkTheme = darkTheme, dynamicColor = false) {
                var showWelcome by remember { mutableStateOf(!PreferencesManager.hasSeenWelcome) }
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasCameraPermission = granted
                }

                LaunchedEffect(showWelcome) {
                    if (
                        showWelcome &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                if (showWelcome) {
                    WelcomeScreen(
                        hasCameraPermission = hasCameraPermission,
                        onRequestCameraPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onContinue = {
                            PreferencesManager.hasSeenWelcome = true
                            showWelcome = false
                        }
                    )
                } else {
                    AppNavigation(
                        appTheme = appTheme,
                        onAppThemeChange = { newTheme ->
                            appTheme = newTheme
                            PreferencesManager.appTheme = newTheme
                        }
                    )
                }
            }
        }
    }
}