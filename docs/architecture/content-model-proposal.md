# 内容模型整理提案

状态：命名和模块边界已落地，实际目录见 [项目结构](project-structure.md)。本文中的 Apps / HOME、收藏历史和自动下一集仍为后续设计。

## 导航与业务分开

第一栏使用 `Section`（Live、Videos、Apps、Settings）。共同特征是可导航的产品入口，
不是所有入口都依赖播放源。Provider 表示内容提供方的运行时实例，ProviderAdapter
表示 M3U、AppleCMS 等接口适配实现，ProviderConfig 表示用户配置。

第二栏 UI 使用 `BrowseCategory`；第三栏称 `ContentPane`，列表/网格项使用 `BrowseEntry`，
详情和编辑表单使用独立的内容状态。这是展示投影，
每个 Section 可以使用不同布局和操作，不把领域对象绑定到三栏或列表/Grid。
BrowseEntry 用带类型的分支：ChannelEntry、VideoEntry、AppEntry、SettingEntry。
共享展示字段与焦点 key，不给所有项塞 url、episodes、packageName 等可空字段。

| Section | 领域中的分组 | 领域中的内容 | 可执行操作 |
| --- | --- | --- | --- |
| Live | ChannelGroup | LiveChannel | 播放其 PlaybackResource |
| Videos | VideoCategory | VideoWork → VideoPart | 播放某一集/正片的 PlaybackResource |
| Apps | AppCollection | InstalledApp | 打开 LaunchTarget |
| Settings | SettingsCategory | SettingEntry | 编辑配置或执行设置操作 |

`Entity` 只作为数据库实体后缀；不将它作为第三栏通用业务名称。
`MediaItem` 容易和 Media3 的 MediaItem 混淆，保留在播放器适配层。

## 提供方、内容与资源

ProviderConfig 描述订阅地址、协议适配类型、名称、启用状态；不表示某条视频 URL。
提供方的原始记录使用 ProviderRecordRef(providerId, remoteId)，不跨提供方比较 remoteId。
聚合产生 LiveChannel / VideoWork 等规范内容，并保留关联的原始记录与匹配证据。
最终应持久化不透明的规范内容 ID；名称、分组、封面变化不应改变收藏和播放历史的身份。
名称归一化和外部 ID 是匹配依据，不是长期数据库主键。

PlaybackResource 是可实际尝试播放的一条资源，包含资源 ID、所属内容/分集、URI、请求头、
可选格式提示和来源引用。直播频道直接关联多条资源；点播先定位 VideoPart，再选择资源。
VideoPart 可以是剧集、电影正片或按日期标识的节目，不强制直播和应用具有“集”。

PlaybackLine 是提供方的一条播放列表，例如 AppleCMS 的某个 play_from。它跨多集，
每集对应不同 URI；因此“线路”与“资源 URL”不是同一层概念。点播资源可以携带
LineRef(providerId, remoteWorkId, remoteLineId)。默认下一集可优先同一线路，失败回退仍限定在当前集。

请求头是资源身份的一部分：同一个 URI 使用不同 UA / Referer，可能是不同可用资源。
APTV 的 http-user-agent / http-referer / http-header 在 M3U 适配层转换为通用 headers，
播放器仅接收 PlaybackRequest，不解析 M3U 或 AppleCMS 字段。

UI 上的“源”应区分配置源（ProviderConfig）和播放资源（PlaybackResource），不继续
让 Source 同时指这两件事。频道分组是成员关系，分类不应进入规范内容 ID。
同一频道可属于多个分组，“全部频道”按规范内容 ID 去重。

直播频道的节目单使用独立 `EpgBinding(epgProviderId, remoteChannelId)` 关联；`tvg-id`
属于提供方/EPG 数据集的标识，不作为全局频道主键。多个节目单绑定可以指向同一规范频道。

