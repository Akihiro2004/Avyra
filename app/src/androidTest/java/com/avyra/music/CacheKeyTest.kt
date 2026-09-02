package com.avyra.music

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avyra.music.data.sources.SourceResolver
import com.avyra.music.playback.AudioCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Which cache entry a streamed track reads and writes.
 *
 * The cache sits outside the resolving data source, so it has to choose an
 * entry before anything has been resolved — it predicts what will be served
 * rather than being told. That is workable only while the prediction matches
 * what the resolver actually goes on to do, and when it does not, two
 * different recordings end up sharing one entry on disk. Nothing about that
 * fails loudly: the track plays, at the right length, with the right title,
 * and the audio is somebody else's until the entry is written over again.
 *
 * The prediction that broke was substitution. It was assumed for any track
 * whenever the *settings* ranked a source above YouTube, while the resolver
 * additionally refuses to substitute a row that carries no runtime — so every
 * untimed row claimed the substituted entry and then played YouTube into it.
 */
@RunWith(AndroidJUnit4::class)
class CacheKeyTest {

    private companion object {
        const val ID = "dQw4w9WgXcQ"
    }

    /** What the player queues for a row that knew its runtime. */
    private fun timed() = Uri.parse("avyra://watch?v=$ID&n=Song&a=Artist&d=200")

    /** And for one that did not — an Android Auto shelf card, for instance. */
    private fun untimed() = Uri.parse("avyra://watch?v=$ID&n=Song&a=Artist")

    private fun keyFor(uri: Uri) = AudioCache.cacheKeyForTest(ID, uri)

    /**
     * The bug, stated directly. A row with no runtime is never substituted, so
     * it will be served YouTube's own bytes — and must therefore read and write
     * YouTube's own entry, not the one substituted copies live in.
     */
    @Test
    fun anUntimedRowKeysToYouTubesOwnEntry() {
        assertEquals(
            "an untimed row claimed the substituted entry; it will be served " +
                "YouTube and write into a copy of a different recording",
            ID,
            keyFor(untimed()),
        )
    }

    /**
     * And the two must not collide, which is the whole reason the `#alt` entry
     * exists in the first place.
     */
    @Test
    fun aTimedAndAnUntimedRowNeverShareAnEntry() {
        // Only meaningful where a source actually outranks YouTube; where none
        // does, nothing is ever substituted and one entry is correct for both.
        if (!SourceResolver.canSubstituteForYouTube()) return

        assertEquals("$ID#alt", keyFor(timed()))
        assertNotEquals(
            "a substituted copy and a YouTube copy share one cache entry",
            keyFor(timed()),
            keyFor(untimed()),
        )
    }
}
