package com.muxrotechnologies.muxroattendance.utils

import android.util.Log

/**
 * Performance monitoring utility for tracking operation durations
 * Helps identify bottlenecks in face recognition pipeline
 */
object PerformanceMonitor {
    
    private const val TAG = "PerformanceMonitor"
    private val timings = mutableMapOf<String, MutableList<Long>>()
    
    /**
     * Track an operation execution time
     */
    inline fun <T> track(operationName: String, block: () -> T): T {
        val startTime = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val duration = System.currentTimeMillis() - startTime
            recordTiming(operationName, duration)
        }
    }
    
    /**
     * Track a suspend operation execution time
     */
    suspend inline fun <T> trackSuspend(operationName: String, crossinline block: suspend () -> T): T {
        val startTime = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val duration = System.currentTimeMillis() - startTime
            recordTiming(operationName, duration)
        }
    }
    
    /**
     * Record timing for an operation
     */
    @PublishedApi
    internal fun recordTiming(operationName: String, duration: Long) {
        synchronized(timings) {
            val list = timings.getOrPut(operationName) { mutableListOf() }
            list.add(duration)
            
            // Log if operation is slow
            if (duration > 100) {
                Log.w(TAG, "$operationName took ${duration}ms")
            }
            
            // Keep only last 100 entries
            if (list.size > 100) {
                list.removeAt(0)
            }
        }
    }
    
    /**
     * Get statistics for an operation
     */
    fun getStats(operationName: String): OperationStats? {
        synchronized(timings) {
            val list = timings[operationName] ?: return null
            if (list.isEmpty()) return null
            
            val sorted = list.sorted()
            return OperationStats(
                operation = operationName,
                count = list.size,
                average = list.average(),
                median = sorted[sorted.size / 2].toDouble(),
                min = sorted.first().toLong(),
                max = sorted.last().toLong(),
                p95 = sorted[(sorted.size * 0.95).toInt()].toLong()
            )
        }
    }
    
    /**
     * Get all statistics
     */
    fun getAllStats(): List<OperationStats> {
        synchronized(timings) {
            return timings.keys.mapNotNull { getStats(it) }
        }
    }
    
    /**
     * Print all statistics to log
     */
    fun printStats() {
        Log.i(TAG, "=== Performance Statistics ===")
        getAllStats().sortedByDescending { it.average }.forEach { stats ->
            Log.i(TAG, stats.toString())
        }
    }
    
    /**
     * Clear all statistics
     */
    fun clear() {
        synchronized(timings) {
            timings.clear()
        }
    }
}

data class OperationStats(
    val operation: String,
    val count: Int,
    val average: Double,
    val median: Double,
    val min: Long,
    val max: Long,
    val p95: Long
) {
    override fun toString(): String {
        return "$operation: avg=${average.toInt()}ms, median=${median.toInt()}ms, " +
               "min=${min}ms, max=${max}ms, p95=${p95}ms (n=$count)"
    }
}
