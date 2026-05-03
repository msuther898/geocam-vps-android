# GeoCam VPS — Android

GPS-free visual positioning research prototype. Two modes:

1. **📷 Photo match (headline feature)** — Snap a photo, on-device DINOv2-Base encoder runs cross-view matching against pre-embedded satellite tiles, returns top-3 candidate locations with confidence (HIGH/MEDIUM/LOW). Works with no GPS, no network, no ARCore.
2. **Live tracking (legacy)** — Tap the satellite map to drop an anchor, walk, ARCore visual-inertial odometry dead-reckons your position; cyan feature points project onto the map.

Built around **2404 Prospect Ave, Hermosa Beach** — 1,409 cached Google satellite tiles (z=18–20) bundled in the APK; 272 z=19 tiles pre-embedded for cross-view match.

## Install

1. Open the [latest release](https://github.com/msuther898/geocam-vps-android/releases/latest) on your phone
2. Download the `.apk`
3. Allow "Install unknown apps" for your downloader
4. Tap the APK → Install
5. Grant camera permission on first launch

**After v0.1.8, the app self-updates** — open the app → tap **Update** (or wait for the auto-check on launch) → Download → Install. All future builds are signed with the same stable debug key, so installs always succeed in place.

## How to use

### Photo-match flow
- Open app → tap **📷 Where am I?** (bottom of map)
- Take a photo (rooftop / balcony / overlook = much better recall than ground level — see below)
- Wait ~150–400 ms (NNAPI first-call compile takes 3–10 s extra; subsequent matches are fast)
- Review screen shows your photo + the 3 candidate aerial tiles + a confidence pill
  - 🟢 HIGH — top1 cosine > 0.70 AND margin to top-2 > 0.04
  - 🟡 MEDIUM — top1 > 0.55 OR margin > 0.025
  - 🔴 LOW — likely wrong; retake from a more distinctive viewpoint
- Tap **Use #N** to set that location as your map anchor

### Live tracking flow
- Tap any point on the satellite map → drops an Anchor pin
- Walk; ARCore VIO updates the Pose pin (~30 Hz)
- Cyan dots = ARCore feature points re-projected through your anchor + compass heading
- Stats panel: AR fps, current/kept feature counts, raw AR translation
- Reset clears the anchor; tap again to start a new walk

### Why elevation matters for photo-match

The cross-view encoder pairs your ground photo with overhead aerial tiles. The closer your viewpoint gets to the satellite's, the more shared content (rooftops, street layout, water/sand transitions). Empirically:

| Camera height | Recall@1 |
|---|---|
| Ground level (phone in hand walking) | 25–60% |
| Elevated 5–15 m (rooftop, deck, overlook) | 60–80% |
| Drone, 50–150 m | 90%+ |

Test with elevated shots first to calibrate.

## Architecture

```
xyz.geocam.vps/
  MainActivity.kt              single Activity, Compose host, ARCore install gate
  VpsApp.kt                    Application class
  MainViewModel.kt             pose/anchor/tracking/match state
  Permissions.kt               runtime perms helper
  ar/
    ArSessionManager.kt        ARCore session lifecycle (motion tracking only — NOT Geospatial)
    ArBackgroundRenderer.kt    minimal GL renderer feeding ARCore the camera texture
    GlUtils.kt                 OES texture create
  vio/
    PoseIntegrator.kt          ARCore translation -> ENU offset -> lat/lon
    Geo.kt                     lat/lon math
    FeaturePointTracker.kt     accumulates ARCore feature points by stable id
  sensors/
    CompassSource.kt           TYPE_ROTATION_VECTOR -> true-north heading
  photo/
    PhotoMatcher.kt            interface (suspend match)
    OnnxPhotoMatcher.kt        ONNX Runtime Mobile + NNAPI EP, cosine-sim against pre-embedded tiles
    StubMatcher.kt             fallback / dev placeholder
    PhotoCapture.kt            CameraX ImageCapture wrapper
  tiles/
    TileProvider.kt            interface (deferred phase 3+ on-the-fly fetch)
    GoogleMapsTileProvider.kt  HTTP impl stub
  update/
    Updater.kt                 GitHub Releases self-updater (FileProvider install intent)
  ui/
    MapScreen.kt               split: ARCore camera (top) + satellite map (bottom)
    PhotoMatchScreen.kt        full-screen camera + shutter
    PhotoResultsScreen.kt      photo + 3 tile thumbnails + confidence
    HelpScreen.kt              in-app /help
    UpdateBanner.kt            available/checking/up-to-date/failed states
    MatchResultsOverlay.kt     compact results overlay on the map
```

### Bundled assets

- `assets/tiles/{z}/{x}/{y}.jpg` — 1,409 Google satellite tiles, z=18–20, ~32 MB
- `assets/ml/ground_encoder_int8.onnx` — DINOv2-Base ViT-B/14 INT8, ~83 MB
- `assets/ml/aerial_embeddings.bin` — 272 × 768 float32, L2-normalized, ~0.8 MB
- `assets/ml/aerial_index.json` — tile (z, x, y) + lat/lon center, ~36 KB

Total APK ≈ 200 MB. Most of that is the encoder.

### ML pipeline (separate repo)

`C:\Users\myles\Documents\GitHub\geocam-vps-ml\` — re-runnable Python scripts:

- `export_encoder.py` — `timm` → DINOv2 → ONNX FP32 → INT8 dynamic quantization
- `embed_tiles.py` — pre-embed every cached aerial tile, output `.bin` + index JSON

Re-run any time you swap encoders. Currently using `vit_base_patch14_dinov2.lvd142m`. Drop in Sample4Geo / GeoDTR+ later for cross-view-trained features.

### Tile cache (separate repo)

`C:\Users\myles\Documents\GitHub\geocam-vps-cache\` — `scripts/fetch_tiles.py` pulls Google Map Tiles API for any address+radius. Currently scoped to 500 m around 2404 Prospect Ave at z=18–20.

## Build locally

Android Studio Hedgehog or later, JDK 17, Android SDK 34.

```
cp local.properties.example local.properties
# add MAPS_API_KEY=<your Google Maps Platform key>
./gradlew :app:assembleDebug
```

APK lands in `app/build/outputs/apk/debug/`.

## CI

`.github/workflows/build-apk.yml` on every push to `main`:

1. Checkout, JDK 17, Android SDK, Gradle 8.10
2. Generate stable debug keystore on first run, commit it back to the repo via `GITHUB_TOKEN` (no re-trigger). All future builds sign with the same cert → in-place self-update works.
3. Inject `MAPS_API_KEY` repo secret into `local.properties`
4. `gradle :app:assembleDebug`
5. Tag `v0.1.<run_number>`, create release, attach APK

The app's self-updater polls `releases/latest` against `BuildConfig.VERSION_NAME_SHORT`.

### Required repo secret

`MAPS_API_KEY` — Google Maps Platform key with **Maps SDK for Android** + **Map Tiles API** enabled. Without it builds still complete; map tiles just don't render.

## Status

| Version | Highlights |
|---|---|
| v0.1.8 | First stable-key signed APK (in-place updates work from here onward) |
| v0.1.9–v0.1.11 | Photo capture UI, stub matcher, real ONNX matcher (DINOv2-Small INT8 CPU) |
| v0.1.12 | DINOv2-Base + NNAPI GPU acceleration + confidence gating |
| v0.1.14 | Photo+tile review screen + ARCore install prompt |

## Roadmap

- [ ] Swap encoder to a cross-view-trained model (Sample4Geo / GeoDTR+) — expect r@1 +15–25 pts
- [ ] Bigger tile region — pre-embed neighboring zip codes, ship as separate "tile pack" downloads
- [ ] EKF fusion of photo-match fixes + ARCore VIO between fixes (back to live tracking, but with cross-view drift correction)
- [ ] Variant build flavor without ARCore (PDR + photo-match only) for non-AR-capable phones
- [ ] On-the-fly tile fetch + on-device aerial embedding for arbitrary regions
- [ ] iOS port (CameraX → AVFoundation, ARCore → ARKit, same ONNX model)

## Plan docs

`docs/plans/2026-05-02-phase-1-map-imu-arcore-plan.md`

## License

Research prototype. Google Maps tile usage is research-only — do not redistribute tiles or derived embeddings.
