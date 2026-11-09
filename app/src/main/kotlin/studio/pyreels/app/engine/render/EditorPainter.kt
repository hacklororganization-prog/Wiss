package studio.pyreels.app.engine.render

import android.graphics.Canvas
import android.text.Layout
import studio.pyreels.core.model.Limits
import studio.pyreels.core.timeline.CodeView
import studio.pyreels.core.timeline.TermKind
import kotlin.math.max
import kotlin.math.min

/**
 * Mobile code-editor app inside the phone: app bar with the file name, highlighted code with line
 * numbers, active-line band and focus frame, a symbol bar, an on-screen keyboard that shows the key
 * being pressed, a ▶ run button, a variables (debugger) panel, a terminal sheet that slides up, and
 * the teacher's explanation arriving as a heads-up notification.
 */
class EditorPainter(private val g: Gfx, private val w: Float, private val h: Float) {
    private val s = g.skin
    private val text = g.text
    private val barBottom = 150f
    private val symH = 68f
    private val kbFull = 430f
    private val gutterW = 74f

    fun draw(canvas: Canvas, code: CodeView) {
        g.fill.color = s.edBg
        canvas.drawRect(0f, 0f, w, h, g.fill)
        val kbH = kbFull * code.keyboard
        val editorBottom = h - symH - kbH
        drawCode(canvas, code, editorBottom)
        drawAppBar(canvas, code)
        drawSymbolBar(canvas, code, editorBottom)
        if (kbH > 1f) drawKeyboard(canvas, code, h - kbH)
        val termH = 400f * code.terminalVisible
        if (code.terminalVisible <= 0.01f || code.terminal.isEmpty()) drawFab(canvas, code, editorBottom)
        if (code.vars.isNotEmpty() || code.iteration != null || code.stack.isNotEmpty()) drawTracePanel(canvas, code, h - max(termH, kbH + symH) - 176f)
        if (termH > 1f && code.terminal.isNotEmpty()) drawTerminal(canvas, code, h - termH)
        code.note?.let { drawNotification(canvas, it.from, it.text, it.appear, it.karaokeWord) }
    }

    private fun drawAppBar(canvas: Canvas, code: CodeView) {
        g.fill.color = s.edBar
        canvas.drawRect(0f, 0f, w, barBottom, g.fill)
        g.statusBar(canvas, w, s.edBarText)
        val cy = 52f + (barBottom - 52f) / 2
        g.iconMenu(canvas, 44f, cy, 40f, s.edBarText)
        val tp = text.paint(TextKit.Face.MONO_BOLD, 32f, s.edBarText)
        canvas.drawText(code.fileName, 92f, cy + 11f, tp)
        val tw = tp.measureText(code.fileName)
        if (code.modified) { g.fill.color = g.c(g.p.focus); canvas.drawCircle(92f + tw + 18f, cy, 7f, g.fill) }
        // tab underline
        g.fill.color = g.c(g.p.focus); canvas.drawRect(84f, barBottom - 6f, 100f + tw, barBottom, g.fill)
        g.iconDots(canvas, w - 36f, cy, 40f, s.edBarText)
        if (code.running) {
            val rp = text.paint(TextKit.Face.MONO, 24f, g.c(g.p.terminalOk))
            canvas.drawText("● running", w - 210f, cy + 8f, rp)
        }
    }

    private fun monoSize(lines: List<String>): Float {
        val maxChars = max(1, lines.maxOfOrNull { it.length } ?: 1)
        val p = text.paint(TextKit.Face.MONO, 36f, s.kbText)
        val charW = p.measureText("M") / 36f
        val byWidth = (w - gutterW - 40f) / (maxChars * charW)
        val area = h - barBottom - symH - kbFull - 30f
        val byHeight = area / (max(lines.size, 6) * 1.45f)
        return min(36f, min(byWidth, byHeight)).coerceAtLeast(22f)
    }

