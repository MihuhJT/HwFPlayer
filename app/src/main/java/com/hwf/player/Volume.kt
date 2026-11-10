package com.hwf.player

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/** System music volume as 0..100, set without the system volume popup (we draw our own). */
class Volume(context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    val percent: Int
        get() = (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt()

    fun setPercent(p: Float) {
        val index = (p.coerceIn(0f, 100f) / 100f * max).roundToInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    }

    /** One hardware step up or down. */
    fun step(direction: Int) {
        val index = (am.getStreamVolume(AudioManager.STREAM_MUSIC) + direction).coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    }
}
