(function () {
  "use strict";
  if (window.__DK_PAGE_MODEL_INSTALLED__) return;
  window.__DK_PAGE_MODEL_INSTALLED__ = true;

  var MAX_DEPTH = 5;
  var MAX_NODES = 15000;
  var MAX_MS = 2200;
  var MAX_CANDIDATES = 40;
  var SKIP_KEYS = {
    "return": 1,
    "stateNode": 1,
    "_owner": 1,
    "alternate": 1,
    "window": 1,
    "document": 1,
    "parentNode": 1,
    "ownerDocument": 1,
    "defaultView": 1
  };

  function score(detail) {
    var video = detail.video || {};
    var list = video.bitRateList || video.bit_rate || [];
    var images = detail.images || [];
    var score = 0;
    if (Array.isArray(list)) score += list.length * 10;
    if (Array.isArray(images)) score += images.length * 10 + (images.length ? 10 : 0);
    if (detail.authorInfo || detail.author) score += 5;
    if (detail.stats || detail.statistics) score += 5;
    if (video.playApi || video.play_api) score += 10;
    try { score += Math.min(Object.keys(detail).length, 30); } catch (e) {}
    return score;
  }

  function useful(detail, expectedId) {
    if (!detail || typeof detail !== "object") return false;
    var id = detail.aweme_id || detail.awemeId;
    if (id === undefined || id === null) return false;
    if (String(id) !== String(expectedId)) return false;

    var video = detail.video;
    var images = detail.images;
    var hasVideo = !!video && (
      (Array.isArray(video.bitRateList) && video.bitRateList.length > 0) ||
      (Array.isArray(video.bit_rate) && video.bit_rate.length > 0) ||
      !!video.play_addr || !!video.playAddr || !!video.playApi || !!video.play_api
    );
    var hasImages = Array.isArray(images) && images.length > 0;
    return hasVideo || hasImages;
  }

  window.__DK_extractPageModel = function (awemeId) {
    try {
      var started = Date.now();
      var deadline = started + MAX_MS;
      var budget = { nodes: 0, seen: new WeakSet(), deadline: deadline };
      var candidates = 0;
      var best = null;
      var bestScore = -1;

      function consider(value) {
        if (candidates >= MAX_CANDIDATES) return;
        try {
          if (value.aweme_detail && typeof value.aweme_detail === "object") {
            consider(value.aweme_detail);
          }
          if (!useful(value, awemeId)) return;
          candidates += 1;
          var s = score(value);
          if (s > bestScore) {
            bestScore = s;
            best = value;
          }
        } catch (e) {}
      }

      function scan(value, depth) {
        if (!value || typeof value !== "object") return;
        if (depth > MAX_DEPTH) return;
        if (budget.nodes++ > MAX_NODES || Date.now() > budget.deadline) return;
        if (candidates >= MAX_CANDIDATES) return;
        if (value === window || value === document) return;
        if (value.nodeType) return;
        try {
          if (budget.seen.has(value)) return;
          budget.seen.add(value);
        } catch (e) {
          return;
        }

        consider(value);

        var keys = [];
        try { keys = Object.keys(value); } catch (e) { return; }
        if (keys.length > 80) keys = keys.slice(0, 80);

        for (var i = 0; i < keys.length; i++) {
          var key = keys[i];
          if (SKIP_KEYS[key]) continue;
          var child;
          try { child = value[key]; } catch (e) { continue; }
          if (!child || typeof child !== "object") continue;
          scan(child, depth + 1);
          if (Date.now() > budget.deadline || candidates >= MAX_CANDIDATES) break;
        }
      }

      // Server-rendered data / router data is often the cheapest and most complete source.
      scan(window._ROUTER_DATA, 0);
      scan(window._SSR_HYDRATED_DATA, 0);
      scan(window.__INITIAL_STATE__, 0);
      scan(window._SSR_DATA, 0);
      scan(window.RENDER_DATA, 0);

      // Older SSR pages put a URL-encoded JSON blob in #RENDER_DATA.
      try {
        var renderNode = document.querySelector("#RENDER_DATA");
        if (renderNode && renderNode.textContent) {
          scan(JSON.parse(decodeURIComponent(renderNode.textContent)), 0);
        }
      } catch (e) {}
      try {
        if (typeof window.RENDER_DATA === "string" && window.RENDER_DATA.length > 20) {
          scan(JSON.parse(decodeURIComponent(window.RENDER_DATA)), 0);
        }
      } catch (e) {}

      // React container props on #root.
      var root = document.querySelector("#root");
      if (root) {
        var rootKeys = [];
        try { rootKeys = Object.getOwnPropertyNames(root); } catch (e) {}
        for (var i = 0; i < rootKeys.length; i++) {
          var key = rootKeys[i];
          if (key.indexOf("__react") === -1 && key.indexOf("_react") === -1) continue;
          try { scan(root[key], 0); } catch (e) {}
        }
      }

      // Some layouts mount React on documentElement or a child of #root.
      if (!best) {
        try {
          var docKeys = Object.getOwnPropertyNames(document.documentElement);
          for (var j = 0; j < docKeys.length; j++) {
            var dk = docKeys[j];
            if (dk.indexOf("__react") === -1 && dk.indexOf("_react") === -1) continue;
            scan(document.documentElement[dk], 0);
          }
        } catch (e) {}
      }

      if (best) {
        window.__DK_postPage(awemeId, best, {
          method: "fiber_or_ssr",
          score: bestScore,
          candidates: candidates,
          ms: Date.now() - started
        });
        return true;
      }
      return false;
    } catch (e) {
      return false;
    }
  };
})();
