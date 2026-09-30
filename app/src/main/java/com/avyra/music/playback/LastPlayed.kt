package com.avyra.music.playback

import android.content.Context
import android.content.SharedPreferences
import com.avyra.music.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/**
 * What was playing, so the next launch opens on it — the track, the queue
 * around it, where in the track it stopped and the order shuffle took it from.
 *
 * Two stores, split by how often each changes:
 *
 *  - **The queue** goes to its own file, written off the main thread and
 *    swapped into place in one rename, only when Media3 reports the playlist
 *    changed. A file rather than a preference because it is the whole queue —
 *    a playlist of a few hundred tracks — and SharedPreferences rewrites and
 *    re-reads its entire file for every key, including the position ticks below.
 *  - **Index and position** are two primitives in SharedPreferences, updated
 *    every few seconds while playing and on every pause, skip and track change.
 *
 * Every queue write carries a version, and the primitives are only trusted when
 * they were written against that same version. A process killed between the
 * two writes therefore restores the queue with the index it was saved with,
 * never with an index that belongs to the queue before it.
 */
object LastPlayed {

    class Snapshot(
        val songs: List<Song>,
        val index: Int,
        val positionMs: Long,
        /** Entry ids in their pre-shuffle order, or empty when shuffle was off. */
        val shuffleOrder: List<String> = emptyList(),
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var file: File
    private val json = Json { ignoreUnknownKeys = true }

    /** One writer, so queue files land on disk in the order they were saved. */
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "last-played") }

    @Volatile
    private var version = 0L

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        file = File(context.filesDir, QUEUE_FILE)
        version = prefs.getLong(KEY_VERSION, 0L)
    }

    /** The slice of a [size]-long queue that is kept, around [index]. */
    internal fun window(size: Int, index: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val safeIndex = index.coerceIn(0, size - 1)
        val start = (safeIndex - KEEP_BEHIND).coerceAtLeast(0)
        return start until (safeIndex + 1 + KEEP_AHEAD).coerceAtMost(size)
    }

    /**
     * Replaces the saved queue. [songs] is already the window to keep — see
     * [window] — and [index] is the playing track's place within it.
     */
    fun saveQueue(
        songs: List<Song>,
        index: Int,
        positionMs: Long,
        shuffleOrder: List<String> = emptyList(),
    ) {
        if (songs.isEmpty()) {
            clear()
            return
        }
        val stored = stored(songs, index, positionMs, shuffleOrder)
        writer.execute { write(stored) }
        prefs.edit()
            .putLong(KEY_VERSION, stored.version)
            .putInt(KEY_INDEX, stored.index)
            .putLong(KEY_POSITION, stored.positionMs)
            .apply()
    }

    /**
     * As [saveQueue], but on disk before this returns. Used at an explicit
     * queue boundary, where restoring the queue from before that boundary
     * would be worse than the small cost of a synchronous write.
     */
    fun saveQueueImmediately(
        songs: List<Song>,
        index: Int,
        positionMs: Long,
        shuffleOrder: List<String> = emptyList(),
    ) {
        if (songs.isEmpty()) {
            clearImmediately()
            return
        }
        val stored = stored(songs, index, positionMs, shuffleOrder)
        // Through the writer rather than around it, so an older queue still
        // waiting in line cannot land on top of this one afterwards.
        writer.submit { write(stored) }.get()
        prefs.edit()
            .putLong(KEY_VERSION, stored.version)
            .putInt(KEY_INDEX, stored.index)
            .putLong(KEY_POSITION, stored.positionMs)
            .commit()
    }

    /** Update only cheap scalar state; this performs no queue conversion or JSON work. */
    fun savePlaybackState(index: Int, positionMs: Long) {
        if (version == 0L) return
        prefs.edit()
            .putLong(KEY_VERSION, version)
            .putInt(KEY_INDEX, index.coerceAtLeast(0))
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun load(): Snapshot? {
        val stored = readFile() ?: readLegacy() ?: return null
        if (stored.tracks.isEmpty()) return null
        val songs = stored.tracks.map(StoredTrack::toSong)
        // The primitives are newer than the file whenever they belong to it.
        val current = stored.version != 0L && prefs.getLong(KEY_VERSION, -1L) == stored.version
        val index = if (current) prefs.getInt(KEY_INDEX, stored.index) else stored.index
        val positionMs = if (current) prefs.getLong(KEY_POSITION, stored.positionMs) else stored.positionMs
        version = stored.version.takeIf { it != 0L } ?: version
        return Snapshot(
            songs = songs,
            index = index.coerceIn(songs.indices),
            positionMs = positionMs.coerceAtLeast(0L),
            shuffleOrder = stored.shuffleOrder,
        )
    }

    fun clear() {
        version = 0L
        writer.execute { file.delete() }
        prefs.edit().clear().apply()
    }

    /** Remove a stale queue before another one is installed. */
    fun clearImmediately() {
        version = 0L
        writer.submit { file.delete() }.get()
        prefs.edit().clear().commit()
    }

    private fun stored(
        songs: List<Song>,
        index: Int,
        positionMs: Long,
        shuffleOrder: List<String>,
    ): StoredQueue {
        // Strictly increasing within a process and across restarts, which is
        // all [load] needs to tell the queue's own primitives from stale ones.
        val next = maxOf(System.currentTimeMillis(), version + 1)
        version = next
        return StoredQueue(
            version = next,
            tracks = songs.map { StoredTrack.from(it) },
            index = index.coerceIn(songs.indices),
            positionMs = positionMs.coerceAtLeast(0L),
            shuffleOrder = shuffleOrder,
        )
    }

    /** Written beside the real file and renamed over it, so a crash mid-write loses nothing. */
    private fun write(stored: StoredQueue) {
        runCatching {
            val encoded = json.encodeToString(StoredQueue.serializer(), stored)
            val temp = File(file.parentFile, "$QUEUE_FILE.tmp")
            temp.writeText(encoded)
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
            // The queue used to live in the preferences file itself.
            if (prefs.contains(KEY_LEGACY_QUEUE)) prefs.edit().remove(KEY_LEGACY_QUEUE).apply()
        }
    }

    private fun readFile(): StoredQueue? =
        file.takeIf { it.isFile }
            ?.let { runCatching { json.decodeFromString<StoredQueue>(it.readText()) }.getOrNull() }

    /** A queue saved by a build that kept it in the preferences file. */
    private fun readLegacy(): StoredQueue? {
        val raw = prefs.getString(KEY_LEGACY_QUEUE, null) ?: return null
        val legacy = runCatching { json.decodeFromString<StoredQueue>(raw) }.getOrNull() ?: return null
        return legacy.copy(
            index = prefs.getInt(KEY_INDEX, legacy.index),
            positionMs = prefs.getLong(KEY_POSITION, legacy.positionMs),
        )
    }

    @Serializable
    private data class StoredQueue(
        val version: Long = 0L,
        val tracks: List<StoredTrack>,
        val index: Int = 0,
        val positionMs: Long = 0L,
        val shuffleOrder: List<String> = emptyList(),
    )

    @Serializable
    private data class StoredTrack(
        val id: String,
        val title: String,
        val artist: String,
        val artwork: String? = null,
        val auto: Boolean = false,
        val tier: String? = null,
        val entryId: String? = null,
        val local: String? = null,
        val path: String? = null,
        val duration: String? = null,
        val album: String? = null,
        val explicit: Boolean? = null,
        val video: Boolean = false,
        val radio: String? = null,
        val source: String? = null,
        val sourceType: String? = null,
        val sourceId: String? = null,
        val artistId: String? = null,
        val albumId: String? = null,
        val videoOrigin: Boolean = false,
    ) {
        fun toSong(): Song {
            val resolvedTier = when (tier) {
                "USER_QUEUE" -> com.avyra.music.data.model.QueueTier.USER_QUEUE
                "CONTEXT" -> com.avyra.music.data.model.QueueTier.CONTEXT
                "AUTOPLAY" -> com.avyra.music.data.model.QueueTier.AUTOPLAY
                else -> if (auto) com.avyra.music.data.model.QueueTier.AUTOPLAY else com.avyra.music.data.model.QueueTier.CONTEXT
            }
            return Song(
                videoId = id,
                title = title,
                artist = artist,
                thumbnailUrl = artwork,
                durationText = duration,
                artistId = artistId,
                albumId = albumId,
                albumName = album,
                isExplicit = explicit,
                isVideo = video,
                isVideoOrigin = videoOrigin || video,
                queueTier = resolvedTier,
                queueEntryId = entryId,
                radioName = radio,
                playbackSource = source,
                playbackSourceType = sourceType?.let {
                    runCatching { com.avyra.music.data.model.PlaybackSourceType.valueOf(it) }.getOrNull()
                },
                playbackSourceId = sourceId,
                localUri = local,
                localPath = path,
            )
        }

        companion object {
            fun from(song: Song) = StoredTrack(
                id = song.videoId,
                title = song.title,
                artist = song.artist,
                artwork = song.thumbnailUrl,
                auto = song.fromAutoplay,
                tier = song.queueTier.name,
                entryId = song.queueEntryId,
                local = song.localUri,
                path = song.localPath,
                duration = song.durationText,
                album = song.albumName,
                explicit = song.isExplicit,
                video = song.isVideo,
                radio = song.radioName,
                source = song.playbackSource,
                sourceType = song.playbackSourceType?.name,
                sourceId = song.playbackSourceId,
                artistId = song.artistId,
                albumId = song.albumId,
                videoOrigin = song.isVideoOrigin,
            )
        }
    }

    /**
     * Wide enough to be "the whole queue" for anything a listener builds — an
     * entire playlist plus AutoPlay's tail — and still a bound, so a
     * several-thousand-track library queue cannot turn every edit into a
     * megabyte write.
     */
    private const val KEEP_BEHIND = 200
    private const val KEEP_AHEAD = 2_000
    private const val PREFS_NAME = "avyra_last_played"
    private const val QUEUE_FILE = "last_queue.json"
    private const val KEY_LEGACY_QUEUE = "queue"
    private const val KEY_VERSION = "queue_version"
    private const val KEY_INDEX = "index"
    private const val KEY_POSITION = "position"
}
