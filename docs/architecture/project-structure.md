# 项目结构

## 模块边界

`:media` 是独立 Kotlin/JVM 模块，集中放置 M3U、XMLTV EPG 和 AppleCMS 的获取、解析、协议模型。
`:app` 依赖它，将协议记录转换为应用领域对象，负责配置、多源聚合、菜单与播放。
`media` 不依赖 Android、Compose、AppContainer、ProviderConfig 或播放器。

```text
media/src/main/kotlin/me/lsong/media/
├── http/                  共享网络传输和端点配置
├── m3u/                   播放列表客户端、解析器和原始模型
├── epg/                   XMLTV 客户端、解析器和原始模型
├── probe/                 HTTP / HLS 资源采样探测
└── vod/maccms/            AppleCMS 客户端、解析器和原始模型

app/src/main/java/me/lsong/mytv/
├── MainActivity.kt        Android 入口，保留组件名称
├── TvApplication.kt       初始化
├── app/                   依赖组装、根画面、播放协调
├── domain/
│   ├── provider/          ProviderConfig、ProviderRecordRef、仓库契约
│   ├── identity/          规范内容 ID 分配契约
│   ├── live/              LiveChannel、ChannelGroup、直播聚合
│   ├── video/             VideoWork、VideoPart、点播聚合
│   ├── epg/               应用内节目单和频道绑定
│   └── playback/          PlaybackResource、PlaybackRequest
├── data/
│   ├── providers/m3u/     协议记录 → 直播领域对象
│   ├── providers/maccms/  协议记录 → 影视领域对象
│   ├── live/              多源加载、进度及错误
│   ├── epg/               节目单客户端适配
│   ├── playback/          线路探测、并发限制与短时缓存
│   ├── preferences/       配置与规范内容 ID 持久化
│   └── remote/            网页配置服务
├── playback/              播放器契约、状态、Media3 / MPV 及控制 UI
├── ui/
│   ├── navigation/        Section：直播、影视、设置
│   ├── browser/           菜单抽屉、BrowseCategory、BrowseEntry、通用列表
│   ├── live/              直播信息
│   ├── video/             影视浏览状态、封面网格、详情
│   ├── settings/          菜单内设置与编辑表单
│   ├── epg/               节目单展示
│   ├── focus/             焦点与滚动策略
│   ├── input/             遥控器与手势
│   ├── components/        通用组件
│   ├── debug/             调试显示
│   └── theme/             TV 主题
└── core/                  通用小工具
```

## 数据流

配置仓库 → 协议客户端 → 应用适配器 → 聚合后的目录 → 菜单 → 选定 PlaybackResource → PlaybackRequest → 播放器。

第一栏 `Section` 是导航入口；`ProviderConfig` 是用户配置的内容提供方。
第二栏 `BrowseCategory` 是 UI 投影，各领域保留自己的 ChannelGroup / VideoCategoryGroup / SettingsCategory。
第三栏按内容使用频道列表、影片网格、详情或设置表单，不建立包含所有可空字段的万能 Entity。

直播频道直接持有多条 PlaybackResource；影视通过 VideoWork → VideoPart → PlaybackResource。
提供方原始记录使用 ProviderRecordRef(providerId, remoteId)，线路引用使用 PlaybackLineRef。
规范频道和影片 ID 在应用中持久化；协议库不决定聚合或长期身份。
请求头跟随播放资源进入播放器，相同 URL、不同请求头可保留为不同资源。

配置存储继续使用原来的 MEDIA_SOURCES、type=IPTV/VOD，以及网页 API 的 sources 字段，
避免包名与领域命名重构覆盖已有配置。旧 IPTV 地址仍通过配置仓库迁移一次。

Apps 启动器、HOME 注册、收藏/历史及自动下一集仍属后续功能。本次没有添加空入口或替代其实现。

