package dev.monthly.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import java.time.LocalDate
import java.time.YearMonth

/** 앱 메인 화면의 달력. 누르면 그날만 보기, 길게 누르면 그날로 일정 추가. */
class MonthView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var onDayClick: ((LocalDate) -> Unit)? = null
    var onDayLongPress: ((LocalDate) -> Unit)? = null

    private var ym: YearMonth = YearMonth.now()
    private var selected: LocalDate? = null
    private val marks = HashMap<LocalDate, MutableList<Int>>()
    private val emojis = HashMap<LocalDate, String>()

    private val d = resources.displayMetrics.density
    private val headerH = 26 * d
    private val cellH = 48 * d
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cPrimary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val cOnPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary)
    private val cSelected = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer)
    private val cText = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    private val cSub = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val cMuted = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
    private val cSun = ctx.getColor(R.color.sunday)
    private val cSat = ctx.getColor(R.color.saturday)

    private val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            dateAt(e.x, e.y)?.let { onDayClick?.invoke(it) }
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            dateAt(e.x, e.y)?.let {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onDayLongPress?.invoke(it)
            }
        }
    })

    fun setData(month: YearMonth, items: List<Item>, theme: Theme, sel: LocalDate?) {
        ym = month
        selected = sel
        marks.clear()
        emojis.clear()
        items.mapNotNull { item -> Store.occurrenceIn(item, ym)?.let { Pair(item, it) } }
            .filter { !(it.first.type == ItemType.TODO && it.first.done) }
            .sortedWith(compareBy<Pair<Item, LocalDate>>({ it.first.type != ItemType.DDAY }, { it.first.id }))
            .forEach { (item, date) ->
                val list = marks.getOrPut(date) { mutableListOf() }
                if (list.size < 3) list.add(Palette.itemColor(item, theme))
                if (item.emoji.isNotBlank() && !emojis.containsKey(date)) emojis[date] = item.emoji.trim()
            }
        requestLayout()
        invalidate()
    }

    private fun weeks(): Int {
        val lead = ym.atDay(1).dayOfWeek.value % 7
        return (lead + ym.lengthOfMonth() + 6) / 7
    }

    private fun start(): LocalDate {
        val first = ym.atDay(1)
        return first.minusDays((first.dayOfWeek.value % 7).toLong())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = paddingTop + paddingBottom + headerH + weeks() * cellH
        setMeasuredDimension(w, h.toInt())
    }

    private fun dateAt(x: Float, y: Float): LocalDate? {
        val colW = (width - paddingLeft - paddingRight) / 7f
        val gy = y - paddingTop - headerH
        if (gy < 0 || colW <= 0) return null
        val col = ((x - paddingLeft) / colW).toInt().coerceIn(0, 6)
        val row = (gy / cellH).toInt()
        if (row >= weeks()) return null
        return start().plusDays((row * 7 + col).toLong())
    }

    private fun centerText(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, bold: Boolean = false) {
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        paint.color = color
        paint.isFakeBoldText = bold
        val fm = paint.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, paint)
        paint.isFakeBoldText = false
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val left = paddingLeft.toFloat()
        val colW = (width - paddingLeft - paddingRight) / 7f
        val top = paddingTop.toFloat()
        val labels = arrayOf("일", "월", "화", "수", "목", "금", "토")
        for (i in 0 until 7) {
            val col = if (i == 0) cSun else if (i == 6) cSat else cSub
            centerText(c, labels[i], left + (i + 0.5f) * colW, top + headerH / 2f, 12 * d, col)
        }
        val today = LocalDate.now()
        val s = start()
        for (r in 0 until weeks()) {
            for (k in 0 until 7) {
                val date = s.plusDays((r * 7 + k).toLong())
                val inMonth = YearMonth.from(date) == ym
                val cx = left + (k + 0.5f) * colW
                val cy = top + headerH + r * cellH + 18 * d
                var color = when {
                    !inMonth -> cMuted
                    k == 0 -> cSun
                    k == 6 -> cSat
                    else -> cText
                }
                if (date == selected && date != today) {
                    paint.color = cSelected
                    c.drawCircle(cx, cy, 16 * d, paint)
                }
                if (date == today) {
                    paint.color = cPrimary
                    c.drawCircle(cx, cy, 16 * d, paint)
                    color = cOnPrimary
                }
                centerText(c, date.dayOfMonth.toString(), cx, cy, 14 * d, color, bold = date == today)
                if (!inMonth) continue
                val em = emojis[date]
                val dotY = cy + 22 * d
                if (em != null) {
                    centerText(c, em, cx, dotY, 10 * d, cText)
                } else {
                    val dots = marks[date] ?: continue
                    val gap = 7 * d
                    var x = cx - (dots.size - 1) * gap / 2f
                    for (dc in dots) {
                        paint.color = dc
                        c.drawCircle(x, dotY, 2.6f * d, paint)
                        x += gap
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = detector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return handled || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()
}
