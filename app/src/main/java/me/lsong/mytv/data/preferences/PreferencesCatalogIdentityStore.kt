package me.lsong.mytv.data.preferences

import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.lsong.mytv.domain.identity.*
import java.util.UUID

/** Load on the first catalog merge, not synchronously before Android creates its first window. */
class PreferencesCatalogIdentityStore(private val preferences: SharedPreferences) : CatalogIdentityStore {
    companion object { const val KEY = "CATALOG_IDENTITIES_V1" }
    private var saved: CatalogIdentitySnapshot? = null
    private val store by lazy {
        val snapshot = preferences.getString(KEY, null)?.let { Json.decodeFromString<CatalogIdentitySnapshot>(it) }
            ?: CatalogIdentitySnapshot()
        saved = snapshot
        InMemoryCatalogIdentityStore(snapshot, allocate = { UUID.randomUUID().toString() })
    }

    @Synchronized
    override fun resolveAll(namespace: String, requests: List<CatalogIdentityRequest>): List<String> {
        val result = store.resolveAll(namespace, requests)
        val current = store.snapshot()
        if (current != saved) {
            preferences.edit().putString(KEY, Json.encodeToString(current)).apply()
            saved = current
        }
        return result
    }
}
