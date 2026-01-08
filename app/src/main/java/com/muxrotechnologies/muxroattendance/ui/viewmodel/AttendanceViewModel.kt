package com.muxrotechnologies.muxroattendance.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayOutputStream
import com.muxrotechnologies.muxroattendance.AttendanceApplication
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceLog
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceType
import com.muxrotechnologies.muxroattendance.data.entity.User
import com.muxrotechnologies.muxroattendance.ml.AttendanceResult
import com.muxrotechnologies.muxroattendance.ml.FaceRecognitionPipeline
import com.muxrotechnologies.muxroattendance.utils.StorageManager
import com.muxrotechnologies.muxroattendance.utils.SecurityUtil
import com.muxrotechnologies.muxroattendance.utils.DecryptedEmbeddingCache
import com.muxrotechnologies.muxroattendance.utils.AuditHashUtil
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceAudit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LivenessState(
    val hasBlinkDetected: Boolean = false,
    val hasSmileDetected: Boolean = false,
    val hasHeadTurnDetected: Boolean = false,
    val hasBlinked: Boolean = false,
    val hasSmiled: Boolean = false,
    val hasTurnedHead: Boolean = false
)

data class AttendanceUiState(
    val isProcessing: Boolean = false,
    val isInitialized: Boolean = false,
    val recognizedUserName: String? = null,
    val recognizedUser: User? = null,
    val confidence: Float? = null,
    val message: String? = null,
    val isError: Boolean = false,
    val error: String? = null,
    val faceRect: Rect? = null,
    val isFaceDetected: Boolean = false,
    val isFaceAligned: Boolean = false,
    val livenessState: LivenessState = LivenessState(),
    val faceBitmap: Bitmap? = null,
    val autoCapturing: Boolean = false,
    val captureCountdown: Int = 0
)

class AttendanceViewModel : ViewModel() {
    
    private val app = AttendanceApplication.getInstance()
    private val userRepository = app.userRepository
    private val attendanceRepository = app.attendanceRepository
    private val configRepository = app.configRepository
    private val auditDao = app.database.attendanceAuditDao()
    
    private lateinit var pipeline: FaceRecognitionPipeline
    
    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()
    
    private var currentAttendanceType: AttendanceType = AttendanceType.CHECK_IN
    
    // Decrypted embeddings cache for faster matching
    private val decryptedEmbeddingCache = DecryptedEmbeddingCache()
    
    // Frame skipping for performance optimization and battery life
    private var frameCounter = 0
    private val frameSkipInterval = 1 // Process every 2nd frame - balanced for speed and efficiency
    
    private var faceAlignedFrameCount = 0

    // Production-ready cooldowns to prevent duplicate marking and abuse
    private var lastProcessTime = 0L
    private var lastAttemptSuccess = true
    private val processCooldown = 3000L // 3 seconds between successful attempts (prevent duplicates)
    private val failedCooldown = 1000L // 1 second retry on failure
    
    // Track last recognized user to prevent immediate re-marking
    private var lastRecognizedUserId: Long? = null
    private var lastRecognizedTime: Long = 0L
    private val duplicateMarkingWindow = 30000L // 30 seconds - prevent same person marking twice quickly
    
    // Store last detected face to reuse in processAttendance
    private var lastDetectedFace: com.muxrotechnologies.muxroattendance.ml.DetectedFace? = null
    
    // Store preview dimensions for coordinate transformation
    private var previewWidth = 0
    private var previewHeight = 0
    private var imageWidth = 0
    private var imageHeight = 0
    
    init {
        initializePipeline()
        checkStorageHealth()
    }
    
