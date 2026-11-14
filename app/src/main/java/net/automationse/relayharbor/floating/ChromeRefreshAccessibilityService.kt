package net.automationse.relayharbor.floating

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional user-controlled Chrome-only reload action.
 *
 * The service deliberately does NOT inspect or log accessibility events.
 * It reads only Chrome's native toolbar/menu node labels immediately after the
 * person presses "Chrome再読み込み" on the side launcher.
 *
 * In particular, it never reads webpage content, URL bars, forms, passwords,
 * clipboard, browsing history, or network responses. No automated actions
 * are scheduled or initiated without this explicit user gesture.
 */
class ChromeRefreshAccessibilityService : AccessibilityService() {
    enum class Result {
        SENT,
        NOT_ENABLED,
        NOT_CHROME,
        CONTROL_MISSING
    }

    companion object {
        @Volatile private var active: ChromeRefreshAccessibilityService? = null

        fun connected(): Boolean = active != null

        fun permissionEnabled(context: Context): Boolean = runCatching {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as AccessibilityManager
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any {
                    it.resolveInfo?.serviceInfo?.packageName == context.packageName &&
                        it.resolveInfo?.serviceInfo?.name ==
                            ChromeRefreshAccessibilityService::class.java.name
                }
        }.getOrDefault(false)

        fun requestRefresh(onResult: (Result) -> Unit) {
            val service = active
            if (service == null) {
                onResult(Result.NOT_ENABLED)
                return
            }
            service.handler.post { service.refresh(onResult) }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingCallback: ((Result) -> Unit)? = null
    private var pendingGeneration = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty. This feature acts only on user button presses.
    }

    override fun onInterrupt() {
        completePending(Result.CONTROL_MISSING)
    }

    override fun onDestroy() {
        if (active === this) active = null
        completePending(Result.NOT_ENABLED)
        super.onDestroy()
    }

    private fun refresh(callback: (Result) -> Unit) {
        if (active !== this) {
            callback(Result.NOT_ENABLED)
            return
        }
        if (pendingCallback != null) {
            // Avoid scheduling overlapping menu operations.
            callback(Result.CONTROL_MISSING)
            return
        }
        val root = rootInActiveWindow
        if (root == null || !ChromeRefreshRules.isChrome(root.packageName?.toString())) {
            callback(Result.NOT_CHROME)
            return
        }
        val reload = findNativeControl(root, ::isReload)
        if (reload != null && performNativeClick(reload)) {
            callback(Result.SENT)
            return
        }

        // Chrome's refresh control may only be present inside its overflow menu.
        val more = findNativeControl(root, ::isMore)
        if (more == null || !performNativeClick(more)) {
            callback(Result.CONTROL_MISSING)
            return
        }
        pendingCallback = callback
        val generation = ++pendingGeneration
        // Chrome's menu appears asynchronously. Retry briefly, but never act
        // later than 1.2s after the user's deliberate tap.
        for (delayMs in listOf(130L, 290L, 510L, 820L, 1150L)) {
            handler.postDelayed({
                if (pendingCallback != null && generation == pendingGeneration) {
                    val next = rootInActiveWindow
                    if (!ChromeRefreshRules.isChrome(next?.packageName?.toString())) {
                        completePending(Result.NOT_CHROME)
                    } else {
                        val candidate = next?.let { findNativeControl(it, ::isReload) }
                        if (candidate != null && performNativeClick(candidate)) {
                            completePending(Result.SENT)
                        } else if (delayMs == 1150L) {
                            completePending(Result.CONTROL_MISSING)
                        }
                    }
                }
            }, delayMs)
        }
    }

    private fun isReload(node: AccessibilityNodeInfo): Boolean =
        ChromeRefreshRules.isReloadButton(
            node.viewIdResourceName,
            node.contentDescription?.toString(),
            node.text?.toString()
        )

    private fun isMore(node: AccessibilityNodeInfo): Boolean =
        ChromeRefreshRules.isOverflowButton(
            node.viewIdResourceName,
            node.contentDescription?.toString(),
            node.text?.toString()
        )

    /** Only walk native Chrome nodes. Do not recurse into WebContents pages. */
    private fun findNativeControl(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 450) {
            val node = queue.removeFirst()
            if (!ChromeRefreshRules.isChrome(node.packageName?.toString())) continue
            if (predicate(node)) {
                if (node.isClickable && node.isEnabled && node.isVisibleToUser) return node
                // Some Chrome menu labels are nested in a clickable native row.
                var parent = node.parent
                var depth = 0
                while (parent != null && depth++ < 3) {
                    if (parent.isClickable && parent.isEnabled &&
                        parent.isVisibleToUser &&
                        parent.viewIdResourceName?.startsWith("com.android.chrome:id/") == true
                    ) return parent
                    parent = parent.parent
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun performNativeClick(node: AccessibilityNodeInfo): Boolean =
        runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }.getOrDefault(false)

    private fun completePending(result: Result) {
        val callback = pendingCallback ?: return
        pendingCallback = null
        pendingGeneration++
        callback(result)
    }
}
