package com.avyra.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.avyra.music.R
import com.avyra.music.ui.components.PAGE_GUTTER
import com.avyra.music.ui.icons.AvyraIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The three screens a first launch opens on.
 *
 * Three, and no more. Onboarding earns its keep only if it is read, and the
 * thing that stops it being read is length — a listener who wanted to play
 * something is being held up, and every page past the third is one they swipe
 * without looking. So: who this is, the one decision worth making up front,
 * and what the app can do. Everything else is discoverable in Settings, which
 * is where it belongs.
 *
 * ### The animation is the explanation
 *
 * Content does not arrive with its page. Each page reveals its rows one at a
 * time, a beat apart, once the pager has settled on it — see [staggered]. That
 * is not decoration: a block of five features that appears at once is read as
 * a wall and skipped, while five that arrive in sequence are read as five
 * things, because the eye is given somewhere to start and a rhythm to follow.
 * The cost is about half a second and the return is the difference between
 * being seen and being looked at.
 *
 * Motion follows the app's own: a spring in, at the damping [StatusPill] uses,
 * so onboarding feels like the thing it is introducing rather than a splash
 * screen bolted to the front of it.
 *
 * Sign-in is a real choice, on its own page, with an equally weighted way past
 * it. Avyra works signed out — search, playback, local files, downloads and the
 * equalizer all do — so a page that made signing in look mandatory would be
 * lying to get a login.
 */
@Composable
fun OnboardingScreen(
    onSignIn: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScopeCompat()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
            ) { page ->
                // Settled, not merely visible: a half-swiped page should not
                // start its reveal, or the sequence plays to nobody and the
                // page is already finished by the time it arrives.
                val settled = pager.currentPage == page && !pager.isScrollInProgress
                when (page) {
                    0 -> WelcomePage(settled)
                    1 -> SignInPage(settled)
                    else -> FeaturesPage(settled)
                }
            }

            PageDots(
                count = PAGES,
                current = pager.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            )

            Column(
                Modifier.padding(
                    start = PAGE_GUTTER + 8.dp,
                    end = PAGE_GUTTER + 8.dp,
                    bottom = 28.dp,
                ),
            ) {
                PrimaryAction(
                    label = if (pager.currentPage == 1) "Sign in" else if (pager.currentPage == PAGES - 1) "Get started" else "Continue",
                    onClick = {
                        when (pager.currentPage) {
                            1 -> onSignIn()
                            PAGES - 1 -> onFinish()
                            else -> scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        }
                    },
                )
                // Only where there is genuinely something to decline. A "skip"
                // on every page would be an invitation to skip the whole thing.
                if (pager.currentPage == 1) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Not now",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { scope.launch { pager.animateScrollToPage(2) } }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

// ---- Pages -----------------------------------------------------------------

@Composable
private fun WelcomePage(settled: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PAGE_GUTTER + 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        staggered(settled, 0) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(88.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        staggered(settled, 1) {
            Text(
                text = "Avyra",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(10.dp))
        staggered(settled, 2) {
            Text(
                text = "Music, in high fidelity.",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(14.dp))
        staggered(settled, 3) {
            Text(
                text = "Millions of songs, the music already on your phone, " +
                    "and lossless copies from sources you configure — in one player.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SignInPage(settled: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PAGE_GUTTER + 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        staggered(settled, 0) {
            Text(
                text = "Bring your library with you",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(10.dp))
        staggered(settled, 1) {
            Text(
                text = "Sign in with the Google account you use for YouTube Music.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(30.dp))
        BENEFITS.forEachIndexed { i, (icon, pair) ->
            staggered(settled, 2 + i) {
                InfoRow(icon = icon, title = pair.first, detail = pair.second)
            }
            Spacer(Modifier.height(18.dp))
        }
        staggered(settled, 2 + BENEFITS.size) {
            Text(
                // Said plainly, because a listener deciding whether to hand over
                // an account deserves to know it is optional.
                text = "Everything else works without an account — search, playback, " +
                    "your own files, downloads and the equalizer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeaturesPage(settled: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PAGE_GUTTER + 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        staggered(settled, 0) {
            Text(
                text = "What Avyra does",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(26.dp))
        FEATURES.forEachIndexed { i, (icon, pair) ->
            staggered(settled, 1 + i) {
                InfoRow(icon = icon, title = pair.first, detail = pair.second)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---- Parts -----------------------------------------------------------------

@Composable
private fun InfoRow(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
    )
}

/**
 * The page indicator, where the active dot stretches rather than merely
 * brightening.
 *
 * Two things are being said at once — which page this is, and how many there
 * are — and a row of same-sized dots says the second loudly and the first
 * faintly. Widening the active one makes position the obvious reading, and it
 * animates, so a swipe is confirmed by the indicator moving with the thumb
 * rather than blinking after it.
 */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == current
            val width by animateDpAsState(
                targetValue = if (active) 22.dp else 7.dp,
                animationSpec = spring(dampingRatio = DAMPING, stiffness = Spring.StiffnessMedium),
                label = "dot",
            )
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(7.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

/**
 * Reveals [content] once its page has settled, [index] places into the queue.
 *
 * The delay is what makes a list read as a list. Each row waits
 * [STAGGER_MS] longer than the one above it, so they arrive in reading order
 * at roughly the speed someone reads them — fast enough not to be a wait, slow
 * enough that the eye lands on each in turn instead of on the block.
 */
@Composable
private fun staggered(settled: Boolean, index: Int, content: @Composable () -> Unit) {
    var shown by remember(settled) { mutableIntStateOf(if (settled) -1 else 0) }
    LaunchedEffect(settled) {
        if (!settled) return@LaunchedEffect
        delay(index * STAGGER_MS)
        shown = 1
    }
    AnimatedVisibility(
        visible = shown == 1,
        enter = fadeIn(animationSpec = tween(FADE_MS)) +
            slideInVertically(
                animationSpec = spring(dampingRatio = DAMPING, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { it / 3 },
            ),
    ) {
        content()
    }
}

/** `rememberCoroutineScope`, named apart so the import list stays obvious. */
@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()

private const val PAGES = 3

/** Matches the status pill: one small overshoot, no wobble. */
private const val DAMPING = 0.72f

/** Between one row appearing and the next. About a reading beat. */
private const val STAGGER_MS = 90L

private const val FADE_MS = 220

private val BENEFITS: List<Pair<ImageVector, Pair<String, String>>> = listOf(
    AvyraIcons.Library to ("Your playlists" to "Everything you have saved, ready to play"),
    AvyraIcons.Heart to ("Liked songs" to "Your ratings travel with you"),
    Icons.Rounded.AutoAwesome to ("Made for you" to "Recommendations from what you actually play"),
)

private val FEATURES: List<Pair<ImageVector, Pair<String, String>>> = listOf(
    Icons.Rounded.GraphicEq to ("Lossless audio" to "FLAC and ALAC from sources you configure"),
    Icons.Rounded.Tune to ("Ten-band equalizer" to "Built in, and works on every device"),
    AvyraIcons.Download to ("Offline downloads" to "Saved with artwork and full tags"),
    AvyraIcons.Lyrics to ("Word-synced lyrics" to "Lit up line by line as they are sung"),
    AvyraIcons.Infinity to ("Automix" to "Beat-matched transitions between tracks"),
)
