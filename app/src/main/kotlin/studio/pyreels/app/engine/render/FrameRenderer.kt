package studio.pyreels.app.engine.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import studio.pyreels.core.model.Limits
import studio.pyreels.core.model.Palette
import studio.pyreels.core.model.Personas
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.Tokens
import studio.pyreels.core.model.castById
import studio.pyreels.core.timeline.CardKind
import studio.pyreels.core.timeline.CardView
import studio.pyreels.core.timeline.ChatMessageView
import studio.pyreels.core.timeline.ChatView
import studio.pyreels.core.timeline.CodeView
import studio.pyreels.core.timeline.Frame
import studio.pyreels.core.timeline.HookView
import studio.pyreels.core.timeline.Layer
import studio.pyreels.core.timeline.MessageKind
import studio.pyreels.core.timeline.Stage
import studio.pyreels.core.timeline.TermKind
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a [Frame] (pure render model from the core timeline) onto a Canvas in the 1080×1920 video
 * coordinate space. The same renderer is used by the 60 fps preview and the 30 fps encoder, so
 * the preview is exactly what gets exported. No allocations in the steady-state hot path:
 * paints, paths, rects and text layouts are pooled/cached.
 */
class FrameRenderer(context: Context, private val scenario: Scenario) {

    private val fonts = Fonts.get(context)
    private val text = TextKit(fonts)
    private val palette: Palette = Tokens.palette(scenario.meta.theme)

