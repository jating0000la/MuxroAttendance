package com.muxrotechnologies.muxroattendance.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxrotechnologies.muxroattendance.utils.ModelDownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One-time screen to download ML models on first launch
 */
@Composable
fun ModelDownloadScreen(
    onDownloadComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var currentFile by remember { mutableStateOf("") }
    var currentProgress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val downloadAndHandleResult: suspend () -> Unit = {
        isDownloading = true
        downloadError = null
        
        // Clear old models first to ensure fresh download
        ModelDownloadManager.clearDownloadedModels(context)

        val result: kotlin.Result<Unit> = ModelDownloadManager.downloadAllModels(context) { progress ->
            currentFile = progress.fileName
            currentProgress = progress.progress
        }

        if (result.isSuccess) {
            isDownloading = false
            delay(500)
            onDownloadComplete()
        } else {
            isDownloading = false
            val exception = result.exceptionOrNull()
            downloadError = exception?.message ?: "An unknown error occurred"
        }
    }

    // Auto-start download on first composition
    LaunchedEffect(Unit) {
        if (ModelDownloadManager.areModelsDownloaded(context)) {
            onDownloadComplete()
        } else {
            downloadAndHandleResult()
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                if (downloadError == null) {
                    // Downloading state
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Downloading",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Setting Up Face Recognition",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Downloading ML models (one-time setup)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Progress indicator
                    if (isDownloading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { currentProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Downloading: $currentFile",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "$currentProgress%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "This may take 1-2 minutes depending on your connection.\nAfter this, the app works completely offline.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )

                } else {
                    // Error state
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Download Failed",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = downloadError ?: "Unknown error",
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Please check your internet connection and try again.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                downloadAndHandleResult()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("RETRY DOWNLOAD")
                    }
                }
            }
        }
    }
}
