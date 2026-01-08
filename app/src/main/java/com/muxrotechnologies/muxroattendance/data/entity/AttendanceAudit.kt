package com.muxrotechnologies.muxroattendance.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Audit log for attendance events
 * Provides accountability and forensics
 */
@Entity(
    tableName = "attendance_audit",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["timestamp"]),
        Index(value = ["deviceId"])
    ]
)
data class AttendanceAudit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val userId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val type: AttendanceType,
    val confidence: Float,
    
    // Security & forensics
    val imageHash: String, // SHA-256 of face region for verification
    val deviceId: String,
    
    // Additional context
    val attemptNumber: Int = 1, // For tracking repeated attempts
    val previousAttemptTimestamp: Long? = null,
    
    // Result
    val success: Boolean,
    val errorMessage: String? = null
)
