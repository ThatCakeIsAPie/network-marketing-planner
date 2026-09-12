package com.networkmarketing.planner.data.remote

import android.content.Context
import com.networkmarketing.planner.BuildConfig

/**
 * Remembers which shared server (if any) the app syncs with. When the URL is blank the
 * app runs fully on-device (Room); when it is set, the app reads and writes that server so
 * it shares data with the browser version.
 *
 * The value defaults to [BuildConfig.PLANNER_SERVER_URL] (settable at build time with
 * -PplannerServerUrl=...), and can be overridden at runtime from the Goals screen.
 */
class ServerPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("planner_server", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_URL, null) ?: BuildConfig.PLANNER_SERVER_URL
        set(value) {
            prefs.edit().putString(KEY_URL, value.trim()).apply()
        }

    val isRemote: Boolean get() = serverUrl.isNotBlank()

    fun clear() {
        prefs.edit().putString(KEY_URL, "").apply()
    }

    companion object {
        private const val KEY_URL = "server_url"
    }
}
