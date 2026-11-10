package com.hwf.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The quiet "liquid glass" circle behind the controls.
 * It ramps up/down over ~1.4 s on play/pause and stops drawing entirely when
 * it is still, or when [visible] is false (screen off, covered by a panel).
 */
@Composable
fun GlassBlob(playing: Boolean, visible: Boolean, cover: ImageBitmap?, modifier: Modifier = Modifier) {
    var motion by remember { mutableFloatStateOf(0f) }
    var energy by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }
    var spin by remember { mutableFloatStateOf(0f) }
    var caustic by remember { mutableFloatStateOf(0f) }
    var bassAvg by remember { mutableStateOf(0f) }
    var hue by remember { mutableFloatStateOf(120f) }   // follows the tempo
    var tint by remember { mutableFloatStateOf(0f) }    // 0 = gray (tempo unknown), 1 = full color
    val isPlaying by rememberUpdatedState(playing)

    // Album cover inside the glass; a new one fades in over the old one
    var shown by remember { mutableStateOf(cover) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val swap = remember { Animatable(1f) }
    LaunchedEffect(cover) {
        if (cover === shown) return@LaunchedEffect
        previous = shown
        shown = cover
        swap.snapTo(0f)
        swap.animateTo(1f, tween(600))
        previous = null
    }

    // Restart the frame loop whenever something could make it move again
    LaunchedEffect(playing, visible) {
        if (!visible) return@LaunchedEffect
        var last = 0L
        while (isPlaying || motion > 0f || energy > .002f) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(.05f, (now - last) / 1e9f)
                last = now
                val goal = if (isPlaying) 1f else 0f
                motion += (goal - motion).coerceIn(-dt / 1.4f, dt / 1.4f)
                val m = smooth(motion)

                // Kick detection: how far the bass is above its running average
                val bass = if (isPlaying) BassMeter.level else 0f
                bassAvg += (bass - bassAvg) * .05f
                val target = max(0f, (bass - bassAvg) * 4f + bass * .35f) * m
                energy += (target - energy) * (if (target > energy) .5f else .12f)

                // Tempo -> hue: ~70 BPM teal-green (170°) ... ~180 BPM red-orange (12°)
                val bpm = BassMeter.bpm
                if (bpm > 0f) hue += (bpmHue(bpm) - hue) * min(1f, dt * 1.2f)
                tint += ((if (bpm > 0f) 1f else 0f) - tint) * min(1f, dt * .8f)

                phase += dt * m
                spin += dt * 6f * m
                caustic += dt * 40f * m
            }
        }
    }

    Canvas(modifier) {
        val m = smooth(motion)
        val c = center
        val t = tint
        fun hsv(s: Float, v: Float) = Color.hsv(hue.coerceIn(0f, 360f), s, v)
        val baseR = size.minDimension / 2f
        val scale = .94f + .06f * m + energy * .06f
        val r = baseR * scale

        // Soft glow behind the glass
        drawCircle(
            Brush.radialGradient(
                listOf(lerp(Color.White, hsv(.8f, 1f), t).copy(alpha = (.04f + .06f * t) * (.6f + .4f * m + energy * 1.5f)), Color.Transparent),
                center = c, radius = 170.dp.toPx() * (1f + energy * .25f)
            ),
            radius = 170.dp.toPx() * (1f + energy * .25f), center = c
        )

        // Blob outline like the mockup's CSS border-radius: four quarter-ellipse corners whose
        // radii drift with layered sines. Always convex, which the GPU draws cheaply.
        fun blobPath(): Path {
            val w = (3f + energy * 9f) * (.5f + .5f * m) / 100f
            fun k(n: Int) = .5f + sin(phase * (.7f + n * .13f) + n * 1.7f) * w + sin(phase * 1.9f + n) * w * .4f
            val (h0, h1, v0, v1) = listOf(k(0), k(1), k(2), k(3))
            val d = 2 * r
            val l = c.x - r
            val t = c.y - r
            // horizontal radii: TL, TR, BR, BL; vertical radii: TL, TR, BR, BL (fractions of the box)
            val hx = floatArrayOf(h0, 1 - h0, h1, 1 - h1).map { it * d }
            val vy = floatArrayOf(v0, v1, 1 - v1, 1 - v0).map { it * d }
            val q = .5523f  // cubic approximation of a quarter ellipse
            return Path().apply {
                moveTo(l + hx[0], t)
                lineTo(l + d - hx[1], t)
                cubicTo(l + d - hx[1] * (1 - q), t, l + d, t + vy[1] * (1 - q), l + d, t + vy[1])
                lineTo(l + d, t + d - vy[2])
                cubicTo(l + d, t + d - vy[2] * (1 - q), l + d - hx[2] * (1 - q), t + d, l + d - hx[2], t + d)
                lineTo(l + hx[3], t + d)
                cubicTo(l + hx[3] * (1 - q), t + d, l, t + d - vy[3] * (1 - q), l, t + d - vy[3])
                lineTo(l, t + vy[0])
                cubicTo(l, t + vy[0] * (1 - q), l + hx[0] * (1 - q), t, l + hx[0], t)
                close()
            }
        }
        val path = blobPath().apply {
            transform(androidx.compose.ui.graphics.Matrix().apply {
                translate(c.x, c.y); rotateZ(spin); translate(-c.x, -c.y)
            })
        }

        // Only the body uses the outline (complex paths are expensive on this GPU).
        // Highlights are plain circles that fade out before reaching the edge, so they need no clipping.
        drawPath(
            path,
            Brush.radialGradient(
                0f to lerp(Color(0xFF151516), hsv(.75f, .30f), t),
                .6f to lerp(Color(0xFF0E0E0F), hsv(.80f, .13f), t),
                1f to lerp(Color(0xFF0A0A0A), hsv(.70f, .06f), t),
                center = c, radius = r
            )
        )
        // The cover bitmap is already darkened with a soft round edge (see Covers), so it's one cheap draw
        fun drawCover(img: ImageBitmap, alpha: Float) {
            if (alpha <= 0f) return
            val d = (2 * r * .96f).toInt()
            drawImage(
                img, dstOffset = IntOffset((c.x - d / 2f).toInt(), (c.y - d / 2f).toInt()), dstSize = IntSize(d, d),
                alpha = alpha, filterQuality = FilterQuality.Low
            )
        }
        previous?.let { drawCover(it, 1f - swap.value) }
        shown?.let { drawCover(it, swap.value) }

        val hl = Offset(c.x - r * .3f, c.y - r * .38f)
        drawCircle(Brush.radialGradient(listOf(lerp(Color.White, hsv(.35f, 1f), t).copy(alpha = .09f + .05f * t), Color.Transparent), center = hl, radius = r * .5f), r * .5f, hl)
        val bounce = Offset(c.x + r * .3f, c.y + r * .42f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .03f), Color.Transparent), center = bounce, radius = r * .5f), r * .5f, bounce)
        // slowly turning caustic
        rotate(caustic + energy * 60f, c) {
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, .28f to Color.White.copy(alpha = .035f), .36f to Color.Transparent,
                    .68f to Color.White.copy(alpha = .02f), .76f to Color.Transparent, 1f to Color.Transparent,
                    center = c
                ),
                radius = r * .9f, center = c
            )
        }
        drawPath(path, lerp(Color.White, hsv(.6f, 1f), t).copy(alpha = .05f + .07f * t), style = Stroke(width = 1.dp.toPx()))
    }
}

private fun smooth(x: Float) = x * x * (3 - 2 * x)

fun bpmHue(bpm: Float): Float = 170f - ((bpm - 70f) / 110f).coerceIn(0f, 1f) * 158f
