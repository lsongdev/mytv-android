package me.lsong.mytv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.lsong.mytv.epg.EpgProgramme
import me.lsong.mytv.iptv.TVChannel
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.utils.handleLeanbackKeyEvents

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.ListItemDefaults
import coil.compose.AsyncImage

@Composable
fun MyTvChannelItem(
    modifier: Modifier = Modifier,
    channelProvider: () -> TVChannel = { TVChannel() },
    epgProgrammeProvider: () -> EpgProgramme? = { null },
    focusRequesterProvider: () -> FocusRequester = { FocusRequester() },
    isSelectedProvider: () -> Boolean = { false },
    isFocusedProvider: () -> Boolean = { false },
    onSelected: () -> Unit = {},
    onFocused: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
) {
    val iptv = channelProvider()
    val focusRequester = focusRequesterProvider()
    val currentProgramme = epgProgrammeProvider()
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isFocusedProvider()) {
            // onInitialFocused()
            // focusRequester.requestFocus()
        }
    }

    CompositionLocalProvider(
        LocalContentColor provides if (isFocused) MaterialTheme.colorScheme.background
        else MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier.clip(ListItemDefaults.shape().shape),
        ) {
            androidx.tv.material3.ListItem(
                modifier = modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused || it.hasFocus
                        if (isFocused) {
                            onFocused()
                        }
                    }
                    .handleLeanbackKeyEvents(
                        key = iptv.hashCode(),
                        onSelect = {
                            if (isFocused) onSelected()
                            else focusRequester.requestFocus()
                        },
                        onLongSelect = {
                            if (isFocused) onFavoriteToggle()
                            else focusRequester.requestFocus()
                        },
                    ),
                colors = ListItemDefaults.colors(
                    // containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    focusedContainerColor = MaterialTheme.colorScheme.onBackground,
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ),
                ),
                selected = isSelectedProvider(),
                onClick = { },
                leadingContent = {
                    if (iptv.icon != null) {
                        androidx.tv.material3.Icon(iptv.icon, iptv.title)
                    }else if (iptv.logo.isNullOrEmpty()) {
                        Text(
                            modifier = modifier
                                .size(40.dp)
                                .background(color = MaterialTheme.colorScheme.primary)
                                .wrapContentHeight(align = Alignment.CenterVertically),
                            textAlign = TextAlign.Center,
                            text = iptv.title.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        AsyncImage(
                            model = iptv.logo,
                            contentDescription = iptv.title,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                },
                headlineContent = {
                    Text(text = iptv.title, maxLines = 2)
                },
                supportingContent = {
                    if (currentProgramme?.title != null) {
                        Text(
                            text = currentProgramme.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.alpha(0.8f),
                        )
                    }
                },
            )
        }
    }
}

@Preview
@Composable
private fun MyTvChannelItemPreview() {
    LeanbackTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MyTvChannelItem(
                channelProvider = { TVChannel.EXAMPLE },
            )

            MyTvChannelItem(
                isFocusedProvider = { true },
                channelProvider = { TVChannel.EXAMPLE },
            )
        }
    }
}