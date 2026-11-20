package me.lsong.mytv.data.preferences

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.lsong.mytv.domain.danmaku.DanmakuRenderSettings

@Serializable
data class DanmakuPreferences(
    val enabled: Boolean = false,
    val area: Float = .5f,
    val rendering: DanmakuRenderSettings = DanmakuRenderSettings(opacity = .85f, densityPct = 50),
    val providers: Set<String> = setOf("bilibili","tencent","iqiyi","youku","mgtv","letv"),
)
@Serializable
data class DanmakuBinding(val providerId: String, val mediaId: String, val episodeId: String? = null)

class DanmakuPreferenceStore(private val preferences: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    fun read(): DanmakuPreferences = runCatching { preferences.getString("DANMAKU_OPTIONS_V1",null)?.let { json.decodeFromString<DanmakuPreferences>(it) } }.getOrNull() ?: DanmakuPreferences()
    fun save(value: DanmakuPreferences) { preferences.edit().putString("DANMAKU_OPTIONS_V1",json.encodeToString(value)).apply() }
    private fun key(workId: String, partId: String?, providerId: String) = "$workId|${partId.orEmpty()}|$providerId"
    private val legacyOffset = preferences.getLong("DANMAKU_LEGACY_OFFSET_MS", runCatching {
        json.parseToJsonElement(preferences.getString("DANMAKU_OPTIONS_V1", "{}").orEmpty())
            .let { it as? kotlinx.serialization.json.JsonObject }?.get("offsetMs")?.toString()?.toLongOrNull()
    }.getOrNull() ?: 0).also { preferences.edit().putLong("DANMAKU_LEGACY_OFFSET_MS",it).apply() }
    fun binding(workId: String, partId: String?, providerId: String): DanmakuBinding? {
        val values = bindings("DANMAKU_BINDINGS_V2")
        return values[key(workId,partId,providerId)] ?: bindings("DANMAKU_BINDINGS_V1")["$workId|${partId.orEmpty()}"]?.takeIf { it.providerId == providerId }
    }
    fun bind(workId: String, partId: String?, binding: DanmakuBinding) {
        val values = bindings("DANMAKU_BINDINGS_V2").toMutableMap().apply {
            remove(key(workId,partId,binding.providerId)); put(key(workId,partId,binding.providerId),binding)
            if (partId != null) put(key(workId,null,binding.providerId),binding.copy(episodeId = null))
        }.entries.toList().takeLast(512).associate { it.key to it.value }
        preferences.edit().putString("DANMAKU_BINDINGS_V2",json.encodeToString(values)).apply()
    }
    fun offset(workId: String, partId: String, providerId: String): Long {
        val values = offsets()
        return values[key(workId,partId,providerId)] ?: values[key(workId,null,providerId)] ?: legacyOffset
    }
    fun setOffset(workId: String, partId: String, providerId: String, offsetMs: Long) {
        val values = offsets().toMutableMap().apply {
            remove(key(workId,partId,providerId)); put(key(workId,partId,providerId),offsetMs)
            put(key(workId,null,providerId),offsetMs)
        }.entries.toList().takeLast(512).associate { it.key to it.value }
        preferences.edit().putString("DANMAKU_SOURCE_OFFSETS_V1",json.encodeToString(values)).apply()
    }
    private fun offsets(): Map<String,Long> = runCatching {
        preferences.getString("DANMAKU_SOURCE_OFFSETS_V1",null)?.let { json.decodeFromString<Map<String,Long>>(it) }
    }.getOrNull().orEmpty()
    private fun bindings(name: String): Map<String,DanmakuBinding> = runCatching {
        preferences.getString(name,null)?.let { json.decodeFromString<Map<String,DanmakuBinding>>(it) }
    }.getOrNull().orEmpty()
}
