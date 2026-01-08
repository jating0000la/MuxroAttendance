package com.muxrotechnologies.muxroattendance.utils

import android.graphics.Bitmap
import java.security.MessageDigest

/**
 * Utility for creating audit hashes of face images
 */
object AuditHashUtil {
    
    /**
     * Create SHA-256 hash of bitmap for audit trail
     * Uses face region only for privacy
     */
    fun createImageHash(bitmap: Bitmap, faceRect: android.graphics.Rect? = null): String {
        return try {
            // Use face region if provided, otherwise full image
            val regionBitmap = if (faceRect != null && 
                faceRect.left >= 0 && faceRect.top >= 0 &&
                faceRect.right <= bitmap.width && faceRect.bottom <= bitmap.height) {
                Bitmap.createBitmap(
                    bitmap,
                    faceRect.left,
                    faceRect.top,
                    faceRect.width(),
                    faceRect.height()
                )
            } else {
                bitmap
            }
            
            // Convert to byte array
            val stream = java.io.ByteArrayOutputStream()
            regionBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val bytes = stream.toByteArray()
            
            // Calculate SHA-256
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(bytes)
            
            // Convert to hex string
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Return empty hash on error
            "error_${System.currentTimeMillis()}"
        }
    }
}
