package me.lsong.mytv.ui.widgets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import me.lsong.mytv.ui.settings.MyTvSettingsCategories
import me.lsong.mytv.ui.settings.components.LeanbackSettingsCategoryContent
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
    onFavoriteToggle: () -> Unit = {},
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
        Box(
            modifier = Modifier.clip(ListItemDefaults.shape().shape),
        ) {
            androidx.tv.material3.ListItem(
                modifier = modifier
                    .align(Alignment.Center)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onFocused() }
                    .handleLeanbackKeyEvents(
                        key = item.hashCode(),
                        onSelect = onSelected,
                        onLongSelect = onFavoriteToggle,
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
}

@Composable
fun MyTvMenu(
    groups: List<MyTvMenuItem>,
    itemsProvider: (String) -> List<MyTvMenuItem>,
    currentGroup: MyTvMenuItem,
    currentItem: MyTvMenuItem,
    onGroupFocused: (MyTvMenuItem) -> Unit = {},
    onGroupSelected: (MyTvMenuItem) -> Unit = {},
    onItemSelected: (MyTvMenuItem) -> Unit = {},
    modifier: Modifier = Modifier,
    onUserAction: () -> Unit = {},
) {
    var focusedGroup by remember { mutableStateOf(currentGroup) }
    var focusedItem by remember { mutableStateOf(currentItem) }
    var items by remember { mutableStateOf(itemsProvider(focusedGroup.title)) }
    val rightListFocusRequester = remember { FocusRequester() }

    Row(modifier = modifier) {
        MyTvMenuItemList(
            items = groups,
            selectedItem = focusedGroup,
            onFocused = { menuGroupItem ->
                focusedGroup = menuGroupItem
                items = itemsProvider(menuGroupItem.title)
                onGroupFocused(focusedGroup)
            },
            onSelected = { menuGroupItem ->
                focusedGroup = menuGroupItem
                items = itemsProvider(menuGroupItem.title)
                focusedItem = items.firstOrNull() ?: MyTvMenuItem()
                onGroupSelected(focusedGroup)
                rightListFocusRequester.requestFocus()
            },
            onUserAction = onUserAction
        )
        MyTvMenuItemList(
            items = items,
            selectedItem = focusedItem,
            onSelected = { menuItem ->
                focusedItem = menuItem
                onItemSelected(focusedItem)
            },
            onUserAction = onUserAction,
            focusRequester = rightListFocusRequester
        )

    }

    LaunchedEffect(Unit) {
        rightListFocusRequester.requestFocus()
    }
}

@Composable
fun MyTvMenuItemList(
    items: List<MyTvMenuItem>,
    selectedItem: MyTvMenuItem = items.firstOrNull() ?: MyTvMenuItem(),
    onUserAction: () -> Unit = {},
    onFocused: (MyTvMenuItem) -> Unit = {},
    onSelected: (MyTvMenuItem) -> Unit = {},
    onFavoriteToggle: (MyTvMenuItem) -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    var focusedItem by remember { mutableStateOf(selectedItem) }
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
            .fillMaxHeight()
            .width(250.dp)
            .background(MaterialTheme.colorScheme.background.copy(0.8f))
            .focusRequester(focusRequester),
    ) {
        itemsIndexed(items, key = { _, item -> item.hashCode() }) { index, item ->
            MyTvMenuItem(
                item = item,
                focusRequester = itemFocusRequesterList[index],
                isSelected = selectedIndex == index,
                isFocused = selectedIndex == index,
                onSelected = { onSelected(item) },
                onFocused = {
                    focusedItem = item
                    onFocused(item)
                },
                onFavoriteToggle = { onFavoriteToggle(item) }
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
