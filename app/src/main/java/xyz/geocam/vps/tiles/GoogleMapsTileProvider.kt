package xyz.geocam.vps.tiles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Stub implementation. Phase 1 doesn't actually consume tiles (Maps SDK draws
 * the satellite layer itself). Phase 3 will use this to fetch tiles for the
 * cross-view matcher; the session token + URL pattern is stored here so the
 * pattern is locked in.
 */
class GoogleMapsTileProvider(private val apiKey: String) : TileProvider {
    override val name = "google-maps-tiles"
    override val maxZoom = 20

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var session: String? = null

    private suspend fun ensureSession(): String? = withContext(Dispatchers.IO) {
        session?.let { return@withContext it }
        val body = """{"mapType":"satellite","language":"en-US","region":"US","scale":"scaleFactor1x"}"""
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://tile.googleapis.com/v1/createSession?key=$apiKey")
            .post(body)
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JSONObject(resp.body!!.string())
                val s = json.optString("session", "")
                if (s.isNotBlank()) { session = s; s } else null
            }
        }.getOrNull()
    }

    override suspend fun fetch(z: Int, x: Int, y: Int): ByteArray? = withContext(Dispatchers.IO) {
        val s = ensureSession() ?: return@withContext null
        val req = Request.Builder()
            .url("https://tile.googleapis.com/v1/2dtiles/$z/$x/$y?session=$s&key=$apiKey")
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        }.getOrNull()
    }
}
