package com.hwf.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

val Accent = Color(0xFFE0352F)
private val Muted = Color(0xFF777777)
private val Faint = Color(0xFF555555)
private val Card = Color(0xFF0D0D0D)
private val ru = Locale.forLanguageTag("ru")

// The keyboard slides up from the bottom edge and back down
private val KeyboardIn = slideInVertically(tween(260)) { it } + expandVertically(tween(260), expandFrom = Alignment.Top)
private val KeyboardOut = slideOutVertically(tween(220)) { it } + shrinkVertically(tween(220), shrinkTowards = Alignment.Top)

/** A list someone can play: what's shown and what goes to the player. */
typealias OnPlay = (tracks: List<Track>, name: String, start: Int) -> Unit

fun Modifier.tap(onClick: () -> Unit) = clickable(MutableInteractionSource(), indication = null, onClick = onClick)

@Composable
fun RoundButton(left: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).background(Color(0xFF161616), CircleShape).tap(onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(16.dp)) {
            val s = size.minDimension / 24f
            val x1 = if (left) 15f else 9f
            val x2 = if (left) 9f else 15f
            drawLine(Color.White, Offset(x1 * s, 6 * s), Offset(x2 * s, 12 * s), 2.4f * s, StrokeCap.Round)
            drawLine(Color.White, Offset(x2 * s, 12 * s), Offset(x1 * s, 18 * s), 2.4f * s, StrokeCap.Round)
        }
    }
}

@Composable
private fun Header(title: String, sub: String, backLeft: Boolean, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        if (backLeft) { RoundButton(true, onBack); Spacer(Modifier.weight(1f)) }
        // The black backing is needed: on the M3 Ultra these texts don't render without a drawn background
        Column(horizontalAlignment = if (backLeft) Alignment.End else Alignment.Start, modifier = (if (backLeft) Modifier else Modifier.weight(1f)).background(Color.Black)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 11.sp)
        }
        if (!backLeft) RoundButton(false, onBack)
    }
}

@Composable
private fun Divider(text: String, right: String = "") {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 6.dp)) {
        Text(text, color = Faint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
        if (right.isNotEmpty()) Text(right, color = Color(0xFF666666), fontSize = 10.sp)
    }
}

