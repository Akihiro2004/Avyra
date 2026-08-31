package com.avyra.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.avyra.music.playback.EqualizerProcessor
import com.avyra.music.ui.screens.PRESETS
import com.avyra.music.ui.screens.suggestedPreamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What the equalizer actually does to a signal, measured rather than reasoned
 * about.
 *
 * The reason this is worth a test at all is that every failure it has is
 * silent. A biquad with the wrong coefficients still outputs audio; a
 * processor that bows out of the chain still plays the track; a gain that
 * never reaches the filter still moves the number above the fader. None of it
 * throws, so the only way to know a band is doing what it says is to put a
 * tone through it and measure what comes out the other end.
 *
 * Everything here runs at a low amplitude on purpose — see [AMPLITUDE]. The
 * soft limiter is transparent below its knee, and a test that tripped it would
 * be measuring the limiter rather than the filter.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class EqualizerTest {

    private companion object {
        const val RATE = 44_100
        const val FRAMES = RATE // one second, far longer than the filters' settling

        /** Well under [EqualizerProcessor]'s 0.83 limiter knee, so nothing is compressed. */
        const val AMPLITUDE = 0.2

        /**
         * How far a measured gain may sit from the one asked for.
         *
         * Generous because this is measured off a real biquad rather than
         * computed: a one-octave bell at its own centre frequency lands within
         * a fraction of a dB of its nominal gain, and the slack is for the
         * 16-bit quantisation either side of it rather than for the filter.
         */
        const val TOLERANCE_DB = 0.6

        fun flat() = FloatArray(EqualizerProcessor.BANDS)
    }

    // ---- Harness ------------------------------------------------------------

    private fun processor(
        gains: FloatArray = flat(),
        preamp: Float = 0f,
        on: Boolean = true,
        channels: Int = 1,
    ): EqualizerProcessor = EqualizerProcessor().apply {
        enabled = on
        setGains(gains)
        setPreamp(preamp)
        configure(AudioProcessor.AudioFormat(RATE, channels, C.ENCODING_PCM_16BIT))
        // Media3 calls this before the first buffer, and it is what settles the
        // gain glide onto its target — so a test that skipped it would be
        // measuring the ramp rather than the curve.
        flush()
    }

    /** A mono 16-bit tone, as Media3 would hand it to the chain. */
    private fun tone(hz: Double, frames: Int = FRAMES): ByteBuffer =
        ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder()).apply {
            for (n in 0 until frames) {
                putShort((sin(2 * PI * hz * n / RATE) * AMPLITUDE * Short.MAX_VALUE).toInt().toShort())
            }
            flip()
        }

    private fun EqualizerProcessor.process(input: ByteBuffer): ShortArray {
        queueInput(input)
        val out = output
        val samples = ShortArray(out.remaining() / 2)
        out.asShortBuffer().get(samples)
        return samples
    }

    private fun ShortArray.rms(): Double {
        if (isEmpty()) return 0.0
        var sum = 0.0
        // The first tenth is dropped: a biquad's output at the very start of a
        // signal is its impulse response settling, not its steady-state gain.
        val from = size / 10
        for (i in from until size) {
            val v = this[i].toDouble() / Short.MAX_VALUE
            sum += v * v
        }
        return sqrt(sum / (size - from))
    }

    /** How much the processor changed a tone at [hz], in dB. */
    private fun gainAt(hz: Double, gains: FloatArray, preamp: Float = 0f): Double {
        val dry = processor(on = false).process(tone(hz)).rms()
        val wet = processor(gains = gains, preamp = preamp).process(tone(hz)).rms()
        return 20 * log10(wet / dry)
    }

    private fun boostOf(band: Int, db: Float) = flat().also { it[band] = db }

    // ---- The band actually being boosted ------------------------------------

    /**
     * The whole point of the control: a band moved to +12 dB makes a tone at
     * that band's centre 12 dB louder.
     *
     * Checked at three centres rather than one because the coefficient maths
     * degrades at the ends — a 31 Hz bell at 44.1 kHz has its poles very close
     * to the unit circle, and a 16 kHz one is near enough Nyquist to be
     * skipped entirely by a bad bounds check.
     */
    @Test
    fun `a boosted band lifts a tone at its centre by the amount asked for`() {
        for (band in intArrayOf(0, 5, 9)) {
            val hz = EqualizerProcessor.FREQUENCIES[band].toDouble()
            val measured = gainAt(hz, boostOf(band, 12f))
            assertEquals(
                "band $band (${hz.toInt()} Hz) measured ${"%.2f".format(measured)} dB",
                12.0,
                measured,
                TOLERANCE_DB,
            )
        }
    }

    /** And the same in the other direction, which is the half people actually use. */
    @Test
    fun `a cut band drops a tone at its centre by the amount asked for`() {
        val hz = EqualizerProcessor.FREQUENCIES[5].toDouble()
        val measured = gainAt(hz, boostOf(5, -12f))
        assertEquals("measured ${"%.2f".format(measured)} dB", -12.0, measured, TOLERANCE_DB)
    }

    /**
     * A bell, not a shelf: boosting 1 kHz must leave 31 Hz alone.
     *
     * This is what separates a working ten-band EQ from one that just changes
     * the volume — and a volume change is exactly what a listener reports as
     * "moving the sliders barely does anything", because every band sounds the
     * same as every other.
     */
    @Test
    fun `a boosted band leaves the rest of the spectrum alone`() {
        val boosted = boostOf(5, 12f) // 1 kHz
        assertTrue(
            "31 Hz moved by ${"%.2f".format(gainAt(31.0, boosted))} dB",
            abs(gainAt(31.0, boosted)) < 1.0,
        )
        assertTrue(
            "16 kHz moved by ${"%.2f".format(gainAt(16_000.0, boosted))} dB",
            abs(gainAt(16_000.0, boosted)) < 1.0,
        )
    }

    /** Two adjacent bands overlap by design; an octave apart they should not fight. */
    @Test
    fun `neighbouring bands do not cancel each other`() {
        val both = flat().also { it[4] = 12f; it[5] = 12f } // 500 Hz and 1 kHz
        assertTrue(
            "500 Hz measured ${"%.2f".format(gainAt(500.0, both))} dB",
            gainAt(500.0, both) > 11.0,
        )
        assertTrue(
            "1 kHz measured ${"%.2f".format(gainAt(1_000.0, both))} dB",
            gainAt(1_000.0, both) > 11.0,
        )
    }

    // ---- Preamp -------------------------------------------------------------

    /** Headroom is a plain gain in front of the bands, and has to behave like one. */
    @Test
    fun `preamp scales the whole signal`() {
        val measured = gainAt(1_000.0, flat(), preamp = -6f)
        assertEquals("measured ${"%.2f".format(measured)} dB", -6.0, measured, TOLERANCE_DB)
    }

    /**
     * The pairing every preset ships with: a boost, and the headroom to survive
     * it. The boosted band should come back to about unity rather than being
     * left quieter than it started.
     */
    @Test
    fun `a boost with matching preamp lands near unity at the boosted band`() {
        val measured = gainAt(1_000.0, boostOf(5, 6f), preamp = -6f)
        assertEquals("measured ${"%.2f".format(measured)} dB", 0.0, measured, TOLERANCE_DB)
    }

    // ---- Costing nothing when it should -------------------------------------

    /** Switched off, the samples that come out must be the ones that went in. */
    @Test
    fun `a disabled equalizer is bit-exact`() {
        val gains = boostOf(5, 12f)
        val dry = processor(on = false).process(tone(1_000.0))
        val wet = processor(gains = gains, on = false).process(tone(1_000.0))
        assertTrue("a disabled equalizer altered the signal", dry.contentEquals(wet))
    }

    /** And flat, with the effect on, has to be indistinguishable from off. */
    @Test
    fun `a flat curve is bit-exact`() {
        val dry = processor(on = false).process(tone(1_000.0))
        val wet = processor(gains = flat(), on = true).process(tone(1_000.0))
        assertTrue("a flat curve altered the signal", dry.contentEquals(wet))
    }

    // ---- Stereo -------------------------------------------------------------

    /**
     * Filter memory is per band *per channel*. Sharing one set between two
     * channels interleaves them through the same delay line, which is audible
     * as the stereo image collapsing — so both channels have to come out of a
     * boost identical when they went in identical.
     */
    @Test
    fun `both channels are filtered independently`() {
        val frames = FRAMES
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder()).apply {
            for (n in 0 until frames) {
                val v = (sin(2 * PI * 1_000 * n / RATE) * AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
                putShort(v)
                putShort(v)
            }
            flip()
        }
        val out = processor(gains = boostOf(5, 12f), channels = 2).process(input)
        val left = ShortArray(out.size / 2) { out[it * 2] }
        val right = ShortArray(out.size / 2) { out[it * 2 + 1] }
        assertTrue("the two channels diverged", left.contentEquals(right))
        assertEquals(
            "boost measured ${"%.2f".format(20 * log10(left.rms() / (AMPLITUDE / sqrt(2.0))))} dB",
            12.0,
            20 * log10(left.rms() / (AMPLITUDE / sqrt(2.0))),
            TOLERANCE_DB,
        )
    }

    // ---- Presets ------------------------------------------------------------

    /**
     * A preset has to make its own strongest band louder. That sounds too
     * obvious to test, and it is exactly what was wrong: the headroom taken to
     * stop a boost clipping was the *whole* boost, so every curve landed its
     * peak band at precisely unity and dropped everything else. "Bass" left the
     * bass where it found it and turned the rest down — a net cut with no net
     * boost anywhere in it, which is heard as the track getting quieter and
     * reported as the equalizer not working.
     *
     * Measured end to end through the filters rather than asserted against the
     * arithmetic, because the bands overlap and what reaches the ear is the sum
     * of them, not the number on the fader.
     */
    @Test
    fun `every preset that boosts is audibly louder at its strongest band`() {
        for ((name, curve) in PRESETS) {
            val peak = curve.max()
            // Flat, and any curve that only cuts, has nothing to prove here.
            if (peak <= 0f) continue
            val band = curve.indexOfFirst { it == peak }
            val hz = EqualizerProcessor.FREQUENCIES[band].toDouble()
            val net = gainAt(hz, curve, suggestedPreamp(curve))
            assertTrue(
                "preset '$name' lands at ${"%.2f".format(net)} dB at ${hz.toInt()} Hz, " +
                    "its own strongest band",
                net > 1.0,
            )
        }
    }

    /** Curves that only cut need no headroom, and must not be handed any. */
    @Test
    fun `a cut-only preset is given no preamp`() {
        assertEquals(0f, suggestedPreamp(floatArrayOf(-3f, -2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)), 0f)
        assertEquals(0f, suggestedPreamp(flat()), 0f)
    }

    // ---- Formats it must decline rather than mangle --------------------------

    /**
     * `DefaultAudioSink` configures every processor whether or not its effect
     * is on, so an unsupported format has to be declined rather than thrown on
     * — a throw here kills the renderer and the track with it.
     */
    @Test
    fun `an unsupported encoding is declined, not thrown`() {
        val processor = EqualizerProcessor()
        val declined = processor.configure(
            AudioProcessor.AudioFormat(RATE, 2, C.ENCODING_PCM_FLOAT),
        )
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, declined)
    }
}
