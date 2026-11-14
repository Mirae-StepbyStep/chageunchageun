package com.mirae.chageunchageun.data.settings

import android.content.Context

/** [SettingsStore] on its own SharedPreferences file, so [clear] removes only these settings. */
class PrefsSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun write(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun clear() = prefs.edit().clear().apply()
}
