package xyz.geocam.vps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.ar.core.TrackingState
import xyz.geocam.vps.MainViewModel

private val HERMOSA_DEFAULT = LatLng(33.872161, -118.392340)

@Composable
fun MapScreen(
    vm: MainViewModel,
    sessionProvider: () -> com.google.ar.core.Session?,
    onOpenHelp: () -> Unit,
) {
    val anchor by vm.anchor.collectAsState()
    val pose by vm.pose.collectAsState()
    val tracking by vm.tracking.collectAsState()
    val update by vm.update.collectAsState()
    val heading by vm.heading.collectAsState()
    val featurePoints by vm.featurePoints.collectAsState()
    val showFeaturePoints by vm.showFeaturePoints.collectAsState()
    val fps by vm.fps.collectAsState()
    val arT by vm.arTranslation.collectAsState()
    val frameFeats by vm.frameFeatureCount.collectAsState()
    val arError by vm.arError.collectAsState()

    val cameraState: CameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(HERMOSA_DEFAULT, 18f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top half: live camera
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                CameraPreviewView(
                    sessionProvider = sessionProvider,
                    onPose = { x, y, z, state -> vm.onArPose(x, y, z, state) },
                    onPointCloud = { ids, xyzc -> vm.onPointCloud(ids, xyzc) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Bottom half: satellite map
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraState,
                    properties = MapProperties(mapType = MapType.SATELLITE),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        compassEnabled = true,
                        myLocationButtonEnabled = false,
                        rotationGesturesEnabled = true,
                    ),
                    onMapClick = { latLng -> vm.placeAnchor(latLng) },
                ) {
                    anchor?.let { Marker(state = MarkerState(position = it), title = "Anchor") }
                    pose?.let { Marker(state = MarkerState(position = it), title = "Pose") }
                    if (showFeaturePoints) {
                        featurePoints.forEach { p ->
                            Circle(
                                center = p,
                                radius = 0.3,
                                fillColor = Color(0x9922D3EE),
                                strokeColor = Color(0x0022D3EE),
                                strokeWidth = 0f,
                            )
                        }
                    }
                }

                if (anchor == null) {
                    Text(
                        "Tap the map to set initial pose",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC0F172A))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Top overlay: status + stats + update banner (sits on top of camera half)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusBar(tracking = tracking, heading = heading, anchor = anchor, pose = pose)
            StatsPanel(
                fps = fps,
                arT = arT,
                frameFeats = frameFeats,
                trackedFeats = featurePoints.size,
                arError = arError,
            )
            UpdateBanner(
                state = update,
                onDownload = vm::beginDownload,
                onInstall = vm::installReady,
            )
        }

        // Bottom controls (overlay on map half)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onOpenHelp) { Text("Help") }
            Button(onClick = vm::reset) { Text("Reset") }
            Button(onClick = vm::checkForUpdate) { Text("Update") }
            Button(onClick = vm::toggleFeaturePoints) {
                Text(if (showFeaturePoints) "FP: ${featurePoints.size}" else "FP off")
            }
        }

        anchor?.let { a ->
            if (cameraState.position.zoom < 18f) {
                cameraState.move(CameraUpdateFactory.newLatLngZoom(a, 19f))
            }
        }
    }
}

@Composable
private fun StatusBar(
    tracking: TrackingState?,
    heading: Double,
    anchor: LatLng?,
    pose: LatLng?,
) {
    val label = when (tracking) {
        TrackingState.TRACKING -> "TRACKING"
        TrackingState.PAUSED -> "PAUSED"
        TrackingState.STOPPED -> "STOPPED"
        null -> "INIT"
    }
    val color = when (tracking) {
        TrackingState.TRACKING -> Color(0xFF22C55E)
        TrackingState.PAUSED -> Color(0xFFF59E0B)
        else -> Color(0xFF6B7280)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC0F172A))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Text("AR: $label", color = Color.White, fontSize = 13.sp)
        val degrees = (heading * 180.0 / Math.PI).toInt().toString().padStart(3, ' ')
        Text("Hdg: $degrees°", color = Color(0xFF94A3B8), fontSize = 13.sp)
        pose?.let {
            Text("${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)}",
                color = Color(0xFF94A3B8), fontSize = 13.sp)
        }
        if (anchor == null) Text("(no anchor)", color = Color(0xFFF59E0B), fontSize = 13.sp)
    }
    Spacer(Modifier.height(0.dp))
}

@Composable
private fun StatsPanel(
    fps: Int,
    arT: FloatArray,
    frameFeats: Int,
    trackedFeats: Int,
    arError: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC0F172A))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "ARCore  ${fps} fps   feats now: $frameFeats   kept: $trackedFeats",
            color = Color(0xFFCBD5E1), fontSize = 11.sp,
        )
        Text(
            "AR pose  x=${"%.2f".format(arT[0])}  y=${"%.2f".format(arT[1])}  z=${"%.2f".format(arT[2])} m",
            color = Color(0xFF94A3B8), fontSize = 11.sp,
        )
        if (arError != null) {
            Text("⚠ ARCore: $arError", color = Color(0xFFEF4444), fontSize = 11.sp)
        }
    }
}
