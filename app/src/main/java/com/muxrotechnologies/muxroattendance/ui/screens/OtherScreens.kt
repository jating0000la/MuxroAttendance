package com.muxrotechnologies.muxroattendance.ui.screens

import android.graphics.RectF
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceLog
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceType
import com.muxrotechnologies.muxroattendance.data.entity.User
import com.muxrotechnologies.muxroattendance.ui.components.*
import com.muxrotechnologies.muxroattendance.ui.theme.AttendanceBlue
import com.muxrotechnologies.muxroattendance.ui.theme.AttendancePurple
import com.muxrotechnologies.muxroattendance.ui.theme.AttendanceGreen
import com.muxrotechnologies.muxroattendance.ui.theme.AttendanceRed
import com.muxrotechnologies.muxroattendance.ui.theme.AttendanceOrange
import com.muxrotechnologies.muxroattendance.ui.viewmodel.AttendanceViewModel
import com.muxrotechnologies.muxroattendance.ui.viewmodel.EnrollmentViewModel
import com.muxrotechnologies.muxroattendance.ui.viewmodel.UserViewModel
import com.muxrotechnologies.muxroattendance.ui.viewmodel.ConfigViewModel
import com.muxrotechnologies.muxroattendance.utils.ModelDownloadManager
import com.muxrotechnologies.muxroattendance.utils.KioskManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceCameraScreen(
    onNavigateBack: () -> Unit,
    viewModel: AttendanceViewModel = viewModel(),
    isKioskMode: Boolean = false,
    onNavigateToHome: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showPasswordDialog by remember { mutableStateOf(false) }

    // Handle back button in kiosk mode
    BackHandler(enabled = isKioskMode) {
        showPasswordDialog = true
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                androidx.camera.view.PreviewView(ctx).apply {
                    implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                    viewModel.startCamera(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (state.faceRect != null) {
            FaceDetectionOverlay(
                faceRect = android.graphics.RectF(state.faceRect!!),
                isFaceDetected = state.isFaceDetected,
                confidence = state.confidence ?: 0f
            )
        }

        FaceGuideOval(
            isAligned = state.isFaceAligned
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Only show back button if not in kiosk mode
                if (!isKioskMode) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Back",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Text(
                    text = if (isKioskMode) "Attendance Kiosk" else "Attendance Scanner",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Show settings icon in kiosk mode
                if (isKioskMode) {
                    IconButton(onClick = { showPasswordDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            "Admin Settings",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            if (state.error != null) {
                InstructionOverlay(
                    message = state.error!!,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                InstructionOverlay(
                    message = when {
                        !state.isFaceDetected -> "Position your face in the oval"
                        !state.isFaceAligned -> "Move closer and center your face"
                        state.isProcessing -> "Processing..."
                        state.autoCapturing && state.captureCountdown > 0 -> "Hold still... ${state.captureCountdown}"
                        else -> "Auto-detecting check-in/check-out..."
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isFaceDetected) {
                LivenessIndicators(
                    blinkDetected = state.livenessState.hasBlinked,
                    smileDetected = state.livenessState.hasSmiled,
                    headTurnDetected = state.livenessState.hasTurnedHead
                )
            }

            if ((state.confidence ?: 0f) > 0f) {
                ConfidenceMeter(confidence = state.confidence ?: 0f)
            }
            
            // Show countdown progress when auto-capturing
            if (state.autoCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = AttendanceGreen,
                    strokeWidth = 4.dp
                )
            }

            // Optional manual capture button (hidden during auto-capture)
            /*
            if (state.isFaceDetected && state.isFaceAligned && !state.isProcessing && !state.autoCapturing) {
                Button(
                    onClick = { viewModel.processAttendance(state.faceBitmap!!, attendanceType) },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (attendanceType == AttendanceType.CHECK_IN)
                            AttendanceBlue
                        else
                            AttendancePurple
                    )
                ) {
                    Icon(Icons.Default.Face, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MARK ${attendanceType.name.replace("_", " ")}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            */
        }

        if (state.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = Color.White
                )
            }
        }

        if (state.recognizedUser != null) {
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            SuccessOverlay(
                userName = state.recognizedUser!!.name,
                time = timeFormat.format(Date()),
                confidence = state.confidence ?: 0f,
                onDismiss = {
                    viewModel.resetState()
                    // In kiosk mode, stay on camera screen; otherwise navigate back
                    if (!isKioskMode) {
                        onNavigateBack()
                    }
                }
            )
        }

        if (state.error != null) {
            FailureOverlay(
                message = state.error ?: "Face not recognized",
                onRetry = { viewModel.resetState() },
                onDismiss = if (isKioskMode) {
                    // In kiosk mode, just reset and stay on screen
                    { viewModel.resetState() }
                } else {
                    onNavigateBack
                }
            )
        }
    }
    
    // Show password dialog in kiosk mode
    if (showPasswordDialog && isKioskMode) {
        KioskPasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onAuthenticated = {
                showPasswordDialog = false
                onNavigateToHome()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEnrollmentScreen(
    onNavigateBack: () -> Unit,
    viewModel: EnrollmentViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    var showCameraView by remember { mutableStateOf(false) }

    var userId by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }

    if (!showCameraView) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Enroll New User") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text("User ID *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Badge, null) }
                )

                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Full Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, null) }
                )

                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("Department *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Business, null) }
                )

                OutlinedTextField(
                    value = designation,
                    onValueChange = { designation = it },
                    label = { Text("Designation") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Work, null) }
                )

                Spacer(modifier = Modifier.weight(1f))

                if (state.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (userId.isBlank() || userName.isBlank() || department.isBlank()) {
                            viewModel.updateError("Please fill all required fields")
                        } else {
                            viewModel.updateUserDetails(userId, userName, department, designation)
                            showCameraView = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !state.isProcessing
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CONTINUE TO FACE CAPTURE", fontSize = 16.sp)
                }
            }
        }
    } else {
        LaunchedEffect(Unit) {
            viewModel.initialize(context)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    androidx.camera.view.PreviewView(ctx).apply {
                        implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                        viewModel.startCamera(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (state.faceRect != null) {
                FaceDetectionOverlay(
                    faceRect = android.graphics.RectF(state.faceRect!!),
                    isFaceDetected = state.isFaceDetected,
                    confidence = 1f
                )
            }

            FaceGuideOval(isAligned = state.isFaceAligned)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { showCameraView = false }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }

                    Text(
                        text = userName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )

                    Spacer(modifier = Modifier.width(48.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                InstructionOverlay(
                    message = when {
                        state.message != null && state.currentSample > 0 -> state.message!!
                        state.currentSample >= state.totalSamples -> "All samples captured! Tap SAVE USER"
                        !state.isFaceDetected -> "Position your face in the oval"
                        !state.isFaceAligned -> "Move closer and center your face"
                        state.isProcessing -> "Processing..."
                        else -> "Tap to capture sample ${state.currentSample + 1}/${state.totalSamples}"
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SampleCaptureCounter(
                    currentSample = state.currentSample,
                    totalSamples = state.totalSamples,
                    quality = state.imageQuality
                )

                if (state.isFaceDetected && state.isFaceAligned && !state.isProcessing) {
                    Button(
                        onClick = { viewModel.captureCurrentFrame() },
                        modifier = Modifier.size(72.dp),
                        enabled = state.currentSample < state.totalSamples,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AttendanceGreen
                        )
                    ) {
                        Icon(
                            Icons.Default.Camera,
                            contentDescription = "Capture",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                
                // Show Save button after all samples are captured
                if (state.currentSample >= state.totalSamples && !state.isProcessing && !state.isEnrollmentComplete) {
                    Button(
                        onClick = { 
                            viewModel.enrollUser(userId, userName, department)
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AttendanceBlue
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SAVE USER",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (state.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Enrolling user...",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            if (state.isEnrollmentComplete) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    onNavigateBack()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Enrollment Successful!",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = userName,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceHistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExport: () -> Unit
) {
    val app = com.muxrotechnologies.muxroattendance.AttendanceApplication.getInstance()
    val scope = rememberCoroutineScope()

    var attendanceLogs by remember { mutableStateOf<List<AttendanceLog>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedFilter) {
        isLoading = true
        attendanceLogs = when (selectedFilter) {
            "Today" -> {
                val startOfDay = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                }.timeInMillis
                app.attendanceRepository.getAttendanceByDateRange(startOfDay, System.currentTimeMillis())
            }
            "This Week" -> {
                val startOfWeek = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                }.timeInMillis
                app.attendanceRepository.getAttendanceByDateRange(startOfWeek, System.currentTimeMillis())
            }
            "This Month" -> {
                val startOfMonth = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                }.timeInMillis
                app.attendanceRepository.getAttendanceByDateRange(startOfMonth, System.currentTimeMillis())
            }
            else -> app.attendanceRepository.getAllAttendance()
        }
        isLoading = false
    }

    val filteredLogs = attendanceLogs.filter { log ->
        searchQuery.isEmpty() ||
        log.userId.toString().contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToExport) {
                        Icon(Icons.Default.FileDownload, "Export")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search by User ID...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Today", "This Week", "This Month").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No attendance records found",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs.size) { index ->
                        val log = filteredLogs[index]
                        AttendanceLogCard(
                            log = log,
                            onDelete = {
                                scope.launch {
                                    app.attendanceRepository.delete(log)
                                    attendanceLogs = attendanceLogs.filter { it.id != log.id }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceLogCard(
    log: AttendanceLog,
    onDelete: () -> Unit
) {
    val app = com.muxrotechnologies.muxroattendance.AttendanceApplication.getInstance()
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    // Fetch user details
    var userName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(log.userId) {
        userName = app.userRepository.getUserById(log.userId)?.name ?: "User #${log.userId}"
    }

    val backgroundColor = when (log.type) {
        AttendanceType.CHECK_IN -> AttendanceBlue.copy(alpha = 0.1f)
        AttendanceType.CHECK_OUT -> AttendancePurple.copy(alpha = 0.1f)
    }

    val iconColor = when (log.type) {
        AttendanceType.CHECK_IN -> AttendanceBlue
        AttendanceType.CHECK_OUT -> AttendancePurple
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (log.type == AttendanceType.CHECK_IN)
                        Icons.Default.Login else Icons.Default.Logout,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = userName ?: "Loading...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateFormat.format(Date(log.timestamp)),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = timeFormat.format(Date(log.timestamp)),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = log.type.name.replace("_", " "),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
                if (log.confidenceScore > 0) {
                    Text(
                        text = "${(log.confidenceScore * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete",
                        tint = AttendanceRed.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Record?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = AttendanceRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(onNavigateBack: () -> Unit) {
    val app = com.muxrotechnologies.muxroattendance.AttendanceApplication.getInstance()
    val scope = rememberCoroutineScope()

    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf("list") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        users = app.userRepository.getAllUsers()
        isLoading = false
    }

    val filteredUsers = users.filter { user ->
        searchQuery.isEmpty() ||
        user.name.contains(searchQuery, ignoreCase = true) ||
        user.userId.contains(searchQuery, ignoreCase = true) ||
        (user.department?.contains(searchQuery, ignoreCase = true) == true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Users (${users.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        viewMode = if (viewMode == "list") "grid" else "list" 
                    }) {
                        Icon(
                            if (viewMode == "list") Icons.Default.GridView else Icons.Default.List,
                            "Toggle View"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search users...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                singleLine = true
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredUsers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PersonOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) 
                                "No users enrolled yet" 
                            else 
                                "No users match your search",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                if (viewMode == "list") {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredUsers.size) { index ->
                            UserListCard(user = filteredUsers[index])
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredUsers.size) { index ->
                            UserGridCard(user = filteredUsers[index])
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserListCard(user: User) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.take(2).uppercase(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user.userId,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = user.department ?: "No Department",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Icon(
                imageVector = if (user.isActive) Icons.Default.CheckCircle else Icons.Default.Block,
                contentDescription = null,
                tint = if (user.isActive) AttendanceGreen else AttendanceRed,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun UserGridCard(user: User) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.take(2).uppercase(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = user.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Text(
                text = user.userId,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = user.department ?: "No Department",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val app = com.muxrotechnologies.muxroattendance.AttendanceApplication.getInstance()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var recognitionThreshold by remember { mutableStateOf(0.80f) }
    var duplicateWindowMinutes by remember { mutableStateOf(5) }
    var livenessEnabled by remember { mutableStateOf(true) }
    var rootDetectionEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(false) }
    var hapticEnabled by remember { mutableStateOf(true) }
    var kioskModeEnabled by remember { mutableStateOf(false) }
    var showPasswordChangeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        recognitionThreshold = app.configRepository.getRecognitionThreshold()
        duplicateWindowMinutes = (app.configRepository.getDuplicateWindowMs() / 60000).toInt()
        livenessEnabled = app.configRepository.isLivenessEnabled()
        rootDetectionEnabled = app.configRepository.isRootDetectionEnabled()
        kioskModeEnabled = KioskManager.isKioskModeEnabled(context)
    }

    var showClearDataDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Recognition Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Recognition Threshold")
                            Text(
                                "${(recognitionThreshold * 100).toInt()}%",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = recognitionThreshold,
                            onValueChange = { recognitionThreshold = it },
                            valueRange = 0.70f..0.95f,
                            onValueChangeFinished = {
                                scope.launch {
                                    app.configRepository.setRecognitionThreshold(recognitionThreshold)
                                }
                            }
                        )
                        Text(
                            text = "Higher values = more strict matching",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Duplicate Window")
                            Text(
                                "$duplicateWindowMinutes min",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = duplicateWindowMinutes.toFloat(),
                            onValueChange = { duplicateWindowMinutes = it.toInt() },
                            valueRange = 1f..60f,
                            steps = 59,
                            onValueChangeFinished = {
                                scope.launch {
                                    app.configRepository.setDuplicateInterval(duplicateWindowMinutes * 60000L)
                                }
                            }
                        )
                        Text(
                            text = "Prevent duplicate attendance within this time",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            item {
                Card(
                    onClick = {
                        livenessEnabled = !livenessEnabled
                        scope.launch {
                            app.configRepository.setLivenessCheckEnabled(livenessEnabled)
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Liveness Detection", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Detect blinks, smiles, head turns",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = livenessEnabled,
                            onCheckedChange = {
                                livenessEnabled = it
                                scope.launch {
                                    app.configRepository.setLivenessCheckEnabled(it)
                                }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Security",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                Card(
                    onClick = {
                        kioskModeEnabled = !kioskModeEnabled
                        KioskManager.setKioskModeEnabled(context, kioskModeEnabled)
                        if (kioskModeEnabled) {
                            com.muxrotechnologies.muxroattendance.service.KioskService.start(context)
                            Toast.makeText(
                                context,
                                "Kiosk mode enabled. App will restart to attendance screen. Please pin this app for best results.",
                                Toast.LENGTH_LONG
                            ).show()
                            // Restart app to apply kiosk mode
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                val intent = android.content.Intent(context, com.muxrotechnologies.muxroattendance.MainActivity::class.java)
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(intent)
                            }, 2000)
                        } else {
                            com.muxrotechnologies.muxroattendance.service.KioskService.stop(context)
                            Toast.makeText(context, "Kiosk mode disabled", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kiosk Mode", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Lock app to attendance screen with password protection",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = kioskModeEnabled,
                            onCheckedChange = {
                                kioskModeEnabled = it
                                KioskManager.setKioskModeEnabled(context, it)
                                if (it) {
                                    com.muxrotechnologies.muxroattendance.service.KioskService.start(context)
                                    Toast.makeText(
                                        context,
                                        "Kiosk mode enabled. App will restart to attendance screen.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    // Restart app
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        val intent = android.content.Intent(context, com.muxrotechnologies.muxroattendance.MainActivity::class.java)
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        context.startActivity(intent)
                                    }, 2000)
                                } else {
                                    com.muxrotechnologies.muxroattendance.service.KioskService.stop(context)
                                    Toast.makeText(context, "Kiosk mode disabled", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
            
            item {
                Card(
                    onClick = { showPasswordChangeDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Change Kiosk Password", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Current: ${if (KioskManager.hasPasswordSet(context)) "Custom" else "admin123 (default)"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(Icons.Default.Lock, "Password")
                    }
                }
            }

            item {
                Card(
                    onClick = {
                        rootDetectionEnabled = !rootDetectionEnabled
                        scope.launch {
                            app.configRepository.setRootDetectionEnabled(rootDetectionEnabled)
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Root Detection", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Block app on rooted devices",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = rootDetectionEnabled,
                            onCheckedChange = {
                                rootDetectionEnabled = it
                                scope.launch {
                                    app.configRepository.setRootDetectionEnabled(it)
                                }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reports",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                var isServerRunning by remember { mutableStateOf(false) }
                var serverUrl by remember { mutableStateOf("Loading...") }
                
                LaunchedEffect(Unit) {
                    try {
                        // Try to get WiFi IP address
                        val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                        if (wifiManager != null) {
                            val wifiInfo = wifiManager.connectionInfo
                            val ip = wifiInfo.ipAddress
                            if (ip != 0) {
                                @Suppress("DEPRECATION")
                                val ipAddress = android.text.format.Formatter.formatIpAddress(ip)
                                serverUrl = "http://$ipAddress:8080"
                            } else {
                                // Fallback: try to get IP from network interfaces
                                try {
                                    val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                                    while (interfaces.hasMoreElements()) {
                                        val networkInterface = interfaces.nextElement()
                                        val addresses = networkInterface.inetAddresses
                                        while (addresses.hasMoreElements()) {
                                            val address = addresses.nextElement()
                                            if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                                                serverUrl = "http://${address.hostAddress}:8080"
                                                break
                                            }
                                        }
                                    }
                                    if (serverUrl == "Loading...") {
                                        serverUrl = "http://localhost:8080"
                                    }
                                } catch (e: Exception) {
                                    serverUrl = "http://localhost:8080"
                                }
                            }
                        } else {
                            serverUrl = "http://localhost:8080"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        serverUrl = "http://localhost:8080"
                    }
                }
                
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Web Report Server", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Access reports from any device on your network",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = isServerRunning,
                                onCheckedChange = { 
                                    isServerRunning = it
                                    if (it) {
                                        com.muxrotechnologies.muxroattendance.service.ReportServerService.start(context)
                                        Toast.makeText(context, "Report server started", Toast.LENGTH_SHORT).show()
                                    } else {
                                        com.muxrotechnologies.muxroattendance.service.ReportServerService.stop(context)
                                        Toast.makeText(context, "Report server stopped", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        
                        if (isServerRunning) {
                            androidx.compose.material3.Divider()
                            
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Access URL:",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                androidx.compose.material3.OutlinedCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            serverUrl,
                                            modifier = Modifier.weight(1f),
                                            fontSize = 14.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = AttendanceBlue
                                        )
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("Server URL", serverUrl)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, "Copy URL")
                                        }
                                    }
                                }
                                
                                Text(
                                    "📱 Share this URL with anyone on the same WiFi network to view reports",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Feedback",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    onClick = { soundEnabled = !soundEnabled }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sound Effects", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Play sounds on success/failure",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { soundEnabled = it }
                        )
                    }
                }
            }

            item {
                Card(
                    onClick = { hapticEnabled = !hapticEnabled }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Haptic Feedback", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Vibrate on key actions",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = hapticEnabled,
                            onCheckedChange = { hapticEnabled = it }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Data Management",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    onClick = { /* TODO: Implement backup */ },
                    colors = CardDefaults.cardColors(
                        containerColor = AttendanceBlue.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Backup, null, tint = AttendanceBlue)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Backup Database", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Export encrypted backup",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            
            item {
                Card(
                    onClick = { 
                        scope.launch {
                            ModelDownloadManager.clearDownloadedModels(context)
                            // Show toast or snackbar
                            android.widget.Toast.makeText(
                                context, 
                                "ML models cleared. Restart app to re-download.", 
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = AttendanceOrange.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = AttendanceOrange)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Re-download ML Models", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Clear and re-download face recognition models",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    onClick = { /* TODO: Implement restore */ },
                    colors = CardDefaults.cardColors(
                        containerColor = AttendanceOrange.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Restore, null, tint = AttendanceOrange)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Restore Database", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Import from backup file",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    onClick = { showClearDataDialog = true },
                    colors = CardDefaults.cardColors(
                        containerColor = AttendanceRed.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DeleteForever, null, tint = AttendanceRed)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Clear All Data", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Delete all users and attendance",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "About",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Version", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text("1.0.0", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mode", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text("Offline Only", fontWeight = FontWeight.SemiBold, color = AttendanceGreen)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Developer", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text("Muxro Technologies", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = AttendanceRed,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("Clear All Data?") },
            text = {
                Text("This will permanently delete:\n\n• All enrolled users\n• All face embeddings\n• All attendance records\n\nThis action CANNOT be undone!")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            app.clearAllData()
                            showClearDataDialog = false
                            android.widget.Toast.makeText(
                                context,
                                "All data cleared",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AttendanceRed
                    )
                ) {
                    Text("DELETE ALL")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showPasswordChangeDialog) {
        var oldPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showPasswordChangeDialog = false },
            icon = {
                Icon(Icons.Default.Lock, "Change Password")
            },
            title = { Text("Change Kiosk Password") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it; errorMessage = "" },
                        label = { Text("Current Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; errorMessage = "" },
                        label = { Text("New Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = "" },
                        label = { Text("Confirm New Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            !KioskManager.verifyKioskPassword(context, oldPassword) -> {
                                errorMessage = "Current password is incorrect"
                            }
                            newPassword.length < 4 -> {
                                errorMessage = "Password must be at least 4 characters"
                            }
                            newPassword != confirmPassword -> {
                                errorMessage = "Passwords do not match"
                            }
                            else -> {
                                KioskManager.setKioskPassword(context, newPassword)
                                Toast.makeText(context, "Password changed successfully", Toast.LENGTH_SHORT).show()
                                showPasswordChangeDialog = false
                            }
                        }
                    }
                ) {
                    Text("Change Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordChangeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(onNavigateBack: () -> Unit) {
    val app = com.muxrotechnologies.muxroattendance.AttendanceApplication.getInstance()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isExporting by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf("CSV") }
    var dateRange by remember { mutableStateOf("All Time") }
    var includeUserDetails by remember { mutableStateOf(true) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var exportedFilePath by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Data") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Export attendance records to external file",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Export Format",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = exportFormat == "CSV",
                            onClick = { exportFormat = "CSV" },
                            label = { Text("CSV") },
                            leadingIcon = {
                                if (exportFormat == "CSV") {
                                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                }
                            }
                        )

                        FilterChip(
                            selected = exportFormat == "PDF",
                            onClick = { exportFormat = "PDF" },
                            label = { Text("PDF") },
                            leadingIcon = {
                                if (exportFormat == "PDF") {
                                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                }
                            }
                        )

                        FilterChip(
                            selected = exportFormat == "JSON",
                            onClick = { exportFormat = "JSON" },
                            label = { Text("JSON") },
                            leadingIcon = {
                                if (exportFormat == "JSON") {
                                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                }
                            }
                        )
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Date Range",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Today", "This Week", "This Month", "All Time").forEach { range ->
                            FilterChip(
                                selected = dateRange == range,
                                onClick = { dateRange = range },
                                label = { Text(range) },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = {
                                    if (dateRange == range) {
                                        Icon(Icons.Default.RadioButtonChecked, null, Modifier.size(18.dp))
                                    } else {
                                        Icon(Icons.Default.RadioButtonUnchecked, null, Modifier.size(18.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Options",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Include User Details")
                            Text(
                                "Name, Department, Designation",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = includeUserDetails,
                            onCheckedChange = { includeUserDetails = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    isExporting = true
                    scope.launch {
                        kotlinx.coroutines.delay(2000) // Simulate export
                        isExporting = false
                        exportedFilePath = "/storage/emulated/0/Download/attendance_${System.currentTimeMillis()}.$exportFormat"
                        showSuccessDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exporting...")
                } else {
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("EXPORT AS $exportFormat", fontSize = 16.sp)
                }
            }

            Text(
                text = "Note: File will be saved to Downloads folder",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSuccessDialog = false
                onNavigateBack()
            },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AttendanceGreen,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("Export Successful!") },
            text = {
                Column {
                    Text("File saved successfully")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = exportedFilePath,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}
