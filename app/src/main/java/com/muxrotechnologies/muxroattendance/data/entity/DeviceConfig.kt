package com.muxrotechnologies.muxroattendance.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Device configuration entity - stores app settings and admin credentials
 */
@Entity(tableName = "device_config")
data class DeviceConfig(
    @PrimaryKey
    val key: String,
    
    val value: String,
    
    val updatedAt: Long = System.currentTimeMillis()
)

// Configuration keys
object ConfigKeys {
    const val ADMIN_PIN_HASH = "admin_pin_hash"
    const val DEVICE_ID = "device_id"
    const val SIMILARITY_THRESHOLD = "similarity_threshold"
    const val MIN_FACE_QUALITY = "min_face_quality"
    const val DUPLICATE_ATTENDANCE_WINDOW_MS = "duplicate_attendance_window_ms"
    const val ENABLE_ROOT_DETECTION = "enable_root_detection"
    const val ENABLE_LIVENESS_DETECTION = "enable_liveness_detection"
    const val APP_INITIALIZED = "app_initialized"
}
