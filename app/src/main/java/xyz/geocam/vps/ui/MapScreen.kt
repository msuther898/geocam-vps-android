package xyz.geocam.vps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.ar.core.TrackingState
import xyz.geocam.vps.MainViewModel
import xyz.geocam.vps.update.UpdateState

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

    val cameraState: CameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(HERMOSA_DEFAULT, 18f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            onMapClick = { latLng ->
                val s = sessionProvider() ?: return@GoogleMap
                runCatching {
                    val frame = s.update()
                    val t = frame.camera.pose.translation
                    vm.placeAnchor(latLng, t[0], t[1], t[2])
                }
            },
        ) {
            anchor?.let { Marker(state = MarkerState(position = it), title = "Anchor") }
            pose?.let { Marker(state = MarkerState(position = it), title = "Pose") }
        }

        // PIP camera preview, bottom-right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(96.dp, 128.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        ) {
            CameraPreviewView(
                sessionProvider = sessionProvider,
                onPose = { x, y, z, state -> vm.onArPose(x, y, z, state) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Status row, top
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 12.dp, end = 12.dp)
                .fillMaxSize(0.95f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusBar(tracking = tracking, heading = heading, anchor = anchor, pose = pose)
            UpdateBanner(
                state = update,
                onDownload = vm::beginDownload,
                onInstall = vm::installReady,
            )
        }

        // Bottom controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onOpenHelp) { Text("Help") }
            Button(onClick = vm::reset) { Text("Reset") }
            Button(onClick = vm::checkForUpdate) { Text("Update") }
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

        // Recenter on first anchor
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
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC0F172A))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Text("AR: $label", color = Color.White)
        val degrees = (heading * 180.0 / Math.PI).toInt().toString().padStart(3, ' ')
        Text("Hdg: $degrees°", color = Color(0xFF94A3B8))
        pose?.let {
            Text("${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)}",
                color = Color(0xFF94A3B8))
        }
        if (anchor == null) Text("(no anchor)", color = Color(0xFFF59E0B))
    }
    Spacer(Modifier.height(0.dp))
}
