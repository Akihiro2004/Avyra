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
import com.avyra.music.data.innertube.Innertube
import com.avyra.music.playback.PlaybackService
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
 * The two tabs that need an account, on a device that does not have one.
 *
 * Every other test in this suite has run signed out, which means Library and
 * Quick picks were never even offered and so were never opened. That leaves the
 * two most failure-prone branches in the tree untested: the gate that decides
 * whether to show them at all, and what they do when the account behind them
 * cannot answer.
 *
 * The second is not hypothetical. A YouTube cookie expires, and when it does
 * every request behind these tabs starts failing while the tabs stay on screen.
 * A driver must get an empty list, not an error and not a crash.
 *
 * The cookie set here is in memory only. It is never written to [AuthStore],
 * and the tests put it back to whatever it was.
 */
@RunWith(AndroidJUnit4::class)
class AutoSignedInTabsTest {

    private companion object {
        const val TIMEOUT_S = 60L

        /** Well-formed enough to pass the signed-in check, useless to YouTube. */
        const val FAKE_COOKIE = "SAPISID=not-a-real-session; __Secure-3PAPISID=not-a-real-session"
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var browser: MediaBrowser
    private var realCookie: String? = null

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
        realCookie = Innertube.cookie
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        browser = onMain { MediaBrowser.Builder(context, token).buildAsync() }.await()
    }

    @After
    fun restore() {
        Innertube.cookie = realCookie
        if (::browser.isInitialized) onMain { browser.release() }
    }

    private fun tabs(): List<MediaItem> {
        val result = onMain { browser.getChildren("avyra:root", 0, 100, null) }.await()
        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
        return result.value.orEmpty()
    }

    private fun childrenOf(id: String): LibraryResult<*> =
        onMain { browser.getChildren(id, 0, 100, null) }.await()

    @Test
    fun signedOutTheAccountTabsAreNotOffered() {
        Innertube.cookie = null
        val ids = tabs().map { it.mediaId }
        assertFalse("Library is offered with no account", ids.contains("avyra:library"))
        assertFalse("Quick picks is offered with no account", ids.contains("avyra:quickpicks"))
        assertTrue("Downloads went missing", ids.contains("avyra:downloads"))
    }

    @Test
    fun signedInTheAccountTabsAppear() {
        Innertube.cookie = FAKE_COOKIE
        val ids = tabs().map { it.mediaId }
        assertTrue("Library is missing while signed in", ids.contains("avyra:library"))
        assertTrue("Quick picks is missing while signed in", ids.contains("avyra:quickpicks"))
    }

    /**
     * The expired-cookie case. Both tabs must answer with an empty list rather
     * than an error: an error is what a car draws as the app having failed, and
     * it would be reached by one tap on a phone whose session simply timed out.
     */
    @Test
    fun anAccountThatCannotAnswerYieldsEmptyTabsRatherThanErrors() {
        Innertube.cookie = FAKE_COOKIE
        listOf("avyra:library", "avyra:quickpicks", "avyra:library:liked").forEach { id ->
            val result = childrenOf(id)
            assertEquals(
                "'$id' failed with code ${result.resultCode} instead of coming back empty",
                LibraryResult.RESULT_SUCCESS,
                result.resultCode,
            )
        }
    }
}