    private fun drawCode(canvas: Canvas, code: CodeView, editorBottom: Float) {
        val lines = code.text.split('\n')
        val size = monoSize(lines)
        val lh = size * 1.45f
        val top = barBottom + 18f
        g.fill.color = s.edGutter
        canvas.drawRect(0f, barBottom, gutterW, editorBottom, g.fill)
        canvas.save()
        canvas.clipRect(0f, barBottom, w, editorBottom)
        if (code.zoom > 0f && code.activeLine > 0) canvas.scale(1f + code.zoom, 1f + code.zoom, w / 2, top + (code.activeLine - 0.5f) * lh)
        if (code.activeLine in 1..lines.size) {
            g.rect.set(gutterW, top + (code.activeLine - 1) * lh, w, top + code.activeLine * lh)
            g.fill.color = g.alpha(g.c(g.p.highlight), 0.35f + 0.65f * code.activeLineMix); canvas.drawRect(g.rect, g.fill)
            g.fill.color = g.alpha(g.c(g.p.focus), code.activeLineMix); canvas.drawRect(gutterW - 6f, g.rect.top, gutterW, g.rect.bottom, g.fill)
        }
        if (code.errorLine in 1..lines.size) {
            g.rect.set(gutterW, top + (code.errorLine - 1) * lh, w, top + code.errorLine * lh)
            g.fill.color = g.alpha(g.c(g.p.terminalError), 0.16f); canvas.drawRect(g.rect, g.fill)
            // calm wavy underline
            g.stroke.color = g.alpha(g.c(g.p.terminalError), 0.8f); g.stroke.strokeWidth = 3f
            g.path.reset()
            var x = gutterW + 16f
            g.path.moveTo(x, g.rect.bottom - 6f)
            while (x < w - 40f) { g.path.quadTo(x + 6f, g.rect.bottom - 12f, x + 12f, g.rect.bottom - 6f); x += 12f }
            canvas.drawPath(g.path, g.stroke)
        }
        if (code.focusLines.isNotEmpty() && code.activeLineMix > 0f) {
            val a = code.focusLines.min().coerceIn(1, lines.size)
            val b = code.focusLines.max().coerceIn(1, lines.size)
            g.rect.set(gutterW + 6f, top + (a - 1) * lh - 2f, w - 10f, top + b * lh + 2f)
            g.stroke.color = g.alpha(g.c(g.p.focus), 0.8f * code.activeLineMix); g.stroke.strokeWidth = 3f
            canvas.drawRoundRect(g.rect, 12f, 12f, g.stroke)
        }
        val num = text.paint(TextKit.Face.MONO, size * 0.78f, g.c(g.p.lineNumber))
        for ((i, line) in lines.withIndex()) {
            val ln = i + 1
            val base = top + i * lh + lh * 0.72f
            val dimmed = code.dim > 0f && ln != code.activeLine && (code.focusLines.isEmpty() || ln !in code.focusLines || code.activeLine > 0)
            val a = if (dimmed) 1f - code.dim else 1f
            val ns = ln.toString()
            canvas.drawText(ns, gutterW - 14f - num.measureText(ns), base, num)
            var x = gutterW + 16f
            for (tok in SyntaxHighlighter.tokens(line, code.language)) {
                val p = text.paint(if (tok.kind == SyntaxHighlighter.Kind.KEYWORD) TextKit.Face.MONO_BOLD else TextKit.Face.MONO, size, SyntaxHighlighter.color(tok.kind, g.p))
                if (a < 1f) p.alpha = (255 * a).toInt()
                canvas.drawText(tok.text, x, base, p)
                x += p.measureText(tok.text)
                if (a < 1f) p.alpha = 255
            }
        }
        if (code.cursorVisible && code.cursorIndex >= 0) {
            val last = lines.last()
            val cw = text.paint(TextKit.Face.MONO, size, s.kbText).measureText(last)
            val cy = top + lines.lastIndex * lh
            g.fill.color = g.c(g.p.focus)
            canvas.drawRect(gutterW + 16f + cw + 1f, cy + lh * 0.15f, gutterW + 16f + cw + 5f, cy + lh * 0.88f, g.fill)
        }
        canvas.restore()
    }

    private val symbols = listOf("⇥", "(", ")", ":", "\"", "'", "=", "[", "]", "#", ".")

