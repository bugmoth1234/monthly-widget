package dev.monthly.widget

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

/** 프로젝트 목록 한 줄 그리기 (목록 화면과 상세 화면이 같이 씀) */
private fun bindProjectSummary(
    ctx: Context, p: Project, items: List<Item>,
    icon: TextView?, name: TextView, dday: TextView?, bar: LinearProgressIndicator, meta: TextView,
) {
    val today = LocalDate.now()
    val col = Palette.color(p.color)
    val pct = Store.percent(p, items)
    val (done, total) = Store.progress(p, items)
    icon?.text = p.emoji.trim().ifEmpty { "📁" }
    name.text = if (icon == null) "${p.emoji.trim()} ${p.name}".trim() else p.name
    dday?.text = Fmt.dday(p.deadline, today)
    dday?.setTextColor(col)
    bar.setProgressCompat(pct, false)
    bar.setIndicatorColor(col)
    bar.trackColor = (col and 0x00FFFFFF) or (0x33 shl 24)
    val prog = if (p.manual) "$done / $total ${p.unit}".trim() else "체크리스트 $done / $total"
    meta.text = "${Fmt.longDate(p.deadline)} 마감 · $prog · $pct%"
    if (dday == null) meta.text = "${Fmt.longDate(p.deadline)} 마감 · ${Fmt.dday(p.deadline, today)}"
}

class ProjectsActivity : AppCompatActivity() {
    private var projects: List<Project> = emptyList()
    private var items: List<Item> = emptyList()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_projects)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        Nav.setup(this, R.id.nav_projects)
        val list = findViewById<ListView>(R.id.project_list)
        adapter = object : BaseAdapter() {
            override fun getCount() = projects.size
            override fun getItem(position: Int): Any = projects[position]
            override fun getItemId(position: Int) = projects[position].id
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_project_row, parent, false)
                bindProjectSummary(
                    this@ProjectsActivity, projects[position], items,
                    v.findViewById(R.id.pr_icon), v.findViewById(R.id.pr_name), v.findViewById(R.id.pr_dday),
                    v.findViewById(R.id.pr_progress), v.findViewById(R.id.pr_meta),
                )
                return v
            }
        }
        list.adapter = adapter
        list.emptyView = findViewById(R.id.project_empty)
        list.setOnItemClickListener { _, _, pos, _ ->
            startActivity(Intent(this, ProjectDetailActivity::class.java).putExtra(ProjectDetailActivity.EXTRA_ID, projects[pos].id))
        }
        findViewById<View>(R.id.fab_new_project).setOnClickListener {
            ProjectEditor.show(this, null) { p ->
                startActivity(Intent(this, ProjectDetailActivity::class.java).putExtra(ProjectDetailActivity.EXTRA_ID, p.id))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val today = LocalDate.now()
        projects = Store.loadProjects(this).sortedWith(compareBy<Project>({ it.deadline.isBefore(today) }, { it.deadline }))
        items = Store.load(this)
        adapter.notifyDataSetChanged()
    }
}

class ProjectDetailActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ID = "project_id"
    }

    private var pid = 0L
    private var project: Project? = null
    private var items: MutableList<Item> = mutableListOf()
    private var tasks: List<Item> = emptyList()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_detail)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        pid = intent.getLongExtra(EXTRA_ID, 0)

        val list = findViewById<ListView>(R.id.pd_list)
        adapter = object : BaseAdapter() {
            override fun getCount() = tasks.size
            override fun getItem(position: Int): Any = tasks[position]
            override fun getItemId(position: Int) = tasks[position].id
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_row, parent, false)
                val t = tasks[position]
                val check = v.findViewById<CheckBox>(R.id.row_check)
                v.findViewById<View>(R.id.row_badge).visibility = View.GONE
                val title = v.findViewById<TextView>(R.id.row_title)
                val date = v.findViewById<TextView>(R.id.row_date)
                title.text = t.title
                title.alpha = if (t.done) 0.4f else 1f
                val late = t.type == ItemType.TODO && !t.done && t.date.isBefore(LocalDate.now())
                date.text = (if (late) "밀림 · " else "") + Fmt.longDate(t.date)
                date.setTextColor(getColor(if (late) R.color.sunday else R.color.text_secondary))
                check.setOnCheckedChangeListener(null)
                val badge = v.findViewById<TextView>(R.id.row_badge)
                val col = Palette.color(project?.color ?: 0)
                if (t.type == ItemType.TODO) {
                    check.visibility = View.VISIBLE
                    check.isChecked = t.done
                    check.buttonTintList = ColorStateList.valueOf(col)
                    check.setOnCheckedChangeListener { _, c -> updateTask(t.copy(done = c)) }
                } else {
                    // 일정·디데이는 체크 없이 배지만
                    check.visibility = View.GONE
                    badge.visibility = View.VISIBLE
                    badge.text = if (t.type == ItemType.DDAY) Fmt.dday(t.date, LocalDate.now()) else (t.time.ifBlank { "일정" })
                    val pill = android.graphics.drawable.GradientDrawable()
                    pill.cornerRadius = 10 * resources.displayMetrics.density
                    pill.setColor(col)
                    badge.background = pill
                    badge.setTextColor(Palette.onColor(col, Store.loadTheme(this@ProjectDetailActivity)))
                }
                return v
            }
        }
        list.adapter = adapter
        list.emptyView = findViewById(R.id.pd_empty)
        list.setOnItemClickListener { _, _, pos, _ -> editTask(tasks[pos]) }

        findViewById<View>(R.id.pd_add).setOnClickListener { editTask(null) }
        findViewById<Button>(R.id.pd_minus).setOnClickListener { bump(-1) }
        findViewById<Button>(R.id.pd_plus).setOnClickListener { bump(+1) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.project_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_edit -> project?.let { p -> ProjectEditor.show(this, p) { reload() } }
            R.id.action_delete -> confirmDelete()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val p = Store.loadProjects(this).firstOrNull { it.id == pid }
        if (p == null) {
            finish()
            return
        }
        project = p
        items = Store.load(this)
        tasks = items.filter { it.projectId == pid }.sortedWith(compareBy<Item>({ it.date }, { it.id }))
        title = p.name
        bindProjectSummary(
            this, p, items, null, findViewById(R.id.pd_title), null,
            findViewById(R.id.pd_progress), findViewById(R.id.pd_meta),
        )
        val (done, total) = Store.progress(p, items)
        findViewById<TextView>(R.id.pd_pct).text =
            "진행률 ${Store.percent(p, items)}%" + (if (p.manual) "" else "  ($done / $total 완료)")
        findViewById<View>(R.id.pd_manual_row).visibility = if (p.manual) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.pd_manual_text).text = "$done / $total ${p.unit}".trim()
        adapter.notifyDataSetChanged()
    }

    private fun bump(delta: Int) {
        val p = project ?: return
        val np = p.copy(manualDone = (p.manualDone + delta).coerceIn(0, p.manualTotal))
        val all = Store.loadProjects(this)
        val i = all.indexOfFirst { it.id == p.id }
        if (i >= 0) all[i] = np
        Store.saveProjects(this, all)
        WidgetRenderer.updateAll(this)
        reload()
    }

    private fun updateTask(t: Item) {
        val i = items.indexOfFirst { it.id == t.id }
        if (i >= 0) items[i] = t else items.add(t)
        Store.save(this, items)
        WidgetRenderer.updateAll(this)
        reload()
    }

    private fun editTask(existing: Item?) {
        val p = project ?: return
        val v = layoutInflater.inflate(R.layout.dialog_task, null)
        val titleInput = v.findViewById<EditText>(R.id.task_title)
        val dateBtn = v.findViewById<Button>(R.id.task_date)
        var date = existing?.date ?: p.deadline
        if (existing != null) {
            titleInput.setText(existing.title)
            titleInput.setSelection(existing.title.length)
        }
        dateBtn.text = Fmt.longDate(date)
        dateBtn.setOnClickListener {
            DatePick.show(this, date, "날짜") { d ->
                date = d
                dateBtn.text = Fmt.longDate(date)
            }
        }
        val b = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "할 일 추가" else "할 일 수정")
            .setView(v)
            .setPositiveButton("저장", null)
            .setNegativeButton("취소", null)
        if (existing != null) {
            b.setNeutralButton("삭제") { _, _ ->
                items.removeAll { it.id == existing.id }
                Store.save(this, items)
                WidgetRenderer.updateAll(this)
                reload()
            }
        }
        val dlg = b.create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t = titleInput.text.toString().trim()
                if (t.isEmpty()) {
                    titleInput.error = "내용을 입력해 주세요"
                    return@setOnClickListener
                }
                val item = existing?.copy(title = t, date = date)
                    ?: Item(System.currentTimeMillis(), date, t, ItemType.TODO, false, p.color, "", false, p.id)
                updateTask(item)
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    private fun confirmDelete() {
        val p = project ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("프로젝트 삭제")
            .setMessage("'${p.name}'와 체크리스트 ${tasks.size}개가 모두 지워져요.")
            .setPositiveButton("삭제") { _, _ ->
                Store.saveProjects(this, Store.loadProjects(this).filter { it.id != p.id })
                Store.save(this, Store.load(this).filter { it.projectId != p.id })
                WidgetRenderer.updateAll(this)
                finish()
            }
            .setNegativeButton("취소", null)
            .show()
    }
}

/** 프로젝트 만들기 / 편집 창 */
object ProjectEditor {
    private val EMOJIS = arrayOf("📕", "📘", "📗", "📙", "✒️", "🖋️", "📚", "⚔️", "🗺️", "🐉", "🎲", "🎨", "✨", "📦", "🏝️", "🌙")

