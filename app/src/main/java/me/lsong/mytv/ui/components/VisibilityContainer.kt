package me.lsong.mytv.ui.components

import androidx.compose.runtime.Composable

@Composable
fun VisibilityContainer(
    visibleProvider: () -> Boolean = { false },
    content: @Composable () -> Unit
) {
    if (visibleProvider()) {
        content()
    }
}
