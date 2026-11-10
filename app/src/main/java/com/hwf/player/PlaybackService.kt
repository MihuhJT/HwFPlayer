package com.hwf.player

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Plays music in the background. The media session also makes the player's
 * hardware buttons and headphone buttons work.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var countedItem: String? = null  // track already counted as a play in this run

    // Crossfade: two players take turns. The session always points at the one playing the
    // current track; the other one fades the previous track out and then waits for the next turn.
    private val attrs = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()
    private val meters = arrayOf(MeterGate(), MeterGate())
    private lateinit var players: Array<ExoPlayer>
    private var active = 0
    private var fadingOut: ExoPlayer? = null
    private var fadeStart = 0L

    private val fadeTick = object : Runnable {
        override fun run() {
            val cur = players[active]
            val out = fadingOut
            if (out == null) {
                val left = cur.duration - cur.currentPosition
                if (cur.isPlaying && cur.hasNextMediaItem() && cur.repeatMode != Player.REPEAT_MODE_ONE &&
                    cur.duration != C.TIME_UNSET && cur.duration > FADE_MS * 3 && left in 1..FADE_MS
                ) startFade()
            } else {
                val p = ((System.currentTimeMillis() - fadeStart) / FADE_MS.toFloat()).coerceIn(0f, 1f)
                // equal-power curves: the sum sounds equally loud all the way through
                cur.volume = sin(p * PI / 2).toFloat()
                out.volume = cos(p * PI / 2).toFloat()
                if (p >= 1f || !cur.playWhenReady) finishFade()  // done, or paused mid-fade
            }
            handler.postDelayed(this, 40)
        }
    }

    private fun startFade() {
        val out = players[active]
        val inc = players[1 - active]
        val items = (0 until out.mediaItemCount).map(out::getMediaItemAt)
        // Only one player feeds the blob's bass meter at a time; it has a single state
        meters[active].on = false
        meters[1 - active].on = true
        out.pauseAtEndOfMediaItems = true  // its track ends inside the fade; don't let it start the next one too
        out.setAudioAttributes(attrs, false)  // give up audio focus first, or the new player's request pauses this one
        inc.setAudioAttributes(attrs, true)
        inc.repeatMode = out.repeatMode
        inc.shuffleModeEnabled = out.shuffleModeEnabled
        inc.setMediaItems(items, out.nextMediaItemIndex, 0)
        inc.volume = 0f
        inc.prepare()
        inc.play()
        session?.player = inc
        active = 1 - active
        fadingOut = out
        fadeStart = System.currentTimeMillis()
    }

    private fun finishFade() {
        val out = fadingOut ?: return
        fadingOut = null
        out.stop()
        out.clearMediaItems()
        out.pauseAtEndOfMediaItems = false
        out.volume = 1f
        players[active].volume = 1f
    }

    // Listening stats are logged here, so they count with the screen off too
    private val statsTick = object : Runnable {
        override fun run() {
            session?.player?.let { p ->
                if (p.isPlaying) {
                    Store.logSecond(p.mediaMetadata.artist?.toString() ?: "Неизвестный исполнитель")
                    val id = p.currentMediaItem?.mediaId
                    if (p.currentPosition >= 30_000 && id != null && countedItem != id) {  // a play counts after 30 s
                        countedItem = id
                        id.toLongOrNull()?.let(Store::countPlay)
                    }
                    if (p.currentPosition < 30_000 && countedItem == id) countedItem = null  // replayed from start
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        players = Array(2) { k ->
            ExoPlayer.Builder(this, AnalyzingRenderersFactory(this, meters[k]))
                .setAudioAttributes(attrs, /* handleAudioFocus = */ k == 0)  // only the session's player holds focus
                .setHandleAudioBecomingNoisy(true)  // pause when headphones are unplugged
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        }
        meters[1].on = false
        val player = players[0]
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // Items from our UI carry the file URI in requestMetadata; restore it here
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { item ->
                        val uri = item.requestMetadata.mediaUri
                        if (item.localConfiguration == null && uri != null) item.buildUpon().setUri(uri).build() else item
                    }.toMutableList()
                )
            })
            .build()
        handler.post(statsTick)
        handler.post(fadeTick)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(statsTick)
        handler.removeCallbacks(fadeTick)
        Store.saveStats()
        session?.release()
        players.forEach { it.release() }
        session = null
        super.onDestroy()
    }
}

/** Puts a tap into the audio chain so the UI can react to the bass without microphone permission. */
private class AnalyzingRenderersFactory(context: Context, private val meter: MeterGate) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setAudioProcessors(arrayOf<AudioProcessor>(TeeAudioProcessor(meter)))
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .build()
}

private const val FADE_MS = 6000L

/** Passes one player's audio to [BassMeter] only while that player is the current one. */
private class MeterGate : TeeAudioProcessor.AudioBufferSink {
    @Volatile var on = true

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        if (on) BassMeter.flush(sampleRateHz, channelCount, encoding)
    }

    override fun handleBuffer(buffer: java.nio.ByteBuffer) {
        if (on) BassMeter.handleBuffer(buffer)
    }
}
