# SeleneTV 弹幕实现与 OhMyTV 接入边界

本次以 `../SeleneTV/app/src/main/java/org/moontechlab/selenetv/` 的实际代码为准。
旧 `docs/danmaku-sources.md` 中关于 JS 脚本、只接 B 站的说明已落后于当前代码。
此前为实现梳理；当前移植已经接入 VOD，具体实现和验证范围见 [弹幕移植](danmaku-port.md)。

## 数据并不来自 AppleCMS 播放线路

`service/danmaku/DanmakuSource.kt` 定义 search → getEpisodes → getComments，
统一得到 timeMs、mode、color、content。mode 1 是滚动，4 是底部，5 是顶部。
`DanmakuSources.kt` 目前注册六个原生 Kotlin 实现：B 站、腾讯、爱奇艺、优酷、芒果、乐视。
没有依赖一个统一弹幕后端，也不是在视频 URL 中读取弹幕。

`service/DanmakuService.kt` 根据影片标题并发搜索各源，对标题/季度等匹配打分，保留每源前三个候选。
正文按需请求，当前只选一个源上屏；其他源作为可切换候选，并不把六个源的正文混在一起。
设置里区分完全关闭、手动和自动匹配。完全关闭时不搜索、不请求。

## 按时间窗加载与缓存

编排层以 300 秒为窗口，播放器请求当前窗口和下一窗口，每个源适配自身实际的分段格式。
播放器周期补载，正文增量合并、去重。磁盘缓存按源、影片、集和窗口区分，非空命中保存七天。
弹幕获取失败时返回空，不中断视频播放。

应注意，当前 resolveEpisodeId 在过滤预告等项目后仍主要按集序号取元素，越界还会退回第一集。
OhMyTV 已有多源聚合与缺集、正片、按日期分集，不能照搬这个兜底，否则容易给当前集配上别集弹幕。

## 后台渲染，与播放内核分开

`screen/player/DanmakuOverlay.kt` 通过 Compose AndroidView 放置透明 TextureView，
位于视频层之上、控制层之下。

`ui/danmaku/` 是自研引擎，不是当前仍在使用 akdanmaku：

- `DanmakuTextureView` 管 Surface 和线程生命周期，销毁时停止并回收线程及 Surface。
- `DanmakuRenderThread` 用 HandlerThread + Choreographer，后台 lockHardwareCanvas / drawText。
- `DanmakuEngine` 管时间游标、活跃集、滚动/顶部/底部轨道与防重叠；没有位图池，也不做每条 saveLayer。
- 时钟按播放器倍速推进；暂停会冻结，缓冲期有单独行为。每两秒检查一次与宿主位置的漂移，超过 1200 ms 时纠正。
- 配置包括显示区域、透明度、字号、滚动速度、密度、防重叠；低内存设备另有降密度策略。

这部分只需要播放器的进度、暂停/播放、缓冲和倍速状态，可以同时服务 Media3 与 MPV。

## 在 OhMyTV 中应如何拆分

| 位置 | 职责 |
| --- | --- |
| media/danmaku | 平台协议模型、查询和解析客户端；不同平台的签名/Protobuf/压缩细节分别放在各自包中 |
| app/domain/danmaku | 用 VideoWork / VideoPart 匹配弹幕影片与分集，保存明确绑定和时间偏移 |
| app/data/danmaku | 候选查询、分片预取、取消、去重与有上限的缓存 |
| app/playback/danmaku | 纯调度引擎、Android 渲染线程、TextureView 和 Compose 叠加层 |
| 播放控制 | 弹幕开关、源/分集纠错、显示设置、时间偏移 |

匹配应使用规范作品/分集身份、年份、季度和集号/日期等信息，缺少对应项时允许手动选择，不能直接退到第一集。
同一 VideoPart 切播放线路时保留弹幕绑定；不同剪辑版通过 offsetMs 调整时间差。
先支持 VOD；IPTV 的直播时间轴不能直接套用这种点播分集匹配。

移植时保留渲染引擎的后端独立性，但将原来同步阻塞的源请求改成可取消的 suspend 客户端。
不把 AppDataService 单例、旧播放器页面状态或平台协议字段带入共享引擎。

## 本次已经对齐的控制条

倍速/直播线路使用进度条上方的紧凑胶囊，13 sp 字号、横向 11 dp / 纵向 3 dp 内边距。
进度条上键进入胶囊，下键回进度条；胶囊操作期间控制条不自动隐藏。
点播进度条下方显示已播时间、预计结束时刻与剩余时长；结束时刻按当前倍速折算，未知总时长不显示。
直播继续显示 LIVE，不伪造固定结束时间。
