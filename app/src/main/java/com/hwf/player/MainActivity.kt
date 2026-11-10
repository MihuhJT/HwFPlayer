package com.hwf.player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    /** Hardware volume keys: +1 / -1. We handle them to show our own overlay. */
    val volumeKeys = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full screen: the app draws its own clock
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { HwfApp(this) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val dir = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) volumeKeys.tryEmit(dir)
        return true
    }
}

private val audioPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun HwfApp(activity: MainActivity) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askPermission.launch(audioPermission) }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).padding(32.dp).clickable { askPermission.launch(audioPermission) },
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Нужен доступ к музыке", color = Color.White, fontSize = 18.sp)
            Text("Тапни, чтобы разрешить", color = Color(0xFF888888), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        return
    }

    // Connect to the playback service
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = future.get() }, ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(future) }
    }

    LaunchedEffect(Unit) { Store.init(context) }
    var library by remember { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(Unit) { library = withContext(Dispatchers.IO) { loadLibrary(context) } }
    LaunchedEffect(controller, library) {
        val c = controller ?: return@LaunchedEffect
        val lib = library ?: return@LaunchedEffect
        if (c.mediaItemCount > 0 || lib.isEmpty()) return@LaunchedEffect  // already playing from before
        // First playlist: 20 random tracks
        c.setMediaItems(lib.shuffled().take(20).map { it.toMediaItem() })
        c.prepare()
        c.play()
    }
    val playList: OnPlay = { tracks, name, start ->
        controller?.run {
            setMediaItems(tracks.map { it.toMediaItem() }, start, 0)
            prepare()
            play()
        }
        Store.source = name
    }

    // Poll the player a few times a second
    var np by remember { mutableStateOf(NowPlaying()) }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            val md = c.mediaMetadata
            var sampleRate = C.RATE_UNSET_INT
            for (g in c.currentTracks.groups) if (g.type == C.TRACK_TYPE_AUDIO && g.length > 0) sampleRate = g.getTrackFormat(0).sampleRate
            val codec = codecLabel(md.extras?.getString("mime"))
            val khz = if (sampleRate > 0) "${if (sampleRate % 1000 == 0) sampleRate / 1000 else "%.1f".format(sampleRate / 1000f)} KHZ" else ""
            np = NowPlaying(
                title = md.title?.toString() ?: "",
                artist = md.artist?.toString() ?: "",
                playing = c.isPlaying || (c.playWhenReady && c.playbackState == androidx.media3.common.Player.STATE_BUFFERING),
                positionMs = c.currentPosition,
                durationMs = c.duration.takeIf { it > 0 } ?: 0,
                index = c.currentMediaItemIndex,
                count = c.mediaItemCount,
                quality = listOf(codec, khz, BassMeter.bpm.let { if (it > 0f) "${Math.round(it)} BPM" else "" })
                    .filter { it.isNotEmpty() }.joinToString(" · "),
            )
            delay(200)
        }
    }

    // Clock
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val ru = Locale.forLanguageTag("ru")
        while (true) {
            val now = Date()
            time = SimpleDateFormat("H:mm", ru).format(now)
            date = SimpleDateFormat("EEEE, d MMM", ru).format(now).replace(".", "").replaceFirstChar { it.uppercase() }
            delay(1000)
        }
    }

    // Volume: our overlay for swipes, hardware keys and external changes
    val vol = remember { Volume(context) }
    var volume by remember { mutableIntStateOf(vol.percent) }
    var volumeVisible by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var lastChange by remember { mutableStateOf(0L) }
    fun showVolume() { volume = vol.percent; volumeVisible = true; lastChange = System.currentTimeMillis() }
    LaunchedEffect(Unit) { activity.volumeKeys.collect { vol.step(it); showVolume() } }
    LaunchedEffect(Unit) {
        while (true) {
            val v = vol.percent
            if (v != volume) showVolume()  // changed by the player's own wheel/buttons
            if (volumeVisible && !dragging && System.currentTimeMillis() - lastChange > 1400) volumeVisible = false
            delay(100)
        }
    }

    // Pause the glass animation when the screen is off / app in background
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)

    val lib = library
    if (lib != null && lib.isEmpty()) {
        Column(Modifier.fillMaxSize().background(Color.Black), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("На плеере не нашлось музыки", color = Color.White, fontSize = 16.sp)
        }
        return
    }

    // Side panels: playlists come from the left, search from the right.
    // progress 0 = hidden, 1 = fully open; it follows the finger while dragging.
    val scope = rememberCoroutineScope()
    var panel by remember { mutableStateOf<Panel?>(null) }
    var panelOpen by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val shortSwipePx = with(LocalDensity.current) { 40.dp.toPx() }
    val flingPx = with(LocalDensity.current) { 400.dp.toPx() }  // per second
    fun settle(open: Boolean) {
        scope.launch {
            progress.animateTo(if (open) 1f else 0f, tween(320))
            panelOpen = open
            if (!open) panel = null
        }
    }
    val closePanel = { settle(false) }
    val play: OnPlay = { tracks, name, start -> playList(tracks, name, start); closePanel() }
    val nowId = controller?.currentMediaItem?.mediaId?.toLongOrNull().takeIf { np.count >= 0 }
    val byId = remember(lib) { lib?.associateBy { it.id } ?: emptyMap() }
    val nowTrack = nowId?.let(byId::get)
    val cover by produceState<ImageBitmap?>(null, nowTrack?.id) {
        value = nowTrack?.let { Covers.blob(context, it.id, it.uri) }
    }

    Box(Modifier.fillMaxSize()) {
    NowPlayingScreen(
        state = np,
        source = Store.source,
        time = time,
        date = date,
        volume = volume,
        volumeVisible = volumeVisible,
        animate = visible && !volumeVisible && !panelOpen,
        cover = cover,
        onPlayPause = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
        onPrev = { controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() } },
        onNext = { controller?.seekToNextMediaItem() },
        onSeek = { controller?.seekTo(it) },
        onVolumeDrag = { start, dy, slide ->
            dragging = true
            vol.setPercent(start - dy / slide * 100f)
            showVolume()
        },
        onVolumeDragEnd = { dragging = false; volumeVisible = false },
        onPanelDrag = { dx ->
            if (lib == null) return@NowPlayingScreen  // library still loading
            if (panel == null) panel = if (dx > 0) Panel.Playlists else Panel.Search
            val p = if (panel == Panel.Playlists) dx / widthPx else -dx / widthPx
            scope.launch { progress.snapTo(p.coerceIn(0f, 1f)) }
        },
        // a short swipe or a quick flick is enough
        onPanelDragEnd = { dx, v -> if (panel != null) settle(abs(dx) > shortSwipePx || abs(v) > flingPx) },
    )

    val current = panel
    if (current != null && lib != null) {
        val sign = if (current == Panel.Playlists) -1 else 1
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset((sign * widthPx * (1f - progress.value)).roundToInt(), 0) }
                .background(Color.Black)
                // Swipe back toward the panel's own edge closes it. Runs in the Initial pass, i.e. before
                // the buttons/fields under the finger, so once it's a swipe they don't get a click.
                .pointerInput(current) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var dx = 0f
                        var dy = 0f
                        var mine = false
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                            dx += ch.positionChange().x
                            dy += ch.positionChange().y
                            tracker.addPosition(ch.uptimeMillis, ch.position)
                            if (!mine && abs(dx) + abs(dy) > viewConfiguration.touchSlop) {
                                // vertical (list scroll, keyboard swipe down) or the wrong way: not ours
                                if (abs(dx) > abs(dy) && sign(dx) == sign.toFloat()) mine = true else return@awaitEachGesture
                            }
                            if (mine) {
                                ch.consume()
                                scope.launch { progress.snapTo((1f - abs(dx) / widthPx).coerceIn(0f, 1f)) }
                            }
                            if (!ch.pressed) break
                        }
                        if (mine) settle(!(abs(dx) > shortSwipePx || abs(tracker.calculateVelocity().x) > flingPx))
                    }
                }
        ) {
            when (current) {
                Panel.Playlists -> PlaylistsPanel(lib, nowId, play, closePanel)
                Panel.Search -> SearchPanel(lib, nowId, panelOpen, play, closePanel)
            }
        }
    }
    }
}

enum class Panel { Playlists, Search }
