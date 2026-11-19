package me.lsong.mytv

import me.lsong.mytv.ui.navigation.Section

import me.lsong.mytv.domain.live.ProviderLiveCatalog

import me.lsong.mytv.domain.live.mergeLiveCatalogs
import me.lsong.mytv.ui.browser.ContentDrawer
import me.lsong.mytv.ui.settings.SettingsContent
import me.lsong.mytv.ui.theme.TvTheme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import me.lsong.mytv.domain.provider.ProviderKind

import me.lsong.mytv.ui.browser.ContentDrawerState
import me.lsong.mytv.ui.settings.SettingsCategory

import me.lsong.mytv.ui.settings.ProviderSettingsViewModel

/** Debug entry into the actual drawer's settings mode, using persisted providers. */
class SettingsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        val category = if (intent.getStringExtra("category") == "VOD") SettingsCategory.VIDEOS else SettingsCategory.LIVE
        setContent {
            val model: ProviderSettingsViewModel = viewModel()
            val providers by model.providers.collectAsState()
            val playlists = providers.filter { it.kind == ProviderKind.M3U && it.isEnabled }.map { ProviderLiveCatalog(it) }
            val state = remember { ContentDrawerState(null).apply { section = Section.SETTINGS; focusSettings = true; settingsCategory = category; column = 1 } }
            TvTheme {
                ContentDrawer(mergeLiveCatalogs(playlists), null, state, onPlay = {}, onClose = { finish() }, settingsContent = { SettingsContent(it, model) })
            }
        }
    }
}
