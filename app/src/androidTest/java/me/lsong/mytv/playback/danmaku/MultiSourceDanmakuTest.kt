@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package me.lsong.mytv.playback.danmaku

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import me.lsong.media.danmaku.*
import me.lsong.mytv.data.danmaku.DanmakuRepository
import me.lsong.mytv.data.preferences.*
import me.lsong.mytv.domain.playback.PlaybackResource
import me.lsong.mytv.domain.provider.ProviderRecordRef
import me.lsong.mytv.domain.video.*
import me.lsong.mytv.ui.danmaku.*
import me.lsong.mytv.ui.theme.TvTheme
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MultiSourceDanmakuTest {
    @get:Rule val compose = createComposeRule()
    @org.junit.Before fun keepDisplayAwake() = keepDanmakuTestDisplayAwake()
    private class Provider(override val id: String, val gate: CompletableDeferred<Unit>? = null, val fails: Boolean = false, val ambiguous: Boolean = false) : DanmakuProvider {
        override val name get() = id
        val requests = CopyOnWriteArrayList<Pair<String,Int>>()
        override suspend fun search(keyword: String): List<DanmakuMedia> {
            gate?.await(); if (fails) error("offline")
            return (if (ambiguous) listOf("show-a","show-b") else listOf("show")).map { DanmakuMedia(id,it,keyword,year = 2024) }
        }
        override suspend fun episodes(mediaId: String) = listOf(DanmakuEpisode(id,"ep1","第1集",1),DanmakuEpisode(id,"ep3","第3集",3))
        override suspend fun comments(episodeId: String, startSeconds: Int, endSeconds: Int): List<DanmakuComment> {
            requests += episodeId to startSeconds
            return listOf(DanmakuComment(startSeconds*1000L+1000,1,0xFFFFFF,"shared"),
                DanmakuComment(startSeconds*1000L+2000,1,0xFFFFFF,"only_$id"))
        }
    }
    private fun remoteClick(tag: String) {
        val node = compose.onNodeWithTag(tag)
        node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle(); instrumentation.waitForIdleSync(); Thread.sleep(250)
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(instrumentation.targetContext.cacheDir,name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    private val ref = ProviderRecordRef("vod","one")
    private val resource = PlaybackResource("https://example.com/movie",ref,"vod")
    private val details = VideoDetails(VideoWork("work",listOf(ProviderVideoRecord(ref,"vod","测试剧","2024",VideoKind.SERIES))),
        listOf(VideoPart("s1e1","第1集",1,listOf(resource)),VideoPart("s1e2","第2集",2,listOf(resource))))
    private fun selection(part: String = "s1e1", work: String = "work") = VideoPlaybackSelection(details.copy(work = details.work.copy(id = work)),part,resource.id)
    private fun fixture(providers: List<Provider>, legacy: Boolean = false, block: (DanmakuViewModel, DanmakuPreferenceStore, () -> DanmakuViewModel) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "multi-danmaku-test-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(id,Context.MODE_PRIVATE)
        val directory = File(context.cacheDir,id)
        val models = mutableListOf<ViewModelStore>()
        if (legacy) {
            prefs.edit().putString("DANMAKU_OPTIONS_V1","""{"enabled":true,"offsetMs":1500,"providers":["tencent","bilibili"]}""")
                .putString("DANMAKU_BINDINGS_V1","""{"work|s1e1":{"providerId":"tencent","mediaId":"show","episodeId":"ep3"}}""").commit()
        } else DanmakuPreferenceStore(prefs).save(DanmakuPreferences(enabled = true,providers = providers.map { it.id }.toSet()))
        fun create(): DanmakuViewModel {
            lateinit var model: DanmakuViewModel
            compose.runOnIdle {
                model = DanmakuViewModel(DanmakuRepository(directory) { providers },DanmakuPreferenceStore(prefs))
                models += ViewModelStore().also { it.put("danmaku",model) }
                model.setVideo(selection())
            }
            return model
        }
        try { block(create(),DanmakuPreferenceStore(prefs),::create) }
        finally { compose.runOnIdle { models.forEach(ViewModelStore::clear) }; directory.deleteRecursively(); prefs.edit().clear().commit() }
    }
    @Test fun slowAndFailedPlatformsDoNotHoldBackMergedComments() {
        val gate = CompletableDeferred<Unit>()
        val fast = Provider("tencent"); val slow = Provider("bilibili",gate); val failed = Provider("mgtv",fails = true)
        fixture(listOf(fast,slow,failed)) { model, _, _ ->
            compose.waitUntil(10_000) { model.state.value.comments.any { it.content == "only_tencent" } }
            assertTrue(model.state.value.sources.first { it.providerId == "bilibili" }.matching)
            compose.runOnIdle { gate.complete(Unit) }
            compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.comments.any { it.content == "only_bilibili" } }
            assertEquals(2,model.state.value.activeSources.size)
            assertEquals(2,model.state.value.comments.count { it.content == "shared" })
            assertTrue(model.state.value.sources.first { it.providerId == "mgtv" }.message.contains("失败"))
        }
    }
    @Test fun sourceRowKeysShiftOnlyThatSourceAndOkTogglesItWithoutRefetchingOthers() {
        val a = Provider("tencent"); val b = Provider("bilibili")
        fixture(listOf(a,b)) { model, _, _ ->
            compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.activeSources.size == 2 }
            compose.setContent { TvTheme { DanmakuSettingsPanel(model,{}) } }
            val row = compose.onNodeWithTag("danmaku-option:tencent:show:ep1")
            row.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            row.assertIsFocused()
            row.performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionRight); pressKey(Key.DirectionLeft) }
            compose.runOnIdle {
                assertEquals(1000L,model.state.value.sources.first { it.providerId == "tencent" }.offsetMs)
                assertEquals(0L,model.state.value.sources.first { it.providerId == "bilibili" }.offsetMs)
            }
            row.assertIsFocused()
            row.performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(5000) { model.state.value.comments.none { it.content == "only_tencent" } }
            assertTrue(model.state.value.comments.any { it.content == "only_bilibili" })
            val bRequests = b.requests.size
            row.performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(5000) { model.state.value.comments.any { it.content == "only_tencent" && it.timeMs == 3000L } }
            assertEquals(bRequests,b.requests.size)
            compose.onNodeWithTag("danmaku-tab:0").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithTag("danmaku-tab:0").performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithText("显示区域").assertExists()
            screenshot("danmaku-style-preview.png")
            compose.onAllNodesWithText("时间偏移",substring = true).assertCountEquals(0)
            compose.onNodeWithTag("danmaku-tab:1").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithTag("danmaku-tab:1").performKeyInput { pressKey(Key.DirectionCenter) }
            screenshot("danmaku-sources-preview.png")
            compose.onNodeWithTag("danmaku-heading:tencent").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.RequestFocus))
            remoteClick("danmaku-expand:tencent")
            remoteClick("danmaku-option:tencent:show:ep3")
            compose.onNodeWithTag("danmaku-option:tencent:show:ep3").assertIsSelected().assertIsFocused()
            compose.onNodeWithTag("danmaku-option:tencent:show:ep1").assertIsNotSelected()
            screenshot("danmaku-flat-matching-preview.png")
            compose.runOnIdle {
                assertEquals("ep3",model.state.value.sources.first { it.providerId == "tencent" }.episode?.episodeId)
                assertEquals("ep1",model.state.value.sources.first { it.providerId == "bilibili" }.episode?.episodeId)
            }
        }
    }
    @Test fun aFlatChoiceSelectsFilmAndEpisodeTogetherWithOneChoicePerProvider() {
        fixture(listOf(Provider("tencent",ambiguous = true),Provider("bilibili"))) { model, _, _ ->
            compose.waitUntil(10_000) { !model.state.value.loading }
            assertNull(model.state.value.sources.first { it.providerId == "tencent" }.episode)
            compose.runOnIdle {
                val option = model.state.value.sources.first { it.providerId == "tencent" }.options.first { it.candidate.media.mediaId == "show-b" && it.episode.episodeId == "ep3" }
                model.chooseMatch("tencent",option.id)
            }
            compose.waitUntil(10_000) { model.state.value.activeSources.size == 2 && !model.state.value.loading }
            assertEquals("show-b",model.state.value.sources.first { it.providerId == "tencent" }.selected?.media?.mediaId)
            assertEquals("ep3",model.state.value.sources.first { it.providerId == "tencent" }.episode?.episodeId)
            compose.runOnIdle {
                val option = model.state.value.sources.first { it.providerId == "tencent" }.options.first { it.candidate.media.mediaId == "show-a" && it.episode.episodeId == "ep1" }
                model.chooseMatch("tencent",option.id)
            }
            assertEquals("show-a",model.state.value.sources.first { it.providerId == "tencent" }.selected?.media?.mediaId)
            assertEquals("ep1",model.state.value.sources.first { it.providerId == "bilibili" }.episode?.episodeId)
        }
    }
    @Test fun bindingsAndOffsetsRestorePerSourceAndDoNotLeakIntoAnotherFilm() {
        fixture(listOf(Provider("tencent"),Provider("bilibili"))) { model, _, create ->
            compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.activeSources.size == 2 }
            compose.runOnIdle {
                model.adjustOffset("tencent",2000)
                val source = model.state.value.sources.first { it.providerId == "tencent" }
                model.chooseMatch("tencent",source.options.first { it.episode.episodeId == "ep3" }.id)
            }
            val restored = create()
            compose.waitUntil(10_000) { !restored.state.value.loading && restored.state.value.activeSources.size == 2 }
            assertEquals("ep3",restored.state.value.sources.first { it.providerId == "tencent" }.episode?.episodeId)
            assertEquals("ep1",restored.state.value.sources.first { it.providerId == "bilibili" }.episode?.episodeId)
            assertEquals(2000L,restored.state.value.sources.first { it.providerId == "tencent" }.offsetMs)
            compose.runOnIdle { restored.setVideo(selection(work = "other")) }
            assertEquals(0L,restored.state.value.sources.first { it.providerId == "tencent" }.offsetMs)
            compose.runOnIdle { restored.setVideo(selection(part = "s1e2")) }
            compose.waitUntil(10_000) { !restored.state.value.loading }
            assertTrue(restored.state.value.sources.all { it.episode == null })
        }
    }
    @Test fun legacySingleBindingAndGlobalOffsetMigrateWithoutCrossProviderEpisodeIds() {
        fixture(listOf(Provider("tencent"),Provider("bilibili")),legacy = true) { model, _, _ ->
            compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.activeSources.size == 2 }
            assertEquals("ep3",model.state.value.sources.first { it.providerId == "tencent" }.episode?.episodeId)
            assertEquals("ep1",model.state.value.sources.first { it.providerId == "bilibili" }.episode?.episodeId)
            assertTrue(model.state.value.sources.all { it.offsetMs == 1500L })
        }
    }
}
