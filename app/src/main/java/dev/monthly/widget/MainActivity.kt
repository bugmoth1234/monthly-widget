package dev.monthly.widget

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.view.Menu
import android.view.MenuItem
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : Activity() {

    companion object {
        const val EXTRA_MONTH = "month"
        private const val REQ_BACKUP = 21
        private const val REQ_RESTORE = 22
        private const val MENU_BACKUP = 1
        private const val MENU_RESTORE = 2
        private const val MENU_CARRY = 3
    }

    private var ym: YearMonth = YearMonth.now()
    private var items: MutableList<Item> = mutableListOf()
    private var widgetTheme = Theme()
    private var shown: List<Entry> = emptyList()

    private lateinit var monthTitle: TextView
    private lateinit var adapter: ItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        readMonthExtra(intent)

        monthTitle = findViewById(R.id.month_title)
        val list = findViewById<ListView>(R.id.list)
        adapter = ItemAdapter()
        list.adapter = adapter
        list.emptyView = findViewById(R.id.empty)
        list.setOnItemClickListener { _, _, pos, _ -> showEditor(shown[pos].item) }

        findViewById<Button>(R.id.btn_prev).setOnClickListener { ym = ym.minusMonths(1); refresh() }
        findViewById<Button>(R.id.btn_next).setOnClickListener { ym = ym.plusMonths(1); refresh() }
        monthTitle.setOnClickListener { ym = YearMonth.now(); refresh() }
        findViewById<Button>(R.id.btn_add).setOnClickListener { showEditor(null) }
        findViewById<Button>(R.id.btn_projects).setOnClickListener {
            startActivity(Intent(this, ProjectsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_decor).setOnClickListener {
            startActivity(Intent(this, DecorActivity::class.java))
        }
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
    }

    private fun refresh() {
        monthTitle.text = Fmt.month(ym)
        shown = Store.entries(items, ym, LocalDate.now(), Store.carryOver(this))
        adapter.notifyDataSetChanged()
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

    private fun showEditor(existing: Item?) {
        val v = layoutInflater.inflate(R.layout.dialog_item, null)
        val titleInput = v.findViewById<EditText>(R.id.input_title)
        val dateBtn = v.findViewById<Button>(R.id.input_date)
        val typeGroup = v.findViewById<RadioGroup>(R.id.type_group)
        val rbTodo = v.findViewById<RadioButton>(R.id.type_todo)
        val rbDday = v.findViewById<RadioButton>(R.id.type_dday)
        val yearlyBox = v.findViewById<CheckBox>(R.id.input_yearly)
        val emojiInput = v.findViewById<EditText>(R.id.input_emoji)
        val emojiRow = v.findViewById<LinearLayout>(R.id.emoji_row)
        val colorRow = v.findViewById<LinearLayout>(R.id.color_row)

        val today = LocalDate.now()
        var date: LocalDate = existing?.date
            ?: if (ym == YearMonth.from(today)) today else ym.atDay(1)
        var color = existing?.color ?: -1

        if (existing != null) {
            titleInput.setText(existing.title)
            titleInput.setSelection(existing.title.length)
            emojiInput.setText(existing.emoji)
            yearlyBox.isChecked = existing.yearly
            if (existing.type == ItemType.DDAY) rbDday.isChecked = true else rbTodo.isChecked = true
        }
        yearlyBox.visibility = if (rbDday.isChecked) View.VISIBLE else View.GONE
        typeGroup.setOnCheckedChangeListener { _, _ ->
            yearlyBox.visibility = if (rbDday.isChecked) View.VISIBLE else View.GONE
        }

        dateBtn.text = "📅  " + Fmt.longDate(date)
        dateBtn.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                date = LocalDate.of(y, m + 1, d)
                dateBtn.text = "📅  " + Fmt.longDate(date)
            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }

        // 아이콘 빠른 선택
        val density = resources.displayMetrics.density
        val choices = listOf("✕") + Palette.emojis.toList()
        for (em in choices) {
            val t = TextView(this)
            t.text = em
            t.textSize = 22f
            t.gravity = Gravity.CENTER
            t.layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt())
            t.setOnClickListener { emojiInput.setText(if (em == "✕") "" else em) }
            emojiRow.addView(t)
        }

        Swatches.fill(colorRow, Palette.colors, color, true) { color = it }

        // 프로젝트 고르기
        val projects = Store.loadProjects(this)
        val spinner = v.findViewById<Spinner>(R.id.input_project)
        val names = listOf("없음") + projects.map { "${it.emoji.trim()} ${it.name}".trim() }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        val pIndex = projects.indexOfFirst { it.id == (existing?.projectId ?: 0L) }
        spinner.setSelection(if (pIndex >= 0) pIndex + 1 else 0)

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "새 일정" else "일정 수정")
            .setView(v)
            .setPositiveButton("저장", null)
            .setNegativeButton("취소", null)
        if (existing != null) {
            builder.setNeutralButton("삭제") { _, _ ->
                items.removeAll { it.id == existing.id }
                persist()
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim()
                if (title.isEmpty()) {
                    titleInput.error = "내용을 입력해 주세요"
                    return@setOnClickListener
                }
                val type = if (rbDday.isChecked) ItemType.DDAY else ItemType.TODO
                val emoji = emojiInput.text.toString().trim()
                val yearly = type == ItemType.DDAY && yearlyBox.isChecked
                val sel = spinner.selectedItemPosition
                val project = if (sel > 0) projects[sel - 1] else null
                val projectId = project?.id ?: 0L
                // 프로젝트 일정인데 색을 안 골랐으면 프로젝트 색을 따라감
                val finalColor = if (color < 0 && project != null) project.color else color
                val item = existing?.copy(date = date, title = title, type = type, color = finalColor, emoji = emoji, yearly = yearly, projectId = projectId)
                    ?: Item(System.currentTimeMillis(), date, title, type, false, finalColor, emoji, yearly, projectId)
                if (!yearly && YearMonth.from(date) != ym && type == ItemType.TODO) ym = YearMonth.from(date)
                replace(item)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // ---------- 메뉴: 백업 / 복원 / 이월 ----------
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_BACKUP, 0, "백업 파일 만들기")
        menu.add(0, MENU_RESTORE, 1, "백업에서 복원하기")
        menu.add(0, MENU_CARRY, 2, "못 한 일 이번 달로 이월").setCheckable(true).isChecked = Store.carryOver(this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_BACKUP -> {
                val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_TITLE, "이번달위젯_백업_${LocalDate.now()}.zip")
                startActivityForResult(i, REQ_BACKUP)
            }
            MENU_RESTORE -> {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                startActivityForResult(i, REQ_RESTORE)
            }
            MENU_CARRY -> {
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
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        if (requestCode == REQ_BACKUP) {
            Thread {
                val ok = Backup.export(this, uri)
                runOnUiThread { toast(if (ok) "백업 파일을 저장했어요" else "백업에 실패했어요") }
            }.start()
        } else if (requestCode == REQ_RESTORE) {
            AlertDialog.Builder(this)
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

    private inner class ItemAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int) = shown[position].item.id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_row, parent, false)
            val entry = shown[position]
            val item = entry.item
            val ci = Palette.itemColor(item, widgetTheme)
            val check = view.findViewById<CheckBox>(R.id.row_check)
            val badge = view.findViewById<TextView>(R.id.row_badge)
            val title = view.findViewById<TextView>(R.id.row_title)
            val date = view.findViewById<TextView>(R.id.row_date)

            title.text = Fmt.titleWithEmoji(item)
            date.text = (if (entry.overdue) "밀림 · " else "") + Fmt.longDate(entry.date) + (if (item.yearly) "  · 매년" else "")
            date.setTextColor(getColor(if (entry.overdue) R.color.sunday else R.color.text_secondary))
            check.setOnCheckedChangeListener(null)

            if (item.type == ItemType.DDAY) {
                check.visibility = View.GONE
                badge.visibility = View.VISIBLE
                badge.text = Fmt.dday(entry.date, LocalDate.now())
                val pill = android.graphics.drawable.GradientDrawable()
                pill.cornerRadius = 8 * resources.displayMetrics.density
                pill.setColor(ci)
                badge.background = pill
                title.alpha = 1f
            } else {
                badge.visibility = View.GONE
                check.visibility = View.VISIBLE
                check.buttonTintList = ColorStateList.valueOf(ci)
                check.isChecked = item.done
                title.alpha = if (item.done) 0.4f else 1f
                check.setOnCheckedChangeListener { _, checked -> replace(item.copy(done = checked)) }
            }
            return view
        }
    }
}
