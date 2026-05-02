package xyz.geocam.vps.photo

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin coroutine wrapper around CameraX ImageCapture.
 */
class PhotoCapture(private val context: Context) {

    private var imageCapture: ImageCapture? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                provider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture
                provider.bindToLifecycle(
                    lifecycleOwner,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                )
                onReady()
            }.onFailure(onError)
        }, ContextCompat.getMainExecutor(context))
    }

    suspend fun takePhoto(): File = suspendCancellableCoroutine { cont ->
        val cap = imageCapture ?: run {
            cont.resumeWithException(IllegalStateException("Camera not bound"))
            return@suspendCancellableCoroutine
        }
        val outDir = File(context.filesDir, "photos").apply { mkdirs() }
        val name = "photo-${SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())}.jpg"
        val out = File(outDir, name)
        val opts = ImageCapture.OutputFileOptions.Builder(out).build()
        cap.takePicture(
            opts,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    cont.resume(out)
                }
                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            },
        )
    }
}
