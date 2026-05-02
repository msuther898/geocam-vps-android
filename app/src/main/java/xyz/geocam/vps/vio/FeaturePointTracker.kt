package xyz.geocam.vps.vio

import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.ConcurrentHashMap

data class FeaturePoint(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val confidence: Float,
    val lastSeenMs: Long,
)

/**
 * Accumulates ARCore feature points keyed by their stable id. Drops anything
 * below the confidence threshold and FIFO-evicts when over [capacity].
 */
class FeaturePointTracker(
    private val capacity: Int = 300,
    private val minConfidence: Float = 0.3f,
) {
    private val points = ConcurrentHashMap<Int, FeaturePoint>()

    fun update(ids: IntBuffer, xyzc: FloatBuffer, nowMs: Long = System.currentTimeMillis()) {
        val n = ids.remaining()
        for (i in 0 until n) {
            val id = ids.get(ids.position() + i)
            val base = xyzc.position() + i * 4
            val c = xyzc.get(base + 3)
            if (c < minConfidence) continue
            val x = xyzc.get(base)
            val y = xyzc.get(base + 1)
            val z = xyzc.get(base + 2)
            points[id] = FeaturePoint(id, x, y, z, c, nowMs)
        }
        if (points.size > capacity) {
            val excess = points.size - capacity
            points.values
                .asSequence()
                .sortedBy { it.lastSeenMs }
                .take(excess)
                .toList()
                .forEach { points.remove(it.id) }
        }
    }

    fun snapshot(): List<FeaturePoint> = points.values.toList()

    fun clear() { points.clear() }
}
