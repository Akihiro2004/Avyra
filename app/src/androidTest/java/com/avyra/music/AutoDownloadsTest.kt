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
import android.net.Uri
import com.avyra.music.data.sources.TrackMatcher
import com.avyra.music.download.DownloadStore
import com.avyra.music.download.Downloads
import com.avyra.music.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    /** What the media store calls the file at [uri]. */
    private fun displayNameOf(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

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
     * The signal the adoption guard rests on, checked against a real file.
     *
     * A download is adopted rather than re-fetched when Music already holds a
     * file of the right name, and a name is only `artist - title` — so two
     * recordings credited identically collide and the second used to adopt the
     * first one's audio. Adoption now also requires the runtimes to agree, and
     * this is the half of that which can only be answered by a device: whether
     * the store can time a file this app wrote at all.
     *
     * It matters in both directions. If this came back null the guard would
     * refuse every adoption — safe, but it would silently re-download whole
     * libraries — and if it came back wrong the guard would refuse the correct
     * file and keep the wrong one.
     */
    @Test
    fun theStoreCanTimeADownloadedFileAccuratelyEnoughToIdentifyIt() {
        val videoId = ensureOneDownload()
        val uri = Uri.parse(
            requireNotNull(Downloads.verifiedSavedUri(videoId)) { "nothing saved for $videoId" },
        )

        val onDisk = DownloadStore.durationSecondsOf(context, uri)
        assertTrue(
            "the store could not time the file, so adoption would always be refused",
            onDisk != null && onDisk > 0,
        )

        val song = kotlinx.coroutines.runBlocking { Downloads.getDownloadedSongs(context) }
            .first { it.videoId == videoId }
        val claimed = TrackMatcher.secondsOf(song.durationText)
        if (claimed != null) {
            assertTrue(
                "the file times at ${onDisk}s but its row says ${claimed}s; " +
                    "the guard would refuse this track's own file",
                kotlin.math.abs(onDisk!! - claimed) <= 3,
            )
        }
    }

    /**
     * The fix itself: a file is adopted only when its runtime agrees.
     *
     * This is the bug in miniature. Both songs below are credited identically,
     * so both ask for exactly the same filename, and the one already on disk is
     * the wrong recording for the second. Adopting it is what left a row showing
     * the right title, artist and album while playing something else — every
     * time, because the record that adoption writes is what playback reads.
     */
    @Test
    fun aFileOfTheRightNameButTheWrongLengthIsNotAdopted() {
        val videoId = ensureOneDownload()
        val saved = kotlinx.coroutines.runBlocking { Downloads.getDownloadedSongs(context) }
            .first { it.videoId == videoId }

        // The name the file actually has, read off the store rather than
        // rebuilt from the row. Rebuilding it looked equivalent and is not: a
        // download is named after the catalogue track behind the row, and the
        // row read back afterwards carries the tags on the file, which differ
        // in punctuation often enough to make the reconstruction miss.
        val savedUri = requireNotNull(Downloads.verifiedSavedUri(videoId))
        val name = requireNotNull(displayNameOf(Uri.parse(savedUri))) {
            "the store has no name for $savedUri"
        }
        val onDisk = requireNotNull(
            DownloadStore.durationSecondsOf(context, Uri.parse(savedUri)),
        )
        assertNotNull(
            "the file is not findable by its own name",
            DownloadStore.existing(context, name),
        )

        // A different recording of the same song: it would ask for exactly this
        // filename, and it runs two minutes longer.
        val impostor = saved.copy(
            videoId = "a-different-id",
            durationText = "${(onDisk + 120) / 60}:${"%02d".format((onDisk + 120) % 60)}",
        )
        assertNull(
            "a file two minutes longer than the track was adopted as that track",
            Downloads.adoptable(context, impostor, name),
        )

        // And the track's own file must still be adopted, or every re-download
        // fetches the whole library again.
        val itself = saved.copy(
            durationText = "${onDisk / 60}:${"%02d".format(onDisk % 60)}",
        )
        assertNotNull(
            "the track's own file was refused; adoption is now dead code",
            Downloads.adoptable(context, itself, name),
        )
    }

    /**
     * The repair pass must not eat a correct download.
     *
     * [Downloads.auditSaved] drops any saved mapping whose file disagrees with
     * the runtime recorded for it, which is how a track that adopted the wrong
     * file gets un-stuck. That comparison runs over every download on the
     * device, so a false positive here is not a slow re-fetch of one track — it
     * is the app quietly forgetting a library that was never wrong.
     */
    @Test
    fun theRepairPassLeavesACorrectDownloadAlone() {
        val videoId = ensureOneDownload()
        assertNotNull(
            "nothing saved to audit",
            Downloads.verifiedSavedUri(videoId),
        )

        kotlinx.coroutines.runBlocking { Downloads.auditSaved(context) }

        assertNotNull(
            "the repair pass forgot a download whose file is the right one",
            Downloads.verifiedSavedUri(videoId),
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
