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
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
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
    /** "I don't need it" or Back on the pause screen for the given app. */
    private val onDeclinePause: (String) -> Unit,
) {
    private enum class Kind { BLOCK, PAUSE }

    private var view: View? = null
    private var windowManager: WindowManager? = null
    private var kind: Kind? = null
    private var timer: CountDownTimer? = null

    @Volatile
    private var target: String? = null

    fun isShowingFor(pkg: String) = target == pkg

    /** A block or pause screen is up (for any app). Any thread. */
    fun isShowing() = target != null

    /** [groupName]: the group whose shared time is used up, for [BlockReason.GROUP_LIMIT]. */
    fun showBlock(
        pkg: String,
        label: String,
        reason: BlockReason,
        until: Long?,
        canExtend: Boolean,
        groupName: String? = null,
        onExtend: () -> Unit,
    ) {
        val message = when (reason) {
            BlockReason.DAILY_LIMIT -> context.getString(R.string.block_limit_message)
            BlockReason.GROUP_LIMIT -> groupName?.let { context.getString(R.string.block_group_message, it) }
                ?: context.getString(R.string.block_limit_message)
            BlockReason.OPEN_LIMIT -> context.getString(R.string.block_opens_message)
            BlockReason.FOCUS -> context.getString(R.string.block_focus_message)
            BlockReason.QUICK_BLOCK -> context.getString(R.string.block_quick_message)
            BlockReason.SCHEDULE -> context.getString(R.string.block_schedule_message)
            BlockReason.BLOCKED -> context.getString(R.string.block_always_message)
            BlockReason.SESSION_LIMIT -> context.getString(R.string.block_session_message, label)
            BlockReason.WEBSITE -> context.getString(R.string.block_site_message)
        }
        val untilText = until?.let { DateFormat.getTimeFormat(context).format(Date(it)) }?.let {
            context.getString(if (reason == BlockReason.SESSION_LIMIT) R.string.block_session_until else R.string.block_until, it)
        }
        val title = if (reason == BlockReason.WEBSITE) context.getString(R.string.block_site_title)
        else context.getString(R.string.block_title, label)
        val root = buildScreen(pkg, title, listOfNotNull(message, untilText).joinToString("\n"), onBack = onGoHome)
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

    /**
     * The mindful pause: a breathing circle, how often the app was opened today, an optional
     * "What are you here for?" prompt, and Continue once [seconds] have passed (and, with
     * [askIntention], a reason is picked). The reason is only a nudge: it is never stored.
     */
    fun showPause(pkg: String, label: String, seconds: Int, opensToday: Int, askIntention: Boolean, onContinue: () -> Unit) {
        var waited = false
        var reasonPicked = !askIntention
        lateinit var open: Button
        fun refreshContinue() {
            val ready = waited && reasonPicked
            open.isEnabled = ready
            open.alpha = if (ready) 1f else 0.5f
            if (waited) {
                open.text = context.getString(if (ready) R.string.pause_continue else R.string.pause_pick_reason)
                // Announced once when the wait is over (and when a reason is picked), not every second.
                open.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                open.contentDescription = open.text
            }
        }
        val intention = if (askIntention) {
            intentionPrompt {
                reasonPicked = true
                refreshContinue()
            }
        } else {
            null
        }
        val root = buildScreen(
            pkg,
            context.getString(R.string.pause_title),
            context.resources.getQuantityString(R.plurals.pause_opens, opensToday, label, opensToday),
            onBack = { onDeclinePause(pkg) },
            beforeTitle = breathing(),
            afterMessage = intention,
            smallIcon = true,
        )
        val buttons = root.findViewWithTag<LinearLayout>(TAG_BUTTONS)
        buttons.addView(primaryButton(context.getString(R.string.pause_leave)) { onDeclinePause(pkg) })
        open = ghostButton(context.getString(R.string.pause_wait, seconds)) {
            dismiss()
            onContinue()
        }
        // A steady label during the countdown, so screen readers don't re-read the seconds.
        open.contentDescription = context.getString(R.string.pause_wait_description)
        buttons.addView(open)
        refreshContinue()
        attach(pkg, root, Kind.PAUSE)
        timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(left: Long) {
                open.text = context.getString(R.string.pause_wait, ((left + 999) / 1000).toInt())
            }

            override fun onFinish() {
                waited = true
                refreshContinue()
            }
        }.start()
    }

    /** The breathing circle with its "Breathe in" / "Breathe out" words (decorative, not announced). */
    private fun breathing(): View {
        val circle = BreathingView(context)
        val words = text(
            context.getString(if (circle.animates) R.string.breathe_in else R.string.breathe_slowly),
            16f,
            TEXT,
            bold = true,
        )
        circle.onPhase = { inhale -> words.text = context.getString(if (inhale) R.string.breathe_in else R.string.breathe_out) }
        return FrameLayout(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(circle, FrameLayout.LayoutParams(dp(BREATH_SIZE), dp(BREATH_SIZE), Gravity.CENTER))
            addView(
                words,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
            )
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(BREATH_SIZE)).apply { bottomMargin = dp(20) }
        }
    }

    /** "What are you here for?" with one-choice chips; [onPicked] runs on every pick. */
    private fun intentionPrompt(onPicked: () -> Unit): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = wrap(bottom = 20)
        }
        column.addView(text(context.getString(R.string.intention_title), 14f, TEXT_2, bold = false), wrap(bottom = 8))
        val chips = listOf(R.string.intention_message, R.string.intention_posting, R.string.intention_bored, R.string.intention_other)
            .map { chip(context.getString(it)) }
        chips.forEach { c ->
            c.setOnClickListener {
                if (view == null) return@setOnClickListener
                chips.forEach { other -> styleChip(other, other === c) }
                onPicked()
            }
        }
        chips.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, c ->
                row.addView(
                    c,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (i == 0) marginEnd = dp(8)
                        bottomMargin = dp(8)
                    },
                )
            }
            column.addView(row, wrap(bottom = 0))
        }
        return column
    }

    private fun chip(label: String) = Button(context).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        minHeight = dp(48)
        // Read out as one choice of several: "A message, radio button, not checked".
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = RadioButton::class.java.name
                info.isCheckable = true
                info.isChecked = host.isSelected
            }
        }
        styleChip(this, selected = false)
    }

    private fun styleChip(chip: Button, selected: Boolean) {
        chip.isSelected = selected
        chip.setTextColor(if (selected) ON_ACCENT else TEXT)
        chip.background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(if (selected) TEAL else SURFACE_RAISED)
            if (!selected) setStroke(dp(1), BORDER_STRONG)
        }
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

    /** Whether a block or pause screen can be shown at all (else only a notification). Any thread. */
    fun canDraw() = accessibilityService() != null || Settings.canDrawOverlays(context)

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
            // TalkBack reads the window (pane) title when the screen appears.
            root.findViewWithTag<TextView>(TAG_TITLE)?.let { heading ->
                title = heading.text
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) root.accessibilityPaneTitle = heading.text
            }
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

    /**
     * The shared screen: app icon, optional [beforeTitle] view, title, message, optional
     * [afterMessage] view and a button column (tag [TAG_BUTTONS]). Back runs [onBack].
     */
    private fun buildScreen(
        pkg: String,
        title: String,
        message: String,
        onBack: () -> Unit,
        beforeTitle: View? = null,
        afterMessage: View? = null,
        smallIcon: Boolean = false,
    ): FrameLayout {
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP && view != null) onBack()
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
                LinearLayout.LayoutParams(dp(if (smallIcon) 48 else 64), dp(if (smallIcon) 48 else 64)).apply { bottomMargin = dp(20) },
            )
        }
        beforeTitle?.let { column.addView(it) }
        column.addView(
            text(title, 26f, TEXT, bold = true).apply {
                tag = TAG_TITLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
            },
            wrap(bottom = 12),
        )
        column.addView(text(message, 16f, TEXT_2, bold = false), wrap(bottom = if (afterMessage != null) 20 else 28))
        afterMessage?.let { column.addView(it) }
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
        const val TAG_TITLE = "title"
        const val BREATH_SIZE = 168
        // Mirrors design/tokens.json (dark theme).
        // Fully opaque: text behind the screen must never bleed through (readability, contrast).
        val SCRIM = Color.parseColor("#FF0B0E1A")
        val TEXT = Color.parseColor("#F2F3F8")
        val TEXT_2 = Color.parseColor("#B4B8CC")
        val TEAL = Color.parseColor("#2DD4BF")
        val VIOLET = Color.parseColor("#8B7CF6")
        val ON_ACCENT = Color.parseColor("#0B0E1A")
        val ACCENT_TEXT = Color.parseColor("#A79BFA")
        val SURFACE_RAISED = Color.parseColor("#1F2440")
        val BORDER_STRONG = Color.parseColor("#29FFFFFF")
    }
}
