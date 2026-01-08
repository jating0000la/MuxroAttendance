package com.muxrotechnologies.muxroattendance.utils

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * Utility for monitoring and managing device storage
 * Prevents data loss due to insufficient storage
 * Supports automatic cleanup and external storage fallback
 */
object StorageManager {
    
    // Storage thresholds
    private const val MIN_REQUIRED_BYTES = 50 * 1024 * 1024L // 50MB minimum
    private const val CRITICAL_THRESHOLD_BYTES = 10 * 1024 * 1024L // 10MB critical
    private const val WARNING_THRESHOLD_BYTES = 100 * 1024 * 1024L // 100MB warning
    
    // Auto-cleanup settings
    private const val AUTO_CLEANUP_ENABLED = true
    private const val OLD_LOGS_DAYS = 90 // Delete logs older than 90 days
    
    // Space estimates
    private const val ENROLLMENT_SPACE_BYTES = 2 * 1024 * 1024L // ~2MB per user (10 samples)
    private const val ATTENDANCE_LOG_BYTES = 500L // ~500 bytes per log entry
    private const val MODEL_DOWNLOAD_BYTES = 10 * 1024 * 1024L // ~10MB for models
    private const val EXPORT_OVERHEAD_BYTES = 1024 * 1024L // 1MB for export files
    
    /**
     * Check if device has sufficient storage for operation
     * @param context Application context
     * @param requiredBytes Minimum bytes required (default: 50MB)
     * @return true if sufficient storage available
     */
    fun hasAvailableStorage(context: Context, requiredBytes: Long = MIN_REQUIRED_BYTES): Boolean {
        return getAvailableBytes(context) >= requiredBytes
    }
    
    /**
     * Get available internal storage in bytes
     * @param context Application context
     * @return Available bytes in internal storage
     */
    fun getAvailableBytes(context: Context): Long {
        return try {
            val path = context.filesDir
            val stat = StatFs(path.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            android.util.Log.e("StorageManager", "Error checking storage", e)
            0L
        }
    }
    
    /**
     * Check if storage is critically low (< 10MB)
     * @param context Application context
     * @return true if storage is critically low
     */
    fun isCriticallyLow(context: Context): Boolean {
        return getAvailableBytes(context) < CRITICAL_THRESHOLD_BYTES
    }
    
    /**
     * Check if storage warning should be shown (< 100MB)
     * @param context Application context
     * @return true if storage is below warning threshold
     */
    fun shouldShowWarning(context: Context): Boolean {
        return getAvailableBytes(context) < WARNING_THRESHOLD_BYTES
    }
    
    /**
     * Get available storage in megabytes
     * @param context Application context
     * @return Available MB
     */
    fun getAvailableMB(context: Context): Long {
        return getAvailableBytes(context) / (1024 * 1024)
    }
    
    /**
     * Get available storage as formatted string
     * @param context Application context
     * @return Formatted string (e.g., "45 MB", "1.2 GB")
     */
    fun getAvailableStorageFormatted(context: Context): String {
        val bytes = getAvailableBytes(context)
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes bytes"
        }
    }
    
    /**
     * Get database file size
     * @param context Application context
     * @return Database size in bytes
     */
    fun getDatabaseSize(context: Context): Long {
        return try {
            val dbFile = context.getDatabasePath("muxro_attendance.db")
            if (dbFile.exists()) dbFile.length() else 0L
        } catch (e: Exception) {
            android.util.Log.e("StorageManager", "Error checking database size", e)
            0L
        }
    }
    
