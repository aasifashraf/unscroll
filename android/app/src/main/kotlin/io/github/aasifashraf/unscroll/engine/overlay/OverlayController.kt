package io.github.aasifashraf.unscroll.engine.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.CountDownTimer
import android.provider.Settings
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.aasifashraf.unscroll.R
import io.github.aasifashraf.unscroll.engine.rules.BlockReason
import io.github.aasifashraf.unscroll.engine.service.Notifications
import java.util.Date

/**
 * Full-screen block and pause screens drawn over other apps. Uses the accessibility overlay
 * window type when the service is connected (no extra permission), otherwise the
 * "display over other apps" window. Main thread only.
 */
class OverlayController(
    private val context: Context,
    private val accessibilityService: () -> AccessibilityService?,
    private val onGoHome: () -> Unit,
    private val onGoBack: () -> Unit,
) {
    private enum class Kind { BLOCK, PAUSE }

    private var view: View? = null
    private var windowManager: WindowManager? = null
    private var kind: Kind? = null
    private var timer: CountDownTimer? = null

    @Volatile
    private var target: String? = null

    fun isShowingFor(pkg: String) = target == pkg

    fun showBlock(pkg: String, label: String, reason: BlockReason, until: Long?, canExtend: Boolean, onExtend: () -> Unit) {
        val message = when (reason) {
            BlockReason.DAILY_LIMIT -> context.getString(R.string.block_limit_message)
            BlockReason.OPEN_LIMIT -> context.getString(R.string.block_opens_message)
            BlockReason.FOCUS -> context.getString(R.string.block_focus_message)
            BlockReason.QUICK_BLOCK -> context.getString(R.string.block_quick_message)
            BlockReason.SCHEDULE -> context.getString(R.string.block_schedule_message)
            BlockReason.BLOCKED -> context.getString(R.string.block_always_message)
            BlockReason.WEBSITE -> context.getString(R.string.block_site_message)
        }
        val untilText = until?.let { context.getString(R.string.block_until, DateFormat.getTimeFormat(context).format(Date(it))) }
        val title = if (reason == BlockReason.WEBSITE) context.getString(R.string.block_site_title)
        else context.getString(R.string.block_title, label)
        val root = buildScreen(pkg, title, listOfNotNull(message, untilText).joinToString("\n"))
        val buttons = root.findViewWithTag<LinearLayout>(TAG_BUTTONS)
        if (reason == BlockReason.WEBSITE) {
            buttons.addView(primaryButton(context.getString(R.string.go_back)) { onGoBack() })
        } else {
            buttons.addView(primaryButton(context.getString(R.string.go_home)) { onGoHome() })
        }
        if (canExtend) {
            buttons.addView(
                ghostButton(context.getString(R.string.five_more_minutes)) {
                    dismiss() // a double tap must count once: the screen is gone after the first
                    onExtend()
                },
            )
        }
        attach(pkg, root, Kind.BLOCK)
        if (!canDraw()) Notifications.blocked(context, title, message)
    }

    fun showPause(pkg: String, label: String, seconds: Int, opensToday: Int, onContinue: () -> Unit) {
        val root = buildScreen(
            pkg,
            context.getString(R.string.pause_title),
            context.resources.getQuantityString(R.plurals.pause_opens, opensToday, label, opensToday),
        )
        val buttons = root.findViewWithTag<LinearLayout>(TAG_BUTTONS)
        buttons.addView(primaryButton(context.getString(R.string.pause_leave)) { onGoHome() })
        val open = ghostButton(context.getString(R.string.pause_wait, seconds)) {
            dismiss()
            onContinue()
        }.apply { isEnabled = false; alpha = 0.5f }
        buttons.addView(open)
        attach(pkg, root, Kind.PAUSE)
        timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(left: Long) {
                open.text = context.getString(R.string.pause_wait, ((left + 999) / 1000).toInt())
            }

            override fun onFinish() {
                open.text = context.getString(R.string.pause_continue)
                open.isEnabled = true
                open.alpha = 1f
            }
        }.start()
    }

    fun dismissIfNot(pkg: String) {
        if (target != null && target != pkg) dismiss()
    }

    fun dismissFor(pkg: String) {
        if (target == pkg) dismiss()
    }

    fun dismissPause() {
        if (kind == Kind.PAUSE) dismiss()
    }

    fun dismiss() {
        timer?.cancel()
        timer = null
        view?.let { v -> runCatching { windowManager?.removeView(v) } }
        view = null
        windowManager = null
        kind = null
        target = null
    }

    private fun canDraw() = accessibilityService() != null || Settings.canDrawOverlays(context)

    private fun attach(pkg: String, root: View, newKind: Kind) {
        dismiss()
        val service = accessibilityService()
        val (wm, type) = when {
            service != null -> service.getSystemService(WindowManager::class.java) to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            Settings.canDrawOverlays(context) -> context.getSystemService(WindowManager::class.java) to WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> return
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Not FLAG_SECURE: the screen shows only an app name, and users must be able to screenshot it for bug reports.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            wm.addView(root, params)
        } catch (_: RuntimeException) {
            return // window token gone (service dying) — fail open
        }
        view = root
        windowManager = wm
        kind = newKind
        target = pkg
    }

    private fun buildScreen(pkg: String, title: String, message: String): FrameLayout {
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) onGoHome()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(SCRIM)
            isFocusableInTouchMode = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()?.let { icon ->
            column.addView(
                ImageView(context).apply {
                    setImageDrawable(icon)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(20) },
            )
        }
        column.addView(text(title, 26f, TEXT, bold = true), wrap(bottom = 12))
        column.addView(text(message, 16f, TEXT_2, bold = false), wrap(bottom = 28))
        column.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                tag = TAG_BUTTONS
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        // Scrollable so the buttons stay reachable at 200% font size or in landscape.
        val content = FrameLayout(context).apply {
            addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        root.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.post { root.requestFocus() }
        return root
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun primaryButton(label: String, onClick: () -> Unit) = Button(context).apply {
        text = label
        isAllCaps = false
        setTextColor(ON_ACCENT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(TEAL, VIOLET)).apply { cornerRadius = dp(28).toFloat() }
        minHeight = dp(52)
        setOnClickListener { if (view != null) onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
    }

    private fun ghostButton(label: String, onClick: () -> Unit) = Button(context).apply {
        text = label
        isAllCaps = false
        setTextColor(ACCENT_TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setBackgroundColor(Color.TRANSPARENT)
        minHeight = dp(48)
        setOnClickListener { if (view != null) onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun wrap(bottom: Int) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG_BUTTONS = "buttons"
        // Mirrors design/tokens.json (dark theme).
        // Fully opaque: text behind the screen must never bleed through (readability, contrast).
        val SCRIM = Color.parseColor("#FF0B0E1A")
        val TEXT = Color.parseColor("#F2F3F8")
        val TEXT_2 = Color.parseColor("#B4B8CC")
        val TEAL = Color.parseColor("#2DD4BF")
        val VIOLET = Color.parseColor("#8B7CF6")
        val ON_ACCENT = Color.parseColor("#0B0E1A")
        val ACCENT_TEXT = Color.parseColor("#A79BFA")
    }
}
