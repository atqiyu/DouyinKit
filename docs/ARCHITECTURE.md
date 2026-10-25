# 架构

## 目标

DouyinKit 是单模块原生 Android App，核心约束是**无服务器、无账号、不上传数据**。所有解析都在手机的 WebView 内完成，所有下载都直连抖音 CDN，所有状态保存在本机。

```
Compose UI
  ├─ Home / Clipboard
  ├─ ParseActivity (WebView)
  ├─ Result / Preview / Downloads (grouped by work) / Library / Settings
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
6. `DouyinDetailNormalizer` 宽容解析 snake_case 与 camelCase，生成 `Aweme`：视频档位、封面、作者、统计、图集、LivePhoto、BGM。
7. 图文作品额外打开 `/note/<id>`，`page_model.js` 遍历 React fiber / SSR 数据，补齐 LivePhoto、fps、codec、playApi；与详情结果按档位名/图片 uri 合并。
8. 结果保存到 Room，交给 `ParseSessionStore`，`MainActivity` 展示结果页。

超时：总等待 45 秒；检测到验证码后延长到 120 秒并显示 WebView；页面模型兜底 22 秒；图文额外 10 秒。

## 下载链路

```
DownloadRequest (Aweme + kind + gear + options)
  → 重复检查（记录 + 内存任务，按清晰度/图片/动图/BGM/封面/ZIP 的稳定 key）
  → DownloadService（前台服务，2 并发）
      → DownloadEngine
          ├─ 视频：gear.urls → playApi → 必要时 defaultUrls
          ├─ 图片：urls → 可选 downloadUrls（水印兜底）；支持 skipMediaKeys 跳过已存在项
          ├─ LivePhoto：urls → playApi → 可选 downloadUrls
          ├─ BGM：music.play_url 直链 → 失败时下载视频并 remux 音轨
          └─ ZIP：媒体 + info.json
      → 覆盖策略：新内容完整保存后按 replaceFiles 删除旧文件/旧记录
      → FileDownloader（Range 续传、镜像容错、magic/大小校验）
      → MediaStoreSaver（API 29+ IS_PENDING；API 24–28 公共目录 + MediaScanner）
  → DownloadRegistry（内存进度）
  → Room download_history（状态、文件 URI、重试请求）
```

- `DownloadService` 使用 `dataSync` 前台类型，通知显示总进度、取消全部；Android 13+ 通知权限在 UI 中请求。
- `DownloadEngine` 先把文件下载到 app-specific external temp，成功后再发布到 MediaStore；任务结束清理 temp。
- 视频候选失败全部为 403/401/410 时抛出 `EXPIRED`，服务标记 `NEEDS_REFRESH`，提示用户回前台重新解析；后台不会启动 WebView。
- 下载 URL 经过 `UrlPolicy` 白名单，拒绝内网/IP/未知域名；OkHttp 最终重定向地址也会校验。
- 重复检查在 UI 触发下载前执行：已有文件按媒体 key 汇总，提示用户跳过、覆盖或仍然重复下载。跳过会给图片请求写入 `skipMediaKeys`；覆盖把旧文件引用写入 `replaceFiles`，后台在新任务完整成功后清理，失败则保留旧文件。
- 下载管理支持按作品/文件级删除。删除本地文件使用 MediaStore（旧系统回退到文件路径 + MediaScanner），只有文件删除成功才移除对应记录；也可只删记录保留文件。

## 数据

Room：
- `awemes`：作品快照 JSON、标题/作者/封面、收藏、标签（逗号分隔）、解析时间
- `download_history`：任务状态、请求 JSON、文件/zip URI、重试所需信息

DataStore：
- 默认清晰度、优先 H.264、LivePhoto/BGM/ZIP 默认项、剪贴板识别、下载页网格/列表、主题/动态色、历史保留天数、免责声明状态

下载请求扩展字段：
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
| `DownloadDuplicates` | 媒体 key、既有内容索引、重复匹配与跳过剩余判断 |
| `DownloadFileCleaner` | MediaStore/旧路径文件删除、按 URI 清理下载记录 |
| `DownloadCleanupDialog` | 作品/文件级删除选择、全选、是否删除本地文件 |
| `WorkPartsSheet` | 下载卡片长按后的文件选择、文件详情与分析入口 |
| `WorkPreviewDialog` | 作品级全屏预览：抖音式视频/图集/LivePhoto/BGM、作品与文件详情面板 |
| `DouyinVideoPlayer` | 自定义交互的 Media3 播放器（单击暂停、平滑拖动进度、检查器隐藏信息） |
| `InlineLoopingVideo` | 图片 Pager 内的 LivePhoto 循环播放（TextureView，可继续左右滑动） |
| `ImageViewerDialog` / `AudioPreviewDialog` | 结果页/详情页图片缩放与音频试听 |

## 生命周期与线程

- `ParseActivity` 通过 `configChanges` 避免旋转重建 WebView；Activity 销毁时释放。
- 解析只在 App 前台进行；下载在 `DownloadService` 中继续，Activity 可离开。
- WebView 回调在主线程；Room/下载在 IO；UI 通过 StateFlow 观察。
- 手动 DI（`AppContainer`）避免 Hilt 构建复杂度；所有依赖从 `Application` 或 `Context.appContainer` 获取。
- `MainScreen` 保留已访问标签页的 Composition（未显示页面跳过测量与绘制），切换时只做约 180 ms 的 alpha/位移过渡，不重建页面；剪贴板监听提升到 `MainScreen` 生命周期，避免切标签重复注册和重复解析。`MainActivity` 的结果页和资料库详情页都以覆盖层呈现，底层页面保持存活，系统返回键由对应层级消费。
