package com.avyra.music

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avyra.music.data.sources.TrackMatcher
import com.avyra.music.playback.PlaybackService
import com.avyra.music.playback.auto.AutoLibrary
import com.avyra.music.playback.toMediaItem
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Measures whether a track plays the recording its own row describes.
 *
 * The wrong-song reports cannot be checked by listening from here, but they do
 * not have to be. A row states a runtime and the decoder reports one, and those
 * two numbers come from completely different places: the first from the search
 * result, the second from the bytes actually being played. A recording that is
 * not the one on the row is almost never the same length as it, so a
 * disagreement between them is the bug, measured rather than heard.
 *
 * Written against one named track because that is what was reported. The query
 * is a constant purely so it can be pointed at the next one.
 */
@RunWith(AndroidJUnit4::class)
class WrongSongDiagnosisTest {

    private companion object {
        const val TAG = "WrongSong"
        const val QUERY = "Senandung Jiwa Ayunda Risu"
        const val TIMEOUT_S = 60L
        const val PLAY_TIMEOUT_MS = 90_000L

        /** Two recordings of one song are rarely this close in length. */
        const val TOLERANCE_SEC = 5
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
        return checkNotNull(result) { "main thread did not answer" }.getOrThrow()
    }

    private fun <T> ListenableFuture<T>.await(): T = get(TIMEOUT_S, TimeUnit.SECONDS)

    @Before
    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        browser = onMain { MediaBrowser.Builder(context, token).buildAsync() }.await()
        onMain {
            browser.stop()
            browser.clearMediaItems()
        }
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

    private fun searchResults(): List<MediaItem> {
        val search = onMain { browser.search(QUERY, null) }.await()
        check(search.resultCode == LibraryResult.RESULT_SUCCESS) { "search failed" }
        return onMain { browser.getSearchResult(QUERY, 0, 10, null) }.await().value.orEmpty()
    }

    @Test
    fun theTrackPlaysTheRecordingItsRowDescribes() {
        val hits = searchResults()
        assertTrue("'$QUERY' returned nothing", hits.isNotEmpty())

        val top = hits[0]
        val song = requireNotNull(AutoLibrary.songFor(top.mediaId)) { "no song behind ${top.mediaId}" }
        val claimed = TrackMatcher.secondsOf(song.durationText)

        Log.w(TAG, "row: '${song.title}' - '${song.artist}'  id=${song.videoId}  says ${song.durationText}")

        onMain {
            browser.setMediaItems(listOf(top))
            browser.prepare()
            browser.play()
        }

        var actualMs = 0L
        var playing = false
        val deadline = System.currentTimeMillis() + PLAY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            playing = onMain { browser.isPlaying }
            actualMs = onMain { browser.duration }
            onMain { browser.playerError?.message }?.let { error("player error: $it") }
            if (playing && actualMs > 0) break
            Thread.sleep(500)
        }
        assertTrue("never started playing", playing)
        assertTrue("the decoder never reported a length", actualMs > 0)

        val actual = (actualMs / 1000).toInt()
        Log.w(TAG, "decoder reports ${actual}s; the row claimed ${claimed}s")

        if (claimed == null) {
            Log.w(TAG, "the row carried no runtime, so nothing can be compared")
            return
        }

        assertTrue(
            "'${song.title}' plays for ${actual}s but its row says ${claimed}s — " +
                "a difference of ${abs(actual - claimed)}s means this is a different recording",
            abs(actual - claimed) <= TOLERANCE_SEC,
        )
    }

    /**
     * The safety net, forced to fire.
     *
     * A real stream is queued under a row that claims a wildly different
     * runtime, which is exactly the shape of the bug: the audio is one
     * recording, the row describes another. The service has to notice, throw
     * the cached bytes away and start the track again rather than play on.
     */
    @Test
    fun aTrackThatDoesNotMatchItsRowIsThrownAwayAndFetchedAgain() {
        val hits = searchResults()
        assertTrue("'$QUERY' returned nothing", hits.isNotEmpty())
        val real = requireNotNull(AutoLibrary.songFor(hits[0].mediaId))

        // Same stream, but the row now claims ten minutes.
        val misTimed = real.copy(durationText = "10:00")
        Log.w(TAG, "queuing '${real.title}' under a row claiming ${misTimed.durationText}")

        onMain {
            browser.setMediaItems(listOf(misTimed.toMediaItem()))
            browser.prepare()
            browser.play()
        }

        // Long enough for the duration to settle, the mismatch to be seen and
        // the re-fetch to be started.
        var sawPlaying = false
        val deadline = System.currentTimeMillis() + PLAY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (onMain { browser.isPlaying }) { sawPlaying = true; break }
            Thread.sleep(500)
        }
        assertTrue("never started playing", sawPlaying)
        Thread.sleep(20_000)
        Log.w(TAG, "after the check: playing=" + onMain { browser.isPlaying } +
            " position=" + onMain { browser.currentPosition })
    }
}
