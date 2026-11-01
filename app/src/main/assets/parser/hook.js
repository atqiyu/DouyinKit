(function () {
  "use strict";
  if (window.__DK_HOOKED__) return;
  window.__DK_HOOKED__ = true;

  var MAX_MESSAGE_BYTES = 350000;
  var seen = Object.create(null);
  var posted = Object.create(null);
  var bridgeName = "unknown";

  function getBridge() {
    try {
      if (window.DouyinKitBridge && typeof window.DouyinKitBridge.postMessage === "function") {
        bridgeName = "webmessage";
        return function (text) { window.DouyinKitBridge.postMessage(text); };
      }
    } catch (e) {}
    try {
      if (window.DouyinKitAndroid && typeof window.DouyinKitAndroid.postMessage === "function") {
        bridgeName = "jsinterface";
        return function (text) { window.DouyinKitAndroid.postMessage(text); };
      }
    } catch (e) {}
    return null;
  }

  function post(type, payload) {
    try {
      var bridge = getBridge();
      if (!bridge) return false;
      var text = JSON.stringify({ type: type, payload: payload });
      if (!text || text.length > MAX_MESSAGE_BYTES) return false;
      bridge(text);
      return true;
    } catch (e) {
      return false;
    }
  }

  function cleanUrl(input) {
    try {
      var u = new URL(String(input || ""), location.href);
      return u.origin + u.pathname;
    } catch (e) {
      return "";
    }
  }

  var COMPACT_KEYS = [
    "aweme_id", "awemeId",
    "desc", "item_title", "itemTitle",
    "create_time", "createTime",
    "aweme_type", "awemeType",
    "media_type", "mediaType",
    "is_slides", "isSlides",
    "author", "authorInfo",
    "statistics", "stats",
    "video", "images",
    "image_post_info", "imagePostInfo",
    "image_infos", "imageInfos",
    "music"
  ];

  function compactDetail(detail) {
    var out = {};
    if (!detail || typeof detail !== "object") return out;
    for (var i = 0; i < COMPACT_KEYS.length; i++) {
      var key = COMPACT_KEYS[i];
      var value = detail[key];
      if (value !== undefined && value !== null && typeof value !== "function") {
        out[key] = value;
      }
    }
    return out;
  }
  window.__DK_compactDetail = compactDetail;

  function unwrap(payload) {
    if (!payload || typeof payload !== "object") return null;
    if (payload.aweme_detail && typeof payload.aweme_detail === "object") return payload.aweme_detail;
    if (payload.data && payload.data.aweme_detail && typeof payload.data.aweme_detail === "object") {
      return payload.data.aweme_detail;
    }
    if (Array.isArray(payload.item_list) && payload.item_list.length) return payload.item_list[0];
    return null;
  }

  function acceptDetail(detail, url, bytes, source) {
    try {
      var id = detail.aweme_id || detail.awemeId;
      if (!id) return;
      id = String(id);
      var size = Number(bytes) || 0;
      var state = seen[id];
      if (!state) {
        seen[id] = { count: 1, size: size };
      } else {
        if (state.count >= 3) return;
        if (size <= state.size * 1.02) return;
        state.count += 1;
        state.size = size;
      }
      var compact = compactDetail(detail);
      var ok = post("detail", {
        awemeId: id,
        detail: compact,
        url: cleanUrl(url),
        bytes: size,
        source: source || "xhr",
        at: Date.now(),
        ua: navigator.userAgent
      });
      if (!ok) {
        try { delete seen[id]; } catch (e) {}
        return;
      }
      posted[id] = (posted[id] || 0) + 1;
    } catch (e) {}
  }

  function handleText(text, url, source) {
    if (!text || typeof text !== "string" || text.length < 20) return;
    if (text.length > 3000000) return;
    var payload;
    try { payload = JSON.parse(text); } catch (e) { return; }
    var detail = unwrap(payload);
    if (!detail) return;
    acceptDetail(detail, url, text.length, source);
  }

  function isDetailUrl(url) {
    return typeof url === "string" && url.indexOf("/aweme/v1/web/aweme/detail/") !== -1;
  }

  function hookFetch() {
    var originalFetch = window.fetch;
    if (typeof originalFetch !== "function") return;
    window.fetch = function (input, init) {
      var url = "";
      try {
        url = typeof input === "string" ? input : (input && input.url) || "";
      } catch (e) {}
      var promise = originalFetch.apply(this, arguments);
      try {
        if (isDetailUrl(url)) {
          promise.then(function (response) {
            try {
              var clone = response.clone();
              clone.text().then(function (text) {
                handleText(text, (response && response.url) || url, "fetch");
              }).catch(function () {});
            } catch (e) {}
          }).catch(function () {});
        }
      } catch (e) {}
      return promise;
    };
  }

  function hookXhr() {
    var XHR = window.XMLHttpRequest;
    if (!XHR || !XHR.prototype) return;
    var originalOpen = XHR.prototype.open;
    var originalSend = XHR.prototype.send;

    XHR.prototype.open = function (method, url) {
      try { this.__dkUrl = String(url || ""); } catch (e) {}
      return originalOpen.apply(this, arguments);
    };

    XHR.prototype.send = function () {
      try {
        var xhr = this;
        xhr.addEventListener("load", function () {
          try {
            if (!isDetailUrl(xhr.__dkUrl)) return;
            var text = null;
            var responseType = xhr.responseType || "";
            if (responseType === "" || responseType === "text") {
              text = xhr.responseText;
            } else if (responseType === "json" && xhr.response) {
              text = JSON.stringify(xhr.response);
            }
            if (text) handleText(text, xhr.responseURL || xhr.__dkUrl, "xhr");
          } catch (e) {}
        });
      } catch (e) {}
      return originalSend.apply(this, arguments);
    };
  }

  window.__DK_postPage = function (awemeId, detail, meta) {
    try {
      if (!detail || typeof detail !== "object") return false;
      var compact = compactDetail(detail);
      return post("page", {
        awemeId: String(awemeId || detail.aweme_id || detail.awemeId || ""),
        detail: compact,
        url: cleanUrl(location.href),
        bytes: 0,
        source: "page_model",
        at: Date.now(),
        meta: meta || null,
        ua: navigator.userAgent
      });
    } catch (e) {
      return false;
    }
  };

  var CHALLENGE_TEXT = [
    "安全验证", "安全校验", "完成验证", "请完成验证", "请完成以下验证",
    "拖动滑块", "滑动验证", "滑块验证", "拼图验证", "人机验证", "行为验证",
    "访问过于频繁", "操作过于频繁", "操作频繁", "网络环境异常", "环境异常",
    "系统繁忙", "请稍后再试", "请求过于频繁", "验证失败", "验证已过期",
    "请完成下方验证", "按住滑块"
  ];
  var CHALLENGE_URL_HINTS = [
    "captcha", "verify", "challenge", "secsdk", "security-check"
  ];
  var LOGIN_TEXT = [
    "登录后免费畅享", "扫码登录", "验证码登录", "密码登录",
    "登录后继续", "请登录后查看", "手机号登录", "一键登录",
    "登录即代表同意"
  ];
  var LOGIN_URL_HINTS = [
    "/login", "login?", "passport", "/signin", "/sso"
  ];
  var CHALLENGE_SELECTOR = [
    "[class*='captcha']", "[id*='captcha']",
    "[class*='secsdk']", "[id*='secsdk']",
    "[class*='verify_container']", "[id*='verify_container']",
    "[class*='verify-wrapper']", "[id*='verify-wrapper']",
    "[class*='captcha_verify']", "[id*='captcha_verify']",
    "[class*='verify-slide']", "[id*='verify-slide']"
  ].join(",");

  function isVisible(el) {
    try {
      if (!el || !el.getBoundingClientRect) return false;
      var style = window.getComputedStyle ? window.getComputedStyle(el) : null;
      if (style && (style.display === "none" || style.visibility === "hidden" ||
          parseFloat(style.opacity || "1") === 0)) {
        return false;
      }
      var rect = el.getBoundingClientRect();
      return rect.width > 2 && rect.height > 2;
    } catch (e) {
      return false;
    }
  }

  function firstVisible(root, selector) {
    try {
      var nodes = root.querySelectorAll ? root.querySelectorAll(selector) : [];
      for (var i = 0; i < nodes.length; i++) {
        if (isVisible(nodes[i])) return nodes[i];
      }
    } catch (e) {}
    return null;
  }

  function challengeInRoot(root, label) {
    try {
      if (!root || typeof root.querySelectorAll !== "function") return "";
      if (firstVisible(root, CHALLENGE_SELECTOR)) return label + ":selector";
      var iframes = root.querySelectorAll("iframe");
      for (var i = 0; i < iframes.length; i++) {
        if (!isVisible(iframes[i])) continue;
        var src = String(iframes[i].src || "").toLowerCase();
        for (var j = 0; j < CHALLENGE_URL_HINTS.length; j++) {
          if (src.indexOf(CHALLENGE_URL_HINTS[j]) !== -1) {
            return label + ":iframe:" + CHALLENGE_URL_HINTS[j];
          }
        }
      }
    } catch (e) {}
    return "";
  }

  function detectLogin() {
    try {
      var href = String(location.href || "").toLowerCase();
      for (var i = 0; i < LOGIN_URL_HINTS.length; i++) {
        if (href.indexOf(LOGIN_URL_HINTS[i]) !== -1) {
          return "url:" + LOGIN_URL_HINTS[i];
        }
      }
      var title = String(document.title || "");
      var text = document.body && document.body.innerText
        ? String(document.body.innerText).slice(0, 4000)
        : "";
      var haystack = title + "\n" + text;
      for (var j = 0; j < LOGIN_TEXT.length; j++) {
        if (haystack.indexOf(LOGIN_TEXT[j]) !== -1) {
          return "text:" + LOGIN_TEXT[j];
        }
      }
    } catch (e) {}
    return "";
  }

  function detectChallenge(suppressUrlHint) {
    try {
      var href = String(location.href || "").toLowerCase();
      if (!suppressUrlHint) {
        for (var i = 0; i < CHALLENGE_URL_HINTS.length; i++) {
          if (href.indexOf(CHALLENGE_URL_HINTS[i]) !== -1) {
            return "url:" + CHALLENGE_URL_HINTS[i];
          }
        }
      }
      var title = String(document.title || "");
      var text = document.body && document.body.innerText
        ? String(document.body.innerText).slice(0, 4000)
        : "";
      var haystack = title + "\n" + text;
      for (var j = 0; j < CHALLENGE_TEXT.length; j++) {
        if (haystack.indexOf(CHALLENGE_TEXT[j]) !== -1) {
          return "text:" + CHALLENGE_TEXT[j];
        }
      }
      var reason = challengeInRoot(document, "dom");
      if (reason) return reason;
      // Verification widgets can be mounted inside an open shadow root.
      var all = document.querySelectorAll ? document.querySelectorAll("*") : [];
      var limit = Math.min(all.length, 2000);
      for (var k = 0; k < limit; k++) {
        var host = all[k];
        if (host && host.shadowRoot) {
          reason = challengeInRoot(host.shadowRoot, "shadow");
          if (reason) return reason;
        }
      }
    } catch (e) {}
    return "";
  }

  window.__DK_getState = function () {
    try {
      var loginReason = detectLogin();
      var challengeReason = detectChallenge(!!loginReason);
      return JSON.stringify({
        href: cleanUrl(location.href),
        title: document.title || "",
        readyState: document.readyState || "",
        hook: true,
        hasRoot: !!document.querySelector("#root"),
        bodyLength: document.body ? document.body.innerHTML.length : 0,
        text: document.body && document.body.innerText ? document.body.innerText.slice(0, 1500) : "",
        detailIds: Object.keys(seen),
        posted: posted,
        bridge: bridgeName,
        challenge: !!challengeReason,
        challengeReason: challengeReason,
        login: !!loginReason,
        loginReason: loginReason,
        at: Date.now()
      });
    } catch (e) {
      return "{}";
    }
  };

  try {
    hookFetch();
    hookXhr();
  } catch (e) {}

  post("diag", {
    event: "hook_installed",
    href: cleanUrl(location.href),
    ua: navigator.userAgent,
    hasRoot: !!document.querySelector("#root"),
    at: Date.now()
  });
})();
