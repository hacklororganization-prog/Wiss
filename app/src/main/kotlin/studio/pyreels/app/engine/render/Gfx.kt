package studio.pyreels.app.engine.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import studio.pyreels.app.data.AvatarStore
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
class Skin(val p: Palette, chatColor: String = "green") {
    private fun c(v: Long) = v.toInt()
    val dark = p.isDark
    // phone
    val bezel = if (dark) 0xFF0D1217.toInt() else 0xFF2A2F36.toInt()
    val bezelEdge = if (dark) 0xFF3A4654.toInt() else 0xFF59616C.toInt()
    val statusText = if (dark) 0xFFD8DEE4.toInt() else 0xFF1F2A30.toInt()
    // messenger
    val waHeader get() = if (dark) 0xFF1E2A33.toInt() else accentSetHeader
    val waHeaderText = if (dark) 0xFFE9EDEF.toInt() else 0xFFF7FAF8.toInt()
    val waHeaderSub = if (dark) 0xFF8D9AA3.toInt() else 0xFFD4E6E0.toInt()
    val waTyping = if (dark) 0xFF4CC9A4.toInt() else 0xFFE4F7EE.toInt()
    val waWall = if (dark) 0xFF111B21.toInt() else 0xFFEDE6DA.toInt()
    val waDoodle = if (dark) 0x0BFFFFFF else 0x0F000000
    val waIn = if (dark) 0xFF202C33.toInt() else 0xFFFFFFFF.toInt()
    // Ahmed's bubbles, the light header and the round button follow the chosen chat colour
    private val accentSet: IntArray = when (chatColor) {
        "blue" -> intArrayOf(0xFF1F3F5C.toInt(), 0xFFD6E8FA.toInt(), 0xFF2E5E8C.toInt(), 0xFF3A7BC8.toInt(), 0xFF2F6FB5.toInt())
        "purple" -> intArrayOf(0xFF3A2F57.toInt(), 0xFFE8DEF8.toInt(), 0xFF5B4B8A.toInt(), 0xFF7C64C4.toInt(), 0xFF6A55B0.toInt())
        "amber" -> intArrayOf(0xFF4A3D1E.toInt(), 0xFFF7EBC9.toInt(), 0xFF8A6D2E.toInt(), 0xFFC59A3A.toInt(), 0xFFA9832D.toInt())
        else -> intArrayOf(0xFF1F5247.toInt(), 0xFFD7F2CF.toInt(), 0xFF2F6F62.toInt(), 0xFF2BA684.toInt(), 0xFF2F8F72.toInt())
    }
    val waOut = if (dark) accentSet[0] else accentSet[1]
    val waText = if (dark) 0xFFE9EDEF.toInt() else 0xFF1B2328.toInt()
    val waTime = if (dark) 0xFF8D9AA3.toInt() else 0xFF6F7A80.toInt()
    val waTick = if (dark) 0xFF53BDEB.toInt() else 0xFF3A9BD1.toInt()
    val waPill = if (dark) 0xFF1E2A33.toInt() else 0xFFFFFFFF.toInt()
    val waNotice = if (dark) 0xFF2A2F25.toInt() else 0xFFFCF4CB.toInt()
    val waNoticeText = if (dark) 0xFFE6D7A3.toInt() else 0xFF5E5427.toInt()
    val waInput = if (dark) 0xFF1E2A33.toInt() else 0xFFFFFFFF.toInt()
    val waBar = if (dark) 0xFF0F171C.toInt() else 0xFFE7E0D4.toInt()
    val waFab get() = if (dark) accentSetFab else accentSetFabLight
    private val accentSetHeader get() = accentSet[2]
    private val accentSetFab get() = accentSet[3]
    private val accentSetFabLight get() = accentSet[4]
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
class Gfx(val text: TextKit, val skin: Skin, private val scenario: Scenario, private val context: Context? = null) {
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
        if (picture(canvas, persona.id, cx, cy, r)) {
            stroke.color = ring ?: alpha(accent, 0.95f); stroke.strokeWidth = r * 0.09f
            canvas.drawCircle(cx, cy, r - stroke.strokeWidth / 2, stroke)
            return
        }
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

    // ---------------------------------------------------------------- gallery pictures

    private val pictures = HashMap<String, BitmapShader?>()
    private val pictureSizes = HashMap<String, Int>()
    private val picturePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pictureMatrix = Matrix()

