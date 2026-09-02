package com.avyra.music

import com.avyra.music.playback.smart.AutomixAnalysisSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomixAnalysisSourceTest {
    @Test fun `youtube analysis accepts only its canonical cache key`() {
        assertTrue(AutomixAnalysisSource.isCanonicalYouTubeRendition("video", "video"))
        assertFalse(AutomixAnalysisSource.isCanonicalYouTubeRendition("video", "video#alt"))
        assertFalse(AutomixAnalysisSource.isCanonicalYouTubeRendition("video", "video#hifi"))
    }

    @Test fun `local audio is not constrained to a youtube cache key`() {
        assertTrue(AutomixAnalysisSource.isCanonicalYouTubeRendition(null, "content://song"))
    }
}
