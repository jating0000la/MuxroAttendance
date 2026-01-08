package com.muxrotechnologies.muxroattendance.ui.components

import android.graphics.RectF
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxrotechnologies.muxroattendance.ui.theme.*

/**
 * Face detection overlay - shows bounding box around detected face
 */
@Composable
fun FaceDetectionOverlay(
    faceRect: RectF?,
    isFaceDetected: Boolean,
    confidence: Float = 0f,
    modifier: Modifier = Modifier
) {
    val animatedAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val color = when {
        !isFaceDetected -> FaceDetectionRed
        confidence > 0.8f -> FaceDetectionGreen
        else -> FaceDetectionYellow
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        faceRect?.let { rect ->
            val strokeWidth = 4.dp.toPx()
            val cornerLength = 40.dp.toPx()
            
            // Draw corner brackets
            val path = Path().apply {
                // Top-left corner
                moveTo(rect.left, rect.top + cornerLength)
                lineTo(rect.left, rect.top)
                lineTo(rect.left + cornerLength, rect.top)
                
                // Top-right corner
                moveTo(rect.right - cornerLength, rect.top)
                lineTo(rect.right, rect.top)
                lineTo(rect.right, rect.top + cornerLength)
                
                // Bottom-right corner
                moveTo(rect.right, rect.bottom - cornerLength)
                lineTo(rect.right, rect.bottom)
                lineTo(rect.right - cornerLength, rect.bottom)
                
                // Bottom-left corner
                moveTo(rect.left + cornerLength, rect.bottom)
                lineTo(rect.left, rect.bottom)
                lineTo(rect.left, rect.bottom - cornerLength)
            }
            
            drawPath(
                path = path,
                color = color.copy(alpha = animatedAlpha),
                style = Stroke(width = strokeWidth)
            )
            
            // Draw semi-transparent fill
            drawRoundRect(
                color = color.copy(alpha = 0.1f),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width(), rect.height()),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
        }
    }
}

/**
 * Guide oval - shows where user should position their face
 */
@Composable
fun FaceGuideOval(
    modifier: Modifier = Modifier,
    isAligned: Boolean = false
) {
    val animatedAlpha by rememberInfiniteTransition(label = "guide").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val color = if (isAligned) FaceDetectionGreen else Color.White
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val ovalWidth = size.width * 0.65f
        val ovalHeight = size.height * 0.45f
        val ovalLeft = (size.width - ovalWidth) / 2
        val ovalTop = (size.height - ovalHeight) / 2
        
        // Draw dashed oval
        drawOval(
            color = color.copy(alpha = animatedAlpha),
            topLeft = Offset(ovalLeft, ovalTop),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(
                width = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
            )
        )
    }
}

/**
 * Confidence meter - shows recognition confidence
 */
@Composable
fun ConfidenceMeter(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val animatedConfidence by animateFloatAsState(
        targetValue = confidence,
        animationSpec = tween(300),
        label = "confidence"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confidence",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "${(animatedConfidence * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { animatedConfidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    animatedConfidence > 0.85f -> AttendanceGreen
                    animatedConfidence > 0.70f -> AttendanceOrange
                    else -> AttendanceRed
                },
            )
        }
    }
}

/**
 * Liveness indicators - shows detected liveness actions
 */
@Composable
fun LivenessIndicators(
    blinkDetected: Boolean,
    smileDetected: Boolean,
    headTurnDetected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LivenessIndicator(
            icon = Icons.Default.RemoveRedEye,
            label = "Blink",
            isDetected = blinkDetected
        )
        
        LivenessIndicator(
            icon = Icons.Default.SentimentSatisfied,
            label = "Smile",
            isDetected = smileDetected
        )
        
        LivenessIndicator(
            icon = Icons.Default.ScreenRotation,
            label = "Turn",
            isDetected = headTurnDetected
        )
    }
}

@Composable
private fun LivenessIndicator(
    icon: ImageVector,
    label: String,
    isDetected: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDetected) 
                AttendanceGreen.copy(alpha = 0.9f) 
            else 
                Color.Black.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Sample capture counter - shows progress during enrollment
 */
@Composable
fun SampleCaptureCounter(
    currentSample: Int,
    totalSamples: Int,
    quality: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sample Progress",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "$currentSample / $totalSamples",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { currentSample.toFloat() / totalSamples },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = AttendanceBlue,
            )
            
            if (quality > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Quality",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = when {
                            quality > 0.8f -> "Excellent"
                            quality > 0.6f -> "Good"
                            else -> "Poor"
                        },
                        color = when {
                            quality > 0.8f -> AttendanceGreen
                            quality > 0.6f -> AttendanceOrange
                            else -> AttendanceRed
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Instruction overlay - shows guidance text
 */
@Composable
fun InstructionOverlay(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

/**
 * Success animation overlay
 */
@Composable
fun SuccessOverlay(
    userName: String,
    time: String,
    confidence: Float,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2500)
        onDismiss()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f),
            colors = CardDefaults.cardColors(
                containerColor = AttendanceGreen
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Attendance Marked!",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = userName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = time,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Confidence: ${(confidence * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Failure animation overlay
 */
@Composable
fun FailureOverlay(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f),
            colors = CardDefaults.cardColors(
                containerColor = AttendanceRed
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = "Failed",
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Recognition Failed",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 16.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = AttendanceRed
                        )
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
