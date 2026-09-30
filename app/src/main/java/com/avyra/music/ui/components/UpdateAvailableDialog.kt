package com.avyra.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.ListStyle
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.avyra.music.R
import com.avyra.music.data.AppUpdateChecker
import com.avyra.music.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.roundToInt

/** UIAlertController's own metrics: fixed narrow width, 14pt corner, 44pt rows. */
internal val ALERT_WIDTH = 270.dp
internal val ALERT_CORNER = 14.dp
internal val ACTION_HEIGHT = 44.dp

/**
 * The dim behind the alert. Flat on purpose — the glass is the card, and
 * blurring the wallpaper *behind* it too leaves nothing for the card to be
 * frosted against, which is what made this read as a grey box before.
 */
internal val SCRIM_COLOR = Color.Black.copy(alpha = 0.28f)

// ---- The update page's own metrics ---------------------------------------
//
// Deliberately none of [ALERT_WIDTH]'s. 270pt is UIAlertController's width and
// it is right for what an alert is: one sentence and two verbs. This carries a
// whole release body — headings, paragraphs, bullet lists — and a changelog set
// in a 270pt column is why the notes read as a wall of broken text. Apple does
// not put release notes in an alert either. Software Update is a page: an icon
// over a title, the notes in a grouped card below it, and the actions docked at
// the bottom where the thumb already is.

private val PAGE_INSET = 20.dp
private val ICON_TILE = 72.dp
private val ICON_TILE_CORNER = 18.dp
private val CARD_CORNER = 16.dp
private val CLOSE_BUTTON = 30.dp

/** iOS system blue, the same one the settings screen's tiles use. */
private val UPDATE_TINT = Color(0xFF0A84FF)

private val PROGRESS_HEIGHT = 6.dp
private val PRIMARY_BUTTON_HEIGHT = 50.dp

/**
 * Once-per-launch nudge that a newer build is on GitHub Releases — the top
 * bar's [Icons.Rounded.SystemUpdate] icon is the quiet, always-there version of
 * this; this is the one-time, hard-to-miss version shown the moment the check
 * comes back.
 *
 * Shaped after iOS Software Update rather than a system alert: an icon tile
 * over a title, the release's own notes in a grouped card beneath it, and the
 * actions docked at the bottom rather than stacked in the middle of the screen.
 * The page scrolls as one piece and the dock stays put, so a long changelog
 * never pushes the buttons out of reach.
 *
 * The update round trip happens here rather than in a browser: Download pulls
 * the release's APK into the app cache (progress fills the bar under the
 * title), then Install hands it to the system installer. Where the release
 * carries no APK at all, the actions fall back to opening the releases page.
 *
 * Back closes it, handled by the caller alongside the flag that shows it.
 *
 * Drawn as an overlay rather than an Android [Dialog][androidx.compose.ui.window.Dialog]
 * so its glass can sample the same [HazeState] the rest of the app's frosted
 * surfaces use, the way [FrostedTopBar] and [MiniPlayer] already do — full
 * bleed, so what is behind it is the app itself, blurred.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun UpdateAvailableDialog(
    version: String,
    notes: String?,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val state by AppUpdateChecker.download.collectAsStateWithLifecycle()

    // The notes pass under the close row as well as under the dock, so the top
    // needs the same hairline the bottom has — but only once there is something
    // above to divide it from, which is how an iOS nav bar earns its rule.
    // Derived so the flip recomposes, not every pixel of the scroll.
    val scrollState = rememberScrollState()
    val scrolled by remember { derivedStateOf { scrollState.value > 0 } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (reduceDynamicBlur) {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                } else {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeMaterials.regular(MaterialTheme.colorScheme.surface),
                    )
                },
            )
            // Swallows taps so nothing behind the page reacts to them.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            // Dismissal lives here rather than only in the bottom actions: at
            // full bleed the way out has to be visible from the top of the
            // page, before any of the notes have been read.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PAGE_INSET, vertical = 12.dp),
            ) {
                CloseButton(onClick = onDismiss)
            }
            if (scrolled) AlertRule()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = PAGE_INSET),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UpdateHeader(version = version, state = state)

                // The release's own notes, rendered as Markdown rather than
                // dumped as raw text — GitHub release bodies lean on headings,
                // bullet lists and bold for the changelog, and those are the
                // whole point of reading this before installing.
                if (!notes.isNullOrBlank()) {
                    Spacer(Modifier.height(28.dp))
                    ReleaseNotes(notes = notes)
                }
                Spacer(Modifier.height(24.dp))
            }

            // The notes scroll under the dock, so without this the card is cut
            // by a hard edge against the page and reads as broken clipping
            // rather than as content continuing below.
            AlertRule()

            UpdateActions(
                state = state,
                onDismiss = onDismiss,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onInstall = onInstall,
                onOpenReleasePage = onOpenReleasePage,
            )
        }
    }
}

/** Icon tile, title, the line saying where the update stands, and its progress. */
@Composable
private fun UpdateHeader(
    version: String,
    state: AppUpdateChecker.DownloadState,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(ICON_TILE)
                .clip(RoundedCornerShape(ICON_TILE_CORNER))
                .background(UPDATE_TINT),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp),
            )
        }

        // The title is "Software Update" and the version lives in the line
        // beneath it, which is both what iOS does and the arrangement that
        // avoids naming the version twice — the existing body strings already
        // carry it, in nine languages, so a version headline would have cost
        // new strings for no information.
        Text(
            text = stringResource(R.string.software_update),
            modifier = Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.W700,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = when (state) {
                is AppUpdateChecker.DownloadState.Downloading ->
                    stringResource(R.string.update_downloading_body, version)
                is AppUpdateChecker.DownloadState.Ready ->
                    stringResource(R.string.update_ready_body, version)
                is AppUpdateChecker.DownloadState.Failed ->
                    stringResource(R.string.update_failed_body, version)
                else ->
                    stringResource(R.string.update_available_body, version)
            },
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                lineHeight = 21.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )

        // The percentage is the only number anywhere in this flow, and a
        // hundred-megabyte download without one is indistinguishable from a
        // stalled one — which is exactly how the last broken version read.
        val downloading = state as? AppUpdateChecker.DownloadState.Downloading
        if (downloading != null) {
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(PROGRESS_HEIGHT)
                    .clip(RoundedCornerShape(PROGRESS_HEIGHT / 2))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            ) {
                if (downloading.fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(downloading.fraction)
                            .height(PROGRESS_HEIGHT)
                            .clip(RoundedCornerShape(PROGRESS_HEIGHT / 2))
                            .background(UPDATE_TINT),
                    )
                }
            }
            Text(
                text = "${(downloading.fraction * 100).roundToInt()}%",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W500,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        if (state is AppUpdateChecker.DownloadState.Failed) {
            Text(
                text = state.message,
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The release body, set to be read.
 *
 * Every size here is stated rather than inherited, which is the actual fix for
 * the notes looking broken. `RichTextStyle.Default` leaves headings at the
 * library's own scale, and a release body's `##` against a 13sp paragraph came
 * out larger than the card's own title — so the changelog read as a stack of
 * shouted fragments. Headings are pinned one step above the body instead, and
 * the body itself is provided through `LocalTextStyle`, which is where the
 * Material 3 renderer takes its baseline from.
 *
 * Left-aligned, unlike the title block above it. Centred prose is legible for
 * one sentence and hostile for twenty.
 *
 * No scroll container of its own: the whole page scrolls as one piece, and a
 * scrollable box inside a scrollable column is how you get a region that eats
 * the gesture and strands the reader.
 */
@Composable
private fun ReleaseNotes(notes: String) {
    val body = remember {
        TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.W400)
    }
    // Levels arrive 1-based from Markdown, but the renderer's own heading block
    // is 0-based; treating 0 and 1 alike means neither convention produces an
    // outsized first heading.
    val richTextStyle = remember {
        RichTextStyle(
            paragraphSpacing = 12.sp,
            headingStyle = { level, style ->
                when (level) {
                    0, 1 -> style.copy(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.W700,
                    )
                    2 -> style.copy(
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.W600,
                    )
                    else -> style.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.W600,
                    )
                }
            },
            listStyle = ListStyle(
                markerIndent = 4.sp,
                contentsIndent = 8.sp,
                itemSpacing = 10.sp,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.whats_new),
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(12.dp))
        // The Material 3 renderer reads both of these: without the colour the
        // notes come out at LocalContentColor's black default regardless of
        // theme, because this page is a plain Box.background(...) rather than a
        // Surface; without the text style the body falls back to bodyLarge.
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            LocalTextStyle provides body,
        ) {
            RichText(style = richTextStyle) {
                Markdown(notes)
            }
        }
    }
}

