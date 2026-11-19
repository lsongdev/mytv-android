package me.lsong.mytv

import me.lsong.mytv.app.AppScreen
import me.lsong.mytv.ui.theme.TvTheme

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import me.lsong.mytv.ui.components.ScreenPadding

import me.lsong.mytv.ui.input.BackKeyHandler
import android.view.KeyEvent

class MainActivity : ComponentActivity() {
    private val backKeys by lazy {
        BackKeyHandler(
            onShortPress = { onBackPressedDispatcher.onBackPressed() },
            onLongPress = { finishAndRemoveTask() },
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        backKeys.dispatch(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        backKeys.dispatch(event) || super.onKeyUp(keyCode, event)

    override fun onPause() {
        backKeys.cancel()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 隐藏状态栏、导航栏
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).let { insetsController ->
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            // 屏幕常亮
            TvTheme {
                AppScreen()
            }
        }

        // Check if the device is a TV
        if (isTVDevice()) {
            // No need to force orientation for TV
        } else {
            // Force landscape mode on non-TV devices
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
    }

    private fun isTVDevice(): Boolean {
        return (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // if (!AppPreferences.uiPipMode) return
        // enterPictureInPictureMode(
        //     PictureInPictureParams.Builder()
        //         .setAspectRatio(Rational(16, 9))
        //         .build()
        // )
        super.onUserLeaveHint()
    }
}

val LeanbackParentPadding = PaddingValues(vertical = 12.dp, horizontal = 24.dp)

@Composable
fun rememberScreenPadding(direction: LayoutDirection = LocalLayoutDirection.current) =
    remember {
        ScreenPadding(
            start = LeanbackParentPadding.calculateStartPadding(direction) + 8.dp,
            top = LeanbackParentPadding.calculateTopPadding(),
            end = LeanbackParentPadding.calculateEndPadding(direction) + 8.dp,
            bottom = LeanbackParentPadding.calculateBottomPadding()
        )
    }
