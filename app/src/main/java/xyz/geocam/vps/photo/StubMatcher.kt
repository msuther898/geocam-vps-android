package xyz.geocam.vps.photo

import com.google.android.gms.maps.model.LatLng
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

/**
 * Phase 1.7 placeholder. Returns three plausible candidates around the search
 * center so the rest of the pipeline (capture -> match -> map markers -> select)
 * can be exercised before the ONNX model is integrated.
 */
class StubMatcher : PhotoMatcher {
    override val name = "stub"

    override suspend fun match(
        photo: File,
        searchCenter: LatLng,
        searchRadiusMeters: Double,
        headingRad: Double,
    ): MatchResult {
        val started = System.currentTimeMillis()
        val rng = Random(photo.length().toInt() xor photo.lastModified().toInt())
        val candidates = (1..3).map { i ->
            val r = rng.nextDouble(searchRadiusMeters * 0.1, searchRadiusMeters)
            val theta = rng.nextDouble(0.0, 2 * PI)
            val east = r * cos(theta)
            val north = r * kotlin.math.sin(theta)
            val dLat = north / 111_320.0
            val dLon = east / (111_320.0 * cos(searchCenter.latitude * PI / 180.0))
            MatchCandidate(
                rank = i,
                latLng = LatLng(searchCenter.latitude + dLat, searchCenter.longitude + dLon),
                score = 1.0f - i * 0.15f,
            )
        }
        return MatchResult(
            photo = photo,
            capturedHeadingRad = headingRad,
            candidates = candidates,
            backendName = name,
            inferenceMs = System.currentTimeMillis() - started,
        )
    }
}
