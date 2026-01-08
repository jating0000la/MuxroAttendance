package com.muxrotechnologies.muxroattendance.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.abs

/**
 * MediaPipe Face Mesh for liveness detection
 * Detects blink, head turn, smile - fully offline
 */
class LivenessDetector(private val context: Context) {
    
    private var faceLandmarker: FaceLandmarker? = null
    
    // Landmarks indices for liveness detection
    private val LEFT_EYE_TOP = 159
    private val LEFT_EYE_BOTTOM = 145
    private val RIGHT_EYE_TOP = 386
    private val RIGHT_EYE_BOTTOM = 374
    
    private val MOUTH_LEFT = 61
    private val MOUTH_RIGHT = 291
    private val MOUTH_TOP = 13
    private val MOUTH_BOTTOM = 14
    
    private val NOSE_TIP = 1
    private val LEFT_CHEEK = 234
    private val RIGHT_CHEEK = 454
    
    companion object {
        private const val TAG = "LivenessDetector"
        private const val MODEL_NAME = "face_landmarker.task"
        private const val BLINK_THRESHOLD = 0.15f
        private const val HEAD_TURN_THRESHOLD = 0.15f
        private const val SMILE_THRESHOLD = 0.3f
    }
    
    /**
     * Initialize Face Landmarker
     */
    fun initialize(): Boolean {
        return try {
            // Get model path from app's files directory (where downloaded models are stored)
            val modelFile = java.io.File(context.filesDir, "ml_models/$MODEL_NAME")
            
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelFile.absolutePath)
                .build()
            
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .build()
            
            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d(TAG, "Face landmarker initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing face landmarker", e)
            false
        }
    }
    
    /**
     * Detect liveness features
     */
    fun detectLiveness(bitmap: Bitmap): LivenessResult? {
        if (faceLandmarker == null) {
            Log.e(TAG, "Face landmarker not initialized")
            return null
        }
        
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = faceLandmarker?.detect(mpImage)
            
            result?.faceLandmarks()?.firstOrNull()?.let { landmarks ->
                LivenessResult(
                    eyeAspectRatio = calculateEyeAspectRatio(landmarks),
                    headPose = calculateHeadPose(landmarks),
                    smileScore = calculateSmileScore(landmarks),
                    isBlink = isBlinking(landmarks),
                    isHeadTurned = isHeadTurned(landmarks),
                    isSmiling = isSmiling(landmarks)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting liveness", e)
            null
        }
    }
    
    /**
     * Calculate Eye Aspect Ratio (EAR) for blink detection
     */
    private fun calculateEyeAspectRatio(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float {
        val leftEyeHeight = abs(
            landmarks[LEFT_EYE_TOP].y() - landmarks[LEFT_EYE_BOTTOM].y()
        )
        val rightEyeHeight = abs(
            landmarks[RIGHT_EYE_TOP].y() - landmarks[RIGHT_EYE_BOTTOM].y()
        )
        
        return (leftEyeHeight + rightEyeHeight) / 2f
    }
    
    /**
     * Check if eyes are blinking
     */
    private fun isBlinking(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        val ear = calculateEyeAspectRatio(landmarks)
        return ear < BLINK_THRESHOLD
    }
    
    /**
     * Calculate head pose (yaw angle)
     */
    private fun calculateHeadPose(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float {
        val noseTip = landmarks[NOSE_TIP].x()
        val leftCheek = landmarks[LEFT_CHEEK].x()
        val rightCheek = landmarks[RIGHT_CHEEK].x()
        
        val faceCenter = (leftCheek + rightCheek) / 2f
        val deviation = noseTip - faceCenter
        
        return deviation
    }
    
    /**
     * Check if head is turned
     */
    private fun isHeadTurned(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        val headPose = abs(calculateHeadPose(landmarks))
        return headPose > HEAD_TURN_THRESHOLD
    }
    
    /**
     * Calculate smile score
     */
    private fun calculateSmileScore(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float {
        val mouthWidth = abs(
            landmarks[MOUTH_LEFT].x() - landmarks[MOUTH_RIGHT].x()
        )
        val mouthHeight = abs(
            landmarks[MOUTH_TOP].y() - landmarks[MOUTH_BOTTOM].y()
        )
        
        return if (mouthHeight > 0) mouthWidth / mouthHeight else 0f
    }
    
    /**
     * Check if smiling
     */
    private fun isSmiling(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        val smileScore = calculateSmileScore(landmarks)
        return smileScore > SMILE_THRESHOLD
    }
    
    /**
     * Close landmarker
     */
    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
        Log.d(TAG, "Face landmarker closed")
    }
}

/**
 * Liveness detection result
 */
data class LivenessResult(
    val eyeAspectRatio: Float,
    val headPose: Float,
    val smileScore: Float,
    val isBlink: Boolean,
    val isHeadTurned: Boolean,
    val isSmiling: Boolean
)

/**
 * Liveness challenge types
 */
enum class LivenessChallenge {
    BLINK,
    TURN_HEAD_LEFT,
    TURN_HEAD_RIGHT,
    SMILE
}
