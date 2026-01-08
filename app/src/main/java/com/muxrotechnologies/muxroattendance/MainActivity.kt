package com.muxrotechnologies.muxroattendance

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.muxrotechnologies.muxroattendance.service.KioskService
import com.muxrotechnologies.muxroattendance.ui.components.KioskPasswordDialog
import com.muxrotechnologies.muxroattendance.ui.navigation.AppNavGraph
import com.muxrotechnologies.muxroattendance.ui.navigation.Screen
import com.muxrotechnologies.muxroattendance.ui.theme.MuxroAttendanceTheme
import com.muxrotechnologies.muxroattendance.utils.KioskManager

/**
 * Main activity - Entry point with permission handling
 */
class MainActivity : ComponentActivity() {
    
    private val requiredPermissions = buildList {
        add(Manifest.permission.CAMERA)
        
        // Storage permissions based on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        
        if (!allGranted) {
            val deniedPermissions = permissions.filter { !it.value }.keys
            
            Toast.makeText(
                this,
                "Required permissions denied: ${deniedPermissions.joinToString()}",
                Toast.LENGTH_LONG
            ).show()
            
            // Camera permission is critical - cannot proceed without it
            if (Manifest.permission.CAMERA in deniedPermissions) {
                Toast.makeText(
                    this,
                    "Camera permission is required for face recognition",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize kiosk manager with default password
        KioskManager.initializeDefaultPassword(this)
        
        // Check if kiosk mode is enabled
        val isKioskMode = KioskManager.isKioskModeEnabled(this)
        
        // Security: Disable screenshots and screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        // Keep screen on in kiosk mode
        if (isKioskMode) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // Start kiosk service
            KioskService.start(this)
            
            // Request battery optimization exemption for better reliability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val packageName = packageName
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    try {
                        val intent = Intent().apply {
                            action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            data = android.net.Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        // Permission not granted, continue without it
                    }
                }
            }
            
            // Enable lock task mode (app pinning) if not already enabled
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                        // User needs to manually pin the app for full kiosk mode
                        // This will work if the app is device owner or in lock task whitelist
                        startLockTask()
                    }
                } catch (e: Exception) {
                    // Lock task mode not available or not permitted
                    // App will still function in kiosk mode without it
                }
            }
        }
        
        // Request permissions before proceeding
        checkAndRequestPermissions()
        
        enableEdgeToEdge()
        
        setContent {
            MuxroAttendanceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    // Check if camera permission is granted
                    val hasCameraPermission = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    
                    // Determine start destination based on kiosk mode and permissions
                    val startDestination = if (isKioskMode && hasCameraPermission) {
                        Screen.AttendanceCamera.route
                    } else if (isKioskMode && !hasCameraPermission) {
                        // If in kiosk mode but no camera permission, go to splash to request it
                        Screen.Splash.route
                    } else {
                        Screen.Splash.route
                    }
                    
                    AppNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        isKioskMode = isKioskMode
                    )
                }
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Re-check critical permissions on resume
        val cameraGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!cameraGranted) {
            Toast.makeText(
                this,
                "Camera permission is required. Please grant it in settings.",
                Toast.LENGTH_LONG
            ).show()
        }
        
        // Restart kiosk service if enabled
        if (KioskManager.isKioskModeEnabled(this)) {
            KioskService.start(this)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Don't stop kiosk service on destroy if in kiosk mode
        // Service will handle restart
    }
    
    override fun onBackPressed() {
        // In kiosk mode, back button is handled by the compose navigation
        // which will show password dialog
        if (!KioskManager.isKioskModeEnabled(this)) {
            super.onBackPressed()
        }
    }
}