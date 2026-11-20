<h1 align="center">
  <img src="./design/branding/icon-square-512.png" alt="OhMyTV Icon" width="200">
  <br>OhMyTV<br>
</h1>

<p align="center"><em>:tv: Simple IPTV App for Android TV</em></p>

<p align="center">
  <a href='https://play.google.com/store/apps/details?id=me.lsong.mytv'>
    <img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' width='250' />
  </a>
</p>

<p align="center">
  <a href='https://github.com/lsongdev/mytv-android/releases'>Get nightly build on GitHub Releases</a>
</p>

----

### TV menu preview

Build and install the debug APK, then open the offline preview on a connected TV:

```sh
bash ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n me.lsong.mytv/.MenuPreviewActivity
```

The preview uses sample playlists and does not play network video. Use left/right
to move between sources, groups and channels; press OK to select a channel, and
Back to close the drawer. In the main app, short Back first hides visible playback controls; otherwise it toggles between
playback and the drawer. Hold Back to finish and
remove the app task; releasing that hold never triggers a second Back action.
OK, left or right first opens playback controls; up/down changes live channels
(with the existing reverse-direction preference). On the focused timeline,
left/right seeks VOD by ten seconds and OK toggles pause for both VOD and live TV.
The play/pause icon or LIVE indicator shares the timeline's focus target. The focused VOD timeline uses its current-time pill as the thumb, centered on the track, rather than a floating bubble above a dot.
Up reaches the compact speed or live-resource pill above the timeline; down returns to the timeline. The menu key also
opens the drawer. Playing controls hide after six seconds of inactivity while the timeline has focus. Pills keep controls visible during adjustment. VOD also shows the estimated finish clock (adjusted for speed) and remaining duration.

`MenuPreviewActivity` and its sample data are only included in debug builds.

### Settings inside the drawer

The first column contains **直播**, **影视**, **应用** and Settings. Focus Settings at the bottom. The second column shows settings
categories in the order 常规, 直播, 影视, 台标与节目单, 弹幕, 关于; the third manages IPTV M3U sources, AppleCMS V10 VOD endpoints and
preferences. Source forms stay in the third column. Back returns to playback; use Cancel to leave the editor. The underlying player remains mounted.

**应用** lists installed launchable apps with their names and icons. Use up/down to select,
OK to launch, and left to return to the first column. Right expands app details with
package/version information and Open, App info, and Uninstall actions. Left or Back
collapses details and restores app focus. Uninstall opens the system confirmation;
system apps can be managed through App info. TV launch activities take priority;
apps with ordinary Android launch activities are included too. The list refreshes after
installation/removal and when returning from another app.
OhMyTV can also be selected as the default Home app in Android system settings.
Once selected, booting the device or pressing Home opens the **应用** section.

Sources have a stable ID, name, URL, type (`IPTV` or `VOD`) and enabled state.
IPTV changes refresh the channel catalog without restarting the player.
The existing QR-code web configuration uses the same source repository.

### Aggregated VOD

Enabled AppleCMS V10 JSON sources appear under **影视** in the first column.
The second column filters recently fetched results by type, offers cross-source
search, pagination, refresh and retry; the third shows a four-column poster grid.
Opening a poster keeps everything inside the drawer: the second column retains
categories, while the third shows the synopsis followed by horizontal episode and
resource rows. The drawer has a translucent background so video remains visible. Browsing never starts playback.

Matching uses external IDs when available, otherwise title, year, type, area,
season and edition. Missing years and conflicting IDs are handled conservatively.
Provider-local film IDs never act as global identities. Posters fall back to the
other providers’ image URLs when a CDN fails, with a placeholder if none load. Catalog requests are lazy,
limited to four concurrent requests, and retain separate page cursors and errors.
Opening a work also searches the other enabled providers for matching resources
outside their latest page, with three concurrent detail tasks.

Episode numbers and dates align resources across sources; unknown labels remain
source-specific rather than being matched by list position. Selecting an episode
or resource starts playback. Switching resources for the same episode preserves
position; a failed resource tries the remaining resources of that episode once.
Pause/resume and playback speed are available in the player control bar.
The details pane keeps its play button above the episode row. Focus stays inside
the third pane when reaching its bottom; left returns to the preceding column. The underlying Media3 player remains
mounted while browsing. Direct HLS and file media URLs are supported; web-player
and share-page links are excluded. Fetching every provider's full catalog, external
web-player parsing, playback history and automatic next-episode playback are not
part of this first implementation.

### Player backends

The SeleneTV player core is adapted to this app’s playback requests and persistent
player surface: Media3 remains the default, with optional libmpv on Android 8+.
Android 7 always uses Media3 and does not initialize libmpv. A failed MPV native
initialization falls back to Media3. Both backends support software/hardware
selection, seeking, pause/resume and VOD speed controls; the MPV backend carries
over SeleneTV’s buffering, reconnect and replacement-event handling.

Choose the backend and decoder in Settings → 常规; these preferences take
effect on the next app launch. Source menus and AppleCMS parsing remain separate
from the player. SeleneTV’s server authentication and full-screen browsing screens are not imported. The danmaku protocols and renderer are adapted separately. Compile SDK 36, Kotlin 2.2.10 and AGP 8.12 are required
by the MPV dependency; minSdk remains 24 and targetSdk remains 34.

