package com.avyra.music

import com.avyra.music.data.scrobbling.primaryArtist
import org.junit.Assert.assertEquals
import org.junit.Test

class PrimaryArtistTest {
    @Test fun `comma credit uses first artist`() =
        assertEquals("Artist One", "Artist One, Artist Two".primaryArtist())

    @Test fun `spaced ampersand credit uses first artist`() =
        assertEquals("Artist One", "Artist One & Artist Two".primaryArtist())

    @Test fun `slash inside a name is preserved`() =
        assertEquals("AC/DC", "AC/DC".primaryArtist())

    @Test fun `ampersand inside a name is preserved`() =
        assertEquals("Simon&Garfunkel", "Simon&Garfunkel".primaryArtist())
}
