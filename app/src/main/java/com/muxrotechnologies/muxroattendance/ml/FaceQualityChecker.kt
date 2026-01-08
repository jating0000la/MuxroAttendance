package com.muxrotechnologies.muxroattendance.ml

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Advanced face quality assessment for better embeddings
 * Evaluates multiple quality factors before capturing embeddings
 */
object FaceQualityChecker {
    
    /**
     * Comprehensive quality assessment result
     */
    data class QualityAssessment(
        val overallScore: Float, // 0-100
        val brightnessScore: Float,
        val contrastScore: Float,
        val sharpnessScore: Float,
        val facePositionScore: Float,
        val faceSizeScore: Float,
        val isAcceptable: Boolean,
        val issues: List<String>
    )
    
    // Quality thresholds
    private const val MIN_OVERALL_QUALITY = 60f
    private const val MIN_BRIGHTNESS = 40f
    private const val MAX_BRIGHTNESS = 220f
    private const val MIN_CONTRAST = 30f
    private const val MIN_SHARPNESS = 40f
    private const val MIN_FACE_SIZE = 140
    private const val OPTIMAL_FACE_SIZE = 230
    
    /**
     * Assess face image quality comprehensively
     */
    fun assessQuality(
        faceBitmap: Bitmap,
        faceRect: android.graphics.Rect,
        imageBounds: android.graphics.Rect
    ): QualityAssessment {
        val brightnessScore = checkBrightness(faceBitmap)
        val contrastScore = checkContrast(faceBitmap)
        val sharpnessScore = checkSharpness(faceBitmap)
        val facePositionScore = checkFacePosition(faceRect, imageBounds)
        val faceSizeScore = checkFaceSize(faceRect)
        
        // Weighted overall score
        val overallScore = (
            brightnessScore * 0.25f +
            contrastScore * 0.20f +
            sharpnessScore * 0.25f +
            facePositionScore * 0.15f +
            faceSizeScore * 0.15f
        )
        
        // Identify issues
        val issues = mutableListOf<String>()
        if (brightnessScore < 50f) issues.add("Too dark or too bright")
        if (contrastScore < 50f) issues.add("Low contrast - check lighting")
        if (sharpnessScore < 50f) issues.add("Image blurry - hold still")
        if (facePositionScore < 50f) issues.add("Center your face")
        if (faceSizeScore < 50f) issues.add("Move closer to camera")
        
        return QualityAssessment(
            overallScore = overallScore,
            brightnessScore = brightnessScore,
            contrastScore = contrastScore,
            sharpnessScore = sharpnessScore,
            facePositionScore = facePositionScore,
            faceSizeScore = faceSizeScore,
            isAcceptable = overallScore >= MIN_OVERALL_QUALITY,
            issues = issues
        )
    }
    
