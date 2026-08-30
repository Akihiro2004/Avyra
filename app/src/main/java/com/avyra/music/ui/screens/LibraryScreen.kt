package com.avyra.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avyra.music.data.YtMusicRepository
import com.avyra.music.data.model.HomeShelf
import com.avyra.music.R
import com.avyra.music.data.model.LibraryPage
import com.avyra.music.data.model.ShelfItem
import com.avyra.music.data.model.UiState
import com.avyra.music.data.settings.AppSettings
import com.avyra.music.download.Downloads
import com.avyra.music.download.SavedCollection
import com.avyra.music.ui.icons.AvyraIcons
import com.avyra.music.ui.components.LibraryArtwork
import com.avyra.music.ui.components.LibraryIconTile
import com.avyra.music.ui.components.LibraryRow
import com.avyra.music.ui.components.MessageState
import com.avyra.music.ui.components.PAGE_GUTTER
import com.avyra.music.ui.components.PullToRefresh
import com.avyra.music.ui.components.SHELF_CARD_WIDTH
import com.avyra.music.ui.components.libraryGrid
import com.avyra.music.ui.components.librarySkeleton
import com.avyra.music.ui.player.MeshGradientBackground
import com.avyra.music.ui.player.rememberArtworkColors
import com.avyra.music.ui.replay.ReplayHeroCard
import java.util.Locale

