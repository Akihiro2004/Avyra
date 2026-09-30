package com.avyra.music

import com.avyra.music.playback.MAX_QUEUE_HISTORY
import com.avyra.music.playback.LastPlayed
import com.avyra.music.playback.queueHistoryTrimCount
import com.avyra.music.playback.queueStartingAt
import com.avyra.music.playback.skippedByQueueJump
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueHistoryTest {

    @Test
    fun `history retains at most twenty five songs`() {
        assertEquals(0, queueHistoryTrimCount(MAX_QUEUE_HISTORY))
        assertEquals(1, queueHistoryTrimCount(MAX_QUEUE_HISTORY + 1))
        assertEquals(75, queueHistoryTrimCount(100))
    }

    @Test
    fun `a direct forward choice removes only bypassed songs`() {
        assertEquals(3..6, skippedByQueueJump(currentIndex = 2, targetIndex = 7))
    }

    @Test
    fun `next and previous navigation preserve the queue`() {
        assertNull(skippedByQueueJump(currentIndex = 2, targetIndex = 3))
        assertNull(skippedByQueueJump(currentIndex = 7, targetIndex = 2))
    }

    @Test
    fun `starting in the middle does not turn earlier unplayed rows into history`() {
        assertEquals(listOf("c", "d"), queueStartingAt(listOf("a", "b", "c", "d"), 2))
    }

    @Test
    fun `the saved queue is the whole queue for anything a listener builds`() {
        val window = LastPlayed.window(size = 300, index = 120)

        assertEquals(0 until 300, window)
    }

    @Test
    fun `a huge queue is still saved as a bounded window around the playing track`() {
        val window = LastPlayed.window(size = 10_000, index = 5_000)

        assertEquals(4_800, window.first)
        assertEquals(2_201, window.count())
    }

    @Test
    fun `the saved window keeps at most two hundred played rows`() {
        val window = LastPlayed.window(size = 10_000, index = 9_999)

        assertEquals(9_799, window.first)
        assertEquals(201, window.count())
    }
}
