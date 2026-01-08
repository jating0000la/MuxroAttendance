package com.muxrotechnologies.muxroattendance.utils

import android.content.Context
import android.provider.Settings
import java.io.File

/**
 * Security utilities for device binding, root detection, and screenshot prevention
 */
object SecurityUtil {
    
    /**
     * Get unique device identifier (Android ID)
     */
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "UNKNOWN_DEVICE"
    }
    
    /**
     * Basic root detection
     * Checks for common root indicators
     */
    fun isDeviceRooted(): Boolean {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3()
    }
    
    private fun checkRootMethod1(): Boolean {
        val buildTags = android.os.Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }
    
    private fun checkRootMethod2(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }
    
    private fun checkRootMethod3(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val input = java.io.BufferedReader(
                java.io.InputStreamReader(process.inputStream)
            )
            input.readLine() != null
        } catch (t: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }
    
    /**
     * Validate device binding
     * Ensures the app is running on the correct device
     */
    fun validateDeviceBinding(context: Context, storedDeviceId: String?): Boolean {
        if (storedDeviceId == null) return true // First run
        val currentDeviceId = getDeviceId(context)
        return currentDeviceId == storedDeviceId
    }
}
