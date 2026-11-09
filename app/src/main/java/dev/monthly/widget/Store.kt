package dev.monthly.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class ItemType { TODO, DDAY }

data class Item(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val type: ItemType,
    val done: Boolean = false,
)

object Store {
    private const val PREFS = "monthly"
    private const val KEY_ITEMS = "items"
    private const val KEY_OFFSET = "widget_offset"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): MutableList<Item> {
        val raw = prefs(ctx).getString(KEY_ITEMS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val out = mutableListOf<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            try {
                out.add(
                    Item(
                        id = o.getLong("id"),
                        date = LocalDate.parse(o.getString("date")),
                        title = o.getString("title"),
                        type = ItemType.valueOf(o.optString("type", "TODO")),
                        done = o.optBoolean("done", false),
                    )
                )
            } catch (e: Exception) {
                // 손상된 항목은 건너뜀
            }
        }
        return out
    }

    fun save(ctx: Context, items: List<Item>) {
        val arr = JSONArray()
        for (it in items) {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("date", it.date.toString())
                    .put("title", it.title)
                    .put("type", it.type.name)
                    .put("done", it.done)
            )
        }
        prefs(ctx).edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun getOffset(ctx: Context): Int = prefs(ctx).getInt(KEY_OFFSET, 0)

    fun setOffset(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt(KEY_OFFSET, value).apply()
    }

    /** 해당 달의 항목 + 그 이후에 다가오는 디데이. 완료한 할 일은 맨 뒤로. */
    fun visibleItems(all: List<Item>, ym: YearMonth, today: LocalDate): List<Item> {
        val monthEnd = ym.atEndOfMonth()
        return all.filter {
            YearMonth.from(it.date) == ym ||
                (it.type == ItemType.DDAY && it.date.isAfter(monthEnd) && !it.date.isBefore(today))
        }.sortedWith(compareBy<Item>({ it.type == ItemType.TODO && it.done }, { it.date }, { it.id }))
    }
}

object Fmt {
    private val weekdays = arrayOf("월", "화", "수", "목", "금", "토", "일")

    fun dday(date: LocalDate, today: LocalDate): String {
        val d = ChronoUnit.DAYS.between(today, date)
        return when {
            d > 0 -> "D-$d"
            d == 0L -> "D-DAY"
            else -> "D+${-d}"
        }
    }

    fun shortDate(date: LocalDate): String =
        "${date.monthValue}/${date.dayOfMonth} (${weekday(date.dayOfWeek)})"

    fun longDate(date: LocalDate): String =
        "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일 (${weekday(date.dayOfWeek)})"

    fun month(ym: YearMonth): String = "${ym.year}년 ${ym.monthValue}월"

    private fun weekday(d: DayOfWeek) = weekdays[d.value - 1]
}
