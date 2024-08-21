package me.lsong.mytv.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import me.lsong.mytv.epg.EpgChannel
import me.lsong.mytv.epg.EpgList
import me.lsong.mytv.iptv.TVChannel
import me.lsong.mytv.iptv.TVChannelList
import me.lsong.mytv.iptv.TVGroup
import me.lsong.mytv.iptv.TVGroupList
import me.lsong.mytv.iptv.TVSource
import me.lsong.mytv.epg.EpgRepository
import me.lsong.mytv.iptv.IptvRepository
import me.lsong.mytv.utils.Constants
import me.lsong.mytv.utils.SP

class MainViewModel : ViewModel() {
    private val iptvRepository = IptvRepository()
    private val epgRepository = EpgRepository()

    private val _uiState = MutableStateFlow<LeanbackMainUiState>(LeanbackMainUiState.Loading())
    val uiState: StateFlow<LeanbackMainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            refreshData()
        }
    }

    private suspend fun refreshData() {
        var epgUrls = emptyArray<String>()
        var iptvUrls = emptyArray<String>()

        // SP.iptvSourceUrls = setOf(
        //     "https://raw.githubusercontent.com/YueChan/Live/main/IPTV.m3u",
        //     "https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/ipv6.m3u",
        //     "https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/itv.m3u",
        //     "https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/index.m3u",
        // )

        if (SP.iptvSourceUrls.isNotEmpty()) {
            iptvUrls += SP.iptvSourceUrls
        }
        if (iptvUrls.isEmpty()) {
            iptvUrls += Constants.IPTV_SOURCE_URL
        }
        flow {
            val allSources = mutableListOf<TVSource>()
            iptvUrls.forEachIndexed { index, url ->
                emit(LoadingState(index + 1, iptvUrls.size, url, "IPTV"))
                val m3u = fetchDataWithRetry { iptvRepository.getChannelSourceList(sourceUrl = url) }
                allSources.addAll(m3u.sources)
                if (m3u.epgUrl != null)
                    epgUrls += (m3u.epgUrl).toString()
            }
            if (epgUrls.isEmpty()) {
                epgUrls += Constants.EPG_XML_URL
            }
            val epgChannels = mutableListOf<EpgChannel>()
            epgUrls.distinct().toTypedArray().forEachIndexed { index, url ->
                emit(LoadingState(index + 1, epgUrls.size, url, "EPG"))
                val epg = fetchDataWithRetry { epgRepository.getEpgList(url) }
                epgChannels.addAll(epg.value)
            }
            val groupList = processChannelSources(allSources)
            emit(DataResult(groupList, EpgList(epgChannels.distinctBy{ it.id })))
        }
            .catch { error ->
                _uiState.value = LeanbackMainUiState.Error(error.message)
                SP.iptvSourceUrlHistoryList -= iptvUrls.toList()
            }
            .collect { result ->
                when (result) {
                    is LoadingState -> {
                        _uiState.value =
                            LeanbackMainUiState.Loading("获取${result.type}数据(${result.currentSource}/${result.totalSources})...")
                    }
                    is DataResult -> {
                        Log.d("epg","合并节目单完成：${result.epgList.size}")
                        _uiState.value = LeanbackMainUiState.Ready(
                            tvGroupList = result.groupList,
                            epgList = result.epgList
                        )
                        SP.iptvSourceUrlHistoryList += iptvUrls.toList()
                    }
                }
            }
    }

    private suspend fun <T> fetchDataWithRetry(fetch: suspend () -> T): T {
        var attempt = 0
        while (attempt < Constants.HTTP_RETRY_COUNT) {
            try {
                return fetch()
            } catch (e: Exception) {
                attempt++
                if (attempt >= Constants.HTTP_RETRY_COUNT) throw e
                delay(Constants.HTTP_RETRY_INTERVAL)
            }
        }
        throw IllegalStateException("Failed to fetch data after $attempt attempts")
    }

    private fun processChannelSources(sources: List<TVSource>): TVGroupList {
        val sourceList = TVChannelList(sources.groupBy { it.name }.map { channelEntry ->
            TVChannel(
                name = channelEntry.key,
                title = channelEntry.value.first().title,
                sources = channelEntry.value)
        })
        val groupList = TVGroupList(sourceList.groupBy { it.groupTitle ?: "其他" }.map { groupEntry ->
            TVGroup(title = groupEntry.key, channels = TVChannelList(groupEntry.value))
        })
        return groupList
    }

    private data class LoadingState(val currentSource: Int, val totalSources: Int, val currentUrl: String, val type: String)
    private data class DataResult(val groupList: TVGroupList, val epgList: EpgList)
}

sealed interface LeanbackMainUiState {
    data class Loading(val message: String? = null) : LeanbackMainUiState
    data class Error(val message: String? = null) : LeanbackMainUiState
    data class Ready(
        val tvGroupList: TVGroupList = TVGroupList(),
        val epgList: EpgList = EpgList(),
    ) : LeanbackMainUiState
}