# 架构

## 目标

DouyinKit 是单模块原生 Android App，核心约束是**无服务器、无账号、不上传数据**。所有解析都在手机的 WebView 内完成，所有下载都直连抖音 CDN，所有状态保存在本机。

```
Compose UI
  ├─ Home / Clipboard
  ├─ ParseActivity (WebView)
  ├─ Result / Preview / Downloads (grouped by work, sorted, collections) / Library / Settings
  └─ DownloadService (foreground)
        │
Domain / data
  ├─ LinkExtractor / ShortLinkResolver
  ├─ DouyinWebViewEngine + hook.js + page_model.js
  ├─ DouyinDetailNormalizer
  ├─ DownloadEngine + FileDownloader + MediaStoreSaver
  ├─ AudioExtractor + ZipExporter
  └─ Room (history, favorites/tags, download history) + DataStore settings
```

## 解析链路

1. `LinkExtractor` 从任意文本提取抖音 URL，保留短码大小写，清洗中文标点；也接受裸 aweme_id。
2. `ShortLinkResolver` 用 OkHttp `followRedirects=false` 读取短链 302 `Location`，正则取 8–25 位作品 ID；长链直接走路径解析。
3. `ParseActivity` 创建单个 `DouyinWebViewEngine`，全屏 WebView 位于解析进度层下方，使用桌面 Chrome UA、持久 Cookie、禁用文件访问。
4. `hook.js` 在 document-start 注入（不支持时退化为 `onPageStarted`），包装 XHR/fetch，只捕获 `/aweme/v1/web/aweme/detail/`。
5. 详情 JSON 在 JS 侧按白名单裁剪，经 `WebMessageListener` 回传；旧 WebView 用 `@JavascriptInterface` 回退。桥只接受合法 aweme_id、大小受限的 JSON，并校验来源域名。
6. `hook.js` 的 `__DK_getState` 只把可见的验证码 DOM、验证 iframe（含开放 shadow root）、明确的安全验证文案和验证 URL 标记为 `challenge`；登录 URL/文案标记为 `login`。`challenge` 与 `login` 互不混淆，避免把“验证码登录”当成安全验证。
7. `ParseController` 只在 `challenge` 时自动展开 WebView 并延长到 120 秒；`login` 只更新“需要登录/可手动显示页面”的提示，不自动展开。页面加载超时不再自动展开，验证阶段也不会被 `/note/` 阶段重置。验证 iframe 和登录/验证主页面域名加入 WebView 导航白名单。
8. `DouyinDetailNormalizer` 宽容解析 snake_case 与 camelCase，生成 `Aweme`：视频档位、封面、作者、统计、图集、LivePhoto、BGM。
9. 图文作品额外打开 `/note/<id>`，`page_model.js` 遍历 React fiber / SSR 数据，补齐 LivePhoto、fps、codec、playApi；与详情结果按档位名/图片 uri 合并。
10. 结果保存到 Room，交给 `ParseSessionStore`，`MainActivity` 展示结果页。

超时：总等待 45 秒；只有检测到安全验证时才延长到 120 秒并显示 WebView；页面模型兜底 22 秒；图文额外 10 秒。登录页不会自动展开。

## 下载链路

```
DownloadRequest (Aweme + kind + gear + options)
  → 重复检查（记录 + 内存任务，按清晰度/图片/动图/BGM/封面/ZIP 的稳定 key）
  → DownloadService（前台服务，2 并发）
      → DownloadEngine
          ├─ 视频：gear.urls → playApi → 必要时 defaultUrls
          ├─ 图片：urls → 可选 downloadUrls（水印兜底）；imageIndexes 限定部分图片，skipMediaKeys 跳过已存在项
          ├─ LivePhoto：urls → playApi → 可选 downloadUrls
          ├─ BGM：music.play_url 直链 → 失败时下载视频并 remux 音轨
          └─ ZIP：媒体 + info.json
      → 覆盖策略：新内容完整保存后按 replaceFiles 删除旧文件/旧记录
      → FileDownloader（Range 续传、镜像容错、magic/大小校验）
      → MediaStoreSaver（API 29+ IS_PENDING；API 24–28 公共目录 + MediaScanner）
  → DownloadProgressTracker（字节/项目聚合、限频回调）
  → DownloadRegistry（内存进度）
  → Room download_history（状态、文件 URI、重试请求）
```

