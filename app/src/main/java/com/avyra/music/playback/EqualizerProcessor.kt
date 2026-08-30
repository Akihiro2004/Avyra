package com.avyra.music.playback

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * A ten-band equalizer that runs inside the player rather than beside it.
 *
 * The row this replaces did not equalize anything: it fired
 * `ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL` and hoped the manufacturer had
 * shipped an app to catch it. On a device that hadn't, it toasted and did
 * nothing — and on one that had, the curve, the band count and the quality all
 * belonged to somebody else. It also sat *downstream* of everything in this
 * file's neighbourhood, at the audio track, so it could never be reasoned about
 * alongside the spatial widener or the transition filter.
 *
 * This sits in the same chain as those two, gets the same 16-bit PCM, and
 * behaves identically on every device.
 *
 * ### The filters
 *
 * One peaking biquad per band per channel, cascaded — the standard
 * constant-Q design, at [Q_OCTAVE] for the one-octave spacing the ISO centres
 * in [FREQUENCIES] imply. Peaking rather than shelving at the ends: a shelf at
 * 31 Hz lifts everything below it including DC and rumble no speaker will
 * reproduce, and the same at 16 kHz lifts hiss. A bell leaves both alone.
 *
 * Run as transposed direct form II, which is the form that keeps its
 * arithmetic well-conditioned at the low end — a 31 Hz bell at 44.1 kHz has its
 * poles extremely close to the unit circle, and direct form I accumulates
 * visible error there.
 *
 * ### Three things a naive EQ gets wrong
 *
 *  - **Gain changes have to glide.** Writing new coefficients the instant a
 *    slider moves steps the transfer function between samples, which is a
 *    click. Gains ramp toward their targets a block at a time, the same way
 *    [TransitionFilterProcessor] ramps its cutoffs and for the same reason.
 *  - **Boosting needs headroom.** Ten bands lifted at once will exceed full
 *    scale on any loud track, and clamping to `Short` there is not "loud", it
 *    is distortion. Hence [preampDb], and hence the soft limiter below it —
 *    the preamp is the honest fix and the limiter is the net under it.
 *  - **Flat has to cost nothing.** An EQ sitting at 0 dB should be
 *    indistinguishable from no EQ, not merely inaudible: see the bypass in
 *    [queueInput], which hands the buffer through untouched rather than
 *    running ten unity-gain biquads over it.
 */
@UnstableApi
class EqualizerProcessor : BaseAudioProcessor() {

    /** Whether the listener has switched the effect on at all. */
    @Volatile
    var enabled: Boolean = false

    private val targetGainDb = FloatArray(BANDS)
    private val currentGainDb = FloatArray(BANDS)

    @Volatile
    private var targetPreampDb: Float = 0f
    private var currentPreampDb: Float = 0f

    /** Normalized biquad coefficients, five per band. */
    private val coefficients = FloatArray(BANDS * 5)

    /**
     * Filter memory: two words per band per channel.
     *
     * One flat array rather than nested ones because this is indexed once per
     * sample per band — at 48 kHz stereo that is nearly a million lookups a
     * second, and an array of arrays pays a bounds check and a pointer chase on
     * every one of them.
     */
    private var state = FloatArray(0)

    private var channelCount = 0
    private var sampleRate = 0

    /** Set whenever a target moves, so coefficients are rebuilt only then. */
    private var coefficientsStale = true

    // ---- Controls ----------------------------------------------------------

