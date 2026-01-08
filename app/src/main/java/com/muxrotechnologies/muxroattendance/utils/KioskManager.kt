package com.muxrotechnologies.muxroattendance.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages kiosk mode settings and password authentication
 */
object KioskManager {
    private const val PREFS_NAME = "kiosk_preferences"
    private const val KEY_KIOSK_ENABLED = "kiosk_enabled"
    private const val KEY_KIOSK_PASSWORD = "kiosk_password"
    private const val DEFAULT_PASSWORD = "admin123"
    
    private fun getPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    fun isKioskModeEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_KIOSK_ENABLED, false)
    }
    
    fun setKioskModeEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_KIOSK_ENABLED, enabled)
            .apply()
    }
    
    fun setKioskPassword(context: Context, password: String) {
        getPreferences(context).edit()
            .putString(KEY_KIOSK_PASSWORD, password)
            .apply()
    }
    
    fun verifyKioskPassword(context: Context, enteredPassword: String): Boolean {
        val savedPassword = getPreferences(context).getString(KEY_KIOSK_PASSWORD, DEFAULT_PASSWORD)
        return enteredPassword == savedPassword
    }
    
    fun hasPasswordSet(context: Context): Boolean {
        return getPreferences(context).contains(KEY_KIOSK_PASSWORD)
    }
    
    fun initializeDefaultPassword(context: Context) {
        if (!hasPasswordSet(context)) {
            setKioskPassword(context, DEFAULT_PASSWORD)
        }
    }
}