- `DownloadService` 使用 `dataSync` 前台类型，通知显示总进度、取消全部；Android 13+ 通知权限在 UI 中请求。
- `DownloadEngine` 先把文件下载到 app-specific external temp，成功后再发布到 MediaStore；任务结束清理 temp。
- 视频候选失败全部为 403/401/410 时抛出 `EXPIRED`，服务标记 `NEEDS_REFRESH`，提示用户回前台重新解析；后台不会启动 WebView。
- 下载 URL 经过 `UrlPolicy` 白名单，拒绝内网/IP/未知域名；OkHttp 最终重定向地址也会校验。
- 重复检查在 UI 触发下载前执行：已有文件按媒体 key 汇总，提示用户跳过、覆盖或仍然重复下载。跳过会给图片请求写入 `skipMediaKeys`；覆盖把旧文件引用写入 `replaceFiles`，后台在新任务完整成功后清理，失败则保留旧文件。
- 下载管理支持按作品/文件级删除。删除本地文件使用 MediaStore（旧系统回退到文件路径 + MediaScanner），只有文件删除成功才移除对应记录；也可只删记录保留文件。
- `DownloadProgressTracker` 用 `baseBytes`（已完成项）+ `currentBytes`（当前项）聚合成单调递增的总字节数；`maxBytes` 保证不会因多次回调重复累计，`total` 至少等于已下载字节，避免 UI 出现“已下载大于总量”。UI 还通过 `DownloadProgress.displayBytes` 做最终上限保护。
- 下载页把任务聚合为 `DownloadGroup` 后，在内存中按 DataStore 的排序模式排序；合集成员关系独立存储在 Room，筛选时按 `awemeId → collectionId` 映射过滤作品组。删除合集只清除成员关系。
- `DownloadFeed` 在排序后按日期/首字符/大小区间/文件数区间生成分组标题，同时应用搜索（标题/作者/标签/合集/ID/BGM）和日期筛选；`LibraryFeed` 使用同样思路，最近排序优先使用最近下载时间，未下载记录回退解析时间。
- 列表性能：历史快照和下载任务在 `Dispatchers.Default` 解码；`LazyColumn`/`LazyVerticalGrid` 使用稳定 key 与 contentType；滚动期间新出现卡片的封面延迟到停止滚动后加载；封面固定请求尺寸，视频封面优先远程、失败回退本地帧。
- 滚动进度由独立的 `ScrollProgressOverlay` 读取列表状态，避免滚动帧触发整个页面重组。发布构建启用 R8 + 资源压缩，并使用与历史安装包相同的 debug 签名以便个人覆盖升级。

## 数据

Room（版本 3）：
- `awemes`：作品快照 JSON、标题/作者/封面、收藏、标签（逗号分隔）、解析时间
- `download_history`：任务状态、请求 JSON、文件/zip URI、重试所需信息
- `download_collections`：合集 ID、名称、创建时间、排序序号
- `download_collection_members`：`awemeId` 主键、合集 ID、加入时间；一个作品最多属于一个合集
- 2→3 迁移只新增两张表，不重建或清空已有表；`AppDatabase` 仍保留 destructive fallback 供更旧版本使用

DataStore：
- 默认清晰度（0 = 最高清晰度）、优先 H.264、LivePhoto/BGM/ZIP 默认项、剪贴板识别、下载页网格/列表、下载页排序模式、主题/动态色、历史保留天数、免责声明状态
- 浮球：启用状态、轻享/连播模式、连播自动下载开关

下载请求扩展字段：
- `imageIndexes`：非空时只下载指定的图片列表索引，用于部分图片下载；
- `skipMediaKeys`：跳过已存在的图片/动图/BGM/ZIP；
- `replaceFiles`：覆盖模式下待清理的旧文件引用（URI + legacy path）。

