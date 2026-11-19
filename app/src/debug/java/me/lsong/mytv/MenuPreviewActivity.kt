package me.lsong.mytv

import me.lsong.mytv.domain.live.mergeLiveCatalogs

import me.lsong.mytv.ui.browser.ContentDrawer
import me.lsong.mytv.ui.settings.SettingsContent
import me.lsong.mytv.ui.theme.TvTheme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

import me.lsong.mytv.ui.browser.ContentDrawerState
import me.lsong.mytv.ui.browser.menuPreviewPlaylists
import androidx.lifecycle.viewmodel.compose.viewModel

import me.lsong.mytv.ui.settings.ProviderSettingsViewModel

/** Deterministic, offline preview of the real drawer. Never included in release builds. */
class MenuPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        setContent {
            TvTheme {
                val playlists = remember { menuPreviewPlaylists() }
                var playing by remember { mutableStateOf(mergeLiveCatalogs(playlists).channels.first()) }
                val drawerState = remember { ContentDrawerState(playing) }
                var visible by remember { mutableStateOf(true) }
                val sourceSettings: ProviderSettingsViewModel = viewModel()
                val backgroundFocus = remember { FocusRequester() }
                LaunchedEffect(visible) {
                    if (!visible) backgroundFocus.requestFocus()
                }
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Color(0xFF243747), Color(0xFF060B13))))
                        .focusRequester(backgroundFocus).focusable()
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyUp && it.key in listOf(Key.DirectionCenter, Key.Enter, Key.Menu, Key.DirectionLeft)) {
                                visible = true
                                true
                            } else false
                        },
                ) {
                    Column(Modifier.align(Alignment.CenterEnd).padding(36.dp), horizontalAlignment = Alignment.End) {
                        Text("菜单预览", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(playing.title, style = MaterialTheme.typography.headlineSmall)
                        Text("离线示例 · OK 打开菜单", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (visible) ContentDrawer(
                        mergeLiveCatalogs(playlists), playing, drawerState,
                        onPlay = { playing = it; visible = false },
                        onClose = { visible = false },
                        settingsContent = { SettingsContent(it, sourceSettings) },
                    )

                }
            }
        }
    }
}
