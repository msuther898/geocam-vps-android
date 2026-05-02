package xyz.geocam.vps.photo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * On-device cross-view matcher.
 *
 * Encoder: DINOv2-small (vit_small_patch14_dinov2.lvd142m), INT8 dynamic-quantized
 *   - Single-tower: same encoder for ground photo and aerial tile
 *   - Input: 1x3x224x224 float32, ImageNet-normalized
 *   - Output: 1x384 float32 (L2-normalized at compare time)
 *
 * Match: cosine similarity between L2-normalized photo embedding and pre-embedded
 * aerial tile embeddings (assets/ml/aerial_embeddings.bin), top-K returned.
 *
 * Aerial tile lat/lon centers come from assets/ml/aerial_index.json (computed
 * offline from the slippy-map (z, x, y) coords of every cached tile).
 */
class OnnxPhotoMatcher(private val context: Context) : PhotoMatcher {

    override val name = "dinov2-s-int8"

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val session: OrtSession by lazy { loadSession() }
    private val aerialEmbeddings: FloatArray by lazy { loadEmbeddings() }
    private val aerialIndex: List<TileEntry> by lazy { loadIndex() }
    private val embDim: Int by lazy { aerialIndex.firstOrNull()?.let { aerialEmbeddings.size / aerialIndex.size } ?: 384 }

    private data class TileEntry(val row: Int, val z: Int, val x: Int, val y: Int, val lat: Double, val lon: Double)

    private fun loadSession(): OrtSession {
        val bytes = context.assets.open("ml/ground_encoder_int8.onnx").use { it.readBytes() }
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
        }
        return env.createSession(bytes, opts)
    }

    private fun loadEmbeddings(): FloatArray {
        val bytes = context.assets.open("ml/aerial_embeddings.bin").use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val n = bytes.size / 4
        val out = FloatArray(n)
        bb.asFloatBuffer().get(out)
        return out
    }

    private fun loadIndex(): List<TileEntry> {
        val text = context.assets.open("ml/aerial_index.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val tiles = root.getJSONArray("tiles")
        val out = ArrayList<TileEntry>(tiles.length())
        for (i in 0 until tiles.length()) {
            val o = tiles.getJSONObject(i)
            out += TileEntry(
                row = o.getInt("row"),
                z = o.getInt("z"),
                x = o.getInt("x"),
                y = o.getInt("y"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
            )
        }
        return out
    }

    override suspend fun match(
        photo: File,
        searchCenter: LatLng,
        searchRadiusMeters: Double,
        headingRad: Double,
    ): MatchResult = withContext(Dispatchers.Default) {
        val started = System.currentTimeMillis()

        val bitmap = decodeOriented(photo)
        val input = preprocess(bitmap)
        val embedding = encode(input)
        l2Normalize(embedding)

        val scored = aerialIndex.map { tile ->
            val base = tile.row * embDim
            var dot = 0f
            for (i in 0 until embDim) {
                dot += aerialEmbeddings[base + i] * embedding[i]
            }
            tile to dot
        }.sortedByDescending { it.second }

        val candidates = scored.take(3).mapIndexed { i, (tile, score) ->
            MatchCandidate(
                rank = i + 1,
                latLng = LatLng(tile.lat, tile.lon),
                score = score,
                tileZ = tile.z, tileX = tile.x, tileY = tile.y,
            )
        }

        MatchResult(
            photo = photo,
            capturedHeadingRad = headingRad,
            candidates = candidates,
            backendName = name,
            inferenceMs = System.currentTimeMillis() - started,
        )
    }

    private fun decodeOriented(file: File): Bitmap {
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalStateException("Cannot decode photo: ${file.absolutePath}")
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL
        if (orientation == ExifInterface.ORIENTATION_NORMAL) return bmp
        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }

    private fun preprocess(bitmap: Bitmap): FloatArray {
        val side = 224
        val resized = Bitmap.createScaledBitmap(bitmap, side, side, true)
        val pixels = IntArray(side * side)
        resized.getPixels(pixels, 0, side, 0, 0, side, side)
        // CHW float32, ImageNet normalized
        val out = FloatArray(3 * side * side)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        var idxR = 0
        var idxG = side * side
        var idxB = 2 * side * side
        for (p in pixels) {
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            out[idxR++] = (r - mean[0]) / std[0]
            out[idxG++] = (g - mean[1]) / std[1]
            out[idxB++] = (b - mean[2]) / std[2]
        }
        return out
    }

    private fun encode(chw: FloatArray): FloatArray {
        val side = 224L
        val shape = longArrayOf(1, 3, side, side)
        val buffer: FloatBuffer = FloatBuffer.wrap(chw)
        OnnxTensor.createTensor(env, buffer, shape).use { tensor ->
            session.run(mapOf("pixel_values" to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val arr = result[0].value as Array<FloatArray>
                return arr[0].copyOf()
            }
        }
    }

    private fun l2Normalize(v: FloatArray) {
        var s = 0f
        for (x in v) s += x * x
        val n = sqrt(s)
        if (n > 0f) for (i in v.indices) v[i] /= n
    }
}
