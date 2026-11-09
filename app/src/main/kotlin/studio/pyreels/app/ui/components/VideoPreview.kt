package studio.pyreels.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import studio.pyreels.app.data.AvatarStore
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import studio.pyreels.app.engine.PreparedVideo
import studio.pyreels.app.engine.render.FrameRenderer
import studio.pyreels.app.ui.theme.LocalUi
import studio.pyreels.core.model.Limits

/** Phone-frame preview drawn by the export renderer (WYSIWYG), with play/pause, scrub and safe zones. */
@Composable
fun VideoPreview(
    video: PreparedVideo,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    safeZones: Boolean,
    onSafeZones: (Boolean) -> Unit,
    controls: Boolean = true,
    startAtMs: Long = 0,
    onTime: (Long) -> Unit = {},
    muted: Boolean = false,
    loop: Boolean = false,
) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val pictures by AvatarStore.version.collectAsState()
    val renderer = remember(video, pictures) { FrameRenderer(context, video.scenario).apply { setChapters(video.timeline) } }
    val player = remember { PreviewPlayer() }
    var playing by remember(video) { mutableStateOf(autoPlay) }
    var time by remember(video) { mutableLongStateOf(startAtMs.coerceIn(0, video.timeline.totalMs)) }
    val total = video.timeline.totalMs

    DisposableEffect(player) { onDispose { player.stop() } }
    // Without its own controls the preview follows the time given by the caller (cover picker).
    LaunchedEffect(startAtMs, controls) { if (!controls) time = startAtMs.coerceIn(0, total) }
    LaunchedEffect(playing, video) {
        if (playing) {
            if (time >= total - 50) time = 0
            player.play(video, time, withAudio = !muted)
            while (playing) {
                withFrameNanos { }
                time = player.positionMs().coerceAtMost(total)
                onTime(time)
                if (time >= total) {
                    if (loop) { time = 0; player.play(video, 0, withAudio = !muted) } else playing = false
                }
            }
            player.stop()
        } else player.stop()
    }

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(Limits.WIDTH / Limits.HEIGHT.toFloat())
                .clip(RoundedCornerShape(18.dp))
                .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                .semantics { contentDescription = ui.t("معاينة الفيديو", "Video preview") },
        ) {
            Canvas(Modifier.matchParentSizeCompat()) {
                val frame = video.timeline.frameAt(time)
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.scale(size.width / Limits.WIDTH, size.height / Limits.HEIGHT)
                    renderer.showSafeZones = safeZones
                    renderer.draw(nc, frame)
                    nc.restore()
                }
            }
        }
        if (controls) {
            // Player controls always read left-to-right (time grows to the right), like every video player.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledIconButton(onClick = { playing = !playing }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) ui.t("إيقاف مؤقت", "Pause") else ui.t("تشغيل", "Play"))
                    }
                    Text(fmt(time), style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = if (total > 0) (time.toFloat() / total).coerceIn(0f, 1f) else 0f,
                        onValueChange = { v -> playing = false; time = (v * total).toLong(); onTime(time) },
                        modifier = Modifier.weight(1f),
                    )
                    Text(fmt(total), style = MaterialTheme.typography.labelMedium)
                    IconToggleButton(checked = safeZones, onCheckedChange = onSafeZones) {
                        Icon(Icons.Outlined.CropFree, contentDescription = ui.t("إظهار المناطق الآمنة", "Show safe zones"),
                            tint = if (safeZones) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().aspectRatio(Limits.WIDTH / Limits.HEIGHT.toFloat()))

fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
