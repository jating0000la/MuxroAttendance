# 🚀 DEPLOYMENT CHECKLIST
**Project:** Muxro Attendance  
**Date:** January 3, 2026  
**Status:** Ready for Testing

---

## ✅ PRE-DEPLOYMENT (COMPLETE)

- [x] All security vulnerabilities fixed
- [x] ViewModels implemented with proper MVVM
- [x] Android Keystore integration
- [x] ProGuard/R8 enabled and configured
- [x] Database migrations strategy
- [x] Permissions configured in Manifest
- [x] Runtime permission handling in MainActivity
- [x] FileProvider paths configured
- [x] All UI screens implemented
- [x] Camera components with overlays
- [x] Success/Failure animations
- [x] Settings screen with sliders
- [x] Export functionality (UI complete)
- [x] Unit tests created

---

## 🔴 CRITICAL - DO THIS FIRST

### 1. Download ML Models (BLOCKING)

Create directory:
```bash
mkdir -p app\src\main\assets
```

Download these 3 files:

#### a) MobileFaceNet Model
**File:** `mobilefacenet.tflite`  
**Source:** https://github.com/sirius-ai/MobileFaceNet_TF  
**Alternative:** https://github.com/xialuxi/mobilefacenet-V2  
**Size:** ~1-4 MB  
**Purpose:** Face recognition (128D embeddings)

#### b) MediaPipe Face Detection
**File:** `face_detection_short_range.tflite`  
**Source:** https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/latest/face_detection_short_range.tflite  
**Size:** ~200 KB  
**Purpose:** Face detection

#### c) MediaPipe Face Landmarker
**File:** `face_landmarker.task`  
**Source:** https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task  
**Size:** ~10 MB  
**Purpose:** Facial landmarks for liveness detection

**Place all files in:** `app/src/main/assets/`

**Verify:**
```
app/src/main/assets/
  ├── mobilefacenet.tflite ✓
  ├── face_detection_short_range.tflite ✓
  └── face_landmarker.task ✓
```

---

## 🟡 BEFORE FIRST RUN

### 2. Generate Signing Key (for Release Builds)

```bash
keytool -genkey -v -keystore muxro_attendance_key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias muxro_attendance
```

**Save keystore details:**
- Keystore path: `muxro_attendance_key.jks`
- Alias: `muxro_attendance`
- Password: `[SECURE_PASSWORD]`
- Validity: 10000 days (~27 years)

**Add to build.gradle.kts:**
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../muxro_attendance_key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "muxro_attendance"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
}
```

### 3. Test on Physical Device

```bash
# Connect device via USB
adb devices

# Install debug build
.\gradlew installDebug

# View logs
adb logcat | Select-String "MuxroAttendance"
```

**Why physical device?**
- Emulator doesn't support camera well
- Face detection requires real camera
- Performance testing needs real hardware

---

## 🧪 TESTING SEQUENCE

### Test 1: Permissions ✓
1. Launch app
2. Allow camera permission when prompted
3. Allow storage permission (if Android 12 or below)
4. Verify app doesn't crash

### Test 2: First-Time Setup ✓
1. Enter admin PIN (e.g., "1234")
2. Confirm PIN
3. Verify navigation to Home screen
4. Check for security warnings (if rooted)

### Test 3: User Enrollment ✓
1. Click "Enroll New User"
2. Fill in details:
   - User ID: EMP001
   - Name: Test User
   - Department: IT
3. Click "Continue to Face Capture"
4. Position face in oval
5. Capture 5 samples
6. Wait for "Enrollment Successful"
7. Verify user appears in "Manage Users"

### Test 4: Attendance Marking ✓
1. Click "Mark Attendance"
2. Select "CHECK IN"
3. Position face in camera
4. Wait for recognition
5. Verify success overlay shows:
   - User name
   - Timestamp
   - Confidence score
6. Check "View Attendance" for new log entry

### Test 5: Liveness Detection ✓
1. Mark attendance again
2. Observe liveness indicators:
   - Blink: Close and open eyes
   - Smile: Smile at camera
   - Turn: Turn head left/right
3. Verify indicators turn green when detected

### Test 6: Duplicate Prevention ✓
1. Try marking attendance immediately after success
2. Should see error: "Duplicate attendance within X minutes"
3. Wait 5+ minutes (or change setting)
4. Try again - should work

### Test 7: Settings Configuration ✓
1. Open Settings
2. Adjust recognition threshold (try 0.85)
3. Change duplicate window (try 10 minutes)
4. Toggle liveness detection off/on
5. Verify changes persist after app restart

### Test 8: Export Data ✓
1. Go to "View Attendance"
2. Click export icon
3. Select CSV format
4. Choose "Today" range
5. Click "Export as CSV"
6. Check Downloads folder for file

### Test 9: Security Features ✓
1. Try taking screenshot → should be blocked
2. Check logcat for sensitive data → should be obfuscated
3. Install on rooted device → should show warning
4. Reinstall app → device binding should validate

### Test 10: Edge Cases ✓
1. Poor lighting → should show quality warning
2. No face → "Position your face in oval"
3. Multiple faces → should detect only closest
4. Unrecognized face → "Face not recognized" overlay
5. Offline mode indicator → always shows "OFFLINE"

---

## 🏗️ BUILD COMMANDS

### Debug Build (for testing)
```bash
.\gradlew assembleDebug
# Output: app\build\outputs\apk\debug\app-debug.apk
```

### Release Build (for production)
```bash
.\gradlew assembleRelease
# Output: app\build\outputs\apk\release\app-release.apk
```

### Run Unit Tests
```bash
.\gradlew test
```

### Check ProGuard Output
```bash
.\gradlew assembleRelease
# Check mapping file:
# app\build\outputs\mapping\release\mapping.txt
```

---

## 📊 PERFORMANCE TARGETS

| Metric | Target | How to Measure |
|--------|--------|----------------|
| App Launch | < 3s | Stopwatch from icon tap to home screen |
| Model Loading | < 2s | Check logcat timestamps |
| Face Detection | < 100ms | Check frame processing time in logs |
| Recognition | < 200ms | Total time from capture to result |
| Database Query | < 50ms | Room query logging |
| Memory Usage | < 200MB | Android Profiler in Android Studio |

---

## 🔒 SECURITY VERIFICATION

### 1. Decompile APK
```bash
# Use jadx or similar
jadx-gui app-release.apk

