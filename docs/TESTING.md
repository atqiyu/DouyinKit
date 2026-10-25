# 测试与真机验收

## 自动检查

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

当前结果：

- debug APK 构建通过
- 37 个 JVM 单元测试通过
- lint 0 error / 23 warning+hint

单元测试覆盖：

| 测试 | 覆盖 |
|---|---|
| `LinkExtractorTest` | 分享文案、短链大小写、长短链、note/slides、中文标点、批量去重、裸 ID、非法域名 |
| `DouyinDetailNormalizerTest` | 视频档位、H264/H265、camelCase、图集、LivePhoto、BGM、空码率兜底、ID 不匹配、页面模型合并 |
| `FileDownloaderTest` | 206 Range 续传、403 过期、HTML 拒绝、magic 校验 |
| `UrlPolicyTest` | CDN 白名单、内网/IP/未知域名拒绝、http→https |
| `MimeSnifferTest` | WebP/JPEG/PNG/GIF/MP3/M4A/MP4/ZIP |
| `ZipExporterTest` | ZIP 条目、info.json、路径穿越清理 |
| `VideoGearSelectorTest` | 竖屏短边分辨率、1080/540 选择、normal_1080_0 优先 |
| `DownloadGroupsTest` | 按作品聚合、组内最新排序、进行中/部分完成状态、任务类型标签 |

## 真机前准备

1. 手机开启开发者选项 → USB 调试，连接电脑，解锁并允许调试。
2. 确认：

```bash
adb devices -l
```

3. 安装：

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

4. Android 13+ 可提前授予通知权限（也可在 App 内首次下载时授权）：

```bash
adb shell pm grant com.atqiyu.douyinkit android.permission.POST_NOTIFICATIONS
```

## 真机 E2E 清单

### A. 启动与输入

- [ ] 首次启动显示使用前须知，同意后进入首页
- [ ] 首页粘贴分享文案能正确填入
- [ ] 从抖音分享面板发送到 DouyinKit，首页自动填入文案
- [ ] 在浏览器/文件中点抖音作品链接，选择 DouyinKit，首页自动填入链接
- [ ] 复制抖音链接后回到 App，出现“检测到抖音链接”横幅；点解析直接开始
- [ ] 设置中关闭剪贴板识别后，回到 App 不再出现横幅

### B. 视频解析

测试链接：

```text
https://v.douyin.com/i_mOpOTb9qM
https://v.douyin.com/lOGKzR5b54o
https://www.douyin.com/video/7697896601788248226
```

- [ ] 首次解析 10–20 秒内完成；如出现验证，显示 WebView 并可完成
- [ ] 结果页显示封面、标题、作者、点赞/评论/收藏
- [ ] 清晰度列表包含 4K/1080P/720P/540P；**1080×1920 显示为 1080P，576×1024 显示为 540P**
- [ ] 清晰度可多选，多个档位分别进入下载队列，结果页显示各自进度
- [ ] H.265 档位有标识，默认选中 1080P H.264
- [ ] BGM 卡片显示标题/作者/时长；有直链时提示“有音频直链”
- [ ] 点击封面可播放视频（或提示链接过期/解码失败）

日志与页面：

```bash
adb logcat -s DouyinWebView
```

解析页“解析诊断”中：

- `hook=true`
- `page` 是 `www.douyin.com/video/...`
- `detailIds` 包含目标 aweme_id
- `bridge=webmessage` 或 `jsinterface`

### C. 视频下载

- [ ] 选择 1080P H.264 开始下载；通知出现进度
- [ ] 下载页显示速度/进度；可取消，再重试
- [ ] 同一作品的多个清晰度、图片、LivePhoto、BGM 在下载页合并为一个作品卡片
- [ ] 网格/列表切换生效，重启 App 后保持上次选择
- [ ] 作品卡片显示本地视频帧或图片封面
- [ ] 点作品卡片可预览视频/图片/LivePhoto/BGM；预览页显示作者、描述和文件元数据
- [ ] 文件出现在 `Movies/DouyinKit/`：

```bash
adb shell ls -l /sdcard/Movies/DouyinKit/
```

- [ ] 系统相册可播放；文件大小与结果页一致；无抖音水印
- [ ] 下载 BGM 出现在 `Music/DouyinKit/`，可播放
- [ ] 保存封面出现在 `Pictures/DouyinKit/`

### D. 图文 + LivePhoto + BGM + ZIP

测试链接：

```text
https://v.douyin.com/4nQR7cylDqw
https://www.douyin.com/note/7700144500140762266
```

- [ ] 结果页显示“图文”，图片数量正确
- [ ] LivePhoto 图片有“动图”角标，预览里可播放动图
- [ ] 下载选项默认包含 LivePhoto、BGM，可勾选 ZIP
- [ ] 图片保存到 `Pictures/DouyinKit/<标题>_<id>/01.webp` 等
- [ ] 动图保存为 `02_live.mp4`
- [ ] ZIP 出现在 `Downloads/DouyinKit/`，包含 `info.json`：

```bash
adb pull /sdcard/Download/DouyinKit/<文件名>.zip ./tmp/
unzip -l ./tmp/<文件名>.zip
```

- [ ] BGM 直链可用时下载原始音频；无直链时日志/结果提示“从视频提取”，输出 `.m4a` 可播放

### E. 预览

