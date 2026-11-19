@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package me.lsong.mytv.ui.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import me.lsong.mytv.domain.live.LiveChannel
import me.lsong.mytv.domain.epg.EpgChannel
import java.text.SimpleDateFormat
import java.util.*

/** An optional fourth column preserves the channel list; Left returns to its channel. */
@Composable
internal fun LiveSchedulePanel(channel: LiveChannel, schedule: EpgChannel?, active: Boolean,
    focusRequest: Int, onPlay: () -> Unit, onLeft: () -> Unit,
) {
    val entry = remember { FocusRequester() }
    val format = remember { SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val programmes = remember(schedule) { schedule?.programmes.orEmpty().filter { it.endAt > now }.take(200) }
    LaunchedEffect(active,focusRequest) { if (active) { withFrameNanos { }; entry.requestFocus() } }
    LazyColumn(Modifier.fillMaxSize().onPreviewKeyEvent {
        if (it.key == Key.DirectionLeft) { if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) onLeft(); true } else false
    },contentPadding = PaddingValues(8.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = onPlay,modifier = Modifier.focusRequester(entry).focusProperties { up = FocusRequester.Cancel }) { Text("播放 · ${channel.title}") } }
        if (programmes.isEmpty()) item { Text("暂无节目单",Modifier.padding(8.dp),style = MaterialTheme.typography.bodyMedium) }
        items(programmes) { programme ->
            ListItem(selected = programme.startAt <= now && now < programme.endAt, onClick = {},
                scale = ListItemDefaults.scale(focusedScale = 1f),
                headlineContent = { Text(programme.title, maxLines = 2) },
                supportingContent = { Text(format.format(Date(programme.startAt)),style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.focusProperties { if (programme == programmes.last()) down = FocusRequester.Cancel })
        }
    }
}
