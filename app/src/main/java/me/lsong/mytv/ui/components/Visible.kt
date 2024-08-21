package me.lsong.mytv.ui.components

import androidx.compose.runtime.Composable

@Composable
fun LeanbackVisible(
    visibleProvider: () -> Boolean = { false },
    content: @Composable () -> Unit
) {
    if (visibleProvider()) {
        content()
    }
}