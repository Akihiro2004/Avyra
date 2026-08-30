package com.avyra.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avyra.music.data.settings.AppSettings
import com.avyra.music.playback.EqualizerProcessor
import com.avyra.music.ui.components.PAGE_GUTTER
import kotlin.math.abs
import kotlin.math.roundToInt
import com.avyra.music.ui.components.avyraSwitchColors

/**
 * Avyra's own equalizer.
 *
 * Ten bands rather than a link to whatever the manufacturer shipped, which is
 * what this replaced — see [EqualizerProcessor] for why that link was worth
 * losing.
 *
 * The layout is the one every graphic EQ has used since the hi-fi separates it
 * borrows from, and deliberately so: a row of vertical faders, low on the left,
 * with a line through the middle at unity. It is worth being unoriginal here.
 * The shape *is* the information — a glance at the faders tells you the curve
 * in a way no list of numbers does — and anyone who has touched an EQ before
 * already knows how to read it.
 */
@Composable
fun EqualizerScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val enabled by AppSettings.equalizerEnabled.collectAsStateWithLifecycle()
    val bands by AppSettings.equalizerBands.collectAsStateWithLifecycle()
    val preamp by AppSettings.equalizerPreamp.collectAsStateWithLifecycle()
    val preset by AppSettings.equalizerPreset.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "title") {
            Text(
                text = "Equalizer",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
            )
        }

        item(key = "switch") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Apply equalizer",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        // Says where it runs, because that is the whole reason
                        // this exists rather than the system panel.
                        text = "Runs inside Avyra, on every device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = AppSettings::setEqualizerEnabled,
                    colors = avyraSwitchColors(),
                )
            }
        }

        item(key = "presets") {
            PresetRow(
                selected = preset,
                onPick = { name, curve ->
                    AppSettings.setEqualizerPreset(name)
                    AppSettings.setEqualizerBands(curve)
                    // A preset that boosts needs the headroom to survive it —
                    // see the preamp note below.
                    AppSettings.setEqualizerPreamp(suggestedPreamp(curve))
                },
                modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
            )
        }

        item(key = "bands") {
            BandRow(
                gains = bands,
                enabled = enabled,
                onChange = { band, db ->
                    val next = bands.copyOf()
                    next[band] = db
                    AppSettings.setEqualizerBands(next)
                    // Hand-edited, so it is nobody's preset any more.
                    if (preset.isNotEmpty()) AppSettings.setEqualizerPreset("")
                },
                modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
            )
        }

        item(key = "preamp") {
            Column(
                modifier = Modifier
                    .padding(horizontal = PAGE_GUTTER)
                    .alpha(if (enabled) 1f else DISABLED_ALPHA),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Preamp",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatDb(preamp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = preamp,
                    onValueChange = { AppSettings.setEqualizerPreamp(it) },
                    valueRange = -EqualizerProcessor.MAX_PREAMP_DB..EqualizerProcessor.MAX_PREAMP_DB,
                    enabled = enabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = "Lowers the whole signal before the bands, so boosting " +
                        "cannot run out of headroom and distort.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "reset") {
            Text(
                text = "Reset to flat",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            AppSettings.setEqualizerBands(FloatArray(EqualizerProcessor.BANDS))
                            AppSettings.setEqualizerPreamp(0f)
                            AppSettings.setEqualizerPreset(PRESET_FLAT)
                        }
                    },
            )
        }
    }
}