    private val W = Limits.WIDTH.toFloat()
    private val H = Limits.HEIGHT.toFloat()
    private val left = W * Limits.SAFE_LEFT + 12f
    private val right = W * (1f - Limits.SAFE_RIGHT)
    private val top = H * Limits.SAFE_TOP
    private val bottom = H * (1f - Limits.SAFE_BOTTOM)
    private val contentW = right - left

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val path = Path()
    private val bgPaint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, H, palette.backgroundTop.toInt(), palette.background.toInt(), Shader.TileMode.CLAMP)
    }

    var showSafeZones = false

    private fun c(v: Long) = v.toInt()
    private fun alpha(color: Int, a: Float): Int = (color and 0x00FFFFFF) or ((((color ushr 24) and 0xFF) * a.coerceIn(0f, 1f)).toInt() shl 24)

    fun draw(canvas: Canvas, frame: Frame) {
        canvas.drawRect(0f, 0f, W, H, bgPaint)
        for (layer in frame.layers) drawLayer(canvas, layer)
        frame.engageChip?.let { drawEngageChip(canvas, it.actions, it.appear, it.heartPulse) }
        frame.caption?.let { cap ->
            if (cap.appear > 0.01f) {
                val l = text.layout(cap.words.joinToString(" "), (contentW - 40).toInt(), Tokens.Type.CAPTION, c(palette.text), TextKit.Face.SEMI, Layout.Alignment.ALIGN_CENTER, 1, cap.activeWord, c(palette.focus))
                val y = bottom - l.height - 30f
                withAlpha(canvas, cap.appear) {
                    rect.set(left, y - 18f, right, y + l.height + 18f)
                    fill.color = alpha(c(palette.surface), 0.85f); canvas.drawRoundRect(rect, 28f, 28f, fill)
                    canvas.save(); canvas.translate(left + 20f, y); l.draw(canvas); canvas.restore()
                }
            }
        }
        drawChrome(canvas, frame)
        if (showSafeZones) drawSafeZones(canvas)
    }

    private inline fun withAlpha(canvas: Canvas, a: Float, block: () -> Unit) {
        if (a >= 0.995f) { block(); return }
        if (a <= 0.005f) return
        val save = canvas.saveLayerAlpha(0f, 0f, W, H, (a * 255).toInt())
        block()
        canvas.restoreToCount(save)
    }

    private fun drawLayer(canvas: Canvas, layer: Layer) {
        withAlpha(canvas, layer.alpha) {
            canvas.save()
            canvas.translate(0f, layer.offsetY)
            if (layer.scale != 1f) canvas.scale(layer.scale, layer.scale, W / 2, H / 2)
            when (layer.stage) {
                Stage.CHAT -> layer.chat?.let { drawChat(canvas, it) }
                Stage.CODE -> layer.code?.let { drawCode(canvas, it) }
                Stage.HOOK -> layer.hook?.let { drawHook(canvas, it) }
                Stage.CARD -> layer.card?.let { drawCard(canvas, it) }
            }
            canvas.restore()
        }
    }

    // ------------------------------------------------------------------ chrome

    private fun drawChrome(canvas: Canvas, f: Frame) {
        // thin progress bar
        fill.color = alpha(c(palette.textMuted), 0.25f)
        canvas.drawRect(0f, 0f, W, 8f, fill)
        fill.color = c(palette.focus)
        canvas.drawRect(W - W * f.progress, 0f, W, 8f, fill) // RTL progress (fills from the right)
        // lesson title chip
        val title = (f.episode?.let { "الحلقة $it · " } ?: "") + f.title
        val l = text.layout(title, (contentW * 0.8f).toInt(), Tokens.Type.CHIP, c(palette.textMuted), TextKit.Face.SEMI, Layout.Alignment.ALIGN_NORMAL, 1)
        val w = text.maxLineWidth(l) + 44f
        val y = top + 4f
        rect.set(right - w, y, right, y + l.height + 20f)
        fill.color = alpha(c(palette.surface), 0.9f)
        canvas.drawRoundRect(rect, 40f, 40f, fill)
        canvas.save(); canvas.translate(right - w + 22f, y + 10f); l.draw(canvas); canvas.restore()
        if (f.watermark) {
            val wm = text.layout("PyReels · ${scenario.meta.series.name ?: "تعلّم مع أحمد"}", contentW.toInt(), 26f, alpha(c(palette.textMuted), 0.55f), TextKit.Face.REGULAR, Layout.Alignment.ALIGN_OPPOSITE, 1)
            canvas.save(); canvas.translate(left, bottom + 14f); wm.draw(canvas); canvas.restore()
        }
    }

    private fun drawSafeZones(canvas: Canvas) {
        fill.color = c(palette.safeZone)
        canvas.drawRect(0f, 0f, W, top, fill)
        canvas.drawRect(0f, bottom, W, H, fill)
        canvas.drawRect(right, top, W, bottom, fill)
        stroke.color = alpha(c(palette.terminalError), 0.6f); stroke.strokeWidth = 3f
        canvas.drawRect(left, top, right, bottom, stroke)
    }

    // ------------------------------------------------------------------ avatars

    private fun drawAvatar(canvas: Canvas, castId: String, cx: Float, cy: Float, r: Float) {
        val member = scenario.castById(castId)
        val persona = member?.let { Personas.forCast(it) } ?: Personas.forLanguage(castId)
        val accent = c(Tokens.accent(persona, palette))
        fill.color = alpha(accent, 0.22f); canvas.drawCircle(cx, cy, r, fill)
        stroke.color = alpha(accent, 0.9f); stroke.strokeWidth = 3f; canvas.drawCircle(cx, cy, r - 1.5f, stroke)
        val mono = persona.monogram
        val p = text.paint(if (castId == "ahmed") TextKit.Face.BOLD else TextKit.Face.MONO_BOLD, r * (if (mono.length > 2) 0.62f else 0.8f), accent)
        p.textAlign = Paint.Align.CENTER
        val fm = p.fontMetrics
        canvas.drawText(mono, cx, cy - (fm.ascent + fm.descent) / 2, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun accentOf(castId: String): Int {
        val m = scenario.castById(castId)
        val persona = m?.let { Personas.forCast(it) } ?: Personas.forLanguage(castId)
        return c(Tokens.accent(persona, palette))
    }

    private fun nameOf(castId: String) = scenario.castById(castId)?.name ?: castId

    // ------------------------------------------------------------------ chat

    private val bubbleMax get() = contentW * 0.80f
    private val avatarR = 34f

    private fun drawChat(canvas: Canvas, chat: ChatView) {
        // Header
        val headerTop = top + 70f
        val headerH = 120f
        rect.set(left, headerTop, right, headerTop + headerH)
        fill.color = c(palette.header); canvas.drawRoundRect(rect, 36f, 36f, fill)
        // avatar cluster (right side, RTL)
        val members = scenario.cast
        var ax = right - 60f
        for (m in members.take(5)) { drawAvatar(canvas, m.id, ax, headerTop + headerH / 2, 30f); ax -= 46f }
        val nameL = text.layout(chat.groupName, (contentW - 330).toInt(), Tokens.Type.HEADER, c(palette.text), TextKit.Face.BOLD, Layout.Alignment.ALIGN_NORMAL, 1)
        val status = chat.typingFrom?.let { "${nameOf(it)} يكتب…" } ?: "متصلون الآن: " + members.joinToString("، ") { it.name }
        val statusL = text.layout(status, (contentW - 330).toInt(), 28f, if (chat.typingFrom != null) c(palette.tickRead) else c(palette.textMuted), TextKit.Face.REGULAR, Layout.Alignment.ALIGN_NORMAL, 1)
        val textRight = ax - 10f
        canvas.save(); canvas.translate(left + 30f, headerTop + 14f)
        // layouts are RTL-aligned within their width: place them so they end at textRight
        canvas.translate(textRight - left - 30f - (contentW - 330), 0f)
        nameL.draw(canvas); canvas.translate(0f, nameL.height.toFloat() - 4f); statusL.draw(canvas)
        canvas.restore()
        // online dot
        fill.color = c(palette.terminalOk); canvas.drawCircle(right - 34f, headerTop + headerH - 26f, 9f, fill)

        // Messages, bottom-aligned
        val listTop = headerTop + headerH + 24f
        var y = bottom - 24f
        val typingFrom = chat.typingFrom
        if (typingFrom != null) {
            val h = 92f
            drawTyping(canvas, typingFrom, chat.typingPhase, y - h)
            y -= h + 22f
        }
        val msgs = chat.messages
        if (msgs.isEmpty()) return
        val newest = msgs.last()
        val newestH = measureMessage(newest)
        val shift = (1f - chat.scroll) * (newestH + 22f)
        canvas.save()
        canvas.clipRect(0f, listTop, W, bottom + 40f)
        for (k in msgs.indices.reversed()) {
            val m = msgs[k]
            val h = if (k == msgs.lastIndex) newestH else measureMessage(m)
            val yTop = y - h + (if (k == msgs.lastIndex) 0f else shift)
            if (yTop + h < listTop) break
            if (k == msgs.lastIndex) {
                withAlpha(canvas, m.appear) {
                    canvas.save(); canvas.translate(0f, (1f - m.appear) * 8f); drawMessage(canvas, m, yTop); canvas.restore()
                }
            } else drawMessage(canvas, m, yTop)
            y = yTop - 22f
        }
        canvas.restore()
    }

    private fun drawTyping(canvas: Canvas, from: String, phase: Float, y: Float) {
        val outgoing = from == "ahmed"
        val w = 150f
        val x = if (outgoing) left + 8f else right - avatarR * 2 - 18f - w
        if (!outgoing) drawAvatar(canvas, from, right - avatarR, y + 46f, avatarR)
        rect.set(x, y, x + w, y + 92f)
        fill.color = c(if (outgoing) palette.bubbleOut else palette.bubbleIn); canvas.drawRoundRect(rect, 36f, 36f, fill)
        for (i in 0..2) {
            // a slow wave (1.2 s cycle) — calm, well under 1 Hz per dot
            val local = ((phase * 3f - i * 0.5f) % 3f + 3f) % 3f
            val a = 0.35f + 0.65f * (if (local < 1f) (1f - kotlin.math.abs(local - 0.5f) * 2f) else 0f)
            fill.color = alpha(c(palette.textMuted), a)
            canvas.drawCircle(x + 45f + i * 30f, y + 46f, 9f, fill)
        }
    }

    private class Measured(var textL: StaticLayout? = null, var bubbleW: Float = 0f, var height: Float = 0f)

    private val measureCache = HashMap<Int, Measured>()

    private fun bodyLayout(m: ChatMessageView): StaticLayout? {
        if (m.text.isBlank()) return null
        val color = c(if (m.outgoing) palette.bubbleOutText else palette.text)
        return text.layout(m.text.replace("`", ""), (bubbleMax - 56).toInt(), Tokens.Type.BUBBLE, color, TextKit.Face.REGULAR, Layout.Alignment.ALIGN_NORMAL, 1, m.karaokeWord, c(palette.focus))
    }

    private fun codeLayout(code: String, width: Int, size: Float = 34f) =
        text.layout(code, width, size, c(palette.codeText), TextKit.Face.MONO, Layout.Alignment.ALIGN_NORMAL, 0, spacing = 1.18f)

    private fun measureMessage(m: ChatMessageView): Float {
        // karaoke changes the layout but not its size; cache by id
        measureCache[m.id]?.let { if (m.karaokeWord < 0) return it.height }
        val body = bodyLayout(m)
        var h = 26f
        if (!m.outgoing) h += 44f // name
        if (body != null) h += body.height
        when (m.kind) {
            MessageKind.ANALOGY -> h += 70f + 150f
            MessageKind.REVEAL -> {
                h += 160f
                m.revealCode?.let { h += codeLayout(it, (bubbleMax - 100).toInt(), 30f).height + 40f }
            }
            else -> Unit
        }
        m.code?.let { h += codeLayout(it, (bubbleMax - 90).toInt()).height + 40f }
        if (m.challenge) h += 56f
        h += 44f // time row
        if (m.reactions.isNotEmpty()) h += 30f
        measureCache[m.id] = Measured(body, 0f, h)
        return h
    }

    private fun drawMessage(canvas: Canvas, m: ChatMessageView, y: Float) {
        val body = bodyLayout(m)
        val h = measureMessage(m)
        val minW = when (m.kind) { MessageKind.ANALOGY, MessageKind.REVEAL -> bubbleMax; else -> 220f }
        var contentWidth = body?.let { text.maxLineWidth(it) } ?: 0f
        m.code?.let { contentWidth = max(contentWidth, text.maxLineWidth(codeLayout(it, (bubbleMax - 90).toInt())) + 40f) }
        val nameL = if (!m.outgoing) text.layout(nameOf(m.from), (bubbleMax - 56).toInt(), Tokens.Type.NAME, accentOf(m.from), TextKit.Face.BOLD, Layout.Alignment.ALIGN_NORMAL, 1) else null
        nameL?.let { contentWidth = max(contentWidth, text.maxLineWidth(it)) }
        val bw = min(bubbleMax, max(minW, contentWidth + 56f))
        val bubbleH = h - (if (m.reactions.isNotEmpty()) 30f else 0f)
        val x = if (m.outgoing) left + 8f else right - avatarR * 2 - 18f - bw
        if (!m.outgoing) drawAvatar(canvas, m.from, right - avatarR, y + avatarR + 6f, avatarR)
        rect.set(x, y, x + bw, y + bubbleH)
        fill.color = c(if (m.outgoing) palette.bubbleOut else palette.bubbleIn)
        canvas.drawRoundRect(rect, 34f, 34f, fill)
        if (m.challenge) { stroke.color = alpha(c(palette.focus), 0.8f); stroke.strokeWidth = 3f; canvas.drawRoundRect(rect, 34f, 34f, stroke) }
        var cy = y + 18f
        val inner = x + 28f
        val innerW = bw - 56f
        nameL?.let { drawRtl(canvas, it, inner, innerW, cy); cy += 44f }
        body?.let { drawRtl(canvas, it, inner, innerW, cy); cy += it.height }
        m.code?.let { code ->
            val cl = codeLayout(code, (bubbleMax - 90).toInt())
            cy += 12f
            rect.set(inner, cy, inner + innerW, cy + cl.height + 24f)
            fill.color = c(palette.codeBg); canvas.drawRoundRect(rect, 18f, 18f, fill)
            canvas.save(); canvas.translate(inner + 18f, cy + 12f); drawCodeText(canvas, cl, code); canvas.restore()
            cy += cl.height + 28f
        }
        when (m.kind) {
            MessageKind.ANALOGY -> { cy = drawAnalogy(canvas, m, inner, innerW, cy) }
            MessageKind.REVEAL -> { cy = drawReveal(canvas, m, inner, innerW, cy) }
            else -> Unit
        }
        if (m.challenge) {
            val chip = text.layout("💬 تحدّي التعليقات", innerW.toInt(), 30f, c(palette.focus), TextKit.Face.SEMI, Layout.Alignment.ALIGN_NORMAL, 1)
            drawRtl(canvas, chip, inner, innerW, cy + 8f); cy += 56f
        }
        // time + ticks (bottom-left in RTL)
        val tp = text.paint(TextKit.Face.REGULAR, Tokens.Type.TIME, c(palette.textMuted))
        canvas.drawText(m.timeLabel, x + 28f, y + bubbleH - 16f, tp)
        if (m.outgoing) drawTicks(canvas, x + 28f + tp.measureText(m.timeLabel) + 14f, y + bubbleH - 26f, m.ticksRead)
        if (m.reactions.isNotEmpty()) {
            val s = 1f + 0.12f * m.reactionPulse
            val rx = if (m.outgoing) x + bw - 70f else x + 30f
            val ry = y + bubbleH + 6f
            rect.set(rx, ry - 20f, rx + 70f * s, ry + 36f)
            fill.color = c(palette.surfaceHigh); canvas.drawRoundRect(rect, 28f, 28f, fill)
            val emoji = m.reactions.joinToString("") { reactionEmoji(it) }
            val ep = text.paint(TextKit.Face.REGULAR, 32f * s, c(palette.text))
            canvas.drawText(emoji, rx + 14f, ry + 24f, ep)
        }
    }

    private fun reactionEmoji(id: String) = when (id) { "heart" -> "❤️"; "like" -> "👍"; "laugh" -> "😄"; "wow" -> "😮"; "clap" -> "👏"; else -> "❤️" }

    /** Draws an RTL-aligned layout so that its lines end at the right edge of [x, x+w]. */
    private fun drawRtl(canvas: Canvas, l: StaticLayout, x: Float, w: Float, y: Float) {
        canvas.save()
        canvas.translate(x + w - l.width, y)
        l.draw(canvas)
        canvas.restore()
    }

    private fun drawTicks(canvas: Canvas, x: Float, y: Float, read: Boolean) {
        stroke.color = c(if (read) palette.tickRead else palette.textMuted); stroke.strokeWidth = 3.5f
        stroke.strokeCap = Paint.Cap.ROUND
        for (k in 0..1) {
            val ox = x + k * 12f
            path.reset(); path.moveTo(ox, y + 8f); path.lineTo(ox + 7f, y + 15f); path.lineTo(ox + 20f, y)
            canvas.drawPath(path, stroke)
        }
    }

    private fun drawAnalogy(canvas: Canvas, m: ChatMessageView, x: Float, w: Float, yIn: Float): Float {
        var y = yIn + 6f
        m.analogyTitle?.let {
            val l = text.layout(it, w.toInt(), 40f, c(palette.focus), TextKit.Face.BOLD, Layout.Alignment.ALIGN_NORMAL, 1)
            drawRtl(canvas, l, x, w, y); y += 64f
        }
        val items = m.analogyItems
        if (items.isEmpty()) return y
        val n = items.size
        val gap = 16f
        val bw = (w - gap * (n - 1)) / n
        for (i in 0 until n) {
            if (i >= m.analogyVisible) continue
            val (label, value) = items[i]
            val bx = x + w - (i + 1) * bw - i * gap // first item at the right (RTL)
            val lab = text.layout(label, bw.toInt(), 28f, c(palette.textMuted), TextKit.Face.MONO, Layout.Alignment.ALIGN_CENTER, 2)
            canvas.save(); canvas.translate(bx, y); lab.draw(canvas); canvas.restore()
            rect.set(bx, y + 40f, bx + bw, y + 140f)
            fill.color = c(palette.surfaceHigh); canvas.drawRoundRect(rect, 20f, 20f, fill)
            stroke.color = alpha(accentOf(m.from), 0.7f); stroke.strokeWidth = 3f; canvas.drawRoundRect(rect, 20f, 20f, stroke)
            // box "lid" line to read as a labeled box
            canvas.drawLine(bx + 16f, y + 62f, bx + bw - 16f, y + 62f, stroke)
            val v = text.layout(value, (bw - 16).toInt(), 38f, c(palette.text), TextKit.Face.MONO_BOLD, Layout.Alignment.ALIGN_CENTER, 2)
            canvas.save(); canvas.translate(bx + 8f, y + 72f); v.draw(canvas); canvas.restore()
        }
        return y + 150f
    }

    private fun drawReveal(canvas: Canvas, m: ChatMessageView, x: Float, w: Float, yIn: Float): Float {
        var y = yIn + 10f
        m.revealCode?.let { code ->
            val cl = codeLayout(code, (w - 40).toInt(), 30f)
            rect.set(x, y, x + w, y + cl.height + 24f)
            fill.color = c(palette.codeBg); canvas.drawRoundRect(rect, 18f, 18f, fill)
            canvas.save(); canvas.translate(x + 18f, y + 12f); drawCodeText(canvas, cl, code, 30f); canvas.restore()
            y += cl.height + 40f
        }
        val label = text.layout("الإجابة", w.toInt(), 30f, c(palette.textMuted), TextKit.Face.SEMI, Layout.Alignment.ALIGN_NORMAL, 1)
        drawRtl(canvas, label, x, w, y)
        val ans = text.layout(m.revealAnswer ?: "", w.toInt(), 64f, c(palette.focus), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 2)
        canvas.save(); canvas.translate(x, y + 44f); ans.draw(canvas); canvas.restore()
        return y + 160f
    }

    // ------------------------------------------------------------------ code stage

    private val lineH = Tokens.Type.CODE * 1.5f

    private fun drawCode(canvas: Canvas, code: CodeView) {
        var y = top + 70f
        val lines = code.text.split('\n')
        val shownLines = max(lines.size, 3).coerceAtMost(Limits.MAX_CODE_LINES + 2)
        val editorH = 92f + shownLines * lineH + 40f
        // editor card
        rect.set(left, y, right, y + editorH)
        fill.color = c(palette.codeBg); canvas.drawRoundRect(rect, 34f, 34f, fill)
        // tab bar: three soft dots + file name
        for (i in 0..2) { fill.color = alpha(c(palette.textMuted), 0.35f); canvas.drawCircle(left + 40f + i * 30f, y + 46f, 9f, fill) }
        val tab = text.paint(TextKit.Face.MONO, 30f, c(palette.textMuted))
        canvas.drawText(code.fileName, right - 40f - tab.measureText(code.fileName), y + 56f, tab)
        if (code.running) {
            fill.color = c(palette.terminalOk); canvas.drawCircle(right - 60f - tab.measureText(code.fileName), y + 46f, 9f, fill)
        }
        val codeTop = y + 92f
        val gutter = left + 30f
        val codeX = left + 110f
        val maxChars = lines.maxOfOrNull { it.length } ?: 0
        val monoSize = fitMonoSize(maxChars, right - codeX - 30f)
        val lh = monoSize * 1.5f

        canvas.save()
        if (code.zoom > 0f && code.activeLine > 0) {
            val cy = codeTop + (code.activeLine - 0.5f) * lh
            canvas.scale(1f + code.zoom, 1f + code.zoom, (left + right) / 2, cy)
        }
        // active line band
        if (code.activeLine in 1..lines.size) {
            rect.set(left + 12f, codeTop + (code.activeLine - 1) * lh + 4f, right - 12f, codeTop + code.activeLine * lh + 2f)
            fill.color = alpha(c(palette.highlight), 0.4f + 0.6f * code.activeLineMix); canvas.drawRoundRect(rect, 14f, 14f, fill)
            fill.color = alpha(c(palette.focus), code.activeLineMix); canvas.drawRoundRect(left + 12f, rect.top, left + 20f, rect.bottom, 4f, 4f, fill)
        }
        if (code.errorLine in 1..lines.size) {
            rect.set(left + 12f, codeTop + (code.errorLine - 1) * lh + 4f, right - 12f, codeTop + code.errorLine * lh + 2f)
            fill.color = alpha(c(palette.terminalError), 0.16f); canvas.drawRoundRect(rect, 14f, 14f, fill)
        }
        // focus frame
        if (code.focusLines.isNotEmpty() && code.activeLineMix > 0f) {
            val a = code.focusLines.min().coerceIn(1, lines.size)
            val b = code.focusLines.max().coerceIn(1, lines.size)
            rect.set(left + 8f, codeTop + (a - 1) * lh, right - 8f, codeTop + b * lh + 6f)
            stroke.color = alpha(c(palette.focus), 0.75f * code.activeLineMix); stroke.strokeWidth = 3f
            canvas.drawRoundRect(rect, 16f, 16f, stroke)
        }
        val numPaint = text.paint(TextKit.Face.MONO, monoSize * 0.8f, c(palette.lineNumber))
        for ((i, line) in lines.withIndex()) {
            val ln = i + 1
            val baseline = codeTop + i * lh + lh * 0.7f
            val dimmed = code.dim > 0f && ln != code.activeLine && (code.focusLines.isEmpty() || ln !in code.focusLines || code.activeLine > 0)
            val a = if (dimmed) 1f - code.dim else 1f
            canvas.drawText(ln.toString(), gutter, baseline, numPaint)
            var x = codeX
            for (tok in SyntaxHighlighter.tokens(line, code.language)) {
                val p = text.paint(if (tok.kind == SyntaxHighlighter.Kind.KEYWORD) TextKit.Face.MONO_BOLD else TextKit.Face.MONO, monoSize, SyntaxHighlighter.color(tok.kind, palette))
                if (a < 1f) p.alpha = (255 * a).toInt()
                canvas.drawText(tok.text, x, baseline, p)
                x += p.measureText(tok.text)
                if (a < 1f) p.alpha = 255
            }
        }
        if (code.cursorVisible && code.cursorIndex >= 0) {
            val lastIdx = lines.lastIndex
            val last = lines.last()
            val w = text.paint(TextKit.Face.MONO, monoSize, c(palette.codeText)).measureText(last)
            fill.color = alpha(c(palette.focus), 0.9f)
            val cy = codeTop + lastIdx * lh
            canvas.drawRoundRect(codeX + w + 2f, cy + lh * 0.15f, codeX + w + 7f, cy + lh * 0.85f, 3f, 3f, fill)
        }
        canvas.restore()
        y += editorH + 26f

        // trace panel: variables, iteration counter, call stack
        if (code.vars.isNotEmpty() || code.iteration != null || code.stack.isNotEmpty()) {
            y = drawTracePanel(canvas, code, y) + 22f
        }
        // teacher note
        code.note?.let { note ->
            val l = text.layout(note.text.replace("`", ""), (contentW - 150).toInt(), 40f, c(palette.text), TextKit.Face.REGULAR, Layout.Alignment.ALIGN_NORMAL, 1, note.karaokeWord, c(palette.focus))
            val h = l.height + 48f
            withAlpha(canvas, note.appear) {
                rect.set(left, y, right - 80f, y + h)
                fill.color = c(palette.bubbleIn); canvas.drawRoundRect(rect, 30f, 30f, fill)
                drawRtl(canvas, l, left + 26f, contentW - 150f, y + 24f)
                drawAvatar(canvas, note.from, right - 36f, y + 40f, 32f)
            }
            y += h + 22f
        }
        // terminal
        if (code.terminal.isNotEmpty() || code.terminalVisible > 0f) {
            val termTop = max(y, bottom - 330f)
            withAlpha(canvas, code.terminalVisible) { drawTerminal(canvas, code, termTop, bottom - 20f) }
        }
    }

    private fun fitMonoSize(maxChars: Int, width: Float): Float {
        val p = text.paint(TextKit.Face.MONO, Tokens.Type.CODE, c(palette.codeText))
        val charW = p.measureText("M")
        if (maxChars <= 0 || charW * maxChars <= width) return Tokens.Type.CODE
        return (Tokens.Type.CODE * width / (charW * maxChars)).coerceAtLeast(26f)
    }

    private fun drawTracePanel(canvas: Canvas, code: CodeView, yIn: Float): Float {
        val y = yIn
        val h = 150f
        rect.set(left, y, right, y + h)
        fill.color = c(palette.surface); canvas.drawRoundRect(rect, 30f, 30f, fill)
        var x = right - 24f
        // iteration + stack chips on the right (RTL)
        code.iteration?.let { it ->
            val l = text.layout("الدورة $it", 220, 30f, c(palette.focus), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1)
            rect.set(x - 180f, y + 24f, x, y + 76f); fill.color = alpha(c(palette.focus), 0.15f); canvas.drawRoundRect(rect, 26f, 26f, fill)
            canvas.save(); canvas.translate(x - 180f - 20f, y + 32f); l.draw(canvas); canvas.restore()
            x -= 196f
        }
        if (code.stack.isNotEmpty()) {
            val s = code.stack.joinToString(" › ")
            val p = text.paint(TextKit.Face.MONO, 28f, c(palette.codeFunction))
            val w = p.measureText(s) + 40f
            rect.set(x - w, y + 24f, x, y + 76f); fill.color = c(palette.surfaceHigh); canvas.drawRoundRect(rect, 26f, 26f, fill)
            canvas.drawText(s, x - w + 20f, y + 60f, p)
            x -= w + 16f
            val lab = text.paint(TextKit.Face.REGULAR, 24f, c(palette.textMuted))
            canvas.drawText("مكدّس الاستدعاء", x - lab.measureText("مكدّس الاستدعاء"), y + 60f, lab)
        }
        // variable boxes from the left
        var bx = left + 24f
        for (v in code.vars.take(4)) {
            val namePaint = text.paint(TextKit.Face.MONO, 26f, c(palette.textMuted))
            val valuePaint = text.paint(TextKit.Face.MONO_BOLD, 38f, c(palette.text))
            val bw = max(130f, max(namePaint.measureText(v.name), max(valuePaint.measureText(v.value), v.previous?.let { valuePaint.measureText(it) } ?: 0f)) + 44f)
            if (bx + bw > x - 10f) break
            canvas.drawText(v.name, bx + 14f, y + 42f, namePaint)
            rect.set(bx, y + 54f, bx + bw, y + 128f)
            fill.color = c(palette.surfaceHigh); canvas.drawRoundRect(rect, 18f, 18f, fill)
            val changing = v.change < 1f
            stroke.color = alpha(c(palette.focus), if (changing) 0.4f + 0.6f * (1f - v.change) else 0.25f); stroke.strokeWidth = 3f
            canvas.drawRoundRect(rect, 18f, 18f, stroke)
            canvas.save(); canvas.clipRect(rect)
            val previous = v.previous
            if (changing && previous != null) {
                valuePaint.alpha = ((1f - v.change) * 255).toInt()
                canvas.drawText(previous, bx + 22f, y + 106f - v.change * 30f, valuePaint)
                valuePaint.alpha = (v.change * 255).toInt()
                canvas.drawText(v.value, bx + 22f, y + 106f + (1f - v.change) * 30f, valuePaint)
                valuePaint.alpha = 255
            } else canvas.drawText(v.value, bx + 22f, y + 106f, valuePaint)
            canvas.restore()
            bx += bw + 18f
        }
        return y + h
    }

    private fun drawTerminal(canvas: Canvas, code: CodeView, yTop: Float, yBottom: Float) {
        rect.set(left, yTop, right, yBottom)
        fill.color = c(palette.terminalBg); canvas.drawRoundRect(rect, 30f, 30f, fill)
        val head = text.paint(TextKit.Face.MONO, 26f, c(palette.textMuted))
        canvas.drawText("Terminal", left + 30f, yTop + 46f, head)
        var y = yTop + 70f
        val w = (contentW - 60).toInt()
        // last lines that fit
        val layouts = code.terminal.map { line ->
            val color = when (line.kind) { TermKind.CMD -> palette.textMuted; TermKind.OUT -> palette.terminalText; TermKind.ERR -> palette.terminalError; TermKind.OK -> palette.terminalOk }
            text.layout(line.text.take(line.visibleChars), w, Tokens.Type.TERMINAL, c(color), if (line.kind == TermKind.ERR) TextKit.Face.MONO_BOLD else TextKit.Face.MONO, Layout.Alignment.ALIGN_NORMAL, 0, spacing = 1.1f)
        }
        var total = layouts.sumOf { it.height + 6 }.toFloat()
        var start = 0
        while (total > yBottom - y - 16f && start < layouts.size - 1) { total -= layouts[start].height + 6; start++ }
        canvas.save(); canvas.clipRect(left, y, right, yBottom - 10f)
        for (k in start until layouts.size) {
            canvas.save(); canvas.translate(left + 30f, y); layouts[k].draw(canvas); canvas.restore()
            y += layouts[k].height + 6f
        }
        canvas.restore()
    }

    private fun drawCodeText(canvas: Canvas, l: StaticLayout, code: String, size: Float = 34f) {
        // Highlighted code inside chat bubbles / reveal cards: per-token drawing on each layout line.
        val lines = code.split('\n')
        val lh = size * 1.18f * 1.2f
        for ((i, line) in lines.withIndex()) {
            var x = 0f
            val baseline = i * lh + size
            for (tok in SyntaxHighlighter.tokens(line, "python")) {
                val p = text.paint(TextKit.Face.MONO, size, SyntaxHighlighter.color(tok.kind, palette))
                canvas.drawText(tok.text, x, baseline, p)
                x += p.measureText(tok.text)
            }
        }
        @Suppress("UNUSED_VARIABLE") val unused = l
    }

    // ------------------------------------------------------------------ hook & cards

    private fun drawHook(canvas: Canvas, hook: HookView) {
        val cx = W * 0.46f
        val e = hook.enter
        canvas.save()
        canvas.translate(0f, (1f - e) * 8f)
        // signature label
        val label = text.layout("توقّع قبل أن أشغّل", contentW.toInt(), 44f, c(palette.focus), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1)
        canvas.save(); canvas.translate(left, top + 150f); label.draw(canvas); canvas.restore()
        // code card with a soft pulse
        val lines = hook.code.split('\n')
        val size = fitMonoSize(lines.maxOf { it.length }, contentW - 120f).coerceAtMost(48f)
        val lh = size * 1.55f
        val cardH = lines.size * lh + 90f
        val cardTop = top + 280f
        val s = 1f + 0.015f * hook.pulse
        canvas.save(); canvas.scale(s, s, cx, cardTop + cardH / 2)
        rect.set(left, cardTop, right, cardTop + cardH)
        fill.color = c(palette.codeBg); canvas.drawRoundRect(rect, 36f, 36f, fill)
        stroke.color = alpha(c(palette.focus), 0.25f + 0.5f * hook.pulse); stroke.strokeWidth = 4f
        canvas.drawRoundRect(rect, 36f, 36f, stroke)
        for ((i, line) in lines.withIndex()) {
            var x = left + 50f
            val baseline = cardTop + 45f + i * lh + lh * 0.7f
            for (tok in SyntaxHighlighter.tokens(line, hook.language)) {
                val p = text.paint(TextKit.Face.MONO, size, SyntaxHighlighter.color(tok.kind, palette))
                canvas.drawText(tok.text, x, baseline, p); x += p.measureText(tok.text)
            }
        }
        canvas.restore()
        // question
        val q = text.layout(hook.question, contentW.toInt(), 64f, c(palette.text), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1)
        withAlpha(canvas, hook.questionAppear) {
            canvas.save(); canvas.translate(left, cardTop + cardH + 70f + (1f - hook.questionAppear) * 8f); q.draw(canvas); canvas.restore()
        }
        // gentle countdown ring
        val ringY = cardTop + cardH + 70f + q.height + 150f
        stroke.strokeWidth = 10f; stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = alpha(c(palette.textMuted), 0.18f); canvas.drawCircle(cx, ringY, 64f, stroke)
        stroke.color = alpha(c(palette.focus), 0.85f)
        rect.set(cx - 64f, ringY - 64f, cx + 64f, ringY + 64f)
        canvas.drawArc(rect, -90f, 360f * hook.ring, false, stroke)
        canvas.restore()
    }

    private fun drawCard(canvas: Canvas, card: CardView) {
        withAlpha(canvas, card.appear) {
            val s = 1f + 0.03f * card.zoom
            canvas.save(); canvas.scale(s, s, W * 0.46f, H * 0.45f)
            val labelColor = when (card.kind) { CardKind.KEY_SENTENCE -> c(palette.focus); else -> c(palette.textMuted) }
            val label = text.layout(card.label, contentW.toInt(), Tokens.Type.CARD_LABEL, labelColor, TextKit.Face.SEMI, Layout.Alignment.ALIGN_CENTER, 1)
            val size = if (card.kind == CardKind.KEY_SENTENCE) Tokens.Type.KEY_SENTENCE else Tokens.Type.CARD_TEXT
            val body = text.layout(card.text, (contentW - 60).toInt(), size, c(palette.text), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1, spacing = 1.2f)
            val sub = card.sub?.let { text.layout("💬 $it", (contentW - 80).toInt(), 40f, c(palette.focus), TextKit.Face.SEMI, Layout.Alignment.ALIGN_CENTER, 1) }
            val total = label.height + 50f + body.height + (sub?.let { it.height + 80f } ?: 0f)
            var y = (top + bottom) / 2 - total / 2
            rect.set(left, y - 70f, right, y + total + 70f)
            fill.color = alpha(c(palette.surface), 0.92f); canvas.drawRoundRect(rect, 48f, 48f, fill)
            if (card.kind == CardKind.KEY_SENTENCE) {
                stroke.color = alpha(c(palette.focus), 0.6f); stroke.strokeWidth = 4f; canvas.drawRoundRect(rect, 48f, 48f, stroke)
            }
            canvas.save(); canvas.translate(left, y); label.draw(canvas); canvas.restore()
            y += label.height + 50f
            canvas.save(); canvas.translate(left + 30f, y); body.draw(canvas); canvas.restore()
            y += body.height + 80f
            sub?.let { canvas.save(); canvas.translate(left + 40f, y); it.draw(canvas); canvas.restore() }
            canvas.restore()
        }
    }

    private fun drawEngageChip(canvas: Canvas, actions: List<String>, appear: Float, heart: Float) {
        if (appear <= 0.01f) return
        withAlpha(canvas, appear) {
            val h = 112f
            val y = bottom - h - 150f + (1f - appear) * 8f
            val itemW = 220f
            val w = itemW * actions.size + 40f
            val x0 = (left + right) / 2 - w / 2
            rect.set(x0, y, x0 + w, y + h)
            fill.color = alpha(c(palette.surfaceHigh), 0.96f); canvas.drawRoundRect(rect, h / 2, h / 2, fill)
            stroke.color = alpha(c(palette.focus), 0.5f); stroke.strokeWidth = 3f; canvas.drawRoundRect(rect, h / 2, h / 2, stroke)
            actions.forEachIndexed { i, a ->
                val cx = x0 + w - 20f - itemW * (i + 0.5f) // RTL order
                val iconX = cx + 60f
                val cy = y + h / 2
                when (a) {
                    "like" -> drawHeart(canvas, iconX, cy, 26f * (1f + 0.12f * heart))
                    "follow" -> drawFollow(canvas, iconX, cy)
                    else -> drawShare(canvas, iconX, cy)
                }
                val label = when (a) { "like" -> "إعجاب"; "follow" -> "متابعة"; else -> "مشاركة" }
                val p = text.paint(TextKit.Face.SEMI, Tokens.Type.CHIP, c(palette.text))
                canvas.drawText(label, iconX - 36f - p.measureText(label), cy + 12f, p)
            }
        }
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.moveTo(cx, cy + r)
        path.cubicTo(cx - r * 1.6f, cy - r * 0.2f, cx - r * 0.6f, cy - r * 1.3f, cx, cy - r * 0.45f)
        path.cubicTo(cx + r * 0.6f, cy - r * 1.3f, cx + r * 1.6f, cy - r * 0.2f, cx, cy + r)
        fill.color = 0xFFE88A8A.toInt(); canvas.drawPath(path, fill)
    }

    private fun drawFollow(canvas: Canvas, cx: Float, cy: Float) {
        fill.color = c(palette.tickRead)
        canvas.drawCircle(cx - 6f, cy - 10f, 12f, fill)
        rect.set(cx - 28f, cy + 4f, cx + 16f, cy + 26f); canvas.drawRoundRect(rect, 12f, 12f, fill)
        stroke.color = c(palette.tickRead); stroke.strokeWidth = 5f; stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(cx + 22f, cy - 10f, cx + 22f, cy + 12f, stroke); canvas.drawLine(cx + 11f, cy + 1f, cx + 33f, cy + 1f, stroke)
    }

    private fun drawShare(canvas: Canvas, cx: Float, cy: Float) {
        stroke.color = c(palette.terminalOk); stroke.strokeWidth = 5f; stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeJoin = Paint.Join.ROUND
        path.reset(); path.moveTo(cx - 22f, cy + 18f); path.quadTo(cx - 18f, cy - 12f, cx + 14f, cy - 12f)
        canvas.drawPath(path, stroke)
        path.reset(); path.moveTo(cx + 4f, cy - 24f); path.lineTo(cx + 18f, cy - 12f); path.lineTo(cx + 4f, cy)
        canvas.drawPath(path, stroke)
    }

    // ------------------------------------------------------------------ cover

    /** Automatic cover image (1080×1920) with the lesson title in the calm style. */
    fun drawCover(canvas: Canvas) {
        canvas.drawRect(0f, 0f, W, H, bgPaint)
        val m = scenario.meta
        var y = top + 60f
        m.series.episode?.let {
            val chip = text.layout("الحلقة $it", contentW.toInt(), 44f, c(palette.focus), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1)
            canvas.save(); canvas.translate(left, y); chip.draw(canvas); canvas.restore(); y += chip.height + 40f
        }
        val title = text.layout(m.title, contentW.toInt(), 110f, c(palette.text), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1, spacing = 1.15f)
        canvas.save(); canvas.translate(left, y); title.draw(canvas); canvas.restore(); y += title.height + 70f
        scenario.scenes.firstOrNull { it is studio.pyreels.core.model.Scene.HookPredict }?.let { hook ->
            hook as studio.pyreels.core.model.Scene.HookPredict
            val lines = hook.code.split('\n')
            val size = fitMonoSize(lines.maxOf { it.length }, contentW - 120f).coerceAtMost(46f)
            val lh = size * 1.55f
            val cardH = lines.size * lh + 80f
            rect.set(left, y, right, y + cardH)
            fill.color = c(palette.codeBg); canvas.drawRoundRect(rect, 36f, 36f, fill)
            stroke.color = alpha(c(palette.focus), 0.5f); stroke.strokeWidth = 4f; canvas.drawRoundRect(rect, 36f, 36f, stroke)
            for ((i, line) in lines.withIndex()) {
                var x = left + 50f
                val baseline = y + 40f + i * lh + lh * 0.7f
                for (tok in SyntaxHighlighter.tokens(line, hook.language)) {
                    val p = text.paint(TextKit.Face.MONO, size, SyntaxHighlighter.color(tok.kind, palette))
                    canvas.drawText(tok.text, x, baseline, p); x += p.measureText(tok.text)
                }
            }
            y += cardH + 50f
            val q = text.layout("توقّع قبل أن أشغّل 👀", contentW.toInt(), 56f, c(palette.focus), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1)
            canvas.save(); canvas.translate(left, y); q.draw(canvas); canvas.restore(); y += q.height + 60f
        }
        // cast row
        val n = scenario.cast.size
        val gap = 130f
        var x = (left + right) / 2 + (n - 1) * gap / 2
        for (member in scenario.cast) { drawAvatar(canvas, member.id, x, y + 60f, 54f); x -= gap }
        val series = text.layout(m.series.name ?: "تعلّم البرمجة مع أحمد", contentW.toInt(), 40f, c(palette.textMuted), TextKit.Face.SEMI, Layout.Alignment.ALIGN_CENTER, 1)
        canvas.save(); canvas.translate(left, bottom - series.height - 10f); series.draw(canvas); canvas.restore()
    }
}