- [ ] 视频预览可播放/暂停/拖动，返回后释放
- [ ] 图片预览左右滑动，双指缩放，双击放大/还原
- [ ] LivePhoto 预览按钮可播放动图
- [ ] H.265 设备不支持时显示可读错误，不崩溃

### F. 历史、收藏、标签

- [ ] 解析后“资料库”出现记录
- [ ] 收藏、取消收藏在结果页和资料库同步
- [ ] 资料库“选择”进入多选，可全选/批量删除（取消确认不删除）
- [ ] 添加多个标签（逗号/中文逗号/顿号），资料库可按标签筛选
- [ ] 搜索标题/作者/标签
- [ ] 删除记录只删除本机记录，不影响已下载文件
- [ ] 杀进程重启后历史、收藏、标签仍在
- [ ] 下载页重启后仍能看到下载历史；上次中断的任务显示失败/可重试
- [ ] 从资料库点作品进入结果页，系统返回键/侧滑返回资料库而不是退出 App
- [ ] 首页/资料库/下载/设置相互切换后，输入内容、筛选和滚动位置仍保留
- [ ] 下载页作品预览中，系统返回先关闭“作品信息与下载详情”，再关闭预览

### G. 设置

- [ ] 默认清晰度改为 720P 后，清晰度弹窗默认选中 720P
- [ ] 关闭“优先 H.264”后，可用档位不再过滤 H.264
- [ ] 主题：跟随系统/浅色/深色即时切换
- [ ] Android 12+ 动态取色生效
- [ ] 图集默认项（LivePhoto/BGM/ZIP）影响下载弹窗初始值
- [ ] 历史保留天数生效（清理非收藏）
- [ ] 清除解析历史、Cookie/浏览器数据、临时文件均成功
- [ ] 关闭剪贴板识别生效
- [ ] App 启动/从后台回到首页时自动读取剪贴板并显示横幅；前台复制新链接也能触发

### H. 异常与兼容

- [ ] 断网解析提示网络错误，恢复后可重试
- [ ] 无效链接/主页/直播链接提示“不是作品链接”
- [ ] 页面触发验证码时可手动完成并继续；取消后可退出
- [ ] 旋转解析页不重建、不丢进度
- [ ] 解析中切后台/回前台行为合理
- [ ] 存储空间不足时提示保存失败
- [ ] 拒绝通知权限时下载仍可完成（只是无通知）
- [ ] Android 7/8 设备：安装、启动、下载到公共目录；重点检查 `WRITE_EXTERNAL_STORAGE` 授权
- [ ] Android 14/15/16：前台服务类型、通知、权限无崩溃

### I. 链接过期 / 刷新

1. 解析一个视频后等待约 1 小时（或让 CDN 返回 403）；
2. 点下载，任务应变为“需要刷新”，提示回前台重新解析；
3. 结果页“重新解析”后再下载成功。

## 失败时请提供

- 手机型号、Android 版本、WebView 版本
- 测试链接（可脱敏，但保留短码大小写）
- 解析诊断截图/文本
- `adb logcat -s DouyinWebView` 输出
- 失败任务的错误提示

## 真机验收记录（2026-10-24）

设备：OPPO PGJM10，Android 14 / API 34，WebView 117.0.5938.60。

已实测通过：

- `https://v.douyin.com/i_mOpOTb9qM/` 视频解析：11 个码率档；1080P H.264 下载 3,594,326 字节，MediaStore 索引到 `Movies/DouyinKit/`
- 视频 BGM：`Music/DouyinKit/` MP3 99,972 字节
- `https://v.douyin.com/4nQR7cylDqw/` 图文解析：2 张图片 + 1 个 LivePhoto；01.webp 281,090 字节、02.webp 38,286 字节、02_live.mp4 217,978 字节、BGM MP3 532,552 字节、ZIP 1,026,992 字节
- ZIP 条目：`info.json`、两张 WebP、LivePhoto MP4、BGM MP3
- 视频播放、LivePhoto 播放、图片左右滑动/缩放
- 收藏、标签、资料库筛选、下载历史在强制停止并重启后仍保留
- 深色主题、动态状态栏图标、深链接收、分享文本预填
- 回车/换行与键盘弹出时顶部不再被状态栏遮挡

2026-10-25 回归新增实测：

- 下载页 5 个作品 / 21 个文件 / 42.6 MB 正确聚合；网格与列表切换、封面、作品信息、视频 1080×1920/30fps/H.264/20s、图片 2160×2872/WebP、LivePhoto 960×720/2s、BGM 33s/MP3 均显示正确
- 抖音风格播放器单击暂停/继续、暂停提示、底部进度条、右侧操作栏正常，不再出现默认五键控制层
- 作品预览内系统返回先关闭详情面板，再关闭预览
- 资料库作品结果页系统返回键成功回到资料库；标签切换保留页面状态
- 冷启动自动识别剪贴板；关闭横幅后切换标签不重复弹出；前台复制新链接立即弹出

本次未覆盖：Android 7/8 真机、H.265 在系统相册的播放兼容性、验证码人工流程、链接真正过期后的刷新流程、完全后台状态下剪贴板读取（受 Android 10+ 系统限制）。

## 已知未验证项

- Android 7 只做了 minSdk 静态检查/Lint，没有 API 24 设备运行记录。
- H.265 相册兼容性取决于设备解码器。
- 验证码出现时的 WebView 交互、链接过期后的 `NEEDS_REFRESH` 流程需要按上面的清单继续回归。
