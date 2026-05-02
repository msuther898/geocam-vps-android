package xyz.geocam.vps.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import xyz.geocam.vps.MainViewModel
import xyz.geocam.vps.photo.Confidence
import xyz.geocam.vps.photo.MatchCandidate
import xyz.geocam.vps.photo.MatchResult

@Composable
fun PhotoResultsScreen(
    vm: MainViewModel,
    onAccept: (rank: Int) -> Unit,
    onRetake: () -> Unit,
    onViewOnMap: () -> Unit,
) {
    val context = LocalContext.current
    val result by vm.matchResult.collectAsState()
    val r = result ?: run {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("No match yet", color = Color.White)
        }
        return
    }

    val photoBitmap = remember(r.photo.absolutePath) { decodeOriented(r.photo.absolutePath) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Match results", color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
            ConfidencePill(r.confidence)
        }
        Text(
            "${r.backendName} · ${r.inferenceMs} ms · margin ${"%.3f".format(r.topMargin)}",
            color = Color(0xFF94A3B8), fontSize = 11.sp,
        )
        if (r.confidence == Confidence.LOW) {
            Text(
                "Low confidence — try elevated angle, recognizable landmarks, or a more open scene.",
                color = Color(0xFFF59E0B), fontSize = 12.sp,
            )
        }

        // Photo preview
        Text("Your photo", color = Color(0xFFCBD5E1), fontSize = 12.sp)
        photoBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Captured photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } ?: Text("(failed to decode photo)", color = Color(0xFFEF4444))

        Text("Top matches (aerial tiles)", color = Color(0xFFCBD5E1), fontSize = 12.sp)

        r.candidates.forEach { c ->
            CandidateRow(
                c = c,
                tileBitmap = remember(c.tileZ, c.tileX, c.tileY) { loadTileBitmap(context, c) },
                onAccept = { onAccept(c.rank) },
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetake) { Text("Retake") }
            Button(onClick = onViewOnMap) { Text("View on map") }
            TextButton(onClick = { vm.dismissMatch(); onViewOnMap() }) { Text("Dismiss", color = Color(0xFF94A3B8)) }
        }
    }
}

@Composable
private fun CandidateRow(
    c: MatchCandidate,
    tileBitmap: Bitmap?,
    onAccept: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (tileBitmap != null) {
                Image(
                    bitmap = tileBitmap.asImageBitmap(),
                    contentDescription = "Tile #${c.rank}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text("?", color = Color(0xFF64748B), fontSize = 14.sp)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "#${c.rank}  score ${"%.3f".format(c.score)}",
                color = when (c.rank) { 1 -> Color(0xFF22D3EE); 2 -> Color(0xFFFBBF24); else -> Color(0xFFF472B6) },
                fontSize = 14.sp,
            )
            Text(
                "${"%.5f".format(c.latLng.latitude)}, ${"%.5f".format(c.latLng.longitude)}",
                color = Color.White, fontSize = 11.sp,
            )
            if (c.tileZ != null) {
                Text("z${c.tileZ}/${c.tileX}/${c.tileY}", color = Color(0xFF64748B), fontSize = 10.sp)
            }
        }
        Button(onClick = onAccept) { Text("Use") }
    }
}

@Composable
private fun ConfidencePill(c: Confidence) {
    val (bg, fg, label) = when (c) {
        Confidence.HIGH -> Triple(Color(0xFF052E1A), Color(0xFF22C55E), "HIGH")
        Confidence.MEDIUM -> Triple(Color(0xFF3F2A05), Color(0xFFFBBF24), "MEDIUM")
        Confidence.LOW -> Triple(Color(0xFF3F0A0A), Color(0xFFEF4444), "LOW")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = fg, fontSize = 11.sp)
    }
}

private fun loadTileBitmap(context: android.content.Context, c: MatchCandidate): Bitmap? {
    if (c.tileZ == null || c.tileX == null || c.tileY == null) return null
    val path = "tiles/${c.tileZ}/${c.tileX}/${c.tileY}.jpg"
    return runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

private fun decodeOriented(path: String): Bitmap? {
    val bmp = BitmapFactory.decodeFile(path) ?: return null
    val orientation = runCatching { ExifInterface(path) }
        .getOrNull()
        ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ?: ExifInterface.ORIENTATION_NORMAL
    if (orientation == ExifInterface.ORIENTATION_NORMAL) return bmp
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}
