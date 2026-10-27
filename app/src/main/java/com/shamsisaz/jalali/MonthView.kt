package com.shamsisaz.jalali

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors

/** شبکهٔ ماه شمسی (شنبه سمت راست) با جمعه/تعطیلات قرمز، امروز و روز انتخاب‌شده مشخص */
class MonthView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var onDaySelected: ((JalaliConverter.JalaliDate) -> Unit)? = null

    var year = 1400; private set
    var month = 1; private set
    var selected: JalaliConverter.JalaliDate? = null

    private var today = JalaliConverter.today()
    private val density = resources.displayMetrics.density
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val headerH get() = 30 * density
    private val headerLetters = arrayOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    private fun color(attr: Int, fallback: Int) = MaterialColors.getColor(this, attr, fallback)

    fun show(y: Int, m: Int) {
        year = y; month = m; today = JalaliConverter.today(); requestLayout(); invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        val cell = width / 7f
        val rows = rowCount()
        setMeasuredDimension(width, (headerH + cell * 0.9f * rows).toInt())
    }

    private fun startCol(): Int = JalaliConverter.weekDayOfJdn(JalaliConverter.jalaliToJdn(year, month, 1))
    private fun rowCount(): Int = (startCol() + JalaliConverter.monthLength(year, month) + 6) / 7

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val cw = w / 7f
        val ch = cw * 0.9f
        val onSurface = color(com.google.android.material.R.attr.colorOnSurface, 0xFF1A1A1A.toInt())
        val muted = color(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF6B7280.toInt())
        val primary = color(androidx.appcompat.R.attr.colorPrimary, 0xFF0F7B4A.toInt())
        val onPrimary = color(com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF.toInt())
        val red = 0xFFD32F2F.toInt()

        text.textSize = 12 * density
        text.isFakeBoldText = true
        for (col in 0 until 7) {
            text.color = if (col == 6) red else muted
            c.drawText(headerLetters[col], w - (col + 0.5f) * cw, headerH * 0.7f, text)
        }

        val len = JalaliConverter.monthLength(year, month)
        val start = startCol()
        text.textSize = 15 * density
        for (day in 1..len) {
            val idx = start + day - 1
            val col = idx % 7
            val row = idx / 7
            val cx = w - (col + 0.5f) * cw
            val cy = headerH + row * ch + ch / 2f
            val d = JalaliConverter.JalaliDate(year, month, day)
            val isToday = d == today
            val isSel = d == selected
            val official = Occasions.forDay(d).any { it.holiday }
            val off = official || col == 6

            if (isToday || isSel) {
                fill.color = if (isToday) primary else (primary and 0x00FFFFFF) or 0x33000000
                rect.set(cx - cw * 0.42f, cy - ch * 0.42f, cx + cw * 0.42f, cy + ch * 0.42f)
                c.drawRoundRect(rect, 12 * density, 12 * density, fill)
            }
            text.isFakeBoldText = isToday
            text.color = when { isToday -> onPrimary; off -> red; else -> onSurface }
            c.drawText(JalaliConverter.fa(day), cx, cy + 5 * density, text)

            if (Occasions.forDay(d).isNotEmpty() && !isToday) {
                fill.color = if (official) red else primary
                c.drawCircle(cx, cy + ch * 0.32f, 2 * density, fill)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            val cw = width / 7f
            val ch = cw * 0.9f
            val col = 6 - (e.x / cw).toInt().coerceIn(0, 6)
            val row = ((e.y - headerH) / ch).toInt()
            val day = row * 7 + col - startCol() + 1
            if (e.y >= headerH && day in 1..JalaliConverter.monthLength(year, month)) {
                val d = JalaliConverter.JalaliDate(year, month, day)
                selected = d
                invalidate()
                onDaySelected?.invoke(d)
                performClick()
            }
        }
        return e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_UP
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