@Composable
fun TrackRow(n: Int?, t: Track, now: Boolean, right: @Composable () -> Unit = { Text(fmt(t.durationMs), color = Faint, fontSize = 11.sp) },
             title: AnnotatedString? = null, sub: AnnotatedString? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tap(onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (n != null) Text("$n", color = Faint, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(18.dp))
        Thumb(t)
        Column(Modifier.weight(1f)) {
            Text(title ?: AnnotatedString(t.title), color = if (now) Accent else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub ?: AnnotatedString(t.artist), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        right()
    }
}

/** Album cover square; a dark tile while it loads or when the file has no cover. */
@Composable
private fun Thumb(t: Track) {
    val cover by rememberThumb(t)
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF161616))) {
        cover?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun PillButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).background(if (primary) Color.White else Color(0xFF161616), RoundedCornerShape(20.dp)).tap(onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (primary) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

/** A text box for our keyboard (no system IME): shows text, caret and placeholder. */
@Composable
fun FakeField(text: String, placeholder: String, focused: Boolean, modifier: Modifier = Modifier, onTap: () -> Unit, onClear: (() -> Unit)? = null) {
    val caret by animateFloatAsState(if (focused) 1f else 0f, label = "caret")
    Row(
        modifier.height(44.dp).background(Color(0xFF0F0F0F), RoundedCornerShape(22.dp))
            .border(1.dp, if (focused) Color(0xFF3A3A3A) else Color(0xFF1E1E1E), RoundedCornerShape(22.dp))
            .tap(onTap).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (text.isEmpty()) placeholder else text + if (focused) "|" else "",
            color = if (text.isEmpty()) Color(0xFF666666) else Color.White.copy(alpha = .8f + .2f * caret),
            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (onClear != null && text.isNotEmpty()) {
            Box(Modifier.size(26.dp).background(Color(0xFF222222), CircleShape).tap(onClear), contentAlignment = Alignment.Center) {
                Text("✕", color = Color(0xFFAAAAAA), fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Playlists

private sealed interface PlScreen {
    data object List : PlScreen
    data class Detail(val key: Int) : PlScreen  // -1 = random 20
    data object Stats : PlScreen
}

@Composable
fun PlaylistsPanel(library: List<Track>, nowId: Long?, onPlay: OnPlay, onClose: () -> Unit) {
    var screen by remember { mutableStateOf<PlScreen>(PlScreen.List) }
    var random20 by remember { mutableStateOf(library.shuffled().take(20)) }
    BackHandler { if (screen == PlScreen.List) onClose() else screen = PlScreen.List }

    AnimatedContent(
        screen,
        transitionSpec = {
            val forward = targetState != PlScreen.List
            (slideInHorizontally(tween(320)) { if (forward) it else -it / 3 }) togetherWith
                (slideOutHorizontally(tween(320)) { if (forward) -it / 3 else it })
        },
        label = "pl"
    ) { s ->
        Box(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 22.dp, vertical = 22.dp)) {
            when (s) {
                PlScreen.List -> PlaylistList(library, onClose,
                    open = { k -> if (k == -1) random20 = library.shuffled().take(20); screen = PlScreen.Detail(k) },
                    stats = { screen = PlScreen.Stats })
                is PlScreen.Detail -> PlaylistDetail(s.key, library, random20, nowId, onPlay,
                    reshuffle = { random20 = library.shuffled().take(20) }, back = { screen = PlScreen.List })
                PlScreen.Stats -> StatsView(library, nowId, onPlay) { screen = PlScreen.List }
            }
        }
    }
}

@Composable
private fun PlaylistList(library: List<Track>, onClose: () -> Unit, open: (Int) -> Unit, stats: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // dragging the list (any vertical swipe above the keyboard) hides it, same as in search
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) typing = false }
    val n = Store.playlists.size + 1
    Column(Modifier.fillMaxSize()) {
        Header("Плейлисты", "$n ${plural(n, "плейлист", "плейлиста", "плейлистов")} · ${library.size} ${plural(library.size, "трек", "трека", "треков")}", false, onClose)
        LazyColumn(Modifier.weight(1f).padding(top = 16.dp), state = listState) {
            item { PlaylistCard("Случайные 20", "Новая подборка при каждом открытии", featured = true) { open(-1) } }
            item { Divider("МОИ ПЛЕЙЛИСТЫ") }
            if (Store.playlists.isEmpty()) item {
                Text("Пока пусто. Создай первый плейлист, потом добавь в него треки.", color = Faint, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            itemsIndexed(Store.playlists) { k, p ->
                val tracks = p.ids.mapNotNull { id -> library.find { it.id == id } }
                PlaylistCard(p.name, "${tracks.size} ${plural(tracks.size, "трек", "трека", "треков")}" + if (tracks.isNotEmpty()) " · ${totalTime(tracks)}" else "") { open(k) }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FakeField(name, "Название плейлиста", typing, Modifier.weight(1f), onTap = { typing = true })
                    PillButton("Создать", true, Modifier.width(84.dp).height(44.dp)) {
                        if (name.isBlank()) typing = true
                        else { val k = Store.addPlaylist(name.trim()); name = ""; typing = false; open(k) }
                    }
                }
            }
        }
        AnimatedVisibility(!typing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            StatsButton(stats)
        }
        AnimatedVisibility(typing, enter = KeyboardIn, exit = KeyboardOut) {
            AppKeyboard(
                onChar = { if (name.length < 40) name += it }, onBackspace = { name = name.dropLast(1) },
                onDone = { typing = false }, doneLabel = "Готово", onDismiss = { typing = false }
            )
        }
    }
}

@Composable
private fun PlaylistCard(name: String, meta: String, featured: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().tap(onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(54.dp).background(
                Brush.radialGradient(if (featured) listOf(Color(0xFF5A1414), Color(0xFF1D0606)) else listOf(Color(0xFF3A3A3A), Color(0xFF141414))),
                RoundedCornerShape(12.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(22.dp)) {
                val s = size.minDimension / 24f
                if (featured) {  // shuffle arrows
                    drawLine(Color.White, Offset(4 * s, 20 * s), Offset(21 * s, 3 * s), 2.2f * s, StrokeCap.Round)
                    drawLine(Color.White, Offset(16 * s, 3 * s), Offset(21 * s, 3 * s), 2.2f * s, StrokeCap.Round)
                    drawLine(Color.White, Offset(21 * s, 3 * s), Offset(21 * s, 8 * s), 2.2f * s, StrokeCap.Round)
                    drawLine(Color.White, Offset(4 * s, 4 * s), Offset(9 * s, 9 * s), 2.2f * s, StrokeCap.Round)
                    drawLine(Color.White, Offset(15 * s, 15 * s), Offset(21 * s, 21 * s), 2.2f * s, StrokeCap.Round)
                    drawLine(Color.White, Offset(21 * s, 16 * s), Offset(21 * s, 21 * s), 2.2f * s, StrokeCap.Round)
                    drawLine(Color.White, Offset(16 * s, 21 * s), Offset(21 * s, 21 * s), 2.2f * s, StrokeCap.Round)
                } else {  // note
                    drawLine(Color.White, Offset(9 * s, 18 * s), Offset(9 * s, 5 * s), 2.2f * s, StrokeCap.Round)
                    drawLine(Color.White, Offset(9 * s, 5 * s), Offset(21 * s, 3 * s), 2.2f * s, StrokeCap.Round)
                    drawLine(Color.White, Offset(21 * s, 3 * s), Offset(21 * s, 16 * s), 2.2f * s, StrokeCap.Round)
                    drawCircle(Color.White, 3 * s, Offset(6 * s, 18 * s))
                    drawCircle(Color.White, 3 * s, Offset(18 * s, 16 * s))
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun StatsButton(onClick: () -> Unit) {
    val today = Store.days[Store.dayKey()] ?: 0
    val week = lastDays(7)
    val max = (week.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).background(Card, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(16.dp)).tap(onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(34.dp).background(Color(0xFF161616), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(17.dp)) {
                val s = size.minDimension / 24f
                listOf(5f to 11f, 12f to 4f, 19f to 14f).forEach { (x, top) ->
                    drawLine(Color.White, Offset(x * s, 20 * s), Offset(x * s, top * s), 2.2f * s, StrokeCap.Round)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text("Статистика", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Сегодня ${hm(today)}", color = Muted, fontSize = 11.sp)
        }
        Row(Modifier.height(22.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            week.forEachIndexed { k, v ->
                Box(Modifier.width(4.dp).height((22f * v / max).coerceAtLeast(2f).dp)
                    .background(if (k == 6) Color.White else Color(0xFF444444), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
            }
        }
    }
}

@Composable
private fun PlaylistDetail(key: Int, library: List<Track>, random20: List<Track>, nowId: Long?, onPlay: OnPlay, reshuffle: () -> Unit, back: () -> Unit) {
    val isRandom = key == -1
    val pl = if (isRandom) null else Store.playlists.getOrNull(key)
    val name = pl?.name ?: "Случайные 20"
    val list = if (isRandom) random20 else pl!!.ids.mapNotNull { id -> library.find { it.id == id } }
    var editing by remember { mutableStateOf(!isRandom && list.isEmpty()) }

    Column(Modifier.fillMaxSize()) {
        Header(name, if (list.isEmpty()) "пустой" else "${list.size} ${plural(list.size, "трек", "трека", "треков")} · ${totalTime(list)}", true, back)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("▶  Слушать", true, Modifier.weight(1f)) { if (list.isNotEmpty()) onPlay(list, name, 0) }
            if (isRandom) PillButton("Другие 20", false, Modifier.weight(1f), reshuffle)
            else PillButton(if (editing) "Готово" else "+ Треки", false, Modifier.weight(1f)) { editing = !editing }
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            if (editing && pl != null) {
                itemsIndexed(library) { k, t ->
                    val inList = t.id in pl.ids
                    TrackRow(k + 1, t, t.id == nowId, right = {
                        Box(
                            Modifier.size(26.dp).background(if (inList) Color.White else Color.Transparent, CircleShape)
                                .border(1.dp, if (inList) Color.White else Color(0xFF333333), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text(if (inList) "✓" else "+", color = if (inList) Color.Black else Color(0xFFAAAAAA), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }) { Store.toggleTrack(key, t.id) }
                }
            } else {
                if (list.isEmpty()) item { Text("Нажми «+ Треки», чтобы добавить песни.", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(12.dp)) }
                itemsIndexed(list) { k, t -> TrackRow(k + 1, t, t.id == nowId) { onPlay(list, name, k) } }
            }
        }
    }
}

// ---------------------------------------------------------------- Stats

private fun lastDays(n: Int): List<Long> {
    val cal = Calendar.getInstance()
    return (n - 1 downTo 0).map { k ->
        val c = cal.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, -k)
        Store.days[Store.dayKey(c.time)] ?: 0
    }
}

private fun streak(): Int {
    val c = Calendar.getInstance()
    if ((Store.days[Store.dayKey(c.time)] ?: 0) == 0L) c.add(Calendar.DAY_OF_YEAR, -1)  // today not started yet
    var n = 0
    while ((Store.days[Store.dayKey(c.time)] ?: 0) > 0) { n++; c.add(Calendar.DAY_OF_YEAR, -1) }
    return n
}

@Composable
private fun StatsView(library: List<Track>, nowId: Long?, onPlay: OnPlay, back: () -> Unit) {
    val today = Store.days[Store.dayKey()] ?: 0
    val week = lastDays(7).sum()
    val artists = HashMap<String, Long>().also { m ->
        Store.artists.forEach { (names, sec) -> splitArtists(names).forEach { a -> m[a] = (m[a] ?: 0) + sec } }
    }.entries.sortedByDescending { it.value }.take(5)
    val amax = artists.firstOrNull()?.value?.coerceAtLeast(1) ?: 1
    val top = Store.plays.entries.sortedByDescending { it.value }
        .mapNotNull { (id, n) -> library.find { it.id == id }?.let { it to n } }.take(5)

    Column(Modifier.fillMaxSize()) {
        Header("Статистика", "по неделям", true, back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(today, "сегодня", Modifier.weight(1f))
                    Tile(week, "за 7 дней", Modifier.weight(1f))
                    Box(Modifier.weight(1f).background(Card, RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF181818), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${streak()}", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                                Text("дн", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
                            }
                            Text("серия подряд", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
            item { Divider("АКТИВНОСТЬ") }
            item { WeekChart() }
            item { Divider("ТОП ИСПОЛНИТЕЛЕЙ", "время") }
            if (artists.isEmpty()) item { Text("Пока пусто — послушай что-нибудь.", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp)) }
            artists.forEach { (a, sec) ->
                item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        Row {
                            Text(a, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(hm(sec), color = Color(0xFF888888), fontSize = 11.sp)
                        }
                        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).background(Color(0xFF121212), RoundedCornerShape(3.dp))) {
                            Box(Modifier.fillMaxWidth(sec.toFloat() / amax).fillMaxHeight().background(Color(0xFFE8E8E8), RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
            item { Divider("ТОП ТРЕКОВ", "прослушиваний") }
            itemsIndexed(top) { k, (t, n) ->
                TrackRow(k + 1, t, t.id == nowId, right = { Text("$n×", color = Faint, fontSize = 11.sp) }) {
                    onPlay(top.map { it.first }, "Топ треков", k)
                }
            }
        }
    }
}

@Composable
private fun Tile(sec: Long, label: String, modifier: Modifier) {
    Column(modifier.background(Card, RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF181818), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (sec >= 3600) "%.1f".format(ru, sec / 3600f) else "${sec / 60}", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (sec >= 3600) "ч" else "мин", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
        }
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

/** Seven fat bars for one week; arrows flip weeks, tap a bar to see that day. */
@Composable
private fun WeekChart() {
    var offset by remember { mutableIntStateOf(0) }
    val now = Calendar.getInstance()
    val todayIdx = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7  // Monday = 0
    var sel by remember(offset) { mutableIntStateOf(if (offset == 0) todayIdx else 6) }

    val monday = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        add(Calendar.DAY_OF_YEAR, -todayIdx + offset * 7)
    }
    val days = (0..6).map { k -> (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, k) }.time }
    val vals = days.map { d -> if (d.after(now.time)) null else Store.days[Store.dayKey(d)] ?: 0 }
    val total = vals.sumOf { it ?: 0 }
    val max = maxOf(vals.maxOf { it ?: 0 }, 3600)
    val fmtShort = SimpleDateFormat("d MMM", ru)
    val title = when (offset) {
        0 -> "Эта неделя"
        -1 -> "Прошлая неделя"
        else -> "${fmtShort.format(days[0])} – ${fmtShort.format(days[6])}".replace(".", "")
    }
    val selV = vals[sel]
    val dn = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.alphaIf(offset > -15)) { RoundButton(true) { if (offset > -15) offset-- } }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("всего ${hm(total)}", color = Muted, fontSize = 11.sp)
            }
            Box(Modifier.alphaIf(offset < 0)) { RoundButton(false) { if (offset < 0) offset++ } }
        }
        Text(
            buildAnnotatedString {
                append(SimpleDateFormat("EEEE, d MMMM", ru).format(days[sel]) + " · ")
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                    append(if (selV == null) "ещё не наступил" else if (selV == 0L) "не слушал" else hm(selV))
                }
            },
            color = Color(0xFF888888), fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp)
        )
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vals.forEachIndexed { k, v ->
                val h by animateFloatAsState(if (v == null || v == 0L) 0f else (v.toFloat() / max).coerceAtLeast(.04f), tween(350), label = "bar")
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(if (k == sel) Color(0xFF0F0F0F) else Color.Transparent, RoundedCornerShape(10.dp))
                        .tap { sel = k }.padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth(.6f).fillMaxHeight(h).background(if (k == sel) Color.White else Color(0xFF4A4A4A), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp)))
                        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(2.dp).background(if (v == null) Color(0xFF151515) else Color(0xFF1C1C1C)))
                    }
                    val today = offset == 0 && k == todayIdx
                    Text(dn[k], color = if (today) Color.White else Color(0xFF666666), fontSize = 11.sp,
                        fontWeight = if (today) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

private fun Modifier.alphaIf(enabled: Boolean) = alpha(if (enabled) 1f else .25f)

// ---------------------------------------------------------------- Search

private fun norm(s: String) = s.lowercase().replace('ё', 'е')

private fun highlight(text: String, q: String): AnnotatedString {
    val at = norm(text).indexOf(q)
    if (q.isEmpty() || at < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, at))
        withStyle(SpanStyle(color = Accent)) { append(text.substring(at, at + q.length)) }
        append(text.substring(at + q.length))
    }
}

@Composable
fun SearchPanel(library: List<Track>, nowId: Long?, open: Boolean, onPlay: OnPlay, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var keyboard by remember { mutableStateOf(true) }
    LaunchedEffect(open) { if (open) keyboard = true }
    BackHandler(enabled = open) { if (keyboard) keyboard = false else onClose() }
    val list = rememberLazyListState()
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) keyboard = false }  // scrolling hides it, like on phones

    val q = norm(query.trim())
    val found = remember(q, library) { if (q.isEmpty()) emptyList() else library.filter { norm("${it.title} ${it.artist} ${it.album}").contains(q) } }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.weight(1f).padding(start = 22.dp, end = 22.dp, top = 22.dp)) {
            Header("Поиск", "треки, исполнители, альбомы", true, onClose)
            FakeField(query, "Что послушать?", keyboard, Modifier.fillMaxWidth().padding(top = 14.dp), onTap = { keyboard = true }, onClear = { query = ""; keyboard = true })
            LazyColumn(Modifier.weight(1f), state = list) {
                if (q.isEmpty()) {
                    item { Divider("ИСПОЛНИТЕЛИ") }
                    item {
                        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            library.flatMap { splitArtists(it.artist) }.distinctBy { it.lowercase() }.filter { it != "Неизвестный исполнитель" }.sortedBy { it.lowercase() }.take(40).forEach { a ->
                                Text(a, color = Color(0xFFDDDDDD), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.background(Card, RoundedCornerShape(16.dp)).border(1.dp, Color(0xFF222222), RoundedCornerShape(16.dp))
                                        .tap { query = a }.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }
                } else if (found.isEmpty()) {
                    item { Text("Ничего не нашлось. Попробуй часть названия или исполнителя.", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(12.dp)) }
                } else {
                    item { Divider("${found.size} ${plural(found.size, "ТРЕК", "ТРЕКА", "ТРЕКОВ")}") }
                    itemsIndexed(found) { k, t ->
                        TrackRow(null, t, t.id == nowId,
                            title = highlight(t.title, q),
                            sub = buildAnnotatedString { append(highlight(t.artist, q)); if (t.album.isNotEmpty()) { append(" · "); append(highlight(t.album, q)) } }
                        ) { onPlay(found, "Поиск: ${query.trim()}", k) }
                    }
                }
            }
        }
        AnimatedVisibility(keyboard, enter = KeyboardIn, exit = KeyboardOut) {
            AppKeyboard(onChar = { query += it }, onBackspace = { query = query.dropLast(1) }, onDone = { keyboard = false }, onDismiss = { keyboard = false })
        }
    }
}

// ---------------------------------------------------------------- helpers

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && (m100 < 10 || m100 >= 20) -> few
        else -> many
    }
}

fun hm(sec: Long): String {
    val m = Math.round(sec / 60.0)
    return if (m >= 60) "${m / 60} ч ${m % 60} мин" else "$m мин"
}

fun totalTime(list: List<Track>): String = hm(list.sumOf { it.durationMs } / 1000)
