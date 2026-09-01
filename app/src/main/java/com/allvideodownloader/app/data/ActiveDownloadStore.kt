package com.allvideodownloader.app.data

import android.content.Context

/**
 * Remembers which DownloadManager IDs belong to this app.
 *
 * DownloadManager outlives our process, so the IDs have to be persisted: after a restart we
 * still need to know which rows to poll for progress and which finished files to publish.
 */
class ActiveDownloadStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun ids(): Set<Long> =
        prefs.getStringSet(KEY_IDS, emptySet())
            ?.mapNotNull(String::toLongOrNull)
            ?.toSet()
            ?: emptySet()

    fun add(id: Long) = synchronized(this) {
        prefs.edit().putStringSet(KEY_IDS, (ids() + id).map(Long::toString).toSet()).apply()
    }

    fun remove(id: Long) = synchronized(this) {
        prefs.edit().putStringSet(KEY_IDS, (ids() - id).map(Long::toString).toSet()).apply()
    }

    fun contains(id: Long): Boolean = id in ids()

    companion object {
        /** Excluded from backup in res/xml/backup_rules.xml — IDs mean nothing on a new device. */
        const val FILE_NAME = "active_downloads"
        private const val KEY_IDS = "ids"
    }
}
