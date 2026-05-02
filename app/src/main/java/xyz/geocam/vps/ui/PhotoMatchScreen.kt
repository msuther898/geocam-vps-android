package xyz.geocam.vps.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import xyz.geocam.vps.MainViewModel
import xyz.geocam.vps.photo.PhotoCapture

@Composable
fun PhotoMatchScreen(
    vm: MainViewModel,
    onClose: () -> Unit,
    onMatchReady: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val heading by vm.heading.collectAsState()
    val matchInProgress by vm.matchInProgress.collectAsState()

    val capture = remember { PhotoCapture(context) }
    var ready by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { pv ->
                    capture.bind(
                        lifecycleOwner = lifecycleOwner,
                        previewView = pv,
                        onReady = { ready = true },
                        onError = { bindError = it.message },
                    )
                }
            },
        )

        // Top bar
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 24.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("Cancel", color = Color.White) }
            Spacer(Modifier.size(12.dp))
            val degrees = (heading * 180.0 / Math.PI).toInt().toString().padStart(3, ' ')
            Text(
                "Heading $degrees°",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // Hint
        if (!ready && bindError == null) {
            Text(
                "Starting camera…",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (bindError != null) {
            Text(
                "Camera error: $bindError",
                color = Color(0xFFEF4444),
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC0F172A))
                    .padding(12.dp),
            )
        }

        // Shutter button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(76.dp)
                .clip(CircleShape)
                .background(if (matchInProgress) Color(0x99FFFFFF) else Color.White)
        ) {
            if (matchInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                    color = Color(0xFF22D3EE),
                )
            } else if (ready && bindError == null) {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                val photo = capture.takePhoto()
                                vm.runPhotoMatch(photo)
                                onMatchReady()
                            }.onFailure { bindError = it.message }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                ) { Text("📷") }
            }
        }

        // Hint above shutter
        if (!matchInProgress) {
            Text(
                "Hold steady, point at the scene around you",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 130.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
