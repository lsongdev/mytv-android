@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package me.lsong.mytv.playback.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import me.lsong.mytv.playback.PlaybackState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlayerControls(
    player: PlaybackState, title: String, live: Boolean, speed: Float,
    onToggle: () -> Unit, onSpeed: () -> Unit, onMenu: () -> Unit, onClose: () -> Unit,
    aspectRatioLabel: String, onAspectRatio: () -> Unit,
    danmakuLabel: String? = null, onDanmakuToggle: () -> Unit = {}, onDanmakuSettings: () -> Unit = {},
    liveProgramme: String? = null, restoreFocus: Int = 0, resourceLabel: String? = null, onResource: () -> Unit = {}, onBack: () -> Unit = onClose,
) {
    var position by remember { mutableLongStateOf(player.positionMs) }
    var duration by remember { mutableLongStateOf(player.durationMs) }
    var activity by remember { mutableIntStateOf(0) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var actionFocused by remember { mutableStateOf(false) }
    val clockFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val scrub = remember { FocusRequester() }
    val action = remember { FocusRequester() }
    val aspect = remember { FocusRequester() }
    val danmakuToggle = remember { FocusRequester() }
    val danmakuSettings = remember { FocusRequester() }
    val hasAction = !live || resourceLabel != null
    BackHandler(onBack = onBack)
    LaunchedEffect(restoreFocus) { withFrameNanos { }; scrub.requestFocus() }
    LaunchedEffect(Unit) {
        while (true) { position = player.positionMs; duration = player.durationMs; nowMs = System.currentTimeMillis(); delay(250) }
    }
    LaunchedEffect(activity, player.isPaused, actionFocused) {
        if (!player.isPaused && !actionFocused) { delay(6_000); onClose() }
    }
    Box(Modifier.fillMaxSize().testTag("player-controls").onPreviewKeyEvent {
        activity++
        if (it.key == Key.Menu) { if (it.type == KeyEventType.KeyUp) onMenu(); true } else false
    }) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f))))
                .padding(horizontal = 48.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Color.White,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (!live && danmakuLabel != null) {
                    PlayerControlPill(danmakuLabel, onDanmakuToggle, { actionFocused = it },
                        Modifier.testTag("player-danmaku").focusRequester(danmakuToggle).focusProperties { down = scrub; up = FocusRequester.Cancel; left = FocusRequester.Cancel; right = danmakuSettings })
                    PlayerControlPill("弹幕设置", onDanmakuSettings, { actionFocused = it },
                        Modifier.testTag("player-danmaku-settings").focusRequester(danmakuSettings).focusProperties { down = scrub; up = FocusRequester.Cancel; left = danmakuToggle; right = action })
                }
                if (hasAction) PlayerControlPill(
                    label = if (live) "线路 · $resourceLabel" else "${speed}×",
                    onClick = if (live) onResource else onSpeed,
                    onFocused = { actionFocused = it },
                    modifier = Modifier.testTag(if (live) "player-resource" else "player-speed")
                        .focusRequester(action).focusProperties {
                            down = scrub; up = FocusRequester.Cancel
                            left = if (!live && danmakuLabel != null) danmakuSettings else FocusRequester.Cancel; right = aspect
                        },
                )
                PlayerControlPill("比例 · $aspectRatioLabel",onAspectRatio,{ actionFocused = it },
                    Modifier.testTag("player-aspect-ratio").focusRequester(aspect).focusProperties {
                        down = scrub; up = FocusRequester.Cancel
                        left = if (hasAction) action else FocusRequester.Cancel; right = FocusRequester.Cancel
                    })
            }
            if (live && liveProgramme != null) Text(liveProgramme, color = Color.White.copy(alpha = .75f),
                fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.testTag("player-programme"))
            PlayerScrubber(
                positionMs = if (live) 0 else position,
                durationMs = if (live) 0 else duration,
                live = live, paused = player.isPaused,
                onSeek = {
                    if (!live && duration > 0) {
                        val target = it.coerceIn(0, duration)
                        player.seekTo(target)
                        position = target
                    }
                },
                onToggle = onToggle,
                onUp = { if (hasAction) action.requestFocus() else aspect.requestFocus() },
                focusRequester = scrub,
                modifier = Modifier.testTag("player-progress"),
            )
            if (!live) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (duration > 0) "剩余 ${formatTime((duration-position).coerceAtLeast(0))}" else "剩余 --:--",
                    Modifier.weight(1f).testTag("player-remaining-time"),color = Color.White.copy(alpha = .85f),fontSize = 13.sp)
                Text(if (duration > 0) "总时长 ${formatTime(duration)}" else "总时长 --:--",
                    Modifier.testTag("player-total-time"),color = Color.White.copy(alpha = .65f),fontSize = 13.sp)
                estimatedPlaybackEndMs(nowMs,position,duration,speed)?.let { end ->
                    Spacer(Modifier.width(16.dp))
                    Text("预计结束 ${clockFormat.format(Date(end))}",Modifier.testTag("player-end-time"),
                        color = Color.White.copy(alpha = .55f),fontSize = 13.sp)
                }
            }
        }
    }
}

