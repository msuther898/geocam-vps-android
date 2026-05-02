package xyz.geocam.vps.ar

import android.content.Context
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.UnavailableException

class ArSessionManager(private val context: Context) {
    @Volatile var session: Session? = null
        private set

    enum class Status { UNINITIALIZED, INITIALIZING, READY, FAILED }

    @Volatile var status: Status = Status.UNINITIALIZED
        private set

    @Volatile var failureReason: String? = null
        private set

    fun create(): Boolean {
        if (session != null) return true
        status = Status.INITIALIZING
        return try {
            val s = Session(context)
            val config = Config(s).apply {
                focusMode = Config.FocusMode.AUTO
                planeFindingMode = Config.PlaneFindingMode.DISABLED
                lightEstimationMode = Config.LightEstimationMode.DISABLED
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            }
            s.configure(config)
            session = s
            status = Status.READY
            true
        } catch (e: UnavailableException) {
            failureReason = e.message ?: e::class.java.simpleName
            status = Status.FAILED
            Log.e(TAG, "ARCore unavailable", e)
            false
        } catch (e: Exception) {
            failureReason = e.message ?: e::class.java.simpleName
            status = Status.FAILED
            Log.e(TAG, "ARCore creation failed", e)
            false
        }
    }

    fun resume() {
        runCatching { session?.resume() }.onFailure {
            Log.e(TAG, "ARCore resume failed", it)
            status = Status.FAILED
            failureReason = it.message
        }
    }

    fun pause() {
        runCatching { session?.pause() }
    }

    fun destroy() {
        runCatching { session?.close() }
        session = null
    }

    fun trackingState(): TrackingState? =
        session?.let { runCatching { it.update().camera.trackingState }.getOrNull() }

    companion object { private const val TAG = "ArSessionManager" }
}