@Composable
private fun PresetRow(
    selected: String,
    onPick: (String, FloatArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PRESETS.forEach { (name, curve) ->
            val active = name == selected
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .pointerInput(name) { detectTapGestures { onPick(name, curve.copyOf()) } }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * The ten faders, and the unity line they hang from.
 *
 * Laid out as one row of equal-weight columns rather than a scrolling strip:
 * the whole curve has to be visible at once or the shape stops being readable,
 * which is the only reason to draw it this way instead of listing numbers.
 */
@Composable
private fun BandRow(
    gains: FloatArray,
    enabled: Boolean,
    onChange: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER - 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (band in 0 until EqualizerProcessor.BANDS) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = shortDb(gains.getOrElse(band) { 0f }),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                BandFader(
                    db = gains.getOrElse(band) { 0f },
                    enabled = enabled,
                    onChange = { onChange(band, it) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = EqualizerProcessor.LABELS[band],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * One fader, filled from the middle rather than the bottom.
 *
 * That is the detail that makes a row of these readable. A gain control runs
 * from -12 through 0 to +12, so the meaningful reference is the centre, not the
 * floor: filling upward from the bottom would draw a half-full bar for a band
 * doing nothing at all, and ten of those read as a wall rather than a curve.
 * Filled from unity, a flat EQ is ten thin lines and any departure from it is
 * the only ink on the control.
 *
 * Drag rather than a rotated [Slider]: rotating one puts its touch target on
 * the wrong axis and its semantics report a horizontal control, which is worse
 * for anyone using a screen reader than a purpose-built one.
 */
@Composable
private fun BandFader(
    db: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val trackHeight = FADER_HEIGHT
    var dragDb by remember(db) { mutableFloatStateOf(db) }

    val max = EqualizerProcessor.MAX_GAIN_DB
    val fraction = (dragDb / max).coerceIn(-1f, 1f)

    Box(
        modifier = Modifier
            .width(FADER_WIDTH)
            .height(trackHeight)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = { onChange(dragDb) },
                ) { change, dragAmount ->
                    change.consume()
                    val perPixel = (max * 2f) / with(density) { trackHeight.toPx() }
                    // Up is louder: screen coordinates grow downward, so the
                    // drag has to be inverted or the fader fights the hand.
                    dragDb = (dragDb - dragAmount * perPixel).coerceIn(-max, max)
                    onChange(dragDb)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The rail, full height and barely there.
        Box(
            Modifier
                .width(TRACK_WIDTH)
                .height(trackHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        // The unity line, which is what everything is read against.
        Box(
            Modifier
                .width(FADER_WIDTH)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
        // The fill, growing out of the centre in whichever direction.
        Box(
            Modifier
                .width(TRACK_WIDTH)
                .height(trackHeight / 2 * abs(fraction))
                .offsetFromCentre(fraction, trackHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        // The thumb.
        Box(
            Modifier
                .width(FADER_WIDTH)
                .height(THUMB_HEIGHT)
                .thumbOffset(fraction, trackHeight)
                .clip(CircleShape)
                .background(
                    if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
        )
    }
}

private fun Modifier.offsetFromCentre(fraction: Float, trackHeight: androidx.compose.ui.unit.Dp) =
    this.then(
        Modifier.padding(
            bottom = if (fraction > 0f) trackHeight / 2 * fraction else 0.dp,
            top = if (fraction < 0f) trackHeight / 2 * -fraction else 0.dp,
        ),
    )

private fun Modifier.thumbOffset(fraction: Float, trackHeight: androidx.compose.ui.unit.Dp) =
    this.then(
        Modifier.padding(
            bottom = if (fraction > 0f) trackHeight * fraction else 0.dp,
            top = if (fraction < 0f) trackHeight * -fraction else 0.dp,
        ),
    )

/** "+4.5", "0", "-3" — short enough to sit above a fader without wrapping. */
private fun shortDb(db: Float): String = when {
    abs(db) < 0.05f -> "0"
    else -> {
        val rounded = (db * 10).roundToInt() / 10f
        val text = if (rounded == rounded.toInt().toFloat()) {
            rounded.toInt().toString()
        } else {
            "%.1f".format(rounded)
        }
        if (rounded > 0) "+$text" else text
    }
}

private fun formatDb(db: Float): String = "${shortDb(db)} dB"

/**
 * Headroom a curve needs to survive itself.
 *
 * The largest boost in the curve, taken back off the whole signal. That is the
 * conservative answer rather than the clever one — bands overlap, so two
 * adjacent +6s sum to more than +6 — but it covers the ordinary case, and the
 * soft limiter in [EqualizerProcessor] is what catches the rest. Curves that
 * only cut need no headroom at all.
 */
private fun suggestedPreamp(curve: FloatArray): Float {
    val peak = curve.maxOrNull() ?: 0f
    return if (peak <= 0f) 0f else -peak
}

private const val PRESET_FLAT = "Flat"
private const val DISABLED_ALPHA = 0.4f

private val FADER_HEIGHT = 168.dp
private val FADER_WIDTH = 28.dp
private val TRACK_WIDTH = 4.dp
private val THUMB_HEIGHT = 10.dp

/**
 * The named curves, in the order they are offered.
 *
 * Deliberately gentle — nothing here exceeds ±6 dB. Presets in most players are
 * drawn to be obvious in a shop demo rather than to be listened to, and a
 * +12 dB bass shelf is the reason people conclude equalizers ruin music. These
 * are shaped to still sound like the record.
 */
private val PRESETS: List<Pair<String, FloatArray>> = listOf(
    PRESET_FLAT to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Bass" to floatArrayOf(5f, 4.5f, 3.5f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Vocal" to floatArrayOf(-2f, -1.5f, 0f, 1f, 3f, 4f, 3.5f, 2f, 0f, -1f),
    "Treble" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 3.5f, 4.5f, 5f),
    "Acoustic" to floatArrayOf(3f, 2.5f, 1.5f, 0f, 1f, 1.5f, 2f, 2.5f, 2f, 1f),
    "Electronic" to floatArrayOf(4f, 3.5f, 1f, 0f, -1.5f, 1f, 0.5f, 2f, 3.5f, 4f),
    "Rock" to floatArrayOf(4f, 3f, 1.5f, -0.5f, -1f, 0.5f, 2f, 3f, 3.5f, 3.5f),
    "Jazz" to floatArrayOf(3f, 2f, 1f, 1.5f, -1f, -1f, 0f, 1.5f, 2.5f, 3f),
    "Podcast" to floatArrayOf(-4f, -3f, -1f, 2f, 4f, 4f, 3f, 1.5f, 0f, -1f),
    "Late night" to floatArrayOf(-3f, -2f, 0f, 2f, 3f, 3f, 2f, 1f, 0f, -1f),
)
