package com.avyra.music

import com.avyra.music.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyEqualizerMigrationTest {

    @Test
    fun `flat ten-band curve stays flat`() {
        assertEquals(List(7) { 0f }, AppSettings.fromLegacyTenBand(List(10) { 0f }))
    }

    @Test
    fun `old curve is sampled at the new centres`() {
        // A straight tilt in log-frequency, 1 dB per octave from 31 Hz.
        val tilt = listOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f)
        val migrated = AppSettings.fromLegacyTenBand(tilt)
        assertEquals(7, migrated.size)
        // 1 kHz is an old centre, so it keeps its value exactly.
        assertEquals(5f, migrated[3], 0.01f)
        // 14 kHz sits between 8 and 16 kHz.
        assertEquals(8.81f, migrated[6], 0.01f)
        // Every step rises, as the tilt does.
        assertEquals(migrated.sorted(), migrated)
    }
}
