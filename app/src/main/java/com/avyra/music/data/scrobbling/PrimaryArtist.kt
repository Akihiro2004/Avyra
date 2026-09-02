package com.avyra.music.data.scrobbling

/**
 * Returns the first credited artist for scrobblers that do not handle joint
 * credits well. Separators embedded in names, such as AC/DC, stay untouched.
 */
internal fun String.primaryArtist(): String =
    split(PRIMARY_ARTIST_SEPARATOR, limit = 2).first().trim().ifBlank { this }

private val PRIMARY_ARTIST_SEPARATOR = Regex("""\s*,\s*|\s+&\s+|\s+＆\s+""")