/** Compact pill sizing and focus treatment adapted from SeleneTV's SpeedPill. */
@Composable
private fun PlayerControlPill(label: String, onClick: () -> Unit, onFocused: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(percent = 50)
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused; onFocused(it.isFocused) },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = .3f)), shape = shape),
            focusedBorder = Border(BorderStroke(0.dp, Color.Transparent), shape = shape),
        ),
    ) {
        Text(label, Modifier.padding(horizontal = 11.dp, vertical = 3.dp),
            color = if (focused) Color(0xFF0A0A0A) else Color.White.copy(alpha = .85f),
            fontSize = 13.sp, fontWeight = FontWeight.W600, maxLines = 1)
    }
}

/** The status icon and track form one focus target; OK toggles either live or VOD playback. */
@Composable
private fun PlayerScrubber(
    positionMs: Long, durationMs: Long, live: Boolean, paused: Boolean,
    onSeek: (Long) -> Unit, onToggle: () -> Unit, onUp: () -> Unit,
    focusRequester: FocusRequester, modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val trackHeight by animateDpAsState(if (focused) 6.dp else 4.dp, tween(160), label = "trackHeight")
    var thumbWidthPx by remember { mutableIntStateOf(0) }
    val toggleLabel = if (paused) "继续播放" else "暂停播放"
    Row(
        modifier.fillMaxWidth().height(32.dp).focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusProperties {
                up = FocusRequester.Cancel; left = FocusRequester.Cancel; right = FocusRequester.Cancel
            }
            .semantics(mergeDescendants = true) {
                stateDescription = if (paused) "已暂停" else "播放中"
                onClick(label = toggleLabel) { onToggle(); true }
            }
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (it.key) {
                    Key.DirectionLeft -> { if (!live) onSeek((positionMs - 10_000).coerceAtLeast(0)); true }
                    Key.DirectionRight -> { if (!live) onSeek((positionMs + 10_000).coerceAtMost(durationMs)); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (it.nativeKeyEvent.repeatCount == 0) onToggle()
                        true
                    }
                    Key.DirectionUp -> { onUp(); true }
                    Key.DirectionDown -> true
                    else -> false
                }
            }.focusable(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (live) Row(
            Modifier.testTag("player-live-indicator"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Default.FiberManualRecord, null, Modifier.size(14.dp), tint = if (paused) Color.Gray else Color(0xFFEF5350))
            Text("LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        } else Icon(
            if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
            toggleLabel, Modifier.size(26.dp).testTag("player-playback-indicator"), tint = Color.White,
        )
        BoxWithConstraints(Modifier.weight(1f).height(28.dp).testTag("player-timeline")) {
            val width = maxWidth
            val widthPx = constraints.maxWidth
            Box(Modifier.fillMaxWidth().height(trackHeight).align(Alignment.Center).clip(CircleShape).background(Color.White.copy(alpha = .25f)))
            Box(Modifier.width(width * if (live) 1f else fraction).height(trackHeight).align(Alignment.CenterStart).clip(CircleShape)
                .background(if (focused) Color.White else Color.White.copy(alpha = .7f)))
            if (focused && !live) Box(
                Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset((widthPx * fraction - thumbWidthPx / 2f).roundToInt()
                        .coerceIn(0,(widthPx - thumbWidthPx).coerceAtLeast(0)),0) }
                    .onSizeChanged { thumbWidthPx = it.width }
                    .testTag("player-time-thumb")
                    .clip(RoundedCornerShape(7.dp)).background(Color.White),
            ) {
                Text(formatTime(positionMs), color = Color(0xFF0A0A0A), fontSize = 12.sp,
                    fontWeight = FontWeight.W700, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
