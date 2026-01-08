package com.muxrotechnologies.muxroattendance.data.dao

import androidx.room.*
import com.muxrotechnologies.muxroattendance.data.entity.User
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for User operations
 */
@Dao
interface UserDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long
    
    @Update
    suspend fun updateUser(user: User)
    
    @Delete
    suspend fun deleteUser(user: User)
    
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): User?
    
    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserByUserId(userId: String): User?
    
    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveUsers(): Flow<List<User>>
    
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<User>>
    
    @Query("SELECT COUNT(*) FROM users WHERE isActive = 1")
    suspend fun getActiveUserCount(): Int
    
    @Query("UPDATE users SET isActive = :isActive WHERE id = :userId")
    suspend fun updateUserActiveStatus(userId: Long, isActive: Boolean)
    
    @Query("SELECT * FROM users WHERE name LIKE '%' || :query || '%' OR userId LIKE '%' || :query || '%'")
    fun searchUsers(query: String): Flow<List<User>>
    
    @Query("SELECT * FROM users ORDER BY name ASC")
    suspend fun getAllUsersSuspend(): List<User>
}
