package me.lsong.mytv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.utils.handleLeanbackKeyEvents

enum class MyTvSettingsCategories(
    val icon: ImageVector,
    val title: String
) {
    APP(Icons.Default.SmartDisplay, "应用"),
    IPTV(Icons.Default.LiveTv, "直播源"),
    EPG(Icons.Default.Menu, "节目单"),
    ABOUT(Icons.Default.Info, "关于"),
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MyTvSettingsCategoryList(
    modifier: Modifier = Modifier,
    focusedCategoryProvider: () -> MyTvSettingsCategories = { MyTvSettingsCategories.entries.first() },
    onFocused: (MyTvSettingsCategories) -> Unit = {},
) {
    var hasFocused = rememberSaveable { false }

    TvLazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.focusRestorer().fillMaxSize()
    ) {
        itemsIndexed(MyTvSettingsCategories.entries) { index, category ->
            val isSelected by remember { derivedStateOf { focusedCategoryProvider() == category } }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (index == 0 && !hasFocused) {
                    focusRequester.requestFocus()
                    hasFocused = true
                }
            }

            MyTvSettingsCategoryItem(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth(),
                icon = category.icon,
                title = category.title,
                isSelectedProvider = { isSelected },
                onFocused = { onFocused(category) },
            )
        }
    }
}

@Composable
private fun MyTvSettingsCategoryItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    isSelectedProvider: () -> Boolean = { false },
    onFocused: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    androidx.tv.material3.ListItem(
        selected = isSelectedProvider(),
        onClick = { },
        leadingContent = { androidx.tv.material3.Icon(icon, title) },
        headlineContent = { androidx.tv.material3.Text(text = title) },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) {
                    onFocused()
                }
            }
            .handleLeanbackKeyEvents(
                onSelect = {
                    if (isFocused) focusManager.moveFocus(FocusDirection.Right)
                    else focusRequester.requestFocus()
                }
            ),
    )
}

@Preview
@Composable
private fun LeanbackSettingsCategoryItemPreview() {
    LeanbackTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MyTvSettingsCategoryItem(
                icon = MyTvSettingsCategories.ABOUT.icon,
                title = MyTvSettingsCategories.ABOUT.title,
            )

            MyTvSettingsCategoryItem(
                icon = MyTvSettingsCategories.ABOUT.icon,
                title = MyTvSettingsCategories.ABOUT.title,
                isSelectedProvider = { true },
            )
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryListPreview() {
    LeanbackTheme {
        MyTvSettingsCategoryList()
    }
}