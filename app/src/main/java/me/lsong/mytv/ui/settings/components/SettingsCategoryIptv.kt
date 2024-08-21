package me.lsong.mytv.ui.settings.components

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.launch
import me.lsong.mytv.iptv.IptvRepository
import me.lsong.mytv.ui.settings.LeanbackSettingsViewModel
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.ui.toast.LeanbackToastState
import me.lsong.mytv.utils.Constants.APP_NAME
import me.lsong.mytv.utils.SP

@Composable
fun URLListEditor(
    urls: Set<String>,
    onUrlsChange: (Set<String>) -> Unit
) {
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    var showDialog by remember { mutableStateOf(false) }
    var inputSource by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // List of URLs
        LazyColumn(
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth()
                .border(1.dp, color = MaterialTheme.colorScheme.border),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(20.dp, 10.dp),
        ) {
            itemsIndexed(urls.toList()) { _, url ->
                androidx.tv.material3.ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    selected = selectedItems.contains(url),
                    onClick = {
                        selectedItems = if (selectedItems.contains(url)) {
                            selectedItems - url
                        } else {
                            selectedItems + url
                        }
                    },
                    headlineContent = {
                        Row {
                            Checkbox(checked = selectedItems.contains(url), onCheckedChange = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = url, maxLines = 1)
                        }
                    }
                )
            }
        }

        if (showDialog) {
            AlertDialog(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.material3.MaterialTheme.shapes.extraSmall,
                title = { Text("Input URL") },
                onDismissRequest = { showDialog = false },
                text = {
                    TextField(
                        value = inputSource,
                        modifier = Modifier.fillMaxWidth(),
                        onValueChange = { inputSource = it }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        onUrlsChange(urls + inputSource)
                        showDialog = false
                        inputSource = ""
                    }) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    Button(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Row {
            IconButton(onClick = {
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
            IconButton(onClick = {
                onUrlsChange(urls - selectedItems)
                selectedItems = emptySet()
            }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}


@Composable
fun LeanbackSettingsCategoryIptv(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    TvLazyColumn (
        modifier = modifier.padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Existing items
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "换台反转",
                supportingContent = if (settingsViewModel.iptvChannelChangeFlip) "方向键上：下一个频道；方向键下：上一个频道"
                else "方向键上：上一个频道；方向键下：下一个频道",
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.iptvChannelChangeFlip,
                        onCheckedChange = null
                    )
                },
                onSelected = {
                    settingsViewModel.iptvChannelChangeFlip =
                        !settingsViewModel.iptvChannelChangeFlip
                },
            )
        }

        item {
            Column {
                Text(text = "IPTV 源管理", style = MaterialTheme.typography.titleMedium)
                var iptvSourceUrls by remember { mutableStateOf(settingsViewModel.iptvSourceUrls) }
                URLListEditor(
                    urls = iptvSourceUrls,
                    onUrlsChange = { newUrls ->
                        iptvSourceUrls = newUrls
                        settingsViewModel.iptvSourceUrls = newUrls
                    }
                )
                Text(
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    text = "「${APP_NAME}」支持从多个直播源中获取频道播放地址，您也可以在这里添加自定义直播源，如果没有提供，将使用默认的直播源提供商。"
                )
            }
        }

    }


}

@Preview
@Composable
private fun MyTvSettingsPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryIptv(
            modifier = Modifier.padding(20.dp),
            settingsViewModel = LeanbackSettingsViewModel().apply {
                iptvSourceCacheTime = 3_600_000
                iptvSourceUrls = setOf(
                    "https://iptv-org.github.io/iptv/iptv.m3u",
                    "https://iptv-org.github.io/iptv/iptv2.m3u",
                    "https://iptv-org.github.io/iptv/iptv3.m3u",
                )
            },
        )
    }
}