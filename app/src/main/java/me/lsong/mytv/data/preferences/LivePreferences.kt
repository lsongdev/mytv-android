package me.lsong.mytv.data.preferences

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveOptions(
    val showLogos: Boolean = true,
    val showProgrammes: Boolean = true,
    val epgEnabled: Boolean = true,
    val discoverEpg: Boolean = true,
    val useDefaultEpg: Boolean = false,
    val refreshHours: Int = 6,
    val epgUrls: Set<String> = emptySet(),
)

/** Also observes URL changes made through the existing web configuration API. */
class LivePreferences(private val preferences: SharedPreferences) {
    private fun read() = LiveOptions(
        preferences.getBoolean("LIVE_LOGOS",true), preferences.getBoolean("LIVE_PROGRAMMES",true),
        preferences.getBoolean("EPG_ENABLED",true), preferences.getBoolean("EPG_DISCOVER",true),
        preferences.getBoolean("EPG_USE_DEFAULT",false), preferences.getInt("EPG_REFRESH_HOURS",6).coerceIn(1,24),
        preferences.getStringSet("EPG_URL_LIST",emptySet()).orEmpty().toSet(),
    )
    private val mutable = MutableStateFlow(read())
    val options = mutable.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mutable.value = read() }
    init { preferences.registerOnSharedPreferenceChangeListener(listener) }
    fun update(value: LiveOptions) {
        preferences.edit().putBoolean("LIVE_LOGOS",value.showLogos).putBoolean("LIVE_PROGRAMMES",value.showProgrammes)
            .putBoolean("EPG_ENABLED",value.epgEnabled).putBoolean("EPG_DISCOVER",value.discoverEpg)
            .putBoolean("EPG_USE_DEFAULT",value.useDefaultEpg).putInt("EPG_REFRESH_HOURS",value.refreshHours)
            .putStringSet("EPG_URL_LIST",value.epgUrls).apply()
        mutable.value = read()
    }
}
