package me.lsong.mytv.data.epg

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

data class EpgFeed(val url: String, val owners: Set<String> = emptySet(), val fallback: Boolean = false) {
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
    val result = feeds.map { EpgFeed(it.key,it.value) }
    return if (options.useDefaultEpg && AppConstants.EPG_XML_URL !in feeds) result + EpgFeed(AppConstants.EPG_XML_URL,fallback = true) else result
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
                val documentsMutex = Mutex()
                val publishMutex = Mutex()
                // Reuse each feed's prepared EPG rows across progress/loading emissions.
                val prepared = mutableMapOf<String,Pair<EpgDocument,List<EpgChannel>>>()
                feeds.forEach { feed -> storage.cached(feed.provider,"epg",EpgDocument.serializer(),maxBytes = 32_000_000)?.value?.let { documents[feed.url] = it } }
                fun hasGuide(document: EpgDocument?) = document?.channels?.any { channel -> channel.programmes.any { it.endsAt > System.currentTimeMillis() } } == true
                suspend fun primaryAvailable() = documentsMutex.withLock {
                    feeds.filterNot { it.fallback }.any { hasGuide(documents[it.url]) }
                }
                suspend fun publish(loading: Boolean) = publishMutex.withLock {
                    val snapshot = documentsMutex.withLock { documents.toMap() }
                    val primaryReady = feeds.filterNot { it.fallback }.any { hasGuide(snapshot[it.url]) }
                    val activeFeeds = feeds.filter { !it.fallback || !primaryReady }
                    val channels = withContext(Dispatchers.Default) {
                        activeFeeds.flatMap { feed ->
                            val document = snapshot[feed.url] ?: return@flatMap emptyList<EpgChannel>()
                            val old = prepared[feed.url]
                            if (old != null && old.first === document) old.second
                            else {
                                val rows = document.channels.flatMap { channel ->
                                    (if (feed.owners.isEmpty()) listOf(null) else feed.owners.toList()).map { owner ->
                                        EpgChannel(channel.id,owner,channel.title,
                                            channel.programmes.map { EpgProgramme(it.channelId,it.startsAt,it.endsAt,it.title) },
                                            channel.names,channel.icon?.let {
                                                me.lsong.mytv.data.providers.m3u.resolvePlaylistUrl(feed.url,it)
                                            })
                                    }
                                }
                                prepared[feed.url] = document to rows
                                rows
                            }
                        }
                    }
                    mutable.value = EpgState(EpgList(channels), feeds, loading)
                }
                publish(feeds.isNotEmpty())
                var force = revision != consumedRevision
                consumedRevision = revision
                while (currentCoroutineContext().isActive) {
                    val permits = Semaphore(2)
                    suspend fun fetch(targets: List<EpgFeed>) = coroutineScope { targets.map { feed -> launch { permits.withPermit {
                        val document = try { storage.get(feed.provider,"epg",EpgDocument.serializer(),freshFor = options.refreshHours * 3600_000L,force = force,maxBytes = 32_000_000) {
                            val document = client.fetch(MediaEndpoint(feed.url))
                            val now = System.currentTimeMillis()
                            document.copy(channels = document.channels.map { channel -> channel.copy(programmes = channel.programmes.filter { it.endsAt > now - 86400_000 && it.startsAt < now + 3 * 86400_000L }) })
                        } } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                        if (document != null) {
                            documentsMutex.withLock { documents[feed.url] = document }
                            publish(true)
                        }
                    } } }.joinAll() }
                    fetch(feeds.filterNot { it.fallback })
                    if (!primaryAvailable()) fetch(feeds.filter { it.fallback })
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
