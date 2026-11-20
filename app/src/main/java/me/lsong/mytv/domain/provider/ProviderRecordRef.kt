package me.lsong.mytv.domain.provider

/** Provider-local identity. Never compare remote IDs across providers. */
@kotlinx.serialization.Serializable
data class ProviderRecordRef(val providerId: String, val remoteId: String) {
    val key: String get() = "${providerId.length}:$providerId$remoteId"
}
