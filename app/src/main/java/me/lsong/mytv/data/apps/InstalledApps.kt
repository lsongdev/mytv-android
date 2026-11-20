package me.lsong.mytv.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import java.text.Collator

data class InstalledApp(
    val component: ComponentName, val title: String, val icon: Drawable?,
    val versionName: String? = null, val isSystemApp: Boolean = false,
) {
    val id: String get() = component.flattenToString()

    fun launchIntent(): Intent = Intent.makeMainActivity(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun infoIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())
    fun uninstallIntent(): Intent = Intent(Intent.ACTION_DELETE, packageUri())
    private fun packageUri(): Uri = Uri.fromParts("package", component.packageName, null)
}

/** One entry per package; prefer its TV activity when it exposes both launcher categories. */
fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val activities = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        .flatMap { category -> pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
        .filter { it.activityInfo.packageName != context.packageName && it.activityInfo.exported && it.activityInfo.enabled }
        .distinctBy { it.activityInfo.packageName }
    val collator = Collator.getInstance()
    return activities.map { info ->
        InstalledApp(
            ComponentName(info.activityInfo.packageName, info.activityInfo.name),
            info.loadLabel(pm).toString(),
            runCatching { info.loadIcon(pm) }.getOrNull(),
            runCatching { pm.getPackageInfo(info.activityInfo.packageName, 0).versionName }.getOrNull(),
            info.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
        )
    }.sortedWith { a, b -> collator.compare(a.title, b.title).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
}
