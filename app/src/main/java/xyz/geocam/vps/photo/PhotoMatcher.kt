package xyz.geocam.vps.photo

import com.google.android.gms.maps.model.LatLng
import java.io.File

enum class Confidence { HIGH, MEDIUM, LOW }

data class MatchCandidate(
    val rank: Int,
    val latLng: LatLng,
    val score: Float,
    val tileZ: Int? = null,
    val tileX: Int? = null,
    val tileY: Int? = null,
)

data class MatchResult(
    val photo: File,
    val capturedHeadingRad: Double,
    val candidates: List<MatchCandidate>,
    val backendName: String,
    val inferenceMs: Long,
    val confidence: Confidence = Confidence.LOW,
    val topMargin: Float = 0f,
)

/**
 * Cross-view matcher contract. Live phase 1.7 ships [StubMatcher]; phase 1.8
 * swaps in an ONNX-Runtime-Mobile-backed Sample4Geo encoder.
 */
interface PhotoMatcher {
    val name: String
    suspend fun match(
        photo: File,
        searchCenter: LatLng,
        searchRadiusMeters: Double,
        headingRad: Double,
    ): MatchResult
}