### Focus and scrolling

`ui/focus/FocusScrolling.kt` defines a shared edge-based bring-into-view policy,
provided by `TvTheme`. Visible focused items keep their positions; clipped
items scroll only enough to become visible. Focus restoration in lists and grids
positions offscreen targets only, rather than aligning every selection to the
start of the viewport. This also applies to nested episode/resource rows and
settings lists, avoiding the default TV 30% pivot scroll after every focus change.

### Aggregated live TV

The **直播** entry combines enabled M3U sources. Matching normalized channel names
share a channel with ordered, distinct playback URLs; resources retain the provider
ID and name. Generic regional names retain group context. Original group memberships
remain browsable, while **全部频道** counts each aggregated channel once. Stable
channel IDs survive removal of the first provider. Successful providers appear as
soon as they finish; a slow or failed provider does not hold back the others.

Playback errors try the next URL. When a live channel has multiple resources, the
player control bar offers a line button showing its provider and resource number.
Individual providers remain configurable under Settings → 直播 / 影视. Source rows
open their inline editor with OK and have no pencil icon.

### Code organization

Protocol clients, parsers and wire models live in the standalone [`media`](media/README.md) module.
The app maps them into provider-scoped records, canonical live/video catalogs and shared playback resources.
See [project structure](docs/architecture/project-structure.md) for package ownership and data flow.

### Resource indicators

VOD resource cards show response latency and sampled transfer rate on their right.
Green/orange/red distinguish fast, intermediate and slow samples, with separate
waiting, testing, timeout and failure states. Only the current part is probed,
with two concurrent requests; switching parts or closing the details cancels work.
Successful results are cached for three minutes, failures for thirty seconds,
with at most 128 cached resources. Probes preserve stream headers and inspect HLS
media segments rather than treating a small playlist download as bandwidth.
Measurements are approximate and do not reorder or disable playback resources.

Poster grids reserve 12 dp for focus scaling/borders and use an edge-only scroll
policy with the same inset, including when bringing middle rows into view.
Search is the first VOD category; Recently Updated retains the initial focus. Opening a work focuses Play. Episode/resource rows reserve border space, keep stable per-card focus targets and stop held Left at their boundary; another fresh Left press exits the pane.

See the [SeleneTV danmaku implementation review](docs/architecture/selenetv-danmaku.md) for the data, renderer and matching boundaries. Danmaku is now available through the VOD playback controls; see the [port and usage notes](docs/architecture/danmaku-port.md).

Provider metadata caches, last response diagnostics and capped failure backoff are documented in the [cache policy](docs/architecture/provider-cache-policy.md).

See [live logos and EPG](docs/architecture/live-epg.md) for playlist discovery, matching, caching and the optional fourth schedule column. Danmaku platform switches live in Settings → 弹幕; playback matching/display settings use a translucent drawer on the right.

See [CI and releases](docs/architecture/ci-release.md) for automatic tests, signed APK publishing and signing-key backup.

### Live logos and programme guide

Settings → 台标与节目单 controls logos, current programme text, EPG enablement,
M3U discovery, the optional default provider, refresh interval and custom XMLTV URLs.
Cached schedules appear first; per-feed status includes last response time, latency,
errors and cached time. On a channel press Right to open the optional fourth-column
schedule; Left restores its channel focus. The M3U logo falls back to the XMLTV icon,
then channel initials, with normal Coil memory/disk caching.
See [EPG behavior and cache policy](docs/architecture/live-epg.md).

Settings → 弹幕 enables the six built-in danmaku data providers. The playback
弹幕设置 button opens a translucent drawer from the right for film/episode matching,
rendering and timing; platform enablement stays in the main settings menu.

### Automated APK releases

Pull requests run unit tests, Lint and Android 7 / Android 14 instrumented tests.
Successful master builds publish a signed Release APK and checksums as a GitHub
nightly prerelease; version tags publish normal releases. See
[CI and signing](docs/architecture/ci-release.md).

Danmaku now merges independent platform matches. The playback drawer separates
**弹幕样式** and **弹幕来源**. Sources are non-focusable section headings; each flat
candidate row identifies both a film and an episode. OK selects/enables one row per
source, or disables the selected row. Multiple source sections can be active.
Left/right on the chosen row shifts that source by one second. Other episodes expand
inline; no intermediate film/episode picker is required. Offsets and manual matches
persist per work/episode/provider, with the movie default used for unadjusted episodes.

Playback controls include an immediate aspect-ratio cycle (Original → 16:9 → 4:3 →
Stretch), shared with General Settings. The timeline thumb shows current position;
below it, remaining duration stays on the left, with total duration and estimated
finish time on the right. The finish estimate reflects playback speed. Opening the
danmaku drawer freezes both video and comments rather than looping a preview.

The app checkpoints its last live/VOD session every five seconds and on playback
state changes or leaving the activity. Startup restores the channel/episode, line,
VOD position, playback speed and pause intent before choosing any default channel.
A cached VOD episode starts immediately while provider details refresh in the
background. Disabled providers are excluded; failed lines retain progress during
fallback. Live playback restores its channel/line at the live edge. Only the current
episode and compact work metadata are persisted, rather than entire catalogs.
