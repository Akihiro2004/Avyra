package com.avyra.music

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avyra.music.data.model.Song
import com.avyra.music.playback.PlaybackService
import com.avyra.music.playback.toMediaItem
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * The Android Auto browse tree, exercised through the same protocol a car uses.
 *
 * This connects a real [MediaBrowser] to the real [PlaybackService] on a real
 * device, which is the only way to find out whether any of this works. Nothing
 * in a browse tree fails loudly: a service that is not reachable, a root that
 * is not browsable, a tab whose children throw — all of them show up in a car
 * as an app that is simply not there, or is there and empty, with no error
 * anywhere on the phone.
 *
 * It deliberately asserts the shape of the tree rather than its contents. What
 * is in Downloads or Library depends on the device and the account; that the
 * root exists, that every tab is browsable, and that every tab answers when it
 * is opened, does not.
 */
@RunWith(AndroidJUnit4::class)
class AutoBrowseTest {

    private companion object {
        /** Generous: a browse can go to the network on a cold emulator. */
        const val TIMEOUT_S = 30L
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var browser: MediaBrowser

    /** Media3 requires a browser to be built and called on the main thread. */
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
            while (result == null && System.currentTimeMillis() < deadline) {
                lock.wait(500)
            }
        }
        return checkNotNull(result) { "main thread did not answer in ${TIMEOUT_S}s" }.getOrThrow()
    }

    private fun <T> ListenableFuture<T>.await(): T = get(TIMEOUT_S, TimeUnit.SECONDS)

    @Before
    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        // The connection itself is the first assertion: it is what fails when
        // the service is not exported, does not answer the browser interface,
        // or throws on the way up.
        browser = onMain { MediaBrowser.Builder(context, token).buildAsync() }.await()
    }

    @After
    fun disconnect() {
        if (::browser.isInitialized) onMain { browser.release() }
    }

    private fun root(): MediaItem {
        val result = onMain { browser.getLibraryRoot(null) }.await()
        assertEquals(
            "the browse root was refused",
            LibraryResult.RESULT_SUCCESS,
            result.resultCode,
        )
        return checkNotNull(result.value) { "the browse root came back empty" }
    }

    private fun childrenOf(parentId: String): List<MediaItem> {
        val result = onMain { browser.getChildren(parentId, 0, 100, null) }.await()
        assertEquals(
            "opening '$parentId' failed with code ${result.resultCode}",
            LibraryResult.RESULT_SUCCESS,
            result.resultCode,
        )
        return result.value.orEmpty()
    }

    // ---- The tree ----------------------------------------------------------

    /**
     * A car finds this app at all only if the service answers the browser
     * interface and hands back a browsable root.
     */
    @Test
    fun theServiceOffersABrowsableRoot() {
        val root = root()
        assertEquals("avyra:root", root.mediaId)
        assertTrue(
            "the root is not browsable, so a car has nothing to open",
            root.mediaMetadata.isBrowsable == true,
        )
    }

    /**
     * The root's children are Auto's tabs. Every one has to be browsable and
     * named — an unnamed tab renders as a blank strip at the top of the screen.
     */
    @Test
    fun everyTabIsBrowsableAndNamed() {
        val tabs = childrenOf(root().mediaId)
        assertTrue("no tabs at all", tabs.isNotEmpty())
        assertTrue("more tabs than Auto will show", tabs.size <= 4)
        tabs.forEach { tab ->
            assertTrue(
                "tab '${tab.mediaId}' is not browsable",
                tab.mediaMetadata.isBrowsable == true,
            )
            assertFalse(
                "tab '${tab.mediaId}' has no title",
                tab.mediaMetadata.title.isNullOrBlank(),
            )
        }
    }

    /** Downloads is the one tab that must be there whatever the device holds. */
    @Test
    fun downloadsIsAlwaysOffered() {
        val tabs = childrenOf(root().mediaId)
        assertTrue(
            "Downloads is missing; it is the only tab that works without signal",
            tabs.any { it.mediaId == "avyra:downloads" },
        )
    }

    /**
     * Every tab has to answer when it is opened, whether or not it has
     * anything in it. An error here is what a car draws as the app having
     * failed, and it is reached by a single tap.
     */
    @Test
    fun everyTabOpensWithoutError() {
        childrenOf(root().mediaId).forEach { tab ->
            // childrenOf asserts the result code; an empty list is a fine
            // answer for an account with no playlists or a phone with no
            // downloads.
            childrenOf(tab.mediaId)
        }
    }

    /**
     * Nothing in the tree may be both unopenable and unplayable. Auto draws
     * such a row and then does nothing when it is pressed, which reads as the
     * screen being frozen.
     */
    @Test
    fun everyRowIsEitherBrowsableOrPlayable() {
        childrenOf(root().mediaId).forEach { tab ->
            childrenOf(tab.mediaId).forEach { row ->
                val browsable = row.mediaMetadata.isBrowsable == true
                val playable = row.mediaMetadata.isPlayable == true
                assertTrue(
                    "'${row.mediaId}' under '${tab.mediaId}' can be neither opened nor played",
                    browsable || playable,
                )
            }
        }
    }

    /**
     * The regression the Auto work could most easily have caused, and the one
     * that would have been noticed last.
     *
     * Rebuilding queue items from their media ids means this app's *own*
     * controller now goes through the same hook a car does. It has to be passed
     * through untouched, because the phone's UI sends items that already carry
     * the URI [Song.toMediaItem] built — and if that pass-through is wrong, the
     * items are dropped and every queue set from the phone arrives empty, which
     * is not a car bug and would not have shown up in any of the tests above.
     */
    @Test
    fun aQueueSetFromTheAppItselfIsNotDropped() {
        val song = Song(
            videoId = "dQw4w9WgXcQ",
            title = "A track the phone queued",
            artist = "Not a car",
            thumbnailUrl = null,
            durationText = "3:32",
        )
        onMain { browser.setMediaItems(listOf(song.toMediaItem())) }

        // A settle, not a poll, and the distinction matters. A Media3 controller
        // updates its own copy of the queue the instant setMediaItems is called
        // and only later reconciles with what the session actually did — so
        // reading the count straight back reports the request rather than the
        // outcome, and passes even when the session dropped every item. Waiting
        // for the session's own timeline to come back is the only read that
        // can fail.
        Thread.sleep(2_000)
        val count = onMain { browser.mediaItemCount }

        assertEquals(
            "the app's own queue item was dropped on the way through onAddMediaItems",
            1,
            count,
        )
        onMain { browser.clearMediaItems() }
    }

    /**
     * Search has to come back with a result rather than an error, which is what
     * a car turns a voice request into.
     */
    @Test
    fun searchAnswers() {
        val result = onMain { browser.search("hello", null) }.await()
        assertEquals(
            "search failed with code ${result.resultCode}",
            LibraryResult.RESULT_SUCCESS,
            result.resultCode,
        )
    }
}
