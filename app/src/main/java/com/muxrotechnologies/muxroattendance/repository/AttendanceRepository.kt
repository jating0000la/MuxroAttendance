package com.muxrotechnologies.muxroattendance.repository

import android.content.Context
import com.muxrotechnologies.muxroattendance.data.dao.AttendanceLogDao
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceLog
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceType
import com.muxrotechnologies.muxroattendance.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

/**
 * Repository for attendance operations
 * Optimized with IO dispatcher and storage checks
 */
class AttendanceRepository(
    private val attendanceLogDao: AttendanceLogDao,
    private val context: Context
) {
    
    /**
     * Mark attendance (check-in or check-out)
     * Checks storage before write to prevent data loss
     * Auto-cleanup old logs if storage is low
     */
    suspend fun markAttendance(log: AttendanceLog): Long = withContext(Dispatchers.IO) {
        // Check storage before write
        if (!StorageManager.canMarkAttendance(context)) {
            // Try automatic cleanup if enabled
            if (StorageManager.isAutoCleanupEnabled()) {
                try {
                    val cleanupTimestamp = StorageManager.getOldLogsCleanupTimestamp()
                    deleteOldLogs(cleanupTimestamp)
                    android.util.Log.i("AttendanceRepository", "Auto-cleanup: Deleted old logs")
                    
                    // Check again after cleanup
                    if (!StorageManager.canMarkAttendance(context)) {
                        // Still not enough space, try external storage
                        if (StorageManager.hasExternalStorageSpace()) {
                            throw IOException(
                                "Internal storage full (${StorageManager.getAvailableMB(context)} MB). " +
                                "External storage available. Consider moving data or free up space."
                            )
                        } else {
                            throw IOException(
                                "Insufficient storage (${StorageManager.getAvailableMB(context)} MB free). " +
                                "Old logs cleaned. Please free up more space to record attendance."
                            )
                        }
                    }
                } catch (e: Exception) {
                    throw IOException(
                        "Storage full (${StorageManager.getAvailableMB(context)} MB). " +
                        "Cannot record attendance. Please free up space."
                    )
                }
            } else {
                throw IOException(
                    "Insufficient storage (${StorageManager.getAvailableMB(context)} MB free). " +
                    "Please free up space to record attendance."
                )
            }
        }
        
        attendanceLogDao.insertLog(log)
    }
    
    /**
     * Get recent attendance logs
     */
    fun getRecentLogs(limit: Int = 100): Flow<List<AttendanceLog>> =
        attendanceLogDao.getRecentLogs(limit)
    
    /**
     * Get logs by user ID
     */
    fun getLogsByUserId(userId: Long): Flow<List<AttendanceLog>> =
        attendanceLogDao.getLogsByUserId(userId)
    
    /**
     * Get logs by date range
     */
    fun getLogsByDateRange(startTime: Long, endTime: Long): Flow<List<AttendanceLog>> =
        attendanceLogDao.getLogsByDateRange(startTime, endTime)
    
    /**
     * Get user logs by date range
     */
    fun getUserLogsByDateRange(userId: Long, startTime: Long, endTime: Long): Flow<List<AttendanceLog>> =
        attendanceLogDao.getUserLogsByDateRange(userId, startTime, endTime)
    
    /**
     * Check if user can mark attendance (prevent duplicates)
     */
    suspend fun canMarkAttendance(
        userId: Long,
        type: AttendanceType,
        windowMs: Long = 5 * 60 * 1000 // 5 minutes
    ): Boolean = withContext(Dispatchers.IO) {
        val afterTime = System.currentTimeMillis() - windowMs
        val lastLog = attendanceLogDao.getLastLogOfType(userId, type, afterTime)
        lastLog == null
    }
    
    /**
     * Get last attendance log for user
     */
    suspend fun getLastLog(userId: Long): AttendanceLog? {
        val logs = attendanceLogDao.getLogsByUserId(userId)
        // Note: This is a workaround; consider adding a specific DAO method
        return null // Implement proper logic
    }
    
    /**
     * Get attendance count for date range
     */
    suspend fun getAttendanceCount(startTime: Long, endTime: Long): Int = withContext(Dispatchers.IO) {
        attendanceLogDao.getAttendanceCountByDateRange(startTime, endTime)
    }
    
    /**
     * Get today's attendance count
     */
    suspend fun getTodayAttendanceCount(): Int = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000
        
        attendanceLogDao.getAttendanceCountByDateRange(startOfDay, endOfDay)
    }
    
    /**
     * Delete old logs (data cleanup)
     */
    suspend fun deleteOldLogs(beforeTime: Long) = withContext(Dispatchers.IO) {
        attendanceLogDao.deleteOldLogs(beforeTime)
    }
    
    /**
     * Get attendance logs by date range (suspending function)
     */
    suspend fun getAttendanceByDateRange(startTime: Long, endTime: Long): List<AttendanceLog> = withContext(Dispatchers.IO) {
        attendanceLogDao.getLogsByDateRangeSuspend(startTime, endTime)
    }
    
    /**
     * Get all attendance logs (suspending function)
     */
    suspend fun getAllAttendance(): List<AttendanceLog> = withContext(Dispatchers.IO) {
        attendanceLogDao.getAllLogsSuspend()
    }
    
    /**
     * Delete an attendance log
     */
    suspend fun delete(log: AttendanceLog) = withContext(Dispatchers.IO) {
        attendanceLogDao.deleteLog(log)
    }
    
    /**
     * Get attendance in range (alias for getAttendanceByDateRange)
     */
    suspend fun getAttendanceInRange(startTime: Long, endTime: Long): List<AttendanceLog> {
        return getAttendanceByDateRange(startTime, endTime)
    }
    
    /**
     * Delete attendance
     */
    suspend fun deleteAttendance(log: AttendanceLog) = withContext(Dispatchers.IO) {
        delete(log)
    }
}
