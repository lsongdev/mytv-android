package me.lsong.mytv.ui.components
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.utils.handleLeanbackKeyEvents

data class MyTvMenuItem(
    val icon: Any? = null,
    val title: String = "",
    val description: String? = null
)

@Composable
fun MyTvMenuItem(
    modifier: Modifier = Modifier,
    item: MyTvMenuItem,
    isFocused: Boolean = false,
    isSelected: Boolean = false,
    onFocused: () -> Unit = {},
    onSelected: () -> Unit = {},
    onLongSelect: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    LaunchedEffect(isSelected) {
        if (isSelected) {
            focusRequester.requestFocus()
        }
    }
    CompositionLocalProvider(
        LocalContentColor provides if (isFocused) MaterialTheme.colorScheme.background
        else MaterialTheme.colorScheme.onBackground
    ) {
        androidx.tv.material3.ListItem(
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .handleLeanbackKeyEvents(
                    key = item.hashCode(),
                    onSelect = onSelected,
                    onLongSelect = onLongSelect,
                ),
            colors = ListItemDefaults.colors(
                focusedContentColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.onBackground,
                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            onClick = onSelected,
            selected = isSelected,
            leadingContent = item.icon?.let { icon ->
                {
                    when (icon) {
                        is ImageVector -> Icon(
                            imageVector = icon,
                            contentDescription = item.title,
                            modifier = Modifier.size(24.dp)
                        )
                        is String -> if (icon.isEmpty()) {
                            Text(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(color = MaterialTheme.colorScheme.primary)
                                    .wrapContentHeight(align = Alignment.CenterVertically),
                                textAlign = TextAlign.Center,
                                text = item.title.take(2).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            AsyncImage(
                                model = icon,
                                contentDescription = item.title,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        else -> null
                    }
                }
            },
            headlineContent = { Text(text = item.title, maxLines = 2) },
            supportingContent = item.description?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier.alpha(0.8f),
                    )
                }
            },
        )
    }
}

@Composable
fun MyTvMenuItemList(
    items: List<MyTvMenuItem>,
    selectedItem: MyTvMenuItem = items.firstOrNull() ?: MyTvMenuItem(),
    onUserAction: () -> Unit = {},
    onFocused: (MyTvMenuItem) -> Unit = {},
    onSelected: (MyTvMenuItem) -> Unit = {},
    onLongSelect: (MyTvMenuItem) -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier,
) {
    val selectedIndex = remember(selectedItem, items) { items.indexOf(selectedItem) }
    val itemFocusRequesterList = remember(items) { List(items.size) { FocusRequester() } }
    val listState = rememberTvLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { onUserAction() }
    }

    LaunchedEffect(selectedItem, items) {
        val index = items.indexOf(selectedItem)
        listState.scrollToItem(maxOf(0, index))
    }

    TvLazyColumn(
        state = listState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .width(250.dp)
            .background(MaterialTheme.colorScheme.background.copy(0.8f))
            .focusRequester(focusRequester)
    ) {
        itemsIndexed(items, key = { _, item -> item.hashCode() }) { index, item ->
            MyTvMenuItem(
                item = item,
                isFocused = selectedIndex == index,
                isSelected = selectedIndex == index,
                onFocused = { onFocused(item) },
                onSelected = { onSelected(item) },
                onLongSelect = { onLongSelect(item) },
                focusRequester = itemFocusRequesterList[index],
            )
        }
    }
}

@Preview
@Composable
private fun MyTvMenuItemListPreview() {
    LeanbackTheme {
        MyTvMenuItemList(
            modifier = Modifier.padding(20.dp),
            items = listOf(
                MyTvMenuItem(title = "Channel 1", description = "Current Program 1"),
                MyTvMenuItem(title = "Channel 2", description = "Current Program 2"),
                MyTvMenuItem(title = "Channel 3", description = "Current Program 3")
            )
        )
    }
}
