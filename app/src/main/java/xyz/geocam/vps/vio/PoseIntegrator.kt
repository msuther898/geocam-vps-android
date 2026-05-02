package xyz.geocam.vps.vio

import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos
import kotlin.math.sin

/**
 * Converts ARCore world-frame translations into ENU offsets and lat/lon by
 * locking against an anchor (lat/lon + heading) captured at session start.
 *
 * ARCore world frame is gravity-aligned at session start:
 *   +X = right of device, +Y = up, +Z = back (toward user)
 *   device-forward direction = -Z
 *
 * Given a true-north heading θ (radians, clockwise from N) of device-forward
 * at the anchor moment, the ENU offset of an ARCore translation
 * (x_a, y_a, z_a) relative to the anchor pose is:
 *
 *   east  =  x_a * cos(θ) - z_a * sin(θ)
 *   north = -x_a * sin(θ) - z_a * cos(θ)
 *   up    =  y_a
 */
class PoseIntegrator {
    private data class Anchor(
        val latLng: LatLng,
        val arX: Float,
        val arY: Float,
        val arZ: Float,
        val headingRad: Double,
    )

    @Volatile private var anchor: Anchor? = null

    fun setAnchor(latLng: LatLng, arX: Float, arY: Float, arZ: Float, headingRad: Double) {
        anchor = Anchor(latLng, arX, arY, arZ, headingRad)
    }

    fun clear() { anchor = null }

    fun hasAnchor(): Boolean = anchor != null

    fun integrate(arX: Float, arY: Float, arZ: Float): LatLng? {
        val a = anchor ?: return null
        val dx = (arX - a.arX).toDouble()
        val dz = (arZ - a.arZ).toDouble()
        val cosT = cos(a.headingRad)
        val sinT = sin(a.headingRad)
        val east = dx * cosT - dz * sinT
        val north = -dx * sinT - dz * cosT
        return Geo.offsetLatLng(a.latLng, east, north)
    }
}
