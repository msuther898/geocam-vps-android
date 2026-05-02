package xyz.geocam.vps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.geocam.vps.BuildConfig

@Composable
fun HelpScreen(onClose: () -> Unit, onCheckUpdate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("GeoCam VPS — Help", fontSize = 22.sp)
        Text("Version ${BuildConfig.VERSION_NAME}")
        Text(" ")
        Text("What this does", fontSize = 16.sp)
        Text("GPS-free visual positioning prototype. Tap the map to set your starting pose, then walk. ARCore + IMU dead-reckons your position; cross-view localization comes in phase 3.")
        Text(" ")
        Text("Quick start", fontSize = 16.sp)
        Text("1. Allow camera permission on first launch.\n2. Open the satellite map and pinch-zoom to your location near 2404 Prospect Ave, Hermosa Beach.\n3. Tap once to drop the initial pose.\n4. Walk. The blue dot updates from ARCore + compass.")
        Text(" ")
        Text("Troubleshooting", fontSize = 16.sp)
        Text("• Status pill stuck on PAUSED — point camera at well-lit, textured ground for 3 seconds.\n• Pose drifts heading-wrong — compass at startup was off; tap reset, point phone toward true north, place anchor again.\n• No map tiles — check that MAPS_API_KEY was set when this APK was built.")
        Text(" ")
        Text("Updates", fontSize = 16.sp)
        Text("This app pulls new builds straight from GitHub Releases. Tap below to check now.")
        Button(onClick = onCheckUpdate) { Text("Check for update") }
        Text(" ")
        Text("Feedback", fontSize = 16.sp)
        Text("Report bugs / ideas at github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/issues")
        Text(" ")
        Button(onClick = onClose) { Text("Back") }
    }
}
