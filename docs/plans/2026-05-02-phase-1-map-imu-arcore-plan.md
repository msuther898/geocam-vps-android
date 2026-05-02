# Phase 1 — Map + IMU + ARCore Dead-Reckoning

**Date:** 2026-05-02
**Slug:** phase-1-map-imu-arcore
**Owner:** Myles
**Goal:** Shareable debug APK that opens to a satellite map of Hermosa Beach, lets the user tap a point to place an initial pose, and dead-reckons that pose using ARCore + compass while the user walks.

## Scope

**In:**
- Kotlin + Jetpack Compose, single-Activity app
- Google Maps Compose with satellite layer (full-screen)
- Tap-to-place initial pose anchor
- ARCore session (motion tracking only — NOT Geospatial API)
- Compass heading captured at anchor time for ARCore→ENU rotation
- IMU sensors logged for debug (accel, gyro, mag)
- `PoseIntegrator`: ARCore pose deltas → ENU offset → lat/lon
- Live pose dot on map, updated at ~30 Hz
- `TileProvider` interface from day 1 (Google impl stub; cache-backed impl deferred to phase 3)
- Tracking-state status pill (TRACKING / PAUSED / STOPPED)
- Small camera preview (96×128 dp PIP) so user knows AR is alive
- In-app `/help` screen (Compose route)
- In-app `/feedback` button (deep-links to GitHub issues)

**Out (deferred to later phases):**
- EKF fusion (phase 2)
- Bootstrap particle filter (phase 2)
- Cross-view localization (phase 3)
- BEV synthesis (phase 4)
- Pre-cached corridor mode using the bundled 32 MB cache (phase 5) — cache exists at `C:\Users\myles\Documents\GitHub\geocam-vps-cache\` but is NOT bundled into the APK in phase 1
- Drift-correction / divergence recovery (phase 4)
- iOS port (phase 7+)

## Architecture

```
xyz.geocam.vps/
  MainActivity.kt              -- single activity, Compose host
  VpsApp.kt                    -- Application class, Maps + ARCore init
  MainViewModel.kt             -- holds Pose state, tracking state
  Permissions.kt               -- runtime perms helper
  ui/
    MapScreen.kt               -- GoogleMap + dot + status + PIP camera + buttons
    HelpScreen.kt              -- in-app /help
    CameraPreviewView.kt       -- AndroidView wrapping GLSurfaceView for ARCore
  ar/
    ArSessionManager.kt        -- ARCore lifecycle
    ArBackgroundRenderer.kt    -- minimal GL renderer drawing camera background
    GlUtils.kt                 -- shader compile + OES texture create
  vio/
    PoseIntegrator.kt          -- ARCore pose -> ENU -> lat/lon
    Geo.kt                     -- lat/lon <-> meters helpers
  sensors/
    CompassSource.kt           -- TYPE_ROTATION_VECTOR -> heading-from-north
  tiles/
    TileProvider.kt            -- interface (z, x, y -> bytes?)
    GoogleMapsTileProvider.kt  -- HTTP impl (placeholder for phase 3)
```

## Critical math

ARCore world frame (gravity-aligned, established at session start):
- +X = right of device at startup
- +Y = up (against gravity)
- +Z = back (toward user) at startup; device-forward = -Z

Given startup compass heading θ (radians clockwise from True North), an ARCore translation `(x_a, y_a, z_a)` maps to ENU as:
```
east  =  x_a * cos(θ) - z_a * sin(θ)
north = -x_a * sin(θ) - z_a * cos(θ)
up    =  y_a
```

Anchor lat/lon `(lat0, lon0)` + ENU offset → lat/lon:
```
lat = lat0 + (north / 111320)
lon = lon0 + (east / (111320 * cos(lat0)))
```

(Flat-earth approximation; valid within ±1 km of anchor at sub-meter error.)

## TDD Tier

**Optional.** Phase 1 is plumbing + UI integration; the math in `PoseIntegrator` is the only logic worth testing and is kept simple enough to verify by walking. EKF/cross-view (where TDD is required) lands in phase 2/3. Recording `Optional` per ship-it rules.

## Design Skill

**Skipped.** Native Android UI, not web. ship-it explicitly allows skip for native mobile.

## Build

- **Gradle:** AGP 8.5, Gradle 8.7, Kotlin 2.0
- **minSdk:** 26 (Android 8.0; ARCore requires API 24+, but 26 is the practical floor)
- **targetSdk / compileSdk:** 34
- **Package:** `xyz.geocam.vps`
- **Map lib:** `com.google.maps.android:maps-compose:6.1.0` + `com.google.android.gms:play-services-maps:19.0.0`
- **ARCore:** `com.google.ar:core:1.45.0` (raw, NOT Geospatial)
- **Compose BOM:** 2024.09.03
- **API key:** `local.properties` → `MAPS_API_KEY=...` (gitignored), wired into manifest via Gradle `manifestPlaceholders`

## Test (native — Chrome DevTools MCP not applicable)

Native mobile, web testing harness does not apply. Substitute gates:
1. Project opens in Android Studio without errors
2. Gradle sync succeeds
3. `./gradlew assembleDebug` produces `app-debug.apk`
4. APK installs on a real device (Android 8+, ARCore-supported)
5. App opens, map renders satellite at Hermosa Beach
6. Tap on map → red pin appears
7. AR status chip transitions PAUSED → TRACKING within ~3 seconds in well-lit space
8. Walking ~10 m visibly moves the pose dot

These are user-executed gates; this scaffolding session can only verify (1)–(2) and only after the user installs Android Studio.

## Results

_To be filled by user after first build._

- Gradle sync time: _____
- assembleDebug time: _____
- APK size: _____
- Device tested: _____
- Walk test outcome: _____

## Rollback

This is the first commit on a new repo. Rollback = `git revert <sha>` of the initial scaffold commit, leaving an empty repo. No production deployment, no users, no data — rollback is trivial.

If a downstream phase introduces a regression:
- `git revert <sha>` of the offending commit
- Rebuild debug APK, sideload to test devices
- No release tags exist yet; nothing to roll back to

## Open questions for next phases

1. Confirm Esri vs Google for tile licensing once moving from research → product
2. Bootstrap heading: compass alone or compass + 5 m walk-to-confirm?
3. Tile cache bundling strategy: ship in APK assets vs. download on first run vs. per-region pack?

## References

- Build plan thread: this conversation, 2026-05-02
- Tile cache: `C:\Users\myles\Documents\GitHub\geocam-vps-cache\` (1409 tiles, z=18–20, around 33.872161, -118.392340)
- ARCore HelloAR Kotlin sample: https://github.com/google-ar/arcore-android-sdk/tree/main/samples/hello_ar_kotlin
