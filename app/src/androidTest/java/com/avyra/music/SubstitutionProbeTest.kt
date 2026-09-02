package com.avyra.music

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avyra.music.data.sources.SourceRegistry
import com.avyra.music.data.sources.SourceResolver
import com.avyra.music.data.sources.TrackMatcher
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asks what the sources ranked above YouTube would actually serve for a track,
 * instead of what they are assumed to serve.
 *
 * The wrong-song reports all describe a row that is completely correct — title,
 * artist, artwork, runtime, even the synced lyrics — playing somebody else's
 * audio. That is not what a broken lookup looks like; it is what a *successful*
 * substitution looks like when the thing it matched is not the same recording.
 *
 * A substitution has to agree on title, artist and runtime, and a cover or a
 * re-recording agrees on all three. This prints what comes back so the answer
 * stops being a guess.
 */
@RunWith(AndroidJUnit4::class)
class SubstitutionProbeTest {

    private companion object {
        const val TAG = "WrongSong"
    }

    @Test
    fun whatWouldBeServedInsteadOfYouTube() {
        runBlocking {
        Log.w(TAG, "sources in order: " + SourceRegistry.active().joinToString { it.displayName })
        Log.w(TAG, "anything ranked above YouTube? " + SourceResolver.canSubstituteForYouTube())

        val target = TrackMatcher.Target(
            title = "Senandung Jiwa",
            artist = "Ayunda Risu",
            durationSec = 219,
        )

        val stream = SourceResolver.substituteForYouTube(target)
        if (stream == null) {
            Log.w(TAG, "nothing was offered; this track would play from YouTube")
        } else {
            Log.w(
                TAG,
                "SUBSTITUTED: '${target.title}' would play from a source instead of YouTube, " +
                    "format=${stream.format.summary} url=${stream.url.take(120)}",
            )
        }
        }
    }
}
