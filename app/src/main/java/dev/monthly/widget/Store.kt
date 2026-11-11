package dev.monthly.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** 할 일(체크), 디데이(카운트다운), 일정(약속 — 체크·카운트다운 없음) */
enum class ItemType { TODO, DDAY, EVENT }

data class Item(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val type: ItemType,
    val done: Boolean = false,
    val color: Int = -1,        // -1 = 기본 색
    val emoji: String = "",
    val yearly: Boolean = false, // 디데이 매년 반복
    val projectId: Long = 0,     // 0 = 프로젝트 없음
    val time: String = "",       // "HH:mm" (일정용, 선택)
)

/** 특정 날짜에 표시되는 일정 (매년 반복이면 그 해의 날짜). */
data class Entry(val item: Item, val date: LocalDate, val overdue: Boolean = false)

/** 작업 프로젝트 (회지 1권, 커뮤 오픈 등). 진행률은 체크리스트 자동 또는 분량 직접 입력. */
data class Project(
    val id: Long,
    val name: String,
    val emoji: String = "",
    val color: Int = 0,          // 팔레트 번호
    val deadline: LocalDate,
    val link: String = "",       // 작업 사이트 주소
    val manual: Boolean = false, // true면 분량 직접 입력
    val manualDone: Int = 0,
    val manualTotal: Int = 10,
    val unit: String = "",
)

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
    val titleFont: String = "",   // 글꼴 파일 이름 (비어 있으면 기본 글꼴)
    val bodyFont: String = "",
    val bodyFontScale: Int = 100, // 글꼴 파일을 쓸 때 글자 크기 %
    val customAccent: Int = 0,    // 직접 고른 포인트 색 (0이면 팔레트 색 사용)
    val bleed: Int = 0,           // 카드 바깥 투명 여백 dp (스티커가 삐져나올 공간)
    val titleStyle: Int = 0,      // 0 "2026년 11월", 1 "November 2026", 2 "11 NOVEMBER"
    val weekdayEn: Boolean = false,
    val border: Int = 0,          // 0 없음, 1 선, 2 스티치(점선)
    val highlight: Boolean = true, // 가장 가까운 디데이를 크게
    val showProjects: Boolean = true, // 위젯에 프로젝트 카드 (마감 가까운 2개)
    val customBg: Int = 0,        // 직접 고른 배경색 (0이면 배경 팔레트 색 사용)
    val onAccentMode: Int = 0,    // 포인트 색 위 글자: 0 자동, 1 흰색, 2 검정
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
                        projectId = o.optLong("projectId", 0),
                        time = o.optString("time", ""),
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
                    .put("projectId", item.projectId)
                    .put("time", item.time)
            )
        }
        prefs(ctx).edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    // ---------- 프로젝트 ----------
    fun loadProjects(ctx: Context): MutableList<Project> {
        val raw = prefs(ctx).getString("projects", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val out = mutableListOf<Project>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            try {
                out.add(
                    Project(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        emoji = o.optString("emoji", ""),
                        color = o.optInt("color", 0),
                        deadline = LocalDate.parse(o.getString("deadline")),
                        link = o.optString("link", ""),
                        manual = o.optBoolean("manual", false),
                        manualDone = o.optInt("manualDone", 0),
                        manualTotal = o.optInt("manualTotal", 10),
                        unit = o.optString("unit", ""),
                    )
                )
            } catch (e: Exception) {
            }
        }
        return out
    }

    fun saveProjects(ctx: Context, list: List<Project>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(
                JSONObject()
                    .put("id", p.id).put("name", p.name).put("emoji", p.emoji).put("color", p.color)
                    .put("deadline", p.deadline.toString()).put("link", p.link)
                    .put("manual", p.manual).put("manualDone", p.manualDone)
                    .put("manualTotal", p.manualTotal).put("unit", p.unit)
            )
        }
        prefs(ctx).edit().putString("projects", arr.toString()).apply()
    }

    /** (완료, 전체) */
    fun progress(p: Project, items: List<Item>): Pair<Int, Int> {
        if (p.manual) return Pair(p.manualDone.coerceAtLeast(0), p.manualTotal.coerceAtLeast(1))
        val list = items.filter { it.projectId == p.id && it.type == ItemType.TODO } // 진행률은 할 일만
        return Pair(list.count { it.done }, list.size)
    }

    fun percent(p: Project, items: List<Item>): Int {
        val (d, t) = progress(p, items)
        return if (t <= 0) 0 else (d * 100 / t).coerceIn(0, 100)
    }

    // ---------- 설정 ----------
    fun carryOver(ctx: Context): Boolean = prefs(ctx).getBoolean("carry", true)
    fun setCarryOver(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean("carry", v).apply() }

    // ---------- 백업용 원본 ----------
    fun rawExport(ctx: Context): JSONObject {
        val pr = prefs(ctx)
        return JSONObject()
            .put("version", 1)
            .put("items", pr.getString(KEY_ITEMS, "[]"))
            .put("projects", pr.getString("projects", "[]"))
            .put("theme", pr.getString(KEY_THEME, "{}"))
            .put("carry", pr.getBoolean("carry", true))
    }

    fun rawImport(ctx: Context, o: JSONObject) {
        prefs(ctx).edit()
            .putString(KEY_ITEMS, o.optString("items", "[]"))
            .putString("projects", o.optString("projects", "[]"))
            .putString(KEY_THEME, o.optString("theme", "{}"))
            .putBoolean("carry", o.optBoolean("carry", true))
            .putInt(KEY_OFFSET, 0)
            .apply()
    }

    // ---------- 꾸미기 ----------
    /** 앱 폴더 안의 파일 이름만 허용 (경로 문자 포함 시 무시) */
    private fun safeName(n: String): String =
        if (n.isEmpty() || n.contains('/') || n.contains('\\') || n.contains("..") || n.startsWith(".")) "" else n

    fun loadTheme(ctx: Context): Theme {
        val raw = prefs(ctx).getString(KEY_THEME, null) ?: return Theme()
        return try {
            val o = JSONObject(raw)
            val st = mutableListOf<Sticker>()
            val arr = o.optJSONArray("stickers") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val fileName = safeName(s.optString("file", ""))
                if (fileName.isEmpty()) continue
                st.add(
                    Sticker(
                        id = s.optString("id", i.toString()),
                        file = fileName,
                        cx = s.optDouble("cx", 0.5).toFloat(),
                        cy = s.optDouble("cy", 0.5).toFloat(),
                        w = s.optDouble("w", 0.3).toFloat(),
                        rot = s.optDouble("rot", 0.0).toFloat(),
                    )
                )
            }
            val monthly = mutableMapOf<String, String>()
            val mo = o.optJSONObject("headerMonthly")
            if (mo != null) for (k in mo.keys()) safeName(mo.getString(k)).takeIf { it.isNotEmpty() }?.let { monthly[k] = it }
            Theme(
                accent = o.optInt("accent", 0),
                bgMode = o.optInt("bgMode", 0),
                bgColor = o.optInt("bgColor", 0),
                bgImage = safeName(o.optString("bgImage", "")),
                bgAlpha = o.optInt("bgAlpha", 95),
                dim = o.optInt("dim", 25),
                textMode = o.optInt("textMode", 0),
                caption = o.optString("caption", ""),
                stickers = st,
                headerImage = safeName(o.optString("headerImage", "")),
                headerMonthly = monthly,
                headerDim = o.optInt("headerDim", 20),
                headerLarge = o.optBoolean("headerLarge", false),
                titleFont = safeName(o.optString("titleFont", "")),
                bodyFont = safeName(o.optString("bodyFont", "")),
                bodyFontScale = o.optInt("bodyFontScale", 100),
                customAccent = o.optInt("customAccent", 0),
                bleed = o.optInt("bleed", 0),
                titleStyle = o.optInt("titleStyle", 0),
                weekdayEn = o.optBoolean("weekdayEn", false),
                border = o.optInt("border", 0),
                highlight = o.optBoolean("highlight", true),
                showProjects = o.optBoolean("showProjects", true),
                customBg = o.optInt("customBg", 0),
                onAccentMode = o.optInt("onAccentMode", 0),
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
            .put("titleFont", t.titleFont).put("bodyFont", t.bodyFont).put("bodyFontScale", t.bodyFontScale)
            .put("customAccent", t.customAccent).put("bleed", t.bleed)
            .put("titleStyle", t.titleStyle).put("weekdayEn", t.weekdayEn)
            .put("border", t.border).put("highlight", t.highlight)
            .put("showProjects", t.showProjects)
            .put("customBg", t.customBg).put("onAccentMode", t.onAccentMode)
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

    /** 끝난 것: 완료한 할 일, 지난 일정 → 흐리게, 목록 아래쪽 */
    fun finished(e: Entry, today: LocalDate): Boolean =
        (e.item.type == ItemType.TODO && e.item.done) || (e.item.type == ItemType.EVENT && e.date.isBefore(today))

    /** 오늘 이후 가장 가까운 디데이 (없으면 null) */
    fun nearestDday(all: List<Item>, today: LocalDate): Entry? =
        all.filter { it.type == ItemType.DDAY }
            .map { Entry(it, nextOccurrence(it, today)) }
            .filter { !it.date.isBefore(today) }
            .minWithOrNull(compareBy<Entry>({ it.date }, { it.item.id }))

    /** 그 달의 일정 + 그 뒤로 다가오는 디데이. 완료한 할 일은 맨 뒤로. */
    fun entries(all: List<Item>, ym: YearMonth, today: LocalDate, carry: Boolean = false): List<Entry> {
        val monthEnd = ym.atEndOfMonth()
        val isCurrent = ym == YearMonth.from(today)
        val out = mutableListOf<Entry>()
        for (item in all) {
            val openTodo = item.type == ItemType.TODO && !item.done
            val occ = occurrenceIn(item, ym)
            if (occ != null) {
                out.add(Entry(item, occ, carry && isCurrent && openTodo && occ.isBefore(today)))
                continue
            }
            // 못 한 일 이월: 지난달 이전의 미완료 할 일도 이번 달 목록에 올림
            if (carry && isCurrent && openTodo && item.date.isBefore(ym.atDay(1))) {
                out.add(Entry(item, item.date, true))
                continue
            }
            if (item.type == ItemType.DDAY) {
                val next = nextOccurrence(item, today)
                if (next.isAfter(monthEnd) && !next.isBefore(today)) out.add(Entry(item, next))
            }
        }
        return out.sortedWith(
            compareBy<Entry>({ finished(it, today) }, { !it.overdue }, { it.date }, { it.item.time }, { it.item.id })
        )
    }
}

object Fmt {
    private val weekdays = arrayOf("월", "화", "수", "목", "금", "토", "일")
    private val EN_MONTHS = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    val WEEK_KO = arrayOf("일", "월", "화", "수", "목", "금", "토")
    val WEEK_EN = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

    fun weekLabels(theme: Theme) = if (theme.weekdayEn) WEEK_EN else WEEK_KO

    /** 글꼴 그림용 (꾸밈 없는 글자) */
    fun titlePlain(ym: YearMonth, style: Int): String = when (style) {
        1 -> "${EN_MONTHS[ym.monthValue - 1]} ${ym.year}"
        2 -> "${ym.monthValue}  ${EN_MONTHS[ym.monthValue - 1].uppercase()}"
        else -> month(ym)
    }

    /** 위젯 제목 (숫자를 크게·포인트 색으로 꾸밈) */
    fun titleStyled(ym: YearMonth, style: Int, accent: Int): CharSequence {
        val text = titlePlain(ym, style)
        val s = android.text.SpannableString(text)
        val flag = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        when (style) {
            1 -> {
                val cut = text.lastIndexOf(' ')
                s.setSpan(android.text.style.RelativeSizeSpan(0.75f), cut, text.length, flag)
            }
            2 -> {
                val cut = text.indexOf(' ')
                s.setSpan(android.text.style.RelativeSizeSpan(1.7f), 0, cut, flag)
                s.setSpan(android.text.style.ForegroundColorSpan(accent), 0, cut, flag)
                s.setSpan(android.text.style.RelativeSizeSpan(0.8f), cut, text.length, flag)
            }
        }
        return s
    }

    fun monthDay(date: LocalDate): String =
        "${date.monthValue}월 ${date.dayOfMonth}일 (${weekday(date.dayOfWeek)})"

    fun dday(date: LocalDate, today: LocalDate): String {
        val d = ChronoUnit.DAYS.between(today, date)
        return when {
            d > 0 -> "D-$d"
            d == 0L -> "D-DAY"
            else -> "D+${-d}"
        }
    }

    /** 11/14 (토) 20:00 */
    fun shortWhen(date: LocalDate, time: String): String =
        if (time.isBlank()) shortDate(date) else "${shortDate(date)} $time"

    fun shortDate(date: LocalDate): String =
        "${date.monthValue}/${date.dayOfMonth} (${weekday(date.dayOfWeek)})"

    fun longDate(date: LocalDate): String =
        "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일 (${weekday(date.dayOfWeek)})"

    fun month(ym: YearMonth): String = "${ym.year}년 ${ym.monthValue}월"

    fun titleWithEmoji(item: Item): String =
        if (item.emoji.isBlank()) item.title else "${item.emoji.trim()} ${item.title}"

    private fun weekday(d: DayOfWeek) = weekdays[d.value - 1]
}
