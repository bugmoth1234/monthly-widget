package dev.monthly.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth

/** 위젯 배경과 스티커를 그림 한 장으로 합성합니다. */
object Decor {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val stickerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun stickerDir(ctx: Context) = File(ctx.filesDir, "stickers").also { it.mkdirs() }
    fun bgDir(ctx: Context) = File(ctx.filesDir, "backgrounds").also { it.mkdirs() }
    fun headerDir(ctx: Context) = File(ctx.filesDir, "headers").also { it.mkdirs() }

    /** 그 달에 쓸 제목 배경 파일 이름 (그 달 전용 > 공통 > 없음) */
    fun headerFile(theme: Theme, ym: YearMonth): String? =
        theme.headerMonthly[ym.toString()] ?: theme.headerImage.ifEmpty { null }

    fun renderHeader(ctx: Context, theme: Theme, ym: YearMonth, w: Int, h: Int, radius: Float): Bitmap? {
        val name = headerFile(theme, ym) ?: return null
        val photo = load(File(headerDir(ctx), name)) ?: return null
        if (w <= 0 || h <= 0) return null
        val fill = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(fill)
        val s = maxOf(w / photo.width.toFloat(), h / photo.height.toFloat())
        val m = Matrix()
        m.postScale(s, s)
        m.postTranslate((w - photo.width * s) / 2f, (h - photo.height * s) / 2f)
        c.drawBitmap(photo, m, stickerPaint)
        c.drawColor(Color.argb(theme.headerDim.coerceIn(0, 90) * 255 / 100, 0, 0, 0))
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = BitmapShader(fill, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        Canvas(out).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, p)
        fill.recycle()
        return out
    }

    fun load(file: File): Bitmap? {
        if (!file.exists()) return null
        val key = file.absolutePath + ":" + file.lastModified()
        cache.get(key)?.let { return it }
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        cache.put(key, bmp)
        return bmp
    }

    fun sticker(ctx: Context, s: Sticker): Bitmap? = load(File(stickerDir(ctx), s.file))

    /** 갤러리 이미지를 줄여서 앱 안에 복사. 스티커는 PNG(투명 유지), 배경은 JPEG. */
    fun importImage(ctx: Context, uri: Uri, dest: File, maxSide: Int, png: Boolean): Boolean {
        return try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false

            val rotation = try {
                cr.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } ?: 0
            } catch (e: Exception) {
                0
            }

            val m = Matrix()
            val big = maxOf(bmp.width, bmp.height)
            if (big > maxSide) {
                val s = maxSide.toFloat() / big
                m.postScale(s, s)
            }
            if (rotation != 0) m.postRotate(rotation.toFloat())
            if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)

            FileOutputStream(dest).use {
                bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** u = 1dp의 픽셀 수. bleed만큼 안쪽에 카드를 그리고 바깥은 투명하게 둡니다. */
    fun renderBackground(ctx: Context, theme: Theme, w: Int, h: Int, u: Float): Bitmap {
        val radius = 22f * u
        val inset = theme.bleed.coerceIn(0, 60) * u
        val fill = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(fill)
        val photo = if (theme.bgMode == 2 && theme.bgImage.isNotEmpty()) load(File(bgDir(ctx), theme.bgImage)) else null
        if (photo != null) {
            val s = maxOf(w / photo.width.toFloat(), h / photo.height.toFloat())
            val m = Matrix()
            m.postScale(s, s)
            m.postTranslate((w - photo.width * s) / 2f, (h - photo.height * s) / 2f)
            c.drawBitmap(photo, m, stickerPaint)
            c.drawColor(Color.argb(theme.dim.coerceIn(0, 90) * 255 / 100, 0, 0, 0))
        } else {
            val base = if (theme.bgMode == 1) Palette.bg(theme.bgColor) else ctx.getColor(R.color.widget_bg)
            c.drawColor(base or 0xFF000000.toInt())
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = BitmapShader(fill, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        p.alpha = theme.bgAlpha.coerceIn(0, 100) * 255 / 100
        val oc = Canvas(out)
        oc.drawRoundRect(RectF(inset, inset, w - inset, h - inset), radius, radius, p)
        fill.recycle()

        // 테두리: 1 = 얇은 선, 2 = 바느질 같은 스티치 점선
        if (theme.border > 0) {
            val accent = Palette.accentColor(theme)
            val bp = Paint(Paint.ANTI_ALIAS_FLAG)
            bp.style = Paint.Style.STROKE
            bp.strokeWidth = 1.6f * u
            if (theme.border == 2) {
                bp.color = (accent and 0x00FFFFFF) or (0xCC shl 24)
                bp.strokeCap = Paint.Cap.ROUND
                bp.pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f * u, 4f * u), 0f)
                val s = 5f * u
                oc.drawRoundRect(RectF(inset + s, inset + s, w - inset - s, h - inset - s), radius - s, radius - s, bp)
            } else {
                bp.color = (accent and 0x00FFFFFF) or (0x99 shl 24)
                val s = 0.8f * u
                oc.drawRoundRect(RectF(inset + s, inset + s, w - inset - s, h - inset - s), radius, radius, bp)
            }
        }
        return out
    }

    fun renderStickers(ctx: Context, stickers: List<Sticker>, w: Int, h: Int): Bitmap? {
        if (stickers.isEmpty()) return null
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        for (s in stickers) {
            val bmp = sticker(ctx, s) ?: continue
            drawSticker(c, bmp, s, w.toFloat(), h.toFloat())
        }
        return out
    }

    fun stickerMatrix(bmp: Bitmap, s: Sticker, w: Float, h: Float): Matrix {
        val scale = s.w * w / bmp.width
        val m = Matrix()
        m.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        m.postScale(scale, scale)
        m.postRotate(s.rot)
        m.postTranslate(s.cx * w, s.cy * h)
        return m
    }

    fun drawSticker(c: Canvas, bmp: Bitmap, s: Sticker, w: Float, h: Float) {
        c.drawBitmap(bmp, stickerMatrix(bmp, s, w, h), stickerPaint)
    }
}
