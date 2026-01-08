package com.muxrotechnologies.muxroattendance.data.dao

import androidx.room.*
import com.muxrotechnologies.muxroattendance.data.entity.FaceEmbedding
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for FaceEmbedding operations
 */
@Dao
interface FaceEmbeddingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: FaceEmbedding): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<FaceEmbedding>)
    
    @Delete
    suspend fun deleteEmbedding(embedding: FaceEmbedding)
    
    @Query("SELECT * FROM face_embeddings WHERE userId = :userId ORDER BY sampleNumber ASC")
    suspend fun getEmbeddingsByUserId(userId: Long): List<FaceEmbedding>
    
    @Query("SELECT * FROM face_embeddings ORDER BY userId ASC")
    suspend fun getAllEmbeddings(): List<FaceEmbedding>
    
    @Query("DELETE FROM face_embeddings WHERE userId = :userId")
    suspend fun deleteEmbeddingsByUserId(userId: Long)
    
    @Query("SELECT COUNT(*) FROM face_embeddings WHERE userId = :userId")
    suspend fun getEmbeddingCountForUser(userId: Long): Int
    
    @Query("SELECT AVG(qualityScore) FROM face_embeddings WHERE userId = :userId")
    suspend fun getAverageQualityForUser(userId: Long): Float?
}
