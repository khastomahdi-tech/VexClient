package com.vexclient.app

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class PowerButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var on: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            animateTo(if (value) 1f else 0f)
        }

    private val density = resources.displayMetrics.density
    private var glow = 0f
    private var anim: ValueAnimator? = null
    private val argb = ArgbEvaluator()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
    }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4 * density
        strokeCap = Paint.Cap.ROUND
    }
    private val arcRect = RectF()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val size = (200 * density).toInt()
        val w = resolveSize(size, widthSpec)
        val h = resolveSize(size, heightSpec)
        val s = minOf(w, h)
        setMeasuredDimension(s, s)
    }

    private fun animateTo(target: Float) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(glow, target).apply {
            duration = 350L
            addUpdateListener {
                glow = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun mix(from: Int, to: Int): Int = argb.evaluate(glow, from, to) as Int

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 12 * density

        if (glow > 0f) {
            fill.shader = RadialGradient(
                cx, cy, r * 1.4f,
                intArrayOf(0x6036C0A8, 0x0036C0A8),
                floatArrayOf(0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r * 1.4f, fill)
            fill.shader = null
        }

        fill.shader = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            mix(0xFF1B2638.toInt(), 0xFF1FB5A0.toInt()),
            mix(0xFF111A28.toInt(), 0xFF0E7C8C.toInt()),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null

        ring.color = mix(0xFF2A3A52.toInt(), 0xFF5EE6CF.toInt())
        canvas.drawCircle(cx, cy, r, ring)

        icon.color = mix(0xFF8A9AB0.toInt(), 0xFFFFFFFF.toInt())
        val ir = r * 0.34f
        arcRect.set(cx - ir, cy - ir, cx + ir, cy + ir)
        canvas.drawArc(arcRect, -60f, 300f, false, icon)
        canvas.drawLine(cx, cy - ir * 1.3f, cx, cy - ir * 0.05f, icon)
    }
}
