package com.hwf.player

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Receives a copy of the decoded audio. Keeps a 0..1 bass [level] for the blob and
 * estimates the tempo ([bpm], 0 = not known yet) from how the bass hits repeat.
 * Runs on the audio thread; the UI just reads the two volatile fields.
 */
object BassMeter : TeeAudioProcessor.AudioBufferSink {
    @Volatile var level = 0f
        private set
    @Volatile var bpm = 0f
        private set

    private var sampleRate = 44100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var alpha = 0f
    private var lowPass = 0f
    private var alphaHi = 0f    // 2 kHz low-pass; the signal minus it = snares/hi-hats
    private var lowPass2k = 0f

    // Tempo: band energies in ~11.6 ms hops -> onset strength (energy rises) -> autocorrelation
    private const val HOP_RATE = 86f          // hops per second
    private const val HISTORY = 688           // ~8 s of onsets
    private val onsets = FloatArray(HISTORY)
    private var filled = 0
    private var head = 0
    private var hopSize = 512
    private var hopFrames = 0
    private var hopEnergy = 0f
    private var hopEnergyHi = 0f
    private var prevEnergy = 0f
    private var prevEnergyHi = 0f
    private var sinceEstimate = 0
    private var fresh = true                  // first estimate after a track change/seek is taken as is
    private var candidate = 0f
    private var candidateVotes = 0

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        // one-pole low-pass around 150 Hz: kick drums and bass
        alpha = (1 - exp(-2 * PI * 150 / sampleRateHz)).toFloat()
        alphaHi = (1 - exp(-2 * PI * 2000 / sampleRateHz)).toFloat()
        lowPass = 0f
        lowPass2k = 0f
        hopSize = (sampleRateHz / HOP_RATE).toInt().coerceAtLeast(64)
        filled = 0; head = 0; hopFrames = 0; hopEnergy = 0f; hopEnergyHi = 0f; prevEnergy = 0f; prevEnergyHi = 0f; sinceEstimate = 0
        fresh = true
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val buf = buffer.duplicate().order(ByteOrder.nativeOrder())
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        val frameBytes = bytesPerSample * channels
        var sumSq = 0f
        var frames = 0
        while (buf.remaining() >= frameBytes) {
            var mono = 0f
            repeat(channels) { mono += readSample(buf) }
            mono /= channels
            lowPass += alpha * (mono - lowPass)
            val e = lowPass * lowPass
            sumSq += e
            frames++
            hopEnergy += e
            lowPass2k += alphaHi * (mono - lowPass2k)
            val hi = mono - lowPass2k
            hopEnergyHi += hi * hi
            if (++hopFrames >= hopSize) endHop()
        }
        if (frames > 0) level = min(1f, sqrt(sumSq / frames) * 4f)
    }

    private fun endHop() {
        // log so quiet and loud tracks behave alike; kick rises + snare/hat rises
        val energy = ln(1e-6f + hopEnergy / hopFrames)
        val energyHi = ln(1e-7f + hopEnergyHi / hopFrames)
        onsets[head] = max(0f, energy - prevEnergy) + max(0f, energyHi - prevEnergyHi)
        prevEnergy = energy
        prevEnergyHi = energyHi
        hopEnergyHi = 0f
        head = (head + 1) % HISTORY
        if (filled < HISTORY) filled++
        hopFrames = 0
        hopEnergy = 0f
        // re-estimate twice a second once there are ~5 s of history
        if (++sinceEstimate >= HOP_RATE / 2 && filled >= 430) {
            sinceEstimate = 0
            estimate()
        }
    }

    private fun estimate() {
        val minLag = (60 * HOP_RATE / 180).toInt()   // 180 BPM
        val maxLag = (60 * HOP_RATE / 70).toInt()    // 70 BPM
        var bestLag = 0
        var best = 0f
        val top = 2 * maxLag + 2
        val scores = FloatArray(top + 1)
        for (lag in minLag..top) {
            if (lag >= filled) break
            var s = 0f
            for (k in lag until filled) {
                val a = onsets[(head - 1 - k + lag + HISTORY * 2) % HISTORY]
                val b = onsets[(head - 1 - k + HISTORY * 2) % HISTORY]
                s += a * b
            }
            scores[lag] = s / (filled - lag)  // longer lags have fewer pairs
        }
        for (lag in minLag..maxLag) {
            val bpmAt = 60 * HOP_RATE / lag
            // gentle preference for ~120 BPM to avoid half/double-tempo picks
            val octaves = ln(bpmAt / 120f) / ln(2f)
            // comb: a real beat period also lines up at twice the interval
            val comb = scores[lag] + .5f * scores[2 * lag]
            val w = comb * exp(-.5f * octaves * octaves / .64f)
            if (w > best) { best = w; bestLag = lag }
        }
        if (bestLag == 0 || best <= 0f) return
        // parabolic refinement between neighbouring lags
        val y0 = scores[bestLag - 1]; val y1 = scores[bestLag]; val y2 = scores[bestLag + 1]
        val denom = y0 - 2 * y1 + y2
        val shift = if (denom != 0f) (.5f * (y0 - y2) / denom).coerceIn(-.5f, .5f) else 0f
        val found = 60 * HOP_RATE / (bestLag + shift)

        when {
            fresh || bpm == 0f -> { bpm = found; fresh = false }
            abs(found - bpm) / bpm < .06f -> bpm = bpm * .8f + found * .2f   // same tempo: smooth it
            else -> {                                                            // different: needs 3 votes in a row
                if (abs(found - candidate) / found < .06f) candidateVotes++ else { candidate = found; candidateVotes = 1 }
                if (candidateVotes >= 3) { bpm = candidate; candidateVotes = 0 }
            }
        }
    }

    private fun readSample(buf: ByteBuffer): Float = when (encoding) {
        C.ENCODING_PCM_16BIT -> buf.short / 32768f
        C.ENCODING_PCM_FLOAT -> buf.float
        C.ENCODING_PCM_32BIT -> buf.int / 2147483648f
        else -> {  // 24-bit little endian
            val b0 = buf.get().toInt() and 0xFF
            val b1 = buf.get().toInt() and 0xFF
            val b2 = buf.get().toInt()
            ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
        }
    }
}
