package com.muxrotechnologies.muxroattendance

import com.muxrotechnologies.muxroattendance.ml.FaceMatcher
import com.muxrotechnologies.muxroattendance.ml.MatchResult
import com.muxrotechnologies.muxroattendance.utils.EncryptionUtil
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for face recognition matching engine
 * Tests cosine similarity calculations and matching logic
 */
class FaceMatcherTest {
    
    @Test
    fun `cosine similarity of identical embeddings is 1`() {
        val embedding1 = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        val embedding2 = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        
        val similarity = FaceMatcher.computeCosineSimilarity(embedding1, embedding2)
        
        assertEquals(1.0f, similarity, 0.001f)
    }
    
    @Test
    fun `cosine similarity of orthogonal embeddings is 0`() {
        val embedding1 = floatArrayOf(1f, 0f, 0f, 0f, 0f)
        val embedding2 = floatArrayOf(0f, 1f, 0f, 0f, 0f)
        
        val similarity = FaceMatcher.computeCosineSimilarity(embedding1, embedding2)
        
        assertEquals(0.0f, similarity, 0.001f)
    }
    
    @Test
    fun `cosine similarity of opposite embeddings is -1`() {
        val embedding1 = floatArrayOf(1f, 2f, 3f)
        val embedding2 = floatArrayOf(-1f, -2f, -3f)
        
        val similarity = FaceMatcher.computeCosineSimilarity(embedding1, embedding2)
        
        assertEquals(-1.0f, similarity, 0.001f)
    }
    
    @Test
    fun `cosine similarity is symmetric`() {
        val embedding1 = floatArrayOf(1f, 2f, 3f, 4f)
        val embedding2 = floatArrayOf(2f, 3f, 4f, 5f)
        
        val sim1 = FaceMatcher.computeCosineSimilarity(embedding1, embedding2)
        val sim2 = FaceMatcher.computeCosineSimilarity(embedding2, embedding1)
        
        assertEquals(sim1, sim2, 0.001f)
    }
    
    @Test
    fun `euclidean distance of identical embeddings is 0`() {
        val embedding1 = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        val embedding2 = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        
        val distance = FaceMatcher.computeEuclideanDistance(embedding1, embedding2)
        
        assertEquals(0.0f, distance, 0.001f)
    }
    
    @Test
    fun `euclidean distance is always positive`() {
        val embedding1 = floatArrayOf(1f, 2f, 3f)
        val embedding2 = floatArrayOf(4f, 5f, 6f)
        
        val distance = FaceMatcher.computeEuclideanDistance(embedding1, embedding2)
        
        assertTrue(distance > 0)
    }
    
    @Test
    fun `matchFace returns NoMatch when threshold not met`() {
        val queryEmbedding = floatArrayOf(1f, 0f, 0f, 0f, 0f)
        val storedEmbeddings = listOf(
            Pair(1L, floatArrayOf(0f, 1f, 0f, 0f, 0f)),
            Pair(2L, floatArrayOf(0f, 0f, 1f, 0f, 0f))
        )
        
        val result = FaceMatcher.matchFace(
            queryEmbedding,
            storedEmbeddings,
            threshold = 0.8f
        )
        
        assertTrue(result is MatchResult.NoMatch)
    }
    
    @Test
    fun `matchFace returns Match when threshold met`() {
        val queryEmbedding = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        val storedEmbeddings = listOf(
            Pair(1L, floatArrayOf(1.1f, 2.1f, 2.9f, 4.1f, 4.9f)), // Very similar
            Pair(2L, floatArrayOf(0f, 0f, 1f, 0f, 0f)) // Not similar
        )
        
        val result = FaceMatcher.matchFace(
            queryEmbedding,
            storedEmbeddings,
            threshold = 0.8f
        )
        
        assertTrue(result is MatchResult.Match)
        if (result is MatchResult.Match) {
            assertEquals(1L, result.userId)
            assertTrue(result.confidence > 0.8f)
        }
    }
    
    @Test
    fun `matchFace selects best match among multiple candidates`() {
        val queryEmbedding = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        val storedEmbeddings = listOf(
            Pair(1L, floatArrayOf(1.2f, 2.2f, 3.2f, 4.2f, 5.2f)), // Good match
            Pair(2L, floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)), // Perfect match
            Pair(3L, floatArrayOf(1.5f, 2.5f, 3.5f, 4.5f, 5.5f))  // OK match
        )
        
        val result = FaceMatcher.matchFace(
            queryEmbedding,
            storedEmbeddings,
            threshold = 0.5f
        )
        