`HistoryRepository` 保存快照时保留已有收藏和标签；历史清理只删除非收藏记录。下载历史在 App 启动时把上次中断的 QUEUED/RUNNING 标记为失败。两个仓库都带有进程内 LRU 解码缓存，避免切换标签页时在主线程重复反序列化 Room 中的 JSON 快照。

## 关键类

| 类 | 职责 |
|---|---|
| `LinkExtractor` | 分享文案 → URL/作品 ID |
| `ShortLinkResolver` | 短链 302 → aweme_id |
| `DouyinWebViewEngine` | WebView 配置、Bridge、脚本注入、页面状态 |
| `ParseController` | 解析阶段、超时、验证码、图文补齐 |
| `DouyinDetailNormalizer` | 原始详情/页面模型 → `Aweme` |
| `DownloadEngine` | 按类型下载、BGM、ZIP、进度聚合 |
| `FileDownloader` | HTTP 下载、续传、候选容错、校验 |
| `MediaStoreSaver` | MediaStore/公共目录保存 |
| `DownloadService` | 前台服务、通知、取消、持久化状态 |
| `HistoryRepository` / `DownloadHistoryRepository` | 本地历史 |
| `SettingsRepository` | DataStore 设置 |
| `DownloadGroups` / `DownloadGroup` | 下载任务按 aweme_id 聚合、作品封面、媒体元数据与组级进度 |
| `DownloadSorting` | 六种排序模式、合集/未分类筛选 |
| `DownloadFeed` / `LibraryFeed` | 搜索、日期筛选、日期/首字符/大小/文件数分组标题 |
| `ScrollProgressOverlay` / `FeedSectionHeader` | 滚动进度胶囊、分组标题、日期日历 |
| `DownloadCollectionRepository` / `DownloadCollectionDao` | 合集创建、重命名、删除、作品移动与成员关系 |
| `DownloadCollections`（UI） | 合集筛选芯片、移动合集面板、管理/命名/删除合集对话框 |
| `ImageSelectionDialog` | 图文结果页缩略图多选、长按预选、部分下载选项 |
| `DownloadDuplicates` | 媒体 key、既有内容索引、重复匹配与跳过剩余判断 |
| `DownloadFileCleaner` | MediaStore/旧路径文件删除、按 URI 清理下载记录 |
| `DownloadCleanupDialog` | 作品/文件级删除选择、全选、是否删除本地文件 |
| `WorkPartsSheet` | 下载卡片长按后的文件选择、文件详情与分析入口 |
| `WorkPreviewDialog` | 作品级全屏预览：抖音式视频/图集/LivePhoto/BGM、作品与文件详情面板 |
| `DouyinVideoPlayer` | 自定义交互的 Media3 播放器（单击暂停、平滑拖动进度、58dp 拖动热区、亮红发光高亮、信息隐藏） |
| `InlineLoopingVideo` | 图片 Pager 内的 LivePhoto 循环播放（TextureView，可继续左右滑动；有 BGM 时静音且不抢音频焦点） |
| `AutoLoopingAudio` | 图文作品预览的无界面 BGM 循环播放器，随内容切换和生命周期暂停/恢复；LivePhoto 播放期间保持 |
| `FloatingAssistantService` | Kit 浮球前台服务、悬浮窗、剪贴板监听、常驻通知 |
| `FloatingParseQueue` / `FloatingWebViewHost` | 隐藏 WebView 顺序解析、自动下载等待与验证页面 |
| `DouyinAccessibilityService` | 抖音/极速版前台识别、分享按钮/分享链接自动点击 |
| `ClipboardFocusReader` | Android 10+ 后台剪贴板的短暂焦点读取补救 |
| `FloatingAssistantScheduler` / `FloatingAssistantJobService` | JobScheduler 周期看护与恢复 |
| `ImageViewerDialog` / `AudioPreviewDialog` | 结果页/详情页图片缩放与音频试听 |

