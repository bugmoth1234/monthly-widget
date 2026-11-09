package dev.monthly.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

object WidgetRenderer {
    const val MAX_LIST = 5
    const val HEADER_NORMAL_DP = 38
    const val HEADER_LARGE_DP = 66
    private val WEEKDAY_IDS = intArrayOf(R.id.wd0, R.id.wd1, R.id.wd2, R.id.wd3, R.id.wd4, R.id.wd5, R.id.wd6)

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, MonthWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val items = Store.load(ctx)
        val theme = Store.loadTheme(ctx)
        for (id in ids) updateOne(ctx, mgr, id, items, theme)
    }

    private fun updateOne(ctx: Context, mgr: AppWidgetManager, id: Int, items: List<Item>, theme: Theme) {
        val opts = mgr.getAppWidgetOptions(id)
        var wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        var hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (wDp <= 0 || hDp <= 0) {
            wDp = 300
            hDp = 320
        } else {
            Store.setWidgetSize(ctx, wDp, hDp)
        }
        // 그림이 너무 크면 런처가 거부하므로 점점 작게 다시 시도
        for (maxSide in intArrayOf(900, 600, 380)) {
            try {
                val rv = buildContent(ctx, items, theme)
                applyDecor(ctx, rv, theme, items, wDp, hDp, maxSide)
                mgr.updateAppWidget(id, rv)
                return
            } catch (e: Throwable) {
                // 다음 크기로 재시도
            }
        }
        try {
            val rv = buildContent(ctx, items, theme)
            rv.setViewVisibility(R.id.sticker_layer, View.GONE)
            mgr.updateAppWidget(id, rv)
        } catch (e: Throwable) {
        }
    }

    private fun applyDecor(ctx: Context, rv: RemoteViews, theme: Theme, items: List<Item>, wDp: Int, hDp: Int, maxSide: Int) {
        val d = ctx.resources.displayMetrics.density
        val w = wDp * d
        val h = hDp * d
        val scale = minOf(1f, maxSide / maxOf(w, h))
        val wp = (w * scale).toInt().coerceAtLeast(1)
        val hp = (h * scale).toInt().coerceAtLeast(1)
        rv.setImageViewBitmap(R.id.bg_layer, Decor.renderBackground(ctx, theme, wp, hp, d * scale))
        applyHeader(ctx, rv, theme, wp, scale)
        applyFonts(ctx, rv, theme, items, wp, hp, scale)
        val st = Decor.renderStickers(ctx, theme.stickers, wp, hp)
        if (st != null) {
            rv.setViewVisibility(R.id.sticker_layer, View.VISIBLE)
            rv.setImageViewBitmap(R.id.sticker_layer, st)
        } else {
            rv.setViewVisibility(R.id.sticker_layer, View.GONE)
        }
    }

    class ListPlan(val projects: List<Project>, val highlight: Entry?, val rest: List<Entry>, val maxRows: Int)

    /** 목록 구성: 프로젝트(최대 2) + 강조할 디데이 1개 + 나머지 일정. 위젯 공간에 맞게 줄 수 조절. */
    fun listPlan(ctx: Context, items: List<Item>, ym: YearMonth, today: LocalDate, theme: Theme): ListPlan {
        val projects = if (theme.showProjects) {
            Store.loadProjects(ctx)
                .sortedWith(compareBy<Project>({ it.deadline.isBefore(today) }, { it.deadline }))
                .take(2)
        } else {
            emptyList()
        }
        val hl = if (theme.highlight) Store.nearestDday(items, today) else null
        val all = Store.entries(items, ym, today, Store.carryOver(ctx))
        val rest = if (hl == null) all else all.filter { it.item.id != hl.item.id }
        val used = projects.size + (if (hl != null) 2 else 0)
        return ListPlan(projects, hl, rest, maxOf(1, MAX_LIST - used))
    }

    /** 위젯의 ○를 누르면 그 할 일을 완료/취소 */
    private fun togglePI(ctx: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, (id xor (id ushr 32)).toInt(),
            Intent(ctx, MonthWidgetProvider::class.java)
                .setAction(MonthWidgetProvider.ACTION_TOGGLE)
                .setData(android.net.Uri.parse("monthly://toggle/$id"))
                .putExtra(MonthWidgetProvider.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 프로젝트 줄을 누르면 앱의 프로젝트 화면 */
    private fun projectPI(ctx: Context, p: Project): PendingIntent {
        val intent = Intent(ctx, ProjectDetailActivity::class.java)
            .setData(android.net.Uri.parse("monthly://project/${p.id}"))
            .putExtra(ProjectDetailActivity.EXTRA_ID, p.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            ctx, 5000 + (p.id % 100000).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun displayMonth(ctx: Context): YearMonth =
        YearMonth.from(LocalDate.now()).plusMonths(Store.getOffset(ctx).toLong())

    /** 제목 배경 이미지. widthPx는 위젯 전체 너비, scale은 그림 축소 비율. */
    fun applyHeader(ctx: Context, rv: RemoteViews, theme: Theme, widthPx: Int, scale: Float) {
        val d = ctx.resources.displayMetrics.density
        val hDp = if (theme.headerLarge) HEADER_LARGE_DP else HEADER_NORMAL_DP
        val w = (widthPx - (24f + 2 * theme.bleed) * d * scale).toInt()
        val h = (hDp * d * scale).toInt()
        val bmp = Decor.renderHeader(ctx, theme, displayMonth(ctx), w, h, 12f * d * scale)
        if (bmp != null) rv.setImageViewBitmap(R.id.header_img, bmp)
    }

    /**
     * 글꼴 파일을 고른 경우: 제목 / 나머지 글자를 그 글꼴로 그린 그림으로 바꿔 끼웁니다.
     * widthPx, heightPx는 위젯 전체 크기(그림 축소 반영), scale은 축소 비율.
     */
    fun applyFonts(ctx: Context, rv: RemoteViews, theme: Theme, items: List<Item>, widthPx: Int, heightPx: Int, scale: Float) {
        val u = ctx.resources.displayMetrics.density * scale // 1dp의 픽셀 수
        val ym = displayMonth(ctx)
        val ink = Inks.of(ctx, theme)
        val hasHeader = Decor.headerFile(theme, ym) != null

        val titleTf = Fonts.get(ctx, theme.titleFont)
        if (titleTf != null) {
            val tw = (widthPx - (96 + 2 * theme.bleed) * u).toInt()
            val th = (30 * u).toInt()
            if (tw > 0 && th > 0) {
                val color = if (hasHeader) Color.WHITE else ink.primary
                rv.setImageViewBitmap(R.id.title_img, Fonts.drawTitle(Fmt.titlePlain(ym, theme.titleStyle), titleTf, tw, th, color, 19 * u))
                rv.setViewVisibility(R.id.title, View.GONE)
                rv.setViewVisibility(R.id.title_img, View.VISIBLE)
            }
        }

        val bodyTf = Fonts.get(ctx, theme.bodyFont)
        if (bodyTf != null) {
            val bw = (widthPx - (24 + 2 * theme.bleed) * u).toInt()
            if (bw > 0) applyBodyFont(ctx, rv, theme, items, ym, ink, bodyTf, bw, u)
        }
    }

    /**
     * 글자 글꼴: 달력은 그림 한 장, 목록은 줄마다 그림으로 바꿔 끼움.
     * ○ 자리에는 투명한 눌림 영역을 겹쳐서 위젯에서 바로 체크할 수 있게 함.
     */
    private fun applyBodyFont(
        ctx: Context, rv: RemoteViews, theme: Theme, items: List<Item>, ym: YearMonth,
        ink: Ink, tf: android.graphics.Typeface, bw: Int, u: Float,
    ) {
        val pkg = ctx.packageName
        val today = LocalDate.now()
        val bp = BodyPainter(theme, ink, tf, bw, u)

        rv.setImageViewBitmap(R.id.body_img, bp.calendar(items, ym, today))
        for (id in intArrayOf(R.id.caption, R.id.weekday_row, R.id.grid, R.id.cal_bg)) rv.setViewVisibility(id, View.GONE)
        rv.setViewVisibility(R.id.body_img, View.VISIBLE)

        rv.removeAllViews(R.id.list)
        fun row(bmp: android.graphics.Bitmap): RemoteViews {
            val r = RemoteViews(pkg, R.layout.widget_img_row)
            r.setImageViewBitmap(R.id.img_row, bmp)
            return r
        }
        val plan = listPlan(ctx, items, ym, today, theme)
        for (pj in plan.projects) {
            val r = row(bp.projectRow(pj, items, today))
            r.setOnClickPendingIntent(R.id.img_root, projectPI(ctx, pj))
            rv.addView(R.id.list, r)
        }
        plan.highlight?.let { rv.addView(R.id.list, row(bp.highlight(it, today))) }
        if (plan.rest.isEmpty() && plan.highlight == null && plan.projects.isEmpty()) {
            rv.addView(R.id.list, row(bp.textRow("일정이 없어요. 눌러서 추가해 보세요.")))
        }
        for (e in plan.rest.take(plan.maxRows)) {
            val r = row(bp.itemRow(e, today))
            if (e.item.type == ItemType.TODO) {
                r.setViewVisibility(R.id.img_tap, View.VISIBLE)
                r.setOnClickPendingIntent(R.id.img_tap, togglePI(ctx, e.item.id))
            }
            rv.addView(R.id.list, r)
        }
        if (plan.rest.size > plan.maxRows) {
            rv.addView(R.id.list, row(bp.textRow("외 ${plan.rest.size - plan.maxRows}개 더 있음")))
        }
    }

    /** 배경 그림을 뺀 위젯 내용 (달력 + 목록). 꾸미기 화면 미리보기에도 사용. */
    fun buildContent(ctx: Context, items: List<Item>, theme: Theme): RemoteViews {
        val pkg = ctx.packageName
        val today = LocalDate.now()
        val ym = displayMonth(ctx)
        val ink = Inks.of(ctx, theme)
        val accent = Palette.accentColor(theme)
        val d = ctx.resources.displayMetrics.density

        val rv = RemoteViews(pkg, R.layout.widget_month)
        val pad = ((12 + theme.bleed.coerceIn(0, 60)) * d).toInt()
        rv.setViewPadding(R.id.content, pad, pad, pad, pad)

        // 제목 자리: 이미지가 있으면 흰 글씨 + 높이 조절
        val hasHeader = Decor.headerFile(theme, ym) != null
        val hDp = if (theme.headerLarge) HEADER_LARGE_DP else HEADER_NORMAL_DP
        val vPad = if (hasHeader) ((hDp - 30) / 2f * d).toInt() else 0
        val hPad = if (hasHeader) (4 * d).toInt() else 0
        rv.setViewPadding(R.id.header_row, hPad, vPad, hPad, vPad)
        rv.setViewVisibility(R.id.header_img, if (hasHeader) View.VISIBLE else View.GONE)
        rv.setTextViewText(R.id.title, Fmt.titleStyled(ym, theme.titleStyle, if (hasHeader) Color.WHITE else Palette.readable(accent, ink)))
        rv.setTextColor(R.id.title, if (hasHeader) Color.WHITE else ink.primary)
        rv.setTextColor(R.id.btn_prev, if (hasHeader) Color.WHITE else ink.secondary)
        rv.setTextColor(R.id.btn_next, if (hasHeader) Color.WHITE else ink.secondary)
        if (theme.caption.isBlank()) {
            rv.setViewVisibility(R.id.caption, View.GONE)
        } else {
            rv.setViewVisibility(R.id.caption, View.VISIBLE)
            rv.setTextViewText(R.id.caption, theme.caption)
            rv.setTextColor(R.id.caption, ink.secondary)
        }
        val labels = Fmt.weekLabels(theme)
        for (i in WEEKDAY_IDS.indices) {
            rv.setTextViewText(WEEKDAY_IDS[i], labels[i])
            if (theme.weekdayEn) rv.setTextViewTextSize(WEEKDAY_IDS[i], android.util.TypedValue.COMPLEX_UNIT_SP, 9.5f)
            rv.setTextColor(WEEKDAY_IDS[i], if (i == 0) ink.sun else if (i == 6) ink.sat else ink.secondary)
        }
        rv.setInt(R.id.divider, "setBackgroundColor", ink.divider)

        // 달력 칸 뒤 판
        if (theme.calBgColor != 0) {
            rv.setViewVisibility(R.id.cal_bg, View.VISIBLE)
            rv.setInt(R.id.cal_bg, "setColorFilter", theme.calBgColor)
            rv.setInt(R.id.cal_bg, "setImageAlpha", theme.calBgAlpha.coerceIn(0, 100) * 255 / 100)
            val cp = (6 * d).toInt()
            rv.setViewPadding(R.id.cal_inner, cp / 2, cp, cp / 2, cp / 2)
        }

        // ---------- 달력 ----------
        val marks = HashMap<LocalDate, Entry>()
        items.mapNotNull { item -> Store.occurrenceIn(item, ym)?.let { Entry(item, it) } }
            .filter { !(it.item.type == ItemType.TODO && it.item.done) }
            .sortedWith(compareBy<Entry>({ it.item.type != ItemType.DDAY }, { it.item.id }))
            .forEach { if (!marks.containsKey(it.date)) marks[it.date] = it }

        val first = ym.atDay(1)
        val lead = first.dayOfWeek.value % 7 // 일요일 = 0
        val start = first.minusDays(lead.toLong())
        val weeks = (lead + ym.lengthOfMonth() + 6) / 7

        rv.removeAllViews(R.id.grid)
        for (w in 0 until weeks) {
            val row = RemoteViews(pkg, R.layout.widget_row)
            for (d in 0 until 7) {
                val date = start.plusDays((w * 7 + d).toLong())
                val inMonth = YearMonth.from(date) == ym
                val cell = RemoteViews(pkg, R.layout.widget_cell)
                cell.setTextViewText(R.id.cell_num, date.dayOfMonth.toString())
                if (date == today) {
                    cell.setViewVisibility(R.id.today_circle, View.VISIBLE)
                    cell.setInt(R.id.today_circle, "setColorFilter", accent)
                    cell.setTextColor(R.id.cell_num, Palette.onColor(accent, theme))
                } else {
                    cell.setTextColor(
                        R.id.cell_num,
                        when {
                            !inMonth -> ink.muted
                            d == 0 -> ink.sun
                            d == 6 -> ink.sat
                            else -> ink.primary
                        }
                    )
                }
                val mark = if (inMonth) marks[date] else null
                if (mark == null) {
                    cell.setTextViewText(R.id.cell_dot, "")
                } else if (mark.item.emoji.isNotBlank()) {
                    cell.setTextViewText(R.id.cell_dot, mark.item.emoji.trim())
                    cell.setTextColor(R.id.cell_dot, ink.primary)
                } else {
                    cell.setTextViewText(R.id.cell_dot, "●")
                    cell.setTextColor(R.id.cell_dot, Palette.itemColor(mark.item, theme))
                }
                row.addView(R.id.row, cell)
            }
            rv.addView(R.id.grid, row)
        }

        // ---------- 목록 ----------
        rv.removeAllViews(R.id.list)
        val plan = listPlan(ctx, items, ym, today, theme)
        val visible = plan.rest
        val maxRows = plan.maxRows
        for (p in plan.projects) {
            val row = RemoteViews(pkg, R.layout.widget_project)
            val col = Palette.color(p.color)
            val pct = Store.percent(p, items)
            val left = java.time.temporal.ChronoUnit.DAYS.between(today, p.deadline)
            row.setTextViewText(R.id.pj_icon, p.emoji.trim().ifEmpty { "📁" })
            row.setTextViewText(R.id.pj_name, p.name)
            row.setTextColor(R.id.pj_name, ink.primary)
            row.setImageViewBitmap(R.id.pj_bar, Decor.progressBar((54 * d).toInt(), (6 * d).toInt(), pct, col, ink.divider))
            row.setTextViewText(R.id.pj_pct, "$pct%")
            row.setTextColor(R.id.pj_pct, ink.secondary)
            row.setTextViewText(R.id.pj_dday, Fmt.dday(p.deadline, today))
            row.setTextColor(R.id.pj_dday, if (left in 0..7) ink.sun else Palette.readable(col, ink))
            row.setOnClickPendingIntent(R.id.pj_root, projectPI(ctx, p))
            rv.addView(R.id.list, row)
        }
        plan.highlight?.let { hl ->
            val c = Palette.itemColor(hl.item, theme)
            val card = RemoteViews(pkg, R.layout.widget_highlight)
            card.setInt(R.id.hl_bg, "setColorFilter", c)
            card.setInt(R.id.hl_bg, "setImageAlpha", 46)
            card.setTextViewText(R.id.hl_icon, hl.item.emoji.trim().ifEmpty { "★" })
            card.setTextColor(R.id.hl_icon, Palette.readable(c, ink))
            card.setTextViewText(R.id.hl_title, hl.item.title)
            card.setTextColor(R.id.hl_title, ink.primary)
            card.setTextViewText(R.id.hl_sub, Fmt.monthDay(hl.date) + (if (hl.item.yearly) " · 매년" else ""))
            card.setTextColor(R.id.hl_sub, ink.secondary)
            card.setTextViewText(R.id.hl_dday, Fmt.dday(hl.date, today))
            card.setTextColor(R.id.hl_dday, Palette.readable(c, ink))
            rv.addView(R.id.list, card)
        }
        if (visible.isEmpty() && plan.highlight == null && plan.projects.isEmpty()) {
            val empty = RemoteViews(pkg, R.layout.widget_more)
            empty.setTextViewText(R.id.more_text, "일정이 없어요. 눌러서 추가해 보세요.")
            empty.setTextColor(R.id.more_text, ink.secondary)
            rv.addView(R.id.list, empty)
        } else {
            for (e in visible.take(maxRows)) {
                val item = e.item
                val row = RemoteViews(pkg, R.layout.widget_item)
                val ci = Palette.itemColor(item, theme)
                val faded = item.type == ItemType.TODO && item.done
                if (item.type == ItemType.DDAY) {
                    row.setTextViewText(R.id.item_badge, Fmt.dday(e.date, today))
                    row.setTextColor(R.id.item_badge, Palette.onColor(ci, theme))
                    row.setViewVisibility(R.id.item_pill, View.VISIBLE)
                    row.setInt(R.id.item_pill, "setColorFilter", ci)
                } else {
                    row.setTextViewText(R.id.item_badge, if (item.done) "✓" else "○")
                    row.setTextColor(R.id.item_badge, if (item.done) ink.muted else ci)
                    row.setOnClickPendingIntent(R.id.item_badge, togglePI(ctx, item.id))
                }
                row.setTextViewText(R.id.item_date, if (e.overdue) "밀림 " + Fmt.shortDate(e.date) else Fmt.shortDate(e.date))
                row.setTextViewText(R.id.item_title, Fmt.titleWithEmoji(item))
                row.setTextColor(R.id.item_title, if (faded) ink.muted else ink.primary)
                row.setTextColor(R.id.item_date, if (faded) ink.muted else if (e.overdue) ink.sun else ink.secondary)
                rv.addView(R.id.list, row)
            }
            if (visible.size > maxRows) {
                val more = RemoteViews(pkg, R.layout.widget_more)
                more.setTextViewText(R.id.more_text, "외 ${visible.size - maxRows}개 더 있음")
                more.setTextColor(R.id.more_text, ink.secondary)
                rv.addView(R.id.list, more)
            }
        }

        // ---------- 터치 동작 ----------
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_MONTH, ym.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        rv.setOnClickPendingIntent(R.id.title, open)
        rv.setOnClickPendingIntent(R.id.grid, open)
        rv.setOnClickPendingIntent(R.id.list, open)
        rv.setOnClickPendingIntent(R.id.title_img, open)
        rv.setOnClickPendingIntent(R.id.body_img, open)
        rv.setOnClickPendingIntent(R.id.btn_prev, broadcast(ctx, MonthWidgetProvider.ACTION_PREV, 1))
        rv.setOnClickPendingIntent(R.id.btn_next, broadcast(ctx, MonthWidgetProvider.ACTION_NEXT, 2))
        return rv
    }

    private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, MonthWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 자정이 지나면 오늘 표시와 디데이 숫자를 갱신하고 이번 달로 돌아옵니다. */
    fun scheduleMidnight(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val at = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000L
        am.set(AlarmManager.RTC, at, broadcast(ctx, MonthWidgetProvider.ACTION_MIDNIGHT, 3))
    }
}
