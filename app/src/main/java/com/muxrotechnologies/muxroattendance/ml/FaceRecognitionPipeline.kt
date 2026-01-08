package com.muxrotechnologies.muxroattendance.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.muxrotechnologies.muxroattendance.data.entity.FaceEmbedding
import com.muxrotechnologies.muxroattendance.utils.EncryptionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

/**
 * Complete face recognition pipeline
 * Detection → Alignment → Embedding → Matching
 * Fully offline operation
 */
class FaceRecognitionPipeline(
    private val context: Context,
    private val encryptionKey: SecretKey
) {
    
    private val faceDetector = MediaPipeFaceDetector(context)
    private val faceRecognitionModel = FaceRecognitionModel(context)
    private val livenessDetector = LivenessDetector(context)
    
    private var isInitialized = false
    
    companion object {
        private const val TAG = "FaceRecognitionPipeline"
        private const val MIN_FACE_SIZE = 70 // Production-ready minimum
        private const val MIN_QUALITY_SCORE = 45f // Balanced quality threshold
    }
    
    /**
     * Check if pipeline is fully initialized
     */
    fun isReady(): Boolean = isInitialized
    
    /**
     * Initialize all ML models
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.Default) {
        if (isInitialized) return@withContext true
        
        val detectorInit = faceDetector.initialize()
        val modelInit = faceRecognitionModel.loadModel()
        val livenessInit = livenessDetector.initialize()
        
        isInitialized = detectorInit && modelInit && livenessInit
        
        if (isInitialized) {
            Log.d(TAG, "Pipeline initialized successfully")
        } else {
            Log.e(TAG, "Pipeline initialization failed")
        }
        
        isInitialized
    }
    
    /**
     * Process enrollment - capture and generate embeddings with quality checks
     */
    suspend fun processEnrollment(bitmap: Bitmap): EnrollmentResult = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            return@withContext EnrollmentResult.Error("Pipeline not initialized")
        }
        
        // 1. Detect face
        val detectedFace = faceDetector.detectFace(bitmap)
            ?: return@withContext EnrollmentResult.Error("No face detected")
        
        if (detectedFace.width() < MIN_FACE_SIZE || detectedFace.height() < MIN_FACE_SIZE) {
            return@withContext EnrollmentResult.Error("Face too small - move closer")
        }
        
        // 2. Extract aligned face
        val alignedFace = faceDetector.extractAlignedFace(bitmap, detectedFace)
            ?: return@withContext EnrollmentResult.Error("Failed to extract face")
        
        // 3. Advanced quality assessment
        val faceRect = android.graphics.Rect(
            detectedFace.left, detectedFace.top,
            detectedFace.right, detectedFace.bottom
        )
        val imageBounds = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        
        val qualityAssessment = FaceQualityChecker.assessQuality(
            alignedFace, faceRect, imageBounds
        )
        
        if (!qualityAssessment.isAcceptable) {
            val message = FaceQualityChecker.getQualityMessage(qualityAssessment)
            return@withContext EnrollmentResult.Error(message)
        }
        
        // 4. Generate embedding
        val embedding = faceRecognitionModel.generateEmbedding(alignedFace)
            ?: return@withContext EnrollmentResult.Error("Failed to generate embedding")
        
        // 5. Encrypt embedding
        val encryptedEmbedding = EncryptionUtil.encryptFloatArray(embedding, encryptionKey)
        
        EnrollmentResult.Success(
            embedding = encryptedEmbedding,
            qualityScore = qualityAssessment.overallScore,
            detectedFace = detectedFace
        )
    }
    
    /**
     * Process enrollment with diversity check
     */
    suspend fun processEnrollmentWithDiversity(
        bitmap: Bitmap,
        existingEmbeddings: List<String> // Encrypted embeddings
    ): EnrollmentResult = withContext(Dispatchers.Default) {
        // First get the basic enrollment result
        val result = processEnrollment(bitmap)
        
        if (result !is EnrollmentResult.Success) {
            return@withContext result
        }
        
        // Decrypt existing embeddings for diversity check
        val decryptedExisting = existingEmbeddings.mapNotNull { encrypted ->
            try {
                EncryptionUtil.decryptToFloatArray(encrypted, encryptionKey)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt embedding for diversity check", e)
                null
            }
        }
        
        // Decrypt new embedding
        val newEmbedding = EncryptionUtil.decryptToFloatArray(result.embedding, encryptionKey)
        
        // Check diversity
        val diversityCheck = EmbeddingDiversityChecker.isDiverseEnough(
            newEmbedding, decryptedExisting
        )
        
        if (!diversityCheck.isDiverse) {
            return@withContext EnrollmentResult.Error(diversityCheck.message)
        }
        
        // Return success with diversity info
        return@withContext EnrollmentResult.Success(
            embedding = result.embedding,
            qualityScore = result.qualityScore,
            detectedFace = result.detectedFace,
            diversityScore = diversityCheck.diversityScore
        )
    }
    
    /**
     * Process attendance - detect, match, and verify liveness
     */
    suspend fun processAttendance(
        bitmap: Bitmap,
        storedEmbeddings: List<FaceEmbedding>,
        requireLiveness: Boolean = true,
        similarityThreshold: Float = FaceMatcher.DEFAULT_THRESHOLD
    ): AttendanceResult = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            return@withContext AttendanceResult.Error("Pipeline not initialized")
        }
        
        // 1. Detect face
        val detectedFace = faceDetector.detectFace(bitmap)
            ?: return@withContext AttendanceResult.Error("No face detected")
        
        // 2. Liveness check (if required)
        if (requireLiveness) {
            val livenessResult = livenessDetector.detectLiveness(bitmap)
            if (livenessResult == null) {
                return@withContext AttendanceResult.Error("Liveness check failed")
            }
            // Additional liveness validation can be added here
        }
        
        // 3. Extract aligned face
        val alignedFace = faceDetector.extractAlignedFace(bitmap, detectedFace)
            ?: return@withContext AttendanceResult.Error("Failed to extract face")
        
        // 4. Generate embedding
        val queryEmbedding = faceRecognitionModel.generateEmbedding(alignedFace)
            ?: return@withContext AttendanceResult.Error("Failed to generate embedding")
        
        // 5. Decrypt stored embeddings and match
        val decryptedEmbeddings = storedEmbeddings.mapNotNull { faceEmbedding ->
            try {
                val decrypted = EncryptionUtil.decryptToFloatArray(
                    faceEmbedding.embeddingEncrypted,
                    encryptionKey
                )
                Pair(faceEmbedding.userId, decrypted)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt embedding", e)
                null
            }
        }
        
        // 6. Match face using multi-sample strategy for better accuracy
        val matchResult = FaceMatcher.matchFaceMultiSample(
            queryEmbedding,
            decryptedEmbeddings,
            similarityThreshold,
            MatchStrategy.BEST // Faster matching strategy
        )
        
        when (matchResult) {
            is MatchResult.Match -> {
                AttendanceResult.Recognized(
                    userId = matchResult.userId,
                    confidence = matchResult.confidence
                )
            }
            is MatchResult.NoMatch -> {
                AttendanceResult.NotRecognized
            }
        }
    }
    
    /**
     * Detect face in bitmap - simplified wrapper
     */
    fun detectFace(bitmap: Bitmap): DetectedFace? {
        if (!isInitialized) {
            Log.w(TAG, "detectFace called before pipeline initialization")
            return null
        }
        return faceDetector.detectFace(bitmap)
    }
    
    /**
     * Detect all faces in bitmap
     * @return List of detected faces
     */
    fun detectAllFaces(bitmap: Bitmap): List<DetectedFace> {
        if (!isInitialized) {
            Log.w(TAG, "detectAllFaces called before pipeline initialization")
            return emptyList()
        }
        return faceDetector.detectAllFaces(bitmap)
    }
    
    /**
     * Decrypt embeddings for caching
     * Returns list of (userId, embedding) pairs
     */
    fun decryptEmbeddings(storedEmbeddings: List<FaceEmbedding>): List<Pair<Long, FloatArray>> {
        return storedEmbeddings.mapNotNull { faceEmbedding ->
            try {
                val decrypted = EncryptionUtil.decryptToFloatArray(
                    faceEmbedding.embeddingEncrypted,
                    encryptionKey
                )
                Pair(faceEmbedding.userId, decrypted)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt embedding", e)
                null
            }
        }
    }
    
    /**
     * Optimized attendance processing with pre-decrypted embeddings and optional face skip
     */
    suspend fun processAttendanceOptimized(
        bitmap: Bitmap,
        detectedFace: DetectedFace?, // Optional: already detected face from preview
        decryptedEmbeddings: List<Pair<Long, FloatArray>>, // Pre-decrypted embeddings
        requireLiveness: Boolean = false,
        similarityThreshold: Float = FaceMatcher.DEFAULT_THRESHOLD
    ): AttendanceResult = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            return@withContext AttendanceResult.Error("Pipeline not initialized")
        }
        
        // 1. Use provided detected face or detect new one
        val face = detectedFace ?: faceDetector.detectFace(bitmap)
            ?: return@withContext AttendanceResult.Error("No face detected")
        
        // 2. Liveness check (if required)
        if (requireLiveness) {
            val livenessResult = livenessDetector.detectLiveness(bitmap)
            if (livenessResult == null) {
                return@withContext AttendanceResult.Error("Liveness check failed")
            }
        }
        
        // 3. Extract aligned face
        val alignedFace = faceDetector.extractAlignedFace(bitmap, face)
            ?: return@withContext AttendanceResult.Error("Failed to extract face")
        
        // 4. Generate embedding
        val queryEmbedding = faceRecognitionModel.generateEmbedding(alignedFace)
            ?: return@withContext AttendanceResult.Error("Failed to generate embedding")
        
        // 5. Match using pre-decrypted embeddings with BEST strategy (fastest)
        val matchResult = FaceMatcher.matchFaceMultiSample(
            queryEmbedding,
            decryptedEmbeddings,
            similarityThreshold,
            MatchStrategy.BEST
        )
        
        when (matchResult) {
            is MatchResult.Match -> {
                AttendanceResult.Recognized(
                    userId = matchResult.userId,
                    confidence = matchResult.confidence
                )
            }
            is MatchResult.NoMatch -> {
                AttendanceResult.NotRecognized
            }
        }
    }
    
    /**
     * Verify liveness with specific challenge
     */
    suspend fun verifyLiveness(bitmap: Bitmap, challenge: LivenessChallenge): Boolean = 
        withContext(Dispatchers.Default) {
            val result = livenessDetector.detectLiveness(bitmap) ?: return@withContext false
            
            when (challenge) {
                LivenessChallenge.BLINK -> result.isBlink
                LivenessChallenge.TURN_HEAD_LEFT -> result.isHeadTurned && result.headPose < 0
                LivenessChallenge.TURN_HEAD_RIGHT -> result.isHeadTurned && result.headPose > 0
                LivenessChallenge.SMILE -> result.isSmiling
            }
        }
    
    /**
     * Release all resources
     */
    fun release() {
        faceDetector.close()
        faceRecognitionModel.close()
        livenessDetector.close()
        isInitialized = false
        Log.d(TAG, "Pipeline resources released")
    }
}

/**
 * Enrollment result
 */
sealed class EnrollmentResult {
    data class Success(
        val embedding: String,
        val qualityScore: Float,
        val detectedFace: DetectedFace,
        val diversityScore: Float? = null
    ) : EnrollmentResult()
    
    data class Error(val message: String) : EnrollmentResult()
}

/**
 * Attendance result
 */
sealed class AttendanceResult {
    data class Recognized(val userId: Long, val confidence: Float) : AttendanceResult()
    object NotRecognized : AttendanceResult()
    data class Error(val message: String) : AttendanceResult()
}
