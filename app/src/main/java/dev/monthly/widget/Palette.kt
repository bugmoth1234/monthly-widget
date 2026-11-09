package dev.monthly.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

object Palette {
    val colors = intArrayOf(
        0xFF5B6CF0.toInt(), // 0 인디고
        0xFFF06292.toInt(), // 1 핑크
        0xFFE5484D.toInt(), // 2 빨강
        0xFFFF8A3D.toInt(), // 3 주황
        0xFFF5B700.toInt(), // 4 노랑
        0xFF43B581.toInt(), // 5 초록
        0xFF26A69A.toInt(), // 6 청록
        0xFF3E9BF5.toInt(), // 7 하늘
        0xFF9B6CF0.toInt(), // 8 보라
        0xFFC77DFF.toInt(), // 9 라일락
        0xFF8D6E63.toInt(), // 10 갈색
        0xFF546E7A.toInt(), // 11 회청
    )



    val bgColors = intArrayOf(
        0xFFFFFFFF.toInt(), // 흰색
        0xFFFFF6E5.toInt(), // 크림
        0xFFFFE4EC.toInt(), // 연분홍
        0xFFEDE7FF.toInt(), // 라벤더
        0xFFE3F7EE.toInt(), // 민트
        0xFFE3F0FF.toInt(), // 하늘
        0xFFFFF9C4.toInt(), // 레몬
        0xFF2B2B33.toInt(), // 차콜
        0xFF14213D.toInt(), // 네이비
        0xFF000000.toInt(), // 검정
    )

    val emojis = arrayOf("🎂", "💖", "⭐", "✨", "🎫", "💿", "📚", "🎬", "🎮", "🎁", "🍰", "📌", "🎤", "📦")

    fun idx(i: Int) = i.coerceIn(0, colors.size - 1)
    fun color(i: Int) = colors[idx(i)]
    fun bg(i: Int) = bgColors[i.coerceIn(0, bgColors.size - 1)]

    /** 달력 칸 배경으로 고를 수 있는 색: 배경 색 + 포인트 팔레트 */
    val calBgChoices: IntArray get() = bgColors + colors

    /** 색 칸 위에 올리는 글자색 (오늘 동그라미, 디데이 배지) */
    fun onColor(bg: Int, theme: Theme): Int = when (theme.onAccentMode) {
        1 -> Color.WHITE
        2 -> 0xFF222222.toInt()
        else -> if (Color.luminance(bg) > 0.6f) 0xFF222222.toInt() else Color.WHITE
    }

    /** 바탕 위에 색 글씨로 쓸 때, 너무 연해서 안 보이면 기본 글자색으로 */
    fun readable(color: Int, ink: Ink): Int {
        val bright = Color.luminance(color) > 0.8f
        val inkDark = Color.luminance(ink.primary) < 0.5f
        return if (bright && inkDark) ink.primary else color
    }

    /** 포인트 색: 직접 고른 색 > 팔레트 색 */
    fun accentColor(theme: Theme): Int =
        if (theme.customAccent != 0) theme.customAccent else color(theme.accent)

    /** 일정의 실제 색: 지정한 색 > 디데이는 핑크 > 할 일은 포인트 색 */
    fun itemColor(item: Item, theme: Theme): Int = when {
        item.color >= 0 -> color(item.color)
        item.type == ItemType.DDAY -> colors[1]
        else -> accentColor(theme)
    }
}

data class Ink(val primary: Int, val secondary: Int, val muted: Int, val divider: Int, val sun: Int, val sat: Int)

object Inks {
    private val DARK = Ink(
        0xFF222222.toInt(), 0xFF6B6B6B.toInt(), 0xFFB0B0B0.toInt(), 0x22000000,
        0xFFE5484D.toInt(), 0xFF3E7BFA.toInt(),
    )
    private val LIGHT = Ink(
        0xFFF7F7F7.toInt(), 0xFFD0D0D6.toInt(), 0xFF8A8A90.toInt(), 0x44FFFFFF,
        0xFFFF8589.toInt(), 0xFF85AEFF.toInt(),
    )

    fun of(ctx: Context, t: Theme): Ink = when (t.textMode) {
        1 -> DARK
        2 -> LIGHT
        else -> when (t.bgMode) {
            1 -> if (Color.luminance(Palette.bg(t.bgColor)) < 0.35f) LIGHT else DARK
            2 -> if (t.dim >= 30) LIGHT else DARK
            else -> Ink(
                ctx.getColor(R.color.text_primary), ctx.getColor(R.color.text_secondary),
                ctx.getColor(R.color.text_muted), ctx.getColor(R.color.divider),
                ctx.getColor(R.color.sunday), ctx.getColor(R.color.saturday),
            )
        }
    }
}

/** 색 고르기 동그라미 줄. index -1은 "기본". */
object Swatches {
    fun fill(
        row: LinearLayout, colors: IntArray, selected: Int, allowDefault: Boolean,
        defaultLabel: String = "기본", onPick: (Int) -> Unit,
    ) {
        val ctx = row.context
        row.removeAllViews()
        val d = ctx.resources.displayMetrics.density
        val size = (34 * d).toInt()
        val margin = (5 * d).toInt()
        val indices = (if (allowDefault) listOf(-1) else emptyList()) + colors.indices.toList()
        for (i in indices) {
            val v = TextView(ctx)
            v.gravity = Gravity.CENTER
            v.textSize = 10f
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(if (i < 0) Color.TRANSPARENT else colors[i])
            bg.setStroke((1 * d).toInt(), ctx.getColor(R.color.text_muted))
            if (i < 0) {
                v.text = defaultLabel
                v.setTextColor(ctx.getColor(R.color.text_secondary))
            }
            if (i == selected) bg.setStroke((3 * d).toInt(), ctx.getColor(R.color.text_primary))
            v.background = bg
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins(margin, margin, margin, margin)
            v.layoutParams = lp
            v.setOnClickListener {
                fill(row, colors, i, allowDefault, defaultLabel, onPick)
                onPick(i)
            }
            row.addView(v)
        }
    }
}
