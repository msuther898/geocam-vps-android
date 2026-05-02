package xyz.geocam.vps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.FloatBuffer
import java.nio.IntBuffer
import xyz.geocam.vps.sensors.CompassSource
import xyz.geocam.vps.update.UpdateState
import xyz.geocam.vps.update.Updater
import xyz.geocam.vps.vio.FeaturePointTracker
import xyz.geocam.vps.vio.PoseIntegrator

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val poseIntegrator = PoseIntegrator()
    private val featureTracker = FeaturePointTracker()
    private val compass = CompassSource(app)
    private val updater = Updater(app)

    private val _anchor = MutableStateFlow<LatLng?>(null)
    val anchor: StateFlow<LatLng?> = _anchor.asStateFlow()

    private val _pose = MutableStateFlow<LatLng?>(null)
    val pose: StateFlow<LatLng?> = _pose.asStateFlow()

    private val _tracking = MutableStateFlow<TrackingState?>(null)
    val tracking: StateFlow<TrackingState?> = _tracking.asStateFlow()

    private val _heading = MutableStateFlow(0.0)
    val heading: StateFlow<Double> = _heading.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    private val _featurePoints = MutableStateFlow<List<LatLng>>(emptyList())
    val featurePoints: StateFlow<List<LatLng>> = _featurePoints.asStateFlow()

    private val _showFeaturePoints = MutableStateFlow(true)
    val showFeaturePoints: StateFlow<Boolean> = _showFeaturePoints.asStateFlow()

    @Volatile private var lastArX: Float = 0f
    @Volatile private var lastArY: Float = 0f
    @Volatile private var lastArZ: Float = 0f
    @Volatile private var lastFeatureFlushMs: Long = 0L

    init {
        viewModelScope.launch {
            compass.headings().collect { _heading.value = it }
        }
    }

    fun placeAnchor(latLng: LatLng) {
        poseIntegrator.setAnchor(latLng, lastArX, lastArY, lastArZ, _heading.value)
        _anchor.value = latLng
        _pose.value = latLng
    }

    fun onArPose(arX: Float, arY: Float, arZ: Float, state: TrackingState) {
        _tracking.value = state
        lastArX = arX; lastArY = arY; lastArZ = arZ
        if (state == TrackingState.TRACKING) {
            poseIntegrator.integrate(arX, arY, arZ)?.let { _pose.value = it }
        }
    }

    fun onPointCloud(ids: IntBuffer, xyzc: FloatBuffer) {
        val now = System.currentTimeMillis()
        featureTracker.update(ids, xyzc, now)
        // Project to lat/lon at most ~2 Hz to keep map recomposition cheap.
        if (now - lastFeatureFlushMs < 500L) return
        lastFeatureFlushMs = now
        if (!poseIntegrator.hasAnchor()) return
        val projected = featureTracker.snapshot().mapNotNull { fp ->
            poseIntegrator.integrate(fp.x, fp.y, fp.z)
        }
        _featurePoints.value = projected
    }

    fun toggleFeaturePoints() {
        _showFeaturePoints.value = !_showFeaturePoints.value
    }

    fun reset() {
        poseIntegrator.clear()
        featureTracker.clear()
        _anchor.value = null
        _pose.value = null
        _featurePoints.value = emptyList()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _update.value = UpdateState.Checking
            _update.value = updater.check()
        }
    }

    fun beginDownload() {
        val s = _update.value as? UpdateState.Available ?: return
        viewModelScope.launch {
            _update.value = UpdateState.Downloading(s.release, 0f)
            val result = updater.download(s.release) { p ->
                _update.value = UpdateState.Downloading(s.release, p)
            }
            _update.value = result
        }
    }

    fun installReady() {
        val s = _update.value as? UpdateState.Ready ?: return
        updater.install(s.apk)
    }
}
