package xyz.geocam.vps

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.delay
import xyz.geocam.vps.ar.ArSessionManager
import xyz.geocam.vps.photo.OnnxPhotoMatcher
import xyz.geocam.vps.photo.StubMatcher
import xyz.geocam.vps.ui.HelpScreen
import xyz.geocam.vps.ui.MapScreen
import xyz.geocam.vps.ui.PhotoMatchScreen
import xyz.geocam.vps.ui.PhotoResultsScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private lateinit var ar: ArSessionManager

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) initArSession()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ar = ArSessionManager(this)

        vm.photoMatcher = runCatching { OnnxPhotoMatcher(applicationContext) }
            .onFailure { android.util.Log.e("MainActivity", "ONNX matcher init failed", it) }
            .getOrElse { StubMatcher() }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var screen by remember { mutableStateOf("map") }
                    LaunchedEffect(Unit) {
                        if (Permissions.allGranted(this@MainActivity)) {
                            initArSession()
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                        vm.checkForUpdate()
                    }
                    when (screen) {
                        "help" -> HelpScreen(
                            onClose = { screen = "map" },
                            onCheckUpdate = { vm.checkForUpdate() },
                        )
                        "photo" -> PhotoMatchScreen(
                            vm = vm,
                            onClose = { screen = "map" },
                            onMatchReady = { screen = "results" },
                        )
                        "results" -> PhotoResultsScreen(
                            vm = vm,
                            onAccept = { rank -> vm.acceptMatch(rank); screen = "map" },
                            onRetake = { screen = "photo" },
                            onViewOnMap = { screen = "map" },
                        )
                        else -> MapScreen(
                            vm = vm,
                            sessionProvider = { ar.session },
                            onOpenHelp = { screen = "help" },
                            onOpenPhoto = { screen = "photo" },
                        )
                    }
                }
            }
        }
    }

    private var requestedArInstall = false

    private fun initArSession() {
        lifecycleScope.launch {
            // Check ARCore APK availability and install via Play Store if missing.
            // Pixel devices ship 'Google Play Services for AR' but it can be
            // disabled or its update queued; without this the session creation
            // fails with UnavailableArcoreNotInstalledException.
            var avail = ArCoreApk.getInstance().checkAvailability(this@MainActivity)
            // Two transient states: UNKNOWN_CHECKING + UNKNOWN_TIMED_OUT — re-poll briefly.
            var tries = 0
            while (avail.isTransient && tries < 10) {
                delay(200)
                avail = ArCoreApk.getInstance().checkAvailability(this@MainActivity)
                tries++
            }
            when {
                avail == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    vm.setArError("This device doesn't support ARCore. Photo-match still works.")
                    return@launch
                }
                !avail.isInstalled -> {
                    try {
                        val status = ArCoreApk.getInstance().requestInstall(
                            this@MainActivity, !requestedArInstall
                        )
                        when (status) {
                            ArCoreApk.InstallStatus.INSTALLED -> { /* proceed */ }
                            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                                requestedArInstall = true
                                vm.setArError("Installing 'Google Play Services for AR'… reopen app after Play Store finishes.")
                                return@launch
                            }
                        }
                    } catch (e: Exception) {
                        vm.setArError("ARCore install failed: ${e.message ?: e::class.java.simpleName}")
                        return@launch
                    }
                }
            }
            if (ar.create()) {
                ar.resume()
                vm.setArError(null)
            } else {
                vm.setArError(ar.failureReason ?: "ARCore session create failed")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ar.session != null) ar.resume()
    }

    override fun onPause() {
        super.onPause()
        ar.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        ar.destroy()
    }
}
