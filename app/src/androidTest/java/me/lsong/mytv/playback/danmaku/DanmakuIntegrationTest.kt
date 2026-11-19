package me.lsong.mytv.playback.danmaku

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.lsong.media.danmaku.*
import me.lsong.mytv.data.danmaku.DanmakuRepository
import me.lsong.mytv.data.preferences.DanmakuPreferenceStore
import me.lsong.mytv.domain.provider.ProviderRecordRef
import me.lsong.mytv.domain.playback.PlaybackResource
import me.lsong.mytv.domain.video.*
import me.lsong.mytv.ui.danmaku.*
import me.lsong.mytv.ui.theme.TvTheme
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DanmakuIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private class Provider : DanmakuProvider {
        override val id = "tencent"; override val name = "测试弹幕源"
        val windows = mutableListOf<Int>()
        override suspend fun search(keyword: String) = listOf(DanmakuMedia(id,"show",keyword,year=2024))
        override suspend fun episodes(mediaId: String) = listOf(DanmakuEpisode(id,"ep1","第1集",1),DanmakuEpisode(id,"ep3","第3集",3))
        override suspend fun comments(episodeId: String, startSeconds: Int, endSeconds: Int): List<DanmakuComment> {
            windows += startSeconds
            return listOf(DanmakuComment((startSeconds+1)*1000L,1,0xFFFFFF,"测试弹幕"))
        }
    }
    @Test fun explicitEnableMatchesAndPreloadsWindowsWithoutMixingEpisodes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("danmaku-test-${UUID.randomUUID()}",Context.MODE_PRIVATE)
        val directory = File(context.cacheDir,"danmaku-test-${UUID.randomUUID()}")
        val provider = Provider()
        lateinit var model: DanmakuViewModel
        val ref = ProviderRecordRef("vod","1")
        val work = VideoWork("work",listOf(ProviderVideoRecord(ref,"视频源","测试剧","2024",VideoKind.SERIES)))
        val resource = PlaybackResource("https://example.com/1.m3u8",ref,"视频源")
        val details = VideoDetails(work,listOf(VideoPart("s1e1","第1集",1,listOf(resource)),VideoPart("s1e2","第2集",2,listOf(resource))))
        compose.runOnIdle {
            model = DanmakuViewModel(DanmakuRepository(directory) { listOf(provider) },DanmakuPreferenceStore(prefs))
            model.setVideo(VideoPlaybackSelection(details,"s1e1",resource.id))
            assertTrue(provider.windows.isEmpty())
            model.toggle()
        }
        compose.waitUntil(10_000) { model.state.value.comments.isNotEmpty() && !model.state.value.loading }
        assertEquals("ep1",model.state.value.episode?.episodeId)
        assertTrue(provider.windows.containsAll(listOf(0,300)))
        compose.runOnIdle { model.advance(610_000) }
        compose.waitUntil(10_000) { provider.windows.containsAll(listOf(600,900)) }
        compose.runOnIdle { model.setVideo(VideoPlaybackSelection(details,"s1e2",resource.id)) }
        compose.waitUntil(10_000) { !model.state.value.loading }
        assertNull(model.state.value.episode)
        compose.setContent { TvTheme { DanmakuSettingsPanel(model,{}) } }
        compose.onNodeWithTag("danmaku-settings").assertIsDisplayed()
        compose.runOnIdle { model.toggle() }
        directory.deleteRecursively(); prefs.edit().clear().commit()
    }
    @Test fun nativeTextureLayerDrawsCommentsWithHardwareCanvas() {
        lateinit var view: DanmakuTextureView
        compose.setContent {
            AndroidView(factory = { context -> DanmakuTextureView(context).also {
                view = it
                it.submitConfig(DanmakuEngineConfig(textSizePx=48f))
                it.submitItems(listOf(DanmakuItem(1,0,"OhMyTV 弹幕渲染测试",DanmakuMode.SCROLL,0xFFFFFF)))
                it.submitPlayback(0,true,1f,false,false)
            } },modifier = Modifier.fillMaxSize())
        }
        compose.waitUntil(10_000) {
            compose.runOnIdle {
                val bitmap = if (view.isAvailable) view.getBitmap(320,180) else null
                if (bitmap == null) false else {
                    val pixels = IntArray(320*180); bitmap.getPixels(pixels,0,320,0,0,320,180); bitmap.recycle()
                    pixels.any { (it ushr 24) != 0 }
                }
            }
        }
    }
}