    /**
     * Check storage health and warn user if low
     * Informs about auto-cleanup and external storage options
     */
    private fun checkStorageHealth() {
        viewModelScope.launch {
            try {
                val health = StorageManager.getStorageHealth(app.applicationContext)
                val hasExternalStorage = StorageManager.hasExternalStorageSpace()
                val autoCleanupEnabled = StorageManager.isAutoCleanupEnabled()
                
                when (health) {
                    StorageManager.StorageHealth.CRITICAL -> {
                        val message = buildString {
                            append("CRITICAL: Storage almost full (${StorageManager.getAvailableMB(app)} MB). ")
                            if (autoCleanupEnabled) {
                                append("Old logs will be auto-deleted. ")
                            }
                            if (hasExternalStorage) {
                                append("External storage available (${StorageManager.getExternalStorageAvailableBytes() / (1024 * 1024)} MB). ")
                            }
                            append("Please free up space immediately!")
                        }
                        _uiState.value = _uiState.value.copy(
                            isError = true,
                            message = message
                        )
                    }
                    StorageManager.StorageHealth.WARNING -> {
                        val message = buildString {
                            append("Warning: Low storage (${StorageManager.getAvailableMB(app)} MB). ")
                            if (autoCleanupEnabled) {
                                append("Auto-cleanup enabled for old logs. ")
                            }
                            if (hasExternalStorage) {
                                append("External storage available. ")
                            }
                            append("Consider freeing up space.")
                        }
                        _uiState.value = _uiState.value.copy(
                            message = message
                        )
                    }
                    else -> { /* Storage healthy */ }
                }
            } catch (e: Exception) {
                android.util.Log.e("AttendanceViewModel", "Error checking storage", e)
            }
        }
    }
    
