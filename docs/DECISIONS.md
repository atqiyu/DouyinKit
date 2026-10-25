# 决策记录（ADR）

## ADR-001：解析核心采用纯本地 WebView

**状态**：已采纳

**背景**：参考 `douyin-resolver-api` 用 Playwright 捕获详情接口。Android 端可选薄客户端 + 本地服务、纯 WebView、或纯 HTTP 复刻签名。

**决策**：纯本地 WebView。`ParseActivity` 加载桌面版作品页，document-start 注入 hook 捕获详情 JSON；页面模型/SSR 兜底。

**原因**：
- 用户要求不依赖电脑；
- 详情签名（a_bogus、x-secsdk-web-signature）由页面真实 JS 生成，纯 HTTP 不可靠；
- 分享页纯 HTTP 只有带水印视频、无码率档/LivePhoto/BGM 直链。

**后果**：
- 解析成功率取决于 WebView 能否通过抖音风控；页面改版可能需要更新 JS；
- 解析必须在前台进行；
- 需要维护验证码人工处理入口和诊断信息。

## ADR-002：compileSdk 36 + 固定依赖矩阵

**状态**：已采纳

**决策**：
- compileSdk/targetSdk 36，minSdk 24，AGP 8.13.2，Gradle 8.13；
- Compose BOM 2026.06.01（UI 1.11.4 / Material3 1.4.0），Navigation 2.9.8，Activity 1.12.4；
- Kotlin 2.3.21，KAPT；
- Coil 3.4.0，OkHttp 5.4.0，Media3 1.11.1，Room 2.8.5，DataStore 1.3.0；
- kotlinx.serialization 1.11.0。

**原因**：
- Compose UI 1.12 / Navigation 2.10 / Coil 3.6.3 要求 compileSdk 37 + AGP 9.1；
- Kotlin 2.4 元数据版本 2.4 超出 Room 2.8.5 的 `kotlin-metadata-jvm` 2.3 支持范围，KAPT 直接失败；
- `kotlinx-serialization 1.12.0` 强制 stdlib 2.4.10，进一步固化该冲突；
- 该组合在 compileSdk 36 上构建、测试、Lint 均通过。

**后果**：
- 依赖升级需同时检查 AAR `minCompileSdk` / `minAGP`、Room metadata、serialization stdlib 约束；
- 不能单独把 Kotlin 升到 2.4 或 Compose 升到 1.12；
- targetSdk 36 可在 Android 16/17 设备运行，但不是 API 37 行为级别。

## ADR-003：后台下载不自动重新解析

**状态**：已采纳

**背景**：签名直链约 1 小时过期；后台服务无法可靠运行 WebView。

**决策**：下载全部镜像失败且为 401/403/410 时标记 `NEEDS_REFRESH`，通知/UI 提示用户回前台重新解析；不在服务里创建 WebView。

**后果**：用户需在前台刷新过期链接；已下载文件不受影响。

## ADR-004：清晰度按视频短边判断

**状态**：已采纳

**背景**：竖屏视频 1080P 的宽高是 1080×1920；原逻辑按 height 判断会把 576×1024 误标为 1080P，默认档位也会选错。

**决策**：`VideoGear.shortSide = min(width, height)`，标签与 `VideoGearSelector` 均按短边匹配（1080P = 短边 1080）。

**后果**：横屏/竖屏标签一致；测试覆盖 1080×1920 与 576×1024。

## ADR-005：BGM 直链优先，缺失时 remux 视频音轨

**状态**：已采纳

**决策**：优先 `music.play_url`；缺失或下载失败时下载选中视频到临时目录，用 `MediaExtractor` + `MediaMuxer` 把第一条音轨无损封装为 `.m4a`。

**原因**：不引入 ffmpeg/原生库；设备兼容且体积可控。

**后果**：输出格式为 M4A（而非 MP3）；图文作品若既无音乐直链又无视频，则无法提取，明确提示。

## ADR-006：前台服务 dataSync + 2 并发

**状态**：已采纳

**决策**：`DownloadService` 使用 `foregroundServiceType="dataSync"`，固定 2 并发；一次性下载任务在服务内串行排队。

**后果**：Android 14+ 需要 `FOREGROUND_SERVICE_DATA_SYNC`；Android 15 dataSync 有 6 小时上限（本应用远低于）；并发设置暂未开放到 UI。

## ADR-007：URL 白名单与 SSRF 防护

**状态**：已采纳

**决策**：媒体下载只允许 `douyinvod.com`、`douyinpic.com`、`douyinstatic.com`、`snssdk.com`、`byteimg.com`、`ibytedtos.com`、`bytecdn.cn`、`douyin.com`、`iesdouyin.com` 及其子域；拒绝 IP 字面量、localhost、未知重定向目标。

