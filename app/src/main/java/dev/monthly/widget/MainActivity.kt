package dev.monthly.widget

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.RadioButton
import android.widget.TextView
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : Activity() {

    companion object {
        const val EXTRA_MONTH = "month"
    }

    private var ym: YearMonth = YearMonth.now()
    private var items: MutableList<Item> = mutableListOf()
    private var shown: List<Item> = emptyList()

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
        list.setOnItemClickListener { _, _, pos, _ -> showEditor(shown[pos]) }

        findViewById<Button>(R.id.btn_prev).setOnClickListener { ym = ym.minusMonths(1); refresh() }
        findViewById<Button>(R.id.btn_next).setOnClickListener { ym = ym.plusMonths(1); refresh() }
        monthTitle.setOnClickListener { ym = YearMonth.now(); refresh() }
        findViewById<Button>(R.id.btn_add).setOnClickListener { showEditor(null) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readMonthExtra(intent)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        items = Store.load(this)
        refresh()
    }

    private fun readMonthExtra(intent: Intent?) {
        val m = intent?.getStringExtra(EXTRA_MONTH) ?: return
        ym = try { YearMonth.parse(m) } catch (e: Exception) { YearMonth.now() }
    }

    private fun refresh() {
        monthTitle.text = Fmt.month(ym)
        shown = Store.visibleItems(items, ym, LocalDate.now())
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
        val rbTodo = v.findViewById<RadioButton>(R.id.type_todo)
        val rbDday = v.findViewById<RadioButton>(R.id.type_dday)

        val today = LocalDate.now()
        var date: LocalDate = existing?.date
            ?: if (ym == YearMonth.from(today)) today else ym.atDay(1)

        if (existing != null) {
            titleInput.setText(existing.title)
            titleInput.setSelection(existing.title.length)
            if (existing.type == ItemType.DDAY) rbDday.isChecked = true else rbTodo.isChecked = true
        }
        dateBtn.text = "📅  " + Fmt.longDate(date)
        dateBtn.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                date = LocalDate.of(y, m + 1, d)
                dateBtn.text = "📅  " + Fmt.longDate(date)
            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }

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
                val item = existing?.copy(date = date, title = title, type = type)
                    ?: Item(System.currentTimeMillis(), date, title, type)
                if (YearMonth.from(date) != ym && type == ItemType.TODO) ym = YearMonth.from(date)
                replace(item)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private inner class ItemAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int) = shown[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_row, parent, false)
            val item = shown[position]
            val check = view.findViewById<CheckBox>(R.id.row_check)
            val badge = view.findViewById<TextView>(R.id.row_badge)
            val title = view.findViewById<TextView>(R.id.row_title)
            val date = view.findViewById<TextView>(R.id.row_date)

            title.text = item.title
            date.text = Fmt.longDate(item.date)
            check.setOnCheckedChangeListener(null)

            if (item.type == ItemType.DDAY) {
                check.visibility = View.GONE
                badge.visibility = View.VISIBLE
                badge.text = Fmt.dday(item.date, LocalDate.now())
                title.alpha = 1f
            } else {
                badge.visibility = View.GONE
                check.visibility = View.VISIBLE
                check.isChecked = item.done
                title.alpha = if (item.done) 0.4f else 1f
                check.setOnCheckedChangeListener { _, checked -> replace(item.copy(done = checked)) }
            }
            return view
        }
    }
}