/**
 * The signed-in library: the saved collections, as vertical lists.
 *
 * Lists rather than the carousels Home and Discover use, and the difference is
 * about what each page is *for* rather than about consistency. A carousel
 * answers "does any of this appeal to me" — it shows six covers large and keeps
 * the rest off-screen, which is the right trade when the listener has not chosen
 * any of it. The library is the opposite question: everything on this page was
 * chosen already, and the listener has come for one specific thing. Sideways
 * scrolling answers that badly — it hides most of the contents, says nothing
 * about how many there are, and turns reaching the twentieth artist into a
 * gesture instead of a glance.
 *
 * So the same shelves are laid out down the page: a screenful is a dozen rows
 * rather than two and a half cards, and the artwork shrinks to the size where it
 * identifies a row rather than filling one. See
 * [LibraryRow][com.avyra.music.ui.components.LibraryRow].
 *
 * Deliberately only the collections. This page used to end with two runs of
 * track rows — "Liked Music" and "Songs" — which are two overlapping answers
 * to the same question and read as one list that couldn't make up its mind: a
 * track that stopped being liked didn't leave the page, it moved down it, into
 * a section most people had taken for more of the same. Liked Music is a
 * playlist, and it is reached the way every other playlist here is, by opening
 * its row.
 *
 * The liked list is still fetched — it is what the rest of the app reads a
 * track's rating off (see MainViewModel's `likeStatuses`); it just isn't a
 * second place to browse it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    signedIn: Boolean,
    state: UiState<LibraryPage>,
    listState: LazyListState,
    onShelfItemClick: (ShelfItem) -> Unit,
    onShelfItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    /**
     * A shelf's "Show all" — every shelf's row here stops at five cards (see
     * [LibraryGridShelf]), so this is the only way to reach whatever didn't
     * fit.
     */
    onShowAll: (HomeShelf) -> Unit,
    /**
     * The Replay's leading card — minutes listened — or null before anything has
     * been played.
     *
     * Not drawn as a card here. This page is a list of places to go, and a card
     * is an object to look at; one sitting at the top of it read as the Replay
     * page's opening reprinted on a page about playlists and downloads. What the
     * card is used for instead is its *numbers* and its *artwork*: the button
     * below says what is behind it, and is painted in the colours of the record
     * that year was mostly spent on.
     */
    replayCard: ReplayHeroCard?,
    onOpenReplay: () -> Unit,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    /**
     * The playlists downloaded whole, as cards behind the two device folders.
     *
     * They belong on that shelf because they are the same promise everything
     * else on it makes — here, now, without a network. Nothing is truncated:
     * the shelf is a row that scrolls, so "all of them" costs nothing.
     *
     * Downloaded *albums* are deliberately not here. An album stamps its name
     * onto each of its tracks, so the Downloads folder's Albums tab groups it
     * back up on its own and a card here would be a second door onto the same
     * list. A playlist has no tag anything can derive it from — its tracks are
     * off forty different releases — so this is the only place it can be reached
     * without going through that folder.
     */
    downloadedPlaylists: List<SavedCollection> = emptyList(),
) {
    val pinnedPlaylists by AppSettings.pinnedPlaylists.collectAsStateWithLifecycle()
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.library),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
                )
            }
            librarySection(
                shelf = HomeShelf(
                    title = ON_DEVICE,
                    items = listOf(
                        ShelfItem(
                            title = "Downloads",
                            subtitle = "Downloaded songs",
                            thumbnailUrl = null,
                            videoId = null,
                            browseId = "local:downloads",
                        ),
                        ShelfItem(
                            title = "Local Music",
                            subtitle = "Audio files on device",
                            thumbnailUrl = null,
                            videoId = null,
                            browseId = "local:all",
                        ),
                    ),
                ),
                onItemClick = onShelfItemClick,
            )
            if (!signedIn) {
                item(key = "signin") {
                    MessageState(
                        message = "Sign in to your Google account to see your YouTube Music " +
                            "liked songs, playlists and history.",
                        actionLabel = "Sign in",
                        onAction = onSignIn,
                    )
                }
                return@LazyColumn
            }
            when (state) {
                is UiState.Loading -> librarySkeleton()
                is UiState.Error -> item(key = "error") {
                    MessageState(state.message, actionLabel = "Retry", onAction = onRetry)
                }
                is UiState.Success -> {
                    // A fresh account has no Playlists shelf at all, and that
                    // is exactly the account most in need of the row that
                    // makes one — so the section is drawn either way, empty but
                    // for the row that creates the first playlist.
                    val shelves = state.data.shelves
                    if (shelves.none { it.title == PLAYLISTS }) {
                        playlistSection(
                            shelf = HomeShelf(PLAYLISTS, emptyList()),
                            onItemClick = onShelfItemClick,
                            onItemLongPress = onShelfItemLongPress,
                            onNewPlaylist = onNewPlaylist,
                        )
                    }
                    shelves.forEach { shelf ->
                        if (shelf.title == PLAYLISTS) {
                            playlistSection(
                                shelf = shelf,
                                onItemClick = onShelfItemClick,
                                onItemLongPress = onShelfItemLongPress,
                                onNewPlaylist = onNewPlaylist,
                            )
                        } else {
                            librarySection(shelf = shelf, onItemClick = onShelfItemClick)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One heading and the rows under it, emitted straight into the page's
 * [LazyColumn] rather than wrapped in a single item.
 *
 * That is what makes this a list rather than a column that happens to be
 * vertical: a section of forty playlists composes the handful of rows on screen
 * and nothing else, and scrolling recycles them. Emitting the whole section as
 * one item — which is what the carousel version did, because a `LazyRow` does
 * its own recycling inside — would measure and lay out every row in the library
 * on the first frame.
 *
 * [leadingRow] rides at the head of the section, ahead of the content: the
 * Playlists section's "New playlist" row, which belongs among the playlists
 * rather than in a bar somewhere above them.
 */
private fun LazyListScope.librarySection(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    leadingRow: (@Composable (showDivider: Boolean) -> Unit)? = null,
) {
    item(key = "header:${shelf.title}") { SectionHeader(shelf.title, shelf.subtitle) }
    leadingRow?.let { row ->
        item(key = "lead:${shelf.title}") { row(shelf.items.isNotEmpty()) }
    }
    itemsIndexed(
        items = shelf.items,
        key = { index, item -> "row:${shelf.title}:${item.browseId ?: item.videoId ?: item.title}:$index" },
    ) { index, item ->
        LibraryRow(
            title = item.title,
            subtitle = item.subtitle,
            onClick = { onItemClick(item) },
            onLongPress = onItemLongPress?.let { press -> { press(item) } },
            // The last row of a section is closed by the gap below it. A
            // divider there would read as the start of the next section
            // rather than the end of this one.
            showDivider = index < shelf.items.lastIndex,
        ) {
            SectionLeading(item = item, circular = shelf.title in PEOPLE)
        }
    }
    item(key = "gap:${shelf.title}") { Spacer(Modifier.height(SECTION_GAP)) }
}

/**
 * The one section on this page that can be written to: it leads with the row
 * that creates a playlist, and holding a row opens the rename/delete menu.
 */
private fun LazyListScope.playlistSection(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    pinnedPlaylists: List<String> = emptyList(),
) {
    librarySection(
        shelf = shelf,
        onItemClick = onItemClick,
        onItemLongPress = onItemLongPress,
        leadingRow = { showDivider ->
            LibraryRow(
                title = "New playlist",
                subtitle = "Saved to YouTube Music",
                onClick = onNewPlaylist,
                showDivider = showDivider,
            ) {
                LibraryIconTile(icon = AvyraIcons.Plus)
            }
        },
    )
}

/**
 * What sits at the head of a row: a cover, a portrait, or a tinted tile.
 *
 * The two on-device rows have no artwork to show and never will, so they get
 * icons — in different container colours, because at this size the colour is
 * what tells them apart at a glance and two similar glyphs would not.
 */
@Composable
private fun SectionLeading(item: ShelfItem, circular: Boolean) {
    when (item.browseId) {
        "local:downloads" -> LibraryIconTile(icon = AvyraIcons.Download)
        "local:all" -> LibraryIconTile(
            icon = Icons.Rounded.LibraryMusic,
            container = MaterialTheme.colorScheme.secondaryContainer,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        else -> LibraryArtwork(url = item.thumbnailUrl, circular = circular)
    }
}

/** The library feed whose rows are the account's own — see [playlistSection]. */
private const val PLAYLISTS = "Playlists"
private const val ON_DEVICE = "On Device"

/**
 * The shelves whose rows are *people*, and so are drawn as circles.
 *
 * A square is a release — something with a cover somebody designed. A circle is
 * a person, and the distinction is worth keeping because these two shelves sit
 * directly beneath the album and playlist ones: without it, a library reads as
 * one undifferentiated run of squares where half of them open a page of releases
 * and the other half open a page of albums *by* someone.
 */
private val PEOPLE = setOf("Artists", "Subscriptions")

/**
 * The space between one section's last row and the next section's heading.
 *
 * Doing the work a divider would otherwise have to: the run of hairlines inside
 * a section stops at its edge, and this gap is what says the next heading starts
 * something rather than continuing it.
 */
private val SECTION_GAP = 18.dp

/**
 * One shelf on a page of its own — upstream's "Show all", rendered as a list.
 *
 * Kept to upstream's signature, [LazyGridState] included, so the call site does
 * not have to know which of us is drawing it. A single-column grid *is* a list,
 * and taking that route rather than swapping in a `LazyColumn` means the state
 * the caller remembers still belongs to the thing it is scrolling.
 *
 * Reachable only in principle from this build: [librarySection] draws every item
 * a shelf has, so nothing here ever asks to see more. It exists because the
 * feature is upstream's and the next merge will expect it — a page that is
 * currently unreachable is cheaper to keep than a merge conflict every release.
 */
@Composable
fun LibraryGridPage(
    shelf: HomeShelf,
    gridState: LazyGridState,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onNewPlaylist: (() -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        onNewPlaylist?.let { create ->
            item(key = "lead:new") {
                LibraryRow(
                    title = "New playlist",
                    subtitle = "Saved to YouTube Music",
                    onClick = create,
                    showDivider = shelf.items.isNotEmpty(),
                ) { LibraryIconTile(icon = AvyraIcons.Plus) }
            }
        }
        gridItemsIndexed(
            items = shelf.items,
            key = { index, item -> "row:${item.browseId ?: item.videoId ?: item.title}:$index" },
        ) { index, item ->
            LibraryRow(
                title = item.title,
                subtitle = item.subtitle,
                onClick = { onItemClick(item) },
                onLongPress = { onItemLongPress(item) },
                showDivider = index < shelf.items.lastIndex,
            ) {
                SectionLeading(item = item, circular = shelf.title in PEOPLE)
            }
        }
    }
}
