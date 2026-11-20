package me.lsong.mytv.domain.live

import me.lsong.mytv.domain.provider.ProviderConfig
import me.lsong.mytv.domain.provider.ProviderRecordRef
import me.lsong.mytv.domain.playback.PlaybackResource
import me.lsong.mytv.domain.epg.EpgBinding

/** A raw channel record emitted by a live provider adapter. */
data class ProviderChannelRecord(
    val ref: ProviderRecordRef,
    val name: String,
    val title: String,
    val groupTitle: String,
    val resource: PlaybackResource,
    val logo: String? = null,
    val epgChannelId: String? = null,
)

data class ProviderLiveCatalog(
    val provider: ProviderConfig,
    val records: List<ProviderChannelRecord> = emptyList(),
    val epgUrls: List<String> = emptyList(),
    val error: String? = null,
    val isLoading: Boolean = false,
)

/** Canonical content; group membership and provider records do not define its ID. */
data class LiveChannel(
    val id: String = "",
    val name: String = "",
    val title: String = "",
    val resources: List<PlaybackResource> = emptyList(),
    val logo: String? = null,
    val records: List<ProviderChannelRecord> = emptyList(),
    val epgBindings: List<EpgBinding> = emptyList(),
)

data class ChannelGroup(val id: String, val title: String, val channels: List<LiveChannel>)

data class LiveCatalog(
    val groups: List<ChannelGroup> = emptyList(),
    val error: String? = null,
    val isLoading: Boolean = false,
    val channels: List<LiveChannel> = groups.flatMap { it.channels }.distinctBy { it.id },
)
