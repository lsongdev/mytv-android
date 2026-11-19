package me.lsong.media.epg

import kotlinx.serialization.Serializable

@Serializable data class EpgDocument(val channels: List<EpgChannel>)
@Serializable data class EpgChannel(val id: String, val title: String, val programmes: List<EpgProgramme>, val names: List<String> = emptyList(), val icon: String? = null)
@Serializable data class EpgProgramme(val channelId: String, val title: String, val startsAt: Long, val endsAt: Long)
