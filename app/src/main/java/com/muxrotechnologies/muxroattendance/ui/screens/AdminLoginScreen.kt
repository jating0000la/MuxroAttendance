package com.muxrotechnologies.muxroattendance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxrotechnologies.muxroattendance.AttendanceApplication
import com.muxrotechnologies.muxroattendance.utils.SecurityUtil
import kotlinx.coroutines.launch

/**
 * Admin login screen - first-time setup
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val app = AttendanceApplication.getInstance()
    val scope = rememberCoroutineScope()
    
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFirstTimeSetup by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        isFirstTimeSetup = !app.configRepository.isAppInitialized()
    }
    
    fun setupAdmin() {
        if (pin.length < 4) {
            errorMessage = "PIN must be at least 4 digits"
            return
        }
        
        if (pin != confirmPin) {
            errorMessage = "PINs do not match"
            return
        }
        
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                val deviceId = SecurityUtil.getDeviceId(context)
                app.configRepository.initializeDefaults(deviceId, pin)
                onLoginSuccess()
            } catch (e: Exception) {
                errorMessage = "Setup failed: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isFirstTimeSetup) "First Time Setup" else "Admin Login",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (isFirstTimeSetup) 
                            "Create admin PIN (minimum 4 digits)" 
                        else 
                            "Enter admin PIN",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 8) pin = it },
                        label = { Text("Admin PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    if (isFirstTimeSetup) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { if (it.length <= 8) confirmPin = it },
                            label = { Text("Confirm PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { setupAdmin() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && pin.isNotEmpty()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(if (isFirstTimeSetup) "Setup" else "Login")
                        }
                    }
                }
            }
        }
    }
}
