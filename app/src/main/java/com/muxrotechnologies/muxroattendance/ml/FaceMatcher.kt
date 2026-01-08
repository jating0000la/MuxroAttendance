package com.muxrotechnologies.muxroattendance.ml

import kotlin.math.sqrt

/**
 * Face matching engine - compares face embeddings using cosine similarity
 * Fully offline matching logic
 */
object FaceMatcher {
    
    // Production similarity thresholds - balanced for accuracy and usability
    const val DEFAULT_THRESHOLD = 0.72f // Optimized for production use
    const val STRONG_MATCH_THRESHOLD = 0.82f
    const val VERY_STRONG_MATCH_THRESHOLD = 0.88f
    
    // Multi-sample matching parameters
    const val MIN_SAMPLES_FOR_CONFIDENCE = 3 // Minimum samples to use voting
    const val VOTE_THRESHOLD = 0.5f // 50% of samples must agree
    
    /**
     * Compute cosine similarity between two embeddings
     * @param embedding1 First face embedding
     * @param embedding2 Second face embedding
     * @return Similarity score (0.0 to 1.0), higher = more similar
     */
    fun computeCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) {
            "Embeddings must have the same dimension"
        }
        
        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f
        
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }
        
        norm1 = sqrt(norm1)
        norm2 = sqrt(norm2)
        
        return if (norm1 > 0 && norm2 > 0) {
            dotProduct / (norm1 * norm2)
        } else {
            0.0f
        }
    }
    
    /**
     * Compute Euclidean distance between two embeddings
     * Alternative distance metric
     */
    fun computeEuclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) {
            "Embeddings must have the same dimension"
        }
        
        var sumSquares = 0.0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sumSquares += diff * diff
        }
        
        return sqrt(sumSquares)
    }
    
    /**
     * Match a face embedding against a list of stored embeddings
     * Uses simple best-match strategy (for single embedding per user)
     * @param queryEmbedding The face embedding to match
     * @param storedEmbeddings List of stored embeddings
     * @param threshold Minimum similarity threshold for a match
     * @return Match result with userId and confidence
     */
    fun matchFace(
        queryEmbedding: FloatArray,
        storedEmbeddings: List<Pair<Long, FloatArray>>, // (userId, embedding)
        threshold: Float = DEFAULT_THRESHOLD
    ): MatchResult {
        if (storedEmbeddings.isEmpty()) {
            return MatchResult.NoMatch
        }
        
        var bestMatch: Pair<Long, Float>? = null
        
        for ((userId, storedEmbedding) in storedEmbeddings) {
            val similarity = computeCosineSimilarity(queryEmbedding, storedEmbedding)
            
            if (similarity >= threshold) {
                if (bestMatch == null || similarity > bestMatch.second) {
                    bestMatch = Pair(userId, similarity)
                }
            }
        }
        
        return if (bestMatch != null) {
            MatchResult.Match(bestMatch.first, bestMatch.second)
        } else {
            MatchResult.NoMatch
        }
    }
    
    /**
     * Advanced matching with multiple samples per user
     * Uses voting and averaging for improved accuracy
     * @param queryEmbedding The face embedding to match
     * @param storedEmbeddings List of stored embeddings grouped by userId
     * @param threshold Minimum similarity threshold
     * @param strategy Matching strategy (AVERAGE, VOTING, BEST)
     * @return Match result with userId and confidence
     */
    fun matchFaceMultiSample(
        queryEmbedding: FloatArray,
        storedEmbeddings: List<Pair<Long, FloatArray>>, // (userId, embedding)
        threshold: Float = DEFAULT_THRESHOLD,
        strategy: MatchStrategy = MatchStrategy.AVERAGE
    ): MatchResult {
        if (storedEmbeddings.isEmpty()) {
            return MatchResult.NoMatch
        }
        
        // Group embeddings by userId
        val embeddingsByUser = storedEmbeddings.groupBy { it.first }
        
        // Calculate match scores for each user
        val userScores = mutableListOf<Pair<Long, Float>>()
        
        for ((userId, userEmbeddings) in embeddingsByUser) {
            val embeddings = userEmbeddings.map { it.second }
            
            val score = when (strategy) {
                MatchStrategy.AVERAGE -> calculateAverageScore(queryEmbedding, embeddings)
                MatchStrategy.VOTING -> calculateVotingScore(queryEmbedding, embeddings, threshold)
                MatchStrategy.BEST -> calculateBestScore(queryEmbedding, embeddings)
                MatchStrategy.WEIGHTED_AVERAGE -> calculateWeightedAverageScore(queryEmbedding, embeddings)
            }
            
            if (score >= threshold) {
                userScores.add(Pair(userId, score))
            }
        }
        
        // Return the best match
        val bestMatch = userScores.maxByOrNull { it.second }
        
        return if (bestMatch != null) {
            MatchResult.Match(bestMatch.first, bestMatch.second)
        } else {
            MatchResult.NoMatch
        }
    }
    
    /**
     * Calculate average similarity across all user samples
     */
    private fun calculateAverageScore(
        queryEmbedding: FloatArray,
        userEmbeddings: List<FloatArray>
    ): Float {
        if (userEmbeddings.isEmpty()) return 0f
        
        val similarities = userEmbeddings.map { embedding ->
            computeCosineSimilarity(queryEmbedding, embedding)
        }
        
        return similarities.average().toFloat()
    }
    
    /**
     * Calculate voting-based score (percentage of samples that match)
     */
    private fun calculateVotingScore(
        queryEmbedding: FloatArray,
        userEmbeddings: List<FloatArray>,
        threshold: Float
    ): Float {
        if (userEmbeddings.isEmpty()) return 0f
        
        val similarities = userEmbeddings.map { embedding ->
            computeCosineSimilarity(queryEmbedding, embedding)
        }
        
        val matchingCount = similarities.count { it >= threshold }
        val votePercentage = matchingCount.toFloat() / similarities.size
        
        // Return average of matching samples if vote passes
        return if (votePercentage >= VOTE_THRESHOLD) {
            similarities.filter { it >= threshold }.average().toFloat()
        } else {
            0f
        }
    }
    
    /**
     * Calculate best (maximum) similarity score
     */
    private fun calculateBestScore(
        queryEmbedding: FloatArray,
        userEmbeddings: List<FloatArray>
    ): Float {
        if (userEmbeddings.isEmpty()) return 0f
        
        return userEmbeddings.maxOf { embedding ->
            computeCosineSimilarity(queryEmbedding, embedding)
        }
    }
    
    /**
     * Calculate weighted average (higher scores get more weight)
     */
    private fun calculateWeightedAverageScore(
        queryEmbedding: FloatArray,
        userEmbeddings: List<FloatArray>
    ): Float {
        if (userEmbeddings.isEmpty()) return 0f
        
        val similarities = userEmbeddings.map { embedding ->
            computeCosineSimilarity(queryEmbedding, embedding)
        }
        
        // Use exponential weighting (higher similarities get exponentially more weight)
        val weights = similarities.map { similarity ->
            kotlin.math.exp(similarity * 5.0).toFloat() // e^(5*similarity)
        }
        
        val weightedSum = similarities.zip(weights).sumOf { (sim, weight) ->
            (sim * weight).toDouble()
        }
        
        val totalWeight = weights.sum()
        
        return (weightedSum / totalWeight).toFloat()
    }
    
    /**
     * Average multiple embeddings for a user
     * Used during enrollment to create a robust template
     */
    fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray? {
        if (embeddings.isEmpty()) return null
        
        val dimension = embeddings[0].size
        val avgEmbedding = FloatArray(dimension)
        
        for (embedding in embeddings) {
            require(embedding.size == dimension) {
                "All embeddings must have the same dimension"
            }
            for (i in embedding.indices) {
                avgEmbedding[i] += embedding[i]
            }
        }
        
        // Average
        for (i in avgEmbedding.indices) {
            avgEmbedding[i] = avgEmbedding[i] / embeddings.size
        }
        
        // Re-normalize
        var norm = 0.0f
        for (value in avgEmbedding) {
            norm += value * value
        }
        norm = sqrt(norm)
        
        if (norm > 0) {
            for (i in avgEmbedding.indices) {
                avgEmbedding[i] = avgEmbedding[i] / norm
            }
        }
        
        return avgEmbedding
    }
    
    /**
     * Calculate face quality score based on various factors
     * @param faceBitmap The detected face image
     * @return Quality score (0-100)
     */
    fun calculateQualityScore(
        faceBitmap: android.graphics.Bitmap,
        faceSize: Int,
        blur: Float = 0f // Future: implement blur detection
    ): Float {
        // Simple quality based on face size
        val sizeScore = (faceSize / 200f).coerceIn(0f, 1f) * 50f
        
        // Brightness/contrast check (simplified)
        val brightnessScore = checkBrightness(faceBitmap) * 30f
        
        // Blur score (placeholder)
        val blurScore = 20f
        
        return (sizeScore + brightnessScore + blurScore).coerceIn(0f, 100f)
    }
    
    private fun checkBrightness(bitmap: android.graphics.Bitmap): Float {
        // Sample center pixels to check brightness
        val width = bitmap.width
        val height = bitmap.height
        
        if (width < 10 || height < 10) return 0.5f
        
        val centerX = width / 2
        val centerY = height / 2
        val sampleSize = 10
        
        var totalBrightness = 0f
        var count = 0
        
        for (x in centerX - sampleSize until centerX + sampleSize) {
            for (y in centerY - sampleSize until centerY + sampleSize) {
                if (x in 0 until width && y in 0 until height) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = (pixel and 0xFF)
                    val brightness = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                    totalBrightness += brightness
                    count++
                }
            }
        }
        
        val avgBrightness = totalBrightness / count
        
        // Ideal brightness is around 0.4-0.7
        return if (avgBrightness in 0.4f..0.7f) {
            1.0f
        } else {
            1.0f - kotlin.math.abs(avgBrightness - 0.55f) * 2
        }.coerceIn(0f, 1f)
    }
}

/**
 * Matching strategy for multi-sample recognition
 */
enum class MatchStrategy {
    AVERAGE,           // Average similarity across all samples (balanced)
    VOTING,            // Vote-based (majority must agree) - more strict
    BEST,              // Use best matching sample (more lenient)
    WEIGHTED_AVERAGE   // Weighted average (better samples have more influence) - RECOMMENDED
}

/**
 * Match result sealed class
 */
sealed class MatchResult {
    data class Match(val userId: Long, val confidence: Float) : MatchResult()
    object NoMatch : MatchResult()
}