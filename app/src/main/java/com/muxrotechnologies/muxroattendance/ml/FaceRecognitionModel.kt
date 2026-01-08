package com.muxrotechnologies.muxroattendance.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * MobileFaceNet TFLite model wrapper
 * Generates 128 or 192-dimensional face embeddings
 * Fully offline face recognition engine
 */
class FaceRecognitionModel(
    private val context: Context,
    private val useGpu: Boolean = true
) {
    
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    
    // Model configuration
    private val inputSize = 112 // MobileFaceNet input size
    private val embeddingSize = 192 // Output embedding dimension (this model uses 192)
    
    // Normalization parameters
    private val mean = floatArrayOf(127.5f, 127.5f, 127.5f)
    private val std = floatArrayOf(128.0f, 128.0f, 128.0f)
    
    companion object {
        private const val TAG = "FaceRecognitionModel"
        private const val MODEL_PATH = "ml_models/mobilefacenet.tflite" // Model in assets/ml_models/
    }
    
    /**
     * Load the TFLite model
     */
    fun loadModel(): Boolean {
        return try {
            val options = Interpreter.Options()
            
            // Optimize interpreter options for better performance
            options.setUseNNAPI(true)
            options.setNumThreads(4)
            options.setAllowFp16PrecisionForFp32(true) // Allow FP16 for speed
            options.setAllowBufferHandleOutput(true) // Optimize memory
            
            Log.d(TAG, "Using NNAPI/CPU with FP16 precision")
            
            val model = loadModelFile(MODEL_PATH)
            interpreter = Interpreter(model, options)
            
            Log.d(TAG, "Model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            false
        }
    }
    
    /**
     * Generate face embedding from aligned face image
     * @param faceBitmap Aligned face image (will be resized to 112x112)
     * @return Face embedding as FloatArray (192 dimensions)
     */
    fun generateEmbedding(faceBitmap: Bitmap): FloatArray? {
        if (interpreter == null) {
            Log.e(TAG, "Model not loaded")
            return null
        }
        
        var resizedBitmap: Bitmap? = null
        return try {
            // Preprocess image
            val inputBuffer = preprocessImage(faceBitmap)
            
            // Output buffer for embedding
            val outputBuffer = Array(1) { FloatArray(embeddingSize) }
            
            // Run inference
            interpreter?.run(inputBuffer, outputBuffer)
            
            // Normalize embedding (L2 normalization)
            val embedding = outputBuffer[0]
            normalizeEmbedding(embedding)
            
            embedding
        } catch (e: Exception) {
            Log.e(TAG, "Error generating embedding", e)
            null
        } finally {
            // Clean up temporary bitmap if created
            resizedBitmap?.recycle()
        }
    }
    
    /**
     * Preprocess image for MobileFaceNet
     */
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        // Resize to model input size
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        
        for (pixel in pixels) {
            // Extract RGB values
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)
            
            // Normalize: (pixel - mean) / std
            inputBuffer.putFloat((r - mean[0]) / std[0])
            inputBuffer.putFloat((g - mean[1]) / std[1])
            inputBuffer.putFloat((b - mean[2]) / std[2])
        }
        
        // Clean up temporary bitmap
        resizedBitmap.recycle()
        
        return inputBuffer
    }
    
    /**
     * L2 normalization of embedding
     */
    private fun normalizeEmbedding(embedding: FloatArray) {
        var sumSquares = 0.0f
        for (value in embedding) {
            sumSquares += value * value
        }
        val norm = kotlin.math.sqrt(sumSquares)
        
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }
    }
    
    /**
     * Load TFLite model from assets
     */
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * Close the interpreter and release resources
     */
    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
        Log.d(TAG, "Model resources released")
    }
    
    /**
     * Check if model is loaded
     */
    fun isLoaded(): Boolean = interpreter != null
}
