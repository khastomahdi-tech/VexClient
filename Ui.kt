package com.vexclient.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import java.util.Locale

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object C {
    val BG_TOP = 0xFF0B1220.toInt()
    val BG_MID = 0xFF0E1A2C.toInt()
    val BG_BOTTOM = 0xFF09101A.toInt()
    val CARD = 0xFF131D2B.toInt()
    val CARD_SEL = 0xFF16283A.toInt()
    val STROKE = 0xFF22304A.toInt()
    val WHITE = 0xFFF2F6FA.toInt()
    val GREY = 0xFF8A9AB0.toInt()
    val TEAL = 0xFF36C0A8.toInt()
    val TEAL_SOFT = 0x2236C0A8
    val TEAL_DARK = 0xFF0F6F66.toInt()
    val DANGER = 0xFFFF6B6B.toInt()

    fun backgroundGradient(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG_TOP, BG_MID, BG_BOTTOM))
}

fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

fun Context.tv(value: String, sp: Float, color: Int, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = value
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.rrect(color: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable {
    val density = resources.displayMetrics.density
    val strokePx = dp(1)
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * density
        if (stroke != null) setStroke(strokePx, stroke)
    }
}

object Format {
    fun speed(bytesPerSecond: Double): String = when {
        bytesPerSecond >= 1048576.0 -> String.format(Locale.US, "%.2f MB/s", bytesPerSecond / 1048576.0)
        bytesPerSecond >= 1024.0 -> String.format(Locale.US, "%.1f KB/s", bytesPerSecond / 1024.0)
        else -> String.format(Locale.US, "%.0f B/s", bytesPerSecond)
    }

    fun duration(ms: Long): String {
        val total = ms / 1000
        return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }
}
