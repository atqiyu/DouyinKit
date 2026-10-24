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
