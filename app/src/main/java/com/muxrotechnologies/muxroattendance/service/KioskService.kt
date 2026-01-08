package com.muxrotechnologies.muxroattendance.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.muxrotechnologies.muxroattendance.MainActivity
import com.muxrotechnologies.muxroattendance.R

/**
 * Foreground service that keeps the app running in kiosk mode
 * Ensures app stays active and can restart if closed
 */
class KioskService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "kiosk_service_channel"
        private const val NOTIFICATION_ID = 1001
        
        fun start(context: Context) {
            val intent = Intent(context, KioskService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, KioskService::class.java)
            context.stopService(intent)
        }
    }
    
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Acquire wake lock to prevent sleep
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MuxroAttendance::KioskWakeLock"
        )
        wakeLock?.acquire()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service will restart if killed
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        // Schedule restart using AlarmManager
        scheduleRestart()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        
        // Delay restart slightly to ensure proper cleanup
        Handler(Looper.getMainLooper()).postDelayed({
            // Restart the app
            val restartIntent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("kiosk_restart", true)
            }
            
            try {
                applicationContext.startActivity(restartIntent)
            } catch (e: Exception) {
                // If direct restart fails, use broadcast receiver
                sendRestartBroadcast()
            }
            
            // Restart the service
            start(applicationContext)
        }, 500)
    }
    
    private fun sendRestartBroadcast() {
        val broadcastIntent = Intent("com.muxrotechnologies.muxroattendance.RESTART_KIOSK")
        broadcastIntent.setPackage(applicationContext.packageName)
        applicationContext.sendBroadcast(broadcastIntent)
    }
    
    private fun scheduleRestart() {
        val restartIntent = Intent(applicationContext, KioskRestartReceiver::class.java)
        restartIntent.action = "com.muxrotechnologies.muxroattendance.RESTART_KIOSK"
        
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Schedule restart in 1 second
        val restartTime = System.currentTimeMillis() + 1000
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                restartTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                restartTime,
                pendingIntent
            )
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kiosk Mode Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the attendance app running in kiosk mode"
                setShowBadge(false)
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Attendance Kiosk Active")
            .setContentText("Running in kiosk mode")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
