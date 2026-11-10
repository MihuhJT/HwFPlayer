package com.hwf.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LAYOUTS = mapOf(
    "ru" to listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю"),
    "en" to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
    "num" to listOf("1234567890", "-/:;()₽&@\"", ".,?!'#%+="),
)
private val ALT = mapOf('е' to 'ё', 'ь' to 'ъ', 'e' to 'é')  // long press

/** The app's own keyboard: RU/EN/123, shift, long-press ё/ъ, hold to erase. */
@Composable
fun AppKeyboard(
    onChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    doneLabel: String = "Найти",
    onDismiss: () -> Unit = onDone,
    modifier: Modifier = Modifier,
) {
    val dismiss by rememberUpdatedState(onDismiss)
    var lang by remember { mutableStateOf("ru") }
    var numbers by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    val rows = LAYOUTS[if (numbers) "num" else lang]!!

    Column(
        modifier
            .fillMaxWidth()
            // A short swipe or flick down anywhere on the keyboard hides it. Initial pass: it sees
            // the finger before the keys, and consuming cancels the key so no letter gets typed.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var dy = 0f
                    var dx = 0f
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                        dy += ch.positionChange().y
                        dx += ch.positionChange().x
                        tracker.addPosition(ch.uptimeMillis, ch.position)
                        val downward = dy > abs(dx)
                        val far = dy > 16.dp.toPx()
                        val flick = !ch.pressed && tracker.calculateVelocity().y > 300.dp.toPx() && dy > 6.dp.toPx()
                        if (downward && (far || flick)) { ch.consume(); dismiss(); break }
                        if (!ch.pressed) break
                    }
                }
            }
            .background(Color(0xFF070707))
            .padding(start = 5.dp, end = 5.dp, top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        @Composable
        fun RowScope.letters(row: String) = row.forEach { c ->
            val ch = if (shift) c.uppercaseChar() else c
            val alt = ALT[c]?.let { if (shift) it.uppercaseChar() else it }
            Key(
                label = ch.toString(), alt = alt?.toString(), maxWidth = 32,
                onTap = { long ->
                    onChar((if (long && alt != null) alt else ch).toString())
                    if (shift) shift = false
                }
            )
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) { letters(rows[0]) }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) { letters(rows[1]) }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
            if (!numbers) Key(icon = { drawShift(it) }, fn = true, weight = 1.5f, on = shift, onTap = { shift = !shift })
            letters(rows[2])
            Key(icon = { drawBackspace(it) }, fn = true, weight = 1.5f, repeat = onBackspace, onTap = {})
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
            Key(if (numbers) (if (lang == "ru") "АБВ" else "ABC") else "123", fn = true, weight = 1.5f, small = true, onTap = { numbers = !numbers })
            Key(lang.uppercase(), fn = true, weight = 1.5f, small = true, onTap = { lang = if (lang == "ru") "en" else "ru"; numbers = false })
            Key(if (lang == "ru") "Русский" else "English", weight = 5f, small = true, dim = true, onTap = { onChar(" ") })
            Key(doneLabel, weight = 2f, small = true, primary = true, onTap = { onDone() })
        }
    }
}

@Composable
private fun RowScope.Key(
    label: String = "",
    alt: String? = null,
    icon: (androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit)? = null,
    fn: Boolean = false,
    primary: Boolean = false,
    small: Boolean = false,
    dim: Boolean = false,
    on: Boolean = false,
    weight: Float = 1f,
    maxWidth: Int? = null,
    repeat: (() -> Unit)? = null,
    onTap: (long: Boolean) -> Unit,
) {
    // latest callbacks without restarting the gesture detector mid-press
    val tap by rememberUpdatedState(onTap)
    val rep by rememberUpdatedState(repeat)
    var pressed by remember { mutableStateOf(false) }
    var long by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bg = when {
        primary -> if (pressed) Color(0xFFDDDDDD) else Color.White
        on -> Color.White
        pressed -> Color(0xFF2C2C2C)
        fn -> Color(0xFF0F0F0F)
        else -> Color(0xFF1A1A1A)
    }
    val fg = when {
        primary || on -> Color.Black
        dim -> Color(0xFF555555)
        fn -> Color(0xFFBBBBBB)
        else -> Color.White
    }
    Box(
        Modifier
            .weight(weight)
            .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth.dp) else Modifier)
            .height(44.dp)
            .zIndex(if (pressed) 1f else 0f)
            .graphicsLayer { val s = if (pressed) .94f else 1f; scaleX = s; scaleY = s }
            .background(bg, RoundedCornerShape(9.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true; long = false
                    val r = rep
                    val job = scope.launch {
                        if (r != null) {
                            r(); delay(400)
                            while (true) { r(); delay(70) }
                        } else { delay(450); long = true }
                    }
                    val released = tryAwaitRelease()
                    job.cancel()
                    pressed = false
                    if (released && r == null) tap(long)
                })
            },
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) Canvas(Modifier.size(18.dp)) { icon(fg) }
        else Text(label, color = fg, fontSize = if (small) 12.sp else 17.sp, fontWeight = if (small) FontWeight.SemiBold else FontWeight.Medium)
        if (alt != null) Text(alt, color = Color(0xFF555555), fontSize = 8.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp))
        // Preview bubble above a pressed letter
        if (pressed && !fn && !primary && !small) {
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (-52).dp).size(42.dp, 48.dp)
                    .background(Color(0xFF2C2C2C), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) { Text(if (long && alt != null) alt else label, color = Color.White, fontSize = 24.sp) }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShift(c: Color) {
    val s = size.minDimension / 24f
    val p = Path().apply {
        moveTo(12 * s, 4 * s); lineTo(20 * s, 12 * s); lineTo(15.5f * s, 12 * s); lineTo(15.5f * s, 19 * s)
        lineTo(8.5f * s, 19 * s); lineTo(8.5f * s, 12 * s); lineTo(4 * s, 12 * s); close()
    }
    drawPath(p, c, style = Stroke(2 * s, join = StrokeJoin.Round))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBackspace(c: Color) {
    val s = size.minDimension / 24f
    val p = Path().apply {
        moveTo(8 * s, 5 * s); lineTo(20 * s, 5 * s); lineTo(20 * s, 19 * s); lineTo(8 * s, 19 * s); lineTo(2 * s, 12 * s); close()
    }
    val st = Stroke(2 * s, join = StrokeJoin.Round, cap = StrokeCap.Round)
    drawPath(p, c, style = st)
    drawLine(c, Offset(11 * s, 9 * s), Offset(17 * s, 15 * s), 2 * s, StrokeCap.Round)
    drawLine(c, Offset(17 * s, 9 * s), Offset(11 * s, 15 * s), 2 * s, StrokeCap.Round)
}
