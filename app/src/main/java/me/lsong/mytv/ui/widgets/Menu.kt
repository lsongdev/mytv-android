package me.lsong.mytv.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import me.lsong.mytv.epg.EpgList
import me.lsong.mytv.epg.EpgList.Companion.currentProgrammes
import me.lsong.mytv.iptv.TVChannel
import me.lsong.mytv.iptv.TVGroupList
import me.lsong.mytv.iptv.TVGroupList.Companion.channels
import me.lsong.mytv.iptv.TVGroupList.Companion.findGroupIndex
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
    menuItemProvider: () -> MyTvMenuItem = { MyTvMenuItem(title = "") },
    focusRequesterProvider: () -> FocusRequester = { FocusRequester() },
    isSelectedProvider: () -> Boolean = { false },
    isFocusedProvider: () -> Boolean = { false },
    onSelected: () -> Unit = {},
    onFocused: (MyTvMenuItem) -> Unit = {},
    onFavoriteToggle: () -> Unit = {}
) {
    val menuItem = menuItemProvider()
    val focusRequester = focusRequesterProvider()
    var isFocused by remember { mutableStateOf(isFocusedProvider()) }

    CompositionLocalProvider(
        LocalContentColor provides if (isFocused) MaterialTheme.colorScheme.background
        else MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier.clip(ListItemDefaults.shape().shape),
        ) {
            androidx.tv.material3.ListItem(
                modifier = modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused || it.hasFocus
                        if (isFocused) {
                            onFocused(menuItem)
                        }
                    }
                    .handleLeanbackKeyEvents(
                        key = menuItem.hashCode(),
                        onSelect = {
                            if (isFocused) onSelected()
                            else focusRequester.requestFocus()
                        },
                        onLongSelect = {
                            if (isFocused) onFavoriteToggle()
                            else focusRequester.requestFocus()
                        },
                    ),
                colors = ListItemDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.onBackground,
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                onClick = { onSelected() },
                selected = isSelectedProvider(),
                leadingContent = menuItem.icon?.let { icon ->
                    {
                        when (icon) {
                            is ImageVector -> Icon(
                                imageVector = icon,
                                contentDescription = menuItem.title,
                                modifier = Modifier.size(24.dp)
                            )
                            is String -> if (icon.isEmpty()) {
                                Text(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(color = MaterialTheme.colorScheme.primary)
                                        .wrapContentHeight(align = Alignment.CenterVertically),
                                    textAlign = TextAlign.Center,
                                    text = menuItem.title.take(2).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                AsyncImage(
                                    model = menuItem.icon,
                                    contentDescription = menuItem.title,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                },
                headlineContent = { Text(text = menuItem.title, maxLines = 2) },
                supportingContent = {
                    if (menuItem.description != null) {
                        Text(
                            text = menuItem.description,
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
    currentGroupProvider: () -> MyTvMenuItem,
    currentItemProvider: () -> MyTvMenuItem,
    onGroupSelected: (MyTvMenuItem) -> Unit,
    onItemSelected: (MyTvMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    onUserAction: () -> Unit = {}
) {
    var focusedGroup by remember { mutableStateOf(currentGroupProvider()) }
    var focusedItem by remember { mutableStateOf(currentItemProvider()) }
    var currentItems by remember { mutableStateOf(itemsProvider(focusedGroup.title)) }

    val rightListFocusRequester = remember { FocusRequester() }

    Row(modifier = modifier) {
        MyTvMenuItemList(
            onUserAction = onUserAction,
            menuItemsProvider = { groups },
            selectedItemProvider = { focusedGroup },
            onFocused = { menuGroupItem ->
                focusedGroup = menuGroupItem
                currentItems = itemsProvider(menuGroupItem.title)
            },
            onSelected = { menuGroupItem ->
                focusedGroup = menuGroupItem
                currentItems = itemsProvider(menuGroupItem.title)
                focusedItem = currentItems.firstOrNull { it.title == focusedItem.title } ?: currentItems.firstOrNull() ?: MyTvMenuItem()
                onGroupSelected(menuGroupItem)
                rightListFocusRequester.requestFocus()
            }
        )
        MyTvMenuItemList(
            focusRequester = rightListFocusRequester,
            menuItemsProvider = { currentItems },
            selectedItemProvider = { focusedItem },
            onUserAction = onUserAction,
            onFocused = { menuItem ->
                focusedItem = menuItem
            },
            onSelected = { menuItem ->
                focusedItem = menuItem
                onItemSelected(menuItem)
            }
        )
    }

    LaunchedEffect(Unit) {
        rightListFocusRequester.requestFocus()
    }
}

@Composable
fun MyTvMenuItemList(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    menuItemsProvider: () -> List<MyTvMenuItem> = { emptyList() },
    selectedItemProvider: () -> MyTvMenuItem = { MyTvMenuItem() },
    onUserAction: () -> Unit = {},
    onFocused: (MyTvMenuItem) -> Unit = {},
    onSelected: (MyTvMenuItem) -> Unit = {},
    onFavoriteToggle: (MyTvMenuItem) -> Unit = {}
) {
    val menuItems = menuItemsProvider()
    val selectedItem = selectedItemProvider()
    val itemFocusRequesterList = remember(menuItems) {
        List(menuItems.size) { FocusRequester() }
    }
    var focusedMenuItem by remember { mutableStateOf(selectedItem) }
    val selectedIndex = remember(selectedItem, menuItems) {
        menuItems.indexOf(selectedItem).takeIf { it != -1 } ?: 0
    }
    val listState = rememberTvLazyListState(
        initialFirstVisibleItemIndex = maxOf(0, selectedIndex - 2)
    )

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    LaunchedEffect(selectedItem, menuItems) {
        val index = menuItems.indexOf(selectedItem)
        if (index != -1) {
            listState.scrollToItem(maxOf(0, index - 2))
            itemFocusRequesterList[index].requestFocus()
        }
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
        itemsIndexed(menuItems, key = { _, item -> item.hashCode() }) { index, item ->
            val isSelected by remember { derivedStateOf { item == selectedItem } }
            MyTvMenuItem(
                menuItemProvider = { item },
                focusRequesterProvider = { itemFocusRequesterList[index] },
                isSelectedProvider = { isSelected },
                isFocusedProvider = { item == focusedMenuItem },
                onSelected = { onSelected(item) },
                onFocused = {
                    focusedMenuItem = it
                    onFocused(it)
                },
                onFavoriteToggle = { onFavoriteToggle(item) }
            )
        }
    }
}

@Preview
@Composable
private fun MyTvMenuItemComponentPreview() {
    LeanbackTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MyTvMenuItem(
                menuItemProvider = { MyTvMenuItem(title = "Channel 1", description = "Current Program 1") },
            )

            MyTvMenuItem(
                isFocusedProvider = { true },
                menuItemProvider = { MyTvMenuItem(title = "Channel 2", description = "Current Program 2") },
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
            menuItemsProvider = {
                listOf(
                    MyTvMenuItem(title = "Channel 1", description = "Current Program 1"),
                    MyTvMenuItem(title = "Channel 2", description = "Current Program 2"),
                    MyTvMenuItem(title = "Channel 3", description = "Current Program 3")
                )
            },
        )
    }
}