    private fun initializePipeline() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isProcessing = true,
                    message = "Initializing face recognition..."
                )
                
                pipeline = FaceRecognitionPipeline(
                    app.applicationContext,
                    app.getEncryptionKey()
                )
                
                val success = pipeline.initialize()
                
                _uiState.value = if (success) {
                    AttendanceUiState(
                        isInitialized = true,
                        message = "Ready to scan face"
                    )
                } else {
                    AttendanceUiState(
                        isError = true,
                        message = "Failed to initialize. Check ML models."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = AttendanceUiState(
                    isError = true,
                    message = "Initialization error: ${e.message}"
                )
            }
        }
    }
    
    fun processAttendance(bitmap: Bitmap, type: AttendanceType) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isProcessing = true,
                    message = "Analyzing face..."
                )
                
                // Get or create decrypted embeddings cache
                val decryptedEmbeddings = decryptedEmbeddingCache.getCachedEmbeddings() ?: run {
                    val storedEmbeddings = userRepository.getAllFaceEmbeddings()
                    
                    if (storedEmbeddings.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            isError = true,
                            message = "No enrolled users. Please enroll first."
                        )
                        return@launch
                    }
                    
                    // Decrypt and cache embeddings
                    val decrypted = pipeline.decryptEmbeddings(storedEmbeddings)
                    decryptedEmbeddingCache.setCachedEmbeddings(decrypted)
                    decrypted
                }
                
                val threshold = configRepository.getSimilarityThreshold()
                
                // Use last detected face to skip face detection
                val result = pipeline.processAttendanceOptimized(
                    bitmap,
                    lastDetectedFace,
                    decryptedEmbeddings,
                    requireLiveness = false, // Disabled for speed
                    threshold
                )
                
                when (result) {
                    is AttendanceResult.Recognized -> {
                        // Production check: prevent duplicate marking of same person
                        val timeSinceLastMark = System.currentTimeMillis() - lastRecognizedTime
                        if (result.userId == lastRecognizedUserId && timeSinceLastMark < duplicateMarkingWindow) {
                            _uiState.value = _uiState.value.copy(
                                isProcessing = false,
                                message = "Already marked. Please wait ${(duplicateMarkingWindow - timeSinceLastMark) / 1000} seconds.",
                                isError = true
                            )
                            return@launch
                        }
                        
                        // Auto-determine check-in or check-out based on today's records
                        val autoType = determineAttendanceType(result.userId)
                        
                        // Create audit log
                        logAuditSuccess(bitmap, result.userId, autoType, result.confidence)
                        
                        // Update tracking
                        lastRecognizedUserId = result.userId
                        lastRecognizedTime = System.currentTimeMillis()
                        lastAttemptSuccess = true
                        
                        handleRecognized(result.userId, result.confidence, autoType)
                    }
                    is AttendanceResult.NotRecognized -> {
                        // Log failed attempt
                        logAuditFailure(bitmap, -1, type, 0f, "Face not recognized")
                        
                        lastAttemptSuccess = false
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            isError = true,
                            message = "Face not recognized. Please try again or enroll."
                        )
                    }
                    is AttendanceResult.Error -> {
                        // Log error attempt
                        logAuditFailure(bitmap, -1, type, 0f, result.message)
                        
                        lastAttemptSuccess = false
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            isError = true,
                            message = result.message
                        )
                    }
                }
            } catch (e: Exception) {
                // Log exception
                logAuditFailure(bitmap, -1, type, 0f, "Exception: ${e.message}")
                
                // Check if storage-related error
                val isStorageError = e is java.io.IOException && 
                                    (e.message?.contains("storage", ignoreCase = true) == true ||
                                     e.message?.contains("space", ignoreCase = true) == true)
                
                val errorMessage = if (isStorageError) {
                    "Storage full! Please free up space to record attendance. (${StorageManager.getAvailableMB(app)} MB free)"
                } else {
                    "Processing error: ${e.message}"
                }
                
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isError = true,
                    message = errorMessage
                )
            }
        }
    }
    
    /**
     * Log successful attendance to audit trail
     */
    private fun logAuditSuccess(bitmap: Bitmap, userId: Long, type: AttendanceType, confidence: Float) {
        viewModelScope.launch {
            try {
                val imageHash = AuditHashUtil.createImageHash(bitmap, lastDetectedFace?.toRect())
                val deviceId = SecurityUtil.getDeviceId(app.applicationContext)
                
                val audit = AttendanceAudit(
                    userId = userId,
                    type = type,
                    confidence = confidence,
                    imageHash = imageHash,
                    deviceId = deviceId,
                    success = true
                )
                
                auditDao.insertAudit(audit)
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Failed to log audit", e)
            }
        }
    }
    
    /**
     * Log failed attendance attempt to audit trail
     */
    private fun logAuditFailure(bitmap: Bitmap?, userId: Long, type: AttendanceType, confidence: Float, errorMessage: String) {
        viewModelScope.launch {
            try {
                val imageHash = bitmap?.let { 
                    AuditHashUtil.createImageHash(it, lastDetectedFace?.toRect())
                } ?: "no_image"
                val deviceId = SecurityUtil.getDeviceId(app.applicationContext)
                
                val audit = AttendanceAudit(
                    userId = userId,
                    type = type,
                    confidence = confidence,
                    imageHash = imageHash,
                    deviceId = deviceId,
                    success = false,
                    errorMessage = errorMessage
                )
                
                auditDao.insertAudit(audit)
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Failed to log audit", e)
            }
        }
    }
    
    /**
     * Extension function to convert DetectedFace to Rect
     */
    private fun com.muxrotechnologies.muxroattendance.ml.DetectedFace.toRect(): android.graphics.Rect {
        return android.graphics.Rect(left, top, right, bottom)
    }
    
    /**
     * Determine if this should be check-in or check-out
     * First scan of the day = CHECK_IN
     * Any subsequent scan = CHECK_OUT
     */
    private suspend fun determineAttendanceType(userId: Long): AttendanceType {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val todayEnd = todayStart + 24 * 60 * 60 * 1000
        
        val todayRecords = attendanceRepository.getAttendanceInRange(todayStart, todayEnd)
            .filter { it.userId == userId }
        
        // If no records today, it's check-in
        // If already has records, it's check-out (will update the last one)
        return if (todayRecords.isEmpty()) AttendanceType.CHECK_IN else AttendanceType.CHECK_OUT
    }
    
    private suspend fun handleRecognized(
        userId: Long,
        confidence: Float,
        type: AttendanceType
    ) {
        val canMark = attendanceRepository.canMarkAttendance(
            userId,
            type,
            configRepository.getDuplicateAttendanceWindow()
        )
        
        if (!canMark) {
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                isError = true,
                message = "Duplicate attendance detected. Please wait before trying again."
            )
            return
        }
        
        val user = userRepository.getUserById(userId)
        
        if (user == null) {
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                isError = true,
                message = "User record not found"
            )
            return
        }
        
        val log = AttendanceLog(
            userId = userId,
            type = type,
            confidenceScore = confidence,
            deviceId = SecurityUtil.getDeviceId(app.applicationContext)
        )
        
        attendanceRepository.markAttendance(log)
        
        _uiState.value = _uiState.value.copy(
            isProcessing = false,
            recognizedUserName = user.name,
            recognizedUser = user,
            confidence = confidence,
            message = "✓ ${user.name} - ${type.name} marked successfully!",
            isError = false,
            error = null
        )
    }
    
    fun initialize(context: Context) {
        // Already initialized in init block
    }
    
    fun setAttendanceType(type: AttendanceType) {
        currentAttendanceType = type
        faceAlignedFrameCount = 0 // Reset countdown when type changes
    }
    
    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(previewView.context)
        
        // Store preview dimensions for coordinate transformation
        previewView.post {
            previewWidth = previewView.width
            previewHeight = previewView.height
        }
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // Preview use case
                val preview = androidx.camera.core.Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                
                // Image analysis use case for face detection
                // Production resolution: 480x360 - balanced quality and speed
                val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(480, 360))
                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(
                            java.util.concurrent.Executors.newSingleThreadExecutor()
                        ) { imageProxy ->
                            processImage(imageProxy)
                        }
                    }
                
                // Select front camera
                val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
                
                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()
                
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    previewView.context as androidx.lifecycle.LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.d("AttendanceViewModel", "Camera started successfully")
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error starting camera", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to start camera: ${e.message}"
                )
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(previewView.context))
    }
    
    private fun processImage(imageProxy: ImageProxy) {
        try {
            // Frame skipping for performance - process every Nth frame
            frameCounter++
            if (frameCounter <= frameSkipInterval) {
                return
            }
            frameCounter = 0
            
            // Production checks: ensure system is ready and not already processing
            if (!_uiState.value.isProcessing && ::pipeline.isInitialized && pipeline.isReady()) {
                val bitmap = imageProxy.toBitmap()
                if (bitmap != null) {
                    // Store image dimensions for coordinate transformation
                    if (imageWidth == 0 || imageHeight == 0) {
                        imageWidth = bitmap.width
                        imageHeight = bitmap.height
                    }
                    
                    // Detect face in real-time (synchronous for better performance)
                    val detectedFace = pipeline.detectFace(bitmap)
                    
                    // Store detected face for reuse in processAttendance
                    lastDetectedFace = detectedFace
                    
                    // Production face detection - balanced requirements for reliability
                    val isFaceDetected = detectedFace?.let {
                        it.width() > 70 && it.height() > 70 && it.confidence > 0.5f
                    } ?: false
                    
                    // Process attendance with validation and rate limiting
                    val currentTime = System.currentTimeMillis()
                    val cooldown = if (lastAttemptSuccess) processCooldown else failedCooldown
                    
                    // Production check: prevent processing if still cooling down
                    val canProcess = isFaceDetected && 
                                   (currentTime - lastProcessTime) > cooldown &&
                                   !_uiState.value.isProcessing
                    
                    if (canProcess) {
                        lastProcessTime = currentTime
                        // Create a copy of the bitmap to avoid recycling issues
                        val bitmapCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        viewModelScope.launch {
                            try {
                                processAttendance(bitmapCopy, currentAttendanceType)
                            } catch (e: Exception) {
                                Log.e("AttendanceViewModel", "Error processing attendance", e)
                                _uiState.value = _uiState.value.copy(
                                    isProcessing = false,
                                    isError = true,
                                    message = "Processing error. Please try again."
                                )
                            } finally {
                                // Production: ensure bitmap cleanup happens
                                kotlinx.coroutines.delay(150)
                                try {
                                    if (!bitmapCopy.isRecycled) {
                                        bitmapCopy.recycle()
                                    }
                                } catch (e: Exception) {
                                    Log.e("AttendanceViewModel", "Error recycling bitmap", e)
                                }
                            }
                        }
                    }
                    
                    // Don't recycle the current bitmap - it might still be in use
                    // Just replace the reference and let GC handle the old one
                    _uiState.value.faceBitmap?.let { oldBitmap ->
                        // Only recycle very old bitmaps that are definitely not in use
                        if (oldBitmap != bitmap && !oldBitmap.isRecycled) {
                            // Schedule recycling after a delay to avoid race conditions
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(200)
                                if (!oldBitmap.isRecycled) {
                                    oldBitmap.recycle()
                                }
                            }
                        }
                    }
                    
                    // Transform face rect coordinates to preview coordinates
                    val transformedRect = detectedFace?.let {
                        transformFaceRect(it, previewWidth, previewHeight, imageWidth, imageHeight)
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        faceBitmap = bitmap,
                        isFaceDetected = isFaceDetected,
                        faceRect = transformedRect,
                        isFaceAligned = isFaceDetected,
                        autoCapturing = false,
                        captureCountdown = 0,
                        message = when {
                            !isFaceDetected -> "No face detected"
                            else -> "Face detected"
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AttendanceViewModel", "Error processing frame", e)
        } finally {
            imageProxy.close()
        }
    }
    
    private fun ImageProxy.toBitmap(): Bitmap? {
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("AttendanceViewModel", "Error converting image", e)
            null
        }
    }
    
    fun processFrame(type: AttendanceType, faceBitmap: Bitmap) {
        // Simplified version - just update UI
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isFaceDetected = true,
                faceBitmap = faceBitmap
            )
        }
    }
    
    suspend fun getAttendanceByDateRange(startTime: Long, endTime: Long): List<AttendanceLog> {
        return attendanceRepository.getAttendanceInRange(startTime, endTime)
    }
    
    suspend fun getAllAttendance(): List<AttendanceLog> {
        return attendanceRepository.getAllAttendance()
    }
    
    fun delete(log: AttendanceLog) {
        viewModelScope.launch {
            attendanceRepository.deleteAttendance(log)
        }
    }
    
    fun resetState() {
        _uiState.value = AttendanceUiState(
            isInitialized = true,
            message = "Ready to scan face"
        )
    }
    
    /**
     * Invalidate decrypted embedding cache (call when users are added/updated)
     */
    fun invalidateEmbeddingCache() {
        decryptedEmbeddingCache.invalidate()
    }
    
    /**
     * Transform face rectangle from image coordinates to preview coordinates
     * Handles scaling, aspect ratio differences, and front camera mirroring
     */
    private fun transformFaceRect(
        detectedFace: com.muxrotechnologies.muxroattendance.ml.DetectedFace,
        previewWidth: Int,
        previewHeight: Int,
        imageWidth: Int,
        imageHeight: Int
    ): Rect? {
        if (previewWidth == 0 || previewHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            return null
        }
        
        // Calculate scale factors - use max to fill the preview (CenterCrop behavior)
        val scaleX = previewWidth.toFloat() / imageWidth.toFloat()
        val scaleY = previewHeight.toFloat() / imageHeight.toFloat()
        val scale = kotlin.math.max(scaleX, scaleY)
        
        // Calculate offsets to center the scaled image
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val offsetX = (previewWidth - scaledWidth) / 2f
        val offsetY = (previewHeight - scaledHeight) / 2f
        
        // Transform coordinates with front camera mirroring (flip horizontally)
        // Front camera image is mirrored, so we need to flip X coordinates
        val mirroredLeft = imageWidth - detectedFace.right
        val mirroredRight = imageWidth - detectedFace.left
        
        val left = (mirroredLeft * scale + offsetX).toInt()
        val top = (detectedFace.top * scale + offsetY).toInt()
        val right = (mirroredRight * scale + offsetX).toInt()
        val bottom = (detectedFace.bottom * scale + offsetY).toInt()
        
        // Add small padding to make the box fit the face better
        val padding = ((right - left) * 0.05f).toInt()
        
        return Rect(
            (left - padding).coerceAtLeast(0),
            (top - padding).coerceAtLeast(0),
            (right + padding).coerceAtMost(previewWidth),
            (bottom + padding).coerceAtMost(previewHeight)
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        if (::pipeline.isInitialized) {
            pipeline.release()
        }
        decryptedEmbeddingCache.invalidate()
    }
}
