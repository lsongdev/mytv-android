<div align="center">
  <img src="design/branding/icon-rounded-512.png" alt="OhMyTV 图标" width="112" height="112">
  <h1>OhMyTV</h1>
  <p><strong>打开电视，回到观看本身。</strong></p>
  <p>为 Android TV 打造的直播与影视播放器。聚合多个内容源，用遥控器轻松浏览与播放。</p>
  <p>
    <a href="https://github.com/lsongdev/mytv-android/actions/workflows/android.yml"><img src="https://github.com/lsongdev/mytv-android/actions/workflows/android.yml/badge.svg" alt="Android CI"></a>
    <img src="https://img.shields.io/badge/Android-7.0%2B-69E1CF?logo=android&logoColor=white" alt="Android 7.0 及以上">
    <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.2.10">
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-14263D" alt="Jetpack Compose">
  </p>
  <p>
    <a href="https://github.com/lsongdev/mytv-android/releases"><strong>下载 APK</strong></a> ·
    <a href="https://play.google.com/store/apps/details?id=me.lsong.mytv">Google Play</a> ·
    <a href="#快速开始">快速开始</a> ·
    <a href="#本地开发">本地开发</a> ·
    <a href="https://github.com/lsongdev/mytv-android/issues">反馈问题</a>
  </p>
</div>

---

## 功能亮点

| | 能力 | 说明 |
| :---: | --- | --- |
| 📺 | 多源直播 | 聚合 M3U 播放列表，合并同名频道并保留分组；多条线路可手动切换，播放失败自动尝试下一条。 |
| 🎬 | 影视聚合 | 接入 AppleCMS V10 JSON 接口，支持跨源搜索、最近更新、分类浏览与分页；同名分类合并展示。 |
| 🧩 | 多源选集 | 聚合匹配影片的剧集与资源，同一集切换线路保留进度；展示线路响应延迟和采样速率。 |
| 🗓️ | 台标与节目单 | 支持 XMLTV / gzip、自定义 EPG 地址及 M3U 自动发现；查看当前节目和频道日程。 |
| 💬 | 多平台弹幕 | 内置六个弹幕数据提供方，支持多源合并、手动匹配、样式调整和各来源独立时间偏移。 |
| ▶️ | 双播放内核 | 默认 Media3，Android 8+ 可选 MPV；支持软硬解、暂停、点播拖动、倍速和画面比例切换。 |
| 🕹️ | 遥控器优先 | 多栏抽屉、焦点恢复与边缘滚动，浏览菜单时保留播放器；可通过局域网网页配置内容源。 |
| 🏠 | 电视应用入口 | 浏览、启动和管理已安装应用；可在系统设置中选择为默认 Home 应用。 |
| ⏯️ | 恢复观看 | 重启后恢复上次频道或剧集、线路、点播进度、倍速及暂停状态。 |

## 快速开始

### 1. 安装

