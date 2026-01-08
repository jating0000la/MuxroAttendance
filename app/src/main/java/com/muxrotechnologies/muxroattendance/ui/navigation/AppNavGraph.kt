package com.muxrotechnologies.muxroattendance.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.muxrotechnologies.muxroattendance.ui.screens.*

/**
 * Main navigation graph
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    isKioskMode: Boolean = false
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.AdminLogin.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToModelDownload = {
                    navController.navigate(Screen.ModelDownload.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                isKioskMode = isKioskMode,
                onNavigateToAttendance = {
                    navController.navigate(Screen.AttendanceCamera.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.ModelDownload.route) {
            ModelDownloadScreen(
                onDownloadComplete = {
                    navController.navigate(Screen.Splash.route) {
                        popUpTo(Screen.ModelDownload.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.AdminLogin.route) {
            AdminLoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.AdminLogin.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToEnrollment = {
                    navController.navigate(Screen.UserEnrollment.route)
                },
                onNavigateToAttendance = {
                    navController.navigate(Screen.AttendanceCamera.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.AttendanceHistory.route)
                },
                onNavigateToUsers = {
                    navController.navigate(Screen.UserList.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(Screen.UserEnrollment.route) {
            UserEnrollmentScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.AttendanceCamera.route) {
            AttendanceCameraScreen(
                onNavigateBack = { 
                    if (!isKioskMode) {
                        navController.popBackStack()
                    }
                },
                isKioskMode = isKioskMode,
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.AttendanceCamera.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.AttendanceHistory.route) {
            AttendanceHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExport = {
                    navController.navigate(Screen.Export.route)
                }
            )
        }
        
        composable(Screen.UserList.route) {
            UserListScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Export.route) {
            ExportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
