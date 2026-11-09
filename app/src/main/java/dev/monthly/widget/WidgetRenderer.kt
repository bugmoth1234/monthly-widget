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
    private const val MAX_LIST = 5
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
                applyDecor(ctx, rv, theme, wDp, hDp, maxSide)
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

    private fun applyDecor(ctx: Context, rv: RemoteViews, theme: Theme, wDp: Int, hDp: Int, maxSide: Int) {
        val d = ctx.resources.displayMetrics.density
        val w = wDp * d
        val h = hDp * d
        val scale = minOf(1f, maxSide / maxOf(w, h))
        val wp = (w * scale).toInt().coerceAtLeast(1)
        val hp = (h * scale).toInt().coerceAtLeast(1)
        rv.setImageViewBitmap(R.id.bg_layer, Decor.renderBackground(ctx, theme, wp, hp, 22f * d * scale))
        val st = Decor.renderStickers(ctx, theme.stickers, wp, hp)
        if (st != null) {
            rv.setViewVisibility(R.id.sticker_layer, View.VISIBLE)
            rv.setImageViewBitmap(R.id.sticker_layer, st)
        } else {
            rv.setViewVisibility(R.id.sticker_layer, View.GONE)
        }
    }

    /** 배경 그림을 뺀 위젯 내용 (달력 + 목록). 꾸미기 화면 미리보기에도 사용. */
    fun buildContent(ctx: Context, items: List<Item>, theme: Theme): RemoteViews {
        val pkg = ctx.packageName
        val today = LocalDate.now()
        val ym = YearMonth.from(today).plusMonths(Store.getOffset(ctx).toLong())
        val ink = Inks.of(ctx, theme)
        val accent = Palette.idx(theme.accent)

        val rv = RemoteViews(pkg, R.layout.widget_month)
        rv.setTextViewText(R.id.title, Fmt.month(ym))
        rv.setTextColor(R.id.title, ink.primary)
        rv.setTextColor(R.id.btn_prev, ink.secondary)
        rv.setTextColor(R.id.btn_next, ink.secondary)
        if (theme.caption.isBlank()) {
            rv.setViewVisibility(R.id.caption, View.GONE)
        } else {
            rv.setViewVisibility(R.id.caption, View.VISIBLE)
            rv.setTextViewText(R.id.caption, theme.caption)
            rv.setTextColor(R.id.caption, ink.secondary)
        }
        for (i in WEEKDAY_IDS.indices) {
            rv.setTextColor(WEEKDAY_IDS[i], if (i == 0) ink.sun else if (i == 6) ink.sat else ink.secondary)
        }
        rv.setInt(R.id.divider, "setBackgroundColor", ink.divider)

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
                    cell.setInt(R.id.cell_num, "setBackgroundResource", Palette.todayBg[accent])
                    cell.setTextColor(R.id.cell_num, Color.WHITE)
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
                    cell.setTextColor(R.id.cell_dot, Palette.color(Palette.itemColor(mark.item, theme)))
                }
                row.addView(R.id.row, cell)
            }
            rv.addView(R.id.grid, row)
        }

        // ---------- 목록 ----------
        rv.removeAllViews(R.id.list)
        val visible = Store.entries(items, ym, today)
        if (visible.isEmpty()) {
            val empty = RemoteViews(pkg, R.layout.widget_more)
            empty.setTextViewText(R.id.more_text, "일정이 없어요. 눌러서 추가해 보세요.")
            empty.setTextColor(R.id.more_text, ink.secondary)
            rv.addView(R.id.list, empty)
        } else {
            for (e in visible.take(MAX_LIST)) {
                val item = e.item
                val row = RemoteViews(pkg, R.layout.widget_item)
                val ci = Palette.itemColor(item, theme)
                val faded = item.type == ItemType.TODO && item.done
                if (item.type == ItemType.DDAY) {
                    row.setTextViewText(R.id.item_badge, Fmt.dday(e.date, today))
                    row.setTextColor(R.id.item_badge, Color.WHITE)
                    row.setInt(R.id.item_badge, "setBackgroundResource", Palette.pill[ci])
                } else {
                    row.setTextViewText(R.id.item_badge, if (item.done) "✓" else "○")
                    row.setTextColor(R.id.item_badge, if (item.done) ink.muted else Palette.color(ci))
                }
                row.setTextViewText(R.id.item_date, Fmt.shortDate(e.date))
                row.setTextViewText(R.id.item_title, Fmt.titleWithEmoji(item))
                row.setTextColor(R.id.item_title, if (faded) ink.muted else ink.primary)
                row.setTextColor(R.id.item_date, if (faded) ink.muted else ink.secondary)
                rv.addView(R.id.list, row)
            }
            if (visible.size > MAX_LIST) {
                val more = RemoteViews(pkg, R.layout.widget_more)
                more.setTextViewText(R.id.more_text, "외 ${visible.size - MAX_LIST}개 더 있음")
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
