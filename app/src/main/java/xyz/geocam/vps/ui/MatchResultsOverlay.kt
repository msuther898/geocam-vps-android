package xyz.geocam.vps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.geocam.vps.photo.Confidence
import xyz.geocam.vps.photo.MatchResult

@Composable
fun MatchResultsOverlay(
    result: MatchResult,
    onAccept: (rank: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC0F172A))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            ConfidenceBadge(result.confidence)
            Spacer(Modifier.width(8.dp))
            Text(
                "${result.backendName} · ${result.inferenceMs} ms · margin ${"%.3f".format(result.topMargin)}",
                color = Color(0xFFCBD5E1), fontSize = 11.sp,
            )
        }
        if (result.confidence == Confidence.LOW) {
            Text(
                "Low confidence — try an elevated angle, more landmark in frame, or move outside the cached region.",
                color = Color(0xFFF59E0B), fontSize = 11.sp,
            )
        }
        result.candidates.forEach { c ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "#${c.rank}",
                    color = when (c.rank) { 1 -> Color(0xFF22D3EE); 2 -> Color(0xFFFBBF24); else -> Color(0xFFF472B6) },
                    fontSize = 14.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${"%.5f".format(c.latLng.latitude)}, ${"%.5f".format(c.latLng.longitude)}",
                    color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text("${"%.2f".format(c.score)}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onAccept(c.rank) }) { Text("Use") }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Dismiss", color = Color(0xFF94A3B8)) }
        }
    }
}

@Composable
private fun ConfidenceBadge(c: Confidence) {
    val (bg, fg, label) = when (c) {
        Confidence.HIGH -> Triple(Color(0xFF052E1A), Color(0xFF22C55E), "HIGH")
        Confidence.MEDIUM -> Triple(Color(0xFF3F2A05), Color(0xFFFBBF24), "MEDIUM")
        Confidence.LOW -> Triple(Color(0xFF3F0A0A), Color(0xFFEF4444), "LOW")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, color = fg, fontSize = 10.sp)
    }
}
