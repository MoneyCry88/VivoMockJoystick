# Mock Joystick Test – vivo Y50 / Android 10

Prototype for Android's official developer Mock Location feature.

## Functions
- Starts from the phone's current GPS position.
- Virtual joystick at fixed 9.3 km/h.
- Three-finger gesture toggles joystick visibility.
- OpenStreetMap map; tap any point to set the simulated position there.
- Start/stop mock location.
- Return to real position.

## Build
1. Open this folder in Android Studio (JDK 17).
2. Let Gradle sync and install Android SDK 35 if prompted.
3. Build > Build APK(s), or run directly on the vivo Y50.

## Phone setup
1. Enable Developer options.
2. Install the app.
3. Developer options > Select mock location app > `Mock Joystick Test`.
4. Grant location permission to the app.
5. Open the app and press START.

## Notes
- Android 10 / API 29 is the minimum version for this prototype.
- The map requires internet access because it uses OpenStreetMap tiles and Leaflet from HTTPS.
- This is a development/testing tool. It contains no anti-detection or game-specific code.
