package studio.pyreels.app.engine.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.StaticLayout
import studio.pyreels.core.model.Palette
import studio.pyreels.core.model.Personas
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.Tokens
import studio.pyreels.core.model.castById

/**
 * Colours of the two in-video "apps" (a chat messenger and a code editor), derived from the
 * calm theme. Dark: blue-gray, never pure black. Light: warm paper.
 */
class Skin(val p: Palette) {
    private fun c(v: Long) = v.toInt()
    val dark = p.isDark
    // phone
    val bezel = if (dark) 0xFF0D1217.toInt() else 0xFF2A2F36.toInt()
    val bezelEdge = if (dark) 0xFF3A4654.toInt() else 0xFF59616C.toInt()
    val statusText = if (dark) 0xFFD8DEE4.toInt() else 0xFF1F2A30.toInt()
    // messenger
    val waHeader = if (dark) 0xFF1E2A33.toInt() else 0xFF2F6F62.toInt()
    val waHeaderText = if (dark) 0xFFE9EDEF.toInt() else 0xFFF7FAF8.toInt()
    val waHeaderSub = if (dark) 0xFF8D9AA3.toInt() else 0xFFD4E6E0.toInt()
    val waTyping = if (dark) 0xFF4CC9A4.toInt() else 0xFFE4F7EE.toInt()
    val waWall = if (dark) 0xFF111B21.toInt() else 0xFFEDE6DA.toInt()
    val waDoodle = if (dark) 0x0BFFFFFF else 0x0F000000
    val waIn = if (dark) 0xFF202C33.toInt() else 0xFFFFFFFF.toInt()
    val waOut = if (dark) 0xFF1F5247.toInt() else 0xFFD7F2CF.toInt()
    val waText = if (dark) 0xFFE9EDEF.toInt() else 0xFF1B2328.toInt()
    val waTime = if (dark) 0xFF8D9AA3.toInt() else 0xFF6F7A80.toInt()
    val waTick = if (dark) 0xFF53BDEB.toInt() else 0xFF3A9BD1.toInt()
    val waPill = if (dark) 0xFF1E2A33.toInt() else 0xFFFFFFFF.toInt()
    val waNotice = if (dark) 0xFF2A2F25.toInt() else 0xFFFCF4CB.toInt()
    val waNoticeText = if (dark) 0xFFE6D7A3.toInt() else 0xFF5E5427.toInt()
    val waInput = if (dark) 0xFF1E2A33.toInt() else 0xFFFFFFFF.toInt()
    val waBar = if (dark) 0xFF0F171C.toInt() else 0xFFE7E0D4.toInt()
    val waFab = if (dark) 0xFF2BA684.toInt() else 0xFF2F8F72.toInt()
    val waIcon = if (dark) 0xFF8D9AA3.toInt() else 0xFF6F7A80.toInt()
    // editor
    val edBar = if (dark) 0xFF232A33.toInt() else 0xFF2E3640.toInt()
    val edBarText = 0xFFE6E2D8.toInt()
    val edBg = c(p.codeBg)
    val edGutter = if (dark) 0xFF1B2129.toInt() else 0xFF262D36.toInt()
    val edSymbolBar = if (dark) 0xFF1A1F26.toInt() else 0xFF232A33.toInt()
    val kbBg = if (dark) 0xFF161B21.toInt() else 0xFF1F252C.toInt()
    val kbKey = if (dark) 0xFF2B323B.toInt() else 0xFF343C46.toInt()
    val kbKeyDark = if (dark) 0xFF222830.toInt() else 0xFF2A3139.toInt()
    val kbText = 0xFFDCE0E6.toInt()
    val fab = c(p.focus)
    val termBg = c(p.terminalBg)
}

/** Shared paints, paths and helpers. One instance per renderer; nothing allocated per frame. */
class Gfx(val text: TextKit, val skin: Skin, private val scenario: Scenario) {
    val p = skin.p
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val rect = RectF()
    val rect2 = RectF()
    val path = Path()

    fun c(v: Long) = v.toInt()
    fun alpha(color: Int, a: Float): Int = (color and 0x00FFFFFF) or ((((color ushr 24) and 0xFF) * a.coerceIn(0f, 1f)).toInt() shl 24)

    inline fun withAlpha(canvas: Canvas, a: Float, w: Float, h: Float, block: () -> Unit) {
        if (a >= 0.995f) { block(); return }
        if (a <= 0.005f) return
        val save = canvas.saveLayerAlpha(0f, 0f, w, h, (a * 255).toInt())
        block()
        canvas.restoreToCount(save)
    }

    /** Draws a layout so that its lines end at the right edge of [x, x+w] (RTL paragraphs). */
    fun drawRtl(canvas: Canvas, l: StaticLayout, x: Float, w: Float, y: Float) {
        canvas.save(); canvas.translate(x + w - l.width, y); l.draw(canvas); canvas.restore()
    }

    fun drawAt(canvas: Canvas, l: StaticLayout, x: Float, y: Float) {
        canvas.save(); canvas.translate(x, y); l.draw(canvas); canvas.restore()
    }

