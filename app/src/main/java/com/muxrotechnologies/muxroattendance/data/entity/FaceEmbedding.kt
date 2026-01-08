package com.muxrotechnologies.muxroattendance.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Face embedding entity - stores encrypted face embeddings for recognition
 * Multiple embeddings per user for better accuracy
 */
@Entity(
    tableName = "face_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["qualityScore"], name = "idx_quality")
    ]
)
data class FaceEmbedding(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val userId: Long, // Foreign key to User
    
    // Encrypted embedding as Base64 string
    val embeddingEncrypted: String,
    
    // Sample number (1-10) for this user
    val sampleNumber: Int,
    
    // Quality score of the captured face (0-100)
    val qualityScore: Float,
    
    val createdAt: Long = System.currentTimeMillis()
)
