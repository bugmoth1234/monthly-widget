package dev.monthly.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import java.time.LocalDate
import java.time.YearMonth

/**
 * 고른 글꼴로 위젯 본문(문구, 요일, 달력, 목록)을 한 장의 그림으로 그립니다.
 * 기본 위젯 레이아웃과 같은 크기(dp)를 쓰도록 맞춰 두었습니다. u = 1dp의 픽셀 수.
 */
object BodyPainter {

    fun draw(
        ctx: Context, theme: Theme, items: List<Item>, ym: YearMonth, today: LocalDate,
        ink: Ink, tf: Typeface, w: Int, h: Int, u: Float,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val k = theme.bodyFontScale.coerceIn(70, 150) / 100f
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        p.typeface = tf
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val colW = w / 7f
        var y = 0f

        fun centerText(text: String, cx: Float, cy: Float, size: Float, color: Int) {
            p.textAlign = Paint.Align.CENTER
            p.textSize = size
            p.color = color
            val fm = p.fontMetrics
            c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, p)
        }

        // 위젯 문구
        if (theme.caption.isNotBlank()) {
            p.textSize = 12 * u * k
            val t = Fonts.ellipsize(theme.caption, p, w.toFloat())
            centerText(t, w / 2f, y + 9 * u, 12 * u * k, ink.secondary)
            y += 18 * u
        }

        // 요일
        y += 4 * u
        val labels = Fmt.weekLabels(theme)
        for (i in 0 until 7) {
            val color = if (i == 0) ink.sun else if (i == 6) ink.sat else ink.secondary
            centerText(labels[i], (i + 0.5f) * colW, y + 8 * u, (if (theme.weekdayEn) 9.5f else 11f) * u * k, color)
        }
        y += 16 * u

        // 달력
        val marks = HashMap<LocalDate, Entry>()
        items.mapNotNull { item -> Store.occurrenceIn(item, ym)?.let { Entry(item, it) } }
            .filter { !(it.item.type == ItemType.TODO && it.item.done) }
            .sortedWith(compareBy<Entry>({ it.item.type != ItemType.DDAY }, { it.item.id }))
            .forEach { if (!marks.containsKey(it.date)) marks[it.date] = it }

        val first = ym.atDay(1)
        val lead = first.dayOfWeek.value % 7
        val start = first.minusDays(lead.toLong())
        val weeks = (lead + ym.lengthOfMonth() + 6) / 7
        val cellH = 37 * u
        for (wk in 0 until weeks) {
            for (d in 0 until 7) {
                val date = start.plusDays((wk * 7 + d).toLong())
                val inMonth = YearMonth.from(date) == ym
                val cx = (d + 0.5f) * colW
                val cy = y + 14 * u
                val color: Int
                if (date == today) {
                    fill.color = Palette.accentColor(theme)
                    c.drawCircle(cx, cy, 12 * u, fill)
                    color = Color.WHITE
                } else {
                    color = when {
                        !inMonth -> ink.muted
                        d == 0 -> ink.sun
                        d == 6 -> ink.sat
                        else -> ink.primary
                    }
                }
                centerText(date.dayOfMonth.toString(), cx, cy, 12 * u * k, color)
                val mark = if (inMonth) marks[date] else null
                if (mark != null) {
                    val dotY = y + 31.5f * u
                    if (mark.item.emoji.isNotBlank()) {
                        centerText(mark.item.emoji.trim(), cx, dotY, 9 * u, ink.primary)
                    } else {
                        fill.color = Palette.itemColor(mark.item, theme)
                        c.drawCircle(cx, dotY, 2.5f * u, fill)
                    }
                }
            }
            y += cellH
        }

        // 구분선
        y += 6 * u
        fill.color = ink.divider
        c.drawRect(0f, y, w.toFloat(), y + 1 * u, fill)
        y += 7 * u

        // 목록
        val plan = WidgetRenderer.listPlan(ctx, items, ym, today, theme)
        val entries = plan.rest
        val rowH = 21 * u * k

        // 프로젝트 줄
        for (pj in plan.projects) {
            if (y + rowH > h) break
            val col = Palette.color(pj.color)
            val pct = Store.percent(pj, items)
            val left = java.time.temporal.ChronoUnit.DAYS.between(today, pj.deadline)
            val mid = y + rowH / 2f
            centerText(pj.emoji.trim().ifEmpty { "📁" }, 9 * u, mid, 14 * u * k, ink.primary)
            p.textAlign = Paint.Align.RIGHT
            p.textSize = 12 * u * k
            p.isFakeBoldText = true
            p.color = if (left in 0..7) ink.sun else col
            var fm = p.fontMetrics
            val dd = Fmt.dday(pj.deadline, today)
            c.drawText(dd, w.toFloat(), mid - (fm.ascent + fm.descent) / 2f, p)
            val ddW = maxOf(p.measureText(dd), 40 * u)
            p.isFakeBoldText = false
            p.textSize = 11 * u * k
            p.color = ink.secondary
            fm = p.fontMetrics
            val pctX = w - ddW - 6 * u
            c.drawText("$pct%", pctX, mid - (fm.ascent + fm.descent) / 2f, p)
            val barR = pctX - 32 * u
            val barL = barR - 54 * u
            fill.color = ink.divider
            c.drawRoundRect(RectF(barL, mid - 3 * u, barR, mid + 3 * u), 3 * u, 3 * u, fill)
            if (pct > 0) {
                fill.color = col
                c.drawRoundRect(RectF(barL, mid - 3 * u, barL + maxOf(54 * u * pct / 100f, 6 * u), mid + 3 * u), 3 * u, 3 * u, fill)
            }
            p.textAlign = Paint.Align.LEFT
            p.textSize = 13 * u * k
            p.isFakeBoldText = true
            p.color = ink.primary
            fm = p.fontMetrics
            val nx = 22 * u
            c.drawText(Fonts.ellipsize(pj.name, p, barL - nx - 6 * u), nx, mid - (fm.ascent + fm.descent) / 2f, p)
            p.isFakeBoldText = false
            y += rowH
        }

