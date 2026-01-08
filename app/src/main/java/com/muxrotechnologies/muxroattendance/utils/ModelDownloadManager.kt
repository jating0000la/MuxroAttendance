package com.muxrotechnologies.muxroattendance.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages ML model downloads on first app launch
 * After initial download, app works completely offline
 */
object ModelDownloadManager {
    
    private const val TAG = "ModelDownloadManager"
    
    // Model URLs - Using MediaPipe's official models
    private const val FACE_DETECTION_URL = 
        "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite"
    
    private const val FACE_LANDMARKER_URL = 
        "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
    
    // MobileFaceNet - Must be bundled in assets/ml_models/
    // Public URLs are unreliable. Bundle the model with your app.
    // Download from: https://github.com/deepinsight/insightface
    // Extract from buffalo_s.zip or buffalo_l.zip and place in assets/ml_models/
    
    // Model file names
    const val FACE_DETECTION_MODEL = "face_detection.tflite"
    const val FACE_LANDMARKER_MODEL = "face_landmarker.task"
    const val MOBILEFACENET_MODEL = "mobilefacenet.tflite"
    
    data class DownloadProgress(
        val fileName: String,
        val progress: Int, // 0-100
        val isComplete: Boolean,
        val error: String? = null
    )
    
    /**
     * Check if all models are downloaded
     */
    fun areModelsDownloaded(context: Context): Boolean {
        val modelsDir = getModelsDirectory(context)
        
        val faceDetection = File(modelsDir, FACE_DETECTION_MODEL)
        val faceLandmarker = File(modelsDir, FACE_LANDMARKER_MODEL)
        val mobileFaceNet = File(modelsDir, MOBILEFACENET_MODEL)
        
        return faceDetection.exists() && 
               faceLandmarker.exists() && 
               mobileFaceNet.exists() &&
               faceDetection.length() > 0 &&
               faceLandmarker.length() > 0 &&
               mobileFaceNet.length() > 0
    }
    
    /**
     * Get the directory where models are stored
     */
    fun getModelsDirectory(context: Context): File {
        val modelsDir = File(context.filesDir, "ml_models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return modelsDir
    }
    
    /**
     * Delete all downloaded models to force re-download
     */
    fun clearDownloadedModels(context: Context): Boolean {
        return try {
            val modelsDir = getModelsDirectory(context)
            val faceDetection = File(modelsDir, FACE_DETECTION_MODEL)
            val faceLandmarker = File(modelsDir, FACE_LANDMARKER_MODEL)
            val mobileFaceNet = File(modelsDir, MOBILEFACENET_MODEL)
            
            faceDetection.delete()
            faceLandmarker.delete()
            mobileFaceNet.delete()
            
            Log.d(TAG, "Cleared all downloaded models")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing models", e)
            false
        }
    }
    
    /**
     * Download all required models
     */
    suspend fun downloadAllModels(
        context: Context,
        onProgress: (DownloadProgress) -> Unit
    ): kotlin.Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check storage before download
            if (!StorageManager.canDownloadModels(context)) {
                return@withContext kotlin.Result.failure(
                    java.io.IOException(
                        "Insufficient storage (${StorageManager.getAvailableMB(context)} MB free). " +
                        "Need at least 60MB to download models."
                    )
                )
            }
            
            val modelsDir = getModelsDirectory(context)
            
            // Download Face Detection Model
            onProgress(DownloadProgress(FACE_DETECTION_MODEL, 0, false))
            try {
                downloadFile(
                    FACE_DETECTION_URL,
                    File(modelsDir, FACE_DETECTION_MODEL)
                ) { progress ->
                    onProgress(DownloadProgress(FACE_DETECTION_MODEL, progress, false))
                }
                onProgress(DownloadProgress(FACE_DETECTION_MODEL, 100, true))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download $FACE_DETECTION_MODEL from $FACE_DETECTION_URL", e)
                throw e
            }
            
            // Download Face Landmarker Model
            onProgress(DownloadProgress(FACE_LANDMARKER_MODEL, 0, false))
            try {
                downloadFile(
                    FACE_LANDMARKER_URL,
                    File(modelsDir, FACE_LANDMARKER_MODEL)
                ) { progress ->
                    onProgress(DownloadProgress(FACE_LANDMARKER_MODEL, progress, false))
                }
                onProgress(DownloadProgress(FACE_LANDMARKER_MODEL, 100, true))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download $FACE_LANDMARKER_MODEL from $FACE_LANDMARKER_URL", e)
                throw e
            }
            
            // Copy MobileFaceNet Model from assets (if bundled)
            onProgress(DownloadProgress(MOBILEFACENET_MODEL, 0, false))
            val mobileFaceNetFile = File(modelsDir, MOBILEFACENET_MODEL)
            if (!mobileFaceNetFile.exists()) {
                try {
                    // Try to copy from assets first
                    context.assets.open("ml_models/$MOBILEFACENET_MODEL").use { input ->
                        mobileFaceNetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied $MOBILEFACENET_MODEL from assets")
                } catch (e: Exception) {
                    Log.e(TAG, "MobileFaceNet model not found in assets. Please bundle it in assets/ml_models/", e)
                    throw Exception("MobileFaceNet model must be bundled with the app. Download from InsightFace and place in assets/ml_models/")
                }
            }
            onProgress(DownloadProgress(MOBILEFACENET_MODEL, 100, true))
            
            Log.d(TAG, "All models downloaded successfully")
            return@withContext kotlin.Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading models", e)
            onProgress(DownloadProgress("", 0, false, e.message))
            return@withContext kotlin.Result.failure(e)
        }
    }
    
    /**
     * Download a single file from URL
     */
    private fun downloadFile(
        urlString: String,
        outputFile: File,
        onProgress: (Int) -> Unit
    ) {
        var connection: HttpURLConnection? = null
        var output: FileOutputStream? = null
        
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000 // 30 seconds
            connection.readTimeout = 30000
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
            }
            
            val fileLength = connection.contentLength
            val input = connection.inputStream
            output = FileOutputStream(outputFile)
            
            val buffer = ByteArray(4096)
            var total = 0L
            var count: Int
            
            while (input.read(buffer).also { count = it } != -1) {
                total += count
                output.write(buffer, 0, count)
                
                if (fileLength > 0) {
                    val progress = ((total * 100) / fileLength).toInt()
                    onProgress(progress)
                }
            }
            
            output.flush()
            Log.d(TAG, "Downloaded: ${outputFile.name} (${total / 1024} KB)")
            
        } finally {
            output?.close()
            connection?.disconnect()
        }
    }
    
    /**
     * Delete all downloaded models (for cleanup/reset)
     */
    fun deleteAllModels(context: Context) {
        val modelsDir = getModelsDirectory(context)
        modelsDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "All models deleted")
    }
    
    /**
     * Get model file path
     */
    fun getModelPath(context: Context, modelName: String): String {
        return File(getModelsDirectory(context), modelName).absolutePath
    }
}
