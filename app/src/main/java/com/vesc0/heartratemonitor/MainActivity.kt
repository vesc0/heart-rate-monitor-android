package com.vesc0.heartratemonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vesc0.heartratemonitor.ui.navigation.AppNavigation
import com.vesc0.heartratemonitor.ui.theme.HeartRateMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HeartRateMonitorTheme(dynamicColor = false) {
                AppNavigation()
            }
        }
    }
}