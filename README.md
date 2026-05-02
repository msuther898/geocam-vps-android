# GeoCam VPS — Android (Phase 1)

GPS-free visual positioning research prototype. Tap the map, walk, watch the dot dead-reckon. Built around 2404 Prospect Ave, Hermosa Beach.

## What's in phase 1

- Google Maps satellite layer
- Tap-to-place initial pose (lat/lon)
- ARCore motion tracking (NOT Geospatial API)
- Compass-aligned ENU pose integration
- IMU sensor pipeline (rotation vector → heading)
- Live pose dot updated from ARCore deltas
- In-app `/help` screen
- **Self-updater**: app pulls new builds from GitHub Releases

Cross-view localization (the actual product) lands in phase 3.

## Install

1. Go to the [latest release](https://github.com/msuther898/geocam-vps-android/releases/latest)
2. Download the `.apk`
3. On the phone, allow "Install unknown apps" for whichever app you opened the file with
4. Tap the APK → Install

After this, the app self-updates: open the app → Update button (or wait for the auto-check on launch) → Download → Install.

## Build locally

You need Android Studio Hedgehog or later, JDK 17, Android SDK 34.

```
cp local.properties.example local.properties
# edit local.properties and paste your MAPS_API_KEY
./gradlew :app:assembleDebug
```

APK lands in `app/build/outputs/apk/debug/`.

## CI

Every push to `main` triggers `.github/workflows/build-apk.yml`:
1. Checkout, JDK 17, Android SDK, Gradle 8.10
2. Inject `MAPS_API_KEY` repo secret into `local.properties`
3. `gradle :app:assembleDebug`
4. Tag the build `v0.1.<run_number>` and publish a release with the APK attached

The app's self-updater watches that release feed.

### Required repo secret

`MAPS_API_KEY` — Google Maps Platform key with **Maps SDK for Android** + **Map Tiles API** enabled. Without it the build still completes, but tiles won't render.

## Plan

`docs/plans/2026-05-02-phase-1-map-imu-arcore-plan.md`

## License

Research prototype. Tile usage is research-only — see plan doc.
