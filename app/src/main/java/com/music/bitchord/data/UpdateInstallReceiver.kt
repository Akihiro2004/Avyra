package com.music.bitchord.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.music.bitchord.MainActivity

/**
 * Hears back from the system about an update this app asked to install.
 *
 * A [PackageInstaller] session reports through a broadcast rather than a
 * result, and that indirection is what makes relaunching possible at all: the
 * install replaces this app, which kills the process that asked for it, so
 * there is nothing left to return to. The broadcast arrives afterwards, in the
 * *new* build's process, which is exactly where "open the app again" wants to
 * be run from.
 *
 * Two of the three statuses matter:
 *
 *  - [PackageInstaller.STATUS_PENDING_USER_ACTION] is the system saying it
 *    wants the user to confirm. That confirmation cannot be skipped — Android
 *    requires it for every install by an ordinary app, whatever permissions it
 *    holds — so the only thing to do is put the dialog on screen promptly. The
 *    intent to launch is handed over in the extras, which is why the
 *    [android.app.PendingIntent] behind this has to be mutable.
 *  - [PackageInstaller.STATUS_SUCCESS] is the new build installed and ready,
 *    and where the app puts itself back on screen so an update reads as one
 *    tap rather than as the app vanishing.
 *
 * Anything else is a failure the user has already been told about by the
 * system's own dialog, so it is recorded and left alone.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                // The session was committed from a context that may be gone by
                // now, so this needs a task of its own to land in.
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { TrackLog.w(TAG, "could not show the install prompt: ${it.message}") }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                TrackLog.d(TAG, "update installed; reopening")
                runCatching {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .setAction(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
                            ),
                    )
                }.onFailure { TrackLog.w(TAG, "installed, but could not reopen: ${it.message}") }
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                TrackLog.w(TAG, "update install did not complete: ${message ?: "no reason given"}")
            }
        }
    }

    private companion object {
        const val TAG = "BitChord"
    }
}
