package com.avyra.music.data

import com.avyra.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * Avyra ships as a sideloaded APK rather than through a store, so nothing
 * pushes an update notice on its own — this polls a "latest release" feed once
 * per launch and compares its tag against the running build.
 *
 * Installation deliberately stays outside the app. The update page opens the
 * release in the user's browser, which owns the download and its per-source
 * "install unknown apps" approval. A music player has no legitimate need for
 * Android's high-risk `REQUEST_INSTALL_PACKAGES` permission merely to update
 * itself, and carrying installer code makes a sideloaded build look more
 * dangerous than it is.
 *
 * The feed is *configured*, never hardcoded. It must point at Avyra's own
 * release API so the page opened from the update notice belongs to the same
 * application and signing identity. Blank by default, which makes the whole
 * feature a silent no-op until
 * `AVYRA_UPDATE_API_URL` is set in `local.properties`.
 */
object AppUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        /** The release's own Markdown body, shown as this update's "what's new". */
        val notes: String?,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    /**
     * How the last *user-initiated* check went.
     *
     * [available] cannot answer this. It holds an update or null, and null is
     * three different things: not looked yet, looked and you are current, and
     * looked and the network refused. That ambiguity is fine for the silent
     * poll at launch — nothing is waiting on it — and useless for a button,
     * where saying nothing back is indistinguishable from the button being
     * broken. So the manual path reports separately and leaves [available] to
     * mean what it always meant.
     */
    sealed interface CheckState {
        data object Idle : CheckState
        data object Checking : CheckState
        data object UpToDate : CheckState
        data class Found(val version: String) : CheckState
        data class Failed(val reason: String) : CheckState

        /** No endpoint compiled in — see `AVYRA_UPDATE_API_URL`. */
        data object NotConfigured : CheckState
    }

    private val _checkState = MutableStateFlow<CheckState>(CheckState.Idle)
    val checkState = _checkState.asStateFlow()

    /**
     * The silent poll at launch. Publishes an update if there is one and says
     * nothing otherwise — see [CheckState] for why that is wrong for a button
     * and right here.
     */
    suspend fun check() {
        fetchLatest()
    }

    /**
     * The same lookup, reported.
     *
     * Runs on [Dispatchers.IO] like everything else here, and drives
     * [checkState] through Checking into exactly one outcome so the UI can
     * show a result either way. [available] is still set on success, so the
     * full update dialog works from a manual check exactly as it does from
     * the automatic one.
     */
    suspend fun checkNow() {
        _checkState.value = CheckState.Checking
        _checkState.value = fetchLatest()
    }

    /** Back to quiet once a result has been read — see the pill's dismissal. */
    fun clearCheckState() {
        _checkState.value = CheckState.Idle
    }

    private suspend fun fetchLatest(): CheckState = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.UPDATE_API_URL.takeIf { it.isNotBlank() }
            ?: return@withContext CheckState.NotConfigured
        runCatching {
            val request = Request.Builder().url(endpoint).build()
            val body = Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}") else response.body?.string()
            } ?: error("Empty response")
            val release = json.parseToJsonElement(body) as? JsonObject ?: error("Malformed release")
            val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: error("Release has no tag")
            val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: error("Release has no page")
            val notes = release["body"]?.jsonPrimitive?.contentOrNull
            val latest = tag.removePrefix("v")
            if (isNewer(latest, BuildConfig.VERSION_NAME)) {
                _available.value = UpdateInfo(latest, url, notes)
                CheckState.Found(latest)
            } else {
                CheckState.UpToDate
            }
        }.getOrElse { CheckState.Failed(it.message ?: "Couldn’t reach the release feed") }
    }

    /** Numeric, dot-separated comparison — "1.10" outranks "1.9". */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