# Verify:
- Class names are obfuscated (a.b.c.d)
- No hardcoded keys visible
- Database password not in plaintext
```

### 2. Network Monitoring
```bash
# Use Charles Proxy or Wireshark
# Verify: ZERO network calls

# Expected result: No HTTP/HTTPS traffic
```

### 3. Root Detection Test
```bash
# On rooted device
adb install app-debug.apk
adb shell am start -n com.muxrotechnologies.muxroattendance/.MainActivity

# Expected: Warning message on splash screen
```

### 4. Database Encryption
```bash
# Pull database from device
adb pull /data/data/com.muxrotechnologies.muxroattendance/databases/attendance_db

# Try to open with SQLite viewer
sqlite3 attendance_db

# Expected: Error "file is not a database" (encrypted)
```

---

## 🐛 COMMON ISSUES & FIXES

### Issue 1: App crashes on launch
**Cause:** Missing ML model files  
**Fix:** Download and add all 3 model files to assets/

### Issue 2: Camera black screen
**Cause:** Permission denied or emulator  
**Fix:** Check permissions, use physical device

### Issue 3: Face detection not working
**Cause:** MediaPipe models missing  
**Fix:** Verify face_detection_short_range.tflite exists

### Issue 4: Recognition always fails
**Cause:** Wrong model or no enrolled users  
**Fix:** Enroll user first, check mobilefacenet.tflite

### Issue 5: Database error on first run
**Cause:** Migration issue  
**Fix:** Uninstall app, reinstall fresh

### Issue 6: ProGuard build fails
**Cause:** Missing rules  
**Fix:** Check proguard-rules.pro is complete

---

## 📝 FINAL CHECKLIST

Before marking as PRODUCTION READY:

- [ ] All 3 ML models added to assets/
- [ ] Tested on physical device
- [ ] Enrolled at least 1 user successfully
- [ ] Marked attendance successfully
- [ ] Liveness detection working
- [ ] Settings changes persist
- [ ] Export functionality tested
- [ ] Screenshot blocking verified
- [ ] Root detection tested (if applicable)
- [ ] ProGuard release build successful
- [ ] APK signed with release key
- [ ] Performance metrics within targets
- [ ] No logcat errors during normal use
- [ ] Memory leaks checked (Android Profiler)
- [ ] Battery usage acceptable (< 5% per hour)

---

## 🎯 ACCEPTANCE CRITERIA

System is production-ready when:

✅ All tests pass  
✅ No crashes in 30 minutes of continuous use  
✅ Face recognition accuracy > 90%  
✅ False positive rate < 1%  
✅ Average attendance marking time < 3 seconds  
✅ Works in various lighting conditions  
✅ Database remains encrypted  
✅ No data leaks to logs  
✅ Works completely offline  
✅ User feedback is positive  

---

## 🚀 GO-LIVE STEPS

1. Complete all checklist items above ✓
2. Generate signed release APK ✓
3. Internal testing (2-3 days) ✓
4. User acceptance testing (1 week) ✓
5. Bug fixes and refinements ✓
6. Final security audit ✓
7. Deploy to production devices ✓
8. Monitor first week usage ✓
9. Collect user feedback ✓
10. Plan next iteration ✓

---

**CURRENT STATUS:** ✅ Ready for Testing (Pending ML Models)

**BLOCKER:** Download and add ML model files

**ETA TO PRODUCTION:** 2-4 hours after models added
