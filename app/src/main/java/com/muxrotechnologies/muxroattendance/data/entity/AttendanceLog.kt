package com.muxrotechnologies.muxroattendance.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Attendance log entity - stores check-in/check-out records
 */
@Entity(
    tableName = "attendance_logs",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["timestamp"])]
)
data class AttendanceLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val userId: Long, // Foreign key to User
    
    val type: AttendanceType, // CHECK_IN or CHECK_OUT
    
    val timestamp: Long = System.currentTimeMillis(),
    
    // Recognition confidence score (0.0 - 1.0)
    val confidenceScore: Float,
    
    // Device identifier for binding
    val deviceId: String,
    
    // Late arrival flag (came after late threshold)
    val isLate: Boolean = false,
    
    // Early departure flag (left before early going threshold)
    val isEarlyGoing: Boolean = false,
    
    // Pair ID to link check-in with check-out (same value for both records)
    val pairId: String? = null,
    
    // Optional location data (future feature)
    val latitude: Double? = null,
    val longitude: Double? = null,
    
    // Synced to external system (future feature)
    val synced: Boolean = false
)

enum class AttendanceType {
    CHECK_IN,
    CHECK_OUT
}
