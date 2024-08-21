package me.lsong.mytv.ui.widgets

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.tooling.preview.Preview
import me.lsong.mytv.epg.EpgChannel
import me.lsong.mytv.epg.EpgList
import me.lsong.mytv.epg.EpgProgramme
import me.lsong.mytv.iptv.TVChannel
import me.lsong.mytv.iptv.TVChannelList
import me.lsong.mytv.iptv.TVGroup
import me.lsong.mytv.iptv.TVGroupList
import me.lsong.mytv.iptv.TVGroupList.Companion.channels
import me.lsong.mytv.iptv.TVGroupList.Companion.findGroupIndex
import me.lsong.mytv.ui.components.MyTvChannelList
import me.lsong.mytv.ui.components.MyTvGroupList
import me.lsong.mytv.ui.settings.LeanbackSettingsCategories
import me.lsong.mytv.ui.settings.components.LeanbackSettingsCategoryContent
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.ui.toast.LeanbackToastState
import me.lsong.mytv.utils.SP
import me.lsong.mytv.utils.handleLeanbackKeyEvents
import kotlin.math.max

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MyTvMenu(
    modifier: Modifier = Modifier,
    groupListProvider: () -> TVGroupList = { TVGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    channelProvider: () -> TVChannel = { TVChannel() },
    onSelected: (TVChannel) -> Unit = {},
    onUserAction: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val groupList = groupListProvider()
    val currentChannel = channelProvider()
    var focusedIptvGroup by remember {
        mutableStateOf(
            groupList[max(0, groupList.findGroupIndex(currentChannel))]
        )
    }
    val focusedIptvFocusRequester by remember { mutableStateOf(FocusRequester.Default) }
    var isSettingsVisible by remember { mutableStateOf(false) }
    var selectedCategoryProvider by remember { mutableStateOf(LeanbackSettingsCategories.entries.first()) }
    val favoriteChannels by remember { mutableStateOf(SP.iptvChannelFavoriteList) }
    val favoriteGroup = TVGroup(
        title = "我的收藏",
        channels = TVChannelList(groupList.channels.filter { favoriteChannels.contains(it.name) })
    )

    val settingsGroup = TVGroup(
        title = "设置",
        channels = TVChannelList(
            listOf(
                TVChannel(title = "通用", icon = Icons.Default.DisplaySettings),
                TVChannel(title = "直播源", icon = Icons.Default.LiveTv),
                TVChannel(title = "节目单", icon = Icons.Default.Menu),
                TVChannel(title = "关于", icon = Icons.Default.Info),
            )
        )
    )

    Row(modifier = modifier) {
        if (!isSettingsVisible) {
            MyTvGroupList(
                groupListProvider = { TVGroupList(listOf(favoriteGroup) + groupList + listOf(settingsGroup)) },
                focusedGroupProvider = { focusedIptvGroup },
                onGroupFocused = { focusedIptvGroup = it },
                exitFocusRequesterProvider = { focusedIptvFocusRequester },
                onUserAction = onUserAction,
            )
        }
        MyTvChannelList(
            modifier = Modifier
                .handleLeanbackKeyEvents(
                    onLeft = {
                        if (isSettingsVisible) {
                            isSettingsVisible = false
                        }
                    },
                )
                .focusProperties {
                    exit = {
                        if (isSettingsVisible && it == FocusDirection.Left) {
                            isSettingsVisible = false
                            FocusRequester.Cancel
                        } else {
                            FocusRequester.Default
                        }
                    }
                },
            epgListProvider = epgListProvider,
            channelsProvider = { focusedIptvGroup.channels },
            focusedProvider = channelProvider,
            onUserAction = onUserAction,
            onSelected = {
                if (focusedIptvGroup.title == "设置") {
                    isSettingsVisible = true
                    when(it.title) {
                        LeanbackSettingsCategories.APP.title -> selectedCategoryProvider = LeanbackSettingsCategories.APP
                        LeanbackSettingsCategories.IPTV.title -> selectedCategoryProvider = LeanbackSettingsCategories.IPTV
                        LeanbackSettingsCategories.EPG.title -> selectedCategoryProvider = LeanbackSettingsCategories.EPG
                        LeanbackSettingsCategories.ABOUT.title -> selectedCategoryProvider = LeanbackSettingsCategories.ABOUT
                    }
                } else {
                    onSelected(it)
                }
            },
            onFavoriteToggle = {
                if (favoriteChannels.contains(it.name)) {
                    SP.iptvChannelFavoriteList -= it.name
                    LeanbackToastState.I.showToast("取消收藏: ${it.title}")
                } else {
                    SP.iptvChannelFavoriteList += it.name
                    LeanbackToastState.I.showToast("收藏: ${it.title}")
                }
            }
        )
        if (isSettingsVisible) {
            LeanbackSettingsCategoryContent(
                focusedCategoryProvider = {selectedCategoryProvider}
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun MyTvMenuPreview() {
    LeanbackTheme {
        MyTvMenu(
            groupListProvider = { TVGroupList.EXAMPLE },
            epgListProvider = {
                EpgList(TVGroupList.EXAMPLE.channels.map {
                    EpgChannel(
                        id = it.name,
                        programmes = List(5) { idx ->
                            EpgProgramme(
                                startAt = System.currentTimeMillis() + idx * 60 * 60 * 1000L,
                                endAt = System.currentTimeMillis() + (idx + 1) * 60 * 60 * 1000L,
                                title = "${it.title}节目${idx + 1}",
                            )
                        }
                    )
                })
            },
        )
    }
}

