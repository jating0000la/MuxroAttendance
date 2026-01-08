package com.muxrotechnologies.muxroattendance.ui.navigation

/**
 * Navigation routes for the app
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object ModelDownload : Screen("model_download")
    object AdminLogin : Screen("admin_login")
    object Home : Screen("home")
    object UserEnrollment : Screen("user_enrollment")
    object AttendanceCamera : Screen("attendance_camera")
    object AttendanceHistory : Screen("attendance_history")
    object UserList : Screen("user_list")
    object Settings : Screen("settings")
    object Export : Screen("export")
}
