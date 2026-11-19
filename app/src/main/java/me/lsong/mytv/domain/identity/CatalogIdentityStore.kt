package me.lsong.mytv.domain.identity

import kotlinx.serialization.Serializable
import me.lsong.mytv.domain.provider.ProviderRecordRef
import java.util.UUID

data class CatalogIdentityRequest(val records: List<ProviderRecordRef>, val matchKey: String)

interface CatalogIdentityStore {
    fun resolveAll(namespace: String, requests: List<CatalogIdentityRequest>): List<String>
}

@Serializable
data class CatalogIdentitySnapshot(val bindings: Map<String, String> = emptyMap())

/** Deterministic standalone store for previews/tests; persistent stores allocate opaque IDs. */
open class InMemoryCatalogIdentityStore(
    snapshot: CatalogIdentitySnapshot = CatalogIdentitySnapshot(),
    private val allocate: (String) -> String = { UUID.nameUUIDFromBytes(it.toByteArray()).toString() },
) : CatalogIdentityStore {
    private val bindings = snapshot.bindings.toMutableMap()
    @Synchronized
    override fun resolveAll(namespace: String, requests: List<CatalogIdentityRequest>): List<String> {
        val assigned = mutableSetOf<String>()
        return requests.map { request ->
            val refs = request.records.map { "$namespace:record:${it.key}" }
            val alias = "$namespace:match:${request.matchKey}"
            // A richer refresh can split an earlier ambiguous match. Never give two
            // distinct groups the same ID merely because they once shared a record.
            val existing = (listOfNotNull(bindings[alias]) + refs.mapNotNull { bindings[it] }).firstOrNull { it !in assigned }
            val id = existing ?: allocate(alias)
            check(assigned.add(id)) { "Identity allocator returned a duplicate ID" }
            (refs + alias).forEach { bindings[it] = id }
            id
        }
    }
    @Synchronized fun snapshot() = CatalogIdentitySnapshot(bindings.toMap())
}
