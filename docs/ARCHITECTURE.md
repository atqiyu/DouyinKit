# 架构

## 目标

DouyinKit 是单模块原生 Android App，核心约束是**无服务器、无账号、不上传数据**。所有解析都在手机的 WebView 内完成，所有下载都直连抖音 CDN，所有状态保存在本机。

```
Compose UI
  ├─ Home / Clipboard
  ├─ ParseActivity (WebView)
  ├─ Result / Preview / Downloads / Library / Settings
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
  → DownloadService（前台服务，2 并发）
      → DownloadEngine
          ├─ 视频：gear.urls → playApi → 必要时 defaultUrls
          ├─ 图片：urls → 可选 downloadUrls（水印兜底）
          ├─ LivePhoto：urls → playApi → 可选 downloadUrls
          ├─ BGM：music.play_url 直链 → 失败时下载视频并 remux 音轨
          └─ ZIP：媒体 + info.json
      → FileDownloader（Range 续传、镜像容错、magic/大小校验）
      → MediaStoreSaver（API 29+ IS_PENDING；API 24–28 公共目录 + MediaScanner）
  → DownloadRegistry（内存进度）
  → Room download_history（状态、文件 URI、重试请求）
```

- `DownloadService` 使用 `dataSync` 前台类型，通知显示总进度、取消全部；Android 13+ 通知权限在 UI 中请求。
- `DownloadEngine` 先把文件下载到 app-specific external temp，成功后再发布到 MediaStore；任务结束清理 temp。
- 视频候选失败全部为 403/401/410 时抛出 `EXPIRED`，服务标记 `NEEDS_REFRESH`，提示用户回前台重新解析；后台不会启动 WebView。
- 下载 URL 经过 `UrlPolicy` 白名单，拒绝内网/IP/未知域名；OkHttp 最终重定向地址也会校验。

## 数据

Room：
- `awemes`：作品快照 JSON、标题/作者/封面、收藏、标签（逗号分隔）、解析时间
- `download_history`：任务状态、请求 JSON、文件/zip URI、重试所需信息

DataStore：
- 默认清晰度、优先 H.264、LivePhoto/BGM/ZIP 默认项、剪贴板识别、主题/动态色、历史保留天数、免责声明状态

`HistoryRepository` 保存快照时保留已有收藏和标签；历史清理只删除非收藏记录。下载历史在 App 启动时把上次中断的 QUEUED/RUNNING 标记为失败。

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

## 生命周期与线程

- `ParseActivity` 通过 `configChanges` 避免旋转重建 WebView；Activity 销毁时释放。
- 解析只在 App 前台进行；下载在 `DownloadService` 中继续，Activity 可离开。
- WebView 回调在主线程；Room/下载在 IO；UI 通过 StateFlow 观察。
- 手动 DI（`AppContainer`）避免 Hilt 构建复杂度；所有依赖从 `Application` 或 `Context.appContainer` 获取。
