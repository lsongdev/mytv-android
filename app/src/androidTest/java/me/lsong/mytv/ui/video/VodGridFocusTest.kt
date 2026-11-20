package me.lsong.mytv.ui.video

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import me.lsong.mytv.domain.provider.ProviderRecordRef
import me.lsong.mytv.domain.video.*
import me.lsong.mytv.ui.theme.TvTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class VodGridFocusTest {
    @get:Rule val compose = createComposeRule()

    private fun catalog(id: String, count: Int) = VideoCatalogState(
        categoryId = id,categoryTitle = id,
        works = (0 until count).map { index ->
            VideoWork("$id-$index",listOf(ProviderVideoRecord(ProviderRecordRef("test","$id-$index"),"test","影片$index")))
        },
    )

    @Test fun restoresAnOffscreenPosterThroughTheStableGridContainer() {
        val state = catalog("电影",64)
        compose.setContent { TvTheme { Box(Modifier.width(540.dp).fillMaxHeight()) {
            VodPosterGrid(state,state.works.last().id,true,1,emptyList(),{}, {}, {}, {}, {})
        } } }
        compose.onNodeWithTag("vod-work:${state.works.last().id}").assertIsFocused()
    }

    @Test fun pendingGridFocusIsCancelledWhenCategoriesChangeOrColumnLosesFocus() {
        var state by mutableStateOf(catalog("初始",64))
        var active by mutableStateOf(false)
        var request by mutableIntStateOf(0)
        val outside = FocusRequester()
        compose.setContent { TvTheme { Column(Modifier.fillMaxSize()) {
            Button(onClick = {},modifier = Modifier.testTag("outside-grid").focusRequester(outside)) { Text("菜单") }
            Box(Modifier.weight(1f).width(540.dp)) {
                VodPosterGrid(state,state.works.lastOrNull()?.id,active,request,emptyList(),{}, {}, {}, {}, {})
            }
        } } }
        compose.onNodeWithTag("outside-grid").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.mainClock.autoAdvance = false
        try {
            repeat(8) { index ->
                compose.runOnIdle { state = catalog("分类$index",64); active = true; request++ }
                // Start the offscreen scroll, then replace its target before the
                // queued frame/focus request completes, as a fast remote does.
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle { state = catalog("加载$index",0); active = false }
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeByFrame()
                // The menu takes focus after the inactive column is composed.
                compose.runOnIdle { outside.requestFocus() }
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeByFrame()
            }
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
        compose.onNodeWithTag("outside-grid").assertIsFocused()
    }

    @Test fun filtersShareTheTitleRowAndKeepFocusWhilePosterResultsChange() {
        var state by mutableStateOf(catalog("电影",0))
        val filters = listOf(VideoCategory("action","动作",emptyList()),VideoCategory("comedy","喜剧",emptyList()))
        compose.setContent { TvTheme { Box(Modifier.width(540.dp).fillMaxHeight()) {
            VodPosterGrid(state,null,true,1,filters,{ state = state.copy(filterId = it) },{}, {}, {}, {})
        } } }
        compose.onNodeWithTag("vod-filter:all").assertIsFocused()
        val heading = compose.onNodeWithTag("vod-grid-heading").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("vod-category-filters").fetchSemanticsNode().boundsInRoot
        assertEquals(heading.center.y,row.center.y,1f)
        assertTrue(heading.right <= row.left)
        repeat(6) { index ->
            compose.runOnIdle { state = catalog("电影",if (index % 2 == 0) 24 else 0) }
            compose.onNodeWithTag("vod-filter:all").assertIsFocused()
        }
        compose.runOnIdle { state = catalog("电影",24) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("vod-work:电影-0").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("vod-filter:all").assertIsFocused()
    }
}
