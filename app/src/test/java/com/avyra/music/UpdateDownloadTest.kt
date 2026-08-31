package com.avyra.music

import com.avyra.music.data.AppUpdateChecker
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

/**
 * The update download, against a server that behaves the way the real one does
 * when a phone drops its connection.
 *
 * Worth a test because the failure it guards is silent in the worst way: a
 * connection that dies part way through a hundred-megabyte APK closes cleanly.
 * The file on disk simply stops early, and the old code handed that stump to
 * the package installer as though it were the update — so what the listener
 * finally saw was an error about a corrupt package, several steps removed from
 * the download that actually failed.
 *
 * Served off a bare [ServerSocket] rather than a mock web server. What is under
 * test is Range resumption and a body that stops early; both are a handful of
 * lines of literal HTTP, and neither is worth a dependency. `HttpServer` would
 * have done too, but `com.sun.net.httpserver` is not on the Android unit test
 * classpath.
 */
class UpdateDownloadTest {

    private companion object {
        /** Small enough to be quick, big enough to need several read loops. */
        const val SIZE = 300_000

        val payload = ByteArray(SIZE) { (it % 251).toByte() }
    }

    private lateinit var socket: ServerSocket
    private lateinit var dir: File

    /** How many bytes of the body the server writes before hanging up. */
    @Volatile
    private var cutAfter = SIZE

    /** Whether the server honours `Range`, or ignores it and resends the lot. */
    @Volatile
    private var honourRange = true

    /** Every `Range` header the server was sent, in order. */
    private val ranges: MutableList<String?> = Collections.synchronizedList(mutableListOf())

    private val url get() = "http://127.0.0.1:${socket.localPort}/app.apk"

    @Before
    fun start() {
        dir = File(System.getProperty("java.io.tmpdir"), "avyra-update-${System.nanoTime()}")
            .apply { mkdirs() }
        socket = ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: return@thread
                thread(isDaemon = true) { runCatching { serve(client) } }
            }
        }
    }

    @After
    fun stop() {
        socket.close()
        dir.deleteRecursively()
    }

    /**
     * One request, one response, connection closed.
     *
     * The truncated case still declares the full length in `Content-Length` and
     * then writes less than it promised, because that is the shape of a dropped
     * connection: the client is told how much is coming and never receives it.
     */
    private fun serve(client: Socket) = client.use {
        val input = client.getInputStream()
        val head = StringBuilder()
        // Headers end at a blank line; the request has no body to follow.
        while (!head.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b == -1) return@use
            head.append(b.toChar())
        }
        val range = head.lines()
            .firstOrNull { it.startsWith("Range:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
        ranges += range

        val from = if (honourRange) {
            range?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
        } else {
            0
        }
        val slice = payload.copyOfRange(from, SIZE)

        val out = client.getOutputStream()
        val status = if (from > 0) "206 Partial Content" else "200 OK"
        val headers = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Length: ${slice.size}\r\n")
            append("Accept-Ranges: bytes\r\n")
            if (from > 0) append("Content-Range: bytes $from-${SIZE - 1}/$SIZE\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray())
        out.write(slice, 0, minOf(cutAfter, slice.size))
        out.flush()
    }

    private fun target() = File(dir, "avyra-1.0.1.apk")

    // ---- The whole file, in one go ------------------------------------------

    @Test
    fun `a download that is not interrupted lands the whole file`() {
        AppUpdateChecker.fetchInto(target(), url)
        assertArrayEquals(payload, target().readBytes())
    }

    // ---- The case the listener actually hit ---------------------------------

    /**
     * A body that stops early has to be reported as a failed download rather
     * than accepted as a short one — and the bytes that did arrive have to be
     * kept, because they are what the next attempt resumes from.
     */
    @Test
    fun `a connection that dies mid-body fails instead of leaving a stump`() {
        cutAfter = SIZE / 3
        val thrown = runCatching { AppUpdateChecker.fetchInto(target(), url) }.exceptionOrNull()
        assertTrue("a truncated download was accepted as complete", thrown != null)
        assertTrue(
            "the partial file was thrown away, so a retry cannot resume it",
            target().length() in 1 until SIZE.toLong(),
        )
    }

    /**
     * And the attempt after it picks up from the byte it stopped on rather than
     * starting the hundred megabytes over — the only reason a download that
     * size finishes at all on a connection that keeps dropping.
     */
    @Test
    fun `the attempt after a drop resumes rather than restarting`() {
        cutAfter = SIZE / 3
        runCatching { AppUpdateChecker.fetchInto(target(), url) }
        val reached = target().length()

        cutAfter = SIZE
        AppUpdateChecker.fetchInto(target(), url)

        assertNull("the first attempt should not have sent a Range header", ranges.first())
        assertEquals("the retry did not resume where it left off", "bytes=$reached-", ranges.last())
        assertArrayEquals("the resumed file does not match the original", payload, target().readBytes())
    }

    /**
     * A server that ignores the range and answers 200 with the whole file must
     * overwrite the partial rather than append to it. Appending would leave a
     * file that is the wrong size and corrupt from the seam onwards — and a
     * corrupt APK is exactly the failure this whole path exists to avoid.
     */
    @Test
    fun `a server that ignores the range restarts the file instead of doubling it`() {
        target().writeBytes(payload.copyOfRange(0, SIZE / 2))
        honourRange = false

        AppUpdateChecker.fetchInto(target(), url)

        assertEquals(SIZE.toLong(), target().length())
        assertArrayEquals(payload, target().readBytes())
    }
}
