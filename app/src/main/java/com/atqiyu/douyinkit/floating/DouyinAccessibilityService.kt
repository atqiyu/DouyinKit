package com.atqiyu.douyinkit.floating

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Personal-device automation used only by the floating assistant's 连播模式.
 * It reads the current Douyin window, clicks "分享" and then "分享链接" for the user.
 * Nothing is uploaded and the service can be disabled at any time in system settings.
 */
class DouyinAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastLoggedPackage: String? = null
    private var lastForegroundCheck = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        FloatingAssistantBus.setAccessibilityReady(true)
        rootInActiveWindow?.packageName?.toString()?.let(FloatingAssistantBus::setForeground)
        Log.d(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (DouyinPackages.of(packageName) != null) {
            val texts = event.text
            val label = when {
                texts == null || texts.isEmpty() -> event.contentDescription?.toString()
                texts.size == 1 -> texts[0]?.toString()
                else -> "${texts[0]} ${texts[1]}"
            }
            if (label?.contains("链接已复制") == true || label?.contains("复制成功") == true) {
                FloatingAssistantBus.signalClipboardCopy()
            }
        }
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        // Content-change events often arrive from a background app after the launcher is shown.
        // The active window is authoritative; content events are throttled because they are noisy.
        val stateChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val now = SystemClock.uptimeMillis()
        if (!stateChanged && now - lastForegroundCheck < 800L) return
        lastForegroundCheck = now
        if (DouyinPackages.isSystemUi(packageName) || packageName == this.packageName) return
        val eventTarget = DouyinPackages.targetOf(packageName)
        if (eventTarget == FloatingAssistantBus.state.value.foreground) return
        val activePackage = runCatching {
            rootInActiveWindow?.packageName?.toString()
        }.getOrNull()
        val target = when {
            activePackage == null -> packageName
            activePackage == this.packageName -> return
            DouyinPackages.isSystemUi(activePackage) -> return
            else -> activePackage
        }
        if (DouyinPackages.isSystemUi(target) || target == this.packageName) return
        val nextTarget = DouyinPackages.targetOf(target)
        if (nextTarget == FloatingAssistantBus.state.value.foreground) return
        if (lastLoggedPackage != target) {
            Log.d(TAG, "window event package=$target")
            lastLoggedPackage = target
        }
        FloatingAssistantBus.setForeground(target)
    }

    override fun onInterrupt() {
        Log.d(TAG, "interrupted")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        FloatingAssistantBus.setAccessibilityReady(false)
        scope.cancel()
        super.onDestroy()
    }

    fun readClipboardText(): String? {
        return runCatching {
            val manager = getSystemService(ClipboardManager::class.java)
            val clip = manager?.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            clip.getItemAt(0)?.coerceToText(this)?.toString()
        }.getOrNull()
    }

    suspend fun autoShareAndCopy(): AutomationResult = withContext(Dispatchers.Main) {
        if (!isCurrentDouyin()) {
            return@withContext AutomationResult.Failure(
                "请先在抖音或抖音极速版中打开一个作品",
            )
        }
        val root = rootInActiveWindow
            ?: return@withContext AutomationResult.Failure("无法读取抖音页面")
        Log.d(TAG, "looking for share button")
        val share = findShareButton(root)
            ?: return@withContext AutomationResult.Failure("没有找到右下角分享按钮")
        Log.d(TAG, "share button found, clicking")
        if (!clickNode(share)) {
            return@withContext AutomationResult.Failure("点击分享按钮失败")
        }
        delay(650)

        var link = findSharedLink()
        if (link == null) {
            revealShareLink()
            link = withTimeoutOrNull(2_500) {
                while (true) {
                    val found = findSharedLink()
                    if (found != null) return@withTimeoutOrNull found
                    delay(220)
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
        }
        if (link == null) {
            Log.d(TAG, "share link not found after reveal")
            return@withContext AutomationResult.Failure("没有找到“分享链接”，请确认分享面板已打开")
        }
        Log.d(TAG, "share link found, clicking")
        if (!clickNode(link)) {
            return@withContext AutomationResult.Failure("点击“分享链接”失败")
        }
        delay(750)
        if (isShareSheetVisible()) {
            Log.d(TAG, "dismissing share sheet")
            dismissShareSheet()
        }
        AutomationResult.Success
    }

    private fun isCurrentDouyin(): Boolean {
        val packageName = rootInActiveWindow?.packageName?.toString()
        return DouyinPackages.of(packageName) != null ||
            FloatingAssistantBus.state.value.foreground.isDouyin
    }

    private fun findShareButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screen = resources.displayMetrics
        val candidates = collectNodes(root)
            .filter { node ->
                val text = node.text?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()
                val label = text.ifBlank { desc }
                label.isNotBlank() &&
                    !label.contains("分享链接") &&
                    (label == "分享" || label.startsWith("分享") ||
                        desc.startsWith("分享")) &&
                    node.isVisibleToUser
            }
            .map { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val rightSide = if (bounds.centerX() > screen.widthPixels * 0.52f) 40 else 0
                val lowerSide = if (bounds.centerY() > screen.heightPixels * 0.35f) 20 else 0
                val clickable = if (node.isClickable || clickableAncestor(node) != null) 30 else 0
                node to (rightSide + lowerSide + clickable)
            }
            .sortedByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    private suspend fun findSharedLink(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val exact = collectNodes(root).filter { node ->
            val label = node.text?.toString().orEmpty()
                .ifBlank { node.contentDescription?.toString().orEmpty() }
            node.isVisibleToUser && (
                label.startsWith("分享链接") ||
                    label.startsWith("复制链接") ||
                    label.startsWith("复制分享链接")
                )
        }
        return exact.firstOrNull { it.isClickable || clickableAncestor(it) != null }
            ?: exact.firstOrNull()
    }

    private fun isShareSheetVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        val screenHeight = resources.displayMetrics.heightPixels
        return collectNodes(root).any { node ->
            if (!node.isVisibleToUser) return@any false
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.centerY() < screenHeight * 0.55f) return@any false
            val label = node.text?.toString().orEmpty()
                .ifBlank { node.contentDescription?.toString().orEmpty() }
            label.contains("链接已复制成功") ||
                label == "微信" ||
                label == "朋友圈" ||
                label == "QQ" ||
                label == "QQ空间" ||
                label == "分享链接"
        }
    }

    private fun dismissShareSheet() {
        val metrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(metrics.widthPixels / 2f, metrics.heightPixels * 0.22f)
            lineTo(metrics.widthPixels / 2f, metrics.heightPixels * 0.22f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun revealShareLink() {
        val root = rootInActiveWindow ?: return
        val screen = resources.displayMetrics
        val scrollable = collectNodes(root).firstOrNull { node ->
            node.isScrollable && node.isVisibleToUser && node.boundsBottom() > screen.heightPixels * 0.55f
        }
        if (scrollable != null) {
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            return
        }
        val y = (screen.heightPixels * 0.82f).toInt()
        swipe(y, x1 = (screen.widthPixels * 0.2f).toInt(), x2 = (screen.widthPixels * 0.82f).toInt())
        swipe(y, x1 = (screen.widthPixels * 0.82f).toInt(), x2 = (screen.widthPixels * 0.2f).toInt())
    }

    private fun swipe(y: Int, x1: Int, x2: Int) {
        val path = Path().apply {
            moveTo(x1.toFloat(), y.toFloat())
            lineTo(x2.toFloat(), y.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 320L))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        var depth = 0
        while (target != null && depth < 6) {
            if (target.isClickable && target.isEnabled) {
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                break
            }
            target = target.parent
            depth++
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        val path = Path().apply {
            moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            lineTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable && parent.isEnabled) return parent
            parent = parent.parent
            depth++
        }
        return null
    }

    private fun collectNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>(96)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 2_500) {
            val node = queue.removeFirst()
            result += node
            visited++
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.addLast(it) }
            }
        }
        return result
    }

    private fun AccessibilityNodeInfo.boundsBottom(): Int {
        val bounds = Rect()
        getBoundsInScreen(bounds)
        return bounds.bottom
    }

    sealed interface AutomationResult {
        data object Success : AutomationResult
        data class Failure(val message: String) : AutomationResult
    }

    companion object {
        private const val TAG = "DouyinAccessibility"

        @Volatile
        var instance: DouyinAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null

        fun openAccessibilitySettings(context: android.content.Context) {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}
