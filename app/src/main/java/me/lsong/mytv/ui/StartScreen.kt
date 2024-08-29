package me.lsong.mytv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.lsong.mytv.R
import me.lsong.mytv.ui.components.LeanbackVisible
import me.lsong.mytv.ui.settings.SettingsScreen
import me.lsong.mytv.utils.Constants

@Composable
fun StartScreen(state: LeanbackMainUiState) {
    var isSettingsVisible by remember { mutableStateOf(false) }
    BackHandler(enabled = !isSettingsVisible) {
        isSettingsVisible = true
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Menu && event.type == KeyEventType.KeyUp) {
                    isSettingsVisible = !isSettingsVisible
                    true
                } else {
                    false
                }
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "DuckTV",
                modifier = Modifier.size(96.dp)
            )
            Text(
                text = Constants.APP_NAME,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            when (state) {
                is LeanbackMainUiState.Loading -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .widthIn(300.dp, 800.dp)
                            .height(8.dp)
                    )
                    state.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            modifier = Modifier.sizeIn(maxWidth = 500.dp),
                        )
                    }
                }
                is LeanbackMainUiState.Error -> {
                    state.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.sizeIn(maxWidth = 500.dp),
                        )
                    }
                }
                else -> {} // This case should never happen
            }
        }
    }
    LeanbackVisible({ isSettingsVisible }) {
        SettingsScreen()
    }
}
