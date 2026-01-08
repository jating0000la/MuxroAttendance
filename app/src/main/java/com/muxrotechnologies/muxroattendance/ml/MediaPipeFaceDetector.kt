package com.muxrotechnologies.muxroattendance.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult

/**
 * MediaPipe Face Detector wrapper
 * Detects faces in camera frames - fully offline
 */
class MediaPipeFaceDetector(private val context: Context) {
    
    private var faceDetector: FaceDetector? = null
    
    companion object {
        private const val TAG = "MediaPipeFaceDetector"
        private const val MODEL_NAME = "face_detection.tflite"
        private const val MIN_DETECTION_CONFIDENCE = 0.5f // Production-ready confidence
    }
    
    /**
     * Initialize MediaPipe Face Detector
     */
    fun initialize(): Boolean {
        return try {
            // Get model path from app's files directory (where downloaded models are stored)
            val modelFile = java.io.File(context.filesDir, "ml_models/$MODEL_NAME")
            
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelFile.absolutePath)
                .build()
            
            val options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setRunningMode(RunningMode.IMAGE)
                .build()
            
            faceDetector = FaceDetector.createFromOptions(context, options)
            Log.d(TAG, "Face detector initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing face detector", e)
            false
        }
    }
    
    /**
     * Detect faces in bitmap
     * @return DetectedFace with bounding box or null
     */
    fun detectFace(bitmap: Bitmap): DetectedFace? {
        if (faceDetector == null) {
            Log.e(TAG, "Face detector not initialized")
            return null
        }
        
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = faceDetector?.detect(mpImage)
            
            result?.detections()?.firstOrNull()?.let { detection ->
                val boundingBox = detection.boundingBox()
                
                DetectedFace(
                    left = boundingBox.left.toInt(),
                    top = boundingBox.top.toInt(),
                    right = boundingBox.right.toInt(),
                    bottom = boundingBox.bottom.toInt(),
                    confidence = detection.categories().firstOrNull()?.score() ?: 0f,
                    keypoints = detection.keypoints().orElse(null)?.map { Pair(it.x(), it.y()) }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting face", e)
            null
        }
    }
    
    /**
     * Detect all faces in bitmap
     * @return List of detected faces
     */
    fun detectAllFaces(bitmap: Bitmap): List<DetectedFace> {
        if (faceDetector == null) {
            Log.e(TAG, "Face detector not initialized")
            return emptyList()
        }
        
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = faceDetector?.detect(mpImage)
            
            result?.detections()?.map { detection ->
                val boundingBox = detection.boundingBox()
                
                DetectedFace(
                    left = boundingBox.left.toInt(),
                    top = boundingBox.top.toInt(),
                    right = boundingBox.right.toInt(),
                    bottom = boundingBox.bottom.toInt(),
                    confidence = detection.categories().firstOrNull()?.score() ?: 0f,
                    keypoints = detection.keypoints().orElse(null)?.map { Pair(it.x(), it.y()) }
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting faces", e)
            emptyList()
        }
    }
    
    /**
     * Extract and align face from bitmap
     */
    fun extractAlignedFace(bitmap: Bitmap, detectedFace: DetectedFace): Bitmap? {
        return try {
            // Add padding
            val padding = 20
            val left = (detectedFace.left - padding).coerceAtLeast(0)
            val top = (detectedFace.top - padding).coerceAtLeast(0)
            val right = (detectedFace.right + padding).coerceAtMost(bitmap.width)
            val bottom = (detectedFace.bottom + padding).coerceAtMost(bitmap.height)
            
            val width = right - left
            val height = bottom - top
            
            if (width > 0 && height > 0) {
                Bitmap.createBitmap(bitmap, left, top, width, height)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting face", e)
            null
        }
    }
    
    /**
     * Close detector and release resources
     */
    fun close() {
        faceDetector?.close()
        faceDetector = null
        Log.d(TAG, "Face detector closed")
    }
}

/**
 * Detected face data class
 */
data class DetectedFace(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
    val keypoints: List<Pair<Float, Float>>? = null
) {
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) / 2
    fun centerY() = (top + bottom) / 2
}
