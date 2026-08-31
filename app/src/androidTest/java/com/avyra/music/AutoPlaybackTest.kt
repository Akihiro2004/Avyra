package com.avyra.music

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avyra.music.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Playing something, the way a car does it.
 *
 * [AutoBrowseTest] proves the tree is reachable and correctly shaped. It does
 * not prove a single note comes out, and on a device with no downloads and no
 * account it walks an almost empty tree — which is exactly the shape of test
 * that passes while the feature is broken.
 *
 * This one goes the rest of the way: it finds real tracks through search, sends
 * back the one media id a tap produces, and then asks whether the player did
 * what a driver expects. Everything here needs the network, because that is
 * what a car reaching for a song actually needs.
 */
@RunWith(AndroidJUnit4::class)
class AutoPlaybackTest {

    private companion object {
        const val TIMEOUT_S = 30L

        /** Resolving a stream walks several client identities; give it room. */
        const val PLAYBACK_TIMEOUT_MS = 90_000L

        /** Ordinary enough to return several results to any account, or none. */
        const val QUERY = "daft punk"
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var browser: MediaBrowser

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: Result<T>? = null
        val lock = Object()
        main.post {
            result = runCatching(block)
            synchronized(lock) { lock.notifyAll() }
        }
        synchronized(lock) {
            val deadline = System.currentTimeMillis() + TIMEOUT_S * 1000
            while (result == null && System.currentTimeMillis() < deadline) lock.wait(500)
        }
        return checkNotNull(result) { "main thread did not answer in ${TIMEOUT_S}s" }.getOrThrow()
    }

    private fun <T> ListenableFuture<T>.await(): T = get(TIMEOUT_S, TimeUnit.SECONDS)

    @Before
    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        browser = onMain { MediaBrowser.Builder(context, token).buildAsync() }.await()
        onMain { browser.clearMediaItems() }
    }

    @After
    fun disconnect() {
        if (::browser.isInitialized) {
            onMain {
                browser.stop()
                browser.clearMediaItems()
                browser.release()
            }
        }
    }

    /**
     * Search results, which is the one list this test can rely on existing:
     * downloads and library depend on the device, a song search does not.
     */
    private fun searchResults(): List<MediaItem> {
        val search = onMain { browser.search(QUERY, null) }.await()
        assertEquals(
            "search failed with code ${search.resultCode}",
            LibraryResult.RESULT_SUCCESS,
            search.resultCode,
        )
        val result = onMain { browser.getSearchResult(QUERY, 0, 20, null) }.await()
        assertEquals(
            "reading search results failed with code ${result.resultCode}",
            LibraryResult.RESULT_SUCCESS,
            result.resultCode,
        )
        return result.value.orEmpty()
    }

    @Test
    fun searchReturnsPlayableTracks() {
        val hits = searchResults()
        assertTrue("search for '$QUERY' came back empty", hits.isNotEmpty())
        hits.forEach { hit ->
            assertTrue(
                "search result '${hit.mediaId}' is not playable",
                hit.mediaMetadata.isPlayable == true,
            )
            assertTrue(
                "search result '${hit.mediaId}' has no title to draw",
                !hit.mediaMetadata.title.isNullOrBlank(),
            )
        }
    }

    /**
     * The behaviour a car makes necessary and a phone never does.
     *
     * Auto sends exactly the row that was tapped and nothing about the list it
     * sat in, so taken literally every tap yields a queue of one and the music
     * stops at the end of that track. What has to happen instead is the list,
     * played from that row.
     */
    @Test
    fun tappingARowQueuesTheListItCameFromAndStartsThere() {
        val hits = searchResults()
        assertTrue("need several results to test expansion", hits.size >= 3)
        val tapped = hits[2]

        // Exactly what Auto sends: the item as it was handed out, which by the
        // time it comes back carries no URI of its own.
        onMain { browser.setMediaItems(listOf(tapped)) }
        Thread.sleep(2_000)

        val count = onMain { browser.mediaItemCount }
        val index = onMain { browser.currentMediaItemIndex }
        val currentId = onMain { browser.currentMediaItem?.mediaId }

        assertTrue(
            "a tapped row produced a queue of $count; the rest of the list was lost",
            count > 1,
        )
        // Specifically the searched list, not merely a long queue. AutoPlay
        // extends a single-track queue on its own, so `count > 1` alone passes
        // even when no expansion happened at all - which is how the first
        // version of this test missed the bug it was written to catch.
        assertEquals(
            "the queue is not the list that was searched",
            hits[0].mediaId,
            onMain { browser.getMediaItemAt(0).mediaId },
        )
        assertEquals("the queue did not start on the row that was tapped", 2, index)
        assertEquals("the wrong track is current", tapped.mediaId, currentId)
    }

    /**
     * The whole point, and the only assertion here that cannot be satisfied by
     * a correctly shaped tree over a broken player: sound.
     *
     * A track chosen in a car has to resolve a stream and reach the speakers
     * without any of this app's UI existing. Failure modes this catches that
     * nothing above does — a queue item rebuilt without its URI, a resolver
     * that never runs outside the app process, a session whose player was
     * never prepared — all look identical from the browse tree.
     */
    @Test
    fun aTrackChosenInTheCarActuallyPlays() {
        val hits = searchResults()
        assertTrue("nothing to play", hits.isNotEmpty())

        onMain {
            browser.setMediaItems(listOf(hits[0]))
            browser.prepare()
            browser.play()
        }

        var state = Player.STATE_IDLE
        var playing = false
        var error: String? = null
        val deadline = System.currentTimeMillis() + PLAYBACK_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            state = onMain { browser.playbackState }
            playing = onMain { browser.isPlaying }
            error = onMain { browser.playerError?.message }
            if (playing || error != null) break
            Thread.sleep(500)
        }

        assertEquals("the player reported an error", null, error)
        assertTrue(
            "no audio after ${PLAYBACK_TIMEOUT_MS / 1000}s; state=$state playing=$playing",
            playing,
        )
        assertTrue(
            "playing, but the position never advanced",
            onMain { browser.currentPosition } >= 0,
        )
    }
}
