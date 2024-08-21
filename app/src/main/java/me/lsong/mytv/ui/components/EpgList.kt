package me.lsong.mytv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import kotlinx.coroutines.flow.distinctUntilChanged
import me.lsong.mytv.rememberLeanbackChildPadding
import me.lsong.mytv.data.entities.EpgChannel
import me.lsong.mytv.data.entities.EpgChannel.Companion.currentProgrammes
import me.lsong.mytv.data.entities.EpgProgramme
import me.lsong.mytv.data.entities.EpgProgramme.Companion.isLive
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.utils.Constants.TIME_ZONE
import me.lsong.mytv.utils.handleLeanbackKeyEvents
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
private fun MyTvEpgDayItem(
    modifier: Modifier = Modifier,
    dayProvider: () -> String = { "" },
    currentDayProvider: () -> String = { "" },
    onChangeCurrentDay: () -> Unit = {},
    onFocused: () -> Unit = {},
) {
    val day = dayProvider()
    val dateFormat = SimpleDateFormat("E MM-dd", TIME_ZONE)
    val today = dateFormat.format(System.currentTimeMillis())
    val tomorrow = dateFormat.format(System.currentTimeMillis() + 24 * 3600 * 1000)
    val dayAfterTomorrow = dateFormat.format(System.currentTimeMillis() + 48 * 3600 * 1000)

    val currentDay = currentDayProvider()
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val isSelected = remember(currentDay) { currentDay == day }

    LaunchedEffect(Unit) {
        if (day == today) {
            focusRequester.requestFocus()
            onChangeCurrentDay()
        }
    }

    androidx.tv.material3.Card(
        onClick = { onChangeCurrentDay() },
        modifier = modifier
            .width(130.dp)
            .height(50.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .handleLeanbackKeyEvents(
                onSelect = {
                    onChangeCurrentDay()
                    true
                },
            ),
        colors = CardDefaults.colors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.onBackground
                isFocused -> MaterialTheme.colorScheme.onBackground
                else -> Color.Transparent
            },
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.onBackground),
            ),
            border = Border(
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        ),
    ){
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceAround,
        ) {
            val key = day.split(" ")
            Text(
                text = when (day) {
                    today -> "今天"
                    tomorrow -> "明天"
                    dayAfterTomorrow -> "后天"
                    else -> key[0]
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = if (isSelected || isFocused) MaterialTheme.colorScheme.background
                else MaterialTheme.colorScheme.onBackground,
            )

            Text(
                text = key[1],
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected || isFocused) MaterialTheme.colorScheme.background
                else MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
fun MyTvEpgView(
    modifier: Modifier = Modifier,
    epgProvider: () -> EpgChannel? = { EpgChannel() },
    onUserAction: () -> Unit = {},
) {
    val dateFormat = SimpleDateFormat("E MM-dd", TIME_ZONE)
    val today = dateFormat.format(System.currentTimeMillis())
    val epg = epgProvider()
    if (epg != null && epg.programmes.isNotEmpty()) {
        val programmesGroup = remember(epg) {
            epg.programmes.groupBy { dateFormat.format(it.startAt) }
        }
        var currentDay by remember { mutableStateOf(today) }
        val programmes = remember(currentDay, programmesGroup) {
            programmesGroup.getOrElse(currentDay) { emptyList() }
        }

        val programmesListState = rememberTvLazyListState()
        val daysListState = rememberTvLazyListState()
        val childPadding = rememberLeanbackChildPadding()

        // Find the index of the live programme
        val liveIndex = remember(programmes) {
            programmes.indexOfFirst { it.isLive() }
        }

        // Scroll to the live programme
        LaunchedEffect(liveIndex) {
            if (liveIndex != -1) {
                programmesListState.scrollToItem(liveIndex)
            }
        }

        LaunchedEffect(programmesListState) {
            snapshotFlow { programmesListState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { _ -> onUserAction() }
        }
        LaunchedEffect(daysListState) {
            snapshotFlow { daysListState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { _ -> onUserAction() }
        }

        Column {
            Column (
                modifier = modifier.padding(start = childPadding.start)
            ){
                Text(
                    text = "正在播放：${epg.currentProgrammes()?.now?.title ?: "无节目"}",
                    maxLines = 1,
                )
                Text(
                    text = "稍后播放：${epg.currentProgrammes()?.next?.title ?: "无节目"}",
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.padding(6.dp))
            TvLazyRow(
                modifier = modifier,
                state = daysListState,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(
                    start = childPadding.start,
                    end = childPadding.end,
                ),
            ) {
                items(programmesGroup.keys.toList()) {
                    MyTvEpgDayItem(
                        dayProvider = { it },
                        currentDayProvider = { currentDay },
                        onChangeCurrentDay = { currentDay = it },
                    )
                }
            }
            Spacer(modifier = Modifier.padding(6.dp))

            TvLazyRow(
                modifier = modifier,
                state = programmesListState,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(
                    start = childPadding.start,
                    end = childPadding.end,
                ),
            ) {
                items(programmes) { programme ->
                    MyTvEpgItem(
                        currentProgrammeProvider = { programme },
                    )
                }
            }
        }
    }
}

@Composable
fun MyTvEpgItem(
    modifier: Modifier = Modifier,
    currentProgrammeProvider: () -> EpgProgramme,
    onFocused: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val programme = currentProgrammeProvider()
    val timeFormat = SimpleDateFormat("HH:mm", TIME_ZONE)
    val isLive = programme.isLive()

    LaunchedEffect(isLive) {
        if (isLive) {
            focusRequester.requestFocus()
        }
    }

    androidx.tv.material3.Card(
        onClick = onClick,
        modifier = modifier
            .width(130.dp)
            .height(54.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .handleLeanbackKeyEvents(
                onSelect = {
                    onClick()
                    true
                },
            ),
        colors = CardDefaults.colors(
            containerColor = when {
                isLive -> MaterialTheme.colorScheme.onBackground
                isFocused -> MaterialTheme.colorScheme.onSurface
                else -> Color.Transparent
            },
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.onBackground),
            ),
            border = Border(
                border = BorderStroke(
                    width = if (isLive) 2.dp else 1.dp,
                    color = if (isLive) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onBackground
                )
            )
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                val start = timeFormat.format(programme.startAt)
                val end = timeFormat.format(programme.endAt)
                Text(
                    text = "$start ~ $end",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isLive || isFocused) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onBackground,
                )

                Text(
                    text = programme.title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = if (isLive || isFocused) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onBackground,
                )
            }
            if (isLive) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "playing",
                    tint = MaterialTheme.colorScheme.background,
                )
            }
        }
    }
}

@Preview
@Composable
private fun EpgListPreview() {
    LeanbackTheme {
        MyTvEpgView(
            epgProvider = {
                EpgChannel(
                    id = "CCTV1",
                    programmes = List(200) { index ->
                        EpgProgramme(
                            title = "节目$index",
                            startAt = System.currentTimeMillis() - 3600 * 1000 * 24 * 5 + index * 3600 * 1000,
                            endAt = System.currentTimeMillis() - 3600 * 1000 * 24 * 5 + index * 3600 * 1000 + 3600 * 1000
                        )
                    }
                )
            }
        )
    }
}