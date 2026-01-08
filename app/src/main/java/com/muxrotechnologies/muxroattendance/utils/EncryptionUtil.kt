package com.muxrotechnologies.muxroattendance.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256 GCM encryption utility for face embeddings and sensitive data
 * Fully offline, no key management service required
 */
object EncryptionUtil {
    
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 256
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    
    /**
     * Generate a secure random AES key
     */
    fun generateKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(KEY_SIZE, SecureRandom())
        return keyGen.generateKey()
    }
    
    /**
     * Derive a key from a passphrase (for database encryption)
     */
    fun deriveKeyFromPassphrase(passphrase: String, salt: ByteArray = ByteArray(16)): SecretKey {
        // Simple key derivation using SHA-256
        // In production, use PBKDF2 or Argon2
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val keyBytes = digest.digest(passphrase.toByteArray())
        return SecretKeySpec(keyBytes, "AES")
    }
    
    /**
     * Encrypt data using AES-256-GCM
     * @param data Raw data to encrypt
     * @param key Secret key
     * @return Base64 encoded encrypted data with IV prepended
     */
    fun encrypt(data: ByteArray, key: SecretKey): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        
        // Check if this is an Android KeyStore key
        val isKeystoreKey = key.javaClass.name.contains("android.security.keystore")
        
        if (isKeystoreKey) {
            // For KeyStore keys, let the system generate the IV
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(data)
            val iv = cipher.iv
            
            // Prepend IV to encrypted data
            val combined = iv + encrypted
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } else {
            // For regular keys, generate random IV
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
            
            val encrypted = cipher.doFinal(data)
            
            // Prepend IV to encrypted data
            val combined = iv + encrypted
            
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }
    }
    
    /**
     * Encrypt float array (face embedding)
     */
    fun encryptFloatArray(floatArray: FloatArray, key: SecretKey): String {
        val byteArray = floatArrayToByteArray(floatArray)
        return encrypt(byteArray, key)
    }
    
    /**
     * Decrypt data using AES-256-GCM
     * @param encryptedBase64 Base64 encoded encrypted data with IV
     * @param key Secret key
     * @return Decrypted data
     */
    fun decrypt(encryptedBase64: String, key: SecretKey): ByteArray {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        
        // Extract IV and encrypted data
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        
        return cipher.doFinal(encrypted)
    }
    
    /**
     * Decrypt to float array (face embedding)
     */
    fun decryptToFloatArray(encryptedBase64: String, key: SecretKey): FloatArray {
        val byteArray = decrypt(encryptedBase64, key)
        return byteArrayToFloatArray(byteArray)
    }
    
    /**
     * Hash a string using SHA-256 (for PIN storage)
     */
    fun hashSHA256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }
    
    /**
     * Verify a hashed value
     */
    fun verifyHash(input: String, hash: String): Boolean {
        return hashSHA256(input) == hash
    }
    
    /**
     * Convert float array to byte array
     */
    private fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
        val byteArray = ByteArray(floatArray.size * 4)
        var offset = 0
        for (float in floatArray) {
            val bits = java.lang.Float.floatToIntBits(float)
            byteArray[offset++] = (bits shr 24).toByte()
            byteArray[offset++] = (bits shr 16).toByte()
            byteArray[offset++] = (bits shr 8).toByte()
            byteArray[offset++] = bits.toByte()
        }
        return byteArray
    }
    
    /**
     * Convert byte array to float array
     */
    private fun byteArrayToFloatArray(byteArray: ByteArray): FloatArray {
        val floatArray = FloatArray(byteArray.size / 4)
        var offset = 0
        for (i in floatArray.indices) {
            val bits = ((byteArray[offset++].toInt() and 0xFF) shl 24) or
                       ((byteArray[offset++].toInt() and 0xFF) shl 16) or
                       ((byteArray[offset++].toInt() and 0xFF) shl 8) or
                       (byteArray[offset++].toInt() and 0xFF)
            floatArray[i] = java.lang.Float.intBitsToFloat(bits)
        }
        return floatArray
    }
    
    /**
     * Generate a secure device-bound encryption key
     * Uses Android ID as salt
     */
    fun generateDeviceBoundKey(androidId: String): SecretKey {
        val salt = hashSHA256(androidId).toByteArray()
        val combinedSeed = (androidId + System.currentTimeMillis()).toByteArray()
        
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val keyBytes = digest.digest(combinedSeed)
        
        return SecretKeySpec(keyBytes, "AES")
    }
}
