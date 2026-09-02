package com.avyra.music.playback.auto

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import com.avyra.music.R
import com.avyra.music.data.YtMusicRepository
import com.avyra.music.data.innertube.Innertube
import com.avyra.music.data.model.SearchFilter
import com.avyra.music.data.model.SearchResult
import com.avyra.music.data.model.Song
import com.avyra.music.download.Downloads
import com.avyra.music.playback.LastPlayed
import java.util.concurrent.ConcurrentHashMap

/**
 * The tree Android Auto browses, and the only thing in this app that knows how
 * a car asks for music.
 *
 * Auto does not run any of this app's UI. It reads a tree of [MediaItem]s out
 * of the [MediaLibraryService][androidx.media3.session.MediaLibraryService] and
 * draws it with its own templates, so everything the driver can reach has to be
 * expressible as browsable and playable nodes — which is why the shape here is
 * flatter and shorter than the phone's.
 *
 * Two constraints drove the shape, and both are about a car rather than a
 * phone:
 *
 * Depth costs attention. Auto caps how much of a list it will show while
 * moving, and every extra level is another glance away from the road, so the
 * tree is two deep and never three.
 *
 * Signal is not a given. Downloads sit first and work in a tunnel; everything
 * else needs the network, and the tabs that need an account are not offered at
 * all when there isn't one — an empty tab a driver taps twice to discover is
 * worse than a tab that was never there.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object AutoLibrary {

    // ---- Ids -----------------------------------------------------------
    //
    // Namespaced so a browse id can never be mistaken for a video id: the
    // playable leaves carry a bare [Song.videoId] as their media id, because
    // that is what has to come back through onAddMediaItems for the queue to
    // be rebuildable. Anything with this prefix is a node, anything without is
    // a track.

    const val ROOT = "avyra:root"

    private const val NODE = "avyra:"
    private const val DOWNLOADS = "avyra:downloads"
    private const val DOWNLOADS_ALL = "avyra:downloads:all"
    private const val DOWNLOADS_COLLECTION = "avyra:downloads:c:"
    private const val LIBRARY = "avyra:library"
    private const val LIBRARY_LIKED = "avyra:library:liked"
    private const val LIBRARY_PLAYLIST = "avyra:library:pl:"
    private const val RECENT = "avyra:recent"
    private const val QUICK_PICKS = "avyra:quickpicks"

    /** Whether [mediaId] names a node in this tree rather than a track. */
    fun isNode(mediaId: String): Boolean = mediaId.startsWith(NODE)

    // ---- Track lookup ---------------------------------------------------

    /**
     * Every track this tree has handed out, by video id.
     *
     * Auto plays a track by sending its media id back, and a media id is all it
     * sends: Media3 strips the local configuration off an item crossing the
     * session boundary, so the [MediaItem] that arrives has no URI and cannot
     * be played as it stands. Rebuilding it needs the [Song] again, and this is
     * the only place that still has one — see `PlaybackService.onAddMediaItems`.
     *
     * Deliberately unbounded within a process and never persisted. It holds
     * what the driver has actually browsed to, which is small; a cold Auto
     * session repopulates it on its first list.
     */
    private val known = ConcurrentHashMap<String, Song>()

    private fun remember(songs: List<Song>): List<Song> {
        songs.forEach { known[it.videoId] = it }
        return songs
    }

    fun songFor(mediaId: String): Song? = known[mediaId]

    // ---- Root -----------------------------------------------------------

    fun root(): MediaItem = browsable(
        id = ROOT,
        title = "Avyra",
        // Auto reads the root's own style to decide how to draw the tabs
        // beneath it; without this they fall back to a plain list, which is
        // the one presentation a car should not have at the top level.
        extras = contentStyle(browsable = GRID, playable = LIST),
    )

    /**
     * The root's children are Auto's tabs, so this list is the whole top-level
     * navigation and is kept to four.
     *
     * The two that need an account appear only when there is one. A tab that
     * opens onto "nothing here" reads as the app being broken rather than as
     * the driver being signed out, and there is no way to explain the
     * difference on a car screen.
     */
    private suspend fun rootChildren(context: Context): List<MediaItem> = buildList {
        add(
            browsable(
                DOWNLOADS,
                "Downloads",
                subtitle = "Available offline",
                icon = R.drawable.ic_auto_downloads,
                extras = contentStyle(browsable = GRID, playable = LIST),
            ),
        )
        if (recentSongs(context).isNotEmpty()) {
            add(
                browsable(
                    RECENT,
                    "Recent",
                    icon = R.drawable.ic_auto_recent,
                    extras = contentStyle(browsable = LIST, playable = LIST),
                ),
            )
        }
        if (signedIn()) {
            add(
                browsable(
                    LIBRARY,
                    "Library",
                    icon = R.drawable.ic_auto_library,
                    extras = contentStyle(browsable = GRID, playable = LIST),
                ),
            )
            add(
                browsable(
                    QUICK_PICKS,
                    "Quick picks",
                    icon = R.drawable.ic_auto_quickpicks,
                    extras = contentStyle(browsable = LIST, playable = LIST),
                ),
            )
        }
    }

    private fun signedIn(): Boolean = Innertube.cookie != null

    // ---- Children --------------------------------------------------------

    suspend fun children(context: Context, parentId: String): List<MediaItem> = when {
        parentId == ROOT -> rootChildren(context)

        parentId == DOWNLOADS -> downloadsChildren(context)
        parentId == DOWNLOADS_ALL -> items(downloadedSongs(context))
        parentId.startsWith(DOWNLOADS_COLLECTION) ->
            items(collectionSongs(context, parentId.removePrefix(DOWNLOADS_COLLECTION)))

        parentId == RECENT -> items(recentSongs(context))

        parentId == LIBRARY -> libraryChildren()
        parentId == LIBRARY_LIKED -> items(likedSongs())
        parentId.startsWith(LIBRARY_PLAYLIST) ->
            items(playlistSongs(parentId.removePrefix(LIBRARY_PLAYLIST)))

        parentId == QUICK_PICKS -> items(quickPicks())

        else -> emptyList()
    }

    /**
     * A single node, for the odd case where Auto asks about one directly —
     * restoring its own back stack after the process was killed, most often.
     */
    suspend fun item(context: Context, mediaId: String): MediaItem? {
        if (mediaId == ROOT) return root()
        if (!isNode(mediaId)) return songFor(mediaId)?.let(::track)
        // Nodes are cheap to rebuild but only their parent knows their title,
        // so the honest way to answer is to ask the parent for its children.
        val parent = parentOf(mediaId) ?: return null
        return children(context, parent).firstOrNull { it.mediaId == mediaId }
    }

    private fun parentOf(mediaId: String): String? = when {
        mediaId == DOWNLOADS || mediaId == RECENT ||
            mediaId == LIBRARY || mediaId == QUICK_PICKS -> ROOT
        mediaId == DOWNLOADS_ALL || mediaId.startsWith(DOWNLOADS_COLLECTION) -> DOWNLOADS
        mediaId == LIBRARY_LIKED || mediaId.startsWith(LIBRARY_PLAYLIST) -> LIBRARY
        else -> null
    }

    // ---- Downloads (the offline half) --------------------------------------

    private suspend fun downloadsChildren(context: Context): List<MediaItem> = buildList {
        val all = downloadedSongs(context)
        if (all.isNotEmpty()) {
            add(
                browsable(
                    DOWNLOADS_ALL,
                    "All downloads",
                    subtitle = "${all.size} ${if (all.size == 1) "song" else "songs"}",
                    icon = R.drawable.ic_auto_downloads,
                    extras = contentStyle(browsable = LIST, playable = LIST),
                ),
            )
        }
        // Saved playlists and albums, which is how most people actually think
        // about what they took offline.
        Downloads.savedPlaylists().forEach { collection ->
            add(
                browsable(
                    DOWNLOADS_COLLECTION + collection.id,
                    collection.title,
                    subtitle = collection.subtitle.ifBlank { null },
                    artworkUri = collection.thumbnailUrl,
                    extras = contentStyle(browsable = LIST, playable = LIST),
                ),
            )
        }
    }

    private suspend fun downloadedSongs(context: Context): List<Song> =
        remember(runCatching { Downloads.getDownloadedSongs(context) }.getOrDefault(emptyList()))

    /**
     * One saved collection's tracks, in the order the page listed them.
     *
     * Filtered against what is actually on disk: a collection remembers the ids
     * it was saved with, and a track deleted individually afterwards is still
     * named there. Offering it in a car would resolve to a stream over a
     * connection that, in the place downloads exist for, is not there.
     */
    private suspend fun collectionSongs(context: Context, id: String): List<Song> {
        val collection = Downloads.collections.value[id] ?: return emptyList()
        val onDisk = downloadedSongs(context).associateBy { it.videoId }
        return collection.videoIds.mapNotNull { onDisk[it] }
    }

    // ---- Recent -----------------------------------------------------------

    /**
     * What was playing last, newest first.
     *
     * Read from the saved queue rather than from YouTube's history: it is on
     * disk, so this tab is the one that answers instantly and without signal,
     * and it is also the thing a driver getting into a car most often wants —
     * whatever they were listening to on the way in.
     */
    private fun recentSongs(context: Context): List<Song> {
        val snapshot = LastPlayed.load() ?: return emptyList()
        // From the track it was left on, so the list starts where the listening
        // stopped rather than at the top of an album finished days ago.
        val ordered = snapshot.songs.drop(snapshot.index) + snapshot.songs.take(snapshot.index)
        return remember(ordered.take(MAX_ROWS))
    }

    // ---- Library (needs an account) ----------------------------------------

    private suspend fun libraryChildren(): List<MediaItem> = buildList {
        add(
            browsable(
                LIBRARY_LIKED,
                "Liked songs",
                icon = R.drawable.ic_auto_library,
                extras = contentStyle(browsable = LIST, playable = LIST),
            ),
        )
        YtMusicRepository.userPlaylists().getOrNull().orEmpty().forEach { playlist ->
            add(
                browsable(
                    LIBRARY_PLAYLIST + playlist.browseId,
                    playlist.title,
                    subtitle = playlist.subtitle.ifBlank { null },
                    artworkUri = playlist.thumbnailUrl,
                    extras = contentStyle(browsable = LIST, playable = LIST),
                ),
            )
        }
    }

    private suspend fun likedSongs(): List<Song> = remember(
        YtMusicRepository.library().getOrNull()?.likedSongs.orEmpty().take(MAX_ROWS),
    )

    private suspend fun playlistSongs(browseId: String): List<Song> = remember(
        YtMusicRepository.browseSongs(browseId).getOrNull()?.songs.orEmpty().take(MAX_ROWS),
    )

    /**
     * The home feed, flattened.
     *
     * Auto has no way to draw a shelf of shelves, and a driver has no attention
     * for one, so the shelves are collapsed into a single queue of tracks in
     * the order the feed offered them.
     */
    private suspend fun quickPicks(): List<Song> {
        val feed = YtMusicRepository.home().getOrNull() ?: return emptyList()
        val songs = feed.shelves
            .flatMap { it.items }
            // A shelf mixes tracks with albums and playlists. Only the tracks
            // can be queued, and a card naming a browse id is a page this tree
            // has no second level to show.
            .mapNotNull { card ->
                card.videoId?.let {
                    Song(
                        videoId = it,
                        title = card.title,
                        artist = card.subtitle,
                        thumbnailUrl = card.thumbnailUrl,
                    )
                }
            }
            .distinctBy { it.videoId }
            .take(MAX_ROWS)
        return remember(songs)
    }

    // ---- Search -----------------------------------------------------------

    /**
     * Voice search, which on Auto is the only text entry a driver gets.
     *
     * Answered from downloads first and only then from the network: "play X"
     * in a car should reach a track that is already on the phone without a
     * round trip, and should still work where there is no round trip to make.
     */
    suspend fun search(context: Context, query: String): List<Song> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()

        val offline = downloadedSongs(context).filter { song ->
            song.title.contains(needle, ignoreCase = true) ||
                song.artist.contains(needle, ignoreCase = true)
        }

        // Not gated on an account: YouTube answers a song search to a signed-out
        // client too, and a driver who never signed in still asked for a song.
        val online = YtMusicRepository.search(needle, SearchFilter.SONGS)
            .getOrNull().orEmpty()
            .filterIsInstance<SearchResult.Track>()
            .map { it.song }

        return remember((offline + online).distinctBy { it.videoId }.take(MAX_ROWS))
    }

    // ---- Item construction -------------------------------------------------

    /**
     * Track rows, and the record of which list they were.
     *
     * Public because search results have to come through here too. They did
     * not, at first: they were mapped straight to items, which left
     * [lastServed] holding whatever had been browsed before — so tapping the
     * third search result played the first, and the only reason the queue was
     * not one track long is that AutoPlay had quietly extended it.
     */
    fun items(songs: List<Song>): List<MediaItem> {
        if (songs.isNotEmpty()) lastServed = songs
        return songs.map(::track)
    }

    /**
     * The list a tapped row belongs to, and where in it that row sits.
     *
     * Auto sends one media id when a row is tapped and says nothing about the
     * list it came from, so the list has to be inferred — and the last one
     * served is the one on screen, because a row cannot be tapped before its
     * list has been drawn. Returns null for anything that isn't in it, which
     * is the honest answer for a voice result or a stale id.
     */
    fun queueFor(mediaId: String): Pair<List<Song>, Int>? {
        val list = lastServed
        val index = list.indexOfFirst { it.videoId == mediaId }
        return if (index >= 0) list to index else null
    }

    @Volatile
    private var lastServed: List<Song> = emptyList()

    /**
     * A playable leaf.
     *
     * The media id is the bare video id on purpose — see [known]. Everything
     * else here is what Auto draws: it renders from [MediaMetadata] alone and
     * never sees the queue item this turns into.
     */
    private fun track(song: Song): MediaItem = MediaItem.Builder()
        .setMediaId(song.videoId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.albumName)
                .setArtworkUri(song.thumbnailUrl?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()

    private fun browsable(
        id: String,
        title: String,
        subtitle: String? = null,
        artworkUri: String? = null,
        @DrawableRes icon: Int? = null,
        extras: Bundle? = null,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(
                    artworkUri?.let(Uri::parse) ?: icon?.let { resourceUri(it) },
                )
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .apply { if (extras != null) setExtras(extras) }
                .build(),
        )
        .build()

    /**
     * A drawable as something Auto can fetch.
     *
     * It loads artwork out of process, so a bitmap or a resource id would mean
     * nothing to it; `android.resource://` is the one form that survives the
     * trip and still resolves back to this package's own drawables.
     */
    private fun resourceUri(@DrawableRes res: Int): Uri =
        Uri.parse("android.resource://$packageName/$res")

    /**
     * This build's own application id, which is not a constant: the dev flavor
     * installs as `com.avyra.music.dev`, and a resource URI naming the wrong
     * package resolves to nothing at all.
     */
    private lateinit var packageName: String

    fun init(context: Context) {
        packageName = context.packageName
    }

    // ---- Auto's presentation hints ----------------------------------------

    private const val GRID = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
    private const val LIST = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM

    private fun contentStyle(browsable: Int, playable: Int) = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, browsable)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, playable)
    }

    /**
     * How many rows any one list is allowed.
     *
     * Auto stops a list well short of this while the car is moving, so the
     * ceiling is not really about the screen — it is about not spending a
     * driver's first ten seconds paging a playlist over a phone connection.
     */
    private const val MAX_ROWS = 200

    /** Everything this tree serves, dropped. Called when the account changes. */
    fun forget() {
        known.clear()
        lastServed = emptyList()
    }
}
