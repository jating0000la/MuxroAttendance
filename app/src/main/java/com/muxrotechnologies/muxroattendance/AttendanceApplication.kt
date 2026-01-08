package com.muxrotechnologies.muxroattendance

import android.app.Application
import android.util.Log
import com.muxrotechnologies.muxroattendance.data.AttendanceDatabase
import com.muxrotechnologies.muxroattendance.repository.AttendanceRepository
import com.muxrotechnologies.muxroattendance.repository.ConfigRepository
import com.muxrotechnologies.muxroattendance.repository.UserRepository
import com.muxrotechnologies.muxroattendance.utils.SecurityUtil
import javax.crypto.SecretKey

/**
 * Application class for initialization
 */
class AttendanceApplication : Application() {
    
    // Lazy initialization of database and repositories
    lateinit var database: AttendanceDatabase
        private set
        
    private lateinit var encryptionKey: SecretKey
    
    lateinit var userRepository: UserRepository
        private set
    
    lateinit var attendanceRepository: AttendanceRepository
        private set
    
    lateinit var configRepository: ConfigRepository
        private set
    
    companion object {
        private const val TAG = "AttendanceApplication"
        
        @Volatile
        private var instance: AttendanceApplication? = null
        
        fun getInstance(): AttendanceApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize database and repositories
        initializeDatabase()
        
        Log.d(TAG, "Application initialized")
    }
    
    private fun initializeDatabase() {
        // Generate device-specific encryption key using Keystore
        val deviceId = SecurityUtil.getDeviceId(this)
        encryptionKey = com.muxrotechnologies.muxroattendance.utils.KeystoreManager
            .getMasterKey()
        
        // Generate secure passphrase using Keystore
        val passphrase = com.muxrotechnologies.muxroattendance.utils.KeystoreManager
            .getDatabasePassphrase(deviceId)
        database = AttendanceDatabase.getInstance(this, passphrase)
        
        // Initialize repositories with context for storage checks
        userRepository = UserRepository(
            database.userDao(),
            database.faceEmbeddingDao(),
            this
        )
        
        attendanceRepository = AttendanceRepository(
            database.attendanceLogDao(),
            this
        )
        
        configRepository = ConfigRepository(
            database.deviceConfigDao()
        )
    }
    
    fun getEncryptionKey(): SecretKey = encryptionKey
    
    /**
     * Clear all application data
     */
    suspend fun clearAllData() {
        database.clearAllTables()
    }
    
    override fun onTerminate() {
        super.onTerminate()
        AttendanceDatabase.closeDatabase()
    }
}
