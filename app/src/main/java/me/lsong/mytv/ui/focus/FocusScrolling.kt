@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.lsong.mytv.ui.focus

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

/** Keep visible items still; scroll only enough to reveal a clipped focused item. */
object EdgeBringIntoViewSpec : BringIntoViewSpec

/** Compose an offscreen focus target without resetting an already visible viewport. */
suspend fun LazyListState.prepareFocusAt(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}

suspend fun LazyGridState.prepareFocusAt(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}

/** Reserve room for a focused card's scale and border while keeping the edge-only policy. */
class PaddedEdgeBringIntoViewSpec(private val padding: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (size + 2 * padding >= containerSize) return EdgeBringIntoViewSpec.calculateScrollDistance(offset, size, containerSize)
        return when {
            offset < padding -> offset - padding
            offset + size > containerSize - padding -> offset + size - containerSize + padding
            else -> 0f
        }
    }
}
