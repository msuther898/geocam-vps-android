# GeoCam VPS — Help

This file mirrors the in-app `/help` screen.

## What this does

GPS-free visual positioning prototype. Tap the map to set your starting pose, then walk. ARCore + IMU dead-reckon your position. Cross-view localization (matching the camera against satellite tiles) is phase 3.

## Quick start

1. Allow camera permission on first launch.
2. Open the satellite map and pinch-zoom to your location near 2404 Prospect Ave, Hermosa Beach.
3. Tap once to drop the initial pose.
4. Walk. The dot updates from ARCore + compass.

## Troubleshooting

- **Status pill stuck on PAUSED** — point camera at well-lit, textured ground for 3 seconds.
- **Pose drifts heading-wrong** — compass at startup was off; tap Reset, point phone toward true north, place anchor again.
- **No map tiles** — `MAPS_API_KEY` was missing when the APK was built. Open an issue.

## Updates

App self-updates from [GitHub Releases](https://github.com/msuther898/geocam-vps-android/releases). Tap "Update" or it auto-checks on launch.

## Feedback

[Open an issue](https://github.com/msuther898/geocam-vps-android/issues/new/choose).
