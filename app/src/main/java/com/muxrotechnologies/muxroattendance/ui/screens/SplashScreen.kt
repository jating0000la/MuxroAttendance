package com.muxrotechnologies.muxroattendance.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxrotechnologies.muxroattendance.AttendanceApplication
import com.muxrotechnologies.muxroattendance.utils.SecurityUtil
import com.muxrotechnologies.muxroattendance.utils.ModelDownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Enhanced Splash screen with animations
 */
@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToModelDownload: () -> Unit,
    isKioskMode: Boolean = false,
    onNavigateToAttendance: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = AttendanceApplication.getInstance()
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("Initializing...") }
    var progress by remember { mutableStateOf(0f) }
    
    // Logo scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    // Fade in animation
    val fadeIn by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000),
        label = "fadeIn"
    )
    
    // Progress animation
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress"
    )
    
    LaunchedEffect(Unit) {
        scope.launch {
            delay(800)
            
            // Step 1: Security check
            progress = 0.2f
            statusMessage = "Security check..."
            delay(600)
            
            val configRepo = app.configRepository
            if (configRepo.isRootDetectionEnabled() && SecurityUtil.isDeviceRooted()) {
                statusMessage = "⚠️ Device is rooted - Security risk!"
                delay(2500)
            }
            
            // Step 2: Device binding validation
            progress = 0.4f
            statusMessage = "Validating device..."
            delay(600)
            
            val storedDeviceId = configRepo.getDeviceId()
            val currentDeviceId = SecurityUtil.getDeviceId(context)
            
            if (!SecurityUtil.validateDeviceBinding(context, storedDeviceId)) {
                statusMessage = "❌ Device binding failed!"
                delay(2000)
            }
            
            // Step 3: Loading ML models
            progress = 0.6f
            statusMessage = "Loading ML models..."
            delay(700)
            
            // Step 4: Initializing database
            progress = 0.8f
            statusMessage = "Connecting to database..."
            delay(500)
            
            // Step 5: Check ML models
            progress = 0.9f
            statusMessage = "Checking ML models..."
            delay(400)
            
            if (!ModelDownloadManager.areModelsDownloaded(context)) {
                statusMessage = "ML models needed"
                delay(300)
                onNavigateToModelDownload()
                return@launch
            }
            
            // Step 6: Complete
            progress = 1.0f
            statusMessage = "Ready!"
            delay(400)
            
            // In kiosk mode, go directly to attendance after checks
            if (isKioskMode) {
                onNavigateToAttendance()
                return@launch
            }
            
            val isInitialized = configRepo.isAppInitialized()
            
            if (isInitialized) {
                onNavigateToHome()
            } else {
                onNavigateToLogin()
            }
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.alpha(fadeIn)
            ) {
                // Animated logo placeholder (you can replace with actual logo)
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(logoScale),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "MA",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Muxro Attendance",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Offline Face Recognition",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Progress indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(280.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = statusMessage,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
            }
            
            // Version info at bottom
            Text(
                text = "v1.0.0 • Powered by TensorFlow Lite & MediaPipe",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
}
