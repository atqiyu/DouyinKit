# DouyinKit

DouyinKit 是一个**纯本地运行**的 Android 工具：粘贴抖音分享链接或文案，解析视频、图文图集、LivePhoto 和 BGM，并保存到系统相册、音乐和下载目录。

- 不依赖电脑、Node 服务或任何云端解析 API
- 不包含分析统计、广告或账号上传
- 解析、历史、收藏、标签和 Cookie 都只保存在本机

> **用途声明**：本项目仅用于个人学习，以及备份你已获得授权的内容。解析依赖抖音公开页面，可能随页面改版或风控失效；请遵守抖音服务条款和内容版权，不要用于批量抓取、商业分发或传播他人作品。

## 功能

- 分享文案/剪贴板识别抖音链接，本地解析 `v.douyin.com` 短链
- 视频：多码率档位、无水印直链、H.264/H.265 标识、默认 1080P H.264
- 图文：全部图片、LivePhoto 动图 MP4、可选 BGM、可选 ZIP 打包
- BGM：优先 `music.play_url` 直链；缺失时用 `MediaExtractor` + `MediaMuxer` 从视频无损提取音轨为 M4A
- 下载：直链/镜像/`playApi` 容错、Range 续传、MP3/MP4/WebP/JPEG/PNG 等 magic 校验、前台服务通知、取消和重试
- 预览：Media3 ExoPlayer 播放视频/动图，图片左右滑动与双指缩放
- 资料库：解析历史、收藏、标签、搜索与筛选；可重新解析过期链接
- 设置：默认清晰度、优先 H.264、LivePhoto/BGM/ZIP 默认项、主题、动态取色、剪贴板识别、历史保留与隐私清理
- Android 7（minSdk 24）到 Android 16（targetSdk 36）；Compose Material 3，支持深色主题

## 技术栈

| 项 | 版本 |
|---|---|
| Kotlin | 2.3.21 |
| AGP / Gradle | 8.13.2 / 8.13 |
| Compose BOM | 2026.06.01（UI 1.11.4，Material3 1.4.0） |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 |
| WebView | androidx.webkit 1.18.0 |
| 下载 | OkHttp 5.4.0 + OkHttp MockWebServer（测试） |
| 图片 | Coil 3.4.0 |
| 播放 | Media3 1.11.1 |
| 本地数据 | Room 2.8.5（KAPT）、DataStore 1.3.0 |
| 序列化 | kotlinx.serialization 1.11.0 |

依赖版本是围绕 **compileSdk 36 + AGP 8.13.2 + Room 2.8.5** 选定的组合：新版 Compose/Navigation/Coil/OkHttp 会要求 compileSdk 37 + AGP 9.1；Kotlin 2.4 与 Room 的 `kotlin-metadata-jvm 2.3` 不兼容；`kotlinx-serialization 1.12` 会强制升级 stdlib 到 2.4。详见 [docs/DECISIONS.md](docs/DECISIONS.md)。

## 构建

环境：

- JDK 17
- Android SDK：`platforms;android-36`、`build-tools;36.0.0`（AGP 也会自动安装 35.0.0）
- 可访问 Google Maven / Maven Central（仓库已配置阿里云镜像，官方仓库作为回退）

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows 下可直接使用 `gradlew.bat`。Debug APK 未做压缩混淆；release 构建需要自行配置签名，仓库不保存密钥。

## 使用

1. 在抖音中分享作品 → 复制链接，或直接复制分享文案；也可以从浏览器/其他 App 选择 DouyinKit 打开抖音作品链接。
2. 打开 DouyinKit，粘贴到首页输入框；剪贴板检测开启时，回到 App 会自动出现“检测到抖音链接”横幅。
3. 点击解析。首次可能需要 10–20 秒；如果页面要求安全验证，解析页会自动显示 WebView，完成验证后继续。
4. 在结果页预览、选择清晰度、下载视频/图集/BGM/封面，或收藏、加标签、重新解析。
5. 下载进度在底部“下载”标签页；完成后可在系统相册/音乐/文件管理器中查看。

保存位置：

- 视频：`Movies/DouyinKit/`
- 图片与 LivePhoto：`Pictures/DouyinKit/<标题>_<作品ID>/`
- BGM：`Music/DouyinKit/`
- ZIP：`Downloads/DouyinKit/`

