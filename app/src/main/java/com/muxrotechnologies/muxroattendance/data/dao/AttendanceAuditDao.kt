package com.muxrotechnologies.muxroattendance.data.dao

import androidx.room.*
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceAudit
import kotlinx.coroutines.flow.Flow

/**
 * DAO for attendance audit logs
 */
@Dao
interface AttendanceAuditDao {
    
    @Insert
    suspend fun insertAudit(audit: AttendanceAudit): Long
    
    @Query("SELECT * FROM attendance_audit ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAudits(limit: Int = 100): Flow<List<AttendanceAudit>>
    
    @Query("SELECT * FROM attendance_audit WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAuditsByUser(userId: Long): Flow<List<AttendanceAudit>>
    
    @Query("SELECT * FROM attendance_audit WHERE success = 0 ORDER BY timestamp DESC LIMIT :limit")
    fun getFailedAttempts(limit: Int = 50): Flow<List<AttendanceAudit>>
    
    @Query("SELECT * FROM attendance_audit WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getAuditsByTimeRange(startTime: Long, endTime: Long): List<AttendanceAudit>
    
    @Query("DELETE FROM attendance_audit WHERE timestamp < :cutoffTime")
    suspend fun deleteOldAudits(cutoffTime: Long): Int
    
    @Query("SELECT COUNT(*) FROM attendance_audit WHERE userId = :userId AND success = 0 AND timestamp > :since")
    suspend fun getFailedAttemptsCount(userId: Long, since: Long): Int
}
