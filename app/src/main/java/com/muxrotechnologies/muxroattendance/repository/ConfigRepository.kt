package com.muxrotechnologies.muxroattendance.repository

import com.muxrotechnologies.muxroattendance.data.dao.DeviceConfigDao
import com.muxrotechnologies.muxroattendance.data.entity.ConfigKeys
import com.muxrotechnologies.muxroattendance.utils.EncryptionUtil
import com.muxrotechnologies.muxroattendance.utils.SecurityUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for device configuration and settings
 * Optimized with IO dispatcher
 */
class ConfigRepository(
    private val deviceConfigDao: DeviceConfigDao
) {
    
    /**
     * Initialize default configuration
     */
    suspend fun initializeDefaults(deviceId: String, adminPin: String) = withContext(Dispatchers.IO) {
        deviceConfigDao.setConfigValue(ConfigKeys.DEVICE_ID, deviceId)
        deviceConfigDao.setConfigValue(
            ConfigKeys.ADMIN_PIN_HASH,
            EncryptionUtil.hashSHA256(adminPin)
        )
        deviceConfigDao.setConfigValue(ConfigKeys.SIMILARITY_THRESHOLD, "0.72")
        deviceConfigDao.setConfigValue(ConfigKeys.MIN_FACE_QUALITY, "45.0")
        deviceConfigDao.setConfigValue(
            ConfigKeys.DUPLICATE_ATTENDANCE_WINDOW_MS,
            (5 * 60 * 1000).toString() // 5 minutes
        )
        deviceConfigDao.setConfigValue(ConfigKeys.ENABLE_ROOT_DETECTION, "true")
        deviceConfigDao.setConfigValue(ConfigKeys.ENABLE_LIVENESS_DETECTION, "true")
        deviceConfigDao.setConfigValue(ConfigKeys.APP_INITIALIZED, "true")
    }
    
    /**
     * Check if app is initialized
     */
    suspend fun isAppInitialized(): Boolean = withContext(Dispatchers.IO) {
        val value = deviceConfigDao.getConfigValue(ConfigKeys.APP_INITIALIZED)
        value == "true"
    }
    
    /**
     * Verify admin PIN
     */
    suspend fun verifyAdminPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val storedHash = deviceConfigDao.getConfigValue(ConfigKeys.ADMIN_PIN_HASH)
        storedHash != null && EncryptionUtil.verifyHash(pin, storedHash)
    }
    
    /**
     * Update admin PIN
     */
    suspend fun updateAdminPin(newPin: String) = withContext(Dispatchers.IO) {
        deviceConfigDao.setConfigValue(
            ConfigKeys.ADMIN_PIN_HASH,
            EncryptionUtil.hashSHA256(newPin)
        )
    }
    
    /**
     * Get device ID
     */
    suspend fun getDeviceId(): String? = withContext(Dispatchers.IO) {
        deviceConfigDao.getConfigValue(ConfigKeys.DEVICE_ID)
    }
    
    /**
     * Get similarity threshold
     */
    suspend fun getSimilarityThreshold(): Float = withContext(Dispatchers.IO) {
        val value = deviceConfigDao.getConfigValue(ConfigKeys.SIMILARITY_THRESHOLD)
        value?.toFloatOrNull() ?: 0.72f // Production default
    }
    
    /**
     * Update similarity threshold
     */
    suspend fun updateSimilarityThreshold(threshold: Float) = withContext(Dispatchers.IO) {
        deviceConfigDao.setConfigValue(ConfigKeys.SIMILARITY_THRESHOLD, threshold.toString())
    }
    
    /**
     * Get minimum face quality
     */
    suspend fun getMinFaceQuality(): Float = withContext(Dispatchers.IO) {
        val value = deviceConfigDao.getConfigValue(ConfigKeys.MIN_FACE_QUALITY)
        value?.toFloatOrNull() ?: 50f
    }
    
    /**
     * Get duplicate attendance window
     */
    suspend fun getDuplicateAttendanceWindow(): Long = withContext(Dispatchers.IO) {
        val value = deviceConfigDao.getConfigValue(ConfigKeys.DUPLICATE_ATTENDANCE_WINDOW_MS)
        value?.toLongOrNull() ?: (5 * 60 * 1000)
    }
    
    /**
     * Is root detection enabled
     */
    suspend fun isRootDetectionEnabled(): Boolean {
        val value = deviceConfigDao.getConfigValue(ConfigKeys.ENABLE_ROOT_DETECTION)
        return value != "false"
    }
    
    /**
     * Is liveness detection enabled
     */
    suspend fun isLivenessDetectionEnabled(): Boolean {
        val value = deviceConfigDao.getConfigValue(ConfigKeys.ENABLE_LIVENESS_DETECTION)
        return value != "false"
    }
    
    /**
     * Update liveness detection setting
     */
    suspend fun updateLivenessDetection(enabled: Boolean) {
        deviceConfigDao.setConfigValue(ConfigKeys.ENABLE_LIVENESS_DETECTION, enabled.toString())
    }
    
    /**
     * Get recognition threshold (alias for getSimilarityThreshold)
     */
    suspend fun getRecognitionThreshold(): Float {
        return getSimilarityThreshold()
    }
    
    /**
     * Get duplicate window in milliseconds
     */
    suspend fun getDuplicateWindowMs(): Long {
        return getDuplicateAttendanceWindow()
    }
    
    /**
     * Check if liveness is enabled
     */
    suspend fun isLivenessEnabled(): Boolean {
        return isLivenessDetectionEnabled()
    }
    
    /**
     * Set recognition threshold
     */
    suspend fun setRecognitionThreshold(threshold: Float) {
        updateSimilarityThreshold(threshold)
    }
    
    /**
     * Set duplicate attendance interval
     */
    suspend fun setDuplicateInterval(ms: Long) {
        deviceConfigDao.setConfigValue(ConfigKeys.DUPLICATE_ATTENDANCE_WINDOW_MS, ms.toString())
    }
    
    /**
     * Set liveness check enabled
     */
    suspend fun setLivenessCheckEnabled(enabled: Boolean) {
        updateLivenessDetection(enabled)
    }
    
    /**
     * Set root detection enabled
     */
    suspend fun setRootDetectionEnabled(enabled: Boolean) {
        deviceConfigDao.setConfigValue(ConfigKeys.ENABLE_ROOT_DETECTION, enabled.toString())
    }
    
    /**
     * Set duplicate attendance window
     */
    suspend fun setDuplicateAttendanceWindow(ms: Long) {
        deviceConfigDao.setConfigValue(ConfigKeys.DUPLICATE_ATTENDANCE_WINDOW_MS, ms.toString())
    }
    
    /**
     * Check if liveness check is enabled (alias)
     */
    suspend fun isLivenessCheckEnabled(): Boolean {
        return isLivenessDetectionEnabled()
    }
}
