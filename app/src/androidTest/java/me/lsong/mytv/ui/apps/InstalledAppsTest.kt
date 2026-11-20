package me.lsong.mytv.ui.apps

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import me.lsong.mytv.data.apps.InstalledApp
import me.lsong.mytv.data.apps.loadInstalledApps
import me.lsong.mytv.ui.theme.TvTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class InstalledAppsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remoteOkLaunchesOnlyTheFocusedApp() {
        val apps = listOf(
            InstalledApp(ComponentName("test.first", "test.first.Main"), "第一个应用", null),
            InstalledApp(ComponentName("test.second", "test.second.Main"), "第二个应用", null),
        )
        val launches = mutableListOf<InstalledApp>()
        var returned = false
        compose.setContent { TvTheme {
            InstalledAppsList(apps, null, true, 0, {}, { returned = true }, launches::add, {})
        } }
        compose.onNodeWithTag("drawer-entry:${apps[0].id}").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("drawer-entry:${apps[1].id}").assertIsFocused()
        assertTrue(launches.isEmpty())
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(apps[1]), launches) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertTrue(returned) }
    }

    @Test fun emptyListKeepsLeftNavigationAvailable() {
        var returned = false
        compose.setContent { TvTheme {
            InstalledAppsList(emptyList(), null, true, 0, {}, { returned = true }, {}, {})
        } }
        compose.onNodeWithText("暂无可启动的应用").assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertTrue(returned) }
    }

    @Test fun detailsShowMetadataAndRequireConfirmationForEachAction() {
        val app = InstalledApp(ComponentName("test.app", "test.app.Main"), "测试应用", null, "1.2.3")
        val actions = mutableListOf<AppAction>()
        compose.setContent { TvTheme { InstalledAppDetails(app, true, 0, {}, actions::add) } }
        compose.onNodeWithText("test.app").assertIsDisplayed()
        compose.onNodeWithText("版本 1.2.3").assertIsDisplayed()
        compose.onNodeWithTag("drawer-entry:app-action:OPEN").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("drawer-entry:app-action:UNINSTALL").assertIsFocused()
        assertTrue(actions.isEmpty())
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(AppAction.UNINSTALL), actions) }
        assertEquals("package:test.app", app.uninstallIntent().data.toString())
        assertEquals(Intent.ACTION_DELETE, app.uninstallIntent().action)
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app.infoIntent().action)
    }

    @Test fun systemAppDetailsOfferSystemSettingsInsteadOfUninstall() {
        val app = InstalledApp(ComponentName("test.system", "test.system.Main"), "系统应用", null, isSystemApp = true)
        compose.setContent { TvTheme { InstalledAppDetails(app, true, 0, {}, {}) } }
        compose.onNodeWithTag("drawer-entry:app-action:INFO").assertExists()
        compose.onNodeWithTag("drawer-entry:app-action:UNINSTALL").assertDoesNotExist()
    }

    @Test fun installedAppsAreLaunchableUniqueAndExcludeSelf() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apps = loadInstalledApps(context)
        assertEquals(apps.size, apps.map { it.component.packageName }.distinct().size)
        assertFalse(apps.any { it.component.packageName == context.packageName })
        apps.forEach { app ->
            val intent = app.launchIntent()
            assertEquals(app.component, intent.component)
            assertEquals(Intent.ACTION_MAIN, intent.action)
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            assertNotNull(context.packageManager.resolveActivity(intent, 0))
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        assertTrue(context.packageManager.queryIntentActivities(home, 0).any {
            it.activityInfo.packageName == context.packageName && it.activityInfo.name == "me.lsong.mytv.MainActivity"
        })
    }
}
