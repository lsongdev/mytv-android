package me.lsong.mytv.ui.settings.components

import android.media.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import me.lsong.mytv.utils.handleLeanbackKeyEvents

@Composable
fun LeanbackSettingsCategoryListItem(
    modifier: Modifier = Modifier,
    headlineContent: String,
    supportingContent: String? = null,
    trailingContent: @Composable () -> Unit = {},
    leadingContent:  @Composable (BoxScope.() -> Unit)? = null,
    onSelected: (() -> Unit)? = null,
    onLongSelected: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    androidx.tv.material3.ListItem(
        selected = false,
        onClick = { },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        ),
        leadingContent = leadingContent,
        headlineContent = {
            Text(text = headlineContent)
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                trailingContent()
            }
        },
        supportingContent = { supportingContent?.let { Text(it) } },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .handleLeanbackKeyEvents(
                onSelect = {
                    if (isFocused) {
                        if (onSelected != null) onSelected()
                    } else focusRequester.requestFocus()
                },
                onLongSelect = {
                    if (isFocused) onLongSelected()
                    else focusRequester.requestFocus()
                },
            ),
    )
}

@Composable
fun LeanbackSettingsCategoryListItem(
    modifier: Modifier = Modifier,
    headlineContent: String,
    supportingContent: String? = null,
    trailingContent: String,
    leadingContent:  @Composable (BoxScope.() -> Unit)? = null,
    onSelected: () -> Unit = {},
    onLongSelected: () -> Unit = {},
) {
    LeanbackSettingsCategoryListItem(
        modifier = modifier,
        leadingContent = leadingContent,
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        trailingContent = { Text(trailingContent) },
        onSelected = onSelected,
        onLongSelected = onLongSelected,
    )
}