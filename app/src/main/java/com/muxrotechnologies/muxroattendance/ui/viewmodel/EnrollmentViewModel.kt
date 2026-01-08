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
import com.muxrotechnologies.muxroattendance.data.entity.FaceEmbedding
import com.muxrotechnologies.muxroattendance.data.entity.User
import com.muxrotechnologies.muxroattendance.ml.EnrollmentResult
import com.muxrotechnologies.muxroattendance.ml.FaceRecognitionPipeline
import com.muxrotechnologies.muxroattendance.utils.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EnrollmentUiState(
    val isProcessing: Boolean = false,
    val isInitialized: Boolean = false,
    val currentSample: Int = 0,
    val totalSamples: Int = 5,
    val capturedSamples: List<String> = emptyList(),
    val averageQuality: Float = 0f,
    val message: String? = null,
    val isError: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false,
    val faceBitmap: Bitmap? = null,
    val imageQuality: Float = 0f,
    val faceRect: Rect? = null,
    val isFaceDetected: Boolean = false,
    val isFaceAligned: Boolean = false,
    val isEnrollmentComplete: Boolean = false
)

class EnrollmentViewModel : ViewModel() {
    
    private val app = AttendanceApplication.getInstance()
    private val userRepository = app.userRepository
    
    private lateinit var pipeline: FaceRecognitionPipeline
    private val capturedEmbeddings = mutableListOf<Pair<String, Float>>()
    
    private val _uiState = MutableStateFlow(EnrollmentUiState())
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()
    
    // Store preview dimensions for coordinate transformation
    private var previewWidth = 0
    private var previewHeight = 0
    private var imageWidth = 0
    private var imageHeight = 0
    
    init {
        initializePipeline()
    }
    