    /**
     * Get database size as formatted string
     * @param context Application context
     * @return Formatted string
     */
    fun getDatabaseSizeFormatted(context: Context): String {
        val bytes = getDatabaseSize(context)
        return when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes bytes"
        }
    }
    
    /**
     * Estimate space needed for user enrollment
     * @param sampleCount Number of face samples (default: 10)
     * @return Estimated bytes needed
     */
    fun estimateEnrollmentSpace(sampleCount: Int = 10): Long {
        return ENROLLMENT_SPACE_BYTES * (sampleCount / 10.0).toLong()
    }
    
    /**
     * Check if space is available for enrollment
     * @param context Application context
     * @param sampleCount Number of samples to enroll
     * @return true if sufficient space
     */
    fun canEnrollUser(context: Context, sampleCount: Int = 10): Boolean {
        val required = estimateEnrollmentSpace(sampleCount) + MIN_REQUIRED_BYTES
        return hasAvailableStorage(context, required)
    }
    
    /**
     * Estimate space needed for attendance logs
     * @param logCount Number of logs to store
     * @return Estimated bytes needed
     */
    fun estimateAttendanceLogSpace(logCount: Int): Long {
        return ATTENDANCE_LOG_BYTES * logCount
    }
    
    /**
     * Check if space is available for attendance
     * @param context Application context
     * @return true if sufficient space
     */
    fun canMarkAttendance(context: Context): Boolean {
        return hasAvailableStorage(context, ATTENDANCE_LOG_BYTES + (1024 * 1024)) // 1MB buffer
    }
    
    /**
     * Estimate space needed for model download
     * @return Estimated bytes needed
     */
    fun estimateModelDownloadSpace(): Long {
        return MODEL_DOWNLOAD_BYTES
    }
    
    /**
     * Check if space is available for model download
     * @param context Application context
     * @return true if sufficient space
     */
    fun canDownloadModels(context: Context): Boolean {
        return hasAvailableStorage(context, MODEL_DOWNLOAD_BYTES + MIN_REQUIRED_BYTES)
    }
    
    /**
     * Estimate space needed for export
     * @param recordCount Number of records to export
     * @return Estimated bytes needed
     */
    fun estimateExportSpace(recordCount: Int): Long {
        return (recordCount * 500L) + EXPORT_OVERHEAD_BYTES
    }
    
    /**
     * Check if space is available for export
     * @param context Application context
     * @param recordCount Number of records to export
     * @return true if sufficient space
     */
    fun canExport(context: Context, recordCount: Int): Boolean {
        val required = estimateExportSpace(recordCount)
        return hasAvailableStorage(context, required)
    }
    
    /**
     * Get storage health status
     * @param context Application context
     * @return Storage health status
     */
    fun getStorageHealth(context: Context): StorageHealth {
        val available = getAvailableBytes(context)
        return when {
            available < CRITICAL_THRESHOLD_BYTES -> StorageHealth.CRITICAL
            available < WARNING_THRESHOLD_BYTES -> StorageHealth.WARNING
            available < MIN_REQUIRED_BYTES * 2 -> StorageHealth.LOW
            else -> StorageHealth.HEALTHY
        }
    }
    
    /**
     * Get storage usage statistics
     * @param context Application context
     * @return Storage statistics
     */
    fun getStorageStats(context: Context): StorageStats {
        val path = context.filesDir
        val stat = StatFs(path.absolutePath)
        
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val usedBytes = totalBytes - availableBytes
        val usedPercent = if (totalBytes > 0) (usedBytes * 100.0 / totalBytes) else 0.0
        
        val dbSize = getDatabaseSize(context)
        
        return StorageStats(
            totalBytes = totalBytes,
            availableBytes = availableBytes,
            usedBytes = usedBytes,
            usedPercent = usedPercent,
            databaseBytes = dbSize,
            health = getStorageHealth(context)
        )
    }
    
    /**
     * Check if external storage is available and writable
     */
    fun isExternalStorageAvailable(): Boolean {
        val state = Environment.getExternalStorageState()
        return state == Environment.MEDIA_MOUNTED
    }
    
    /**
     * Get external storage available space
     */
    fun getExternalStorageAvailableBytes(): Long {
        return try {
            if (!isExternalStorageAvailable()) return 0L
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            android.util.Log.e("StorageManager", "Error checking external storage", e)
            0L
        }
    }
    
    /**
     * Check if external storage has sufficient space
     */
    fun hasExternalStorageSpace(requiredBytes: Long = MIN_REQUIRED_BYTES): Boolean {
        return isExternalStorageAvailable() && 
               getExternalStorageAvailableBytes() >= requiredBytes
    }
    
    /**
     * Get external storage directory for app data
     */
    fun getExternalStorageDir(context: Context): File? {
        return try {
            if (!isExternalStorageAvailable()) return null
            context.getExternalFilesDir(null)
        } catch (e: Exception) {
            android.util.Log.e("StorageManager", "Error getting external storage dir", e)
            null
        }
    }
    
    /**
     * Calculate timestamp for old logs cleanup
     * Returns timestamp for logs older than specified days
     */
    fun getOldLogsCleanupTimestamp(daysOld: Int = OLD_LOGS_DAYS): Long {
        return System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
    }
    
    /**
     * Check if automatic cleanup is enabled
     */
    fun isAutoCleanupEnabled(): Boolean = AUTO_CLEANUP_ENABLED
    
    /**
     * Estimate space that can be freed by cleanup
     * This is an estimate based on typical log size
     */
    suspend fun estimateCleanupSpace(context: Context, logCount: Int): Long {
        return logCount * ATTENDANCE_LOG_BYTES
    }
    
    /**
     * Storage health status
     */
    enum class StorageHealth {
        HEALTHY,    // > 100MB available
        LOW,        // 50-100MB available
        WARNING,    // 10-50MB available
        CRITICAL    // < 10MB available
    }
    
    /**
     * Storage statistics data class
     */
    data class StorageStats(
        val totalBytes: Long,
        val availableBytes: Long,
        val usedBytes: Long,
        val usedPercent: Double,
        val databaseBytes: Long,
        val health: StorageHealth
    ) {
        val totalMB: Long get() = totalBytes / (1024 * 1024)
    val availableMB: Long get() = availableBytes / (1024 * 1024)
    val usedMB: Long get() = usedBytes / (1024 * 1024)
    val databaseMB: Double get() = databaseBytes / (1024.0 * 1024)
    
    fun getFormattedTotal(): String = formatBytes(totalBytes)
    fun getFormattedAvailable(): String = formatBytes(availableBytes)
    fun getFormattedUsed(): String = formatBytes(usedBytes)
    fun getFormattedDatabase(): String = formatBytes(databaseBytes)
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes bytes"
        }
    }
    }
}
