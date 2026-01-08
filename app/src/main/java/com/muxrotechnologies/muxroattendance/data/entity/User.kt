package com.muxrotechnologies.muxroattendance.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User entity - stores user information
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["userId"], unique = true)]
)
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val userId: String, // Unique user identifier (e.g., employee ID)
    val name: String,
    val department: String? = null,
    val role: String? = null,
    val phone: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
