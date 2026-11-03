package io.github.aasifashraf.unscroll.engine.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * A circle that slowly grows (breathe in) and shrinks (breathe out) for the pause screen.
 * Static when the system's "remove animations" setting is on. Decorative: not announced.
 */
class BreathingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    /** Called with true when a breath in starts and false when a breath out starts. */
    var onPhase: ((inhale: Boolean) -> Unit)? = null

    private var scale = STILL_SCALE
    private var animator: ValueAnimator? = null
    private var inhale = true
    private val density = resources.displayMetrics.density

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = RING
    }
    private val orb = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Whether the system lets apps animate (off with "remove animations" and in some Battery Saver modes). */
    val animates: Boolean
        get() = ValueAnimator.areAnimatorsEnabled()

    /** The system animation speed setting (1 = normal); animations last this many times longer. */
    private fun durationScale(): Float = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ValueAnimator.getDurationScale()
    } else {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animates) return
        animator = ValueAnimator.ofFloat(MIN_SCALE, 1f).apply {
            // One breath stays about 4 s even when the system speeds animations up or slows them down.
            duration = (BREATH_MILLIS / durationScale().coerceAtLeast(MIN_SCALE_SETTING)).toLong()
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                scale = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: Animator) {
                    inhale = !inhale
                    onPhase?.invoke(inhale)
                }
            })
            start()
        }
        onPhase?.invoke(true)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        orb.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), ORB_START, ORB_END, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) - ring.strokeWidth
        canvas.drawCircle(cx, cy, radius, ring)
        canvas.drawCircle(cx, cy, radius * scale, orb)
    }

    private companion object {
        const val BREATH_MILLIS = 4_000L
        const val MIN_SCALE = 0.6f
        const val STILL_SCALE = 0.8f
        const val MIN_SCALE_SETTING = 0.1f

        // Mirrors design/tokens.json (teal, violet), dimmed so light text stays readable on top.
        const val RING = 0x802DD4BF.toInt()
        const val ORB_START = 0x662DD4BF
        const val ORB_END = 0x668B7CF6
    }
}
