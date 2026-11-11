package dev.monthly.widget

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MONTH = "month"
        private const val REQ_BACKUP = 21
        private const val REQ_RESTORE = 22
    }

    private var ym: YearMonth = YearMonth.now()
    private var selected: LocalDate? = null
    private var items: MutableList<Item> = mutableListOf()
    private var widgetTheme = Theme()
    private var projectNames: Map<Long, String> = emptyMap()

    /** 목록 한 줄: 소제목 또는 일정 */
    private sealed class Row {
        class Header(val text: String) : Row()
        class EntryRow(val e: Entry) : Row()
    }

    private var rows: List<Row> = emptyList()
    private lateinit var monthView: MonthView
    private lateinit var adapter: RowAdapter
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setOnClickListener { goToday() }

        readMonthExtra(intent)

        list = findViewById(R.id.list)
        val header = layoutInflater.inflate(R.layout.header_month, list, false)
        monthView = header.findViewById(R.id.month_view)
        list.addHeaderView(header, null, false)
        adapter = RowAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val r = rows.getOrNull(pos - list.headerViewsCount)
            if (r is Row.EntryRow) showEditor(r.e.item, null)
        }

        monthView.onDayClick = { d ->
            selected = if (selected == d) null else d
            if (YearMonth.from(d) != ym) ym = YearMonth.from(d)
            refresh()
        }
        monthView.onDayLongPress = { d -> showEditor(null, d) }

        findViewById<ExtendedFloatingActionButton>(R.id.fab_add).setOnClickListener { showEditor(null, selected) }
        Nav.setup(this, R.id.nav_schedule)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readMonthExtra(intent)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        items = Store.load(this)
        widgetTheme = Store.loadTheme(this)
        refresh()
    }

    private fun readMonthExtra(intent: Intent?) {
        val m = intent?.getStringExtra(EXTRA_MONTH) ?: return
        ym = try { YearMonth.parse(m) } catch (e: Exception) { YearMonth.now() }
        selected = null
    }

    private fun goToday() {
        ym = YearMonth.now()
        selected = null
        refresh()
    }

    private fun refresh() {
        supportActionBar?.title = Fmt.month(ym)
        projectNames = Store.loadProjects(this).associate { it.id to "${it.emoji.trim()} ${it.name}".trim() }
        monthView.setData(ym, items, widgetTheme, selected)
        rows = buildRows()
        adapter.notifyDataSetChanged()
    }

    private fun buildRows(): List<Row> {
        val today = LocalDate.now()
        val out = mutableListOf<Row>()
        val sel = selected
        if (sel != null) {
            val day = items.mapNotNull { item ->
                Store.occurrenceIn(item, YearMonth.from(sel))?.takeIf { it == sel }?.let { Entry(item, it) }
            }.sortedWith(compareBy<Entry>({ Store.finished(it, today) }, { it.item.time }, { it.item.id }))
            out.add(Row.Header(Fmt.monthDay(sel) + (if (day.isEmpty()) " · 일정 없음 (날짜를 길게 누르면 추가)" else "")))
            day.forEach { out.add(Row.EntryRow(it)) }
            return out
        }
        val all = Store.entries(items, ym, today, Store.carryOver(this))
        val overdue = all.filter { it.overdue }
        val done = all.filter { Store.finished(it, today) }
        val open = all.filter { !it.overdue && !Store.finished(it, today) }
        if (overdue.isNotEmpty()) {
            out.add(Row.Header("밀린 일 ${overdue.size}"))
            overdue.forEach { out.add(Row.EntryRow(it)) }
        }
        if (open.isNotEmpty()) {
            out.add(Row.Header("일정"))
            open.forEach { out.add(Row.EntryRow(it)) }
        }
        if (done.isNotEmpty()) {
            out.add(Row.Header("완료·지난 일정 ${done.size}"))
            done.forEach { out.add(Row.EntryRow(it)) }
        }
        if (out.isEmpty()) out.add(Row.Header("이 달에는 일정이 없어요 · ＋ 버튼이나 날짜를 길게 눌러 추가"))
        return out
    }

    private fun persist() {
        Store.save(this, items)
        WidgetRenderer.updateAll(this)
        refresh()
    }

    private fun replace(updated: Item) {
        val i = items.indexOfFirst { it.id == updated.id }
        if (i >= 0) items[i] = updated else items.add(updated)
        persist()
    }

    // ---------- 일정 추가/수정 시트 ----------
    private fun showEditor(existing: Item?, presetDate: LocalDate?) {
        val sheet = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_item, null)
        sheet.setContentView(v)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true

        val titleLayout = v.findViewById<TextInputLayout>(R.id.title_layout)
        val titleInput = v.findViewById<EditText>(R.id.input_title)
        val dateBtn = v.findViewById<Button>(R.id.input_date)
        val toggle = v.findViewById<MaterialButtonToggleGroup>(R.id.type_toggle)
        val yearlySwitch = v.findViewById<MaterialSwitch>(R.id.input_yearly)
        val emojiInput = v.findViewById<EditText>(R.id.input_emoji)
        val emojiGroup = v.findViewById<ChipGroup>(R.id.emoji_group)
        val colorRow = v.findViewById<LinearLayout>(R.id.color_row)
        val projectInput = v.findViewById<MaterialAutoCompleteTextView>(R.id.input_project)
        v.findViewById<TextView>(R.id.sheet_title).text = if (existing == null) "새 일정" else "일정 수정"

        val today = LocalDate.now()
        var date: LocalDate = existing?.date ?: presetDate ?: if (ym == YearMonth.from(today)) today else ym.atDay(1)
        var color = existing?.color ?: -1

        toggle.check(
            when (existing?.type) {
                ItemType.DDAY -> R.id.btn_type_dday
                ItemType.EVENT -> R.id.btn_type_event
                else -> R.id.btn_type_todo
            }
        )
        fun isDday() = toggle.checkedButtonId == R.id.btn_type_dday
        fun isEvent() = toggle.checkedButtonId == R.id.btn_type_event

        // 시간 (일정만)
        val timeRow = v.findViewById<View>(R.id.time_row)
        val timeBtn = v.findViewById<Button>(R.id.input_time)
        val timeClear = v.findViewById<Button>(R.id.btn_time_clear)
        var time = existing?.time ?: ""
        fun showTime() {
            timeBtn.text = if (time.isBlank()) "시간 추가 (선택)" else "시간: $time"
            timeClear.visibility = if (time.isBlank()) View.GONE else View.VISIBLE
        }
        showTime()
        timeBtn.setOnClickListener {
            val h = time.substringBefore(':').toIntOrNull() ?: 20
            val m = time.substringAfter(':', "").toIntOrNull() ?: 0
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(h).setMinute(m)
                .setTitleText("시간")
                .build()
            picker.addOnPositiveButtonClickListener {
                time = String.format(java.util.Locale.US, "%02d:%02d", picker.hour, picker.minute)
                showTime()
            }
            picker.show(supportFragmentManager, "time")
        }
        timeClear.setOnClickListener { time = ""; showTime() }

        fun syncType() {
            yearlySwitch.visibility = if (isDday()) View.VISIBLE else View.GONE
            timeRow.visibility = if (isEvent()) View.VISIBLE else View.GONE
        }
        yearlySwitch.isChecked = existing?.yearly ?: false
        syncType()
        toggle.addOnButtonCheckedListener { _, _, _ -> syncType() }

        if (existing != null) {
            titleInput.setText(existing.title)
            titleInput.setSelection(existing.title.length)
            emojiInput.setText(existing.emoji)
        }

        fun showDate() { dateBtn.text = Fmt.longDate(date) }
        showDate()
        dateBtn.setOnClickListener {
            DatePick.show(this, date, "날짜") { d ->
                date = d
                showDate()
            }
        }

        // 아이콘 칩
        for (em in Palette.emojis) {
            val chip = layoutInflater.inflate(R.layout.chip_emoji, emojiGroup, false) as Chip
            chip.text = em
            chip.isCheckable = true
            chip.isCheckedIconVisible = false
            chip.isChecked = existing?.emoji?.trim() == em
            chip.setOnClickListener { emojiInput.setText(if (chip.isChecked) em else "") }
            emojiGroup.addView(chip)
        }

        Swatches.fill(colorRow, Palette.colors, color, true) { color = it }

        // 프로젝트 고르기
        val projects = Store.loadProjects(this)
        val names = listOf("없음") + projects.map { "${it.emoji.trim()} ${it.name}".trim() }
        projectInput.setSimpleItems(names.toTypedArray())
        var projectIndex = projects.indexOfFirst { it.id == (existing?.projectId ?: 0L) } + 1
        projectInput.setText(names[projectIndex], false)
        projectInput.setOnItemClickListener { _, _, pos, _ -> projectIndex = pos }

        val del = v.findViewById<Button>(R.id.btn_delete)
        if (existing != null) {
            del.visibility = View.VISIBLE
            del.setOnClickListener {
                items.removeAll { it.id == existing.id }
                persist()
                sheet.dismiss()
            }
        }

        v.findViewById<Button>(R.id.btn_save).setOnClickListener {
            val title = titleInput.text.toString().trim()
            if (title.isEmpty()) {
                titleLayout.error = "내용을 입력해 주세요"
                return@setOnClickListener
            }
            val type = when {
                isDday() -> ItemType.DDAY
                isEvent() -> ItemType.EVENT
                else -> ItemType.TODO
            }
            val emoji = emojiInput.text.toString().trim()
            val yearly = type == ItemType.DDAY && yearlySwitch.isChecked
            val project = projects.getOrNull(projectIndex - 1)
            val projectId = project?.id ?: 0L
            // 프로젝트 일정인데 색을 안 골랐으면 프로젝트 색을 따라감
            val finalColor = if (color < 0 && project != null) project.color else color
            val finalTime = if (type == ItemType.EVENT) time else ""
            val item = existing?.copy(
                date = date, title = title, type = type, color = finalColor,
                emoji = emoji, yearly = yearly, projectId = projectId, time = finalTime,
            ) ?: Item(System.currentTimeMillis(), date, title, type, false, finalColor, emoji, yearly, projectId, finalTime)
            if (!yearly && YearMonth.from(date) != ym) ym = YearMonth.from(date)
            replace(item)
            sheet.dismiss()
        }
        sheet.show()
    }

    // ---------- 메뉴: 달 이동 / 백업 / 복원 / 이월 ----------
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_carry)?.isChecked = Store.carryOver(this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_prev -> { ym = ym.minusMonths(1); selected = null; refresh() }
            R.id.action_next -> { ym = ym.plusMonths(1); selected = null; refresh() }
            R.id.action_today -> goToday()
            R.id.action_backup -> {
                val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_TITLE, "달력위젯_백업_${LocalDate.now()}.zip")
                @Suppress("DEPRECATION")
                startActivityForResult(i, REQ_BACKUP)
            }
            R.id.action_restore -> {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                @Suppress("DEPRECATION")
                startActivityForResult(i, REQ_RESTORE)
            }
            R.id.action_carry -> {
                val v = !Store.carryOver(this)
                Store.setCarryOver(this, v)
                item.isChecked = v
                WidgetRenderer.updateAll(this)
                refresh()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    @Deprecated("단순 구현")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        if (requestCode == REQ_BACKUP) {
            Thread {
                val ok = Backup.export(this, uri)
                runOnUiThread { toast(if (ok) "백업 파일을 저장했어요" else "백업에 실패했어요") }
            }.start()
        } else if (requestCode == REQ_RESTORE) {
            MaterialAlertDialogBuilder(this)
                .setTitle("백업에서 복원")
                .setMessage("지금 있는 일정·프로젝트·꾸미기 설정이 백업 내용으로 바뀌어요. 계속할까요?")
                .setPositiveButton("복원") { _, _ ->
                    Thread {
                        val ok = Backup.import(this, uri)
                        runOnUiThread {
                            if (ok) {
                                items = Store.load(this)
                                widgetTheme = Store.loadTheme(this)
                                WidgetRenderer.updateAll(this)
                                refresh()
                                toast("복원했어요")
                            } else {
                                toast("백업 파일을 읽지 못했어요")
                            }
                        }
                    }.start()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- 목록 ----------
    private inner class RowAdapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1
        override fun isEnabled(position: Int) = rows[position] is Row.EntryRow

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val r = rows[position]
            if (r is Row.Header) {
                val v = convertView ?: layoutInflater.inflate(R.layout.row_header, parent, false)
                v.findViewById<TextView>(R.id.header_text).text = r.text
                return v
            }
            val entry = (r as Row.EntryRow).e
            val view = convertView ?: layoutInflater.inflate(R.layout.item_row, parent, false)
            val item = entry.item
            val ci = Palette.itemColor(item, widgetTheme)
            val check = view.findViewById<CheckBox>(R.id.row_check)
            val badge = view.findViewById<TextView>(R.id.row_badge)
            val title = view.findViewById<TextView>(R.id.row_title)
            val date = view.findViewById<TextView>(R.id.row_date)

            title.text = Fmt.titleWithEmoji(item)
            val parts = mutableListOf<String>()
            if (entry.overdue) parts.add("밀림")
            parts.add(Fmt.longDate(entry.date) + (if (item.time.isNotBlank()) " ${item.time}" else ""))
            if (item.yearly) parts.add("매년")
            projectNames[item.projectId]?.let { parts.add(it) }
            date.text = parts.joinToString(" · ")
            date.setTextColor(
                if (entry.overdue) getColor(R.color.sunday)
                else com.google.android.material.color.MaterialColors.getColor(date, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            check.setOnCheckedChangeListener(null)

            if (item.type != ItemType.TODO) {
                check.visibility = View.GONE
                badge.visibility = View.VISIBLE
                badge.text = if (item.type == ItemType.DDAY) Fmt.dday(entry.date, LocalDate.now()) else "일정"
                val pill = GradientDrawable()
                pill.cornerRadius = 10 * resources.displayMetrics.density
                pill.setColor(ci)
                badge.background = pill
                badge.setTextColor(Palette.onColor(ci, widgetTheme))
                title.alpha = if (Store.finished(entry, LocalDate.now())) 0.45f else 1f
            } else {
                badge.visibility = View.GONE
                check.visibility = View.VISIBLE
                check.buttonTintList = ColorStateList.valueOf(ci)
                check.isChecked = item.done
                title.alpha = if (item.done) 0.45f else 1f
                check.setOnCheckedChangeListener { _, checked -> replace(item.copy(done = checked)) }
            }
            return view
        }
    }
}
