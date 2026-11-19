# Live logos and XMLTV

Settings → 台标与节目单 controls channel logos, current-programme labels, EPG
loading, playlist discovery, optional default feed and the 1/3/6/12/24-hour refresh
interval (default 6 hours). Custom feeds are validated HTTP(S) XMLTV URLs. The
legacy `EPG_URL_LIST` preference and web configuration remain compatible and reactive.
The default 51zmt feed is opt-in; no hidden default request is sent.

M3U adapters read `tvg-logo`, `tvg-id`, `tvg-name`, `x-tvg-url` and `url-tvg`.
Relative logo and EPG URLs resolve against the playlist URL. Logo images use Coil's
memory/disk cache, then the matched XMLTV channel icon if the playlist logo fails,
then a text placeholder. Hidden logos create no image requests.

The normal live menu keeps three columns. Right on a channel opens an optional
fourth schedule column; Left closes it and restores the original channel focus.
Current programmes appear under channel names; current/next also appear in live
playback controls. Playback is never triggered by browsing the schedule.

XMLTV parsing retains display-name aliases, channel icons, timestamps and sorted
programmes. `.xml.gz` is detected by gzip magic bytes, including URLs without a gzip
extension. The transport bounds decoded XML to 64 MB. Parsing and lookup-index
construction run off the UI thread. Matching uses provider-scoped `tvg-id`, then
IDs from explicitly global custom feeds, then exact normalized names/aliases from
eligible feeds. Punctuation, full-width characters and HD suffixes normalize;
substring matching never confuses CCTV1 and CCTV10.

Feeds with the same URL download once; discovered feeds retain their provider
scope. At most two feeds load concurrently. Cached schedules publish before
network refresh; partial failures retain other feeds and existing cached data.
Metadata uses the shared persistent failure diagnostics and exponential backoff
(15 seconds to 10 minutes); explicit refresh bypasses freshness/backoff.
XMLTV metadata permits up to 32 MB per cache entry, within the shared 48 MB /
128-entry budget. Ordinary catalog entries keep their 8 MB limit. Metadata may be
used for up to seven days; old programme timestamps never count as current.

There is no catch-up playback implied by a programme row. Live preferences and EPG
are separate from reusable `:media` HTTP/parsing clients.
