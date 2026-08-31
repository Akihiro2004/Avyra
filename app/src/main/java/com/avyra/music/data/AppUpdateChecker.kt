package com.avyra.music.data

import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.avyra.music.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Avyra ships as a sideloaded APK rather than through a store, so nothing
 * pushes an update notice on its own — this polls a "latest release" feed once
 * per launch and compares its tag against the running build.
 *
 * The update itself is also handled here: the release's `.apk` asset is
 * downloaded into the app's cache and handed to the system package installer,
 * so the whole round trip stays inside the app instead of bouncing out to a
 * browser.
 *
 * The feed is *configured*, never hardcoded — see [endpoint]. Avyra is a
 * separate application id signed with a separate key, so pointing this at
 * somebody else's repository would offer the listener an APK that cannot
 * update this app and installs alongside it as a second, unrelated one. Blank
 * by default, which makes the whole feature a silent no-op until
 * `AVYRA_UPDATE_API_URL` is set in `local.properties`.
 */
object AppUpdateChecker {

    private const val TAG = "Avyra"

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        val apkUrl: String?,
        /** The release's own Markdown body, shown as this update's "what's new". */
        val notes: String?,
    )

    private const val CACHE_SUBDIR = "updates"

    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    /** Where this update's APK download currently stands, for the dialog's progress row. */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val fraction: Float) : DownloadState
        data class Ready(val file: File) : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download = _download.asStateFlow()

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

    /** Set from the UI thread when the user cancels; polled between network reads. */
    @Volatile
    private var downloadCancelled = false

    /**
     * The download's own scope, deliberately not the caller's — see
     * [downloadApk] for why that distinction is the whole fix.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var job: Job? = null

    /** Attempts per download, and the base gap between them. */
    private const val ATTEMPTS = 4
    private const val RETRY_DELAY_MS = 1_500L

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
            val apkUrl = apkAssetUrl(release)
            val notes = release["body"]?.jsonPrimitive?.contentOrNull
            val latest = tag.removePrefix("v")
            if (isNewer(latest, BuildConfig.VERSION_NAME)) {
                _available.value = UpdateInfo(latest, url, apkUrl, notes)
                CheckState.Found(latest)
            } else {
                CheckState.UpToDate
            }
        }.getOrElse { CheckState.Failed(it.message ?: "Couldn’t reach the release feed") }
    }

    /**
     * Wipes any APK left over from a previous run. Called once at cold start
     * so a downloaded update is only ever "Install Now" for the session that
     * downloaded it — the next launch starts clean rather than trying to work
     * out whether a leftover file is still good.
     */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        File(context.cacheDir, CACHE_SUBDIR).listFiles()?.forEach { it.delete() }
    }

    /**
     * The release usually carries exactly one `.apk`; take its direct download
     * URL. A release without one (source-only draft, renamed asset) leaves
     * [UpdateInfo.apkUrl] null and the UI falls back to opening the releases
     * page as before.
     */
    private fun apkAssetUrl(release: JsonObject): String? = runCatching {
        release["assets"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { asset ->
                asset["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true &&
                    asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
            }
            ?.get("browser_download_url")
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()

    /**
     * Streams the current update's APK into the app cache, reporting progress
     * through [download]. A finished file survives a cancelled dialog: until
     * the state is reset, "Install Now" comes straight back without a second
     * download.
     *
     * Runs on this object's own [scope] rather than the caller's, and that is
     * the point rather than a detail. Both callers are composables — one a
     * `LaunchedEffect`, one a `rememberCoroutineScope` — so anything that left
     * that composition cancelled the download along with it, closing the socket
     * out from under a read already in flight. Eighty megabytes into a hundred,
     * that arrives as "Software caused connection abort" and reads as the
     * download having simply given up. Nothing on screen can interrupt it now;
     * [cancelDownload] is the only way to stop it.
     *
     * Single-flight, for a race the two callers made easy to hit: a manual
     * check that finds an update starts this *and* opens the dialog offering a
     * Download button, so tapping it started a second copy that deleted the
     * first one's half-written file out from under it.
     */
    fun downloadApk(context: Context) {
        if (job?.isActive == true) return
        // The activity that asked for this can be long gone before it finishes.
        val app = context.applicationContext
        downloadCancelled = false
        _download.value = DownloadState.Downloading(0f)
        job = scope.launch { runDownload(app) }
    }

    /**
     * One attempt per pass, each resuming where the last one stopped.
     *
     * The asset is around a hundred megabytes and phones drop connections. A
     * single unresumable stream that size fails often enough on mobile data to
     * look broken rather than unlucky, so a dropped one is picked up instead of
     * restarted — GitHub's asset host advertises `Accept-Ranges: bytes`.
     *
     * Each attempt re-requests the release URL rather than whatever it
     * redirected to last time: that redirect lands on a signed URL carrying an
     * expiry, and reusing it is how a retry earns itself a 403.
     *
     * The partial file is left behind on failure deliberately. It is what the
     * next attempt resumes from, and [clearCache] clears it at the next cold
     * start regardless.
     */
    private suspend fun runDownload(context: Context) {
        val info = _available.value ?: return
        val url = info.apkUrl ?: return
        val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
        val target = File(dir, "avyra-${info.version}.apk")
        // Anything else in here belongs to a version no longer being offered.
        dir.listFiles()?.forEach { if (it != target) it.delete() }

        var lastError: Throwable? = null
        for (attempt in 1..ATTEMPTS) {
            if (downloadCancelled) break
            val outcome = runCatching { fetchInto(target, url) }
            if (outcome.isSuccess) {
                _download.value =
                    if (downloadCancelled) DownloadState.Idle else DownloadState.Ready(target)
                return
            }
            lastError = outcome.exceptionOrNull()
            TrackLog.w(TAG, "update download attempt $attempt failed: ${lastError?.message}")
            if (downloadCancelled || attempt == ATTEMPTS) break
            delay(RETRY_DELAY_MS * attempt)
        }

        _download.value = if (downloadCancelled) {
            DownloadState.Idle
        } else {
            DownloadState.Failed(lastError?.message ?: "Download failed")
        }
    }

    /** Throws unless [target] is left holding the whole asset. */
    internal fun fetchInto(target: File, url: String) {
        val have = target.length()
        val request = Request.Builder().url(url)
            .apply { if (have > 0) header("Range", "bytes=$have-") }
            .build()

        Http.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
            // 206 is the tail of the file and appends. Any other success is the
            // whole thing again — the server ignored the range — so the partial
            // has to be overwritten rather than added to.
            val resumed = response.code == 206 && have > 0
            val body = response.body ?: error("Empty download body")
            val length = body.contentLength()
            val total = if (resumed && length > 0) have + length else length

            var written = if (resumed) have else 0L
            FileOutputStream(target, resumed).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (downloadCancelled) break
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            _download.value = DownloadState.Downloading(
                                (written.toFloat() / total).coerceIn(0f, 1f),
                            )
                        }
                    }
                }
            }
            // Belt and braces rather than the main defence. A body that stops
            // short of its own Content-Length is caught by OkHttp first, which
            // is the case GitHub's asset host produces; this covers the one it
            // cannot see, where the length was never announced and a stream
            // that ends early is indistinguishable from one that finished. A
            // short file here would reach the installer as a corrupt package,
            // reported as a bad APK rather than as a failed download.
            check(downloadCancelled || total <= 0 || written >= total) {
                "Download stopped at $written of $total bytes"
            }
        }
    }

    /** Stops an in-flight download; the next read loop sees this and bails. */
    fun cancelDownload() {
        downloadCancelled = true
    }

    /** Back to square one after a failure, so the dialog offers Download again. */
    fun resetDownload() {
        _download.value = DownloadState.Idle
    }

    /**
     * Installs a downloaded APK, and puts the app back on screen afterwards.
     *
     * Driven through [PackageInstaller] rather than the older
     * `ACTION_INSTALL_PACKAGE` intent, for one reason worth the extra code: a
     * session reports its outcome to a receiver, and that receiver runs *after*
     * the install, in the new build's process. An intent cannot do that. The
     * install replaces this app and kills the process that launched it, so
     * there is no result to come back to and nothing left to relaunch from —
     * which is why the intent version always ended with the app simply gone and
     * the user left on their home screen. See [UpdateInstallReceiver].
     *
     * What it cannot do is skip the confirmation. Android shows a system
     * install prompt for every install by an ordinary app, and no permission
     * available to one removes it — `REQUEST_INSTALL_PACKAGES` only earns the
     * right to ask. So the honest shape of this is: one tap here, one tap on
     * the system dialog, and the app reopens itself.
     *
     * Sideloaded apps also need the user's blessing per app ("install unknown
     * apps"). Without it the session is refused, so the user is sent to that
     * switch first and taps Update again after.
     */
    fun installApk(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }

        runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                setAppPackageName(context.packageName)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("avyra", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                // Mutable because the system fills in the confirmation intent
                // it wants shown — see [UpdateInstallReceiver]. Immutable here
                // means STATUS_PENDING_USER_ACTION arrives with nothing to
                // launch and the install silently never happens.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, UpdateInstallReceiver::class.java)
                        .setPackage(context.packageName),
                    flags,
                )
                session.commit(callback.intentSender)
            }
        }.onFailure { error ->
            TrackLog.w(TAG, "could not start the update install: ${error.message}")
            _download.value = DownloadState.Failed(
                error.message ?: "Couldn’t start the installer",
            )
        }
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
