package me.lsong.mytv.ui.browser

import me.lsong.mytv.ui.navigation.Section

import me.lsong.mytv.ui.browser.ContentDrawer
import me.lsong.mytv.ui.theme.TvTheme

import me.lsong.mytv.ui.browser.ContentDrawerState

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import me.lsong.mytv.domain.live.mergeLiveCatalogs
import me.lsong.mytv.domain.live.LiveChannel

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import me.lsong.mytv.domain.live.ProviderLiveCatalog
import me.lsong.mytv.domain.provider.ProviderConfig
import me.lsong.mytv.domain.provider.ProviderKind

@OptIn(ExperimentalTestApi::class)
class ContentDrawerTest {
    @get:Rule val compose = createComposeRule()

    private fun press(key: Key) {
        compose.waitUntil(10_000) { compose.onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test fun scheduleExpandsFourthColumnAndLeftRestoresChannelFocus() {
        val catalog = mergeLiveCatalogs(menuPreviewPlaylists())
        val playing = catalog.channels.first()
        val state = ContentDrawerState(playing)
        val now = System.currentTimeMillis()
        val epg = me.lsong.mytv.domain.epg.EpgList(listOf(me.lsong.mytv.domain.epg.EpgChannel(
            id = playing.name,title = playing.title,programmes = listOf(me.lsong.mytv.domain.epg.EpgProgramme(
                channelId = playing.name,startAt = now-60_000,endAt = now+60_000,title = "测试当前节目")))))
        compose.setContent { TvTheme { ContentDrawer(catalog,playing,state,{}, {}, {},epg = epg,nowMs = now) } }
        compose.waitUntil(5000) { state.liveScheduleId == playing.id }
        compose.onNodeWithTag("drawer-entry:${playing.id}").assertIsFocused()
        val before = compose.onNodeWithTag("drawer-live-channel-column").fetchSemanticsNode().boundsInRoot
        press(Key.DirectionDown)
        compose.runOnIdle { assertNull(state.liveScheduleId); assertEquals(2,state.column) }
        val withoutEpg = compose.onNodeWithTag("drawer-live-channel-column").fetchSemanticsNode().boundsInRoot
        assertEquals(before.left,withoutEpg.left,.5f); assertEquals(before.width,withoutEpg.width,.5f)
        press(Key.DirectionUp)
        compose.waitUntil(5000) { state.liveScheduleId == playing.id }
        val after = compose.onNodeWithTag("drawer-live-channel-column").fetchSemanticsNode().boundsInRoot
        assertEquals(before.left,after.left,.5f); assertEquals(before.width,after.width,.5f)
        press(Key.DirectionRight)
        compose.runOnIdle { assertEquals(3,state.column); assertEquals(playing.id,state.liveScheduleId) }
        compose.onNodeWithTag("drawer-entry:${playing.id}").assertExists()
        compose.onNodeWithText("播放 · ${playing.title}").assertIsFocused()
        press(Key.DirectionLeft)
        compose.runOnIdle { assertEquals(playing.id,state.liveScheduleId); assertEquals(2,state.column) }
        compose.onNodeWithTag("drawer-entry:${playing.id}").assertIsFocused()
    }

    @Test fun browsingAnotherGroupDoesNotPlayUntilConfirmed() {
        val playlists = menuPreviewPlaylists()
        val playing = mergeLiveCatalogs(playlists).channels.first()
        val state = ContentDrawerState(playing)
        val requests = mutableListOf<LiveChannel>()
        compose.setContent { TvTheme { ContentDrawer(mergeLiveCatalogs(playlists), playing, state, requests::add, {}, {}) } }
        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        compose.runOnIdle { assertTrue(requests.isEmpty()) }
        press(Key.DirectionRight)
        val expected = mergeLiveCatalogs(playlists).groups[1].channels.first()
        compose.onNodeWithTag("drawer-entry:${expected.id}").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(expected.id, requests.single().id)
        assertEquals(2, requests.single().resources.map { it.uri }.size)
    }

    @Test fun returningToAColumnRestoresItsFocusedChannel() {
        val playlists = menuPreviewPlaylists()
        val playing = mergeLiveCatalogs(playlists).channels.first()
        val state = ContentDrawerState(playing)
        compose.setContent {
            TvTheme { ContentDrawer(mergeLiveCatalogs(playlists), playing, state, {}, {}, {}) }
        }
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        val third = mergeLiveCatalogs(playlists).channels[2]
        compose.onNodeWithTag("drawer-entry:${third.id}").assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        compose.onNodeWithTag("drawer-entry:${third.id}").assertIsFocused()
    }

    @Test fun changingGroupUpdatesChannelsWithoutMovingFocus() {
        val playlists = menuPreviewPlaylists()
        val playing = mergeLiveCatalogs(playlists).channels.first()
        val state = ContentDrawerState(playing)
        compose.setContent {
            TvTheme { ContentDrawer(mergeLiveCatalogs(playlists), playing, state, {}, {}, {}) }
        }
        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        compose.onNodeWithTag("drawer-entry:group:卫视频道").assertIsFocused()
        compose.onNodeWithTag("drawer-entry:${playing.id}").assertDoesNotExist()
        press(Key.DirectionRight)
        val channel = mergeLiveCatalogs(playlists).groups[1].channels.first()
        compose.onNodeWithTag("drawer-entry:${channel.id}").assertIsFocused()
    }

    @Test fun failedSourceKeepsSettingsAndBackAvailable() {
        val playlists = listOf(ProviderLiveCatalog(ProviderConfig("broken", "失效源", "https://example.com", ProviderKind.M3U), error = "播放源加载失败"))
        val state = ContentDrawerState(null)
        var closed = false
        compose.setContent {
            TvTheme { ContentDrawer(mergeLiveCatalogs(playlists), null, state, {}, { closed = true }, {}) }
        }
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithText("直播源加载失败，请在设置中检查地址").assertIsDisplayed()
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        compose.onNodeWithTag("drawer-entry:live").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("drawer-settings").assertIsFocused()
        press(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(Section.SETTINGS, state.section); assertEquals(1, state.column); assertFalse(closed) }
    }
    @Test fun reopeningDrawerRestoresAnOffscreenChannel() {
        val playlists = menuPreviewPlaylists()
        val playing = mergeLiveCatalogs(playlists).channels.first()
        val state = ContentDrawerState(playing)
        val visible = mutableStateOf(true)
        compose.setContent {
            TvTheme {
                if (visible.value) ContentDrawer(mergeLiveCatalogs(playlists), playing, state, {}, {}, { visible.value = false })
            }
        }
        repeat(7) { press(Key.DirectionDown) }
        val channel = mergeLiveCatalogs(playlists).channels[7]
        compose.onNodeWithTag("drawer-entry:${channel.id}").assertIsFocused()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("drawer-entry:${channel.id}").assertIsFocused()
    }

    @Test fun firstColumnFocusFollowsMenuOrderInBothDirections() {
        val playlists = menuPreviewPlaylists()
        val playing = mergeLiveCatalogs(playlists).channels.first()
        val state = ContentDrawerState(playing)
        compose.setContent {
            TvTheme { ContentDrawer(mergeLiveCatalogs(playlists), playing, state, {}, {}, {}) }
        }
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        compose.onNodeWithTag("drawer-entry:videos").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("drawer-entry:apps").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("drawer-settings").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("drawer-settings").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("drawer-entry:apps").assertIsFocused()
        compose.runOnIdle { assertEquals(Section.APPS, state.section); assertFalse(state.focusSettings) }
        press(Key.DirectionUp)
        compose.onNodeWithTag("drawer-entry:videos").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("drawer-entry:live").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("drawer-entry:live").assertIsFocused()

        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionRight)
        compose.onNodeWithTag("drawer-entry:GENERAL").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("drawer-settings").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("drawer-entry:apps").assertIsFocused()
    }

    @Test fun applicationsEntryOpensListAndLeftRestoresNavigation() {
        val state = ContentDrawerState(null)
        val preparedApps = me.lsong.mytv.data.apps.InstalledAppsSnapshot(apps = listOf(
            me.lsong.mytv.data.apps.InstalledApp(android.content.ComponentName("test.ready","test.ready.Main"),"已预热应用",null),
        ))
        compose.setContent { TvTheme {
            ContentDrawer(mergeLiveCatalogs(emptyList()), null, state, {}, {}, {}, apps = preparedApps)
        } }
        val groupWidth = compose.onNodeWithTag("drawer-live-group-column").fetchSemanticsNode().boundsInRoot.width
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("drawer-entry:apps").assertIsFocused()
        press(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(Section.APPS, state.section); assertEquals(1, state.column) }
        compose.onNodeWithText("已安装应用").assertIsDisplayed()
        val appWidth = compose.onNodeWithTag("drawer-app-list-column").fetchSemanticsNode().boundsInRoot.width
        assertTrue(appWidth > groupWidth)
        compose.waitUntil(10_000) { state.applicationId != null }
        val selected = state.applicationId
        press(Key.DirectionRight)
        compose.onNodeWithTag("drawer-app-details-column").assertIsDisplayed()
        compose.onNodeWithTag("drawer-entry:app-action:OPEN").assertIsFocused()
        assertEquals(appWidth, compose.onNodeWithTag("drawer-app-list-column").fetchSemanticsNode().boundsInRoot.width, 1f)
        press(Key.DirectionLeft)
        compose.onNodeWithTag("drawer-app-details-column").assertDoesNotExist()
        compose.onNodeWithTag("drawer-entry:$selected").assertIsFocused()
        press(Key.DirectionRight)
        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("drawer-app-details-column").assertDoesNotExist()
        compose.onNodeWithTag("drawer-entry:$selected").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("drawer-entry:apps").assertIsFocused()
    }

    @Test fun downAtTheBottomOfChannelsDoesNotEnterSettings() {
        val playlists = menuPreviewPlaylists()
        val playing = mergeLiveCatalogs(playlists).channels.last()
        val state = ContentDrawerState(playing)
        compose.setContent { TvTheme { ContentDrawer(mergeLiveCatalogs(playlists), playing, state, {}, {}, {}) } }
        repeat(4) { press(Key.DirectionDown) }
        compose.onNodeWithTag("drawer-entry:${playing.id}").assertIsFocused()
        compose.onNodeWithTag("drawer-settings").assertIsNotFocused()
        assertEquals(Section.LIVE, state.section)
    }

    @Test fun visibleChannelsDoNotShiftWhenMovingFocusOrReturningToTheColumn() {
        val playlists = menuPreviewPlaylists()
        val playing = mergeLiveCatalogs(playlists).channels.first()
        val fourth = mergeLiveCatalogs(playlists).channels[3]
        val state = ContentDrawerState(playing)
        compose.setContent { TvTheme { ContentDrawer(mergeLiveCatalogs(playlists), playing, state, {}, {}, {}) } }
        val firstNode = compose.onNodeWithTag("drawer-entry:${playing.id}")
        val fourthNode = compose.onNodeWithTag("drawer-entry:${fourth.id}")
        val firstY = firstNode.fetchSemanticsNode().boundsInRoot.top
        val fourthY = fourthNode.fetchSemanticsNode().boundsInRoot.top
        repeat(3) { press(Key.DirectionDown) }
        fourthNode.assertIsFocused()
        assertEquals(firstY, firstNode.fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals(fourthY, fourthNode.fetchSemanticsNode().boundsInRoot.top, 1f)
        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        fourthNode.assertIsFocused()
        assertEquals(fourthY, fourthNode.fetchSemanticsNode().boundsInRoot.top, 1f)
    }

}
