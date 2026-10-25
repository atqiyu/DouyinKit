# GitHub 仓库与首个 Release

本地仓库已经准备好：

- 分支：`main`
- 首个版本：`v1.2.0`
- Release 说明：[RELEASE_NOTES_v1.2.0.md](../RELEASE_NOTES_v1.2.0.md)
- APK 产物：`dist/DouyinKit-v1.2.0-debug.apk`（已在 `.gitignore` 中忽略，不会提交进源码）
- SHA-256：`5468cbbdf30b41c468a1afa9179b7b47ea1106886c8173358d51486d35817715`
- 许可证：[MIT](../LICENSE)

## 仓库信息

- 所有者：`atqiyu`
- 仓库名：`DouyinKit`
- 可见性：Public
- 默认分支：`main`
- 描述：

  > DouyinKit：纯本地 WebView 解析的 Android 抖音作品下载工具，支持视频/图文/LivePhoto/BGM、作品预览、下载记录管理和重复下载策略。个人使用，MIT 开源。

- Topics：

  `android`, `kotlin`, `jetpack-compose`, `douyin`, `downloader`, `webview`, `media3`, `room`, `personal-use`

## 方式 A：GitHub CLI（推荐）

GitHub CLI 需要在本机登录一次。当前开发环境没有 GitHub 登录态，所以这一步需要你授权。

```bash
# 1. 安装 GitHub CLI（已安装可跳过）
winget install --id GitHub.cli --exact

# 2. 重新打开终端后登录；选择 GitHub.com → HTTPS → Login with a web browser
gh auth login --hostname github.com --git-protocol https --web

# 3. 在项目根目录创建公开仓库、设置 origin 并推送 main
gh repo create atqiyu/DouyinKit --public --source=. --remote=origin --push \
  --description "DouyinKit：纯本地 WebView 解析的 Android 抖音作品下载工具，支持视频/图文/LivePhoto/BGM、作品预览、下载记录管理和重复下载策略。个人使用，MIT 开源。"

gh repo edit atqiyu/DouyinKit \
  --add-topic android --add-topic kotlin --add-topic jetpack-compose \
  --add-topic douyin --add-topic downloader --add-topic webview \
  --add-topic media3 --add-topic room --add-topic personal-use

# 4. 推送 v1.2.0 标签并发布首个 Release（附带 APK）
git push origin v1.2.0
gh release create v1.2.0 "dist/DouyinKit-v1.2.0-debug.apk" \
  --title "DouyinKit v1.2.0" \
  --notes-file RELEASE_NOTES_v1.2.0.md
```

## 方式 B：GitHub 网页 + Git

1. 打开 <https://github.com/new>。
2. 仓库名填 `DouyinKit`，选择 Public。
3. **不要**勾选 Add README / .gitignore / license，本地已经准备好，避免冲突。
4. 创建后，在项目根目录执行：

```bash
git remote add origin https://github.com/atqiyu/DouyinKit.git
git push -u origin main
git push origin v1.2.0
```

首次 push 如果弹出 Git Credential Manager，登录并授权即可。

5. 进入仓库 **Releases → Draft a new release**，选择标签 `v1.2.0`。
6. 标题填 `DouyinKit v1.2.0`，描述粘贴 `RELEASE_NOTES_v1.2.0.md` 的内容。
7. 上传 `dist/DouyinKit-v1.2.0-debug.apk`，点击 Publish release。

## 发布包说明

该 APK 使用 Android debug keystore 签名，适合个人 sideload 测试，不建议作为长期分发或商店包。若后续需要正式签名：

1. 本地生成 keystore，并放在仓库之外；
2. 在 `app/build.gradle.kts` 或 CI secrets 中配置 release signing；
3. 不要把 keystore、密码或 `local.properties` 提交到仓库；
4. 对同一个应用包名始终使用同一签名，避免更新安装冲突。

## 推送后检查

- 仓库 About 区域显示描述、Topics 和 MIT License
- `main` 上有完整提交历史
- `v1.2.0` 标签存在
- Releases 页面有首个版本和 APK 资产
- App 的“设置 → 关于”中的 GitHub 仓库与许可证链接可打开
