package com.muxrotechnologies.muxroattendance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxrotechnologies.muxroattendance.AttendanceApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConfigUiState(
    val recognitionThreshold: Float = 0.85f,
    val duplicateWindowMs: Long = 1800000, // 30 minutes
    val isLivenessEnabled: Boolean = true,
    val isRootDetectionEnabled: Boolean = true,
    val isSoundEnabled: Boolean = true,
    val isHapticEnabled: Boolean = true
)

class ConfigViewModel : ViewModel() {
    
    private val app = AttendanceApplication.getInstance()
    private val configRepository = app.configRepository
    
    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val threshold = configRepository.getRecognitionThreshold()
                val window = configRepository.getDuplicateAttendanceWindow()
                val liveness = configRepository.isLivenessCheckEnabled()
                
                _uiState.value = _uiState.value.copy(
                    recognitionThreshold = threshold,
                    duplicateWindowMs = window,
                    isLivenessEnabled = liveness
                )
            } catch (e: Exception) {
                // Use defaults
            }
        }
    }
    
    suspend fun getRecognitionThreshold(): Float {
        return configRepository.getRecognitionThreshold()
    }
    
    suspend fun getDuplicateWindowMs(): Long {
        return configRepository.getDuplicateAttendanceWindow()
    }
    
    suspend fun isLivenessEnabled(): Boolean {
        return configRepository.isLivenessCheckEnabled()
    }
    
    suspend fun setRecognitionThreshold(value: Float) {
        configRepository.setRecognitionThreshold(value)
        _uiState.value = _uiState.value.copy(recognitionThreshold = value)
    }
    
    suspend fun setDuplicateInterval(ms: Long) {
        configRepository.setDuplicateAttendanceWindow(ms)
        _uiState.value = _uiState.value.copy(duplicateWindowMs = ms)
    }
    
    suspend fun setLivenessCheckEnabled(enabled: Boolean) {
        configRepository.setLivenessCheckEnabled(enabled)
        _uiState.value = _uiState.value.copy(isLivenessEnabled = enabled)
    }
    
    suspend fun setRootDetectionEnabled(enabled: Boolean) {
        // configRepository.setRootDetectionEnabled(enabled)
        _uiState.value = _uiState.value.copy(isRootDetectionEnabled = enabled)
    }
    
    suspend fun clearAllData() {
        // Clear all data logic
    }
}
