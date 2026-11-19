package me.lsong.mytv.domain.provider

import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.StateFlow
import java.net.URI
import java.util.UUID

@Serializable
enum class ProviderKind { @kotlinx.serialization.SerialName("IPTV") M3U, @kotlinx.serialization.SerialName("VOD") MAC_CMS }

@Serializable
data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    @kotlinx.serialization.SerialName("type") val kind: ProviderKind,
    val isEnabled: Boolean = true,
) {
    fun normalized() = copy(name = name.trim(), url = url.trim())

    fun validationError(): String? = when {
        id.isBlank() -> "播放源标识不能为空"
        name.isBlank() -> "请填写源名称"
        url.isBlank() -> "请填写接口地址"
        !isHttpUrl(url) -> "请输入完整的 http:// 或 https:// 地址"
        else -> null
    }
}

fun isHttpUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

interface ProviderConfigRepository {
    val providers: StateFlow<List<ProviderConfig>>
    fun save(source: ProviderConfig)
    fun delete(id: String)
    fun replace(providers: List<ProviderConfig>)
}