    /**
     * Sets one band's gain in dB, clamped to [MAX_GAIN_DB].
     *
     * Takes effect over the next few milliseconds rather than immediately —
     * see the glide note in the class doc.
     */
    fun setBandGain(band: Int, db: Float) {
        if (band !in 0 until BANDS) return
        targetGainDb[band] = db.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)
    }

    /** All ten at once, for applying a preset or restoring saved settings. */
    fun setGains(db: FloatArray) {
        for (band in 0 until min(BANDS, db.size)) setBandGain(band, db[band])
    }

    /**
     * Headroom, in dB, applied before the bands.
     *
     * Negative values are the point: pulling the whole signal down by the
     * largest boost about to be applied is what stops that boost clipping.
     */
    fun setPreamp(db: Float) {
        targetPreampDb = db.coerceIn(-MAX_PREAMP_DB, MAX_PREAMP_DB)
    }

    // ---- Media3 plumbing ---------------------------------------------------

    /**
     * 16-bit PCM only, matching the other two processors in this chain — and
     * bowing out with [AudioProcessor.AudioFormat.NOT_SET] rather than throwing
     * for the same reason they do: `DefaultAudioSink` configures every
     * processor whether or not its effect is switched on, and a throw from any
     * of them kills the renderer outright.
     */
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            Log.w(
                TAG,
                "Equalizer inactive: encoding=${inputAudioFormat.encoding} " +
                    "channels=${inputAudioFormat.channelCount} is not 16-bit PCM",
            )
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        state = FloatArray(BANDS * channelCount * 2)
        targetGainDb.copyInto(currentGainDb)
        currentPreampDb = targetPreampDb
        coefficientsStale = true
        return inputAudioFormat
    }

    override fun onFlush() {
        state.fill(0f)
        // Snapped rather than glided: a flush is a seek or a new source, so
        // there is no continuous signal for a glide to stay continuous with.
        targetGainDb.copyInto(currentGainDb)
        currentPreampDb = targetPreampDb
        coefficientsStale = true
    }

    override fun onReset() {
        state = FloatArray(0)
        targetGainDb.fill(0f)
        currentGainDb.fill(0f)
        targetPreampDb = 0f
        currentPreampDb = 0f
        coefficientsStale = true
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val bytesPerFrame = BYTES_PER_SAMPLE * channelCount
        if (bytesPerFrame == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        // Off, or flat and already settled there, is a straight pass-through.
        // The "already settled" half matters for the same reason it does in the
        // transition filter: a band still gliding back to zero is still shaping
        // the signal, and dropping it out mid-glide is the click the glide
        // exists to avoid.
        if (!enabled || (isFlat(targetGainDb) && targetIsSettled())) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        var remaining = frameCount
        while (remaining > 0) {
            val block = min(remaining, GLIDE_FRAMES)
            if (glideTowardTargets()) coefficientsStale = true
            if (coefficientsStale) {
                updateCoefficients()
                coefficientsStale = false
            }
            val preamp = dbToLinear(currentPreampDb)

            repeat(block) {
                for (channel in 0 until channelCount) {
                    // Normalized to ±1 so the limiter below has a fixed knee to
                    // work against rather than one scaled to the sample format.
                    var sample = inputBuffer.short.toFloat() / SHORT_SCALE * preamp
                    for (band in 0 until BANDS) {
                        sample = runBand(band, channel, sample)
                    }
                    outputBuffer.putShort(clampToShort(softLimit(sample) * SHORT_SCALE))
                }
            }
            remaining -= block
        }
        outputBuffer.flip()
    }

    // ---- Filter ------------------------------------------------------------

    /** @return true if anything actually moved, so coefficients need rebuilding. */
    private fun glideTowardTargets(): Boolean {
        var moved = false
        for (band in 0 until BANDS) {
            val delta = targetGainDb[band] - currentGainDb[band]
            if (abs(delta) > SETTLED_DB) {
                currentGainDb[band] += delta * GLIDE_RATE
                moved = true
            } else if (currentGainDb[band] != targetGainDb[band]) {
                currentGainDb[band] = targetGainDb[band]
                moved = true
            }
        }
        val preampDelta = targetPreampDb - currentPreampDb
        if (abs(preampDelta) > SETTLED_DB) {
            currentPreampDb += preampDelta * GLIDE_RATE
        } else {
            currentPreampDb = targetPreampDb
        }
        return moved
    }

    private fun targetIsSettled(): Boolean {
        for (band in 0 until BANDS) {
            if (abs(currentGainDb[band] - targetGainDb[band]) > SETTLED_DB) return false
        }
        return abs(currentPreampDb - targetPreampDb) <= SETTLED_DB
    }

    private fun isFlat(gains: FloatArray): Boolean {
        for (g in gains) if (abs(g) > SETTLED_DB) return false
        return abs(targetPreampDb) <= SETTLED_DB
    }

    /**
     * The standard peaking-EQ biquad, one per band, normalized by `a0`.
     *
     * A band sitting at 0 dB still gets coefficients — they come out as exact
     * unity and cost one multiply-add chain per sample. Skipping those
     * individually was measured as slower than running them: the branch per
     * band per sample costs more than the arithmetic it avoids.
     */
    private fun updateCoefficients() {
        val nyquist = sampleRate / 2f
        for (band in 0 until BANDS) {
            val f0 = FREQUENCIES[band]
            // A band above Nyquist has nothing to act on; park it at unity so
            // 16 kHz does not blow up on a 22.05 kHz stream.
            if (f0 >= nyquist * MAX_FREQ_FRACTION) {
                setUnity(band)
                continue
            }
            val a = dbToLinear(currentGainDb[band] / 2f)
            val w0 = TWO_PI * f0 / sampleRate
            val cosW0 = cos(w0.toDouble()).toFloat()
            val alpha = sin(w0.toDouble()).toFloat() / (2f * Q_OCTAVE)

            val b0 = 1f + alpha * a
            val b1 = -2f * cosW0
            val b2 = 1f - alpha * a
            val a0 = 1f + alpha / a
            val a1 = -2f * cosW0
            val a2 = 1f - alpha / a

            val i = band * 5
            coefficients[i] = b0 / a0
            coefficients[i + 1] = b1 / a0
            coefficients[i + 2] = b2 / a0
            coefficients[i + 3] = a1 / a0
            coefficients[i + 4] = a2 / a0
        }
    }

    private fun setUnity(band: Int) {
        val i = band * 5
        coefficients[i] = 1f
        coefficients[i + 1] = 0f
        coefficients[i + 2] = 0f
        coefficients[i + 3] = 0f
        coefficients[i + 4] = 0f
    }

    /** Transposed direct form II — see the class doc for why this form. */
    private fun runBand(band: Int, channel: Int, input: Float): Float {
        val c = band * 5
        val s = (band * channelCount + channel) * 2
        val z1 = state[s]
        val z2 = state[s + 1]
        val output = coefficients[c] * input + z1
        state[s] = coefficients[c + 1] * input - coefficients[c + 3] * output + z2
        state[s + 1] = coefficients[c + 2] * input - coefficients[c + 4] * output
        return output
    }

    /**
     * A soft knee at the top of the range, so a boosted peak rounds over
     * instead of being sheared flat.
     *
     * Below [LIMIT_KNEE] this is exactly unity — the overwhelming majority of
     * samples, on which it costs one comparison. Above it, `tanh` maps the
     * remaining headroom onto an asymptote at 1.0, so nothing can reach full
     * scale however hard the bands are pushed. That is audibly a compressor
     * rather than a clipper: harmonic, not fizzy.
     */
    private fun softLimit(sample: Float): Float {
        val magnitude = abs(sample)
        if (magnitude <= LIMIT_KNEE) return sample
        val excess = (magnitude - LIMIT_KNEE) / (1f - LIMIT_KNEE)
        val limited = LIMIT_KNEE + (1f - LIMIT_KNEE) * tanh(excess.toDouble()).toFloat()
        return if (sample < 0f) -limited else limited
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    private fun dbToLinear(db: Float): Float =
        Math.pow(10.0, (db / 20f).toDouble()).toFloat()

    companion object {
        private const val TAG = "AvyraEqualizer"

        /** ISO octave centres, the spacing every graphic EQ people have used runs on. */
        val FREQUENCIES = floatArrayOf(
            31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f,
        )

        /** Short labels for the UI, so it never has to format these itself. */
        val LABELS = arrayOf(
            "31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k",
        )

        const val BANDS = 10

        /**
         * Wide enough that ten bands sum to something smooth rather than ten
         * separate bumps, narrow enough that each still belongs to its own
         * octave. The constant-Q value for one-octave spacing.
         */
        private const val Q_OCTAVE = 1.414f

        /** The range the sliders offer. Past this an EQ stops shaping and starts breaking. */
        const val MAX_GAIN_DB = 12f

        const val MAX_PREAMP_DB = 12f

        private const val BYTES_PER_SAMPLE = 2
        private const val SHORT_SCALE = 32768f
        private const val TWO_PI = 6.283185f

        /** Frames between coefficient rebuilds. ~1.5 ms at 44.1 kHz. */
        private const val GLIDE_FRAMES = 64

        /** Per-block glide fraction, matching the transition filter's feel. */
        private const val GLIDE_RATE = 0.05f

        /** Close enough to a target to call it reached, so a glide terminates. */
        private const val SETTLED_DB = 0.01f

        /** Where the limiter starts rounding. Roughly -1.6 dBFS. */
        private const val LIMIT_KNEE = 0.83f

        /** Keeps a band's centre clear of Nyquist, where the maths degenerates. */
        private const val MAX_FREQ_FRACTION = 0.9f
    }
}
