package com.avyra.music.data.sources

/**
 * The kinds of source this build knows how to talk to.
 *
 * Fixed and small on purpose. **Declaration order here is the order sources
 * are tried** — see `SourceRegistry.active()`, which sorts on `kind.ordinal` —
 * so a custom module comes before the built-in one, then JioSaavn, then
 * YouTube Music. Adding a source means adding a [MusicSource] implementation
 * and an entry here, which is the point — every protocol the app speaks is one
 * someone can read in this repo, and a source can't teach the app a new way to
 * behave after it ships.
 *
 * What varies per *instance* — which index, whose module — is [SourceConfig].
 */
enum class SourceKind(
    val label: String,
    val detail: String,
    /** The chips under the name on the sources screen. */
    val labels: List<String>,
    /** Whether an instance needs a URL before it can do anything — and so needs an editor. */
    val needsServer: Boolean,
    /** Whether this kind can serve bit-exact audio when asked. */
    val canServeLossless: Boolean,
    /**
     * Whether this kind answers quickly enough to be worth asking *before* a
     * track is played, so its copy can be pinned and cached ahead of time.
     *
     * Measured on this device, the gap is not close: JioSaavn answers a search
     * and hands back a stream URL in about 0.4s, while a module index takes
     * 7-13s to walk its backends — and read-ahead runs on the track *after* the
     * one playing, so a lookup that slow is usually still going when the
     * listener arrives. A wasted JioSaavn resolve costs one HTTP round trip; a
     * wasted module resolve costs a QuickJS engine, an index fetch and several
     * backend searches. The first is worth spending speculatively and the
     * second is not.
     *
     * False for [YOUTUBE] as well, though it *is* warmed ahead of time — that
     * happens through its own read-ahead in
     * [AudioCache][com.avyra.music.playback.AudioCache], which speaks video
     * ids directly and needs no cross-source match to find the track.
     */
    val worthPrefetching: Boolean = false,
) {
    /**
     * A module index the user pointed at themselves, tried ahead of the one
     * baked into the build.
     *
     * Same protocol as [MODULE] and served by the same [ModuleSource] — the
     * only thing this kind carries that the other doesn't is its place in the
     * order, which is what being declared first here decides. There is at most
     * one at a time; see [SourceRegistry.setCustomModule].
     */
    CUSTOM_MODULE(
        label = "Custom module",
        detail = "Your own compatible module index. Tried before the built-in one.",
        labels = listOf("FLAC", "Lossless", "Hi-Res", "Plugins"),
        needsServer = true,
        canServeLossless = true,
    ),

    /**
     * A URL to a compatible module-index JSON.
     *
     * The index lists JS plugin descriptors; each plugin ships a JS file
     * that exports `searchTracks()` and `getTrackStreamUrl()`. The app
     * fetches the index, loads each plugin's JS into a QuickJS sandbox,
     * and calls those functions — the same mechanism Convx uses to support
     * services like Tidal, Qobuz, Apple Music, etc.
     *
     * The JS runs in a sandboxed QuickJS VM with no access to the Android
     * runtime, only to a wired-in `fetch()` implementation.
     */
    MODULE(
        label = "Module source",
        detail = "A URL to a compatible module index. Modules are JS plugins that " +
            "can search and stream from services like Tidal, Qobuz, Apple Music and more.",
        labels = listOf("FLAC", "Lossless", "Hi-Res", "Plugins"),
        needsServer = true,
        canServeLossless = true,
    ),

    /**
     * The source the app was built on, and the one a queued track was chosen
     * from — so nothing below it may quietly stand in for it.
     *
     * The order these are declared in *is* the ranking: `SourceRegistry.active`
     * sorts by it, and anything above YouTube is offered every YouTube track
     * before it plays, with whatever comes back played instead. That trade is
     * only worth making for a source the listener deliberately added, because
     * the match behind it is title, artist and runtime and nothing else — and a
     * cover, a re-recording or an unrelated song released under the same name
     * agrees on all three.
     *
     * Measured on a real track: a hololive cover queued from YouTube was served
     * an unrelated JioSaavn song of the same name and length, while the row went
     * on showing the right title, artist, artwork, runtime and synced lyrics.
     * Nothing downstream can catch that — every check available downstream is
     * one of the three things that already agreed.
     *
     * It cannot be removed — see [SourceRegistry]. Nothing else in the app can
     * supply a home feed, a radio station or a related-tracks queue.
     */
    YOUTUBE(
        label = "YouTube Music",
        detail = "The full catalogue, at Opus up to about 171 kbps. Lossy — there is no " +
            "lossless rendition to ask for.",
        labels = listOf("Lossy", "Full catalogue", "Radio"),
        needsServer = false,
        canServeLossless = false,
    ),

    /**
     * Below YouTube on purpose, which is a change from how this shipped.
     *
     * It used to sit above, so it was offered every YouTube track and its
     * answer was played instead — a swap nobody asked for, on a source that is
     * enabled out of the box and lossy anyway, so the upside was 320kbps over
     * 171 and the downside was somebody else's recording. It is still tried
     * when YouTube has nothing to give; it just no longer stands in for a track
     * YouTube can serve.
     */
    JIOSAAVN(
        label = "JioSaavn",
        detail = "JioSaavn high-quality streams up to 320kbps AAC/MP4. A lossy fallback, " +
            "tried when YouTube cannot serve a track.",
        labels = listOf("High Quality", "320kbps"),
        needsServer = false,
        canServeLossless = false,
        worthPrefetching = true,
    ),
}
