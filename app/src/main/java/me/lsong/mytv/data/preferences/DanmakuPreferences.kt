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
    val offsetMs: Long = 0,
    val rendering: DanmakuRenderSettings = DanmakuRenderSettings(opacity = .85f, densityPct = 50),
    val providers: Set<String> = setOf("bilibili","tencent","iqiyi","youku","mgtv","letv"),
)
@Serializable
data class DanmakuBinding(val providerId: String, val mediaId: String, val episodeId: String)

class DanmakuPreferenceStore(private val preferences: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    fun read(): DanmakuPreferences = runCatching { preferences.getString("DANMAKU_OPTIONS_V1",null)?.let { json.decodeFromString<DanmakuPreferences>(it) } }.getOrNull() ?: DanmakuPreferences()
    fun save(value: DanmakuPreferences) { preferences.edit().putString("DANMAKU_OPTIONS_V1",json.encodeToString(value)).apply() }
    fun binding(key: String): DanmakuBinding? = bindings()[key]
    fun bind(key: String, binding: DanmakuBinding) {
        val values = bindings().toMutableMap().apply { remove(key); put(key,binding) }.entries.toList().takeLast(128).associate { it.key to it.value }
        preferences.edit().putString("DANMAKU_BINDINGS_V1", json.encodeToString(values)).apply()
    }
    private fun bindings(): Map<String,DanmakuBinding> = runCatching {
        preferences.getString("DANMAKU_BINDINGS_V1",null)?.let { json.decodeFromString<Map<String,DanmakuBinding>>(it) }
    }.getOrNull().orEmpty()
}