从 [GitHub Releases](https://github.com/lsongdev/mytv-android/releases) 下载 APK，在电视或盒子上安装。

- **系统要求**：Android 7.0（API 24）及以上。
- **支持架构**：ARMv7、ARM64、x86_64，CI 发布通用 APK。
- **发布渠道**：`master` 构建通过检查后发布 Nightly 预发行版，`v*` 标签发布正式版本。

也可以通过 ADB 安装下载的 APK：

```sh
adb install -r /path/to/OhMyTV.apk
```

### 2. 添加内容源

在主菜单底部进入 **设置**，按内容类型添加并启用来源：

| 设置入口 | 接受的来源 |
| --- | --- |
| 设置 → 直播 | M3U 播放列表地址 |
| 设置 → 影视 | AppleCMS V10 JSON 接口地址 |
| 设置 → 台标与节目单 | XMLTV 或 `.xml.gz` 节目单地址，也可开启从 M3U 发现节目单 |

不方便用遥控器输入时，让手机或电脑与电视连接同一局域网，进入 **设置 → 关于**，扫描二维码或打开页面上显示的网址，即可通过网页管理内容源和 EPG。

点播支持直接可播放的 HLS 与文件媒体地址；网页播放器和网盘分享页链接暂不支持解析。

### 3. 开始观看

- **直播**：选择分组和频道，按确认键播放；多线路频道可在播放控制栏切换资源。
- **影视**：搜索、查看最近更新，或按电影、剧集、动漫等大类浏览；网格上方可按类型或地区细分筛选，网格底部可加载更多。只显示源中实际支持的大类，无法可靠归类的内容保留在「其他分类」。打开影片后选择播放、剧集或资源，浏览详情不会自动开始播放。
- **弹幕**：在 **设置 → 弹幕** 启用平台，再从播放控制栏打开弹幕设置，调整匹配、样式和时间偏移。
- **应用**：选择已安装应用并启动；按右键展开版本信息及打开、应用信息、卸载等操作。

## 遥控操作

按键行为随当前焦点变化：

| 场景 | 按键 | 操作 |
| --- | --- | --- |
| 播放画面 | 确认 / 左 / 右 | 显示播放控制栏 |
| 直播画面 | 上 / 下 | 切换频道，可在常规设置中反转方向 |
| 播放控制栏时间轴 | 确认 | 暂停或继续播放 |
| 点播时间轴 | 左 / 右 | 后退或前进 10 秒 |
| 播放控制栏时间轴 | 上 | 移至倍速或直播线路选项 |
| 主界面 | 菜单键 | 打开菜单抽屉 |
| 主界面 | 短按返回 | 先隐藏可见的播放控制栏；否则在播放画面与菜单抽屉之间切换 |
| 主界面 | 长按返回 | 退出并移除应用任务 |
| 菜单抽屉 | 方向键 / 确认 | 在栏目、分类和内容之间移动并选择 |
| 频道列表 | 右 | 打开节目单栏，按左返回频道 |
| 应用列表 | 右 | 展开应用详情，按左或返回收起 |

播放控制栏还可切换画面比例：**原始 → 16:9 → 4:3 → 拉伸**。点播时显示剩余时长和随倍速调整的预计结束时间。

## 播放与兼容性

| 项目 | 行为 |
| --- | --- |
| 默认内核 | Media3 |
| MPV | Android 8+ 可选；原生初始化失败时回退到 Media3 |
| Android 7 | 固定使用 Media3，不初始化 MPV |
| 内核与解码设置 | 在 **设置 → 常规** 修改，下次启动生效 |
| 播放恢复 | 自动保存最近一次直播或点播会话；直播回到直播位置，点播恢复保存进度 |
| 资源测速 | 延迟和速率为短时采样参考值，不代表持续带宽，也不自动调整资源排序 |

当前支持恢复最近一次播放；完整观看历史、自动播放下一集和外部网页播放器解析尚未实现。

## 本地开发

### 环境

- JDK 17
- Android SDK Platform 36、Build Tools 35.0.0
- Kotlin 2.2.10、Android Gradle Plugin 8.12.0（由项目管理）

### 构建与安装

```sh
git clone https://github.com/lsongdev/mytv-android.git
cd mytv-android
bash ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 检查

```sh
# 协议模块、应用单元测试与 Lint
bash ./gradlew :media:test :app:testDebugUnitTest :app:lintDebug

# 连接 Android 设备或模拟器后运行界面与集成测试
bash ./gradlew :app:connectedDebugAndroidTest
```

CI 同时覆盖 Android 7（API 24）与 Android 14（API 34）模拟器。签名、版本号和发布流程见 [CI 与发布](docs/architecture/ci-release.md)。

<details>
<summary><strong>离线菜单预览</strong></summary>

安装 Debug APK 后执行：

```sh
adb shell am start -n me.lsong.mytv/.MenuPreviewActivity
```

预览使用示例播放列表，不播放网络视频，可用于查看多栏菜单、焦点移动和频道选择。`MenuPreviewActivity` 及示例数据仅包含在 Debug 构建中。

</details>

### 项目结构

```text
.
├── app/                   Android 应用：界面、配置、内容聚合与播放
├── media/                 独立 Kotlin/JVM 协议库
├── design/branding/       图标、横幅与品牌资源
├── docs/architecture/     架构、功能边界与维护文档
└── .github/workflows/     自动检查、构建与发布
```

`media` 负责 HTTP、M3U、XMLTV、AppleCMS、资源探测与弹幕协议，不依赖 Android、Compose 或播放器。应用层负责内容身份、多源聚合、配置和播放协调；平台专属协议保留在各自适配器中。

## 深入了解

| 文档 | 内容 |
| --- | --- |
| [项目结构](docs/architecture/project-structure.md) | 模块边界、包职责与数据流 |
| [Media 协议库](media/README.md) | 客户端、解析器与调用示例 |
| [台标与节目单](docs/architecture/live-epg.md) | 来源发现、频道匹配、缓存及节目单交互 |
| [弹幕移植与使用](docs/architecture/danmaku-port.md) | 弹幕接入、匹配与渲染 |
| [弹幕实现评审](docs/architecture/selenetv-danmaku.md) | 数据、渲染及匹配边界 |
| [提供方缓存策略](docs/architecture/provider-cache-policy.md) | 元数据缓存、请求诊断与失败退避 |
| [CI 与发布](docs/architecture/ci-release.md) | 自动测试、签名 APK 与发布流程 |
| [品牌资源](design/branding/README.md) | 图标、电视横幅及资源生成 |

## 致谢与许可

本项目基于 [yaoxieyoulei/mytv-android](https://github.com/yaoxieyoulei/mytv-android)，感谢原作者的工作。部分播放内核与弹幕实现适配自 SeleneTV，相关边界与移植说明见上方文档。

许可及附加条款请阅读 [LICENSE](LICENSE)；上游项目采用 MIT 许可。
