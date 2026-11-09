package studio.pyreels.app.engine.render

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import androidx.core.content.res.ResourcesCompat
import studio.pyreels.app.R

/** Bundled typefaces: one Arabic font (Cairo) and one monospace font (JetBrains Mono). */
class Fonts(context: Context) {
    val arabic: Typeface = ResourcesCompat.getFont(context, R.font.cairo_regular) ?: Typeface.DEFAULT
    val arabicSemi: Typeface = ResourcesCompat.getFont(context, R.font.cairo_semibold) ?: Typeface.DEFAULT_BOLD
    val arabicBold: Typeface = ResourcesCompat.getFont(context, R.font.cairo_bold) ?: Typeface.DEFAULT_BOLD
    val mono: Typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE
    val monoBold: Typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_bold) ?: Typeface.MONOSPACE

    companion object {
        @Volatile private var instance: Fonts? = null
        fun get(context: Context): Fonts = instance ?: synchronized(this) { instance ?: Fonts(context.applicationContext).also { instance = it } }
    }
}

/**
 * Cached text layout (StaticLayout gives HarfBuzz shaping + ICU bidi: correct Arabic, and mixed
 * Arabic/English runs). Layouts are cached so the render hot path does not allocate per frame.
 */
class TextKit(private val fonts: Fonts) {

    enum class Face { REGULAR, SEMI, BOLD, MONO, MONO_BOLD }

    private data class Key(
        val text: CharSequence, val width: Int, val size: Float, val color: Int, val face: Face,
        val align: Layout.Alignment, val rtl: Int, val highlightWord: Int, val highlightColor: Int, val spacing: Float,
    )

    private val cache = object : LinkedHashMap<Key, StaticLayout>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, StaticLayout>?) = size > 400
    }
    private val paints = HashMap<Triple<Face, Float, Int>, TextPaint>()

    fun paint(face: Face, size: Float, color: Int): TextPaint = paints.getOrPut(Triple(face, size, color)) {
        TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = when (face) {
                Face.REGULAR -> fonts.arabic; Face.SEMI -> fonts.arabicSemi; Face.BOLD -> fonts.arabicBold
                Face.MONO -> fonts.mono; Face.MONO_BOLD -> fonts.monoBold
            }
            textSize = size
            this.color = color
        }
    }

    /**
     * @param rtl 1 = first-strong with RTL default (Arabic chat), 0 = forced LTR (code), 2 = first-strong LTR default.
     * @param highlightWord word index to colour (karaoke), -1 for none.
     */
    fun layout(
        text: CharSequence, width: Int, size: Float, color: Int, face: Face = Face.REGULAR,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, rtl: Int = 1,
        highlightWord: Int = -1, highlightColor: Int = 0, spacing: Float = 1.12f,
    ): StaticLayout {
        val key = Key(text, width.coerceAtLeast(1), size, color, face, align, rtl, highlightWord, highlightColor, spacing)
        cache[key]?.let { return it }
        val content: CharSequence = if (highlightWord >= 0) karaoke(text.toString(), highlightWord, highlightColor) else text
        val dir: TextDirectionHeuristic = when (rtl) {
            0 -> TextDirectionHeuristics.LTR
            1 -> TextDirectionHeuristics.FIRSTSTRONG_RTL
            else -> TextDirectionHeuristics.FIRSTSTRONG_LTR
        }
        val l = StaticLayout.Builder.obtain(content, 0, content.length, paint(face, size, color), width.coerceAtLeast(1))
            .setAlignment(align)
            .setTextDirection(dir)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .build()
        cache[key] = l
        return l
    }

    private fun karaoke(text: String, word: Int, color: Int): CharSequence {
        val s = SpannableString(text)
        var idx = 0
        var start = -1
        var i = 0
        while (i < text.length) {
            while (i < text.length && text[i].isWhitespace()) i++
            val ws = i
            while (i < text.length && !text[i].isWhitespace()) i++
            if (ws < i) {
                if (idx == word) { start = ws; s.setSpan(ForegroundColorSpan(color), ws, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); break }
                idx++
            }
        }
        if (start < 0) return text
        return s
    }

    fun maxLineWidth(l: StaticLayout): Float {
        var w = 0f
        for (i in 0 until l.lineCount) w = maxOf(w, l.getLineWidth(i))
        return w
    }

    fun clear() = cache.clear()
}
