# GitHub 仓库与 Release 流程

已发布版本：

- 仓库：<https://github.com/atqiyu/DouyinKit>
- 最新 Release：<https://github.com/atqiyu/DouyinKit/releases/tag/v1.3.0>
- 最新 APK：`DouyinKit-v1.3.0-debug.apk`（26,234,854 字节）
- 最新资产 SHA-256：`2146e38aa14b9aa4eb39fd3e07b78b4d445a0fd2c4706f17c11bf77739076fff`
- 首个 Release：<https://github.com/atqiyu/DouyinKit/releases/tag/v1.2.0>
- 可见性：Public；默认分支：`main`；许可证：MIT
- 已设置仓库描述和 9 个 Topics

## 当前本地状态

- 分支：`main`
- 当前版本：`v1.3.0`
- Release 说明：[RELEASE_NOTES_v1.3.0.md](../RELEASE_NOTES_v1.3.0.md)
- APK 产物：`dist/DouyinKit-v1.3.0-debug.apk`（已在 `.gitignore` 中忽略，不会提交进源码）
- 许可证：[MIT](../LICENSE)

## 仓库信息

- 所有者：`atqiyu`
- 仓库名：`DouyinKit`
- 可见性：Public
- 默认分支：`main`
- 描述：

  > DouyinKit：纯本地 WebView 解析的 Android 抖音作品下载工具，支持视频/图文/LivePhoto/BGM、作品预览、下载记录管理、排序与合集分类。个人使用，MIT 开源。

- Topics：

  `android`, `kotlin`, `jetpack-compose`, `douyin`, `downloader`, `webview`, `media3`, `room`, `personal-use`

## 后续版本发版（GitHub CLI）

仓库和 `origin` 已经创建好，后续发版不需要再执行 `gh repo create` / `gh repo edit`：

```bash
# 1. 确认登录（token 存在系统密钥环，不要写进仓库）
gh auth status

# 2. 构建、测试并提交
./gradlew assembleDebug testDebugUnitTest lintDebug
git add .
git commit -m "feat: ..."
git push origin main

# 3. 复制 APK 到 dist，计算校验和
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/DouyinKit-v1.3.0-debug.apk
sha256sum dist/DouyinKit-v1.3.0-debug.apk
# 把大小和 SHA-256 写入 RELEASE_NOTES_v1.3.0.md

# 4. 打标签、推送并发布 Release
git tag v1.3.0
git push origin v1.3.0
gh release create v1.3.0 "dist/DouyinKit-v1.3.0-debug.apk" \
  --title "DouyinKit v1.3.0" \
  --notes-file RELEASE_NOTES_v1.3.0.md
```

如果从零重建仓库（仅作参考，当前仓库已完成）：

```bash
gh repo create atqiyu/DouyinKit --public --source=. --remote=origin --push \
  --description "DouyinKit：纯本地 WebView 解析的 Android 抖音作品下载工具。个人使用，MIT 开源。"
gh repo edit atqiyu/DouyinKit \
  --add-topic android --add-topic kotlin --add-topic jetpack-compose \
  --add-topic douyin --add-topic downloader --add-topic webview \
  --add-topic media3 --add-topic room --add-topic personal-use
```

## 方式 B：GitHub 网页 + Git

1. 打开 <https://github.com/new>。
2. 仓库名填 `DouyinKit`，选择 Public。
3. **不要**勾选 Add README / .gitignore / license，本地已经准备好，避免冲突。
4. 创建后，在项目根目录执行：

```bash
git remote add origin https://github.com/atqiyu/DouyinKit.git
git push -u origin main
git push origin v1.3.0
```

首次 push 如果自动弹出 Git Credential Manager，登录并授权即可。

5. 进入仓库 **Releases → Draft a new release**，选择标签 `v1.3.0`。
6. 标题填 `DouyinKit v1.3.0`，描述粘贴 `RELEASE_NOTES_v1.3.0.md` 的内容。
7. 上传 `dist/DouyinKit-v1.3.0-debug.apk`，点击 Publish release。

## 发布包说明

该 APK 使用 Android debug keystore 签名，适合个人 sideload 测试，不建议作为长期分发或商店包。若后续需要正式签名：

1. 本地生成 keystore，并放在仓库之外；
2. 在 `app/build.gradle.kts` 或 CI secrets 中配置 release signing；
3. 不要把 keystore、密码或 `local.properties` 提交到仓库；
4. 对同一个应用包名始终使用同一签名，避免更新安装冲突。

## 推送后检查

- 仓库 About 区域显示描述、Topics 和 MIT License
- `main` 上有完整提交历史
- `v1.3.0` 标签存在，且指向包含发布说明的提交
- Releases 页面有 v1.3.0 和 APK 资产，资产大小/SHA-256 与发布说明一致
- App 的“设置 → 关于”中的版本、GitHub 仓库与许可证链接可打开
