# 协议输入与兼容性

协议解析统一位于 `:media`，应用的 provider adapter 只负责资源身份、相对 URL 和播放请求映射。

## M3U / IPTV

- `#EXTM3U` / `#EXTINF`：保留条目名称、属性及引号中的逗号。
- `#EXTGRP`：条目分组。
- `#EXTVLCOPT:http-user-agent` / `http-referer` / `http-referrer`：逐条目传递 HTTP 请求头，不泄漏至下一条目。
- 相对媒体 URL 按播放列表地址解析。允许 http(s)、RTSP、RTMP、UDP、RTP、SRT 等明确的媒体协议；并非所有后端都支持这些协议。
- 台标和 XMLTV URL 仍限制在 HTTP(S)；非 HTTP 媒体 URI 可以交给支持该协议的播放后端处理。
- 同名频道保留多线路；相同 URL 但请求头不同视为不同资源。

## XMLTV / AppleCMS

- XMLTV SAX 解析拒绝 DOCTYPE/ENTITY，支持时区与节目开始时间排序；多源快照更新需协调并发。
- AppleCMS V10 使用结构化 URL 查询参数，损坏记录跳过，不自动把网页/网盘分享页当作可播放地址。

## 验证

`./gradlew :media:test :app:testDebugUnitTest :app:lintDebug` 验证协议解析、gzip 边界、资源 URL、线路恢复和聚合逻辑。
`./gradlew :app:connectedDebugAndroidTest` 覆盖 Android API 24 / 34 模拟器；无法替代不同电视硬件、运营商组播网络的回归测试。