        // 가장 가까운 디데이 강조 카드
        plan.highlight?.let { hl ->
            val col = Palette.itemColor(hl.item, theme)
            val cardH = 42 * u * k
            if (y + cardH <= h) {
                fill.color = (col and 0x00FFFFFF) or (46 shl 24)
                c.drawRoundRect(RectF(0f, y, w.toFloat(), y + cardH), 12 * u, 12 * u, fill)
                val mid = y + cardH / 2f
                centerText(hl.item.emoji.trim().ifEmpty { "★" }, 22 * u, mid, 20 * u * k, col)
                p.textAlign = Paint.Align.RIGHT
                p.textSize = 20 * u * k
                p.isFakeBoldText = true
                p.color = col
                var fm = p.fontMetrics
                val dd = Fmt.dday(hl.date, today)
                c.drawText(dd, w - 10 * u, mid - (fm.ascent + fm.descent) / 2f, p)
                val ddW = p.measureText(dd)
                p.textAlign = Paint.Align.LEFT
                val tx = 44 * u
                val avail = w - tx - ddW - 18 * u
                p.textSize = 13 * u * k
                p.color = ink.primary
                fm = p.fontMetrics
                c.drawText(Fonts.ellipsize(hl.item.title, p, avail), tx, mid - 7 * u * k - (fm.ascent + fm.descent) / 2f, p)
                p.isFakeBoldText = false
                p.textSize = 11 * u * k
                p.color = ink.secondary
                fm = p.fontMetrics
                val sub = Fmt.monthDay(hl.date) + (if (hl.item.yearly) " · 매년" else "")
                c.drawText(Fonts.ellipsize(sub, p, avail), tx, mid + 8 * u * k - (fm.ascent + fm.descent) / 2f, p)
                y += cardH + 4 * u
            }
        }

        p.textAlign = Paint.Align.LEFT
        if (entries.isEmpty() && plan.highlight == null && plan.projects.isEmpty()) {
            p.textSize = 12 * u * k
            p.color = ink.secondary
            val fm = p.fontMetrics
            c.drawText("일정이 없어요. 눌러서 추가해 보세요.", 0f, y + rowH / 2f - (fm.ascent + fm.descent) / 2f, p)
        }
        var shownCount = 0
        for (e in entries.take(plan.maxRows)) {
            if (y + rowH > h) break
            val item = e.item
            val ci = Palette.itemColor(item, theme)
            val faded = item.type == ItemType.TODO && item.done
            val mid = y + rowH / 2f
            val badgeW = 40 * u * maxOf(1f, k)

            if (item.type == ItemType.DDAY) {
                val text = Fmt.dday(e.date, today)
                p.textSize = 11 * u * k
                val pw = maxOf(badgeW, p.measureText(text) + 8 * u)
                fill.color = ci
                c.drawRoundRect(RectF(0f, mid - 8 * u * k, pw, mid + 8 * u * k), 8 * u, 8 * u, fill)
                centerText(text, pw / 2f, mid, 11 * u * k, Color.WHITE)
            } else {
                centerText(if (item.done) "✓" else "○", badgeW / 2f, mid, 11 * u * k, if (item.done) ink.muted else ci)
            }

            p.textAlign = Paint.Align.LEFT
            var x = badgeW + 6 * u
            p.textSize = 12 * u * k
            p.color = if (faded) ink.muted else if (e.overdue) ink.sun else ink.secondary
            var fm = p.fontMetrics
            val dateText = if (e.overdue) "밀림 " + Fmt.shortDate(e.date) else Fmt.shortDate(e.date)
            c.drawText(dateText, x, mid - (fm.ascent + fm.descent) / 2f, p)
            x += p.measureText(dateText) + 8 * u

            p.textSize = 13 * u * k
            p.color = if (faded) ink.muted else ink.primary
            fm = p.fontMetrics
            val title = Fonts.ellipsize(Fmt.titleWithEmoji(item), p, w - x)
            c.drawText(title, x, mid - (fm.ascent + fm.descent) / 2f, p)
            y += rowH
            shownCount++
        }
        val rest = entries.size - shownCount
        if (shownCount > 0 && rest > 0 && y + rowH <= h) {
            p.textSize = 12 * u * k
            p.color = ink.secondary
            val fm = p.fontMetrics
            c.drawText("외 ${rest}개 더 있음", 0f, y + rowH / 2f - (fm.ascent + fm.descent) / 2f, p)
        }
        return bmp
    }
}
