package me.lsong.mytv.data.providers.m3u

import me.lsong.mytv.domain.live.liveChannelEditionYear
import me.lsong.mytv.domain.live.LiveCatalogProvider

import me.lsong.media.m3u.M3uDocument
import me.lsong.mytv.data.providers.cache.ProviderStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.lsong.media.m3u.M3uEntry
import me.lsong.media.m3u.M3uClient
import me.lsong.media.http.MediaEndpoint
import me.lsong.mytv.domain.live.ProviderChannelRecord
import me.lsong.mytv.domain.live.ProviderLiveCatalog
import me.lsong.mytv.domain.playback.PlaybackResource
import me.lsong.mytv.domain.provider.ProviderConfig
import me.lsong.mytv.domain.provider.ProviderRecordRef

class M3uLiveProviderAdapter(private val client: M3uClient = M3uClient(), private val storage: ProviderStorage? = null) : LiveCatalogProvider {
    override suspend fun cached(provider: ProviderConfig): ProviderLiveCatalog? = storage?.cached(provider,"live",M3uDocument.serializer())?.value?.let { map(provider,it) }
    override suspend fun fetch(provider: ProviderConfig): ProviderLiveCatalog {
        val document = storage?.get(provider,"live",M3uDocument.serializer()) { client.fetch(MediaEndpoint(provider.url)) }
            ?: client.fetch(MediaEndpoint(provider.url))
        return map(provider,document)
    }
    private suspend fun map(provider: ProviderConfig, document: M3uDocument): ProviderLiveCatalog = withContext(Dispatchers.Default) {
        ProviderLiveCatalog(provider, document.entries.mapNotNull { entry ->
            val resolved = resolveMediaUrl(provider.url,entry.uri) ?: return@mapNotNull null
            entry.copy(uri = resolved).toChannelRecord(provider)
        },
            listOfNotNull(document.headers["x-tvg-url"], document.headers["url-tvg"]).flatMap { it.split(',') }
                .mapNotNull { resolvePlaylistUrl(provider.url,it.trim()) }.distinct(), error = storage?.state(provider)?.error)
    }
}

internal fun M3uEntry.toChannelRecord(provider: ProviderConfig): ProviderChannelRecord {
    val name = attributes["tvg-name"]?.takeIf(String::isNotBlank) ?: title
    val group = attributes["group-title"]?.takeIf(String::isNotBlank) ?: "未分组"
    val remoteId = attributes["tvg-id"]?.takeIf(String::isNotBlank) ?: listOf(group, name).joinToString("|")
    val edition = liveChannelEditionYear(title)
    val ref = ProviderRecordRef(provider.id,if (edition == null) remoteId else "$remoteId|edition:$edition")
    val headers = buildMap {
        attributes["http-user-agent"]?.let { put("User-Agent", it) }
        (attributes["http-referer"] ?: attributes["http-referrer"])?.let { put("Referer", it) }
        attributes["http-header"]?.let { header ->
            val separator = header.indexOf('=').takeIf { it > 0 } ?: header.indexOf(':')
            if (separator > 0) put(header.substring(0, separator).trim(), header.substring(separator + 1).trim())
        }
    }
    return ProviderChannelRecord(ref, name, title, group,
        PlaybackResource(uri, ref, provider.name, headers), attributes["tvg-logo"]?.let { resolvePlaylistUrl(provider.url,it) },
        attributes["tvg-id"]?.takeIf(String::isNotBlank))
}

internal fun resolvePlaylistUrl(base: String, value: String): String? = runCatching {
    value.takeIf(String::isNotBlank)?.let { java.net.URI(base).resolve(it.trim()).toString() }
        ?.takeIf { me.lsong.mytv.domain.provider.isHttpUrl(it) }
}.getOrNull()

/** Resolve media URLs against the playlist while retaining non-HTTP transports for MPV. */
internal fun resolveMediaUrl(playlistUrl: String, raw: String): String? = runCatching {
    val value = raw.trim().takeIf { it.isNotEmpty() } ?: return@runCatching null
    val resolved = java.net.URI(playlistUrl).resolve(java.net.URI(value))
    val uri = resolved.toString()
    when (resolved.scheme?.lowercase()) {
        "http", "https" -> uri.takeIf { me.lsong.mytv.domain.provider.isHttpUrl(it) }
        "rtsp", "rtsps", "rtmp", "udp", "rtp", "srt" ->
            uri.takeIf { resolved.rawSchemeSpecificPart.isNotBlank() }
        else -> null
    }
}.getOrNull()
