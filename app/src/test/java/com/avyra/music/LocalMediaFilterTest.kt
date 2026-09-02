package com.avyra.music

import com.avyra.music.data.LocalMediaRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaFilterTest {
    @Test fun `short audio is rejected`() = assertFalse(
        LocalMediaRepository.isEligibleLocalMusic(
            29_999,
            "effect.mp3",
            "/storage/emulated/0/Music/effect.mp3",
        ),
    )

    @Test fun `voice recorder output is rejected`() = assertFalse(
        LocalMediaRepository.isEligibleLocalMusic(
            180_000,
            "voice-note.mp3",
            "/storage/emulated/0/Voice Recorder/voice-note.mp3",
        ),
    )

    @Test fun `ordinary lossless music is retained`() = assertTrue(
        LocalMediaRepository.isEligibleLocalMusic(
            180_000,
            "Song.flac",
            "/storage/emulated/0/Music/Artist/Song.flac",
        ),
    )

    @Test fun `long wav music is retained for Avyra`() = assertTrue(
        LocalMediaRepository.isEligibleLocalMusic(
            180_000,
            "Song.wav",
            "/storage/emulated/0/Music/Artist/Song.wav",
        ),
    )
}
