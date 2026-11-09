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
    val color: Int = -1,        // -1 = 기본 색
    val emoji: String = "",
    val yearly: Boolean = false, // 디데이 매년 반복
)

/** 특정 날짜에 표시되는 일정 (매년 반복이면 그 해의 날짜). */
data class Entry(val item: Item, val date: LocalDate)

/** cx, cy: 위젯 너비·높이 대비 중심 위치(0~1), w: 위젯 너비 대비 스티커 너비, rot: 각도 */
data class Sticker(val id: String, val file: String, val cx: Float, val cy: Float, val w: Float, val rot: Float)

data class Theme(
    val accent: Int = 0,
    val bgMode: Int = 0,     // 0 기본, 1 단색, 2 사진
    val bgColor: Int = 0,
    val bgImage: String = "",
    val bgAlpha: Int = 95,
    val dim: Int = 25,
    val textMode: Int = 0,   // 0 자동, 1 어둡게, 2 밝게
    val caption: String = "",
    val stickers: List<Sticker> = emptyList(),
    val headerImage: String = "",                    // 모든 달 공통 제목 배경
    val headerMonthly: Map<String, String> = emptyMap(), // "2026-11" → 그 달만 쓰는 이미지
    val headerDim: Int = 20,
    val headerLarge: Boolean = false,
)

object Store {
    private const val PREFS = "monthly"
    private const val KEY_ITEMS = "items"
    private const val KEY_THEME = "theme"
    private const val KEY_OFFSET = "widget_offset"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- 일정 ----------
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
                        color = o.optInt("color", -1),
                        emoji = o.optString("emoji", ""),
                        yearly = o.optBoolean("yearly", false),
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
        for (item in items) {
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("date", item.date.toString())
                    .put("title", item.title)
                    .put("type", item.type.name)
                    .put("done", item.done)
                    .put("color", item.color)
                    .put("emoji", item.emoji)
                    .put("yearly", item.yearly)
            )
        }
        prefs(ctx).edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    // ---------- 꾸미기 ----------
    fun loadTheme(ctx: Context): Theme {
        val raw = prefs(ctx).getString(KEY_THEME, null) ?: return Theme()
        return try {
            val o = JSONObject(raw)
            val st = mutableListOf<Sticker>()
            val arr = o.optJSONArray("stickers") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                st.add(
                    Sticker(
                        id = s.optString("id", i.toString()),
                        file = s.getString("file"),
                        cx = s.optDouble("cx", 0.5).toFloat(),
                        cy = s.optDouble("cy", 0.5).toFloat(),
                        w = s.optDouble("w", 0.3).toFloat(),
                        rot = s.optDouble("rot", 0.0).toFloat(),
                    )
                )
            }
            val monthly = mutableMapOf<String, String>()
            val mo = o.optJSONObject("headerMonthly")
            if (mo != null) for (k in mo.keys()) monthly[k] = mo.getString(k)
            Theme(
                accent = o.optInt("accent", 0),
                bgMode = o.optInt("bgMode", 0),
                bgColor = o.optInt("bgColor", 0),
                bgImage = o.optString("bgImage", ""),
                bgAlpha = o.optInt("bgAlpha", 95),
                dim = o.optInt("dim", 25),
                textMode = o.optInt("textMode", 0),
                caption = o.optString("caption", ""),
                stickers = st,
                headerImage = o.optString("headerImage", ""),
                headerMonthly = monthly,
                headerDim = o.optInt("headerDim", 20),
                headerLarge = o.optBoolean("headerLarge", false),
            )
        } catch (e: Exception) {
            Theme()
        }
    }

    fun saveTheme(ctx: Context, t: Theme) {
        val arr = JSONArray()
        for (s in t.stickers) {
            arr.put(
                JSONObject()
                    .put("id", s.id).put("file", s.file)
                    .put("cx", s.cx.toDouble()).put("cy", s.cy.toDouble())
                    .put("w", s.w.toDouble()).put("rot", s.rot.toDouble())
            )
        }
        val o = JSONObject()
            .put("accent", t.accent).put("bgMode", t.bgMode).put("bgColor", t.bgColor)
            .put("bgImage", t.bgImage).put("bgAlpha", t.bgAlpha).put("dim", t.dim)
            .put("textMode", t.textMode).put("caption", t.caption).put("stickers", arr)
            .put("headerImage", t.headerImage).put("headerMonthly", JSONObject(t.headerMonthly as Map<*, *>))
            .put("headerDim", t.headerDim).put("headerLarge", t.headerLarge)
        prefs(ctx).edit().putString(KEY_THEME, o.toString()).apply()
    }

    // ---------- 위젯 상태 ----------
    fun getOffset(ctx: Context): Int = prefs(ctx).getInt(KEY_OFFSET, 0)

    fun setOffset(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt(KEY_OFFSET, value).apply()
    }

    fun widgetSize(ctx: Context): Pair<Int, Int> =
        Pair(prefs(ctx).getInt("w_dp", 300), prefs(ctx).getInt("h_dp", 320))

    fun setWidgetSize(ctx: Context, wDp: Int, hDp: Int) {
        prefs(ctx).edit().putInt("w_dp", wDp).putInt("h_dp", hDp).apply()
    }

    // ---------- 날짜 계산 ----------
    /** 그 달에 이 일정이 있으면 그 날짜, 없으면 null. 매년 반복은 원래 해 이후로만. */
    fun occurrenceIn(item: Item, ym: YearMonth): LocalDate? {
        if (!item.yearly) return if (YearMonth.from(item.date) == ym) item.date else null
        if (item.date.month != ym.month) return null
        if (ym.isBefore(YearMonth.from(item.date))) return null
        return item.date.withYear(ym.year) // 2/29 → 평년엔 2/28
    }

    /** 오늘 이후 가장 가까운 날짜 (매년 반복이 아니면 원래 날짜). */
    fun nextOccurrence(item: Item, today: LocalDate): LocalDate {
        if (!item.yearly) return item.date
        var d = item.date.withYear(today.year)
        if (d.isBefore(today)) d = item.date.withYear(today.year + 1)
        return if (d.isBefore(item.date)) item.date else d
    }

    /** 그 달의 일정 + 그 뒤로 다가오는 디데이. 완료한 할 일은 맨 뒤로. */
    fun entries(all: List<Item>, ym: YearMonth, today: LocalDate): List<Entry> {
        val monthEnd = ym.atEndOfMonth()
        val out = mutableListOf<Entry>()
        for (item in all) {
            val occ = occurrenceIn(item, ym)
            if (occ != null) {
                out.add(Entry(item, occ))
                continue
            }
            if (item.type == ItemType.DDAY) {
                val next = nextOccurrence(item, today)
                if (next.isAfter(monthEnd) && !next.isBefore(today)) out.add(Entry(item, next))
            }
        }
        return out.sortedWith(
            compareBy<Entry>({ it.item.type == ItemType.TODO && it.item.done }, { it.date }, { it.item.id })
        )
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

    fun titleWithEmoji(item: Item): String =
        if (item.emoji.isBlank()) item.title else "${item.emoji.trim()} ${item.title}"

    private fun weekday(d: DayOfWeek) = weekdays[d.value - 1]
}