    private fun initializePipeline() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isProcessing = true,
                    message = "Initializing..."
                )
                
                pipeline = FaceRecognitionPipeline(
                    app.applicationContext,
                    app.getEncryptionKey()
                )
                
                val success = pipeline.initialize()
                
                _uiState.value = if (success) {
                    EnrollmentUiState(
                        isInitialized = true,
                        message = "Position your face in the frame"
                    )
                } else {
                    EnrollmentUiState(
                        isError = true,
                        message = "Failed to initialize. Check ML models."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = EnrollmentUiState(
                    isError = true,
                    message = "Initialization error: ${e.message}"
                )
            }
        }
    }
    
    fun captureSample(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                Log.d("EnrollmentViewModel", "Starting capture for sample ${capturedEmbeddings.size + 1}")
                
                _uiState.value = _uiState.value.copy(
                    isProcessing = true,
                    message = "Processing face sample..."
                )
                
                val result = pipeline.processEnrollment(bitmap)
                
                when (result) {
                    is EnrollmentResult.Success -> {
                        capturedEmbeddings.add(
                            Pair(result.embedding, result.qualityScore)
                        )
                        
                        val currentSample = capturedEmbeddings.size
                        val avgQuality = capturedEmbeddings
                            .map { it.second }
                            .average()
                            .toFloat()
                        
                        Log.d("EnrollmentViewModel", "Sample captured successfully. Total samples: $currentSample")
                        
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            currentSample = currentSample,
                            averageQuality = avgQuality,
                            capturedSamples = capturedEmbeddings.map { it.first },
                            message = if (currentSample < _uiState.value.totalSamples) {
                                "✓ Sample $currentSample captured (Quality: ${avgQuality.toInt()}%). ${_uiState.value.totalSamples - currentSample} remaining."
                            } else {
                                "All samples captured! Ready to save user."
                            },
                            isError = false
                        )
                    }
                    is EnrollmentResult.Error -> {
                        Log.e("EnrollmentViewModel", "Capture failed: ${result.message}")
                        
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            isError = true,
                            message = result.message
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("EnrollmentViewModel", "Capture error", e)
                
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isError = true,
                    message = "Capture error: ${e.message}"
                )
            }
        }
    }
    
    fun enrollUser(userId: String, name: String, department: String?) {
        viewModelScope.launch {
            try {
                if (capturedEmbeddings.size < _uiState.value.totalSamples) {
                    _uiState.value = _uiState.value.copy(
                        isError = true,
                        message = "Please capture all ${_uiState.value.totalSamples} samples first"
                    )
                    return@launch
                }
                
                // Check storage before enrollment
                if (!StorageManager.canEnrollUser(app.applicationContext, capturedEmbeddings.size)) {
                    _uiState.value = _uiState.value.copy(
                        isError = true,
                        message = "Insufficient storage (${StorageManager.getAvailableMB(app)} MB free). " +
                                "Need at least ${StorageManager.estimateEnrollmentSpace(capturedEmbeddings.size) / (1024 * 1024)} MB to enroll user. " +
                                "Please free up space and try again."
                    )
                    return@launch
                }
                
                _uiState.value = _uiState.value.copy(
                    isProcessing = true,
                    message = "Saving user data..."
                )
                
                val user = User(
                    userId = userId,
                    name = name,
                    department = department
                )
                
                val insertedUserId = userRepository.insertUser(user)
                
                val embeddings = capturedEmbeddings.mapIndexed { index, (embedding, quality) ->
                    FaceEmbedding(
                        userId = insertedUserId,
                        embeddingEncrypted = embedding,
                        sampleNumber = index + 1,
                        qualityScore = quality
                    )
                }
                
                userRepository.addFaceEmbeddings(embeddings)
                
                // Invalidate cache in other ViewModels
                app.userRepository.clearCache()
                
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isComplete = true,
                    isEnrollmentComplete = true,
                    message = "✓ ${name} enrolled successfully!"
                )
            } catch (e: Exception) {
                // Check if storage-related error
                val isStorageError = e is java.io.IOException && 
                                    (e.message?.contains("storage", ignoreCase = true) == true ||
                                     e.message?.contains("space", ignoreCase = true) == true)
                
                val errorMessage = if (isStorageError) {
                    "Storage full! Cannot enroll user. (${StorageManager.getAvailableMB(app)} MB free) " +
                    "Please free up at least 2MB and try again."
                } else {
                    "Enrollment failed: ${e.message}"
                }
                
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isError = true,
                    message = errorMessage
                )
            }
        }
    }
    
    fun resetEnrollment() {
        capturedEmbeddings.clear()
        _uiState.value = EnrollmentUiState(
            isInitialized = true,
            message = "Position your face in the frame"
        )
    }
    
    fun initialize(context: Context) {
        // Already initialized in init block
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
                val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(
                            androidx.core.content.ContextCompat.getMainExecutor(previewView.context)
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
                
                Log.d("EnrollmentViewModel", "Camera started successfully")
            } catch (e: Exception) {
                Log.e("EnrollmentViewModel", "Error starting camera", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to start camera: ${e.message}"
                )
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(previewView.context))
    }
    
    private fun processImage(imageProxy: ImageProxy) {
        try {
            if (!_uiState.value.isProcessing && 
                _uiState.value.currentSample < _uiState.value.totalSamples && 
                ::pipeline.isInitialized && pipeline.isReady()) {
                
                val bitmap = imageProxy.toBitmap()
                if (bitmap != null) {
                    // Store image dimensions for coordinate transformation
                    if (imageWidth == 0 || imageHeight == 0) {
                        imageWidth = bitmap.width
                        imageHeight = bitmap.height
                    }
                    
                    // Detect face in real-time
                    val detectedFace = pipeline.detectFace(bitmap)
                    val isAligned = detectedFace?.let {
                        it.width() > 80 && it.height() > 80 && it.confidence > 0.5f
                    } ?: false
                    
                    // Transform face rect coordinates to preview coordinates
                    val transformedRect = detectedFace?.let {
                        transformFaceRect(it, previewWidth, previewHeight, imageWidth, imageHeight)
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        faceBitmap = bitmap,
                        isFaceDetected = detectedFace != null,
                        faceRect = transformedRect,
                        isFaceAligned = isAligned,
                        imageQuality = detectedFace?.confidence ?: 0f
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("EnrollmentViewModel", "Error processing frame", e)
        } finally {
            imageProxy.close()
        }
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
            Log.e("EnrollmentViewModel", "Error converting image", e)
            null
        }
    }
    
    fun captureCurrentFrame() {
        val bitmap = _uiState.value.faceBitmap
        Log.d("EnrollmentViewModel", "captureCurrentFrame called. Bitmap available: ${bitmap != null}")
        if (bitmap != null) {
            captureSample(bitmap)
        } else {
            Log.e("EnrollmentViewModel", "No bitmap available to capture")
            _uiState.value = _uiState.value.copy(
                error = "No image available. Please ensure camera is working.",
                isError = true
            )
        }
    }
    
    fun updateError(message: String?) {
        _uiState.value = _uiState.value.copy(
            error = message,
            isError = message != null
        )
    }
    
    fun updateUserDetails(name: String, email: String, department: String, employeeId: String) {
        // Store for later enrollment
    }
    
    override fun onCleared() {
        super.onCleared()
        if (::pipeline.isInitialized) {
            pipeline.release()
        }
    }
}
