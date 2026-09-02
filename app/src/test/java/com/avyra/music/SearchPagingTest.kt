package com.avyra.music

import com.avyra.music.data.innertube.InnertubeParser
import com.avyra.music.data.model.SearchResult
import com.avyra.music.data.model.ShelfItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPagingTest {
    @Test fun `search page returns rows and continuation`() {
        val json = """
            {
              "contents": [
                {"musicResponsiveListItemRenderer": {
                  "navigationEndpoint": {"browseEndpoint": {
                    "browseId": "VLPL123",
                    "browseEndpointContextSupportedConfigs": {
                      "browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_PLAYLIST"}
                    }
                  }},
                  "flexColumns": [
                    {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Road songs"}]}}}
                  ]
                }},
                {"continuationItemRenderer": {"continuationEndpoint": {
                  "continuationCommand": {"token": "SEARCH_MORE"}
                }}}
              ]
            }
        """.trimIndent()

        val page = InnertubeParser.parseSearchPage(Json.parseToJsonElement(json).jsonObject)
        assertEquals("SEARCH_MORE", page.continuation)
        assertEquals(1, page.rows.size)
        assertTrue(page.rows.single() is SearchResult.Browse)
    }

    @Test fun `library page returns cards and continuation`() {
        val json = """
            {
              "contents": [
                {"musicTwoRowItemRenderer": {
                  "title": {"runs": [{"text": "Road songs"}]},
                  "navigationEndpoint": {"browseEndpoint": {"browseId": "VLPLROAD"}}
                }}
              ],
              "continuations": [{"nextContinuationData": {"continuation": "LIBRARY_MORE"}}]
            }
        """.trimIndent()

        val page = InnertubeParser.parseLibraryItemPage(Json.parseToJsonElement(json).jsonObject)
        assertEquals("LIBRARY_MORE", page.continuation)
        assertEquals("VLPLROAD", page.items.single().browseId)
    }

    @Test fun `paged playlist picker still excludes auto playlists`() {
        val playlists = InnertubeParser.parseUserPlaylists(
            listOf(
                ShelfItem("Road songs", "Playlist", null, null, "VLPLROAD"),
                ShelfItem("Liked Music", "Auto playlist", null, null, "VLLM"),
            ),
        )
        assertEquals(listOf("PLROAD"), playlists.map { it.playlistId })
    }
}
