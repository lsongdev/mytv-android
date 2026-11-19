package me.lsong.mytv.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import me.lsong.mytv.domain.provider.ProviderConfig
import me.lsong.mytv.domain.provider.ProviderKind
import me.lsong.mytv.domain.provider.ProviderCheck
import me.lsong.mytv.ui.settings.ProviderSettingsViewModel

@Composable
fun ProviderSettingsPanel(kind: ProviderKind, viewModel: ProviderSettingsViewModel, modifier: Modifier = Modifier) {
    val providers by viewModel.providers.collectAsState()
    val health by viewModel.health.collectAsState()
    val filtered = providers.filter { it.kind == kind }
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }
    val entryFocus = remember { FocusRequester() }
    var hadEditor by remember { mutableStateOf(false) }
    LaunchedEffect(editing?.id) {
        if (editing == null && hadEditor) { withFrameNanos { }; entryFocus.requestFocus() }
    }
    editing?.let { source ->
        SourceEditorForm(
            source = source,
            existing = filtered.any { it.id == source.id },
            check = viewModel.check,
            onChanged = viewModel::clearCheck,
            onTest = viewModel::test,
            onSave = viewModel::save,
            onDelete = { viewModel.delete(source.id); editing = null; viewModel.clearCheck() },
            onDismiss = { editing = null; viewModel.clearCheck() },
        )
        return
    }
    val label = if (kind == ProviderKind.M3U) "直播" else "影视"
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (kind == ProviderKind.M3U) "直播" else "影视", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (kind == ProviderKind.M3U) "添加 M3U 地址，聚合多个源的分组与频道" else "添加 AppleCMS V10 接口地址",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { hadEditor = true; viewModel.clearCheck(); editing = ProviderConfig(name = "", url = "", kind = kind) },
                modifier = Modifier.testTag("add-source").focusRequester(entryFocus),
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("添加源")
            }
        }
        Text("${filtered.size} 个源 · ${filtered.count { it.isEnabled }} 个已启用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.White.copy(alpha = 0.025f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有${label}源", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("选择右上角「添加源」开始配置", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().focusGroup(), contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { source ->
                    ListItem(
                        selected = false,
                        onClick = { hadEditor = true; viewModel.clearCheck(); editing = source },
                        scale = ListItemDefaults.scale(focusedScale = 1f),
                        colors = ListItemDefaults.colors(containerColor = Color.White.copy(alpha = .07f), focusedContainerColor = MaterialTheme.colorScheme.onSurface, focusedContentColor = MaterialTheme.colorScheme.surface),
                        headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(source.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val status = health[source.id]?.let { viewModel.status(source) }
                                val formatter = remember { java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.getDefault()) }
                                val hint = status?.let {
                                    val retryClock = java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault())
                                    val stamp = formatter.format(java.util.Date(it.respondedAt))
                                    if (it.error == null) "最近响应 $stamp · ${it.latencyMs} ms" + if (it.cachedAt > 0) " · 已缓存" else ""
                                    else "${it.error} · $stamp · 退避至 ${retryClock.format(java.util.Date(it.retryAt))}"
                                } ?: "尚未响应"
                                Text(hint, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, color = if (status?.error != null) Color(0xFFFF9393) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        trailingContent = { Text(if (source.isEnabled) "已启用" else "已停用", style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.fillMaxWidth().testTag("source:${source.id}"),
                    )
                }
            }
        }
    }

}

@Composable
private fun SourceEditorForm(
    source: ProviderConfig,
    existing: Boolean,
    check: ProviderCheck?,
    onChanged: () -> Unit,
    onTest: (ProviderConfig) -> Unit,
    onSave: (ProviderConfig) -> String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(source.id) { mutableStateOf(source.name) }
    var url by rememberSaveable(source.id) { mutableStateOf(source.url) }
    var enabled by rememberSaveable(source.id) { mutableStateOf(source.isEnabled) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val firstField = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun draft() = source.copy(name = name.trim(), url = url.trim(), isEnabled = enabled)
    fun changed() { error = null; onChanged() }
        Column(
            Modifier.fillMaxSize()
                .background(Color(0xFF1A202B), RoundedCornerShape(12.dp)).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("${if (existing) "编辑" else "添加"} ${source.kind.name} 源", style = MaterialTheme.typography.headlineSmall)
            SourceTextField("源名称", name, { name = it; changed() }, "例如：家庭直播 / 电影资源", "source-name", firstField)
            SourceTextField("接口地址", url, { url = it; changed() }, "http:// 或 https://", "source-url", isUrl = true)
            ListItem(
                selected = enabled,
                onClick = { enabled = !enabled },
                headlineContent = { Text("启用此源") },
                trailingContent = { Text(if (enabled) "开启" else "关闭") },
                scale = ListItemDefaults.scale(focusedScale = 1f),
                modifier = Modifier.fillMaxWidth().testTag("source-enabled"),
            )
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("source-error"))
            when (check) {
                ProviderCheck.Loading -> Text("正在测试连接…", style = MaterialTheme.typography.bodyMedium)
                is ProviderCheck.Result -> Text(check.message, color = if (check.successful) Color(0xFFA9D5AD) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                null -> Unit
            }
            if (deleting) {
                Text("确定删除「${source.name}」？", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { deleting = false }) { Text("取消删除") }
                    Button(onClick = onDelete, modifier = Modifier.testTag("confirm-delete-source")) { Text("确定删除") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        keyboard?.hide()
                        val value = draft()
                        error = value.validationError()
                        if (error == null) error = onSave(value)
                        if (error == null) { keyboard?.hide(); onDismiss() }
                    }, modifier = Modifier.testTag("save-source")) { Text("保存") }
                    OutlinedButton(onClick = {
                        keyboard?.hide()
                        val value = draft()
                        error = value.validationError()
                        if (error == null) { keyboard?.hide(); onTest(value) }
                    }, enabled = check != ProviderCheck.Loading, modifier = Modifier.testTag("test-source")) { Text("测试连接") }
                    OutlinedButton(onClick = onDismiss) { Text("取消") }
                    if (existing) OutlinedButton(onClick = { deleting = true }, modifier = Modifier.testTag("delete-source")) { Text("删除") }
                }
            }
        }
        LaunchedEffect(source.id) { withFrameNanos { }; firstField.requestFocus() }
}

@Composable
private fun SourceTextField(
    label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, tag: String,
    focusRequester: FocusRequester? = null, isUrl: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = if (isUrl) KeyboardType.Uri else KeyboardType.Text, imeAction = if (isUrl) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }, onDone = { keyboard?.hide(); focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth().testTag(tag)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .background(Color(0xFF11151D), RoundedCornerShape(10.dp))
                .border(if (focused) 2.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else Color(0xFF475365), RoundedCornerShape(10.dp))
                .padding(14.dp),
            decorationBox = { input -> Box { if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge); input() } },
        )
    }
}
