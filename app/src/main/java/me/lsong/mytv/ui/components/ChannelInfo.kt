package me.lsong.mytv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.lsong.mytv.iptv.TVChannel
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.utils.isIPv6

@Composable
fun MyTvChannelInfo(
    modifier: Modifier = Modifier,
    channelProvider: () -> TVChannel = { TVChannel() },
    channelIndexProvider: () -> Int = { 0 },
    channelSourceIndexProvider: () -> Int = { 0 },
) {
    val channel = channelProvider()
    val channelIndex = channelIndexProvider();
    val sourceIndex = channelSourceIndexProvider()
    val channelNo = (channelIndex+1).toString();
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =  channelNo.padStart(2, '0'),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.alignByBaseline(),
                maxLines = 1,
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = channel.title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.alignByBaseline(),
                maxLines = 1,
            )

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.labelMedium,
                    LocalContentColor provides LocalContentColor.current.copy(alpha = 0.8f),
                ) {
                    val textModifier = Modifier
                        .background(
                            LocalContentColor.current.copy(alpha = 0.3f),
                            MaterialTheme.shapes.extraSmall,
                        )
                        .padding(vertical = 2.dp, horizontal = 4.dp)

                    // 多线路标识
                    if (channel.urls.size > 1) {
                        Text(
                            text = "${sourceIndex + 1}/${channel.urls.size}",
                            modifier = textModifier,
                        )
                    }

                    // ipv4、iptv6标识
                    Text(
                        text = if (channel.urls[sourceIndex].isIPv6()) "IPV6" else "IPV4",
                        modifier = textModifier,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MyTvChannelInfoPreview() {
    LeanbackTheme {
        MyTvChannelInfo(
            channelProvider = { TVChannel.EXAMPLE },
            channelSourceIndexProvider = { 1 },
        )
    }
}