APTV 中的 IPv4 / IPv6 分组也说明了分类与资源属性的边界：可以保留原始分组标签，再通过
分类映射统一到“央视”“卫视”；网络类型、清晰度和请求头归播放资源。当前实现保留原始
分组，不因模型讨论而自动重命名或丢弃这些标签。

## 状态与能力

浏览状态按 Section 保存分类和选中条目，与播放状态分开；列表负责焦点恢复和滚动。
聚焦分类/分集只改变浏览状态；OK 才改变正在播放的内容。
当前 PlaybackCoordinator 与 PlaybackState 共同维护内容/分集、资源、进度、暂停和倍速；VOD 同集换资源恢复进度，
直播换资源回到直播边缘。

LiveCatalogProvider 与 VideoCatalogProvider 分别声明所需能力。AppRepository 读取系统应用，
SettingsRepository 管理配置；没有必要让应用启动器和设置实现远程媒体 Provider 接口。
纯 HTTP、M3U 基础解析位于 media 模块；AppleCMS 字段和分隔规则保留在 media/vod/maccms 内部，应用适配器只做模型转换。

## Apps 与 HOME

Apps 的 AppCollection 可先提供全部应用、收藏；InstalledApp 的 LaunchTarget 使用
ComponentName，避免同一包的多个入口被错误合并。优先列出 TV 可启动入口，普通应用入口
可以作为可选补充。Android 11+ 按所查询的 Intent 声明包可见性。

HOME 能力与 Apps 数据模型独立。提供 ACTION_MAIN + CATEGORY_HOME + CATEGORY_DEFAULT 的
独立 intent-filter，保留现有 LAUNCHER / LEANBACK_LAUNCHER。成为默认桌面由系统处理，
API 29+ 可检测并申请 ROLE_HOME；Android 7 使用系统默认桌面选择路径。

建议 HOME 启动默认打开 Apps；冷启动不自动播放电视。普通应用启动可以保留媒体恢复策略。
打开其他 App 时暂停本应用播放器；返回 HOME 时依据入口和 Section 决定是否恢复，
不能简单沿用“每次 Activity.onResume 都恢复视频”的策略。HOME 入口下 Back 不直接退出桌面。

## 迁移进展

1. 已引入 Section、ContentDrawer、BrowseEntry，并将通用列表、影视面板与设置内容分开。
2. 已使用 ProviderConfig；原始记录使用 ProviderRecordRef，聚合内容保留来源。
3. 已统一 PlaybackResource，影视保留 VideoPart、ProviderPlaybackLine 与 PlaybackLineRef。
4. 已引入持久化的规范内容 ID 与 EpgBinding；收藏和最近播放迁移留待这些功能实现。
5. 后续接入 Apps 的本地仓库与启动操作，再增加 HOME 能力和启动/恢复策略。

参考：
- https://developer.android.com/reference/android/content/Intent
- https://developer.android.com/reference/android/app/role/RoleManager
- https://developer.android.com/training/package-visibility

## 原命名与当前命名

| 原类型 | 当前类型 |
| --- | --- |
| DrawerSection | UI 的 Section |
| SourceConfig / SourceRepository | ProviderConfig / ProviderConfigRepository |
| IPTVProvider | M3uLiveProviderAdapter |
| MyTvProviderManager | LiveCatalogRepository（提供聚合目录） |
| TVChannel / TVGroup | LiveChannel / ChannelGroup |
| TVSource | 原始频道记录与 PlaybackResource 分离 |
| VodItem / VodWork | ProviderVideoRecord / VideoWork |
| VodEpisode / VodLine / VodResource | VideoPart / ProviderPlaybackLine / PlaybackResource |
| PlaybackRequest | 保留为播放器边界的已解析请求 |

分类归一化需保存各 Provider 的原始分类 ID 映射，不能把不同源的 remoteId 当作同一分类。
线路可用性和优先级应逐步按 PlaybackResource ID 记录，替代仅记住播放域名的做法。
