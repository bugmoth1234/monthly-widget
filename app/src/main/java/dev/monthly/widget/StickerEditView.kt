package dev.monthly.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot

/** 미리보기 위에서 스티커를 옮기고(한 손가락), 크기·각도를 바꾸는(두 손가락) 뷰. */
class StickerEditView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    var stickers: MutableList<Sticker> = mutableListOf()
    var selectedId: String? = null
    var onChanged: (() -> Unit)? = null

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = 0xFF5B6CF0.toInt()
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private var lastX = 0f
    private var lastY = 0f
    private var startDist = 0f
    private var startAngle = 0f
    private var startW = 0f
    private var startRot = 0f
    private var moved = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        for (s in stickers) {
            val bmp = Decor.sticker(context, s) ?: continue
            Decor.drawSticker(canvas, bmp, s, w, h)
            if (s.id == selectedId) {
                val pts = floatArrayOf(
                    0f, 0f, bmp.width.toFloat(), 0f,
                    bmp.width.toFloat(), bmp.height.toFloat(), 0f, bmp.height.toFloat(),
                )
                Decor.stickerMatrix(bmp, s, w, h).mapPoints(pts)
                val p = Path()
                p.moveTo(pts[0], pts[1])
                p.lineTo(pts[2], pts[3])
                p.lineTo(pts[4], pts[5])
                p.lineTo(pts[6], pts[7])
                p.close()
                canvas.drawPath(p, boxPaint)
            }
        }
    }

    private fun hit(x: Float, y: Float): Sticker? {
        val w = width.toFloat()
        val h = height.toFloat()
        for (s in stickers.asReversed()) {
            val bmp = Decor.sticker(context, s) ?: continue
            val inv = Matrix()
            if (!Decor.stickerMatrix(bmp, s, w, h).invert(inv)) continue
            val p = floatArrayOf(x, y)
            inv.mapPoints(p)
            if (p[0] >= 0f && p[1] >= 0f && p[0] <= bmp.width && p[1] <= bmp.height) return s
        }
        return null
    }

    private fun selectedIndex(): Int = stickers.indexOfFirst { it.id == selectedId }

    private fun dist(e: MotionEvent): Float = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    private fun angle(e: MotionEvent): Float =
        Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()

    private fun beginPinch(e: MotionEvent) {
        val i = selectedIndex()
        if (i < 0 || e.pointerCount < 2) return
        startDist = dist(e)
        startAngle = angle(e)
        startW = stickers[i].w
        startRot = stickers[i].rot
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val s = hit(e.x, e.y)
                selectedId = s?.id
                invalidate()
                if (s == null) return false // 빈 곳은 화면 스크롤에 양보
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = e.x
                lastY = e.y
                moved = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> beginPinch(e)
            MotionEvent.ACTION_MOVE -> {
                val i = selectedIndex()
                if (i < 0 || width == 0 || height == 0) return true
                val s = stickers[i]
                if (e.pointerCount >= 2) {
                    if (startDist > 10f) {
                        val nw = (startW * dist(e) / startDist).coerceIn(0.05f, 1.6f)
                        stickers[i] = s.copy(w = nw, rot = startRot + angle(e) - startAngle)
                        moved = true
                        invalidate()
                    }
                } else {
                    val dx = (e.x - lastX) / width
                    val dy = (e.y - lastY) / height
                    stickers[i] = s.copy(
                        cx = (s.cx + dx).coerceIn(-0.2f, 1.2f),
                        cy = (s.cy + dy).coerceIn(-0.2f, 1.2f),
                    )
                    lastX = e.x
                    lastY = e.y
                    moved = true
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val keep = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(keep)
                lastY = e.getY(keep)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (moved) onChanged?.invoke()
                moved = false
            }
        }
        return true
    }
}
