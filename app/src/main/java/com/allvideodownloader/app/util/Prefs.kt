package com.allvideodownloader.app.util

import android.content.Context

class Prefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER, false)
        set(value) = prefs.edit().putBoolean(KEY_DISCLAIMER, value).apply()

    var mediaPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_ASKED, value).apply()

    private companion object {
        const val KEY_DISCLAIMER = "disclaimer_accepted_v1"
        const val KEY_PERMISSION_ASKED = "media_permission_asked"
    }
}
