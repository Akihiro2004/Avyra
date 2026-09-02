package com.avyra.music.playback.smart

/**
 * Keeps Automix analysis on the canonical YouTube rendition. Alternate cache
 * keys belong to source substitutions or quality upgrades and may be a
 * different recording even when playback legitimately prefers them.
 */
internal object AutomixAnalysisSource {
    fun isCanonicalYouTubeRendition(videoId: String?, cacheKey: String): Boolean =
        videoId == null || cacheKey == videoId
}