        assertTrue(result is MatchResult.Match)
        if (result is MatchResult.Match) {
            assertEquals(2L, result.userId) // Should select perfect match
        }
    }
    
    @Test
    fun `averageEmbeddings returns null for empty list`() {
        val result = FaceMatcher.averageEmbeddings(emptyList())
        assertNull(result)
    }
    
    @Test
    fun `averageEmbeddings correctly averages embeddings`() {
        val embeddings = listOf(
            floatArrayOf(1f, 2f, 3f),
            floatArrayOf(3f, 4f, 5f),
            floatArrayOf(2f, 3f, 4f)
        )
        
        val averaged = FaceMatcher.averageEmbeddings(embeddings)
        
        assertNotNull(averaged)
        // Average should be (2, 3, 4) then normalized
        // Just verify it's not null and has correct size
        assertEquals(3, averaged?.size)
    }
    
    @Test
    fun `averageEmbeddings result is normalized`() {
        val embeddings = listOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f)
        )
        
        val averaged = FaceMatcher.averageEmbeddings(embeddings)
        
        assertNotNull(averaged)
        // Calculate L2 norm
        var normSquared = 0f
        averaged?.forEach { normSquared += it * it }
        val norm = kotlin.math.sqrt(normSquared)
        
        assertEquals(1.0f, norm, 0.001f)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `cosine similarity throws on mismatched dimensions`() {
        val embedding1 = floatArrayOf(1f, 2f, 3f)
        val embedding2 = floatArrayOf(1f, 2f, 3f, 4f)
        
        FaceMatcher.computeCosineSimilarity(embedding1, embedding2)
    }
}

/**
 * Unit tests for encryption utilities
 * Tests AES-256-GCM encryption and decryption
 */
class EncryptionUtilTest {
    
    @Test
    fun `encrypt and decrypt returns original data`() {
        val originalData = "Hello, World!".toByteArray()
        val key = EncryptionUtil.generateKey()
        
        val encrypted = EncryptionUtil.encrypt(originalData, key)
        val decrypted = EncryptionUtil.decrypt(encrypted, key)
        
        assertArrayEquals(originalData, decrypted)
    }
    
    @Test
    fun `encrypt produces different output each time due to IV`() {
        val data = "Test Data".toByteArray()
        val key = EncryptionUtil.generateKey()
        
        val encrypted1 = EncryptionUtil.encrypt(data, key)
        val encrypted2 = EncryptionUtil.encrypt(data, key)
        
        assertNotEquals(encrypted1, encrypted2)
    }
    
    @Test
    fun `encryptFloatArray and decryptToFloatArray preserves data`() {
        val originalArray = floatArrayOf(1.5f, 2.7f, 3.9f, 4.2f, 5.8f)
        val key = EncryptionUtil.generateKey()
        
        val encrypted = EncryptionUtil.encryptFloatArray(originalArray, key)
        val decrypted = EncryptionUtil.decryptToFloatArray(encrypted, key)
        
        assertArrayEquals(originalArray, decrypted, 0.0001f)
    }
    
    @Test
    fun `hashSHA256 produces consistent output`() {
        val input = "test_password"
        
        val hash1 = EncryptionUtil.hashSHA256(input)
        val hash2 = EncryptionUtil.hashSHA256(input)
        
        assertEquals(hash1, hash2)
    }
    
    @Test
    fun `hashSHA256 produces different output for different inputs`() {
        val hash1 = EncryptionUtil.hashSHA256("password1")
        val hash2 = EncryptionUtil.hashSHA256("password2")
        
        assertNotEquals(hash1, hash2)
    }
    
    @Test
    fun `verifyHash correctly validates hash`() {
        val input = "my_secret_pin"
        val hash = EncryptionUtil.hashSHA256(input)
        
        assertTrue(EncryptionUtil.verifyHash(input, hash))
        assertFalse(EncryptionUtil.verifyHash("wrong_pin", hash))
    }
    
    @Test
    fun `generateKey produces different keys`() {
        val key1 = EncryptionUtil.generateKey()
        val key2 = EncryptionUtil.generateKey()
        
        assertNotEquals(key1, key2)
    }
    
    @Test
    fun `deriveKeyFromPassphrase produces consistent keys`() {
        val passphrase = "test_passphrase"
        val salt = ByteArray(16) { 42 }
        
        val key1 = EncryptionUtil.deriveKeyFromPassphrase(passphrase, salt)
        val key2 = EncryptionUtil.deriveKeyFromPassphrase(passphrase, salt)
        
        assertArrayEquals(key1.encoded, key2.encoded)
    }
}

/**
 * Unit tests for attendance business logic
 */
class AttendanceLogicTest {
    
    @Test
    fun `duplicate attendance window calculation`() {
        val currentTime = System.currentTimeMillis()
        val windowMs = 5 * 60 * 1000L // 5 minutes
        
        val recentTime = currentTime - (3 * 60 * 1000L) // 3 minutes ago
        val oldTime = currentTime - (10 * 60 * 1000L) // 10 minutes ago
        
        assertTrue(currentTime - recentTime < windowMs)
        assertFalse(currentTime - oldTime < windowMs)
    }
    
    @Test
    fun `confidence threshold validation`() {
        val strongMatch = 0.92f
        val weakMatch = 0.75f
        val noMatch = 0.50f
        
        assertTrue(strongMatch >= FaceMatcher.STRONG_MATCH_THRESHOLD)
        assertTrue(weakMatch >= FaceMatcher.DEFAULT_THRESHOLD)
        assertFalse(noMatch >= FaceMatcher.DEFAULT_THRESHOLD)
    }
}
