package me.lsong.mytv.ui.apps

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.compose.ui.text.style.TextOverflow
import me.lsong.mytv.ui.browser.BrowseCategory
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import me.lsong.mytv.data.apps.InstalledApp
import me.lsong.mytv.ui.browser.BrowseEntry
import me.lsong.mytv.ui.browser.BrowseList
import me.lsong.mytv.ui.browser.ColumnHeading

@Composable
internal fun InstalledAppsPanel(
    snapshot: me.lsong.mytv.data.apps.InstalledAppsSnapshot,
    onRefresh: () -> Unit,
    selectedId: String?,
    active: Boolean,
    focusRequest: Int,
    onFocused: (String) -> Unit,
    onLeft: () -> Unit,
    detailsId: String?,
    detailsActive: Boolean,
    onDetails: (String) -> Unit,
    onCloseDetails: () -> Unit,
    listWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val apps = snapshot.apps
    val loading = snapshot.loading
    val error = snapshot.error
    LaunchedEffect(apps, loading, detailsId) {
        if (!loading && detailsId != null && apps.none { it.id == detailsId }) onCloseDetails()
    }
    val performAction: (InstalledApp, AppAction) -> Unit = { app, action ->
        val intent = when (action) {
            AppAction.OPEN -> app.launchIntent()
            AppAction.INFO -> app.infoIntent()
            AppAction.UNINSTALL -> app.uninstallIntent()
        }
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "无法执行此操作，请在系统设置中管理应用", Toast.LENGTH_SHORT).show(); onRefresh()
        } catch (_: SecurityException) {
            Toast.makeText(context, "系统不允许执行此操作", Toast.LENGTH_SHORT).show()
        }
    }
    Row(modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InstalledAppsList(apps, selectedId, active, focusRequest, onFocused, onLeft,
            onLaunch = { performAction(it, AppAction.OPEN) },
            onRight = { (apps.firstOrNull { it.id == selectedId } ?: apps.firstOrNull())?.let { onDetails(it.id) } },
            emptyMessage = if (loading) "正在加载应用…" else error ?: "暂无可启动的应用",
            modifier = Modifier.width(listWidth))
        apps.firstOrNull { it.id == detailsId }?.let { app ->
            BackHandler(onBack = onCloseDetails)
            InstalledAppDetails(app, detailsActive, focusRequest, onCloseDetails,
                onAction = { performAction(app, it) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun InstalledAppsList(
    apps: List<InstalledApp>, selectedId: String?, active: Boolean, focusRequest: Int,
    onFocused: (String) -> Unit, onLeft: () -> Unit, onLaunch: (InstalledApp) -> Unit, onRight: () -> Unit,
    modifier: Modifier = Modifier, emptyMessage: String = "暂无可启动的应用",
) {
    Column(modifier.fillMaxHeight().testTag("drawer-app-list-column")) {
        ColumnHeading("已安装应用", "${apps.size}")
        BrowseList(
            entries = apps.map { BrowseEntry.Application(it) },
            selectedId = selectedId,
            contextKey = "installed-apps:${apps.joinToString { it.id }}",
            active = active, focusRequest = focusRequest,
            onFocused = onFocused,
            onSelected = { id -> apps.firstOrNull { it.id == id }?.let(onLaunch) },
            onLeft = onLeft, onRight = onRight, emptyMessage = emptyMessage,
        )
    }
}


internal enum class AppAction(val title: String) {
    OPEN("打开"), INFO("应用信息"), UNINSTALL("卸载")
}

@Composable
internal fun InstalledAppDetails(
    app: InstalledApp, active: Boolean, focusRequest: Int, onLeft: () -> Unit,
    onAction: (AppAction) -> Unit, modifier: Modifier = Modifier,
) {
    var selectedAction by remember(app.id) { mutableStateOf(AppAction.OPEN) }
    val actions = if (app.isSystemApp) listOf(AppAction.OPEN, AppAction.INFO) else AppAction.entries
    Column(modifier.fillMaxHeight().testTag("drawer-app-details-column")) {
        ColumnHeading("应用详情", if (app.isSystemApp) "系统应用" else "已安装")
        Text(app.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp))
        Spacer(Modifier.height(8.dp))
        Text(app.component.packageName, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
        app.versionName?.takeIf { it.isNotBlank() }?.let {
            Text("版本 $it", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
        }
        Spacer(Modifier.height(16.dp))
        BrowseList(
            entries = actions.map { BrowseEntry.Category(BrowseCategory("app-action:${it.name}", it.title,
                when (it) {
                    AppAction.INFO -> "在系统设置中管理权限和存储"
                    AppAction.UNINSTALL -> "由系统确认卸载"
                    else -> null
                })) },
            selectedId = "app-action:${selectedAction.name}", contextKey = "app-details:${app.id}",
            active = active, focusRequest = focusRequest,
            onFocused = { selectedAction = AppAction.valueOf(it.substringAfter(':')) },
            onSelected = { onAction(AppAction.valueOf(it.substringAfter(':'))) },
            onLeft = onLeft, onRight = {}, emptyMessage = "",
        )
    }
}