    fun persona(castId: String) = scenario.castById(castId)?.let { Personas.forCast(it) } ?: Personas.forLanguage(castId)
    fun accent(castId: String) = c(Tokens.accent(persona(castId), p))
    fun nameOf(castId: String) = scenario.castById(castId)?.name ?: castId

    fun avatar(canvas: Canvas, castId: String, cx: Float, cy: Float, r: Float, ring: Int? = null) {
        val persona = persona(castId)
        val accent = accent(castId)
        fill.color = if (skin.dark) blend(0xFF1B242C.toInt(), accent, 0.30f) else blend(0xFFFFFFFF.toInt(), accent, 0.28f)
        canvas.drawCircle(cx, cy, r, fill)
        stroke.color = ring ?: alpha(accent, 0.95f); stroke.strokeWidth = r * 0.09f
        canvas.drawCircle(cx, cy, r - stroke.strokeWidth / 2, stroke)
        val mono = persona.monogram
        val face = if (castId == "ahmed") TextKit.Face.BOLD else TextKit.Face.MONO_BOLD
        val tp = text.paint(face, r * when { mono.length > 2 -> 0.58f; mono.length == 2 -> 0.74f; else -> 0.95f }, if (skin.dark) accent else blend(accent, 0xFF000000.toInt(), 0.25f))
        tp.textAlign = Paint.Align.CENTER
        val fm = tp.fontMetrics
        canvas.drawText(mono, cx, cy - (fm.ascent + fm.descent) / 2, tp)
        tp.textAlign = Paint.Align.LEFT
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, s: Int) = (x shr s) and 0xFF
        fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    // ---------------------------------------------------------------- tiny vector icons

    fun iconBack(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int, pointRight: Boolean) {
        stroke.color = color; stroke.strokeWidth = s * 0.12f
        val d = if (pointRight) 1f else -1f
        canvas.drawLine(cx - d * s * 0.45f, cy, cx + d * s * 0.45f, cy, stroke)
        path.reset(); path.moveTo(cx + d * s * 0.05f, cy - s * 0.4f); path.lineTo(cx + d * s * 0.45f, cy); path.lineTo(cx + d * s * 0.05f, cy + s * 0.4f)
        canvas.drawPath(path, stroke)
    }

    fun iconVideo(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.color = color; stroke.strokeWidth = s * 0.1f
        rect.set(cx - s * 0.5f, cy - s * 0.3f, cx + s * 0.18f, cy + s * 0.3f); canvas.drawRoundRect(rect, s * 0.1f, s * 0.1f, stroke)
        path.reset(); path.moveTo(cx + s * 0.22f, cy - s * 0.08f); path.lineTo(cx + s * 0.5f, cy - s * 0.26f); path.lineTo(cx + s * 0.5f, cy + s * 0.26f); path.lineTo(cx + s * 0.22f, cy + s * 0.08f)
        canvas.drawPath(path, stroke)
    }

    fun iconPhone(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.color = color; stroke.strokeWidth = s * 0.11f
        path.reset()
        path.moveTo(cx - s * 0.38f, cy - s * 0.36f)
        path.quadTo(cx - s * 0.46f, cy + s * 0.05f, cx + s * 0.02f, cy + s * 0.4f)
        path.lineTo(cx + s * 0.2f, cy + s * 0.42f)
        path.lineTo(cx + s * 0.36f, cy + s * 0.2f)
        canvas.drawPath(path, stroke)
        canvas.drawLine(cx - s * 0.38f, cy - s * 0.36f, cx - s * 0.18f, cy - s * 0.4f, stroke)
    }

    fun iconDots(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.color = color
        for (i in -1..1) canvas.drawCircle(cx, cy + i * s * 0.32f, s * 0.08f, fill)
    }

    fun iconMenu(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.color = color; stroke.strokeWidth = s * 0.1f
        for (i in -1..1) canvas.drawLine(cx - s * 0.4f, cy + i * s * 0.28f, cx + s * 0.4f, cy + i * s * 0.28f, stroke)
    }

    fun iconSmile(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.color = color; stroke.strokeWidth = s * 0.09f
        canvas.drawCircle(cx, cy, s * 0.42f, stroke)
        fill.color = color
        canvas.drawCircle(cx - s * 0.15f, cy - s * 0.1f, s * 0.05f, fill); canvas.drawCircle(cx + s * 0.15f, cy - s * 0.1f, s * 0.05f, fill)
        rect.set(cx - s * 0.22f, cy - s * 0.12f, cx + s * 0.22f, cy + s * 0.24f)
        canvas.drawArc(rect, 20f, 140f, false, stroke)
    }

    fun iconClip(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.color = color; stroke.strokeWidth = s * 0.09f
        canvas.save(); canvas.rotate(-40f, cx, cy)
        rect.set(cx - s * 0.14f, cy - s * 0.44f, cx + s * 0.14f, cy + s * 0.36f); canvas.drawRoundRect(rect, s * 0.14f, s * 0.14f, stroke)
        canvas.restore()
    }

