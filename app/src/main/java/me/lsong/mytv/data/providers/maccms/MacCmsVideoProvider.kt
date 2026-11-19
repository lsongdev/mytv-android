package me.lsong.mytv.data.providers.maccms

import me.lsong.media.http.MediaEndpoint
import me.lsong.mytv.data.providers.cache.ProviderStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.lsong.media.vod.maccms.*
import me.lsong.mytv.domain.video.*
import me.lsong.mytv.domain.playback.*
import me.lsong.mytv.domain.provider.*

class MacCmsVideoProvider(private val client: MacCmsClient = MacCmsClient(), private val storage: ProviderStorage? = null) : VideoCatalogProvider {
    override suspend fun cachedPage(source: ProviderConfig, query: String, page: Int): VideoPage? =
        storage?.cached(source,"page:$query:$page",MacCmsPage.serializer())?.value?.let { map(source,it) }
    override suspend fun page(source: ProviderConfig, query: String, page: Int, force: Boolean): VideoPage {
        val result = storage?.get(source,"page:$query:$page",MacCmsPage.serializer(),force = force) { client.page(MediaEndpoint(source.url),MacCmsQuery(query,page)) }
            ?: client.page(MediaEndpoint(source.url),MacCmsQuery(query,page))
        return map(source,result)
    }
    private suspend fun map(source: ProviderConfig, result: MacCmsPage): VideoPage = withContext(Dispatchers.Default) {
        VideoPage(result.items.map { it.toRecord(source) },result.page,result.pageCount, storage?.state(source)?.error)
    }
    override suspend fun details(source: ProviderConfig, remoteId: String): ProviderVideoRecord {
        val result = storage?.get(source,"details:$remoteId",MacCmsVideo.serializer(),freshFor = 300_000) { client.details(MediaEndpoint(source.url),remoteId) }
            ?: client.details(MediaEndpoint(source.url),remoteId)
        return withContext(Dispatchers.Default) { result.toRecord(source) }
    }
}

internal fun MacCmsVideo.toRecord(provider: ProviderConfig): ProviderVideoRecord = ProviderVideoRecord(
    ref = ProviderRecordRef(provider.id,id), providerName = provider.name, title = title, year = year,
    kind = classifyVideoCategory(category), category = category, area = area, poster = poster, summary = summary,
    remarks = remarks, doubanId = doubanId, version = version,
    lines = lines.map { line -> ProviderPlaybackLine(line.code,line.title,line.parts.map { part ->
        ProviderVideoPartRecord(part.label, if (part.directlyPlayable) PlaybackResource(part.uri,
            ProviderRecordRef(provider.id,"$id:${line.code}:${part.label}"),provider.name,
            line = PlaybackLineRef(ProviderRecordRef(provider.id,id),line.code,line.title)) else null)
    }) },
)

private fun classifyVideoCategory(category: String): VideoKind = when {
        category.contains("短剧") || category.contains("女频") || category.contains("男频") -> VideoKind.SHORT
        category.contains("动漫") || category.contains("动画") -> VideoKind.ANIME
        category.contains("综艺") -> VideoKind.VARIETY
        category.contains("纪录") || category.contains("记录") -> VideoKind.DOCUMENTARY
        category.contains("篮球") || category.contains("足球") || category.contains("体育") || category.contains("赛事") -> VideoKind.SPORTS
        category.endsWith("片") || category.contains("电影") || category in setOf("动作", "喜剧", "爱情", "科幻", "恐怖", "剧情", "战争", "犯罪") -> VideoKind.MOVIE
        category.contains("剧") -> VideoKind.SERIES
        else -> VideoKind.OTHER
    }
