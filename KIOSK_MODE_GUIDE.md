# Kiosk Mode Setup Guide

## Overview
Your Muxro Attendance app has been enhanced with **Kiosk Mode** to transform any Android device into a dedicated attendance machine. When enabled, the device will:
- Always launch directly to the attendance screen
- Auto-restart if the app is closed
- Require password authentication to access other features
- Keep the screen on continuously
- Prevent unauthorized access

## Features

### 1. **Auto-Launch to Attendance Screen**
   - App bypasses splash screen and goes straight to attendance camera
   - No manual navigation needed

### 2. **Auto-Restart Protection**
   - If someone closes the app, it automatically reopens
   - Foreground service ensures continuous operation
   - Survives task removal from recent apps

### 3. **Password Protection**
   - Default password: `admin123`
   - Back button/Settings icon prompts for password
   - Only authorized users can access admin features
   - Password is encrypted in secure storage

### 4. **Screen Pinning Support**
   - Can be pinned for enhanced security
   - Prevents access to other apps
   - Optional but recommended

### 5. **Keep Screen On**
   - Screen stays active during kiosk mode
   - No sleep/timeout interruptions
   - Reduces power consumption with optimized settings

## Setup Instructions

### Step 1: Initial App Setup
1. Install the app on your old Android device
2. Grant all required permissions:
   - Camera (required)
   - Storage (for user photos)
3. Complete first-time setup:
   - Download ML models (requires internet once)
   - Set admin password (or use default)
   - Enroll users with their face data

### Step 2: Enable Kiosk Mode
1. Open the app normally
2. Navigate to **Home** → **Settings**
3. Scroll to the **Security** section
4. Toggle **"Kiosk Mode"** ON
5. App will show a message and restart automatically
6. After restart, app opens directly to attendance screen

### Step 3: Pin the App (Recommended)
For maximum security, pin the app to prevent access to other features:

#### Android 9+ (Screen Pinning):
1. Go to device **Settings** → **Security** → **Screen Pinning**
2. Enable "Screen Pinning"
3. Open your attendance app
4. Tap **Recent Apps** button (square/three lines)
5. On the app card, tap the **pin icon** at the top
6. Confirm pinning

To unpin: Press and hold **Back + Recent Apps** buttons together

#### Android 10+ (Alternative):
1. Settings → Security → Advanced → Device admin apps
2. Enable your attendance app as device admin (if supported)

### Step 4: Mount the Device
1. Get a phone mount or stand
2. Position near door entrance at face height (~5-6 feet)
3. Ensure good lighting (avoid backlighting)
4. Angle slightly downward for optimal face capture
5. Connect to power source (keep plugged in)

### Step 5: Test the System
1. Have a enrolled user test face recognition
2. Try pressing back button → should show password dialog
3. Close app from recent apps → should auto-reopen
4. Verify screen stays on

## Using Kiosk Mode

### For Employees (Marking Attendance)
1. Simply look at the camera
2. Face will be detected automatically
3. Attendance marked (check-in/check-out auto-detected)
4. Success message displayed
5. Ready for next person immediately

### For Administrators (Accessing Settings)
1. Tap the **Settings icon** (⚙️) in top-right corner
2. OR press device **Back button**
3. Enter password when prompted (default: `admin123`)
4. Access Home screen with all features:
   - Enroll new users
   - View attendance history
   - Export reports
   - Change settings
   - Disable kiosk mode

## Changing the Kiosk Password

### From Normal Mode (before enabling kiosk):
1. Settings → Security → "Change Kiosk Password"
2. Enter current password
3. Enter new password (minimum 4 characters)
4. Confirm new password
5. Password saved securely

### From Kiosk Mode:
1. Authenticate with current password
2. Navigate to Settings
3. Change password as described above
4. Back button returns to kiosk mode

## Disabling Kiosk Mode

1. From attendance screen, tap Settings icon or press Back
2. Enter administrator password
3. Navigate to **Settings**
4. Toggle **"Kiosk Mode"** OFF
5. App returns to normal operation
6. Restart recommended

## Troubleshooting

### App doesn't auto-restart after closing
- **Solution**: Check if battery optimization is enabled
  - Settings → Apps → Muxro Attendance → Battery → "Unrestricted"
  - Settings → Battery → Battery optimization → Select app → Don't optimize

### Screen turns off despite kiosk mode
- **Solution**: Adjust device sleep settings
  - Settings → Display → Sleep → Set to maximum time
  - Or: Settings → Developer options → Stay awake (when charging)

### Can't unpin the app
- **Solution**: Press and hold **Back + Recent Apps** simultaneously for 2-3 seconds
- Alternative: Restart the device

