# R8 构建与性能提醒

本文给后续开发者和 AI Agent 使用：**凡是需要安装到真机测试或对外发布的 APK，都必须使用 R8 release 构建，不要用 debug APK 评估性能。**

## 为什么必须用 release

debug 构建不混淆、不裁剪，Compose/Room 的额外开销很大。2026-11 在 OPPO PGJM10、289 个下载作品 / 286 条资料库记录上实测：

| 构建 | APK 大小 | 资料库滚动 90 分位 | 下载页滚动 90 分位 |
|---|---:|---:|---:|
| debug | 约 26 MB | 约 69–93 ms | 约 69 ms |
| R8 release | 约 3.39 MB | 约 12 ms | 约 11 ms |

同一轮快速连续滑动中，release 的卡顿帧分别为资料库 2.27%、下载页 0.28%。因此：
- 性能问题必须先在 release 包上复现，不能只看 debug。
- 发布资产必须是 `app/build/outputs/apk/release/app-release.apk`（复制为 `dist/DouyinKit-vX.Y.Z.apk`）。
- debug 包仅用于需要调试 WebView、日志或 R8 规则排查的场景。

## 当前 release 配置

`app/build.gradle.kts`：

- `release.isMinifyEnabled = true`
- `release.isShrinkResources = true`
- `release.signingConfig = signingConfigs.getByName("debug")`

使用标准 Android debug keystore 是为了让个人 sideload 用户能从旧版本直接覆盖升级。**公开分发前应换成私有 release keystore**，并保证同一 applicationId 的签名长期一致；否则用户必须卸载重装。

不要提交 `dist/`、`*.jks`、`keystore.properties`、`local.properties`、cookies 或构建输出。

## 标准构建与发布顺序

```bash
# 1. 修改版本号（app/build.gradle.kts）
# versionCode + 1, versionName = "x.y.z"

# 2. 自动检查
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug

# 3. R8 优化包
./gradlew :app:assembleRelease

# 4. 校验签名、复制资产、计算校验和
"$ANDROID_HOME/build-tools/36.0.0/apksigner.bat" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/DouyinKit-vX.Y.Z.apk
sha256sum dist/DouyinKit-vX.Y.Z.apk

# 5. 覆盖安装并冒烟测试
adb install -r app/build/outputs/apk/release/app-release.apk
```

发布说明、`README.md`、`CHANGELOG.md`、`docs/GITHUB_RELEASE.md` 要同步更新 APK 大小和 SHA-256。

## R8 回归清单

R8 可能裁剪反射、序列化或 WebView 桥。改动以下区域后必须用 release 包真机验证：

- WebView 解析：`hook.js`、`page_model.js`、`@JavascriptInterface`、WebMessage 桥。
- kotlinx.serialization：`DownloadRequest`、`Aweme`、历史快照、`$$serializer`。
- Room：实体、DAO、数据库迁移、旧记录读取。
- Coil/Media3：封面、视频播放、LivePhoto、BGM。
- 文件分享/FileProvider 和 MediaStore 保存。
- Kit 浮球：`FloatingAssistantService`（specialUse 前台服务/通知）、
  `DouyinAccessibilityService`（无障碍组件/分享点击）、悬浮输入框、1×1 隐藏 WebView、
  `ClipboardFocusReader`、`FloatingAssistantJobService` 和 `BootCompletedReceiver`。
  这些组件在 manifest 中声明，但仍需在 release 包上验证 R8 后权限、服务和点击链路。

`app/proguard-rules.pro` 已保留序列化器、WebView JS 接口、Room 基类/实体和 OkHttp 相关类。新增反射型库时要补规则。

## 滚动性能测量

```bash
adb shell dumpsys gfxinfo com.atqiyu.douyinkit reset
# 连续快速滑动 10 次；按实际屏幕尺寸调整坐标
for i in 1 2 3 4 5 6 7 8 9 10; do
  adb shell input swipe 540 1900 540 500 220
  sleep 0.25
done
adb shell dumpsys gfxinfo com.atqiyu.douyinkit | grep -E \
  "Total frames rendered|Janky frames|90th percentile|95th percentile|99th percentile"
```

当前 release 基线：资料库 90 分位约 12 ms、下载页约 11 ms。若 release 也明显超过 30 ms，再检查：
- 是否有新的主线程 JSON 解码、Room 查询或排序；
- 列表 item 是否失去稳定 key/contentType；
- 是否在滚动时同步加载大图/视频帧；
- 是否有调试工具、日志或未关闭的动画在制造额外工作。

## GitHub 发布

完整流程见 [GITHUB_RELEASE.md](GITHUB_RELEASE.md)。Release 资产必须是优化后的 release APK；如果 Git HTTPS 暂时不可用，可先 `git push origin main`，再用 `gh release create --target <完整commit sha>` 创建远端标签和 Release，最后用 `gh api` 校验资产 digest。
