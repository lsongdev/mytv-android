package me.lsong.mytv

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.IntRange
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import me.lsong.mytv.ui.LoadingScreen
import me.lsong.mytv.ui.components.LeanbackPadding
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.ui.toast.LeanbackToastScreen
import me.lsong.mytv.ui.toast.LeanbackToastState
import me.lsong.mytv.utils.HttpServer
import me.lsong.mytv.utils.Settings
import kotlin.system.exitProcess


class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 隐藏状态栏、导航栏
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).let { insetsController ->
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            // 屏幕常亮
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            LeanbackTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    LeanbackApp(
                        onBackPressed = {
                            finish()
                            exitProcess(0)
                        },
                    )
                }
            }
        }

        // Check if the device is a TV
        if (isTVDevice()) {
            // No need to force orientation for TV
        } else {
            // Force landscape mode on non-TV devices
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }


        HttpServer.start(applicationContext, showToast = {
            LeanbackToastState.I.showToast(it, id = "httpServer")
        })
    }

    private fun isTVDevice(): Boolean {
        return (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!Settings.uiPipMode) return

        enterPictureInPictureMode(
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
        )
        super.onUserLeaveHint()
    }
}


@Composable
fun LeanbackApp(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
) {
    val doubleBackPressedExitState = rememberLeanbackDoubleBackPressedExitState()
    LeanbackToastScreen()
    LoadingScreen(
        modifier = modifier,
        onBackPressed = {
            if (doubleBackPressedExitState.allowExit) {
                onBackPressed()
            } else {
                doubleBackPressedExitState.backPress()
                LeanbackToastState.I.showToast("再按一次退出")
            }
        },
    )
}


/**
 * 退出应用二次确认
 */
class LeanbackDoubleBackPressedExitState internal constructor(
    @IntRange(from = 0)
    private val resetSeconds: Int,
) {
    private var _allowExit by mutableStateOf(false)
    val allowExit get() = _allowExit

    fun backPress() {
        _allowExit = true
        channel.trySend(resetSeconds)
    }

    private val channel = Channel<Int>(Channel.CONFLATED)

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel.consumeAsFlow()
            .debounce { it.toLong() * 1000 }
            .collect { _allowExit = false }
    }
}

/**
 * 退出应用二次确认状态
 */
@Composable
fun rememberLeanbackDoubleBackPressedExitState(@IntRange(from = 0) resetSeconds: Int = 2) =
    remember { LeanbackDoubleBackPressedExitState(resetSeconds = resetSeconds) }
        .also { LaunchedEffect(it) { it.observe() } }

val LeanbackParentPadding = PaddingValues(vertical = 12.dp, horizontal = 24.dp)

@Composable
fun rememberLeanbackChildPadding(direction: LayoutDirection = LocalLayoutDirection.current) =
    remember {
        LeanbackPadding(
            start = LeanbackParentPadding.calculateStartPadding(direction) + 8.dp,
            top = LeanbackParentPadding.calculateTopPadding(),
            end = LeanbackParentPadding.calculateEndPadding(direction) + 8.dp,
            bottom = LeanbackParentPadding.calculateBottomPadding()
        )
    }