### Forgot kiosk password
- **Solution**: 
  1. Uninstall and reinstall the app (loses all data), OR
  2. Clear app data: Settings → Apps → Muxro Attendance → Storage → Clear Data
  3. Password resets to default: `admin123`
  4. Re-enroll all users

### Face recognition not working in kiosk mode
- Check lighting conditions
- Clean camera lens
- Verify users are properly enrolled
- Check recognition threshold in Settings

### App closes unexpectedly
- Ensure sufficient storage space
- Check for Android system updates
- Disable battery optimization for the app
- Grant all required permissions

## Best Practices

### Device Selection
- Use Android 8.0 or higher
- Minimum 2GB RAM
- Good front camera (5MP+)
- Reliable power source

### Placement
- Mount at face height (5-6 feet)
- Well-lit area (avoid direct sunlight)
- Stable mounting (no wobbling)
- Within reach if touch needed
- Protected from rain/moisture

### Maintenance
- Clean camera lens weekly
- Check battery/charging monthly
- Review attendance logs regularly
- Update app when new versions available
- Backup database periodically

### Security
- Change default password immediately
- Use strong passwords (8+ characters)
- Don't share password with unauthorized users
- Enable screen pinning for public areas
- Regularly audit access logs

### Power Management
- Keep device plugged in always
- Use quality charger (original or certified)
- Monitor device temperature
- Consider power backup (UPS)

## Advanced Configuration

### Customize Recognition Settings
From authenticated settings:
- **Recognition Threshold**: 70-95% (higher = stricter)
- **Duplicate Window**: 1-60 minutes
- **Liveness Detection**: Enable for anti-spoofing
- **Feedback**: Enable sound/haptic for user confirmation

### Network Requirements
- **Initial Setup**: Internet required for ML model download (~50MB)
- **Operation**: Completely offline
- **Updates**: Internet for app updates only

### Device Admin Mode (Optional)
For enterprise deployments:
1. Use Android Device Policy or similar MDM
2. Set app as default launcher
3. Disable access to settings/notifications
4. Restrict app installation

## Permissions Explained

### Required Permissions
- **Camera**: Face recognition and capture
- **Storage**: Save user photos and embeddings
- **Foreground Service**: Keep app running in kiosk mode
- **Wake Lock**: Keep screen on

### Optional Permissions
- **Internet**: Only for initial model download
- **Vibration**: Haptic feedback
- **System Alert Window**: Enhanced kiosk overlays (not used by default)

## Technical Details

### Kiosk Service
- Runs as foreground service (persistent notification)
- Automatically restarts app if closed
- START_STICKY flag ensures resilience
- Handles task removal events

### Security Features
- Encrypted password storage (AES-256)
- No network communication during operation
- Local-only face recognition
- No cloud/external dependencies

### Battery Impact
- Optimized for continuous operation
- Minimal CPU usage when idle
- Camera only active during detection
- Efficient ML inference

## Support & Feedback

### Common Questions
**Q: Can multiple devices share the same user database?**
A: No, each device maintains its own local database. For multi-device setup, enroll users on each device separately.

**Q: How many users can be enrolled?**
A: Recommended maximum is 100 users for optimal performance. Supports more but recognition speed may decrease.

**Q: Does it work in low light?**
A: Requires adequate lighting for reliable face detection. Consider adding supplemental lighting if needed.

**Q: Can I backup the data?**
A: Yes, use the Export feature to save attendance records. Database backup feature is planned for future update.

### Getting Help
- Check this guide first
- Review other documentation files in project
- Check app Settings → About for version info
- Contact: Muxro Technologies

## Version History

### v1.0.0 - Kiosk Mode Release
- ✅ Auto-launch to attendance screen
- ✅ Auto-restart on app close
- ✅ Password-protected admin access
- ✅ Screen pinning support
- ✅ Keep screen on
- ✅ Foreground service for reliability
- ✅ Settings icon for quick access
- ✅ Encrypted password storage
- ✅ Configurable in Settings

---

## Quick Start Checklist

- [ ] Install app on device
- [ ] Grant all permissions
- [ ] Download ML models (first time)
- [ ] Create/enroll at least one test user
- [ ] Enable Kiosk Mode in Settings
- [ ] Set custom password (change from default)
- [ ] Pin the app to home screen
- [ ] Mount device at entrance
- [ ] Connect to power
- [ ] Test with enrolled user
- [ ] Verify auto-restart works
- [ ] Confirm password protection works

---

**Your attendance kiosk is now ready! Mount it at your door and let it do the work. 🎉**
