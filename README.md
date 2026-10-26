# DouyinKit

DouyinKit 是一个**纯本地运行**的 Android 工具：粘贴抖音分享链接或文案，解析视频、图文图集、LivePhoto 和 BGM，并保存到系统相册、音乐和下载目录。

开发者：[atqiyu](https://github.com/atqiyu) · GitHub 仓库：[atqiyu/DouyinKit](https://github.com/atqiyu/DouyinKit) · 开源许可证：[MIT License](LICENSE) · [最新 Release](https://github.com/atqiyu/DouyinKit/releases/latest)

- 不依赖电脑、Node 服务或任何云端解析 API
- 不包含分析统计、广告或账号上传
- 解析、历史、收藏、标签和 Cookie 都只保存在本机

> **用途声明**：本项目仅用于个人学习，以及备份你已获得授权的内容。解析依赖抖音公开页面，可能随页面改版或风控失效；请遵守抖音服务条款和内容版权，不要用于批量抓取、商业分发或传播他人作品。

## 功能

- 分享文案/剪贴板识别抖音链接，本地解析 `v.douyin.com` 短链
- 视频：多码率档位、无水印直链、H.264/H.265 标识；默认清晰度支持“最高清晰度”、4K、1080P 等，选择“最高”时自动取作品中最高分辨率，同分辨率优先 H.264
- 图文：全部图片、LivePhoto 动图 MP4、可选 BGM、可选 ZIP 打包
- BGM：优先 `music.play_url` 直链；缺失时用 `MediaExtractor` + `MediaMuxer` 从视频无损提取音轨为 M4A
- 下载：直链/镜像/`playApi` 容错、Range 续传、MP3/MP4/WebP/JPEG/PNG 等 magic 校验、前台服务通知、取消和重试
- 清晰度支持多选，一次下载多个档位；结果页实时显示下载进度、速度和完成状态
- 预览：Media3 ExoPlayer 播放视频/动图/本地 BGM，图片左右滑动与双指缩放；下载页按作品聚合，可切换网格/列表，并从作品预览查看作者、描述、统计、作品 ID、发布时间和每个文件的尺寸/格式/帧率/编码/时长
- 抖音风格播放器：无默认五键控制条，单击暂停/继续、底部平滑进度线可拖动；详情/分享/打开集中在右上角。左下角作品信息可一键渐隐/渐显，隐藏时顶部标题和操作按钮以动画变淡，图片预览带沉底渐变和背景铺底
- 视频进度条提供 58dp 纵向拖动热区；拖动时进度线与游标变为更亮的红色并加粗、带发光效果，松手后平滑恢复
- 图文作品预览时，若作品带有 BGM，会自动循环播放；播放 LivePhoto 动图时 BGM 继续循环（动图原声静音，避免重叠）；切换到视频/音频预览时暂停，返回图片后继续
- LivePhoto 在图片预览中直接点击即可循环播放，播放中再次点击不暂停，仍可左右滑动切换图片和正常返回
- 下载卡片长按打开内容选择：查看文件级详情、在作品预览中分析、移动到合集，或用系统应用打开
- 下载记录管理：右上角入口可按作品全选/部分选择（长按作品选择单张图片、视频清晰度、BGM、ZIP 等），确认时可选择是否同时删除本地文件
- 下载页排序：最近/最早下载、文件最大、文件最多、按作品标题、按作者昵称，选择保存在 DataStore
- 下载页合集：新建、重命名、删除合集；长按作品“移动到合集”或移回未分类；顶部合集芯片筛选，作品卡片显示所属合集；删除合集只解除分类，不删除作品或本地文件
- 重复下载识别：按作品的具体视频清晰度、图片、动图、BGM、封面和 ZIP 判断，可选择跳过重复、覆盖原有（新文件成功保存后删除旧文件并更新下载时间）或重复下载
- 资料库：解析历史、收藏、标签、搜索与筛选、多选删除；可重新解析过期链接；从资料库打开的结果页支持系统返回键/侧滑手势回到资料库
- 首页最近解析可直接打开或跳转资料库；剪贴板监听常驻主页层，启动、回到前台和前台复制新链接时立即识别，切换标签不会重复触发或丢失已有横幅
- 首页/资料库/下载/设置保留各自状态和滚动位置，切换使用短时淡入/位移过渡；结果页和作品预览使用独立滑入动画
- 下载页网格/列表与排序方式保存在 DataStore，重启后保持
- 设置：默认清晰度（最高/4K/1080P/720P/540P）、优先 H.264、LivePhoto/BGM/ZIP 默认项、主题、动态取色、剪贴板识别、历史保留与隐私清理
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
5. 打开底部“下载”标签页查看进度和已完成文件。同一作品的文件会自动归为一组，可切换网格/列表、按下载时间/大小/文件数/标题/作者排序；长按作品可移动到自建合集，顶部芯片按合集筛选。点卡片打开作品预览，右侧“详情”查看作品信息和每个文件的尺寸、编码、帧率、时长等。

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

## 开源与许可

- 源代码以 [MIT License](LICENSE) 开源，版权所有 © 2026 atqiyu。
- 代码许可证只覆盖本项目源代码，不授予抖音/字节跳动的作品、商标、页面数据或任何第三方内容的权利。
- 本项目仅用于个人学习与已授权内容的本地备份，不隶属于抖音/字节跳动，也不代表平台认可。
- 更多发布说明见 [CHANGELOG.md](CHANGELOG.md) 与 [RELEASE_NOTES_v1.3.0.md](RELEASE_NOTES_v1.3.0.md)。

## 隐私与安全

- 无第三方分析 SDK；除抖音页面与 CDN 外不发起网络请求
- Cookie 仅保存在 WebView 本地，可在设置中一键清除
- 下载 URL 限制为抖音/字节 CDN 域名，拒绝内网、IP 字面量和未知重定向目标
- 解析历史存放在 Room；下载历史存放在 Room；均可清除
- `allowBackup=false`，并配置了排除全部内容的备份规则
- 不在日志中输出 Cookie 或完整签名 URL（诊断仅显示 host/path）

## 当前验证状态

- `./gradlew assembleDebug`：通过，debug APK 约 26 MB
- `./gradlew testDebugUnitTest`：49 个单元测试全部通过（链接提取、归一化、下载续传/过期/HTML/格式校验、URL 白名单、ZIP、档位选择/最高清晰度、MIME、下载分组、排序与合集筛选、下载进度聚合、重复下载策略）
- `./gradlew lintDebug`：0 error，22 warning/hint（主要是依赖可升级提示、KAPT 建议和布局性能提示）
- **真机验证已通过**（2026-10-24 至 2026-10-26，OPPO PGJM10 / Android 14 / API 34 / WebView 117）：
  - 视频短链解析成功，详情接口返回 11 个码率档，1080P H.264 下载为 3,594,326 字节并写入 `Movies/DouyinKit/`
  - 图文短链解析成功：2 张图片 + 1 个 LivePhoto；下载得到 `01.webp` 281,090 字节、`02.webp` 38,286 字节、`02_live.mp4` 217,978 字节、BGM MP3 532,552 字节、ZIP 1,026,992 字节（含 `info.json`）
  - 视频 BGM 直链下载为 99,972 字节 MP3
  - 视频/LivePhoto Media3 预览、图片滑动缩放、收藏/标签、下载历史持久化、深色主题、深链接收均已实测
  - 剪贴板在 App 启动/回到前台时自动识别并显示横幅；实测能识别真实抖音链接
  - 清晰度多选实测同时入队 1080P H.264 + 1080P H.265，结果页显示实时进度/速度，完成后显示"已保存到相册"
  - 下载页本地预览已实测：视频、图片、LivePhoto 动图、BGM 音频
  - 2026-10-25 回归：下载页按作品聚合（5 个作品 / 21 个文件 / 42.6 MB）、网格与列表切换、作品封面、作品信息与文件元数据、BGM 内嵌播放均已实测
  - 2026-10-25 回归：抖音风格视频预览单击暂停/继续、拖动进度条、右上角操作区均正常；系统返回键在作品详情页内先关闭详情，再关闭预览
  - 2026-10-25 回归：从资料库打开作品结果页后，系统返回键和侧滑手势可返回资料库，不再退出 App；标签页切换保留滚动位置
  - 2026-10-25 回归：冷启动自动弹出剪贴板横幅；关闭横幅后切换到其他标签再回首页不会重复弹出；前台再次复制新链接会立即弹出
  - 2026-10-25 性能回归：保留已访问标签页后，单次切换实测 90 分位约 10–31 ms、99 分位约 12–69 ms（设备为 OPPO PGJM10，具体取决于首次进入和图片解码）
  - 2026-10-25 回归：作品预览已去掉底部文件详情卡，详情/分享/打开移到右上角；信息隐藏/显示渐隐动画、LivePhoto 点击内联循环播放、左右滑动切图均实测
  - 2026-10-25 回归：长按下载卡片可打开内容选择，查看详情与“分析”定位到所选图片页；按钮文字与交互正常
  - 2026-10-25 回归：下载管理支持全选和长按部分选择；实测只选测试封面并勾选“同时删除本地文件”后，记录与 MediaStore 文件都被删除
  - 2026-10-25 回归：图片重复下载弹窗可识别 4 类重复内容；选择“跳过重复”后没有新增下载记录；测试封面选择“覆盖原有”后旧文件被删除、新记录时间更新
  - 2026-10-25 回归：拖动视频进度条时整条进度线、当前时间和发光游标高亮；隐藏作品信息时顶部标题与四个操作按钮渐隐到 35%
  - 2026-10-25 回归：图文作品预览自动循环 BGM，音频焦点使用 `CONTENT_TYPE_MUSIC`；设置“关于”显示版本、开发者、GitHub 仓库和 MIT 许可证链接
  - 2026-10-26 回归：Room 2→3 迁移成功，保留 198 条旧下载记录；新增合集表与成员表未破坏旧数据
  - 2026-10-26 回归：下载页实测六种排序、新建“动图测试”合集、长按作品移动、合集芯片筛选、作品卡片合集角标、重命名入口和删除合集；删除合集后作品与本地文件保留、筛选自动回到全部
  - 2026-10-26 回归：默认清晰度选“最高清晰度”后，结果页清晰度弹窗自动选中 4K（该作品无 4K H.264，因此选中 4K H.265）
  - 2026-10-26 回归：拖动进度条时在 58dp 热区上方也能触发，进度线与发光游标呈亮红色且明显加粗
  - 2026-10-26 回归：11 张 LivePhoto + BGM 的图文作品，点击播放动图后 `dumpsys audio` 仍只有 BGM 的 `CONTENT_TYPE_MUSIC` 焦点、gain 为 GAIN，动图不抢占音频焦点，BGM 持续循环
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
- H.265 文件可以下载，但部分设备相册/播放器无法解码；默认 1080P 优先 H.264，选择“最高清晰度”时优先同分辨率 H.264，若作品没有 4K H.264 则会选 4K H.265
- 重复下载检测基于本机下载记录；若用户在系统相册中手动删除了文件但记录仍在，App 仍会把它视为已下载。可先在下载管理里清理记录
- “覆盖原有”只在新下载完整成功且没有文件失败时才删除旧文件；部分失败会保留旧文件，避免内容丢失
- LivePhoto 保存为“静态图 + 动图 MP4”，不合成系统动态照片
- Android 10+ 限制后台读取剪贴板；App 会在启动、回到前台以及 App 处于前台时检测剪贴板变化，完全进入后台后由系统阻止读取，不会也无法在后台轮询。回到前台时会补读最新内容。
- 未在 Android 7 真机上运行验证，仅通过 minSdk 代码审查和 Lint；建议在目标设备上回归
- 未上架 Google Play，个人使用建议 sideload APK
