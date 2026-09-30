package com.avyra.music.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avyra.music.R
import com.avyra.music.data.model.ShelfItem
import com.avyra.music.data.settings.AppSettings
import com.avyra.music.download.Downloads
import com.avyra.music.download.SavedCollection
import com.avyra.music.ui.components.PAGE_GUTTER
import com.avyra.music.ui.replay.ReplayCardRow
import com.avyra.music.ui.replay.ReplayHeroCard
import com.avyra.music.ui.replay.ReplayStoryPage

// The phone's halves of the shared Library page: what sits on its "On device"
// shelf, and the way in to Replay at its head.

/**
 * The phone's "On device" shelf: Downloads and Local Music, the two remote
 * libraries it can reach, and the playlists downloaded whole.
 *
 * The downloaded playlists belong on that shelf because they are the same
 * promise everything else on it makes — here, now, without a network. Nothing
 * is truncated: the shelf is a row that scrolls, so "all of them" costs nothing.
 *
 * Downloaded *albums* are deliberately not here. An album stamps its name onto
 * each of its tracks, so the Downloads folder's Albums tab groups it back up on
 * its own and a card here would be a second door onto the same list. A
 * playlist has no tag anything can derive it from — its tracks are off forty
 * different releases — so this is the only place it can be reached without
 * going through that folder.
 */
@Composable
fun libraryDeviceItems(downloadedPlaylists: List<SavedCollection>): List<ShelfItem> {
    val webdavConfigured by AppSettings.webdavUrl.collectAsStateWithLifecycle()
    val smbHost by AppSettings.smbHost.collectAsStateWithLifecycle()
    val smbShare by AppSettings.smbShare.collectAsStateWithLifecycle()
    // The remote libraries share one card shape; each entry is title, subtitle
    // and the page it opens.
    val remotes = listOf(
        Triple(
            stringResource(R.string.webdav),
            if (webdavConfigured.isBlank()) {
                stringResource(R.string.webdav_not_configured)
            } else {
                stringResource(R.string.webdav_subtitle)
            },
            com.avyra.music.data.webdav.WebDavConfig.BROWSE_ID,
        ),
        Triple(
            stringResource(R.string.smb),
            if (smbHost.isBlank() || smbShare.isBlank()) {
                stringResource(R.string.smb_not_configured)
            } else {
                stringResource(R.string.smb_subtitle)
            },
            com.avyra.music.data.smb.SmbConfig.BROWSE_ID,
        ),
    )
    val downloadedPlaylist = stringResource(R.string.downloaded_playlist)
    return listOf(
        ShelfItem(
            title = stringResource(R.string.downloads),
            subtitle = stringResource(R.string.downloaded_songs),
            thumbnailUrl = null,
            videoId = null,
            browseId = "local:downloads",
        ),
        ShelfItem(
            title = stringResource(R.string.local_music),
            subtitle = stringResource(R.string.audio_files_on_device),
            thumbnailUrl = null,
            videoId = null,
            browseId = "local:all",
        ),
    ) + remotes.map { (title, subtitle, browseId) ->
        ShelfItem(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = null,
            videoId = null,
            browseId = browseId,
        )
    } + downloadedPlaylists.map { playlist ->
        ShelfItem(
            title = playlist.title,
            // The credit the playlist was downloaded with, because this is also
            // what the page it opens bills itself by — see `headerLines`, which
            // reads the kind and the owner back out of it. Saying "Downloaded
            // playlist" here instead would make that header read "Downloaded
            // playlist" over "PLAYLIST • 12 SONGS", and the shelf this card is on
            // already says where it lives.
            subtitle = playlist.subtitle.ifBlank { downloadedPlaylist },
            thumbnailUrl = playlist.thumbnailUrl,
            videoId = null,
            browseId = Downloads.pageIdFor(playlist.id),
        )
    }
}