## 生命周期与线程

- `ParseActivity` 通过 `configChanges` 避免旋转重建 WebView；Activity 销毁时释放。
- 解析只在 App 前台进行；下载在 `DownloadService` 中继续，Activity 可离开。
- WebView 回调在主线程；Room/下载在 IO；UI 通过 StateFlow 观察。资料库与下载页的 Room JSON 解码、下载任务重建、分组构建和 feed 排序在 `Dispatchers.Default` 完成，避免首屏阻塞主线程。
- 手动 DI（`AppContainer`）避免 Hilt 构建复杂度；所有依赖从 `Application` 或 `Context.appContainer` 获取。
- `MainScreen` 保留已访问标签页的 Composition（未显示页面跳过测量与绘制），切换时只做约 180 ms 的 alpha/位移过渡，不重建页面；剪贴板监听提升到 `MainScreen` 生命周期，避免切标签重复注册和重复解析。`MainActivity` 的结果页和资料库详情页都以覆盖层呈现，底层页面保持存活，系统返回键由对应层级消费。

## Kit 浮球

浮球是可选的前台辅助进程，入口在设置中；它不改变主解析链路，只在用户开启后增加：

- `FloatingAssistantService`：`specialUse` 前台服务，持有悬浮球、环绕操作窗口、独立信息浮窗、
  输入框、剪贴板监听、`FloatingParseQueue` 和常驻通知；`SYSTEM_ALERT_WINDOW` 使其能在其他应用上层显示。
  操作按钮是独立的小窗口，以便从浮球弹出并跟随拖动；信息浮窗先于浮球加入 WindowManager，
  避免毛玻璃窗口遮住浮球和按钮。
- `DouyinAccessibilityService`：用户手动开启的无障碍服务；只读取抖音/抖音极速版窗口，维护前台状态，
  在连播模式中点击“分享 → 分享链接”。不处理验证码，不导出页面内容。
- `ClipboardFocusReader`：Android 10+ 后台剪贴板限制的补救。分享复制后短暂创建 1×1 可聚焦悬浮窗，
  读取剪贴板后立即移除，把焦点还给抖音。
- 浮球状态点只表示前台应用（抖音/极速版绿色，其他灰色）；模式用图标底部“轻/连”徽标表示；
  浮球外圈 `OrbProgressRing` 显示解析扫描或下载进度。
- `FloatingParseQueue`：复用 `DouyinWebViewEngine` + `ParseController`，WebView 挂在 1×1 隐藏窗口；
  一次只解析一个作品，解析后按默认值自动下载并等待 `DownloadRegistry` 终止，再处理队列下一项。
- 安全验证/登录：验证状态把同一个 WebView 扩展为全屏页面；登录只提示手动打开。
- `FloatingAssistantJobService` + `FloatingAssistantScheduler`：15 分钟 persisted JobScheduler 看护；
  `BootCompletedReceiver` 在开机/更新后重新注册；OEM 可能仍阻止后台启动，设置提供电池优化入口。
- `MainActivity` 启动/恢复时检查 `floatingBallEnabled` 并恢复服务；应用更新后部分系统会关闭无障碍，
  设置卡片会显示未授权并提供入口。
- 设置页按分类页签组织；新增 `ConfigBackup`（JSON 配置，包含 schema/app 版本/全部设置）和
  `HistoryBackup`（ZIP + `manifest.json` + `awemes.jsonl` + `downloads.jsonl`，流式导出/分批导入，
  带进度和错误/跳过统计）。记录包含作品信息和本地文件 URI/路径，不包含媒体文件本身。

浮球的自动下载使用 `buildFloatingDownloadRequest`：视频按默认清晰度/优先 H.264，图文按
LivePhoto/BGM/ZIP 默认开关，不自动下载封面；入队前复用 `DownloadDuplicates`，重复内容自动跳过。
队列只在进程内保存，服务被杀后丢失。详见 [FLOATING_ASSISTANT.md](FLOATING_ASSISTANT.md)。
