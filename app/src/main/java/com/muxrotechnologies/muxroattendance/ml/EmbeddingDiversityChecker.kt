package com.muxrotechnologies.muxroattendance.ml

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Ensures diversity in captured face embeddings
 * Prevents capturing too similar samples
 */
object EmbeddingDiversityChecker {
    
    // Minimum dissimilarity between samples (0.0 = identical, 1.0 = completely different)
    private const val MIN_DIVERSITY_THRESHOLD = 0.15f // 15% difference required
    private const val OPTIMAL_DIVERSITY = 0.25f // 25% difference is optimal
    
    /**
     * Check if a new embedding is diverse enough from existing ones
     */
    fun isDiverseEnough(
        newEmbedding: FloatArray,
        existingEmbeddings: List<FloatArray>,
        minDiversity: Float = MIN_DIVERSITY_THRESHOLD
    ): DiversityResult {
        if (existingEmbeddings.isEmpty()) {
            return DiversityResult(
                isDiverse = true,
                diversityScore = 100f,
                message = "First sample"
            )
        }
        
        // Calculate similarity with each existing embedding
        val similarities = existingEmbeddings.map { existing ->
            FaceMatcher.computeCosineSimilarity(newEmbedding, existing)
        }
        
        // Check if too similar to any existing
        val maxSimilarity = similarities.maxOrNull() ?: 0f
        val minSimilarity = similarities.minOrNull() ?: 0f
        val avgSimilarity = similarities.average().toFloat()
        
        // Diversity is inverse of similarity
        val diversity = 1f - maxSimilarity
        
        val isDiverse = diversity >= minDiversity
        
        // Score based on diversity (0-100)
        val diversityScore = when {
            diversity < MIN_DIVERSITY_THRESHOLD -> (diversity / MIN_DIVERSITY_THRESHOLD) * 50f
            diversity < OPTIMAL_DIVERSITY -> {
                50f + ((diversity - MIN_DIVERSITY_THRESHOLD) / (OPTIMAL_DIVERSITY - MIN_DIVERSITY_THRESHOLD)) * 50f
            }
            else -> 100f
        }
        
        val message = when {
            !isDiverse -> "Too similar to previous sample - change angle or expression"
            diversityScore >= 80f -> "Good variation!"
            diversityScore >= 60f -> "Acceptable variation"
            else -> "Try varying your pose slightly"
        }
        
        return DiversityResult(
            isDiverse = isDiverse,
            diversityScore = diversityScore,
            maxSimilarity = maxSimilarity,
            minSimilarity = minSimilarity,
            avgSimilarity = avgSimilarity,
            message = message
        )
    }
    
    /**
     * Calculate overall diversity of an embedding set
     */
    fun calculateSetDiversity(embeddings: List<FloatArray>): Float {
        if (embeddings.size < 2) return 100f
        
        var totalDiversity = 0f
        var pairCount = 0
        
        // Compare all pairs
        for (i in embeddings.indices) {
            for (j in i + 1 until embeddings.size) {
                val similarity = FaceMatcher.computeCosineSimilarity(embeddings[i], embeddings[j])
                val diversity = 1f - similarity
                totalDiversity += diversity
                pairCount++
            }
        }
        
        val avgDiversity = totalDiversity / pairCount
        
        // Normalize to 0-100 scale
        return (avgDiversity / OPTIMAL_DIVERSITY * 100f).coerceIn(0f, 100f)
    }
    
    /**
     * Get recommended poses for diverse samples
     */
    fun getRecommendedPoses(currentSample: Int, totalSamples: Int): String {
        return when (currentSample % 5) {
            0 -> "Face camera directly with neutral expression"
            1 -> "Slight smile"
            2 -> "Turn head slightly left"
            3 -> "Turn head slightly right"
            4 -> "Tilt head slightly"
            else -> "Natural expression"
        }
    }
    
    /**
     * Suggest improvements for next capture
     */
    fun getSuggestionForNextCapture(
        currentSample: Int,
        previousDiversity: Float?
    ): String {
        if (previousDiversity == null) {
            return "Capture first sample with neutral expression"
        }
        
        return when {
            previousDiversity < 30f -> "Change your facial expression or head angle more"
            previousDiversity < 60f -> "Good - try a slightly different angle"
            else -> "Excellent variation! Continue with similar diversity"
        }
    }
    
    /**
     * Analyze embedding set quality
     */
    fun analyzeEmbeddingSet(embeddings: List<FloatArray>): SetAnalysis {
        if (embeddings.size < 2) {
            return SetAnalysis(
                diversityScore = 0f,
                qualityScore = 0f,
                recommendation = "Need at least 2 samples",
                isGoodSet = false
            )
        }
        
        val diversity = calculateSetDiversity(embeddings)
        
        // Check if any embeddings are outliers (too different)
        val avgSimilarities = embeddings.map { emb1 ->
            embeddings
                .filter { it !== emb1 }
                .map { emb2 -> FaceMatcher.computeCosineSimilarity(emb1, emb2) }
                .average()
                .toFloat()
        }
        
        val minAvgSimilarity = avgSimilarities.minOrNull() ?: 0f
        val hasOutlier = minAvgSimilarity < 0.70f // Less than 70% similarity to others
        
        // Quality score considers both diversity and consistency
        val consistencyScore = (minAvgSimilarity * 100f).coerceIn(0f, 100f)
        val qualityScore = (diversity * 0.6f + consistencyScore * 0.4f)
        
        val recommendation = when {
            hasOutlier -> "One sample may be incorrect - review captures"
            diversity < 30f -> "Samples too similar - recapture with more variation"
            diversity > 70f -> "Samples too different - ensure same person"
            qualityScore >= 70f -> "Good quality embedding set"
            else -> "Acceptable - could be improved with better variation"
        }
        
        return SetAnalysis(
            diversityScore = diversity,
            qualityScore = qualityScore,
            hasOutlier = hasOutlier,
            recommendation = recommendation,
            isGoodSet = qualityScore >= 60f && !hasOutlier
        )
    }
}

/**
 * Result of diversity check
 */
data class DiversityResult(
    val isDiverse: Boolean,
    val diversityScore: Float,
    val maxSimilarity: Float = 0f,
    val minSimilarity: Float = 0f,
    val avgSimilarity: Float = 0f,
    val message: String
)

/**
 * Analysis of complete embedding set
 */
data class SetAnalysis(
    val diversityScore: Float,
    val qualityScore: Float,
    val hasOutlier: Boolean = false,
    val recommendation: String,
    val isGoodSet: Boolean
)