## 解析原理（简述）

```
分享文本
  → LinkExtractor 提取抖音 URL
  → OkHttp 不跟随重定向读取短链 302 Location
  → 隐藏 WebView（桌面 UA）打开 www.douyin.com/video/<id>
  → document-start 注入 hook，拦截 /aweme/v1/web/aweme/detail/ 响应
  → JS 侧裁剪 JSON，通过 WebMessage/JavascriptInterface 回传
  → Kotlin 归一化：视频档位/封面/作者/统计/图集/LivePhoto/BGM
  → 图文再打开 /note/<id>，从 React 页面模型补齐 LivePhoto 与播放信息
  → 结果页；下载直连 CDN（Referer + 桌面 UA）
```

详细实现与调试方式见 [docs/PARSING.md](docs/PARSING.md)，整体结构见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 隐私与安全

- 无第三方分析 SDK；除抖音页面与 CDN 外不发起网络请求
- Cookie 仅保存在 WebView 本地，可在设置中一键清除
- 下载 URL 限制为抖音/字节 CDN 域名，拒绝内网、IP 字面量和未知重定向目标
- 解析历史存放在 Room；下载历史存放在 Room；均可清除
- `allowBackup=false`，并配置了排除全部内容的备份规则
- 不在日志中输出 Cookie 或完整签名 URL（诊断仅显示 host/path）

## 当前验证状态

- `./gradlew assembleDebug`：通过，debug APK 约 25 MB
- `./gradlew testDebugUnitTest`：33 个单元测试全部通过（链接提取、归一化、下载续传/过期/HTML/格式校验、URL 白名单、ZIP、档位选择、MIME）
- `./gradlew lintDebug`：0 error，21 warning（主要是依赖可升级提示和 KAPT 建议）
- **真机验证已通过**（2026-10-24，OPPO PGJM10 / Android 14 / API 34 / WebView 117）：
  - 视频短链解析成功，详情接口返回 11 个码率档，1080P H.264 下载为 3,594,326 字节并写入 `Movies/DouyinKit/`
  - 图文短链解析成功：2 张图片 + 1 个 LivePhoto；下载得到 `01.webp` 281,090 字节、`02.webp` 38,286 字节、`02_live.mp4` 217,978 字节、BGM MP3 532,552 字节、ZIP 1,026,992 字节（含 `info.json`）
  - 视频 BGM 直链下载为 99,972 字节 MP3
  - 视频/LivePhoto Media3 预览、图片滑动缩放、收藏/标签、下载历史持久化、深色主题、深链接收均已实测
- Android 7 真机尚未运行；仅做 minSdk 静态检查和 Lint。H.265 相册兼容性取决于设备解码器。

如果解析失败，优先检查：

1. 解析页“显示页面”后页面是否出现验证码/登录提示；
2. 解析诊断里的 `hook`、`bodyLength`、`detailIds`；
3. WebView 版本是否过旧（建议 Chrome/WebView 100+）。

## 目录

```
app/src/main/java/com/atqiyu/douyinkit/
  core/       模型、链接提取、格式化、HTTP 客户端
  parser/     WebView 引擎、JS Bridge、短链解析、归一化、ParseActivity
  download/   下载器、MediaStore、前台服务、BGM 提取、ZIP、下载历史联动
  data/       Room、DataStore、历史仓库
  di/         手动依赖注入容器
  ui/         Compose 页面（首页/解析/结果/下载/资料库/设置/预览）
app/src/main/assets/parser/  hook.js、page_model.js
app/src/test/                单元测试
docs/                       架构、解析、测试、决策记录
```

## 已知限制

- 只支持公开作品；私密、登录可见、直播、主页、合集不支持
- 解析成功率取决于抖音 WebView 页面与风控；不是官方 API，页面改版可能需要更新 `hook.js` / `page_model.js`
- 播放地址约 1 小时过期；后台下载不会启动 WebView 自动重新解析，过期后需回到前台重新解析
- H.265 文件可以下载，但部分设备相册/播放器无法解码；默认选 H.264
- LivePhoto 保存为“静态图 + 动图 MP4”，不合成系统动态照片
- 未在 Android 7 真机上运行验证，仅通过 minSdk 代码审查和 Lint；建议在目标设备上回归
- 未上架 Google Play，个人使用建议 sideload APK