    private fun drawSymbolBar(canvas: Canvas, code: CodeView, y: Float) {
        g.fill.color = s.edSymbolBar
        canvas.drawRect(0f, y, w, y + symH, g.fill)
        val kw = w / symbols.size
        val pressed = code.pressedKey?.toString()
        val tp = text.paint(TextKit.Face.MONO_BOLD, 30f, s.kbText)
        for ((i, k) in symbols.withIndex()) {
            val cx = kw * i + kw / 2
            if (k == pressed || (k == "⇥" && code.pressedKey == '\t')) {
                g.rect.set(cx - kw / 2 + 4f, y + 8f, cx + kw / 2 - 4f, y + symH - 8f)
                g.fill.color = g.alpha(g.c(g.p.focus), 0.45f); canvas.drawRoundRect(g.rect, 10f, 10f, g.fill)
            }
            canvas.drawText(k, cx - tp.measureText(k) / 2, y + symH / 2 + 11f, tp)
        }
    }

    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    private fun drawKeyboard(canvas: Canvas, code: CodeView, top: Float) {
        g.fill.color = s.kbBg
        canvas.drawRect(0f, top, w, top + kbFull, g.fill)
        val pk = code.pressedKey?.lowercaseChar()
        val keyW = (w - 22f) / 10f
        val keyH = 84f
        val tp = text.paint(TextKit.Face.REGULAR, 34f, s.kbText)
        for ((r, row) in rows.withIndex()) {
            val y = top + 16f + r * (keyH + 14f)
            val offset = (10 - row.length) * keyW / 2 + 11f + (if (r == 2) 0f else 0f)
            for ((i, ch) in row.withIndex()) {
                val x = offset + i * keyW
                key(canvas, x + 4f, y, x + keyW - 4f, y + keyH, ch.toString(), ch == pk, tp)
            }
            if (r == 2) {
                key(canvas, 11f, y, 11f + keyW * 1.4f, y + keyH, "⇧", false, tp, dark = true)
                key(canvas, w - 11f - keyW * 1.4f, y, w - 11f, y + keyH, "⌫", pk == '\b', tp, dark = true)
            }
        }
        val y = top + 16f + 3 * (keyH + 14f)
        key(canvas, 11f, y, 11f + keyW * 1.6f, y + keyH, "?123", pk != null && !pk.isLetter() && pk != ' ' && pk != '\n' && pk != '\b' && pk.toString() !in symbols, tp, dark = true)
        key(canvas, 11f + keyW * 1.75f, y, w - 11f - keyW * 1.75f, y + keyH, "", pk == ' ', tp)
        key(canvas, w - 11f - keyW * 1.6f, y, w - 11f, y + keyH, "⏎", pk == '\n', tp, dark = true)
    }

    private fun key(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, label: String, pressed: Boolean, tp: android.text.TextPaint, dark: Boolean = false) {
        g.rect.set(x0, y0, x1, y1)
        g.fill.color = if (pressed) g.c(g.p.focus) else if (dark) s.kbKeyDark else s.kbKey
        canvas.drawRoundRect(g.rect, 12f, 12f, g.fill)
        if (label.isNotEmpty()) {
            val lw = tp.measureText(label)
            val old = tp.color
            if (pressed) tp.color = s.kbBg
            canvas.drawText(label, (x0 + x1) / 2 - lw / 2, (y0 + y1) / 2 + 12f, tp)
            tp.color = old
        }
        if (pressed) {
            // key preview bubble above the key, like a phone keyboard
            if (label.length == 1) {
                g.rect.set(x0 - 6f, y0 - 92f, x1 + 6f, y0 - 6f)
                g.fill.color = s.kbKey; canvas.drawRoundRect(g.rect, 14f, 14f, g.fill)
                val bp = text.paint(TextKit.Face.SEMI, 50f, s.kbText)
                canvas.drawText(label, (x0 + x1) / 2 - bp.measureText(label) / 2, y0 - 32f, bp)
            }
        }
    }

