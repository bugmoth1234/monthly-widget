package dev.monthly.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

object WidgetRenderer {
    private const val MAX_LIST = 5

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, MonthWidgetProvider::class.java))
        if (ids.isEmpty()) return
        mgr.updateAppWidget(ids, build(ctx))
    }

    private fun build(ctx: Context): RemoteViews {
        val pkg = ctx.packageName
        val today = LocalDate.now()
        val ym = YearMonth.from(today).plusMonths(Store.getOffset(ctx).toLong())
        val items = Store.load(ctx)

        val cText = ctx.getColor(R.color.text_primary)
        val cMuted = ctx.getColor(R.color.text_muted)
        val cSecondary = ctx.getColor(R.color.text_secondary)
        val cSun = ctx.getColor(R.color.sunday)
        val cSat = ctx.getColor(R.color.saturday)
        val cAccent = ctx.getColor(R.color.accent)
        val cDday = ctx.getColor(R.color.dday)
        val cOnAccent = ctx.getColor(R.color.on_accent)

        val rv = RemoteViews(pkg, R.layout.widget_month)
        rv.setTextViewText(R.id.title, Fmt.month(ym))

        // ---------- 달력 ----------
        val active = items.filter { !(it.type == ItemType.TODO && it.done) }
        val ddayDates = active.filter { it.type == ItemType.DDAY }.map { it.date }.toSet()
        val todoDates = active.filter { it.type == ItemType.TODO }.map { it.date }.toSet()

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
                    cell.setInt(R.id.cell_num, "setBackgroundResource", R.drawable.today_bg)
                    cell.setTextColor(R.id.cell_num, cOnAccent)
                } else {
                    val color = when {
                        !inMonth -> cMuted
                        d == 0 -> cSun
                        d == 6 -> cSat
                        else -> cText
                    }
                    cell.setTextColor(R.id.cell_num, color)
                }

                when {
                    inMonth && date in ddayDates -> {
                        cell.setTextViewText(R.id.cell_dot, "●")
                        cell.setTextColor(R.id.cell_dot, cDday)
                    }
                    inMonth && date in todoDates -> {
                        cell.setTextViewText(R.id.cell_dot, "●")
                        cell.setTextColor(R.id.cell_dot, cAccent)
                    }
                    else -> cell.setTextViewText(R.id.cell_dot, "")
                }
                row.addView(R.id.row, cell)
            }
            rv.addView(R.id.grid, row)
        }

        // ---------- 목록 ----------
        rv.removeAllViews(R.id.list)
        val visible = Store.visibleItems(items, ym, today)
        if (visible.isEmpty()) {
            val empty = RemoteViews(pkg, R.layout.widget_more)
            empty.setTextViewText(R.id.more_text, "일정이 없어요. 눌러서 추가해 보세요.")
            rv.addView(R.id.list, empty)
        } else {
            for (item in visible.take(MAX_LIST)) {
                val row = RemoteViews(pkg, R.layout.widget_item)
                val faded = item.type == ItemType.TODO && item.done
                if (item.type == ItemType.DDAY) {
                    row.setTextViewText(R.id.item_badge, Fmt.dday(item.date, today))
                    row.setTextColor(R.id.item_badge, cOnAccent)
                    row.setInt(R.id.item_badge, "setBackgroundResource", R.drawable.dday_bg)
                } else {
                    row.setTextViewText(R.id.item_badge, if (item.done) "✓" else "○")
                    row.setTextColor(R.id.item_badge, if (item.done) cMuted else cAccent)
                }
                row.setTextViewText(R.id.item_date, Fmt.shortDate(item.date))
                row.setTextViewText(R.id.item_title, item.title)
                row.setTextColor(R.id.item_title, if (faded) cMuted else cText)
                row.setTextColor(R.id.item_date, if (faded) cMuted else cSecondary)
                rv.addView(R.id.list, row)
            }
            if (visible.size > MAX_LIST) {
                val more = RemoteViews(pkg, R.layout.widget_more)
                more.setTextViewText(R.id.more_text, "외 ${visible.size - MAX_LIST}개 더 있음")
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
