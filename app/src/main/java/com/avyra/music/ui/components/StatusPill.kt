package com.avyra.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avyra.music.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * A small frosted capsule that drops in under the top bar, says one thing, and
 * leaves.
 *
 * The shape of feedback that belongs to an action the user just took and does
 * not need answering: a check that came back, a thing that got copied. It is
 * deliberately not a dialog, a snackbar or a banner — none of those are wrong
 * exactly, but all three are built to be *dealt with*. A dialog takes the
 * screen and demands a tap. A snackbar squats at the bottom over the tab bar
 * with a button in it. This has no dismiss control at all, because giving it
 * one would imply it needed dismissing.
 *
 * ### The motion
 *
 * It enters on a spring and leaves on a curve, and that asymmetry is the whole
 * trick. A spring reads as something arriving under its own weight — the small
 * overshoot as it settles is what makes it feel like an object rather than a
 * rectangle being faded up. But a spring on the way *out* reads as reluctance,
 * and anything the user has finished with should go quickly and without
 * comment. So: [ENTER_DAMPING] with a touch of bounce coming in, a flat fast
 * tween going out, and roughly a two-to-one ratio between them.
 *
 * Scale rides along with the slide at both ends, from [ENTER_SCALE], so it
 * grows into place rather than sliding as a rigid block. It is a small effect —
 * eight percent — and doing it at all is most of the difference between "an
 * element animated in" and "something appeared".
 *
 * ### Why it is frosted
 *
 * It floats over scrolling content, and a solid fill there reads as part of the
 * page rather than above it. The blur is what puts it on its own plane. Routed
 * through the app's own Reduce dynamic blur setting, which falls back to a
 * solid surface — the pill still has to be legible for anyone who turned blur
 * off.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun StatusPill(
    visible: Boolean,
    text: String,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** Replaces [icon] while something is still in flight. */
    busy: Boolean = false,
    /** Makes the whole capsule a target — for a result worth acting on. */
    onClick: (() -> Unit)? = null,
) {
    val reduceBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        // Twice its own height above, so it is already travelling by the time
        // it clears the bar rather than unfolding from under it.
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = ENTER_DAMPING,
                stiffness = ENTER_STIFFNESS,
            ),
            initialOffsetY = { -it * 2 },
        ) + fadeIn(animationSpec = tween(FADE_IN_MS)) +
            scaleIn(
                animationSpec = spring(
                    dampingRatio = ENTER_DAMPING,
                    stiffness = ENTER_STIFFNESS,
                ),
                initialScale = ENTER_SCALE,
            ),
        exit = slideOutVertically(
            animationSpec = tween(EXIT_MS),
            targetOffsetY = { -it * 2 },
        ) + fadeOut(animationSpec = tween(EXIT_MS)) +
            scaleOut(animationSpec = tween(EXIT_MS), targetScale = ENTER_SCALE),
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .then(
                    if (reduceBlur) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else {
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.regular(MaterialTheme.colorScheme.surface),
                        )
                    },
                )
                // A hairline, so the capsule keeps an edge against artwork as
                // well as against a flat page.
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            when {
                busy -> CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Just past [Spring.DampingRatioNoBouncy], the narrow band where a spring
 * settles with one small overshoot rather than either wobbling or arriving
 * dead. Lower and the pill bounces like a toy; higher and the spring stops
 * being worth having over a plain tween.
 */
private const val ENTER_DAMPING = 0.72f

/** Brisk. This is feedback, and feedback that ambles in has already failed. */
private const val ENTER_STIFFNESS = Spring.StiffnessMedium

/** Grows the last 8% into place — enough to read as arriving, not as zooming. */
private const val ENTER_SCALE = 0.92f

/** Shorter than the slide, so it is legible before it has finished settling. */
private const val FADE_IN_MS = 140

/** Leaving is not an event. Flat, quick, about half the entry. */
private const val EXIT_MS = 190
