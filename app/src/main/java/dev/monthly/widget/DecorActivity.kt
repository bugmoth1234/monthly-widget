package dev.monthly.widget

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.Toast
import java.io.File
import java.util.UUID

class DecorActivity : Activity() {

    companion object {
        private const val REQ_STICKER = 11
        private const val REQ_BG = 12
        private const val REQ_HEADER_ALL = 13
        private const val REQ_HEADER_MONTH = 14
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
        findViewById<Button>(R.id.btn_bg_photo).setOnClickListener { pick(REQ_BG) }
        findViewById<Button>(R.id.btn_reset).setOnClickListener { confirmReset() }

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

        Swatches.fill(findViewById<LinearLayout>(R.id.accent_row), Palette.colors, decor.accent, false) {
            decor = decor.copy(accent = it)
            save()
        }
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
                Decor.renderBackground(this, decor, w, h, 22f * resources.displayMetrics.density),
            )
            WidgetRenderer.applyHeader(this, rv, decor, w, 1f)
            rv.setViewVisibility(R.id.sticker_layer, View.GONE) // 스티커는 편집 뷰가 직접 그림
            host.removeAllViews()
            host.addView(
                rv.apply(this, host),
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
        decor = decor.copy(stickers = editor.stickers.toList())
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
        File(Decor.stickerDir(this), s.file).delete()
        decor = decor.copy(stickers = editor.stickers.toList())
        save(refresh = false)
        editor.invalidate()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("꾸미기 초기화")
            .setMessage("스티커, 배경 사진, 색 설정이 모두 지워져요. 일정은 그대로예요.")
            .setPositiveButton("초기화") { _, _ ->
                Decor.stickerDir(this).listFiles()?.forEach { it.delete() }
                Decor.bgDir(this).listFiles()?.forEach { it.delete() }
                Decor.headerDir(this).listFiles()?.forEach { it.delete() }
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
