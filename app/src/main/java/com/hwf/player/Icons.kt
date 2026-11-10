package com.hwf.player

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

// Icons from the mockup, drawn in a 24x24 grid scaled to the canvas.
// Triangles get a round-joined stroke on top of the fill, which softens the corners.

private fun DrawScope.unit() = size.minDimension / 24f

private fun DrawScope.roundedTriangle(color: Color, vararg pts: Float) {
    val s = unit()
    val path = Path().apply {
        moveTo(pts[0] * s, pts[1] * s)
        lineTo(pts[2] * s, pts[3] * s)
        lineTo(pts[4] * s, pts[5] * s)
        close()
    }
    drawPath(path, color)
    drawPath(path, color, style = Stroke(width = 2.4f * s, join = StrokeJoin.Round))
}

private fun DrawScope.bar(color: Color, x: Float, y: Float, w: Float, h: Float) {
    val s = unit()
    drawRoundRect(color, Offset(x * s, y * s), Size(w * s, h * s), CornerRadius(w / 2 * s))
}

fun DrawScope.drawPrev(color: Color) {
    bar(color, 4f, 5f, 2.6f, 14f)
    roundedTriangle(color, 20f, 6.2f, 20f, 17.8f, 10.2f, 12f)
}

fun DrawScope.drawNext(color: Color) {
    bar(color, 17.4f, 5f, 2.6f, 14f)
    roundedTriangle(color, 4f, 6.2f, 4f, 17.8f, 13.8f, 12f)
}

fun DrawScope.drawPlay(color: Color) = roundedTriangle(color, 7.5f, 5.5f, 7.5f, 18.5f, 18.5f, 12f)

fun DrawScope.drawPause(color: Color) {
    bar(color, 5.5f, 4f, 4.6f, 16f)
    bar(color, 13.9f, 4f, 4.6f, 16f)
}

/** Speaker with one or two sound waves depending on the level. */
fun DrawScope.drawSpeaker(color: Color, volume: Int) {
    val s = unit()
    val body = Path().apply {
        moveTo(3f * s, 9f * s); lineTo(7f * s, 9f * s); lineTo(12f * s, 5f * s)
        lineTo(12f * s, 19f * s); lineTo(7f * s, 15f * s); lineTo(3f * s, 15f * s); close()
    }
    drawPath(body, color)
    val stroke = Stroke(width = 2f * s, cap = StrokeCap.Round)
    // arcs matching "M15 9.5a3.5 3.5 0 0 1 0 5" and "M17.5 7a7 7 0 0 1 0 10"
    drawArc(
        color.copy(alpha = if (volume > 0) color.alpha else .2f), -45.6f, 91.2f, false,
        topLeft = Offset((12.55f - 3.5f) * s, (12f - 3.5f) * s), size = Size(7f * s, 7f * s), style = stroke
    )
    drawArc(
        color.copy(alpha = if (volume > 50) color.alpha else .2f), -45.6f, 91.2f, false,
        topLeft = Offset((12.6f - 7f) * s, (12f - 7f) * s), size = Size(14f * s, 14f * s), style = stroke
    )
}
