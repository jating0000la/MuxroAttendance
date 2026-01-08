package com.muxrotechnologies.muxroattendance.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import com.muxrotechnologies.muxroattendance.R
import com.muxrotechnologies.muxroattendance.utils.ReportServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground service to run HTTP server for attendance reports
 */
class ReportServerService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reportServer: ReportServer? = null
    
    companion object {
        private const val CHANNEL_ID = "report_server_channel"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_START = "START_SERVER"
        const val ACTION_STOP = "STOP_SERVER"
        
        fun start(context: Context) {
            val intent = Intent(context, ReportServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, ReportServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_STICKY
    }
    
    private fun startServer() {
        serviceScope.launch {
            try {
                val ipAddress = getLocalIpAddress()
                val port = 8080
                
                reportServer = ReportServer(applicationContext, port)
                reportServer?.start()
                
                val notification = createNotification(ipAddress, port, true)
                startForeground(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                e.printStackTrace()
                val notification = createNotification("Error", 0, false)
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }
    
    private fun stopServer() {
        reportServer?.stop()
        reportServer = null
        stopForeground(true)
        stopSelf()
    }
    
    @SuppressLint("ServiceCast")
    private fun getLocalIpAddress(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ip = wifiInfo.ipAddress
            @Suppress("DEPRECATION")
            Formatter.formatIpAddress(ip)
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Report Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Serves attendance reports on local network"
                setShowBadge(false)
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(ip: String, port: Int, isRunning: Boolean): Notification {
        val message = if (isRunning) {
            "Access reports at: http://$ip:$port"
        } else {
            "Server stopped"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Attendance Report Server")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(isRunning)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }
}
