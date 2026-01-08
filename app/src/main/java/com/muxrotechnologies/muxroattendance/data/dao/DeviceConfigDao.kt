package com.muxrotechnologies.muxroattendance.data.dao

import androidx.room.*
import com.muxrotechnologies.muxroattendance.data.entity.DeviceConfig
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for DeviceConfig operations
 */
@Dao
interface DeviceConfigDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: DeviceConfig)
    
    @Query("SELECT * FROM device_config WHERE key = :key")
    suspend fun getConfig(key: String): DeviceConfig?
    
    @Query("SELECT value FROM device_config WHERE key = :key")
    suspend fun getConfigValue(key: String): String?
    
    @Query("SELECT * FROM device_config")
    fun getAllConfigs(): Flow<List<DeviceConfig>>
    
    @Query("DELETE FROM device_config WHERE key = :key")
    suspend fun deleteConfig(key: String)
    
    @Transaction
    suspend fun setConfigValue(key: String, value: String) {
        insertConfig(DeviceConfig(key, value, System.currentTimeMillis()))
    }
}