    fun iconCamera(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.color = color; stroke.strokeWidth = s * 0.09f
        rect.set(cx - s * 0.45f, cy - s * 0.28f, cx + s * 0.45f, cy + s * 0.34f); canvas.drawRoundRect(rect, s * 0.1f, s * 0.1f, stroke)
        canvas.drawCircle(cx, cy + s * 0.03f, s * 0.15f, stroke)
    }

    fun iconMic(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.color = color; stroke.color = color; stroke.strokeWidth = s * 0.08f
        rect.set(cx - s * 0.13f, cy - s * 0.38f, cx + s * 0.13f, cy + s * 0.08f); canvas.drawRoundRect(rect, s * 0.13f, s * 0.13f, fill)
        rect.set(cx - s * 0.25f, cy - s * 0.2f, cx + s * 0.25f, cy + s * 0.2f); canvas.drawArc(rect, 0f, 180f, false, stroke)
        canvas.drawLine(cx, cy + s * 0.2f, cx, cy + s * 0.34f, stroke)
    }

    fun iconSend(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.color = color
        // points left (RTL send)
        path.reset()
        path.moveTo(cx - s * 0.4f, cy); path.lineTo(cx + s * 0.38f, cy - s * 0.34f); path.lineTo(cx + s * 0.22f, cy); path.lineTo(cx + s * 0.38f, cy + s * 0.34f); path.close()
        canvas.drawPath(path, fill)
    }

    fun iconPlay(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.color = color
        path.reset(); path.moveTo(cx - s * 0.22f, cy - s * 0.32f); path.lineTo(cx + s * 0.34f, cy); path.lineTo(cx - s * 0.22f, cy + s * 0.32f); path.close()
        canvas.drawPath(path, fill)
    }

    fun iconLock(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.color = color; stroke.color = color; stroke.strokeWidth = s * 0.1f
        rect.set(cx - s * 0.3f, cy - s * 0.05f, cx + s * 0.3f, cy + s * 0.4f); canvas.drawRoundRect(rect, s * 0.06f, s * 0.06f, fill)
        rect.set(cx - s * 0.2f, cy - s * 0.38f, cx + s * 0.2f, cy + s * 0.05f); canvas.drawArc(rect, 180f, 180f, false, stroke)
    }

    fun drawHeart(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int = 0xFFE8838A.toInt()) {
        path.reset()
        path.moveTo(cx, cy + r)
        path.cubicTo(cx - r * 1.6f, cy - r * 0.2f, cx - r * 0.6f, cy - r * 1.3f, cx, cy - r * 0.45f)
        path.cubicTo(cx + r * 0.6f, cy - r * 1.3f, cx + r * 1.6f, cy - r * 0.2f, cx, cy + r)
        fill.color = color; canvas.drawPath(path, fill)
    }

    fun drawFollow(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        fill.color = color
        canvas.drawCircle(cx - 6f, cy - 10f, 12f, fill)
        rect.set(cx - 28f, cy + 4f, cx + 16f, cy + 26f); canvas.drawRoundRect(rect, 12f, 12f, fill)
        stroke.color = color; stroke.strokeWidth = 5f
        canvas.drawLine(cx + 22f, cy - 10f, cx + 22f, cy + 12f, stroke); canvas.drawLine(cx + 11f, cy + 1f, cx + 33f, cy + 1f, stroke)
    }

    fun drawShare(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        stroke.color = color; stroke.strokeWidth = 5f
        path.reset(); path.moveTo(cx - 22f, cy + 18f); path.quadTo(cx - 18f, cy - 12f, cx + 14f, cy - 12f)
        canvas.drawPath(path, stroke)
        path.reset(); path.moveTo(cx + 4f, cy - 24f); path.lineTo(cx + 18f, cy - 12f); path.lineTo(cx + 4f, cy)
        canvas.drawPath(path, stroke)
    }

    /** Status bar of the phone (RTL: clock on the right). */
    fun statusBar(canvas: Canvas, w: Float, color: Int) {
        val tp = text.paint(TextKit.Face.SEMI, 26f, color)
        val clock = "9:41"
        canvas.drawText(clock, w - 34f - tp.measureText(clock), 34f, tp)
        // battery
        stroke.color = color; stroke.strokeWidth = 2.5f
        rect.set(30f, 16f, 72f, 36f); canvas.drawRoundRect(rect, 5f, 5f, stroke)
        fill.color = color; canvas.drawRect(34f, 20f, 62f, 32f, fill); canvas.drawRect(73f, 22f, 77f, 30f, fill)
        // signal bars
        for (i in 0..3) canvas.drawRect(96f + i * 9f, 34f - (i + 1) * 4.5f, 102f + i * 9f, 34f, fill)
        // wifi arcs
        stroke.strokeWidth = 3f
        for (i in 0..2) { val r = 6f + i * 6f; rect.set(150f - r, 34f - r, 150f + r, 34f + r); canvas.drawArc(rect, 225f, 90f, false, stroke) }
    }
}