影视分类来自 ProviderVideoCategory(ref, title, parentId)，按父子关系及通用语义归入 VideoCategoryGroup。
大类只在至少一个源支持时显示；细分 VideoCategory 筛选位于影片标题行右侧，别名归一化限定在同一大类内。
无法可靠判断的分类保留到“其他分类”；不按提供方名称或固定 remoteId 编写分类规则。
父分类及子分类的原始 ID 通过 VideoCatalogQuery 分别传给接口，每个提供方/分类有独立游标。
大类先请求叶子分类，提供方之间轮流排队，每批至多 8 个查询、同时至多 4 个请求。
更多分类和下一页由网格底部“加载更多”触发；空批次自动继续未浏览分类，切换筛选取消过期任务。
晚到的分类元数据更新保留仍然有效的影片及游标，避免清空当前网格，并遵守已有查询预算。
影片内容类型 VideoKind 仍用于同片匹配和元数据；菜单大类支持情况来自实际源分类。
分类缓存与退避独立于影片请求；分类加载有独立的三路并发，不占用影片列表的四路并发。

网格焦点请求绑定稳定容器，并随分类、筛选及栏目激活状态取消；虚拟化卡片只登记已布局的焦点目标，避免延迟请求访问已移除节点。

影视目录由应用级 VideoCatalogRepository 持有，而不是由菜单生命周期驱动。
启动时先恢复各大类的原始页缓存并生成目录快照，再以低并发后台预热；主菜单仅订阅并切换 VideoBrowseKey 对应的不可变快照。
每个大类/筛选/搜索分别保留影片、已加载页与刷新状态，切换不清空目录、不重新读盘、不重复聚合。
用户当前选中的目录优先于后台预热；相同原始页请求共享，原始数据未变化时保留原来的聚合列表对象。
后台按 100ms 合并一批更新，聚合及身份解析在 Default dispatcher 执行；已打开的菜单可触发过期快照的后台刷新。
内存最多保留 24 个目录快照和 160 个原始页，主要大类优先保留；停用源或修改地址会使旧快照失效。

从播放画面打开菜单时，以当前播放内容作为入口：直播恢复其有效分组并聚焦当前频道；影视直接使用播放会话详情，选中当前分集和线路并聚焦“继续播放”。
影视在开始播放时记住对应的 VideoBrowseKey，返回列表时恢复该分类/筛选/搜索及滚动上下文；直播记住各分组的列表视口。
显示菜单不触发播放或重新拉取影视详情，“继续播放”对相同集/线路只恢复现有播放器；HOME 的应用入口仍可直接定位 Apps。

### 应用级目录快照

`AppContainer.startCatalogues()` 在 `TvApplication` 启动时启动直播、影视和应用目录，页面 ViewModel 不再持有直播加载任务。三个 Repository 分别公开只读 `StateFlow`，负责后台准备和发布快照；它们保留各自的业务 API，不强制使用泛型目录接口。

- `LiveCatalogRepository` 先恢复 M3U 缓存，再并发校验各源，在后台合并频道和分组。“全部频道”列表随快照预先计算；只有加载状态变化或内容未变时复用已有列表。失败保留已有频道，禁用或修改源地址会移除旧记录。
- `VideoCatalogRepository` 保持分类、分页、搜索的后台预热和缓存；启动不依赖菜单是否显示。
- `InstalledAppsRepository` 在后台扫描系统、读取名称和图标并排序，内存快照跨菜单和 Activity 保留。`InstalledAppsObserver` 在应用生命周期内监听包安装、卸载和更新；事件会合并，扫描过程中到达的事件不会丢失。

菜单打开只读取快照并调用 `menuOpened()`，直播/影视按十分钟、应用按一分钟检查是否需要后台刷新；连续打开会复用新鲜数据。显式 `refresh()` 请求由 Repository 执行，直播仍遵守底层源缓存和失败退避策略。刷新期间不清空可见内容，菜单继续维护自己的选择、焦点和滚动位置。