    fun show(act: AppCompatActivity, existing: Project?, onSaved: (Project) -> Unit) {
        val v = act.layoutInflater.inflate(R.layout.dialog_project, null)
        val nameIn = v.findViewById<EditText>(R.id.pj_in_name)
        val deadlineBtn = v.findViewById<Button>(R.id.pj_in_deadline)
        val emojiIn = v.findViewById<EditText>(R.id.pj_in_emoji)
        val emojiRow = v.findViewById<LinearLayout>(R.id.pj_in_emoji_row)
        val colorRow = v.findViewById<LinearLayout>(R.id.pj_in_color_row)
        val modeGroup = v.findViewById<RadioGroup>(R.id.pj_in_mode)
        val rbManual = v.findViewById<RadioButton>(R.id.rb_mode_manual)
        val manualBox = v.findViewById<View>(R.id.pj_in_manual_box)
        val totalIn = v.findViewById<EditText>(R.id.pj_in_total)
        val unitIn = v.findViewById<EditText>(R.id.pj_in_unit)
        val tplGroup = v.findViewById<RadioGroup>(R.id.pj_in_template)

        val today = LocalDate.now()
        var deadline = existing?.deadline ?: today.plusMonths(1)
        var color = existing?.color ?: 0

        if (existing != null) {
            nameIn.setText(existing.name)
            emojiIn.setText(existing.emoji)
            if (existing.manual) rbManual.isChecked = true
            totalIn.setText(existing.manualTotal.toString())
            unitIn.setText(existing.unit)
            // 템플릿은 새로 만들 때만
            tplGroup.visibility = View.GONE
            v.findViewById<View>(R.id.pj_in_template_label).visibility = View.GONE
            v.findViewById<View>(R.id.pj_in_template_hint).visibility = View.GONE
        }
        manualBox.visibility = if (rbManual.isChecked) View.VISIBLE else View.GONE
        modeGroup.setOnCheckedChangeListener { _, _ ->
            manualBox.visibility = if (rbManual.isChecked) View.VISIBLE else View.GONE
        }

        fun showDeadline() { deadlineBtn.text = "마감: " + Fmt.longDate(deadline) }
        showDeadline()
        deadlineBtn.setOnClickListener {
            DatePick.show(act, deadline, "마감일") { d ->
                deadline = d
                showDeadline()
            }
        }

        val density = act.resources.displayMetrics.density
        for (em in EMOJIS) {
            val t = TextView(act)
            t.text = em
            t.textSize = 22f
            t.gravity = Gravity.CENTER
            t.layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt())
            t.setOnClickListener { emojiIn.setText(em) }
            emojiRow.addView(t)
        }
        Swatches.fill(colorRow, Palette.colors, color, false) { color = it }

        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle(if (existing == null) "새 프로젝트" else "프로젝트 편집")
            .setView(v)
            .setPositiveButton("저장", null)
            .setNegativeButton("취소", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameIn.text.toString().trim()
                if (name.isEmpty()) {
                    nameIn.error = "이름을 입력해 주세요"
                    return@setOnClickListener
                }
                val manual = rbManual.isChecked
                val total = (totalIn.text.toString().toIntOrNull() ?: 10).coerceAtLeast(1)
                val p = existing?.copy(
                    name = name, emoji = emojiIn.text.toString().trim(), color = color, deadline = deadline,
                    manual = manual, manualTotal = total,
                    manualDone = (existing?.manualDone ?: 0).coerceAtMost(total), unit = unitIn.text.toString().trim(),
                ) ?: Project(
                    System.currentTimeMillis(), name, emojiIn.text.toString().trim(), color, deadline,
                    "", manual, 0, total, unitIn.text.toString().trim(),
                )

                val projects = Store.loadProjects(act)
                val i = projects.indexOfFirst { it.id == p.id }
                if (i >= 0) projects[i] = p else projects.add(p)
                Store.saveProjects(act, projects)

                val items = Store.load(act)
                var changed = false
                if (existing == null) {
                    val steps = when (tplGroup.checkedRadioButtonId) {
                        R.id.rb_tpl_zine -> Templates.ZINE
                        R.id.rb_tpl_commu -> Templates.COMMU
                        else -> null
                    }
                    if (steps != null) {
                        items.addAll(Templates.build(steps, p, today, System.currentTimeMillis() + 1))
                        changed = true
                    }
                } else if (existing.color != p.color) {
                    // 프로젝트 색을 바꾸면 그 색을 쓰던 할 일도 같이
                    for (k in items.indices) {
                        if (items[k].projectId == p.id && items[k].color == existing.color) {
                            items[k] = items[k].copy(color = p.color)
                            changed = true
                        }
                    }
                }
                if (changed) Store.save(act, items)
                WidgetRenderer.updateAll(act)
                dlg.dismiss()
                onSaved(p)
            }
        }
        dlg.show()
    }
}
