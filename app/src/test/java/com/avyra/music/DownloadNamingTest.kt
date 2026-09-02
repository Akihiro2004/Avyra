package com.avyra.music

import com.avyra.music.data.model.Song
import com.avyra.music.download.DownloadStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a downloaded file is called, and why that is not enough to say what it
 * is.
 *
 * A download is adopted rather than re-fetched when Music already holds a file
 * of the right name. The name is derived from title and artist and nothing
 * else, so the question this file exists to pin down is: how often do two
 * different recordings ask for the same one? Often, is the answer, and that was
 * the whole bug — the second one adopted the first one's audio and filed its own
 * video id against it, leaving a row with the right title, artist and album
 * playing a different recording on every replay.
 *
 * The runtime check that now guards adoption needs a device and lives in
 * `AutoDownloadsTest`; what is here is the collision itself, which needs
 * nothing.
 */
class DownloadNamingTest {

    private fun song(
        videoId: String,
        title: String,
        artist: String,
        duration: String? = null,
    ) = Song(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = null,
        durationText = duration,
    )

    /**
     * The collision, stated plainly. These are two different recordings — a
     * studio take and its remaster, different ids, three seconds apart — and
     * the store cannot tell them apart by name.
     */
    @Test
    fun `two recordings credited the same want the same filename`() {
        val original = song("aaaaaaaaaaa", "Get Lucky", "Daft Punk", "6:09")
        val remaster = song("bbbbbbbbbbb", "Get Lucky", "Daft Punk", "4:08")

        assertEquals(
            "the collision this guards against no longer exists; the guard may be stale",
            DownloadStore.fileNameFor(original, "m4a"),
            DownloadStore.fileNameFor(remaster, "m4a"),
        )
    }

    /** And the escape hatch has to actually separate them. */
    @Test
    fun `a distinguished name is unique to its track`() {
        val original = song("aaaaaaaaaaa", "Get Lucky", "Daft Punk", "6:09")
        val remaster = song("bbbbbbbbbbb", "Get Lucky", "Daft Punk", "4:08")

        assertNotEquals(
            DownloadStore.fileNameFor(original, "m4a", distinguish = true),
            DownloadStore.fileNameFor(remaster, "m4a", distinguish = true),
        )
        assertTrue(
            DownloadStore.fileNameFor(remaster, "m4a", distinguish = true)
                .contains("bbbbbbbbbbb"),
        )
    }

    /**
     * The ordinary name must not change shape. Every file already on every
     * device is named this way, and a download that stopped recognising them
     * would re-fetch a whole library.
     */
    @Test
    fun `the ordinary name is unchanged`() {
        assertEquals(
            "Daft Punk - Get Lucky.m4a",
            DownloadStore.fileNameFor(song("x", "Get Lucky", "Daft Punk"), "m4a"),
        )
    }

    /**
     * A source-backed id carries characters a filename cannot, and the
     * disambiguated form is still written to disk.
     */
    @Test
    fun `a distinguished name stays a legal filename`() {
        val fromModule = song("module:tidal/12345?x=1", "Song", "Artist")
        val name = DownloadStore.fileNameFor(fromModule, "flac", distinguish = true)
        assertTrue(
            "illegal characters survived into '$name'",
            name.none { it in "\\/:*?\"<>|" },
        )
    }
}
