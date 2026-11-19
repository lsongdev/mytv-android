package me.lsong.mytv.domain.identity

import me.lsong.mytv.domain.provider.ProviderRecordRef
import org.junit.Assert.*
import org.junit.Test

class CatalogIdentityTest {
    private fun request(provider: String, name: String) = CatalogIdentityRequest(listOf(ProviderRecordRef(provider, "1")), name)
    @Test fun providerRecordKeepsIdentityAfterRenameAndRestart() {
        var next = 0
        val first = InMemoryCatalogIdentityStore(allocate = { "opaque-${next++}" })
        val id = first.resolveAll("live", listOf(request("a", "original"))).single()
        val restored = InMemoryCatalogIdentityStore(first.snapshot())
        assertEquals(id, restored.resolveAll("live", listOf(request("a", "renamed"))).single())
    }
    @Test fun providerLocalIdsAndNamespacesStaySeparate() {
        val store = InMemoryCatalogIdentityStore()
        val ids = store.resolveAll("live", listOf(request("a", "first"), request("b", "second")))
        assertNotEquals(ids[0], ids[1])
        assertNotEquals(ids[0], store.resolveAll("video", listOf(request("a", "first"))).single())
    }
    @Test fun metadataSplitsNeverAllocateDuplicateIds() {
        var next = 0
        val store = InMemoryCatalogIdentityStore(allocate = { "opaque-${next++}" })
        store.resolveAll("video", listOf(CatalogIdentityRequest(listOf(ProviderRecordRef("a", "1"), ProviderRecordRef("b", "1")), "old")))
        val ids = store.resolveAll("video", listOf(request("a", "edition-a"), request("b", "edition-b")))
        assertEquals(2, ids.toSet().size)
    }
}