    private fun drawFab(canvas: Canvas, code: CodeView, editorBottom: Float) {
        val cx = w - 84f
        val cy = editorBottom - 84f
        val sc = 1f - 0.08f * code.runPress
        if (code.runPress > 0.01f) {
            g.stroke.color = g.alpha(s.fab, 0.5f * code.runPress); g.stroke.strokeWidth = 6f
            canvas.drawCircle(cx, cy, 52f + 30f * code.runPress, g.stroke)
        }
        g.fill.color = g.alpha(0xFF000000.toInt(), 0.25f); canvas.drawCircle(cx, cy + 5f, 50f * sc, g.fill)
        g.fill.color = s.fab; canvas.drawCircle(cx, cy, 50f * sc, g.fill)
        g.iconPlay(canvas, cx + 4f, cy, 52f * sc, s.kbBg)
    }

    private fun drawTracePanel(canvas: Canvas, code: CodeView, y: Float) {
        val x0 = 18f; val x1 = w - 18f
        g.rect.set(x0, y, x1, y + 158f)
        g.fill.color = g.alpha(s.edBar, 0.97f); canvas.drawRoundRect(g.rect, 24f, 24f, g.fill)
        g.stroke.color = g.alpha(g.c(g.p.focus), 0.35f); g.stroke.strokeWidth = 2f; canvas.drawRoundRect(g.rect, 24f, 24f, g.stroke)
        val title = text.layout("المتغيرات", 300, 24f, g.c(g.p.textMuted), TextKit.Face.SEMI, Layout.Alignment.ALIGN_NORMAL, 1, maxLines = 1)
        g.drawRtl(canvas, title, x1 - 320f, 300f, y + 8f)
        // iteration + call stack chips on the left
        var cx = x0 + 18f
        code.iteration?.let {
            val l = text.layout("الدورة $it", 220, 28f, g.c(g.p.focus), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1, maxLines = 1)
            val cw = text.maxLineWidth(l) + 36f
            g.rect2.set(cx, y + 12f, cx + cw, y + 56f)
            g.fill.color = g.alpha(g.c(g.p.focus), 0.16f); canvas.drawRoundRect(g.rect2, 22f, 22f, g.fill)
            g.drawAt(canvas, l, cx + cw / 2 - 110f, y + 34f - l.height / 2f)
            cx += cw + 10f
        }
        if (code.stack.isNotEmpty()) {
            val st = code.stack.joinToString(" › ")
            val sp = text.paint(TextKit.Face.MONO, 26f, g.c(g.p.codeFunction))
            val cw = sp.measureText(st) + 30f
            g.rect2.set(cx, y + 12f, cx + cw, y + 56f)
            g.fill.color = s.kbKey; canvas.drawRoundRect(g.rect2, 22f, 22f, g.fill)
            canvas.drawText(st, cx + 15f, y + 44f, sp)
        }
        // variable boxes, right to left
        var bx = x1 - 18f
        for (v in code.vars.take(4)) {
            val np = text.paint(TextKit.Face.MONO, 24f, g.c(g.p.textMuted))
            val vp = text.paint(TextKit.Face.MONO_BOLD, 34f, s.kbText)
            val bw = max(120f, max(np.measureText(v.name), max(vp.measureText(v.value), v.previous?.let { vp.measureText(it) } ?: 0f)) + 40f)
            if (bx - bw < x0 + 10f) break
            val l0 = bx - bw
            g.rect2.set(l0, y + 66f, bx, y + 144f)
            g.fill.color = s.kbKey; canvas.drawRoundRect(g.rect2, 16f, 16f, g.fill)
            val changing = v.change < 1f
            g.stroke.color = g.alpha(g.c(g.p.focus), if (changing) 0.35f + 0.65f * (1f - v.change) else 0.3f); g.stroke.strokeWidth = 3f
            canvas.drawRoundRect(g.rect2, 16f, 16f, g.stroke)
            g.fill.color = s.edBar
            val nw = np.measureText(v.name) + 16f
            canvas.drawRect(l0 + 14f, y + 54f, l0 + 14f + nw, y + 76f, g.fill)
            canvas.drawText(v.name, l0 + 22f, y + 74f, np)
            canvas.save(); canvas.clipRect(g.rect2)
            val previous = v.previous
            if (changing && previous != null) {
                vp.alpha = ((1f - v.change) * 255).toInt()
                canvas.drawText(previous, l0 + 20f, y + 122f - v.change * 28f, vp)
                vp.alpha = (v.change * 255).toInt()
                canvas.drawText(v.value, l0 + 20f, y + 122f + (1f - v.change) * 28f, vp)
                vp.alpha = 255
            } else canvas.drawText(v.value, l0 + 20f, y + 122f, vp)
            canvas.restore()
            bx = l0 - 14f
        }
    }

