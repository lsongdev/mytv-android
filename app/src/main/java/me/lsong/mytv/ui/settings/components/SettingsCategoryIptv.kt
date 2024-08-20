package me.lsong.mytv.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.launch
import me.lsong.mytv.data.repositories.iptv.IptvRepository
import me.lsong.mytv.ui.settings.LeanbackSettingsViewModel
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.ui.toast.LeanbackToastState
import me.lsong.mytv.utils.SP

@Composable
fun LeanbackSettingsCategoryIptv(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var dialogText by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf("") }
    val selectedItems = remember { mutableStateListOf<String>() }
    Column (
        modifier = modifier.padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvLazyColumn(
            modifier = modifier,
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



            // New IPTV source management section
            item {
                Text(
                    text = "IPTV 源管理",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // List of existing sources with checkboxes
            items(settingsViewModel.iptvSourceUrls.toList()) { source ->
                androidx.tv.material3.ListItem(
                    selected = false,
                    onClick = {
                        selectedSource = source
                        showDialog = true
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    ),
                    headlineContent = {
                        Row (
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedItems.contains(source),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedItems.add(source)
                                    } else {
                                        selectedItems.remove(source)
                                    }
                                }
                            )
                            Text(text = source, maxLines = 1, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                    }
                )
            }

            // Add and Delete buttons
            item {
                Row {
                    IconButton(onClick = {
                        selectedSource = ""
                        showDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                    IconButton(onClick = {
                        selectedItems.forEach {
                            settingsViewModel.iptvSourceUrls =
                                settingsViewModel.iptvSourceUrls.minus(it)
                        }
                        selectedItems.clear()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }
            item {
                LeanbackSettingsCategoryListItem(
                    headlineContent = "清除缓存",
                    supportingContent = "短按清除直播源缓存文件、可播放域名列表",
                    onSelected = {
                        settingsViewModel.iptvPlayableHostList = emptySet()
                        coroutineScope.launch { IptvRepository().clearCache() }
                        LeanbackToastState.I.showToast("清除缓存成功")
                    },
                )
            }
        }

        // Dialog for adding/editing sources
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(text = if (selectedSource.isEmpty()) "添加源" else "编辑源") },
                text = {
                    TextField(
                        value = if (selectedSource.isEmpty()) dialogText else selectedSource,
                        onValueChange = { dialogText = it },
                        placeholder = { Text(text = "请输入源 URL") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (selectedSource.isEmpty()) {
                            settingsViewModel.iptvSourceUrls =
                                settingsViewModel.iptvSourceUrls.plus(dialogText)
                        } else {
                            settingsViewModel.iptvSourceUrls =
                                settingsViewModel.iptvSourceUrls.minus(selectedSource).plus(dialogText)
                        }
                        dialogText = ""
                        showDialog = false
                    }) {
                        Text("确认")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dialogText = ""
                        showDialog = false
                    }) {
                        Text("取消")
                    }
                }
            )
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