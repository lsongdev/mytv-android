package me.lsong.mytv.data.epg

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.lsong.media.epg.EpgClient
import me.lsong.media.epg.EpgDocument
import me.lsong.media.http.MediaEndpoint
import me.lsong.mytv.app.AppConstants
import me.lsong.mytv.data.preferences.LiveOptions
import me.lsong.mytv.data.preferences.LivePreferences
import me.lsong.mytv.data.providers.cache.ProviderStorage
import me.lsong.mytv.domain.epg.*
import me.lsong.mytv.domain.live.ProviderLiveCatalog
import me.lsong.mytv.domain.provider.*

data class EpgFeed(val url: String, val owners: Set<String> = emptySet()) {
    val provider get() = ProviderConfig(id = "epg:" + url, name = "节目单", url = url, kind = ProviderKind.M3U)
}
data class EpgState(val programmes: EpgList = EpgList(), val feeds: List<EpgFeed> = emptyList(), val loading: Boolean = false)

internal fun epgFeeds(catalogs: List<ProviderLiveCatalog>, options: LiveOptions): List<EpgFeed> {
    val feeds = linkedMapOf<String,MutableSet<String>>()
    options.epgUrls.filter(::isHttpUrl).forEach { feeds[it] = linkedSetOf() }
    if (options.discoverEpg) catalogs.forEach { catalog -> catalog.epgUrls.filter(::isHttpUrl).forEach { url ->
        // An explicitly configured feed also applies globally.
        if (url !in options.epgUrls) feeds.getOrPut(url) { linkedSetOf() }.add(catalog.provider.id)
    } }
    if (feeds.isEmpty() && options.useDefaultEpg) feeds[AppConstants.EPG_XML_URL] = linkedSetOf()
    return feeds.map { EpgFeed(it.key,it.value) }
}

/** Cache first, independent feed failures, and bounded parallel refresh outside composition. */
class EpgRepository(val preferences: LivePreferences, val storage: ProviderStorage,
    private val client: EpgClient = EpgClient(me.lsong.media.http.MediaHttpClient(maxResponseBytes = 64_000_000)),
) {
    private val mutable = MutableStateFlow(EpgState())
    val state = mutable.asStateFlow()
    private val refresh = MutableStateFlow(0)
    fun refresh() { refresh.value++ }
    suspend fun observe(catalogs: Flow<List<ProviderLiveCatalog>>) {
        var consumedRevision = 0
        combine(catalogs,preferences.options,refresh) { sources,options,revision -> Triple(epgFeeds(sources,options),options,revision) }
            .distinctUntilChanged().collectLatest { (feeds,options,revision) ->
                if (!options.epgEnabled) { mutable.value = EpgState(); return@collectLatest }
                val documents = linkedMapOf<String,EpgDocument>()
                feeds.forEach { feed -> storage.cached(feed.provider,"epg",EpgDocument.serializer(),maxBytes = 32_000_000)?.value?.let { documents[feed.url] = it } }
                suspend fun publish(loading: Boolean) { mutable.value = EpgState(withContext(Dispatchers.Default) {
                    EpgList(feeds.flatMap { feed -> documents[feed.url]?.channels.orEmpty().flatMap { channel ->
                        (if (feed.owners.isEmpty()) listOf(null) else feed.owners.toList()).map { owner ->
                            EpgChannel(channel.id,owner,channel.title,channel.programmes.map { EpgProgramme(it.channelId,it.startsAt,it.endsAt,it.title) },channel.names,channel.icon?.let { me.lsong.mytv.data.providers.m3u.resolvePlaylistUrl(feed.url,it) })
                        }
                    } })
                }, feeds, loading) }
                publish(feeds.isNotEmpty())
                var force = revision != consumedRevision
                consumedRevision = revision
                while (currentCoroutineContext().isActive) {
                    val permits = Semaphore(2)
                    val results = coroutineScope { feeds.map { feed -> async { permits.withPermit {
                        try { feed.url to storage.get(feed.provider,"epg",EpgDocument.serializer(),freshFor = options.refreshHours * 3600_000L,force = force,maxBytes = 32_000_000) {
                            val document = client.fetch(MediaEndpoint(feed.url))
                            val now = System.currentTimeMillis()
                            document.copy(channels = document.channels.map { channel -> channel.copy(programmes = channel.programmes.filter { it.endsAt > now - 86400_000 && it.startsAt < now + 3 * 86400_000L }) })
                        } } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { feed.url to null }
                    } } }.awaitAll() }
                    results.forEach { (url,document) -> if (document != null) documents[url] = document }
                    publish(false); force = false
                    // Sleep until the earliest feed expires or its backoff ends. Avoid reading
                    // and deserializing large cached XMLTV documents every minute.
                    val now = System.currentTimeMillis()
                    val refreshAfter = feeds.minOfOrNull { feed ->
                        val status = storage.state(feed.provider)
                        if (status?.error != null) status.retryAt - now
                        else (status?.successAt?.takeIf { it > 0 } ?: now) + options.refreshHours * 3600_000L - now
                    } ?: options.refreshHours * 3600_000L
                    delay(refreshAfter.coerceAtLeast(15_000))
                }
            }
    }
}
