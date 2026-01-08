package com.muxrotechnologies.muxroattendance.repository

import android.content.Context
import com.muxrotechnologies.muxroattendance.data.dao.UserDao
import com.muxrotechnologies.muxroattendance.data.dao.FaceEmbeddingDao
import com.muxrotechnologies.muxroattendance.data.entity.User
import com.muxrotechnologies.muxroattendance.data.entity.FaceEmbedding
import com.muxrotechnologies.muxroattendance.utils.FaceEmbeddingCache
import com.muxrotechnologies.muxroattendance.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user and face embedding operations
 * Optimized with caching, IO dispatcher, and storage checks
 */
class UserRepository(
    private val userDao: UserDao,
    private val faceEmbeddingDao: FaceEmbeddingDao,
    private val context: Context
) {
    // Embedding cache for performance
    private val embeddingCache = FaceEmbeddingCache(maxSize = 200)
    
    /**
     * Get all active users
     */
    fun getAllActiveUsers(): Flow<List<User>> = userDao.getAllActiveUsers()
    
    /**
     * Get user by ID
     */
    suspend fun getUserById(userId: Long): User? = withContext(Dispatchers.IO) {
        userDao.getUserById(userId)
    }
    
    /**
     * Get user by user ID string
     */
    suspend fun getUserByUserId(userId: String): User? = withContext(Dispatchers.IO) {
        userDao.getUserByUserId(userId)
    }
    
    /**
     * Insert new user
     * Checks storage before write to prevent data loss
     */
    suspend fun insertUser(user: User): Long = withContext(Dispatchers.IO) {
        if (!StorageManager.canEnrollUser(context)) {
            // Check if external storage is available as fallback
            if (StorageManager.hasExternalStorageSpace()) {
                throw IOException(
                    "Internal storage full (${StorageManager.getAvailableMB(context)} MB). " +
                    "External storage available. Free up internal space or enable external storage option."
                )
            } else {
                throw IOException(
                    "Insufficient storage (${StorageManager.getAvailableMB(context)} MB free). " +
                    "Need at least ${StorageManager.estimateEnrollmentSpace() / (1024 * 1024)} MB to enroll user."
                )
            }
        }
        userDao.insertUser(user)
    }
    
    /**
     * Update user
     */
    suspend fun updateUser(user: User) = withContext(Dispatchers.IO) {
        userDao.updateUser(user)
    }
    
    /**
     * Delete user and associated embeddings
     */
    suspend fun deleteUser(user: User) = withContext(Dispatchers.IO) {
        embeddingCache.invalidateUser(user.id)
        faceEmbeddingDao.deleteEmbeddingsByUserId(user.id)
        userDao.deleteUser(user)
    }
    
    /**
     * Search users
     */
    fun searchUsers(query: String): Flow<List<User>> = userDao.searchUsers(query)
    
    /**
     * Get active user count
     */
    suspend fun getActiveUserCount(): Int = withContext(Dispatchers.IO) {
        userDao.getActiveUserCount()
    }
    
    /**
     * Update user active status
     */
    suspend fun updateUserActiveStatus(userId: Long, isActive: Boolean) = withContext(Dispatchers.IO) {
        userDao.updateUserActiveStatus(userId, isActive)
    }
    
    /**
     * Add face embeddings for user
     */
    suspend fun addFaceEmbeddings(embeddings: List<FaceEmbedding>) = withContext(Dispatchers.IO) {
        faceEmbeddingDao.insertEmbeddings(embeddings)
        // Invalidate cache for affected users
        embeddings.forEach { embeddingCache.invalidateUser(it.userId) }
    }
    
    /**
     * Get face embeddings for user (cached)
     */
    suspend fun getFaceEmbeddings(userId: Long): List<FaceEmbedding> = withContext(Dispatchers.IO) {
        embeddingCache.getEmbeddingsByUserId(userId, faceEmbeddingDao)
    }
    
    /**
     * Get all face embeddings (for matching) - cached for performance
     */
    suspend fun getAllFaceEmbeddings(): List<FaceEmbedding> = withContext(Dispatchers.IO) {
        embeddingCache.getAllEmbeddings(faceEmbeddingDao)
    }
    
    /**
     * Get embedding count for user
     */
    suspend fun getEmbeddingCount(userId: Long): Int = withContext(Dispatchers.IO) {
        faceEmbeddingDao.getEmbeddingCountForUser(userId)
    }
    
    /**
     * Delete embeddings for user
     */
    suspend fun deleteFaceEmbeddings(userId: Long) = withContext(Dispatchers.IO) {
        embeddingCache.invalidateUser(userId)
        faceEmbeddingDao.deleteEmbeddingsByUserId(userId)
    }
    
    /**
     * Get all users (suspending function)
     */
    suspend fun getAllUsers(): List<User> = withContext(Dispatchers.IO) {
        userDao.getAllUsersSuspend()
    }
    
    /**
     * Clear embedding cache (call when bulk operations performed)
     */
    fun clearCache() {
        embeddingCache.clear()
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats() = embeddingCache.getStats()
}
