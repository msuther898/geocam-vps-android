package xyz.geocam.vps.ui

import android.content.Context
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.nio.FloatBuffer
import java.nio.IntBuffer
import xyz.geocam.vps.ar.ArBackgroundRenderer

@Composable
fun CameraPreviewView(
    sessionProvider: () -> Session?,
    onPose: (x: Float, y: Float, z: Float, state: TrackingState) -> Unit,
    onPointCloud: (ids: IntBuffer, xyzc: FloatBuffer) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val pcState = remember { LongArray(1) }  // last-seen point-cloud timestamp
    val renderer = remember {
        ArBackgroundRenderer(sessionProvider) { frame ->
            val cam = frame.camera
            val t = cam.pose.translation
            onPose(t[0], t[1], t[2], cam.trackingState)
            runCatching {
                val pc = frame.acquirePointCloud()
                try {
                    if (pc.timestamp != pcState[0]) {
                        pcState[0] = pc.timestamp
                        onPointCloud(pc.ids, pc.points)
                    }
                } finally {
                    pc.release()
                }
            }
        }
    }
    val surfaceRef = remember { mutableHolder<GLSurfaceView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx: Context ->
            GLSurfaceView(ctx).apply {
                preserveEGLContextOnPause = true
                setEGLContextClientVersion(3)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                // Pull this surface above the GoogleMap's SurfaceView so the
                // camera preview is actually visible in the PIP.
                setZOrderMediaOverlay(true)
                holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                surfaceRef.value = this
            }
        },
        update = { /* no-op */ },
    )

    DisposableEffect(Unit) {
        onDispose { surfaceRef.value?.onPause() }
    }
}

private class Holder<T>(var value: T)
private fun <T> mutableHolder(initial: T) = Holder(initial)
