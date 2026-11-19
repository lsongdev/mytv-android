package me.lsong.mytv.ui.settings.components

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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text

@Composable
fun SettingsRow(
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
        onClick = { onSelected?.invoke() },
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
        modifier = modifier,
    )
}

@Composable
fun SettingsRow(
    modifier: Modifier = Modifier,
    headlineContent: String,
    supportingContent: String? = null,
    trailingContent: String,
    leadingContent:  @Composable (BoxScope.() -> Unit)? = null,
    onSelected: () -> Unit = {},
    onLongSelected: () -> Unit = {},
) {
    SettingsRow(
        modifier = modifier,
        leadingContent = leadingContent,
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        trailingContent = { Text(trailingContent) },
        onSelected = onSelected,
        onLongSelected = onLongSelected,
    )
}
