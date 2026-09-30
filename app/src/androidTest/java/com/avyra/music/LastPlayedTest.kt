package com.avyra.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avyra.music.data.model.QueueTier
import com.avyra.music.data.model.Song
import com.avyra.music.playback.LastPlayed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The resume-where-you-left-off contract: whatever was saved is what the next
 * launch reopens on — the whole queue, the track, the position, and the order
 * shuffle came from.
 *
 * Against the real storage, because the risk lives there: the queue and the
 * position are written separately and at different rates.
 */
@RunWith(AndroidJUnit4::class)
class LastPlayedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun song(i: Int, tier: QueueTier = QueueTier.CONTEXT) = Song(
        videoId = "video$i",
        title = "Track $i",
        artist = "Artist",
        thumbnailUrl = "https://example.com/$i.jpg",
        durationText = "3:0${i % 10}",
        queueTier = tier,
        queueEntryId = "entry$i",
    )

    @Before
    fun setUp() {
        LastPlayed.init(context)
        LastPlayed.clearImmediately()
    }

    @After
    fun tearDown() = LastPlayed.clearImmediately()

    @Test
    fun wholeQueueTrackAndPositionComeBack() {
        val queue = (0 until 300).map { song(it) }
        LastPlayed.saveQueueImmediately(queue, index = 120, positionMs = 42_000)

        val restored = LastPlayed.load()!!
        assertEquals(300, restored.songs.size)
        assertEquals("video120", restored.songs[restored.index].videoId)
        assertEquals(42_000, restored.positionMs)
        assertEquals(QueueTier.CONTEXT, restored.songs[0].queueTier)
        assertEquals("entry7", restored.songs[7].queueEntryId)
    }

    @Test
    fun progressAfterTheQueueWasSavedWins() {
        val queue = (0 until 10).map { song(it) }
        LastPlayed.saveQueueImmediately(queue, index = 2, positionMs = 1_000)
        // Two skips and a minute of listening later, only the cheap state moves.
        LastPlayed.savePlaybackState(index = 4, positionMs = 61_000)
        Thread.sleep(200)

        val restored = LastPlayed.load()!!
        assertEquals("video4", restored.songs[restored.index].videoId)
        assertEquals(61_000, restored.positionMs)
    }

    @Test
    fun aNewQueueIgnoresProgressFromTheOldOne() {
        LastPlayed.saveQueueImmediately((0 until 10).map { song(it) }, index = 8, positionMs = 90_000)
        LastPlayed.saveQueueImmediately(
            (100 until 103).map { song(it) },
            index = 1,
            positionMs = 0,
            shuffleOrder = listOf("entry102", "entry100", "entry101"),
        )

        val restored = LastPlayed.load()!!
        assertEquals("video101", restored.songs[restored.index].videoId)
        assertEquals(0, restored.positionMs)
        assertEquals(listOf("entry102", "entry100", "entry101"), restored.shuffleOrder)
    }

    @Test
    fun anAsyncSaveLandsBeforeTheNextRead() {
        LastPlayed.saveQueue((0 until 50).map { song(it) }, index = 10, positionMs = 5_000)
        // Queue writes are ordered on one thread; an immediate save behind it
        // has to wait for it rather than be overwritten by it.
        LastPlayed.saveQueueImmediately((0 until 5).map { song(it) }, index = 3, positionMs = 7_000)

        val restored = LastPlayed.load()!!
        assertEquals(5, restored.songs.size)
        assertEquals("video3", restored.songs[restored.index].videoId)
    }

    @Test
    fun clearingLeavesNothingToResume() {
        LastPlayed.saveQueueImmediately((0 until 3).map { song(it) }, index = 0, positionMs = 0)
        LastPlayed.clearImmediately()
        assertNull(LastPlayed.load())
    }
}
