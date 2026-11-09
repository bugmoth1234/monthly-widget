package dev.monthly.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 고른 글꼴로 위젯 글자를 그림으로 그립니다.
 * 달력(문구·요일·날짜)은 한 장, 목록은 줄마다 한 장씩 따로 그려서
 * 위젯에서 줄마다 누르는 기능(체크, 사이트 열기)을 살립니다.
 * 기본 레이아웃과 같은 크기(dp)를 쓰도록 맞춰 두었습니다. u = 1dp의 픽셀 수.
 */
class BodyPainter(
    private val theme: Theme,
    private val ink: Ink,
    tf: Typeface,
    private val w: Int,
    private val u: Float,
) {
    private val k = theme.bodyFontScale.coerceIn(70, 150) / 100f
    private val p = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).also { it.typeface = tf }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private lateinit var c: Canvas

    val rowH: Float get() = 22 * u * k
    /** 줄 앞 ○ 자리 너비(dp) — 위젯의 눌림 영역과 맞춤 */
    val badgeDp: Float get() = 40 * maxOf(1f, k)

    private fun canvas(h: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        c = Canvas(bmp)
        return bmp
    }

    private fun text(s: String, x: Float, cy: Float, size: Float, color: Int, align: Paint.Align, bold: Boolean = false) {
        p.textAlign = align
        p.textSize = size
        p.color = color
        p.isFakeBoldText = bold
        val fm = p.fontMetrics
        c.drawText(s, x, cy - (fm.ascent + fm.descent) / 2f, p)
        p.isFakeBoldText = false
    }

    private fun measure(s: String, size: Float, bold: Boolean = false): Float {
        p.textSize = size
        p.isFakeBoldText = bold
        val m = p.measureText(s)
        p.isFakeBoldText = false
        return m
    }

    private fun fit(s: String, size: Float, width: Float, bold: Boolean = false): String {
        p.textSize = size
        p.isFakeBoldText = bold
        val r = Fonts.ellipsize(s, p, width)
        p.isFakeBoldText = false
        return r
    }

    /** 문구 + 요일 + 날짜 칸 */
    fun calendar(items: List<Item>, ym: YearMonth, today: LocalDate): Bitmap {
        val first = ym.atDay(1)
        val lead = first.dayOfWeek.value % 7
        val start = first.minusDays(lead.toLong())
        val weeks = (lead + ym.lengthOfMonth() + 6) / 7
        val cellH = 37 * u
        val captionH = if (theme.caption.isNotBlank()) 18 * u else 0f
        val panel = theme.calBgColor != 0
        val pad = if (panel) 6 * u else 0f
        val bmp = canvas(captionH + 27 * u + weeks * cellH + pad * 1.5f)
        val colW = w / 7f
        var y = 0f

        if (theme.caption.isNotBlank()) {
            text(fit(theme.caption, 12 * u * k, w.toFloat()), w / 2f, y + 9 * u, 12 * u * k, ink.secondary, Paint.Align.CENTER)
            y += captionH
        }

        y += 4 * u
        if (panel) {
            fill.color = (theme.calBgColor and 0x00FFFFFF) or ((theme.calBgAlpha.coerceIn(0, 100) * 255 / 100) shl 24)
            c.drawRoundRect(RectF(0f, y, w.toFloat(), y + 23 * u + weeks * cellH + pad * 1.5f), 12 * u, 12 * u, fill)
            y += pad
        }
        val labels = Fmt.weekLabels(theme)
        for (i in 0 until 7) {
            val color = if (i == 0) ink.sun else if (i == 6) ink.sat else ink.secondary
            text(labels[i], (i + 0.5f) * colW, y + 8 * u, (if (theme.weekdayEn) 9.5f else 11f) * u * k, color, Paint.Align.CENTER)
        }
        y += 23 * u // 요일 아래 간격을 넉넉하게

        val marks = HashMap<LocalDate, Entry>()
        items.mapNotNull { item -> Store.occurrenceIn(item, ym)?.let { Entry(item, it) } }
            .filter { !(it.item.type == ItemType.TODO && it.item.done) }
            .sortedWith(compareBy<Entry>({ it.item.type != ItemType.DDAY }, { it.item.id }))
            .forEach { if (!marks.containsKey(it.date)) marks[it.date] = it }

        for (wk in 0 until weeks) {
            for (d in 0 until 7) {
                val date = start.plusDays((wk * 7 + d).toLong())
                val inMonth = YearMonth.from(date) == ym
                val cx = (d + 0.5f) * colW
                val cy = y + 14 * u
                val color = if (date == today) {
                    fill.color = Palette.accentColor(theme)
                    c.drawCircle(cx, cy, 12 * u, fill)
                    Palette.onColor(Palette.accentColor(theme), theme)
                } else when {
                    !inMonth -> ink.muted
                    d == 0 -> ink.sun
                    d == 6 -> ink.sat
                    else -> ink.primary
                }
                text(date.dayOfMonth.toString(), cx, cy, 12 * u * k, color, Paint.Align.CENTER)
                val mark = if (inMonth) marks[date] else null
                if (mark != null) {
                    val dotY = y + 31.5f * u
                    if (mark.item.emoji.isNotBlank()) {
                        text(mark.item.emoji.trim(), cx, dotY, 9 * u, ink.primary, Paint.Align.CENTER)
                    } else {
                        fill.color = Palette.itemColor(mark.item, theme)
                        c.drawCircle(cx, dotY, 2.5f * u, fill)
                    }
                }
            }
            y += cellH
        }
        return bmp
    }

    fun projectRow(pj: Project, items: List<Item>, today: LocalDate): Bitmap {
        val bmp = canvas(rowH)
        val mid = rowH / 2f
        val col = Palette.color(pj.color)
        val pct = Store.percent(pj, items)
        val left = ChronoUnit.DAYS.between(today, pj.deadline)
        text(pj.emoji.trim().ifEmpty { "📁" }, 9 * u, mid, 14 * u * k, ink.primary, Paint.Align.CENTER)
        val dd = Fmt.dday(pj.deadline, today)
        text(dd, w.toFloat(), mid, 12 * u * k, if (left in 0..7) ink.sun else Palette.readable(col, ink), Paint.Align.RIGHT, bold = true)
        val ddW = maxOf(measure(dd, 12 * u * k, true), 40 * u)
        val pctX = w - ddW - 6 * u
        text("$pct%", pctX, mid, 11 * u * k, ink.secondary, Paint.Align.RIGHT)
        val barR = pctX - 32 * u
        val barL = barR - 54 * u
        fill.color = ink.divider
        c.drawRoundRect(RectF(barL, mid - 3 * u, barR, mid + 3 * u), 3 * u, 3 * u, fill)
        if (pct > 0) {
            fill.color = col
            c.drawRoundRect(RectF(barL, mid - 3 * u, barL + maxOf(54 * u * pct / 100f, 6 * u), mid + 3 * u), 3 * u, 3 * u, fill)
        }
        val nx = 22 * u
        text(fit(pj.name, 13 * u * k, barL - nx - 6 * u, true), nx, mid, 13 * u * k, ink.primary, Paint.Align.LEFT, bold = true)
        return bmp
    }

    fun highlight(hl: Entry, today: LocalDate): Bitmap {
        val cardH = 44 * u * k
        val bmp = canvas(cardH)
        val col = Palette.itemColor(hl.item, theme)
        fill.color = (col and 0x00FFFFFF) or (46 shl 24)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), cardH), 12 * u, 12 * u, fill)
        val mid = cardH / 2f
        text(hl.item.emoji.trim().ifEmpty { "★" }, 22 * u, mid, 20 * u * k, Palette.readable(col, ink), Paint.Align.CENTER)
        val dd = Fmt.dday(hl.date, today)
        text(dd, w - 10 * u, mid, 20 * u * k, Palette.readable(col, ink), Paint.Align.RIGHT, bold = true)
        val ddW = measure(dd, 20 * u * k, true)
        val tx = 44 * u
        val avail = w - tx - ddW - 18 * u
        text(fit(hl.item.title, 13 * u * k, avail, true), tx, mid - 8 * u * k, 13 * u * k, ink.primary, Paint.Align.LEFT, bold = true)
        val sub = Fmt.monthDay(hl.date) + (if (hl.item.yearly) " · 매년" else "")
        text(fit(sub, 11 * u * k, avail), tx, mid + 8 * u * k, 11 * u * k, ink.secondary, Paint.Align.LEFT)
        return bmp
    }

    fun itemRow(e: Entry, today: LocalDate): Bitmap {
        val bmp = canvas(rowH)
        val item = e.item
        val ci = Palette.itemColor(item, theme)
        val faded = item.type == ItemType.TODO && item.done
        val mid = rowH / 2f
        val badgeW = badgeDp * u

        if (item.type == ItemType.DDAY) {
            val t = Fmt.dday(e.date, today)
            val pw = maxOf(badgeW, measure(t, 11 * u * k) + 8 * u)
            fill.color = ci
            c.drawRoundRect(RectF(0f, mid - 8 * u * k, pw, mid + 8 * u * k), 8 * u, 8 * u, fill)
            text(t, pw / 2f, mid, 11 * u * k, Palette.onColor(ci, theme), Paint.Align.CENTER)
        } else {
            text(if (item.done) "✓" else "○", badgeW / 2f, mid, 12 * u * k, if (item.done) ink.muted else ci, Paint.Align.CENTER)
        }

        var x = badgeW + 6 * u
        val dateText = if (e.overdue) "밀림 " + Fmt.shortDate(e.date) else Fmt.shortDate(e.date)
        text(dateText, x, mid, 12 * u * k, if (faded) ink.muted else if (e.overdue) ink.sun else ink.secondary, Paint.Align.LEFT)
        x += measure(dateText, 12 * u * k) + 8 * u
        val title = fit(Fmt.titleWithEmoji(item), 13 * u * k, w - x)
        text(title, x, mid, 13 * u * k, if (faded) ink.muted else ink.primary, Paint.Align.LEFT)
        return bmp
    }

    fun textRow(s: String): Bitmap {
        val bmp = canvas(rowH)
        text(fit(s, 12 * u * k, w.toFloat()), 0f, rowH / 2f, 12 * u * k, ink.secondary, Paint.Align.LEFT)
        return bmp
    }
}