    /** Draws the gallery picture saved for [key] as a circle. False when there is none. */
    fun picture(canvas: Canvas, key: String, cx: Float, cy: Float, r: Float): Boolean {
        val ctx = context ?: return false
        if (!pictures.containsKey(key)) {
            val bmp: Bitmap? = AvatarStore.load(ctx, key)
            pictures[key] = bmp?.let { pictureSizes[key] = it.width; BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        }
        val shader = pictures[key] ?: return false
        val size = pictureSizes[key] ?: return false
        val k = 2f * r / size
        pictureMatrix.setScale(k, k)
        pictureMatrix.postTranslate(cx - r, cy - r)
        shader.setLocalMatrix(pictureMatrix)
        picturePaint.shader = shader
        canvas.drawCircle(cx, cy, r, picturePaint)
        return true
    }

    // ---------------------------------------------------------------- vector icons (24×24 path data)

    private val iconPaths = HashMap<String, Path?>()

    /** Draws a filled icon centred at (cx, cy) with the given size. False if the path is unavailable. */
    fun icon(canvas: Canvas, name: String, cx: Float, cy: Float, size: Float, color: Int, mirror: Boolean = false): Boolean {
        if (!iconPaths.containsKey(name)) {
            iconPaths[name] = ICONS[name]?.let { d -> runCatching { Path(PathParser().parsePathString(d).toPath().asAndroidPath()) }.getOrNull() }
        }
        val path = iconPaths[name] ?: return false
        fill.color = color
        canvas.save()
        canvas.translate(cx - size / 2, cy - size / 2)
        canvas.scale(size / 24f, size / 24f)
        if (mirror) canvas.scale(-1f, 1f, 12f, 12f)
        canvas.drawPath(path, fill)
        canvas.restore()
        return true
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
        if (icon(canvas, "videocam", cx, cy, s * 1.05f, color)) return
        stroke.color = color; stroke.strokeWidth = s * 0.1f
        rect.set(cx - s * 0.5f, cy - s * 0.3f, cx + s * 0.18f, cy + s * 0.3f); canvas.drawRoundRect(rect, s * 0.1f, s * 0.1f, stroke)
        path.reset(); path.moveTo(cx + s * 0.22f, cy - s * 0.08f); path.lineTo(cx + s * 0.5f, cy - s * 0.26f); path.lineTo(cx + s * 0.5f, cy + s * 0.26f); path.lineTo(cx + s * 0.22f, cy + s * 0.08f)
        canvas.drawPath(path, stroke)
    }

    fun iconPhone(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        if (icon(canvas, "call", cx, cy, s * 0.95f, color)) return
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
        if (icon(canvas, "mood", cx, cy, s * 1.0f, color)) return
        stroke.color = color; stroke.strokeWidth = s * 0.09f
        canvas.drawCircle(cx, cy, s * 0.42f, stroke)
        fill.color = color
        canvas.drawCircle(cx - s * 0.15f, cy - s * 0.1f, s * 0.05f, fill); canvas.drawCircle(cx + s * 0.15f, cy - s * 0.1f, s * 0.05f, fill)
        rect.set(cx - s * 0.22f, cy - s * 0.12f, cx + s * 0.22f, cy + s * 0.24f)
        canvas.drawArc(rect, 20f, 140f, false, stroke)
    }

    fun iconClip(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        canvas.save(); canvas.rotate(45f, cx, cy)
        val ok = icon(canvas, "attach", cx, cy, s * 1.0f, color)
        canvas.restore()
        if (ok) return
        stroke.color = color; stroke.strokeWidth = s * 0.09f
        canvas.save(); canvas.rotate(-40f, cx, cy)
        rect.set(cx - s * 0.14f, cy - s * 0.44f, cx + s * 0.14f, cy + s * 0.36f); canvas.drawRoundRect(rect, s * 0.14f, s * 0.14f, stroke)
        canvas.restore()
    }

    fun iconCamera(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        if (icon(canvas, "camera", cx, cy, s * 1.0f, color)) return
        stroke.color = color; stroke.strokeWidth = s * 0.09f
        rect.set(cx - s * 0.45f, cy - s * 0.28f, cx + s * 0.45f, cy + s * 0.34f); canvas.drawRoundRect(rect, s * 0.1f, s * 0.1f, stroke)
        canvas.drawCircle(cx, cy + s * 0.03f, s * 0.15f, stroke)
    }

    fun iconMic(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        if (icon(canvas, "mic", cx, cy, s * 0.95f, color)) return
        fill.color = color; stroke.color = color; stroke.strokeWidth = s * 0.08f
        rect.set(cx - s * 0.13f, cy - s * 0.38f, cx + s * 0.13f, cy + s * 0.08f); canvas.drawRoundRect(rect, s * 0.13f, s * 0.13f, fill)
        rect.set(cx - s * 0.25f, cy - s * 0.2f, cx + s * 0.25f, cy + s * 0.2f); canvas.drawArc(rect, 0f, 180f, false, stroke)
        canvas.drawLine(cx, cy + s * 0.2f, cx, cy + s * 0.34f, stroke)
    }

    fun iconSend(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        if (icon(canvas, "send", cx, cy, s * 0.95f, color, mirror = true)) return
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
        if (icon(canvas, "lock", cx, cy, s * 1.15f, color)) return
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

    companion object {
        /** Standard 24×24 filled icon outlines. */
        private val ICONS = mapOf(
            "call" to "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z",
            "videocam" to "M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z",
            "attach" to "M16.5 6v11.5c0 2.21-1.79 4-4 4s-4-1.79-4-4V5c0-1.38 1.12-2.5 2.5-2.5s2.5 1.12 2.5 2.5v10.5c0 .55-.45 1-1 1s-1-.45-1-1V6H10v9.5c0 1.38 1.12 2.5 2.5 2.5s2.5-1.12 2.5-2.5V5c0-2.21-1.79-4-4-4S7 2.79 7 5v12.5c0 3.04 2.46 5.5 5.5 5.5s5.5-2.46 5.5-5.5V6h-1.5z",
            "camera" to "M9 2L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zM8.8 12a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0-6.4 0z",
            "mic" to "M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z",
            "send" to "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z",
            "lock" to "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z",
            "group" to "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z",
            "mood" to "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm3.5-9c.83 0 1.5-.67 1.5-1.5S16.33 8 15.5 8 14 8.67 14 9.5s.67 1.5 1.5 1.5zm-7 0c.83 0 1.5-.67 1.5-1.5S9.33 8 8.5 8 7 8.67 7 9.5 7.67 11 8.5 11zm3.5 6.5c2.33 0 4.31-1.46 5.11-3.5H6.89c.8 2.04 2.78 3.5 5.11 3.5z",
        )
    }
}
