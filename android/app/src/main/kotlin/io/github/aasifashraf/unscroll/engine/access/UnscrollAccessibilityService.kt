package io.github.aasifashraf.unscroll.engine.access

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import io.github.aasifashraf.unscroll.engine.core.Engine
import io.github.aasifashraf.unscroll.engine.shortform.ShortFormWatcher

/**
 * Notices which app is open (for blocking) and reads browser address bars (for website
 * blocking), and closes short-video feeds in apps the user chose. It reads only window/package
 * names, the address-bar text, and in those apps the view IDs, class names and sizes of the
 * screen (never its text), matches them against the user's rules on the device, and stores nothing.
 */
class UnscrollAccessibilityService : AccessibilityService() {
    private var lastAddress: String? = null
    private var lastAddressCheck = 0L
    private val shortForm = ShortFormWatcher(this)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Engine.init(this)
        Engine.attachAccessibility(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Android switches off an accessibility service that crashes, which would silently stop
        // all blocking. Never let an unexpected error escape.
        try {
            handle(event)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Ignored error while handling an accessibility event", e)
        }
    }

    private fun handle(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        shortForm.onEvent(event)
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Engine.onForeground(pkg, event.className?.toString())
                if (pkg in BROWSER_URL_BARS) {
                    lastAddress = null // re-check on every return to the browser (no bypass via Home)
                    checkAddressBar(pkg)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> if (pkg in BROWSER_URL_BARS) {
                val now = SystemClock.uptimeMillis()
                if (now - lastAddressCheck >= ADDRESS_THROTTLE_MILLIS) { // content events are very frequent
                    lastAddressCheck = now
                    checkAddressBar(pkg)
                }
            }
            else -> Unit
        }
    }

    private fun checkAddressBar(pkg: String) {
        val root = try {
            rootInActiveWindow
        } catch (_: RuntimeException) {
            null
        } ?: return // view data unavailable (e.g. Advanced Protection): fail open
        val text = BROWSER_URL_BARS.getValue(pkg).firstNotNullOfOrNull { id ->
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.text?.toString()
        }
        if (text.isNullOrBlank() || text == lastAddress) return
        lastAddress = text
        Engine.onAddressBar(pkg, text)
    }

    /** After "Go back": check the address again even if it didn't change. Main thread. */
    fun recheckAddressBar() {
        lastAddress = null
        val pkg = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (_: RuntimeException) {
            null
        } ?: return
        if (pkg in BROWSER_URL_BARS) checkAddressBar(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shortForm.stop()
        Engine.attachAccessibility(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shortForm.stop()
        Engine.attachAccessibility(null)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "UnscrollA11y"
        const val ADDRESS_THROTTLE_MILLIS = 250L
        /** Address-bar view IDs per supported browser. Unknown IDs simply never match (fail open). */
        val BROWSER_URL_BARS = mapOf(
            "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
            "com.chrome.beta" to listOf("com.chrome.beta:id/url_bar"),
            "org.mozilla.firefox" to listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view"),
            "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
            "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
            "com.sec.android.app.sbrowser" to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text"),
        )
    }
}
