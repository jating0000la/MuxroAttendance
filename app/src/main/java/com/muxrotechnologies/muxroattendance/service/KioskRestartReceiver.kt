package com.muxrotechnologies.muxroattendance.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.muxrotechnologies.muxroattendance.MainActivity
import com.muxrotechnologies.muxroattendance.utils.KioskManager

/**
 * Broadcast receiver for auto-restart functionality
 * Handles boot completion and manual restart triggers
 */
class KioskRestartReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.muxrotechnologies.muxroattendance.RESTART_KIOSK" -> {
                // Only restart if kiosk mode is enabled
                if (KioskManager.isKioskModeEnabled(context)) {
                    // Start the service
                    KioskService.start(context)
                    
                    // Launch the main activity
                    val activityIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("kiosk_restart", true)
                    }
                    
                    try {
                        context.startActivity(activityIntent)
                    } catch (e: Exception) {
                        // If immediate restart fails, try after short delay
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                context.startActivity(activityIntent)
                            } catch (ex: Exception) {
                                // Log but don't crash
                            }
                        }, 2000)
                    }
                }
            }
        }
    }
}
