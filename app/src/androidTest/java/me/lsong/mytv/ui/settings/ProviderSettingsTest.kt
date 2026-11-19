package me.lsong.mytv.ui.settings

import me.lsong.mytv.ui.navigation.Section

import me.lsong.mytv.domain.live.LiveCatalog
import me.lsong.mytv.domain.live.mergeLiveCatalogs
import me.lsong.mytv.ui.browser.ContentDrawer
import me.lsong.mytv.ui.theme.TvTheme

import android.content.Context
import androidx.compose.runtime.*
import androidx.test.espresso.Espresso

import me.lsong.mytv.ui.browser.ContentDrawerState
import me.lsong.mytv.ui.browser.menuPreviewPlaylists
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import me.lsong.mytv.domain.provider.*
import me.lsong.mytv.data.preferences.*

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalTestApi::class)
class ProviderSettingsTest {
    @get:Rule val compose = createComposeRule()

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
    }

    private fun showSettings(model: ProviderSettingsViewModel, category: SettingsCategory = SettingsCategory.LIVE) {
        val state = ContentDrawerState(null).apply {
            section = Section.SETTINGS; focusSettings = true; settingsCategory = category; column = 2
        }
        compose.setContent {
            TvTheme {
                ContentDrawer(LiveCatalog(), null, state, {}, {}, { SettingsContent(it, model) })
            }
        }
    }

    private fun repository(): PreferencesProviderConfigRepository {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return PreferencesProviderConfigRepository(context.getSharedPreferences("source-test-${UUID.randomUUID()}", Context.MODE_PRIVATE))
    }

    @Test fun iptvAndVodSettingsPersistIndependently() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("source-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val repo = PreferencesProviderConfigRepository(prefs)
        repo.save(ProviderConfig("iptv", "家庭直播", "http://192.168.6.1/iptv.m3u", ProviderKind.M3U))
        repo.save(ProviderConfig("vod", "影视资源", "https://example.com/api.php/provide/vod", ProviderKind.MAC_CMS, false))
        val restored = PreferencesProviderConfigRepository(prefs).providers.value
        assertEquals(2, restored.size)
        assertEquals("家庭直播", restored[0].name)
        assertFalse(restored[1].isEnabled)
        repo.delete("iptv")
        assertEquals("vod", PreferencesProviderConfigRepository(prefs).providers.value.single().id)
    }

    @Test fun oldIptvUrlsMigrateOnceAndDeletionStaysDeleted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("source-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("IPTV_SOURCE_URL_LIST", setOf("http://192.168.6.1/iptv.m3u")).commit()
        val repo = PreferencesProviderConfigRepository(prefs)
        assertEquals(ProviderKind.M3U, repo.providers.value.single().kind)
        repo.delete(repo.providers.value.single().id)
        assertTrue(PreferencesProviderConfigRepository(prefs).providers.value.isEmpty())
    }

    @Test fun invalidOrDuplicateAddressesDoNotReplaceSavedConfiguration() {
        val repo = repository()
        val valid = ProviderConfig("iptv", "家庭直播", "http://192.168.6.1/iptv.m3u", ProviderKind.M3U)
        repo.save(valid)
        assertTrue(runCatching { repo.save(valid.copy(id = "other")) }.isFailure)
        assertTrue(runCatching { repo.save(valid.copy(url = "/Desktop/vod.json")) }.isFailure)
        assertEquals(listOf(valid), repo.providers.value)
    }

    @Test fun vodFormSavesAnEndpointAndEnabledState() {
        val repo = repository()
        val model = ProviderSettingsViewModel(repo)
        showSettings(model, SettingsCategory.VIDEOS)
        click("add-source")
        compose.onNodeWithTag("source-name").performTextInput("测试影视源")
        compose.onNodeWithTag("source-url").performTextInput("https://example.com/api.php/provide/vod")
        compose.onNodeWithTag("source-enabled").performScrollTo(); click("source-enabled")
        compose.onNodeWithTag("save-source").performScrollTo(); click("save-source")
        compose.runOnIdle {
            val source = repo.providers.value.single()
            assertEquals(ProviderKind.MAC_CMS, source.kind)
            assertEquals("测试影视源", source.name)
            assertFalse(source.isEnabled)
        }
        compose.onNodeWithText("测试影视源").assertIsDisplayed()
    }

    @Test fun editingAndDeletingAConfiguredSourceUpdatesTheList() {
        val repo = repository()
        repo.save(ProviderConfig("vod", "旧名称", "https://example.com/vod", ProviderKind.MAC_CMS))
        val model = ProviderSettingsViewModel(repo)
        showSettings(model, SettingsCategory.VIDEOS)
        click("source:vod")
        compose.onNodeWithTag("source-name").performTextReplacement("新名称")
        compose.onNodeWithTag("save-source").performScrollTo(); click("save-source")
        compose.onNodeWithText("新名称").assertIsDisplayed()
        click("source:vod")
        compose.onNodeWithTag("delete-source").performScrollTo(); click("delete-source")
        compose.onNodeWithTag("confirm-delete-source").performScrollTo(); click("confirm-delete-source")
        compose.runOnIdle { assertTrue(repo.providers.value.isEmpty()) }
        compose.onNodeWithText("还没有影视源").assertIsDisplayed()
    }

    @Test fun malformedEndpointShowsValidationWithoutSaving() {
        val repo = repository()
        showSettings(ProviderSettingsViewModel(repo))
        click("add-source")
        compose.onNodeWithTag("source-name").performTextInput("测试源")
        compose.onNodeWithTag("source-url").performTextInput("not-a-url")
        compose.onNodeWithTag("save-source").performScrollTo(); click("save-source")
        compose.onNodeWithTag("source-error").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(repo.providers.value.isEmpty()) }
    }
    @Test fun menuSettingsAreRenderedInTheSecondAndThirdColumns() {
        val repo = repository()
        val model = ProviderSettingsViewModel(repo)
        val playlists = menuPreviewPlaylists()
        val playing = mergeLiveCatalogs(playlists).channels.first()
        val state = ContentDrawerState(playing)
        var closed = false
        compose.setContent {
            TvTheme {
                ContentDrawer(mergeLiveCatalogs(playlists), playing, state, {}, { closed = true }, { SettingsContent(it, model) })
            }
        }
        click("drawer-settings")
        compose.onNodeWithTag("drawer-entry:LIVE").assertIsDisplayed()
        compose.onNodeWithTag("drawer-entry:GENERAL").assertIsFocused()
        compose.onNodeWithTag("drawer-entry:GENERAL").assertTextContains("常规")
        click("drawer-entry:LIVE")
        compose.onNodeWithTag("add-source").assertIsDisplayed()
        Espresso.pressBack()
        compose.runOnIdle { assertTrue(closed) }
    }
}
