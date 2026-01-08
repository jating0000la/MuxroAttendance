package com.muxrotechnologies.muxroattendance.data.dao

import androidx.room.*
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceLog
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for AttendanceLog operations
 */
@Dao
interface AttendanceLogDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AttendanceLog): Long
    
    @Query("SELECT * FROM attendance_logs WHERE userId = :userId ORDER BY timestamp DESC")
    fun getLogsByUserId(userId: Long): Flow<List<AttendanceLog>>
    
    @Query("SELECT * FROM attendance_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<AttendanceLog>>
    
    @Query("""
        SELECT * FROM attendance_logs 
        WHERE timestamp >= :startTime AND timestamp <= :endTime 
        ORDER BY timestamp DESC
    """)
    fun getLogsByDateRange(startTime: Long, endTime: Long): Flow<List<AttendanceLog>>
    
    @Query("""
        SELECT * FROM attendance_logs 
        WHERE userId = :userId 
        AND timestamp >= :startTime 
        AND timestamp <= :endTime 
        ORDER BY timestamp DESC
    """)
    fun getUserLogsByDateRange(userId: Long, startTime: Long, endTime: Long): Flow<List<AttendanceLog>>
    
    @Query("""
        SELECT * FROM attendance_logs 
        WHERE userId = :userId 
        AND type = :type 
        AND timestamp >= :afterTime 
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun getLastLogOfType(userId: Long, type: AttendanceType, afterTime: Long): AttendanceLog?
    
    @Query("SELECT COUNT(*) FROM attendance_logs WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getAttendanceCountByDateRange(startTime: Long, endTime: Long): Int
    
    @Query("SELECT * FROM attendance_logs WHERE synced = 0")
    suspend fun getUnsyncedLogs(): List<AttendanceLog>
    
    @Query("UPDATE attendance_logs SET synced = 1 WHERE id IN (:logIds)")
    suspend fun markLogsAsSynced(logIds: List<Long>)
    
    @Query("DELETE FROM attendance_logs WHERE timestamp < :beforeTime")
    suspend fun deleteOldLogs(beforeTime: Long)
    
    @Query("""
        SELECT * FROM attendance_logs 
        WHERE timestamp >= :startTime AND timestamp <= :endTime 
        ORDER BY timestamp DESC
    """)
    suspend fun getLogsByDateRangeSuspend(startTime: Long, endTime: Long): List<AttendanceLog>
    
    @Query("SELECT * FROM attendance_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsSuspend(): List<AttendanceLog>
    
    @Delete
    suspend fun deleteLog(log: AttendanceLog)
}
