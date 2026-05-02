package xyz.geocam.vps.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.json.JSONObject
import xyz.geocam.vps.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val notes: String,
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class UpToDate(val current: String) : UpdateState()
    data class Available(val current: String, val release: ReleaseInfo) : UpdateState()
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState()
    data class Ready(val release: ReleaseInfo, val apk: File) : UpdateState()
    data class Failed(val reason: String) : UpdateState()
}

class Updater(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val owner = BuildConfig.GITHUB_OWNER
    private val repo = BuildConfig.GITHUB_REPO
    private val current = BuildConfig.VERSION_NAME_SHORT

    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext UpdateState.Failed("HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").removePrefix("v")
                val name = json.optString("name", tag)
                val notes = json.optString("body", "")
                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                var apkSize = 0L
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        val n = a.optString("name", "")
                        if (n.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = a.optString("browser_download_url", "")
                            apkSize = a.optLong("size", 0)
                            break
                        }
                    }
                }
                if (tag.isBlank() || apkUrl.isBlank()) {
                    return@withContext UpdateState.Failed("No APK in latest release")
                }
                if (isNewer(latest = tag, current = current)) {
                    UpdateState.Available(current, ReleaseInfo(tag, name, apkUrl, apkSize, notes))
                } else {
                    UpdateState.UpToDate(current)
                }
            }
        }.getOrElse { UpdateState.Failed(it.message ?: it::class.java.simpleName) }
    }

    suspend fun download(release: ReleaseInfo, onProgress: (Float) -> Unit): UpdateState = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val out = File(dir, "geocam-vps-${release.tag}.apk")
        val req = Request.Builder().url(release.apkUrl).build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext UpdateState.Failed("HTTP ${resp.code}")
                val total = resp.body?.contentLength() ?: release.apkSizeBytes
                val source = resp.body!!.source()
                out.sink().buffer().use { sink ->
                    var read = 0L
                    val buf = okio.Buffer()
                    while (true) {
                        val n = source.read(buf, 64 * 1024)
                        if (n == -1L) break
                        read += n
                        sink.write(buf, n)
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                    sink.flush()
                }
            }
            UpdateState.Ready(release, out)
        }.getOrElse { UpdateState.Failed(it.message ?: it::class.java.simpleName) }
    }

    fun install(apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.e(TAG, "Install intent failed", it) }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val li = l.getOrNull(i) ?: 0
            val ci = c.getOrNull(i) ?: 0
            if (li != ci) return li > ci
        }
        return false
    }

    private fun parse(v: String): List<Int> =
        v.substringBefore('+').substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }

    companion object { private const val TAG = "Updater" }
}