    private fun drawTerminal(canvas: Canvas, code: CodeView, top: Float) {
        g.rect.set(0f, top, w, h + 40f)
        g.fill.color = s.termBg; canvas.drawRoundRect(g.rect, 30f, 30f, g.fill)
        g.fill.color = g.alpha(s.kbText, 0.25f)
        g.rect2.set(w / 2 - 40f, top + 12f, w / 2 + 40f, top + 20f); canvas.drawRoundRect(g.rect2, 4f, 4f, g.fill)
        val hp = text.paint(TextKit.Face.MONO_BOLD, 26f, g.c(g.p.textMuted))
        canvas.drawText("Terminal", 30f, top + 56f, hp)
        var y = top + 80f
        val tw = (w - 60f).toInt()
        val layouts = code.terminal.map { line ->
            val color = when (line.kind) { TermKind.CMD -> g.p.textMuted; TermKind.OUT -> g.p.terminalText; TermKind.ERR -> g.p.terminalError; TermKind.OK -> g.p.terminalOk }
            text.layout(line.text.take(line.visibleChars), tw, 32f, g.c(color), if (line.kind == TermKind.ERR) TextKit.Face.MONO_BOLD else TextKit.Face.MONO, Layout.Alignment.ALIGN_NORMAL, 0, spacing = 1.05f)
        }
        var total = layouts.sumOf { it.height + 8 }.toFloat()
        var start = 0
        while (total > h - y - 20f && start < layouts.size - 1) { total -= layouts[start].height + 8; start++ }
        canvas.save(); canvas.clipRect(0f, y, w, h - 10f)
        for (k in start until layouts.size) {
            if (code.terminal[k].kind == TermKind.ERR) {
                g.fill.color = g.alpha(g.c(g.p.terminalError), 0.12f)
                canvas.drawRect(20f, y - 4f, w - 20f, y + layouts[k].height + 4f, g.fill)
            }
            g.drawAt(canvas, layouts[k], 30f, y)
            y += layouts[k].height + 8f
        }
        canvas.restore()
    }

    /** The teacher's explanation drops from the top of the screen as a heads-up notification. */
    private fun drawNotification(canvas: Canvas, from: String, msg: String, appear: Float, karaoke: Int) {
        if (appear <= 0.01f) return
        val l = text.layout(msg.replace("`", ""), (w - 170).toInt(), 34f, s.waText, TextKit.Face.REGULAR, Layout.Alignment.ALIGN_NORMAL, 1, karaoke, g.c(g.p.focus), spacing = 0.92f)
        val name = text.layout(g.nameOf(from) + " · الآن", (w - 170).toInt(), 26f, g.accent(from), TextKit.Face.BOLD, Layout.Alignment.ALIGN_NORMAL, 1, maxLines = 1)
        val hh = l.height + name.height + 44f
        val top = 60f - (1f - appear) * (hh + 60f)
        g.withAlpha(canvas, appear, w, h) {
            g.rect.set(16f, top, w - 16f, top + hh)
            g.fill.color = g.alpha(0xFF000000.toInt(), 0.25f); canvas.drawRoundRect(16f, top + 6f, w - 16f, top + hh + 6f, 30f, 30f, g.fill)
            g.fill.color = s.waIn; canvas.drawRoundRect(g.rect, 30f, 30f, g.fill)
            g.avatar(canvas, from, w - 62f, top + 50f, 32f)
            g.drawRtl(canvas, name, 36f, w - 150f, top + 14f)
            g.drawRtl(canvas, l, 36f, w - 150f, top + 18f + name.height)
        }
    }

    @Suppress("unused") private val cap = Limits.MAX_CODE_LINES
}
