package com.avyra.music.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Moves data written by builds that still used the old internal identity.
 *
 * The old files are left in place. Copying first makes the migration safe if a
 * launch is interrupted, and existing values in the Avyra file always win.
 */
internal object LegacyDataMigration {

    private val plainPreferenceFiles = listOf(
        "bitchord_settings" to "avyra_settings",
        "bitchord_last_played" to "avyra_last_played",
        "bitchord_widget" to "avyra_widget",
    )

    fun migratePlainPreferences(context: Context) {
        plainPreferenceFiles.forEach { (legacyName, currentName) ->
            copyMissing(
                from = context.getSharedPreferences(legacyName, Context.MODE_PRIVATE),
                to = context.getSharedPreferences(currentName, Context.MODE_PRIVATE),
            )
        }
    }

    fun encryptedPreferences(
        context: Context,
        currentEncryptedName: String,
        legacyEncryptedName: String,
        currentPlainName: String,
        legacyPlainName: String,
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val current = encrypted(context, currentEncryptedName, masterKey)

        runCatching { encrypted(context, legacyEncryptedName, masterKey) }
            .getOrNull()
            ?.let { copyMissing(it, current) }
        copyMissing(context.getSharedPreferences(currentPlainName, Context.MODE_PRIVATE), current)
        copyMissing(context.getSharedPreferences(legacyPlainName, Context.MODE_PRIVATE), current)
        return current
    }

    fun plainPreferences(
        context: Context,
        currentName: String,
        legacyName: String,
    ): SharedPreferences = context.getSharedPreferences(currentName, Context.MODE_PRIVATE).also {
        copyMissing(context.getSharedPreferences(legacyName, Context.MODE_PRIVATE), it)
    }

    private fun encrypted(
        context: Context,
        name: String,
        masterKey: MasterKey,
    ): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        name,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun copyMissing(from: SharedPreferences, to: SharedPreferences) {
        if (from.all.isEmpty()) return
        val editor = to.edit()
        var changed = false
        from.all.forEach { (key, value) ->
            if (to.contains(key)) return@forEach
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> return@forEach
            }
            changed = true
        }
        if (changed) editor.commit()
    }
}
