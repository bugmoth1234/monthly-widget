package dev.monthly.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.text.TextPaint
import android.text.TextUtils
import java.io.File
import java.io.FileOutputStream

/** 사용자가 고른 .ttf / .otf 글꼴 파일 관리와 글자 그리기. */
object Fonts {
    private val cache = HashMap<String, Typeface>()

    fun dir(ctx: Context) = File(ctx.filesDir, "fonts").also { it.mkdirs() }

    fun get(ctx: Context, name: String): Typeface? {
        if (name.isEmpty()) return null
        cache[name]?.let { return it }
        val f = File(dir(ctx), name)
        if (!f.exists()) return null
        return try {
            Typeface.createFromFile(f).also { cache[name] = it }
        } catch (e: Exception) {
            null
        }
    }

    /** 화면에 보여 줄 이름 (저장 이름 "f123_원래이름.ttf" → "원래이름.ttf") */
    fun label(name: String): String = if (name.isEmpty()) "기본" else name.substringAfter('_')

    /** 글꼴 파일을 앱 안으로 복사. 성공하면 저장 이름, 실패하면 null. */
    fun import(ctx: Context, uri: Uri): String? {
        var original = "font.ttf"
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) original = it.getString(0) ?: original
            }
        } catch (e: Exception) {
        }
        val safe = original.replace(Regex("[^\\w.가-힣-]"), "_")
        val name = "f" + System.currentTimeMillis() + "_" + safe
        val dest = File(dir(ctx), name)
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { input.copyTo(it) }
            } ?: return null
            val tf = Typeface.createFromFile(dest)
            if (tf == null || tf == Typeface.DEFAULT) {
                dest.delete()
                null
            } else {
                name
            }
        } catch (e: Exception) {
            dest.delete()
            null
        }
    }

    fun drawTitle(text: String, tf: Typeface, w: Int, h: Int, color: Int, size: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        p.typeface = tf
        p.color = color
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        // 글꼴이 넓으면 칸에 맞게 줄임
        val tw = p.measureText(text)
        if (tw > w * 0.96f) p.textSize = size * (w * 0.96f / tw)
        val fm = p.fontMetrics
        Canvas(bmp).drawText(text, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p)
        return bmp
    }

    fun ellipsize(text: String, p: TextPaint, width: Float): String =
        if (width <= 0f) "" else TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString()
}
