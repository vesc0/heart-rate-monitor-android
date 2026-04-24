package com.vesc0.heartratemonitor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class AppThemeOption(val key: String, val title: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromKey(value: String): AppThemeOption {
            return entries.firstOrNull { it.key == value } ?: SYSTEM
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appTheme: String,
    onThemeChange: (String) -> Unit
) {
    val selected = AppThemeOption.fromKey(appTheme)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Appearance",
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppThemeOption.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = selected == option,
                        onClick = { onThemeChange(option.key) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = AppThemeOption.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Color.Red,
                            activeContentColor = Color.White
                        ),
                        icon = {}
                    ) {
                        Text(option.title)
                    }
                }
            }
        }
    }
}
