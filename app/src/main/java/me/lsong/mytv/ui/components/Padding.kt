package me.lsong.mytv.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

@Immutable
data class LeanbackPadding(
    val start: Dp,
    val top: Dp,
    val end: Dp,
    val bottom: Dp,
)