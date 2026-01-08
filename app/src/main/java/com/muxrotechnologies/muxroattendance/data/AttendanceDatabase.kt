package com.muxrotechnologies.muxroattendance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.muxrotechnologies.muxroattendance.data.dao.*
import com.muxrotechnologies.muxroattendance.data.entity.*
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Main application database
 * Encrypted using SQLCipher for security
 */
@Database(
    entities = [
        User::class,
        FaceEmbedding::class,
        AttendanceLog::class,
        DeviceConfig::class,
        AttendanceAudit::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AttendanceDatabase : RoomDatabase() {
    
    abstract fun userDao(): UserDao
    abstract fun faceEmbeddingDao(): FaceEmbeddingDao
    abstract fun attendanceLogDao(): AttendanceLogDao
    abstract fun deviceConfigDao(): DeviceConfigDao
    abstract fun attendanceAuditDao(): AttendanceAuditDao
    
    companion object {
        @Volatile
        private var INSTANCE: AttendanceDatabase? = null
        
        private const val DATABASE_NAME = "muxro_attendance.db"
        
        // Database migrations
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add attendance_audit table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS attendance_audit (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        imageHash TEXT NOT NULL,
                        deviceId TEXT NOT NULL,
                        attemptNumber INTEGER NOT NULL DEFAULT 1,
                        previousAttemptTimestamp INTEGER,
                        success INTEGER NOT NULL,
                        errorMessage TEXT
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_attendance_audit_userId ON attendance_audit(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_attendance_audit_timestamp ON attendance_audit(timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_attendance_audit_deviceId ON attendance_audit(deviceId)")
            }
        }
        
        fun getInstance(context: Context, passphrase: String): AttendanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = try {
                    buildDatabase(context, passphrase)
                } catch (e: Exception) {
                    // Check if error is due to storage full
                    val isStorageFull = e.message?.contains("disk", ignoreCase = true) == true ||
                                      e.message?.contains("SQLITE_FULL", ignoreCase = true) == true ||
                                      e.message?.contains("space", ignoreCase = true) == true
                    
                    if (isStorageFull) {
                        android.util.Log.e("AttendanceDatabase", "Storage full, cannot initialize database", e)
                        throw java.io.IOException(
                            "Insufficient storage space. Please free up at least 50MB and restart the app."
                        )
                    }
                    
                    // If database is corrupted, delete it and recreate
                    android.util.Log.e("AttendanceDatabase", "Database corrupted, recreating", e)
                    context.deleteDatabase(DATABASE_NAME)
                    buildDatabase(context, passphrase)
                }
                INSTANCE = instance
                instance
            }
        }
        
        private fun buildDatabase(context: Context, passphrase: String): AttendanceDatabase {
            // Create encrypted database using SQLCipher
            val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))
            
            return Room.databaseBuilder(
                context.applicationContext,
                AttendanceDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2)
                // Only use fallback in debug builds
                // .fallbackToDestructiveMigration()
                .build()
        }
        
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
