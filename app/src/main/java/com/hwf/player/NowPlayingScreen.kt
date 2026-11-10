package com.hwf.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val index: Int = 0,
    val count: Int = 0,
    val quality: String = "",
)

private val Gray = Color(0xFF9A9A9A)
private val Red = Color(0xFFE0352F)

@Composable
fun NowPlayingScreen(
    state: NowPlaying,
    source: String,
    time: String,
    date: String,
    volume: Int,
    volumeVisible: Boolean,
    animate: Boolean,
    cover: ImageBitmap?,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeDrag: (startPercent: Int, dyPx: Float, slidePx: Float) -> Unit,
    onVolumeDragEnd: () -> Unit,
    onPanelDrag: (dxPx: Float) -> Unit,
    onPanelDragEnd: (dxPx: Float, velocityX: Float) -> Unit,
) {
    val density = LocalDensity.current
    val slidePx = with(density) { 200.dp.toPx() }  // a third of the screen = the whole 0..100 range
    val currentVolume by rememberUpdatedState(volume)
    val volDrag by rememberUpdatedState(onVolumeDrag)
    val volEnd by rememberUpdatedState(onVolumeDragEnd)
    val panDrag by rememberUpdatedState(onPanelDrag)
    val panEnd by rememberUpdatedState(onPanelDragEnd)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Vertical swipe = volume; horizontal = playlists (right) / search (left)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startVol = currentVolume
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var total = Offset.Zero
                    var mode = 0  // 0 undecided, 1 volume, 2 panel, -1 not ours
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed && mode == 0) { mode = -1 }  // the seek bar took it
                        total += change.positionChange()  // the lift event can carry the last move too
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (mode == 0 && total.getDistance() > viewConfiguration.touchSlop) {
                            mode = if (abs(total.y) > abs(total.x)) 1 else 2
                        }
                        if (mode == 1) {
                            change.consume()
                            volDrag(startVol, total.y, slidePx)
                        }
                        if (mode == 2) {
                            change.consume()
                            panDrag(total.x)
                        }
                        if (!change.pressed) break
                    }
                    if (mode == 1) volEnd()
                    if (mode == 2) panEnd(total.x, tracker.calculateVelocity().x)
                }
            }
    ) {
        // While the volume screen is up, the player UI isn't drawn at all: on the M3 Ultra
        // parts of it (the seek bar) showed through the overlay otherwise.
        if (!volumeVisible) {
        GlassBlob(
            playing = state.playing,
            visible = animate,
            cover = cover,
            modifier = Modifier.align(Alignment.Center).offset(y = (-17).dp).size(280.dp)
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp)) {
            TopRow(source, "${state.index + 1} из ${state.count}", time, date)
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Controls(state.playing, onPlayPause, onPrev, onNext)
                Spacer(Modifier.height(46.dp))
                Text(
                    state.title.uppercase(), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(state.artist, color = Gray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.quality.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(state.quality, color = Color(0xFF5C5C5C), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                }
            }
            SeekBar(state.positionMs, state.durationMs, onSeek)
        }
        }

        VolumeOverlay(volume, volumeVisible)
    }
}

@Composable
private fun TopRow(source: String, sub: String, time: String, date: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp, bottom = 7.dp)) {
                repeat(2) { Box(Modifier.size(11.dp).background(Color.White, RoundedCornerShape(3.dp))) }
            }
            Column {
                Text(source.uppercase(), color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, color = Color(0xFF8A8A8A), fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(time, color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
            Text(date, color = Gray, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 1)
        }
    }
}

@Composable
private fun Controls(playing: Boolean, onPlayPause: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(46.dp)) {
        IconButton(38, onPrev) { drawPrev(Color.White) }
        IconButton(74, onPlayPause) { if (playing) drawPause(Color.White) else drawPlay(Color.White) }
        IconButton(38, onNext) { drawNext(Color.White) }
    }
}

@Composable
private fun IconButton(sizeDp: Int, onClick: () -> Unit, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val scale by animateFloatAsState(if (pressed) .86f else 1f, tween(120), label = "press")
    Canvas(
        Modifier
            .size(sizeDp.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    var moved = false
                    while (true) {
                        val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        if (!ch.pressed) break
                    }
                    pressed = false
                    if (!moved) click()
                }
            },
        onDraw = draw
    )
}

/** Thin progress line; the knob follows the finger and the track only jumps on release. */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val fraction = scrub ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownMs = scrub?.let { (it * durationMs).toLong() } ?: positionMs
    val knob by animateFloatAsState(if (scrub != null) 7f else 4f, tween(150), label = "knob")

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(fmt(shownMs), color = Gray, fontSize = 11.sp, modifier = Modifier.width(36.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(durationMs) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        scrub = (down.position.x / size.width).coerceIn(0f, 1f)
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                            ch.consume()
                            if (!ch.pressed) break
                            scrub = (ch.position.x / size.width).coerceIn(0f, 1f)
                        }
                        scrub?.let { seek((it * durationMs).toLong()) }
                        scrub = null
                    }
                }
        ) {
            val y = size.height / 2
            val h = 2.dp.toPx()
            drawRoundRect(Color(0xFF333333), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h))
            drawRoundRect(if (scrub != null) Color.White else Color(0xFFBBBBBB), Offset(0f, y - h / 2), Size(size.width * fraction, h), CornerRadius(h))
            drawCircle(Color.White, knob.dp.toPx(), Offset(size.width * fraction, y))
        }
        Text(fmt(durationMs), color = Gray, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
    }
}

/** Full-screen "🔊 80%" that blends to red above 80%. */
@Composable
private fun VolumeOverlay(volume: Int, visible: Boolean) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, if (visible) snap() else tween(250), label = "vol")
    if (alpha == 0f) return
    val heat = smoothstep(((volume - 80) / 20f).coerceIn(0f, 1f))
    val color = lerp(Color.White, Red, heat)

    Box(Modifier.fillMaxSize().alpha(alpha).background(Color.Black), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = 280.dp.toPx()
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFF3A3A3A), .38f / .7f to Color(0xFF1B1B1B), .62f / .7f to Color(0xFF070707), 1f to Color.Black,
                    center = Offset(center.x, center.y - r * .12f), radius = r * .7f / .7f
                ),
                radius = r, center = center
            )
            drawCircle(
                Brush.radialGradient(listOf(Red.copy(alpha = .32f * heat), Color.Transparent), center = center, radius = 260.dp.toPx() * .6f),
                radius = 260.dp.toPx(), center = center
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Canvas(Modifier.size(76.dp)) { drawSpeaker(color, volume) }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$volume", color = color, fontSize = 58.sp, fontWeight = FontWeight.Normal, letterSpacing = (-1.7).sp)
                Text("%", color = color, fontSize = 34.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        // 16 ticks; the ones past 80% warm up one by one
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(16) { k ->
                val on = k < Math.round(volume / 100f * 16)
                val tickHeat = smoothstep((((k + 1) / 16f * 100 - 80) / 20f).coerceIn(0f, 1f))
                Box(
                    Modifier.size(6.dp, 14.dp).background(
                        if (on) lerp(Color.White, Red, tickHeat) else Color(0xFF2A2A2A), RoundedCornerShape(2.dp)
                    )
                )
            }
        }
    }
}

private fun smoothstep(x: Float) = x * x * (3 - 2 * x)

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