    /**
     * Check if image brightness is in acceptable range
     */
    private fun checkBrightness(bitmap: Bitmap): Float {
        // Sample pixels for performance (every 5th pixel)
        var totalBrightness = 0f
        var sampleCount = 0
        
        val width = bitmap.width
        val height = bitmap.height
        val step = 5
        
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // Perceived brightness
                val brightness = (0.299f * r + 0.587f * g + 0.114f * b)
                totalBrightness += brightness
                sampleCount++
            }
        }
        
        val avgBrightness = totalBrightness / sampleCount
        
        // Score based on how close to optimal range (80-180)
        return when {
            avgBrightness < MIN_BRIGHTNESS -> (avgBrightness / MIN_BRIGHTNESS) * 50f
            avgBrightness > MAX_BRIGHTNESS -> ((255 - avgBrightness) / (255 - MAX_BRIGHTNESS)) * 50f
            else -> {
                // In good range, score based on closeness to optimal (130)
                val optimal = 130f
                val deviation = abs(avgBrightness - optimal)
                (100f - (deviation / optimal) * 50f).coerceIn(50f, 100f)
            }
        }
    }
    
    /**
     * Check image contrast (difference between light and dark areas)
     */
    private fun checkContrast(bitmap: Bitmap): Float {
        var minBrightness = 255f
        var maxBrightness = 0f
        var sampleCount = 0
        
        val width = bitmap.width
        val height = bitmap.height
        val step = 5
        
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                val brightness = (0.299f * r + 0.587f * g + 0.114f * b)
                minBrightness = minOf(minBrightness, brightness)
                maxBrightness = maxOf(maxBrightness, brightness)
                sampleCount++
            }
        }
        
        val contrast = maxBrightness - minBrightness
        
        // Score based on contrast level
        return when {
            contrast < MIN_CONTRAST -> (contrast / MIN_CONTRAST) * 50f
            contrast < 100f -> 50f + ((contrast - MIN_CONTRAST) / (100f - MIN_CONTRAST)) * 50f
            else -> 100f
        }
    }
    
    /**
     * Check image sharpness using Laplacian variance
     */
    private fun checkSharpness(bitmap: Bitmap): Float {
        // Convert to grayscale and apply Laplacian
        val width = bitmap.width
        val height = bitmap.height
        
        // Sample center region for performance
        val centerX = width / 2
        val centerY = height / 2
        val sampleSize = minOf(100, width / 2, height / 2)
        
        var variance = 0.0
        var count = 0
        
        for (y in (centerY - sampleSize / 2) until (centerY + sampleSize / 2)) {
            for (x in (centerX - sampleSize / 2) until (centerX + sampleSize / 2)) {
                if (x > 0 && x < width - 1 && y > 0 && y < height - 1) {
                    // Get surrounding pixels
                    val center = getGrayscale(bitmap.getPixel(x, y))
                    val top = getGrayscale(bitmap.getPixel(x, y - 1))
                    val bottom = getGrayscale(bitmap.getPixel(x, y + 1))
                    val left = getGrayscale(bitmap.getPixel(x - 1, y))
                    val right = getGrayscale(bitmap.getPixel(x + 1, y))
                    
                    // Laplacian kernel
                    val laplacian = abs(4 * center - top - bottom - left - right)
                    variance += laplacian * laplacian
                    count++
                }
            }
        }
        
        variance = variance / count
        
        // Normalize to 0-100 scale
        // Higher variance = sharper image
        val sharpnessScore = sqrt(variance).toFloat().coerceIn(0f, 100f)
        
        return when {
            sharpnessScore < MIN_SHARPNESS -> (sharpnessScore / MIN_SHARPNESS) * 50f
            else -> 50f + ((sharpnessScore - MIN_SHARPNESS) / (100f - MIN_SHARPNESS)) * 50f
        }
    }
    
    /**
     * Convert pixel to grayscale value
     */
    private fun getGrayscale(pixel: Int): Float {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
    
    /**
     * Check if face is centered in the frame
     */
    private fun checkFacePosition(faceRect: android.graphics.Rect, imageBounds: android.graphics.Rect): Float {
        val faceCenterX = faceRect.centerX()
        val faceCenterY = faceRect.centerY()
        val imageCenterX = imageBounds.centerX()
        val imageCenterY = imageBounds.centerY()
        
        val maxOffset = imageBounds.width() * 0.3f // 30% tolerance
        
        val offsetX = abs(faceCenterX - imageCenterX)
        val offsetY = abs(faceCenterY - imageCenterY)
        val totalOffset = sqrt((offsetX * offsetX + offsetY * offsetY).toFloat())
        
        val normalizedOffset = totalOffset / maxOffset
        
        return ((1f - normalizedOffset.coerceIn(0f, 1f)) * 100f).coerceAtLeast(0f)
    }
    
    /**
     * Check if face size is adequate
     */
    private fun checkFaceSize(faceRect: android.graphics.Rect): Float {
        val faceSize = minOf(faceRect.width(), faceRect.height())
        
        return when {
            faceSize < MIN_FACE_SIZE -> (faceSize.toFloat() / MIN_FACE_SIZE) * 50f
            faceSize < OPTIMAL_FACE_SIZE -> {
                50f + ((faceSize - MIN_FACE_SIZE).toFloat() / (OPTIMAL_FACE_SIZE - MIN_FACE_SIZE)) * 50f
            }
            else -> 100f
        }
    }
    
    /**
     * Quick check if face meets minimum requirements
     */
    fun meetsMinimumQuality(
        faceBitmap: Bitmap,
        faceRect: android.graphics.Rect,
        imageBounds: android.graphics.Rect
    ): Boolean {
        val assessment = assessQuality(faceBitmap, faceRect, imageBounds)
        return assessment.isAcceptable
    }
    
    /**
     * Get user-friendly quality message
     */
    fun getQualityMessage(assessment: QualityAssessment): String {
        return when {
            assessment.overallScore >= 80f -> "Excellent quality!"
            assessment.overallScore >= 70f -> "Good quality"
            assessment.overallScore >= 60f -> "Acceptable - ${assessment.issues.firstOrNull() ?: ""}"
            else -> assessment.issues.firstOrNull() ?: "Poor quality"
        }
    }
}
