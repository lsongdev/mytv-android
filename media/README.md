# Media protocols

A standalone Kotlin/JVM library, targeting Java 8 bytecode for Android 7+.
It has no Android, Compose, application preferences, aggregation or player dependency.

```
me.lsong.media/
  http/           MediaEndpoint, bounded cancellable HTTP transport
  m3u/            M3uClient, M3uParser, M3uDocument / M3uEntry
  epg/            EpgClient, XmltvParser, EpgDocument / EpgChannel / EpgProgramme
  probe/          StreamProbe: bounded HTTP / HLS media samples
  vod/maccms/     MacCmsClient, MacCmsParser, MacCmsPage / MacCmsVideo / MacCmsLine / MacCmsPart
```

Clients expose suspend functions and accept `MediaEndpoint(uri, headers)`.
Parsers accept downloaded text and can be used without network access.
HTTP handles UTF-8 BOMs, gzip detection, timeouts, cancellation and response size limits.

```kotlin
val playlist = M3uClient().fetch(MediaEndpoint("https://example.com/live.m3u"))
val schedule = EpgClient().fetch(MediaEndpoint("https://example.com/epg.xml.gz"))
val endpoint = MediaEndpoint("https://example.com/api.php/provide/vod")
val page = MacCmsClient().page(endpoint, MacCmsQuery(keyword = "电影", page = 1))
val details = MacCmsClient().details(endpoint, remoteId = "123")
```

M3U preserves playlist attributes. Application adapters interpret channel identities,
EPG bindings and stream header attributes. AppleCMS field names, API parameters and
play-list delimiters stay in `vod/maccms`, outside the shared HTTP and M3U packages.
XMLTV timestamps are epoch milliseconds. AppleCMS marks web-player links as not directly
playable; the application decides whether to offer those parts.

Run `bash ./gradlew :media:test` without an Android device.

`StreamProbe.measure(endpoint)` follows up to two HLS playlists to sample a media segment,
preserving endpoint headers and redirect-relative URLs. It reads at most 256 KiB from
each response and cancels the call when that bound is reached. The overall deadline
is six seconds. Latency measures time to the first media byte including playlist
resolution; speed is sample bytes divided by the media request duration, including
its connection/response overhead. These are indicative samples, not sustained
bandwidth or a guarantee of decoding/playback success.

The `danmaku` package supplies six built-in platform clients with scoped media/episode
IDs and timestamped comments. Calls run on IO and cancel active HTTP work on
coroutine cancellation. Platform adapters are internal; no Android/UI or VOD
canonical identities enter the protocol library.
