# 解析实现与排障

## 为什么用 WebView

抖音网页详情接口需要页面环境生成 `a_bogus`、`x-secsdk-web-signature` 等签名，并绑定 Cookie、时间戳和浏览器指纹。纯 HTTP 复刻不稳定；项目改用系统 WebView 执行真实页面 JS，再拦截页面自己发出的详情响应。

`iesdouyin.com` 分享页纯 HTTP 能拿到元数据和图集，但视频只有 `playwm` 带水印地址，且没有码率档、LivePhoto、BGM 直链，因此只适合作为未来兼容兜底，不是主路径。

## 链接与短链

`LinkExtractor` 规则：

- 支持 `v.douyin.com/...`、`www.douyin.com/video|note|slides/<id>`、`www.iesdouyin.com/share/...`、裸 8–25 位 ID
- 保留短码大小写（`l` / `I` 不同）
- 自动补 `https://`，`http://` 升级为 `https://`
- 清理尾部中文标点，拒绝非 `douyin.com` / `iesdouyin.com` 主机

`ShortLinkResolver` 只读第一跳 302 `Location`，从路径取 ID；不跟随到分享页，避免 UA 差异和 App 引导页。

## WebView 配置

`DouyinWebViewEngine`：

- 桌面 Chrome UA：从 WebView 默认 UA 提取 Chrome 版本，避免版本号明显不一致
- JavaScript、DOM Storage、第三方 Cookie、wide viewport；禁用文件/内容访问、弹窗、混合内容、地理定位
- 全屏 WebView 位于解析进度层下方，真实渲染；验证码时进度层收起，用户可直接操作页面
- `WebView.setWebContentsDebuggingEnabled` 仅在 debug 构建开启
- SSL 错误拒绝；导航只允许抖音域名；`intent://`、`snssdk1128://` 等外部 scheme 被拦截
- `onRenderProcessGone` 视为 fatal，提示重试

## JS Bridge 契约

`hook.js` 通过以下对象发送 JSON 字符串：

- 首选 `window.DouyinKitBridge.postMessage(text)`（`WebViewCompat.addWebMessageListener`）
- 回退 `window.DouyinKitAndroid.postMessage(text)`（`@JavascriptInterface`）

消息格式：

```json
{
  "type": "detail" | "page" | "diag",
  "payload": {
    "awemeId": "7697896601788248226",
    "detail": { "...": "裁剪后的详情对象" },
    "url": "https://www.douyin.com/video/7697896601788248226",
    "bytes": 12345,
    "source": "xhr" | "fetch" | "page_model",
    "at": 1790000000000
  }
}
```

Kotlin 侧校验：

- `type` 只接受 detail/page/diag
- `awemeId` 必须为 8–25 位数字
- JSON 消息最多 500k 字符（JS 侧上限 350k）
- WebMessage 来源域名必须是抖音；JS 接口回退会再检查当前 WebView URL

详情裁剪白名单：`aweme_id/desc/create_time/aweme_type/media_type/is_slides/author/statistics/video/images/music` 等；避免把页面上下文里的 React 循环引用或无关大数据传回原生。

## 捕获与兜底

1. **XHR/fetch**：包装 `open/send` 和 `fetch`，只处理 URL 含 `/aweme/v1/web/aweme/detail/` 的响应；XHR 兼容 `responseType=json`。
2. **页面模型**：`page_model.js` 遍历 `#root` 的 React fiber、`_ROUTER_DATA`、`SSR_HYDRATED_DATA`、`RENDER_DATA` 等，按 aweme_id、档位数量、图片数量、作者/统计/playApi 打分，返回最佳详情对象。
3. **图文补齐**：检测到 `images` 后加载 `/note/<id>`，等页面 ready，再提取页面模型；按档位名/分辨率/图片 uri 合并 fps、codec、playApi、LivePhoto。
4. **验证码**：页面文本/href 出现 captcha/verify/安全验证，或长时间无详情且无 root，自动显示页面；用户完成后详情响应会继续被捕获。总等待最多 120 秒。不实现验证码破解。
5. **失败**：抛出 `DETAIL_TIMEOUT` / `NEEDS_VERIFICATION` / `NO_MEDIA` 等错误，结果页提供重试/打开页面/重新解析。

## 诊断

解析页底部“解析诊断”显示：

- `stage`、页面进度
- 当前 URL（无 query）
- `hook` 是否注入、`hasRoot`、`bodyLength`
- Bridge 类型（webmessage/jsinterface）
- 已捕获详情 ID 列表
- 页面正文前 300 字符

Logcat tag：`DouyinWebView`（仅 debug 输出 console）。

真机深度调试：

1. `adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>` 或 Chrome `chrome://inspect` 连接 WebView；
2. 在 ParseActivity “显示页面”后观察 Network 中是否发出 `aweme/detail`；
3. 在 Console 执行 `window.__DK_getState()` 检查 hook、bridge；
4. 执行 `window.__DK_extractPageModel('<aweme_id>')` 检查页面模型兜底。

## 抖音改版时的维护点

| 症状 | 可能位置 | 处理 |
|---|---|---|
| `detailIds` 为空、页面正常 | 详情接口 URL 变更 | 更新 `hook.js` 的 `isDetailUrl` |
| hook 注入失败 | WebView 特性/CSP/时序 | 检查 `DOCUMENT_START_SCRIPT` 支持；保留 onPageStarted 注入 |
| 详情字段缺失 | 上游字段改名 | 在 `DouyinDetailNormalizer` 增加 snake/camel 候选 key |
| 档位为空 | 页面模型字段变化 | 更新 `page_model.js` 的扫描/打分 |
| 图文没有 LivePhoto | note 页面模型变化 | 检查 `/note/` 导航与 `images[].video` |
| 风控频繁 | UA/验证码/频率 | 手动完成验证；降低频率；保持 profile Cookie；必要时更新 WebView |
| 下载 403 | CDN 策略 | 已带 Referer，尝试 `playApi`；确认 `UrlPolicy` 是否误拦截新 CDN 域名 |

## 安全约束

- 不读取/导出 Cookie；清理入口在设置
- 不执行页面下发的任意 native 方法；桥只有 postMessage
- 导航白名单 + 下载 URL 白名单 + 最终重定向校验
- 日志不记录签名 query；历史快照只在本机
- 不实现验证码破解、批量抓取或去水印分发
