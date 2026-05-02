package xyz.geocam.vps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.geocam.vps.update.UpdateState

@Composable
fun UpdateBanner(
    state: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is UpdateState.Available -> Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .padding(12.dp)
        ) {
            Text("Update available: v${state.release.tag}", color = Color.White)
            Text("Current: ${state.current}", color = Color(0xFF94A3B8), fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDownload) { Text("Download") }
            }
        }
        is UpdateState.Downloading -> Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .padding(12.dp)
        ) {
            Text("Downloading v${state.release.tag} — ${(state.progress * 100).toInt()}%", color = Color.White)
            Spacer(Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is UpdateState.Ready -> Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .padding(12.dp)
        ) {
            Text("Ready to install v${state.release.tag}", color = Color.White)
            Spacer(Modifier.width(8.dp))
            Button(onClick = onInstall) { Text("Install") }
        }
        is UpdateState.Failed -> Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC0F172A))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✗ Update check failed: ${state.reason}", color = Color(0xFFEF4444), fontSize = 12.sp)
        }
        is UpdateState.UpToDate -> Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC0F172A))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✓ Up to date · v${state.current}", color = Color(0xFF22C55E), fontSize = 12.sp)
        }
        is UpdateState.Checking -> Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC0F172A))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Checking for updates…", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        else -> {}
    }
}
