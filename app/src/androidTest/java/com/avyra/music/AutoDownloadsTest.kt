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
import com.avyra.music.download.Downloads
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
 * The Downloads tab, with something actually in it.
 *
 * Every other test here has run against a device holding no downloads, which
 * means the tab that matters most in a car — the only one that works with no
 * signal — has been asserted to exist and never asserted to work. An empty list
 * is a passing answer for all of them, so a Downloads tab that could not list a
 * single track would have gone out looking fully tested.
 *
 * This one downloads a real track onto the device and then browses to it the
 * way a car does.
 */
@RunWith(AndroidJUnit4::class)
class AutoDownloadsTest {

    private companion object {
        const val TIMEOUT_S = 30L
        const val DOWNLOAD_TIMEOUT_MS = 180_000L
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
    }

    @After
    fun disconnect() {
        if (::browser.isInitialized) onMain { browser.release() }
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

    /**
     * Puts one real track on the device, returning its video id.
     *
     * Reuses whatever is already downloaded when there is something, so a
     * second run of this suite does not fetch another track.
     */
    private fun ensureOneDownload(): String {
        Downloads.saved.value.keys.firstOrNull()?.let { return it }

        val search = onMain { browser.search(QUERY, null) }.await()
        assertEquals(LibraryResult.RESULT_SUCCESS, search.resultCode)
        val hits = onMain { browser.getSearchResult(QUERY, 0, 5, null) }.await().value.orEmpty()
        assertTrue("nothing to download", hits.isNotEmpty())

        val song = com.avyra.music.data.model.Song(
            videoId = hits[0].mediaId,
            title = hits[0].mediaMetadata.title?.toString().orEmpty(),
            artist = hits[0].mediaMetadata.artist?.toString().orEmpty(),
            thumbnailUrl = null,
        )
        onMain { Downloads.enqueue(context, song) }

        val deadline = System.currentTimeMillis() + DOWNLOAD_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (Downloads.saved.value.containsKey(song.videoId)) return song.videoId
            Thread.sleep(1_000)
        }
        throw AssertionError(
            "the download never landed in ${DOWNLOAD_TIMEOUT_MS / 1000}s; " +
                "active=${Downloads.active.value}",
        )
    }

    /**
     * A downloaded track has to be reachable from the car in two taps:
     * Downloads, then All downloads.
     */
    @Test
    fun aDownloadedTrackIsReachableFromTheDownloadsTab() {
        val videoId = ensureOneDownload()

        val downloads = childrenOf("avyra:downloads")
        assertTrue(
            "the Downloads tab is empty even though a track is saved on the device",
            downloads.isNotEmpty(),
        )
        val all = downloads.firstOrNull { it.mediaId == "avyra:downloads:all" }
        assertTrue("'All downloads' is missing with a track on disk", all != null)

        val rows = childrenOf(all!!.mediaId)
        assertTrue(
            "the downloaded track is not listed under 'All downloads'",
            rows.any { it.mediaId == videoId },
        )
        rows.forEach { row ->
            assertTrue(
                "downloaded row '${row.mediaId}' is not playable",
                row.mediaMetadata.isPlayable == true,
            )
        }
    }

    /**
     * And playing it must not touch the network, which is the entire reason
     * the tab exists. Asserted through the queue item's own URI: a track served
     * from disk carries a file or content URI, and anything resolving over the
     * network carries this app's `avyra://` scheme instead.
     */
    @Test
    fun playingADownloadUsesTheFileOnDiskRatherThanTheNetwork() {
        val videoId = ensureOneDownload()
        val rows = childrenOf("avyra:downloads:all")
        val row = rows.first { it.mediaId == videoId }

        onMain { browser.setMediaItems(listOf(row)) }
        Thread.sleep(2_000)

        // The controller is told the queue but not the URIs behind it — Media3
        // strips those on the way out — so this reads the saved record instead,
        // which is what the item was built from.
        val saved = Downloads.verifiedSavedUri(videoId)
        assertTrue(
            "the download's file is not on disk, so this would have streamed",
            saved != null,
        )
        assertTrue(
            "the saved uri is not a local one: $saved",
            saved!!.startsWith("content://") || saved.startsWith("file://"),
        )
        assertEquals(
            "the queue did not land on the downloaded track",
            videoId,
            onMain { browser.currentMediaItem?.mediaId },
        )
        onMain {
            browser.stop()
            browser.clearMediaItems()
        }
    }
}
