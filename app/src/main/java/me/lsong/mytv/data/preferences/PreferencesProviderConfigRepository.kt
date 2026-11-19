package me.lsong.mytv.data.preferences

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

import me.lsong.mytv.domain.provider.*

class PreferencesProviderConfigRepository(private val preferences: SharedPreferences) : ProviderConfigRepository {
    companion object {
        const val KEY = "MEDIA_SOURCES"
        private const val LEGACY_IPTV_KEY = "IPTV_SOURCE_URL_LIST"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _sources = MutableStateFlow(readSources())
    override val providers = _sources.asStateFlow()

    private fun readSources(): List<ProviderConfig> {
        val saved = preferences.getString(KEY, null)
        if (saved != null) return json.decodeFromString(saved)
        val migrated = preferences.getStringSet(LEGACY_IPTV_KEY, emptySet()).orEmpty().sorted().mapIndexed { index, url ->
            ProviderConfig(
                id = UUID.nameUUIDFromBytes(url.toByteArray()).toString(),
                name = "直播源 ${index + 1}", url = url, kind = ProviderKind.M3U,
            )
        }
        preferences.edit().putString(KEY, json.encodeToString(migrated)).apply()
        return migrated
    }

    @Synchronized
    override fun save(source: ProviderConfig) {
        val normalized = source.normalized()
        val current = _sources.value
        replace(if (current.any { it.id == normalized.id }) current.map { if (it.id == normalized.id) normalized else it } else current + normalized)
    }

    @Synchronized
    override fun delete(id: String) = replace(_sources.value.filterNot { it.id == id })

    @Synchronized
    override fun replace(providers: List<ProviderConfig>) {
        val normalized = providers.map { it.normalized() }
        normalized.forEach { require(it.validationError() == null) { it.validationError().orEmpty() } }
        require(normalized.map { it.id }.distinct().size == normalized.size) { "播放源标识重复" }
        require(normalized.map { it.kind to it.url }.distinct().size == normalized.size) { "此接口地址已添加" }
        preferences.edit().putString(KEY, json.encodeToString(normalized)).apply()
        _sources.value = normalized
    }
}
