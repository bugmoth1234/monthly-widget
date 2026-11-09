package dev.monthly.widget

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.UUID

class DecorActivity : AppCompatActivity() {

    companion object {
        private const val REQ_STICKER = 11
        private const val REQ_BG = 12
        private const val REQ_HEADER_ALL = 13
        private const val REQ_HEADER_MONTH = 14
        private const val REQ_TITLE_FONT = 15
        private const val REQ_BODY_FONT = 16
    }

    private var decor = Theme()
    private var items: List<Item> = emptyList()
    private var syncing = false

    private lateinit var frame: FrameLayout
    private lateinit var host: FrameLayout
    private lateinit var editor: StickerEditView
    private lateinit var bgGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_decor)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        Nav.setup(this, R.id.nav_decor)
        setupSections()
        decor = Store.loadTheme(this)
        items = Store.load(this)

        frame = findViewById(R.id.preview_frame)
        host = findViewById(R.id.preview_host)
        editor = findViewById(R.id.sticker_edit)
        editor.stickers = decor.stickers.toMutableList()
        editor.onChanged = {
            decor = decor.copy(stickers = editor.stickers.toList())
            save(refresh = false)
        }

        // 미리보기를 실제 위젯 비율에 맞춤
        val outer = findViewById<FrameLayout>(R.id.preview_outer)
        outer.post {
            val (wd, hd) = Store.widgetSize(this)
            val density = resources.displayMetrics.density
            val avail = outer.width - outer.paddingLeft - outer.paddingRight
            val wPx = minOf(avail, (wd * density).toInt())
            val hPx = (wPx * hd / wd.toFloat()).toInt()
            val lp = frame.layoutParams
            lp.width = wPx
            lp.height = hPx
            frame.layoutParams = lp
        }
        frame.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) frame.post { refreshPreview() }
        }

        findViewById<Button>(R.id.btn_add_sticker).setOnClickListener { pick(REQ_STICKER) }
        findViewById<Button>(R.id.btn_del_sticker).setOnClickListener { deleteSelected() }
        findViewById<Button>(R.id.btn_sticker_front).setOnClickListener { moveSelected(+1) }
        setupStickerSize()
        setupBleed()
        findViewById<Button>(R.id.btn_sticker_back).setOnClickListener { moveSelected(-1) }
        findViewById<Button>(R.id.btn_bg_photo).setOnClickListener { pick(REQ_BG) }
        findViewById<Button>(R.id.btn_reset).setOnClickListener { confirmReset() }

        // 글꼴
        updateFontLabels()
        findViewById<Button>(R.id.btn_title_font).setOnClickListener { pickFile(REQ_TITLE_FONT) }
        findViewById<Button>(R.id.btn_body_font).setOnClickListener { pickFile(REQ_BODY_FONT) }
        findViewById<Button>(R.id.btn_title_font_clear).setOnClickListener { setFont("", title = true) }
        findViewById<Button>(R.id.btn_body_font_clear).setOnClickListener { setFont("", title = false) }
        val scaleGroup = findViewById<RadioGroup>(R.id.font_scale)
        scaleGroup.check(
            when {
                decor.bodyFontScale < 100 -> R.id.rb_scale_s
                decor.bodyFontScale > 100 -> R.id.rb_scale_l
                else -> R.id.rb_scale_m
            }
        )
        scaleGroup.setOnCheckedChangeListener { _, id ->
            val v = when (id) {
                R.id.rb_scale_s -> 90
                R.id.rb_scale_l -> 115
                else -> 100
            }
            decor = decor.copy(bodyFontScale = v)
            save()
        }

        // 제목 배경
        val ym = WidgetRenderer.displayMonth(this)
        findViewById<Button>(R.id.btn_header_month).text = "${ym.monthValue}월만 다른 이미지"
        findViewById<Button>(R.id.btn_header_all).setOnClickListener { pick(REQ_HEADER_ALL) }
        findViewById<Button>(R.id.btn_header_month).setOnClickListener { pick(REQ_HEADER_MONTH) }
        findViewById<Button>(R.id.btn_header_clear).setOnClickListener { clearHeader() }
        setupSeek(R.id.seek_header_dim, decor.headerDim) { decor = decor.copy(headerDim = it) }
        val sizeGroup = findViewById<RadioGroup>(R.id.header_size)
        sizeGroup.check(if (decor.headerLarge) R.id.rb_header_large else R.id.rb_header_normal)
        sizeGroup.setOnCheckedChangeListener { _, id ->
            decor = decor.copy(headerLarge = id == R.id.rb_header_large)
            save()
        }

        setupAccentPicker()
        setupStyle()
        setupPresets()
        Swatches.fill(findViewById<LinearLayout>(R.id.bgcolor_row), Palette.bgColors, decor.bgColor, false) {
            decor = decor.copy(bgColor = it, bgMode = 1)
            syncBgRadio()
            save()
        }

        bgGroup = findViewById(R.id.bg_mode)
        syncBgRadio()
        bgGroup.setOnCheckedChangeListener { _, id ->
            if (syncing) return@setOnCheckedChangeListener
            val mode = when (id) {
                R.id.rb_bg_color -> 1
                R.id.rb_bg_photo -> 2
                else -> 0
            }
            decor = decor.copy(bgMode = mode)
            save()
            if (mode == 2 && decor.bgImage.isEmpty()) pick(REQ_BG)
        }

        val textGroup = findViewById<RadioGroup>(R.id.text_mode)
        textGroup.check(
            when (decor.textMode) {
                1 -> R.id.rb_text_dark
                2 -> R.id.rb_text_light
                else -> R.id.rb_text_auto
            }
        )
        textGroup.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.rb_text_dark -> 1
                R.id.rb_text_light -> 2
                else -> 0
            }
            decor = decor.copy(textMode = mode)
            save()
        }

        setupSeek(R.id.seek_alpha, decor.bgAlpha) { decor = decor.copy(bgAlpha = it) }
        setupSeek(R.id.seek_dim, decor.dim) { decor = decor.copy(dim = it) }

        val caption = findViewById<EditText>(R.id.input_caption)
        caption.setText(decor.caption)
        caption.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                decor = decor.copy(caption = s?.toString()?.trim() ?: "")
                refreshPreview()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        save(refresh = false)
    }

    private fun syncBgRadio() {
        syncing = true
        bgGroup.check(
            when (decor.bgMode) {
                1 -> R.id.rb_bg_color
                2 -> R.id.rb_bg_photo
                else -> R.id.rb_bg_default
            }
        )
        syncing = false
    }

    private fun setupSeek(id: Int, value: Int, apply: (Int) -> Unit) {
        val bar = findViewById<SeekBar>(id)
        bar.progress = value
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    apply(progress)
                    refreshPreview()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { save() }
        })
    }

    private fun save(refresh: Boolean = true) {
        Store.saveTheme(this, decor)
        WidgetRenderer.updateAll(this)
        if (refresh) refreshPreview()
    }

    private fun refreshPreview() {
        val w = frame.width
        val h = frame.height
        if (w <= 0 || h <= 0) return
        try {
            val rv = WidgetRenderer.buildContent(this, items, decor)
            rv.setImageViewBitmap(
                R.id.bg_layer,
                Decor.renderBackground(this, decor, w, h, resources.displayMetrics.density),
            )
            WidgetRenderer.applyHeader(this, rv, decor, w, 1f)
            WidgetRenderer.applyFonts(this, rv, decor, items, w, h, 1f)
            rv.setViewVisibility(R.id.sticker_layer, View.GONE) // 스티커는 편집 뷰가 직접 그림
            host.removeAllViews()
            host.addView(
                rv.apply(applicationContext, host), // 앱 테마 영향 없이 위젯과 똑같이
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        } catch (e: Exception) {
            // 미리보기 실패는 무시
        }
        editor.invalidate()
    }

    private fun pick(req: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(Intent.createChooser(intent, "이미지 고르기"), req)
        } catch (e: Exception) {
            toast("이미지를 고를 수 있는 앱이 없어요")
        }
    }

    @Deprecated("Activity result API를 쓰지 않는 단순 구현")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            if (requestCode == REQ_BG && decor.bgImage.isEmpty() && decor.bgMode == 2) {
                decor = decor.copy(bgMode = 0)
                syncBgRadio()
                save()
            }
            return
        }
        toast("불러오는 중…")
        Thread {
            if (requestCode == REQ_STICKER) {
                val name = "st_" + System.currentTimeMillis() + ".png"
                val ok = Decor.importImage(this, uri, File(Decor.stickerDir(this), name), 600, true)
                runOnUiThread { if (ok) addSticker(name) else toast("이미지를 불러오지 못했어요") }
            } else if (requestCode == REQ_TITLE_FONT || requestCode == REQ_BODY_FONT) {
                val name = Fonts.import(this, uri)
                runOnUiThread {
                    if (name != null) setFont(name, title = requestCode == REQ_TITLE_FONT)
                    else toast("글꼴 파일을 읽지 못했어요 (.ttf / .otf 파일인지 확인해 주세요)")
                }
            } else if (requestCode == REQ_HEADER_ALL || requestCode == REQ_HEADER_MONTH) {
                val name = "hd_" + System.currentTimeMillis() + ".jpg"
                val ok = Decor.importImage(this, uri, File(Decor.headerDir(this), name), 1200, false)
                runOnUiThread { if (ok) setHeader(name, requestCode == REQ_HEADER_MONTH) else toast("이미지를 불러오지 못했어요") }
            } else {
                val name = "bg_" + System.currentTimeMillis() + ".jpg"
                val ok = Decor.importImage(this, uri, File(Decor.bgDir(this), name), 1400, false)
                runOnUiThread {
                    if (ok) {
                        if (decor.bgImage.isNotEmpty()) File(Decor.bgDir(this), decor.bgImage).delete()
                        decor = decor.copy(bgMode = 2, bgImage = name)
                        syncBgRadio()
                        save()
                    } else {
                        toast("이미지를 불러오지 못했어요")
                    }
                }
            }
        }.start()
    }

    private fun pickFile(req: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(Intent.createChooser(intent, "글꼴 파일 고르기"), req)
        } catch (e: Exception) {
            toast("파일을 고를 수 있는 앱이 없어요")
        }
    }

    private fun setFont(name: String, title: Boolean) {
        val old = if (title) decor.titleFont else decor.bodyFont
        decor = if (title) decor.copy(titleFont = name) else decor.copy(bodyFont = name)
        if (old.isNotEmpty() && old != decor.titleFont && old != decor.bodyFont) File(Fonts.dir(this), old).delete()
        updateFontLabels()
        save()
    }

    private fun updateFontLabels() {
        findViewById<TextView>(R.id.title_font_label).text = "제목 글꼴: " + Fonts.label(decor.titleFont)
        findViewById<TextView>(R.id.body_font_label).text = "글자 글꼴 (요일·날짜·목록): " + Fonts.label(decor.bodyFont)
    }

    private fun setHeader(name: String, onlyThisMonth: Boolean) {
        if (onlyThisMonth) {
            val key = WidgetRenderer.displayMonth(this).toString()
            val old = decor.headerMonthly[key]
            decor = decor.copy(headerMonthly = decor.headerMonthly + (key to name))
            if (old != null) deleteHeaderFileIfUnused(old)
        } else {
            val old = decor.headerImage
            decor = decor.copy(headerImage = name)
            if (old.isNotEmpty()) deleteHeaderFileIfUnused(old)
        }
        save()
    }

    private fun clearHeader() {
        val key = WidgetRenderer.displayMonth(this).toString()
        val monthly = decor.headerMonthly[key]
        when {
            monthly != null -> {
                decor = decor.copy(headerMonthly = decor.headerMonthly - key)
                deleteHeaderFileIfUnused(monthly)
                toast("이 달 전용 이미지를 지웠어요")
            }
            decor.headerImage.isNotEmpty() -> {
                val old = decor.headerImage
                decor = decor.copy(headerImage = "")
                deleteHeaderFileIfUnused(old)
                toast("모든 달 공통 이미지를 지웠어요")
            }
            else -> toast("지울 제목 이미지가 없어요")
        }
        save()
    }

    private fun deleteHeaderFileIfUnused(name: String) {
        if (decor.headerImage == name || decor.headerMonthly.containsValue(name)) return
        File(Decor.headerDir(this), name).delete()
    }

    private fun addSticker(file: String) {
        val s = Sticker(UUID.randomUUID().toString(), file, 0.5f, 0.45f, 0.35f, 0f)
        editor.stickers.add(s)
        editor.selectedId = s.id
        editor.onSelection?.invoke(s)
        decor = decor.copy(stickers = editor.stickers.toList())
        save(refresh = false)
        editor.invalidate()
    }

    // ---------- 포인트 색: 팔레트 + 색상/채도/밝기 ----------
    private lateinit var hueBar: SeekBar
    private lateinit var satBar: SeekBar
    private lateinit var valBar: SeekBar

    private fun fillAccentRow() {
        val selected = if (decor.customAccent != 0) -2 else decor.accent // -2 = 팔레트에서 선택 안 됨
        Swatches.fill(findViewById<LinearLayout>(R.id.accent_row), Palette.colors, selected, false) {
            decor = decor.copy(accent = it, customAccent = 0)
            syncColorBars(Palette.colors[it])
            save()
        }
    }

    private fun currentHsv() = floatArrayOf(hueBar.progress.toFloat(), satBar.progress / 100f, valBar.progress / 100f)

    private fun syncColorBars(color: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hueBar.progress = hsv[0].toInt()
        satBar.progress = (hsv[1] * 100).toInt()
        valBar.progress = (hsv[2] * 100).toInt()
        paintColorBars()
    }

    private fun paintColorBars() {
        val d = resources.displayMetrics.density
        val hsv = currentHsv()
        fun bar(colors: IntArray): android.graphics.drawable.GradientDrawable {
            val g = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, colors)
            g.cornerRadius = 14 * d
            return g
        }
        hueBar.background = bar(IntArray(7) { android.graphics.Color.HSVToColor(floatArrayOf(it * 60f % 360f, 1f, 1f)) })
        satBar.background = bar(intArrayOf(
            android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 0f, maxOf(hsv[2], 0.15f))),
            android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, maxOf(hsv[2], 0.15f))),
        ))
        valBar.background = bar(intArrayOf(
            android.graphics.Color.BLACK,
            android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f)),
        ))
        val dot = android.graphics.drawable.GradientDrawable()
        dot.shape = android.graphics.drawable.GradientDrawable.OVAL
        dot.setColor(Palette.accentColor(decor))
        dot.setStroke((1 * d).toInt(), getColor(R.color.text_muted))
        findViewById<FrameLayout>(R.id.accent_preview).background = dot
        findViewById<TextView>(R.id.hue_label).text = "색상 ${hsv[0].toInt()}°"
        findViewById<TextView>(R.id.sat_label).text = "채도 ${satBar.progress}%"
        findViewById<TextView>(R.id.val_label).text = "밝기(명도) ${valBar.progress}%"
    }

    private fun setupAccentPicker() {
        hueBar = findViewById(R.id.seek_hue)
        satBar = findViewById(R.id.seek_sat)
        valBar = findViewById(R.id.seek_val)
        fillAccentRow()
        syncColorBars(Palette.accentColor(decor))
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val wasPalette = decor.customAccent == 0
                decor = decor.copy(customAccent = android.graphics.Color.HSVToColor(currentHsv()))
                if (wasPalette) fillAccentRow()
                paintColorBars()
                refreshPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { save() }
        }
        hueBar.setOnSeekBarChangeListener(listener)
        satBar.setOnSeekBarChangeListener(listener)
        valBar.setOnSeekBarChangeListener(listener)
    }

    // ---------- 접고 펴는 묶음 ----------
    private fun setupSections() {
        val box = findViewById<LinearLayout>(R.id.sections)
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        for (i in 0 until box.childCount) {
            val card = box.getChildAt(i) as? ViewGroup ?: continue
            val inner = card.getChildAt(0) as? ViewGroup ?: continue
            if (inner.childCount < 2) continue
            val header = inner.getChildAt(0)
            val body = inner.getChildAt(1)
            val arrow = header.findViewWithTag<View>("arrow")
            val key = "decor_sec_$i"
            var open = prefs.getBoolean(key, i < 2) // 처음엔 추천 테마·스티커만 펼침
            fun apply() {
                body.visibility = if (open) View.VISIBLE else View.GONE
                arrow?.rotation = if (open) 180f else 0f
            }
            apply()
            header.setOnClickListener {
                open = !open
                apply()
                prefs.edit().putBoolean(key, open).apply()
            }
        }
    }

    // ---------- 스타일 ----------
    private fun setupStyle() {
        fun radio(groupId: Int, ids: IntArray, current: Int, apply: (Int) -> Unit) {
            val g = findViewById<RadioGroup>(groupId)
            g.check(ids[current.coerceIn(0, ids.size - 1)])
            g.setOnCheckedChangeListener { _, id ->
                val i = ids.indexOf(id)
                if (i >= 0) {
                    apply(i)
                    save()
                }
            }
        }
        radio(R.id.title_style, intArrayOf(R.id.rb_title_ko, R.id.rb_title_en, R.id.rb_title_big), decor.titleStyle) {
            decor = decor.copy(titleStyle = it)
        }
        radio(R.id.weekday_lang, intArrayOf(R.id.rb_week_ko, R.id.rb_week_en), if (decor.weekdayEn) 1 else 0) {
            decor = decor.copy(weekdayEn = it == 1)
        }
        radio(R.id.on_accent_mode, intArrayOf(R.id.rb_on_auto, R.id.rb_on_white, R.id.rb_on_black), decor.onAccentMode) {
            decor = decor.copy(onAccentMode = it)
        }
        setupCalendarBg()
        radio(R.id.border_style, intArrayOf(R.id.rb_border_none, R.id.rb_border_line, R.id.rb_border_stitch), decor.border) {
            decor = decor.copy(border = it)
        }
        val cb = findViewById<android.widget.CheckBox>(R.id.cb_highlight)
        cb.isChecked = decor.highlight
        cb.setOnCheckedChangeListener { _, checked ->
            decor = decor.copy(highlight = checked)
            save()
        }
        val cbp = findViewById<android.widget.CheckBox>(R.id.cb_projects)
        cbp.isChecked = decor.showProjects
        cbp.setOnCheckedChangeListener { _, checked ->
            decor = decor.copy(showProjects = checked)
            save()
        }
    }

    // ---------- 달력 칸 배경 ----------
    private fun setupCalendarBg() {
        val choices = Palette.calBgChoices
        val row = findViewById<LinearLayout>(R.id.calbg_row)
        val sel = if (decor.calBgColor == 0) -1 else choices.indexOf(decor.calBgColor)
        Swatches.fill(row, choices, sel, true, "없음") {
            decor = decor.copy(calBgColor = if (it < 0) 0 else choices[it])
            save()
        }
        val bar = findViewById<SeekBar>(R.id.seek_calbg_alpha)
        val label = findViewById<TextView>(R.id.calbg_alpha_label)
        bar.progress = decor.calBgAlpha
        label.text = "달력 칸 배경 진하기 ${decor.calBgAlpha}%"
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                decor = decor.copy(calBgAlpha = progress)
                label.text = "달력 칸 배경 진하기 ${progress}%"
                refreshPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { save() }
        })
    }

    // ---------- 추천 테마 ----------
    private class Preset(
        val name: String, val accent: Int, val bgColor: Int, val bgAlpha: Int,
        val textMode: Int, val border: Int, val titleStyle: Int, val weekdayEn: Boolean,
    )

    private val presets = listOf(
        Preset("라벤더 드림", 0xFF9B7BEA.toInt(), 3, 95, 0, 2, 2, true),
        Preset("딸기우유", 0xFFF06292.toInt(), 2, 95, 0, 2, 2, true),
        Preset("크림 다이어리", 0xFFB08968.toInt(), 1, 97, 0, 1, 0, false),
        Preset("민트 소다", 0xFF26A69A.toInt(), 4, 95, 0, 1, 1, true),
        Preset("레몬 스티커", 0xFFF5A300.toInt(), 6, 95, 0, 2, 2, false),
        Preset("미니멀 화이트", 0xFF222222.toInt(), 0, 100, 1, 0, 1, true),
        Preset("모노 나이트", 0xFFC77DFF.toInt(), 7, 92, 2, 0, 2, true),
        Preset("투명 글래스", 0xFFFFFFFF.toInt(), 0, 22, 2, 1, 1, true),
        Preset("크리스마스", 0xFFE5484D.toInt(), 8, 95, 2, 2, 2, true),
    )

    private fun setupPresets() {
        val row = findViewById<LinearLayout>(R.id.preset_row)
        val d = resources.displayMetrics.density
        for (pr in presets) {
            val bg = Palette.bg(pr.bgColor)
            val chip = TextView(this)
            chip.text = "●  " + pr.name
            chip.textSize = 13f
            val light = android.graphics.Color.luminance(bg) < 0.35f || pr.bgAlpha < 40
            chip.setTextColor(if (light) android.graphics.Color.WHITE else 0xFF222222.toInt())
            val span = android.text.SpannableString(chip.text)
            span.setSpan(android.text.style.ForegroundColorSpan(pr.accent), 0, 1, 0)
            chip.text = span
            chip.setPadding((14 * d).toInt(), (9 * d).toInt(), (14 * d).toInt(), (9 * d).toInt())
            val shape = android.graphics.drawable.GradientDrawable()
            shape.cornerRadius = 18 * d
            shape.setColor(if (pr.bgAlpha < 40) 0xFF3B5B7A.toInt() else bg)
            if (pr.border == 2) {
                shape.setStroke((1.5f * d).toInt(), pr.accent, 5 * d, 3 * d)
            } else {
                shape.setStroke((1 * d).toInt(), getColor(R.color.text_muted))
            }
            chip.background = shape
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, (8 * d).toInt(), 0)
            chip.layoutParams = lp
            chip.setOnClickListener {
                decor = decor.copy(
                    customAccent = pr.accent, bgMode = 1, bgColor = pr.bgColor, bgAlpha = pr.bgAlpha,
                    textMode = pr.textMode, border = pr.border, titleStyle = pr.titleStyle, weekdayEn = pr.weekdayEn,
                )
                Store.saveTheme(this, decor)
                WidgetRenderer.updateAll(this)
                recreate() // 아래 설정 화면도 새 값으로 다시 그림
            }
            row.addView(chip)
        }
    }

    // ---------- 카드 바깥 여백 ----------
    private fun setupBleed() {
        val bar = findViewById<SeekBar>(R.id.seek_bleed)
        val label = findViewById<TextView>(R.id.bleed_label)
        bar.progress = decor.bleed
        label.text = "카드 바깥 여백: ${decor.bleed}dp"
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                decor = decor.copy(bleed = progress)
                label.text = "카드 바깥 여백: ${progress}dp"
                refreshPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { save() }
        })
    }

    private fun setupStickerSize() {
        val bar = findViewById<SeekBar>(R.id.seek_sticker_size)
        val label = findViewById<TextView>(R.id.sticker_size_label)
        fun show(s: Sticker?) {
            if (s == null) {
                label.text = "선택한 스티커 크기 (스티커를 먼저 눌러 주세요)"
            } else {
                val pct = (s.w * 100).toInt()
                label.text = "선택한 스티커 크기: 위젯 너비의 $pct%"
                bar.progress = (pct - 5).coerceIn(0, bar.max)
            }
        }
        show(null)
        editor.onSelection = { show(it) }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (editor.selected() == null) return
                editor.setSelectedWidth((progress + 5) / 100f)
                label.text = "선택한 스티커 크기: 위젯 너비의 ${progress + 5}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {
                if (editor.selected() == null) toast("크기를 바꿀 스티커를 미리보기에서 먼저 눌러 주세요")
            }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                if (editor.selected() == null) return
                decor = decor.copy(stickers = editor.stickers.toList())
                save(refresh = false)
            }
        })
    }

    /** 겹치는 순서 바꾸기: 목록 뒤쪽일수록 위에 그려짐. dir +1 = 한 칸 앞으로, -1 = 한 칸 뒤로 */
    private fun moveSelected(dir: Int) {
        val list = editor.stickers
        val i = list.indexOfFirst { it.id == editor.selectedId }
        if (i < 0) {
            toast("순서를 바꿀 스티커를 미리보기에서 먼저 눌러 주세요")
            return
        }
        val j = i + dir
        if (j < 0 || j >= list.size) {
            toast(if (dir > 0) "이미 맨 앞이에요" else "이미 맨 뒤예요")
            return
        }
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
        decor = decor.copy(stickers = list.toList())
        save(refresh = false)
        editor.invalidate()
    }

    private fun deleteSelected() {
        val id = editor.selectedId
        val s = editor.stickers.firstOrNull { it.id == id }
        if (s == null) {
            toast("지울 스티커를 미리보기에서 먼저 눌러 주세요")
            return
        }
        editor.stickers.remove(s)
        editor.selectedId = null
        editor.onSelection?.invoke(null)
        File(Decor.stickerDir(this), s.file).delete()
        decor = decor.copy(stickers = editor.stickers.toList())
        save(refresh = false)
        editor.invalidate()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("꾸미기 초기화")
            .setMessage("스티커, 배경·제목 이미지, 글꼴, 색 설정이 모두 지워져요. 일정은 그대로예요.")
            .setPositiveButton("초기화") { _, _ ->
                Decor.stickerDir(this).listFiles()?.forEach { it.delete() }
                Decor.bgDir(this).listFiles()?.forEach { it.delete() }
                Decor.headerDir(this).listFiles()?.forEach { it.delete() }
                Fonts.dir(this).listFiles()?.forEach { it.delete() }
                decor = Theme()
                Store.saveTheme(this, decor)
                WidgetRenderer.updateAll(this)
                recreate()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
