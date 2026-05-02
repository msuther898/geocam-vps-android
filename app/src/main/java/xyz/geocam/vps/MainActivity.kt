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
import xyz.geocam.vps.ar.ArSessionManager
import xyz.geocam.vps.ui.HelpScreen
import xyz.geocam.vps.ui.MapScreen

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
                        else -> MapScreen(
                            vm = vm,
                            sessionProvider = { ar.session },
                            onOpenHelp = { screen = "help" },
                        )
                    }
                }
            }
        }
    }

    private fun initArSession() {
        lifecycleScope.launch {
            if (ar.create()) {
                ar.resume()
                vm.setArError(null)
            } else {
                vm.setArError(ar.failureReason ?: "ARCore unavailable")
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
