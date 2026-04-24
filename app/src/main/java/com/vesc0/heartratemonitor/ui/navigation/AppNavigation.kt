package com.vesc0.heartratemonitor.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vesc0.heartratemonitor.ui.screens.HistoryScreen
import com.vesc0.heartratemonitor.ui.screens.MeasurementScreen
import com.vesc0.heartratemonitor.ui.screens.ProfileScreen
import com.vesc0.heartratemonitor.ui.screens.SettingsScreen
import com.vesc0.heartratemonitor.viewmodel.AuthViewModel
import com.vesc0.heartratemonitor.viewmodel.HeartRateViewModel

private enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    MEASURE("measure", "Measure", Icons.Filled.Favorite),
    HISTORY("history", "Stats", Icons.AutoMirrored.Filled.List),
    PROFILE("profile", "Profile", Icons.Filled.AccountCircle),
    SETTINGS("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun AppNavigation(
    appTheme: String,
    onAppThemeChange: (String) -> Unit
) {
    val navController = rememberNavController()
    val vm: HeartRateViewModel = viewModel()
    val auth: AuthViewModel = viewModel()
    val isSignedIn by auth.isSignedIn.collectAsState()

    // Sync data when auth state changes
    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            vm.refreshFromServer()
            auth.fetchProfile()
        } else {
            vm.clearForLogout()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Red.copy(alpha = 0.18f),
                            selectedIconColor = Color.Red,
                            selectedTextColor = Color.Red
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.MEASURE.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.MEASURE.route) { MeasurementScreen(vm = vm, auth = auth) }
            composable(Screen.HISTORY.route) { HistoryScreen(vm = vm) }
            composable(Screen.PROFILE.route) { ProfileScreen(auth = auth) }
            composable(Screen.SETTINGS.route) {
                SettingsScreen(
                    appTheme = appTheme,
                    onThemeChange = onAppThemeChange
                )
            }
        }
    }
}