**后果**：新 CDN 域名需要在 `UrlPolicy` 增加；避免恶意页面让 App 请求内网地址。

## ADR-008：仓库配置阿里云镜像

**状态**：已采纳

**决策**：`settings.gradle.kts` 优先使用阿里云 Google/Public/Gradle Plugin 镜像，保留 `google()` / `mavenCentral()` / `gradlePluginPortal()` 回退。

**原因**：当前网络从官方源下载依赖极慢（<1MB/min），镜像 20–30MB/s。

**后果**：镜像缺失的新构件会自动回退官方源；依赖校验仍由 Gradle 按 Maven 校验和完成。

## ADR-009：底部标签页保留 Composition，剪贴板监听上提

**状态**：已采纳

**背景**：早期用 `AnimatedContent` 包裹整页，切换时销毁并重建页面；资料库/下载页每次都要在主线程重新反序列化全部 Room 快照，剪贴板监听也随首页销毁而重置，造成切页卡顿和横幅重复/丢失。

**决策**：
- `MainScreen` 记录已访问的标签页，保留其 Compose Composition；未显示的页面用零尺寸 layout 跳过测量和绘制；
- 切换只改变 zIndex/alpha/轻微位移，约 180 ms，不再重建整页；
- `HistoryRepository` / `DownloadHistoryRepository` 加进程内 LRU 解码缓存；
- 剪贴板监测上提到 `MainScreen`，在 `ON_START`/`ON_RESUME` 和 `OnPrimaryClipChangedListener` 时读取，横幅状态与已读签名跨标签保留；
- 资料库详情页和解析结果页都作为覆盖层显示，底层标签页保持存活，返回键由覆盖层消费。

**后果**：切页不再触发整页重建和重复 JSON 解码；首次访问仍会有一次布局/图片解码成本。完全后台时 Android 10+ 禁止读取剪贴板，只能回到前台补读。

## ADR-010：下载页按作品聚合，视频封面用 Coil 视频帧

**状态**：已采纳

**决策**：下载记录按 `aweme_id` 分组；同一组内展示多个清晰度/图片/LivePhoto/BGM。作品预览用 `WorkPreviewDialog`：播放器为自定义交互的 Media3 PlayerView，文件详情列出尺寸、帧率、编码、时长、格式和大小。视频作品没有下载封面时，用 `coil-video` 从本地 MP4 解码封面帧；有远程封面时先作为占位。

**后果**：下载页从“任务列表”变为“作品列表”；网格/列表偏好写入 DataStore。视频帧解码首次需要几百毫秒到数秒，期间显示远程封面或类型占位图。

## ADR-011：重复下载按媒体内容识别并提供三种策略

**状态**：已采纳

**背景**：同一作品经常被反复解析，用户可能已经下载过某个清晰度、图片或 BGM；直接再下载会产生无意义的重复文件，而直接覆盖又有下载失败丢文件的风险。

**决策**：
- 用稳定的媒体 key 识别重复：视频按“分辨率 + 编码 + 帧率”，图片/动图按索引，BGM/封面/ZIP 各一个 key；
- UI 在入队前查询 Room + 内存注册表，发现重复时提供“跳过 / 覆盖 / 重复下载”；
- 跳过时，视频/BGM/封面请求直接省略，图集请求写入 `skipMediaKeys`，引擎只下载缺失项；
- 覆盖时把旧文件引用写入 `replaceFiles`，新任务下载到新文件；只有任务完整成功（无警告）后才删除旧文件并清理旧记录；
- 重复下载不做特殊处理，与旧文件并存。

**后果**：覆盖失败或部分失败时不会丢旧文件；新文件名可能因 MediaStore 去重出现 `_1` 后缀。清理记录和物理文件是独立选项，由下载管理界面控制。

## ADR-012：作品预览的操作层级与 LivePhoto 内联播放

**状态**：已采纳

**决策**：作品预览把详情/分享/打开放到右上角，左下角只保留作品信息并提供眼形按钮控制渐隐；文件级详情只在“详情”面板展示。LivePhoto 在图片 Pager 内使用 TextureView 循环播放，点击图片开始，再次点击不暂停，横向滑动交给 Pager 处理。

**后果**：图片预览更接近抖音的信息层级；TextureView 比 SurfaceView 更适合在可滚动 Pager 内播放；信息隐藏时仍保留顶部操作和进度线，但标题与操作变淡。

图文作品预览会通过 `AutoLoopingAudio` 自动循环播放 BGM。为避免声音叠加，打开 LivePhoto 内联视频、切换到视频/音频内容或打开音频试听时暂停，回到普通图片后继续；Activity 后台时暂停，回前台恢复。BGM 优先使用已下载音频，缺失时回退到作品 `music.playUrls`。
