package com.muxrotechnologies.muxroattendance.data

import androidx.room.TypeConverter
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceType

/**
 * Type converters for Room database
 */
class Converters {
    
    @TypeConverter
    fun fromAttendanceType(type: AttendanceType): String {
        return type.name
    }
    
    @TypeConverter
    fun toAttendanceType(value: String): AttendanceType {
        return AttendanceType.valueOf(value)
    }
}