/**
 * The docked actions.
 *
 * Filled pill for the one thing the page is asking for, plain text underneath
 * for the way out — the App Store's arrangement rather than the alert's two
 * equal rows, because at full bleed there is a clear primary action and
 * pretending otherwise just makes it harder to find.
 */
@Composable
private fun UpdateActions(
    state: AppUpdateChecker.DownloadState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_INSET)
            .padding(top = 14.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is AppUpdateChecker.DownloadState.Downloading -> {
                TextAction(label = stringResource(R.string.cancel), onClick = onCancelDownload)
            }
            is AppUpdateChecker.DownloadState.Ready -> {
                FilledAction(label = stringResource(R.string.install_now), onClick = onInstall)
                TextAction(label = stringResource(R.string.later), onClick = onDismiss)
            }
            is AppUpdateChecker.DownloadState.Failed -> {
                FilledAction(label = stringResource(R.string.try_again), onClick = onDownload)
                TextAction(label = stringResource(R.string.open_releases_page), onClick = onOpenReleasePage)
            }
            else -> {
                FilledAction(label = stringResource(R.string.download_now), onClick = onDownload)
                TextAction(label = stringResource(R.string.remind_me_later), onClick = onDismiss)
            }
        }
    }
}

/** The page's one filled action — iOS's big blue pill. */
@Composable
private fun FilledAction(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PRIMARY_BUTTON_HEIGHT)
            .clip(RoundedCornerShape(14.dp))
            // iOS dims the whole control on press rather than rippling inside it.
            .background(UPDATE_TINT.copy(alpha = if (pressed) 0.75f else 1f))
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.W600,
            ),
            color = Color.White,
        )
    }
}

/** The quiet one underneath it. */
@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ACTION_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.W400,
            ),
            color = UPDATE_TINT.copy(alpha = if (pressed) 0.6f else 1f),
        )
    }
}

/** Top-left dismissal, the way a full-screen iOS sheet closes. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(CLOSE_BUTTON)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (pressed) 0.16f else 0.08f),
            )
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.cancel),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Full-bleed action row. Tinted rather than filled, so the two read as equals
 * in weight and only the font differentiates the default action — the alert's
 * whole point is that neither choice is a trap.
 */
@Composable
internal fun AlertAction(
    label: String,
    emphasised: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ACTION_HEIGHT)
            // iOS washes the whole row instead of drawing a ripple inside it.
            .background(
                if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = if (emphasised) FontWeight.W600 else FontWeight.W400,
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/** Hairline separator — [HorizontalDivider][androidx.compose.material3.HorizontalDivider]'s 1dp reads as a bar at this scale. */
@Composable
internal fun AlertRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    )
}
