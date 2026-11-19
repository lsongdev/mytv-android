package me.lsong.mytv.domain.epg

/** XMLTV IDs are scoped to their EPG/provider dataset. */
data class EpgBinding(val providerId: String, val remoteChannelId: String)
