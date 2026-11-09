package studio.pyreels.app.engine.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.text.Layout
import studio.pyreels.core.model.Limits
import studio.pyreels.core.model.Palette
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.Scene
import studio.pyreels.core.model.Tokens
import studio.pyreels.core.timeline.CardKind
import studio.pyreels.core.timeline.CardView
import studio.pyreels.core.timeline.Frame
import studio.pyreels.core.timeline.HookView
import studio.pyreels.core.timeline.Layer
import studio.pyreels.core.timeline.Stage

/**
 * Draws a [Frame] (pure render model of the core timeline) in the 1080×1920 video space.
 *
 * Composition: a calm gradient background, a phone in the safe area that runs two "apps" — a group
 * messenger (CHAT stage) and a code editor (CODE stage) — switching like apps; the signature hook
 * and the recap / sentence-of-the-day / teaser cards are full designed cards. Preview and export use
 * this same renderer (WYSIWYG). Steady-state drawing allocates nothing: paints, paths and text
 * layouts are cached.
 */
class FrameRenderer(context: Context, private val scenario: Scenario) {

    private val palette: Palette = Tokens.palette(scenario.meta.theme)
    private val skin = Skin(palette)
    private val text = TextKit(Fonts.get(context))
    private val g = Gfx(text, skin, scenario)

    private val W = Limits.WIDTH.toFloat()
    private val H = Limits.HEIGHT.toFloat()
    private val safeLeft = W * Limits.SAFE_LEFT
    private val safeRight = W * (1f - Limits.SAFE_RIGHT)
    private val safeTop = H * Limits.SAFE_TOP
    private val safeBottom = H * (1f - Limits.SAFE_BOTTOM)

    // phone geometry (inside the safe area)
    private val px0 = 66f
    private val px1 = safeRight - 4f
    private val py0 = safeTop + 6f
    private val py1 = safeBottom - 6f
    private val bezel = 13f
    private val sx0 = px0 + bezel
    private val sy0 = py0 + bezel
    private val sw = px1 - px0 - 2 * bezel
    private val sh = py1 - py0 - 2 * bezel
    private val screenRadius = 54f
    private val screenClip = Path().apply { addRoundRect(0f, 0f, sw, sh, screenRadius, screenRadius, Path.Direction.CW) }

    private val chat = ChatPainter(g, scenario, sw, sh)
    private val editor = EditorPainter(g, sw, sh)

    private val bgPaint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, H, palette.backgroundTop.toInt(), palette.background.toInt(), Shader.TileMode.CLAMP)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(W * 0.8f, H * 0.18f, W * 0.7f, g.alpha(palette.focus.toInt(), if (palette.isDark) 0.10f else 0.12f), 0, Shader.TileMode.CLAMP)
    }
    private val glow2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(W * 0.1f, H * 0.85f, W * 0.8f, g.alpha(palette.tickRead.toInt(), if (palette.isDark) 0.08f else 0.10f), 0, Shader.TileMode.CLAMP)
    }

    var showSafeZones = false

    fun draw(canvas: Canvas, frame: Frame) {
        background(canvas)
        val phoneAlpha = frame.layers.filter { it.stage == Stage.CHAT || it.stage == Stage.CODE }.sumOf { it.alpha.toDouble() }.toFloat().coerceAtMost(1f)
        if (phoneAlpha > 0.01f) {
            g.withAlpha(canvas, phoneAlpha, W, H) { phoneBody(canvas) }
            canvas.save()
            canvas.translate(sx0, sy0)
            canvas.clipPath(screenClip)
            for (layer in frame.layers) if (layer.stage == Stage.CHAT || layer.stage == Stage.CODE) screenLayer(canvas, layer, frame)
            canvas.restore()
        }
        for (layer in frame.layers) {
            when (layer.stage) {
                Stage.HOOK -> layer.hook?.let { h -> layered(canvas, layer) { drawHook(canvas, h) } }
                Stage.CARD -> layer.card?.let { c -> layered(canvas, layer) { drawCard(canvas, c) } }
                else -> Unit
            }
        }
        chrome(canvas, frame)
        if (showSafeZones) safeZones(canvas)
    }

    private inline fun layered(canvas: Canvas, layer: Layer, block: () -> Unit) {
        g.withAlpha(canvas, layer.alpha, W, H) {
            canvas.save()
            canvas.translate(0f, layer.offsetY)
            if (layer.scale != 1f) canvas.scale(layer.scale, layer.scale, W / 2, H / 2)
            block()
            canvas.restore()
        }
    }

    /** App switch: the leaving app shrinks slightly while fading, the entering one grows into place. */
    private fun screenLayer(canvas: Canvas, layer: Layer, frame: Frame) {
        g.withAlpha(canvas, layer.alpha, sw, sh) {
            canvas.save()
            val s = 0.94f + 0.06f * layer.alpha
            canvas.scale(s, s, sw / 2, sh / 2)
            canvas.translate(0f, layer.offsetY)
            layer.chat?.let { chat.draw(canvas, it, frame.engageChip) }
            layer.code?.let { editor.draw(canvas, it) }
            frame.caption?.let { cap ->
                if (cap.appear > 0.01f) {
                    val l = text.layout(cap.words.joinToString(" "), (sw - 80).toInt(), 40f, palette.text.toInt(), TextKit.Face.SEMI, Layout.Alignment.ALIGN_CENTER, 1, cap.activeWord, palette.focus.toInt(), spacing = 0.95f)
                    val y = sh - 150f - l.height
                    g.withAlpha(canvas, cap.appear, sw, sh) {
                        g.rect.set(24f, y - 16f, sw - 24f, y + l.height + 16f)
                        g.fill.color = g.alpha(palette.surface.toInt(), 0.92f); canvas.drawRoundRect(g.rect, 24f, 24f, g.fill)
                        g.drawAt(canvas, l, 40f, y)
                    }
                }
            }
            canvas.restore()
        }
    }

    private fun background(canvas: Canvas) {
        canvas.drawRect(0f, 0f, W, H, bgPaint)
        canvas.drawRect(0f, 0f, W, H, glowPaint)
        canvas.drawRect(0f, 0f, W, H, glow2)
    }

    private fun phoneBody(canvas: Canvas) {
        // soft shadow, bezel, edge highlight and side buttons
        g.fill.color = g.alpha(0xFF000000.toInt(), if (palette.isDark) 0.45f else 0.18f)
        g.rect.set(px0 + 6f, py0 + 18f, px1 + 6f, py1 + 22f); canvas.drawRoundRect(g.rect, 72f, 72f, g.fill)
        g.fill.color = skin.bezel
        g.rect.set(px0, py0, px1, py1); canvas.drawRoundRect(g.rect, 68f, 68f, g.fill)
        g.stroke.color = skin.bezelEdge; g.stroke.strokeWidth = 3f
        canvas.drawRoundRect(g.rect, 68f, 68f, g.stroke)
        g.fill.color = skin.bezelEdge
        g.rect.set(px1 - 1f, py0 + 220f, px1 + 5f, py0 + 330f); canvas.drawRoundRect(g.rect, 3f, 3f, g.fill)
        g.rect.set(px0 - 5f, py0 + 260f, px0 + 1f, py0 + 340f); canvas.drawRoundRect(g.rect, 3f, 3f, g.fill)
    }

    // ------------------------------------------------------------------ chrome

    private fun chrome(canvas: Canvas, f: Frame) {
        // thin progress bar (fills from the right: RTL)
        g.fill.color = g.alpha(palette.textMuted.toInt(), 0.22f); canvas.drawRect(0f, 0f, W, 8f, g.fill)
        g.fill.color = palette.focus.toInt(); canvas.drawRect(W - W * f.progress, 0f, W, 8f, g.fill)
        // lesson chip above the phone
        val title = (f.episode?.let { "الحلقة $it · " } ?: "") + f.title
        val l = text.layout(title, (safeRight - safeLeft - 40).toInt(), 32f, palette.text.toInt(), TextKit.Face.SEMI, Layout.Alignment.ALIGN_CENTER, 1, maxLines = 1)
        val tw = text.maxLineWidth(l) + 56f
        val cx = (px0 + px1) / 2
        val y = safeTop - 74f
        g.rect.set(cx - tw / 2, y, cx + tw / 2, y + 60f)
        g.fill.color = g.alpha(palette.surface.toInt(), 0.92f); canvas.drawRoundRect(g.rect, 30f, 30f, g.fill)
        g.fill.color = palette.focus.toInt(); canvas.drawCircle(cx + tw / 2 - 26f, y + 30f, 7f, g.fill)
        g.drawAt(canvas, l, cx - l.width / 2f - 8f, y + 30f - l.height / 2f)

    }

    private fun safeZones(canvas: Canvas) {
        g.fill.color = palette.safeZone.toInt()
        canvas.drawRect(0f, 0f, W, safeTop, g.fill)
        canvas.drawRect(0f, safeBottom, W, H, g.fill)
        canvas.drawRect(safeRight, safeTop, W, safeBottom, g.fill)
        g.stroke.color = g.alpha(palette.terminalError.toInt(), 0.7f); g.stroke.strokeWidth = 3f
        canvas.drawRect(safeLeft, safeTop, safeRight, safeBottom, g.stroke)
    }

    // ------------------------------------------------------------------ engage chip (inside the messenger)

    private fun engageChip(canvas: Canvas, actions: List<String>, appear: Float, heart: Float) {
        if (appear <= 0.01f) return
        g.withAlpha(canvas, appear, sw, sh) {
            val h = 104f
            val y = sh - 112f - h - 26f + (1f - appear) * 10f
            val itemW = 262f
            val w = itemW * actions.size + 30f
            val x0 = sw / 2 - w / 2
            g.rect.set(x0, y, x0 + w, y + h)
            g.fill.color = g.alpha(0xFF000000.toInt(), 0.25f); canvas.drawRoundRect(x0, y + 6f, x0 + w, y + h + 6f, h / 2, h / 2, g.fill)
            g.fill.color = skin.waPill; canvas.drawRoundRect(g.rect, h / 2, h / 2, g.fill)
            g.stroke.color = g.alpha(palette.focus.toInt(), 0.6f); g.stroke.strokeWidth = 3f; canvas.drawRoundRect(g.rect, h / 2, h / 2, g.stroke)
            actions.forEachIndexed { i, a ->
                val cx = x0 + w - 15f - itemW * (i + 0.5f)
                val iconX = cx + 78f
                val cy = y + h / 2
                when (a) {
                    "like" -> g.drawHeart(canvas, iconX, cy, 24f * (1f + 0.15f * heart))
                    "follow" -> g.drawFollow(canvas, iconX, cy, skin.waTick)
                    else -> g.drawShare(canvas, iconX, cy, skin.waFab)
                }
                val label = when (a) { "like" -> "إعجاب"; "follow" -> "متابعة"; else -> "مشاركة" }
                val p = text.paint(TextKit.Face.SEMI, 32f, skin.waText)
                canvas.drawText(label, iconX - 38f - p.measureText(label), cy + 11f, p)
            }
        }
    }

    // ------------------------------------------------------------------ hook

    private fun codeCard(canvas: Canvas, code: String, language: String, x0: Float, y0: Float, x1: Float, maxSize: Float, glow: Float, reveal: Float = 1f): Float {
        val lines = code.split('\n')
        val mp = text.paint(TextKit.Face.MONO, 40f, palette.codeText.toInt())
        val charW = mp.measureText("M") / 40f
        val size = ((x1 - x0 - 100f) / (lines.maxOf { it.length }.coerceAtLeast(1) * charW)).coerceAtMost(maxSize).coerceAtLeast(26f)
        val lh = size * 1.5f
        val barH = 64f
        val y1 = y0 + barH + lines.size * lh + 50f
        if (glow > 0.01f) {
            g.stroke.color = g.alpha(palette.focus.toInt(), 0.18f * glow); g.stroke.strokeWidth = 26f
            g.rect.set(x0, y0, x1, y1); canvas.drawRoundRect(g.rect, 40f, 40f, g.stroke)
        }
        g.rect.set(x0, y0, x1, y1)
        g.fill.color = palette.codeBg.toInt(); canvas.drawRoundRect(g.rect, 40f, 40f, g.fill)
        g.stroke.color = g.alpha(palette.focus.toInt(), 0.25f + 0.55f * glow); g.stroke.strokeWidth = 3f
        canvas.drawRoundRect(g.rect, 40f, 40f, g.stroke)
        val dots = intArrayOf(0xFFE8838A.toInt(), 0xFFE8C872.toInt(), 0xFF9FD3A6.toInt())
        for (i in 0..2) { g.fill.color = g.alpha(dots[i], 0.8f); canvas.drawCircle(x0 + 44f + i * 30f, y0 + 34f, 9f, g.fill) }
        val fp = text.paint(TextKit.Face.MONO, 26f, palette.textMuted.toInt())
        val fn = studio.pyreels.core.timeline.Timeline.fileName(language)
        canvas.drawText(fn, x1 - 36f - fp.measureText(fn), y0 + 43f, fp)
        for ((i, line) in lines.withIndex()) {
            // lines appear one after another
            val a = (reveal * lines.size - i).coerceIn(0f, 1f)
            if (a <= 0f) continue
            var x = x0 + 50f
            val base = y0 + barH + 18f + i * lh + lh * 0.72f + (1f - a) * 10f
            for (tok in SyntaxHighlighter.tokens(line, language)) {
                val p = text.paint(if (tok.kind == SyntaxHighlighter.Kind.KEYWORD) TextKit.Face.MONO_BOLD else TextKit.Face.MONO, size, SyntaxHighlighter.color(tok.kind, palette))
                if (a < 1f) p.alpha = (a * 255).toInt()
                canvas.drawText(tok.text, x, base, p); x += p.measureText(tok.text)
                p.alpha = 255
            }
        }
        return y1
    }

    private fun drawHook(canvas: Canvas, hook: HookView) {
        val x0 = safeLeft + 20f
        val x1 = safeRight - 10f
        canvas.save()
        canvas.translate(0f, (1f - hook.enter) * 10f)
        // signature badge
        val badge = text.layout("توقّع قبل أن أشغّل", 600, 40f, skin.kbBg, TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1, maxLines = 1)
        val bw = text.maxLineWidth(badge) + 80f
        val cx = (x0 + x1) / 2
        var y = safeTop + 120f
        g.rect.set(cx - bw / 2, y, cx + bw / 2, y + 76f)
        g.fill.color = palette.focus.toInt(); canvas.drawRoundRect(g.rect, 38f, 38f, g.fill)
        g.drawAt(canvas, badge, cx - 300f, y + 38f - badge.height / 2f)
        y += 130f
        val s = 1f + 0.012f * hook.pulse
        canvas.save(); canvas.scale(s, s, cx, y + 200f)
        val bottom = codeCard(canvas, hook.code, hook.language, x0, y, x1, 54f, hook.pulse, hook.lineReveal)
        canvas.restore()
        y = bottom + 80f
        val q = text.layout(hook.question, (x1 - x0).toInt(), 70f, palette.text.toInt(), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1, spacing = 0.95f)
        g.withAlpha(canvas, hook.questionAppear, W, H) {
            g.drawAt(canvas, q, x0, y + (1f - hook.questionAppear) * 8f)
        }
        // gentle countdown ring with the remaining seconds
        val ry = y + q.height + 150f
        g.stroke.strokeWidth = 12f
        g.stroke.color = g.alpha(palette.textMuted.toInt(), 0.18f); canvas.drawCircle(cx, ry, 70f, g.stroke)
        g.stroke.color = palette.focus.toInt()
        g.rect.set(cx - 70f, ry - 70f, cx + 70f, ry + 70f)
        canvas.drawArc(g.rect, -90f, 360f * hook.ring, false, g.stroke)
        val secs = kotlin.math.ceil(hook.ring * 2f).toInt().coerceAtLeast(0)
        val np = text.paint(TextKit.Face.BOLD, 56f, palette.text.toInt())
        np.textAlign = Paint.Align.CENTER
        canvas.drawText(if (hook.ring > 0f) secs.toString() else "▶", cx, ry + 20f, np)
        np.textAlign = Paint.Align.LEFT
        canvas.restore()
    }

    // ------------------------------------------------------------------ cards

    private fun drawCard(canvas: Canvas, card: CardView) {
        g.withAlpha(canvas, card.appear, W, H) {
            val s = 1f + 0.025f * card.zoom
            val cx = (safeLeft + safeRight) / 2
            canvas.save(); canvas.scale(s, s, cx, H * 0.45f)
            val x0 = safeLeft + 20f
            val x1 = safeRight - 10f
            val iw = (x1 - x0 - 100f).toInt()
            val accent = when (card.kind) { CardKind.RECAP -> palette.tickRead.toInt(); CardKind.KEY_SENTENCE -> palette.focus.toInt(); CardKind.TEASER -> palette.terminalOk.toInt() }
            val icon = when (card.kind) { CardKind.RECAP -> "↺"; CardKind.KEY_SENTENCE -> "★"; CardKind.TEASER -> "▶" }
            val size = if (card.kind == CardKind.KEY_SENTENCE) Tokens.Type.KEY_SENTENCE else 64f
            val bodyText = card.text.removePrefix(card.label).trimStart(':', '：', ' ', '…')
            val body = text.layout(bodyText.ifBlank { card.text }, iw, size, palette.text.toInt(), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1, spacing = 1.05f)
            val label = text.layout(card.label, iw, 38f, accent, TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1, maxLines = 1)
            val sub = card.sub?.let { text.layout("💬 $it", iw - 40, 38f, palette.text.toInt(), TextKit.Face.SEMI, Layout.Alignment.ALIGN_CENTER, 1, spacing = 0.95f) }
            val total = 120f + label.height + 50f + body.height + (sub?.let { it.height + 110f } ?: 0f) + 60f
            var y = (safeTop + safeBottom) / 2 - total / 2
            // card
            g.fill.color = g.alpha(0xFF000000.toInt(), if (palette.isDark) 0.35f else 0.12f)
            canvas.drawRoundRect(x0 + 4f, y + 16f, x1 + 4f, y + total + 16f, 56f, 56f, g.fill)
            g.rect.set(x0, y, x1, y + total)
            g.fill.color = palette.surface.toInt(); canvas.drawRoundRect(g.rect, 56f, 56f, g.fill)
            g.fill.color = accent; canvas.drawRoundRect(x0 + 60f, y, x1 - 60f, y + 10f, 5f, 5f, g.fill)
            // icon badge
            g.fill.color = g.alpha(accent, 0.18f); canvas.drawCircle(cx, y + 80f, 52f, g.fill)
            val ip = text.paint(TextKit.Face.BOLD, 56f, accent); ip.textAlign = Paint.Align.CENTER
            canvas.drawText(icon, cx, y + 100f, ip); ip.textAlign = Paint.Align.LEFT
            y += 160f
            g.drawAt(canvas, label, x0 + 50f, y); y += label.height + 40f
            if (card.kind == CardKind.KEY_SENTENCE) {
                // soft highlighter stroke behind the last line
                val lastLine = body.lineCount - 1
                val lw = body.getLineWidth(lastLine)
                g.fill.color = g.alpha(accent, 0.22f * ((card.reveal - 0.5f) / 0.5f).coerceIn(0f, 1f))
                val ly = y + body.getLineBottom(lastLine) - 26f
                canvas.drawRoundRect(cx - lw / 2 - 12f, ly - 22f, cx + lw / 2 + 12f, ly + 6f, 12f, 12f, g.fill)
            }
            // body lines arrive one by one after the label
            for (li in 0 until body.lineCount) {
                val a = (card.reveal * (body.lineCount + 1) - li).coerceIn(0f, 1f)
                if (a <= 0f) continue
                g.withAlpha(canvas, a, W, H) {
                    canvas.save()
                    canvas.clipRect(x0, y + body.getLineTop(li), x1, y + body.getLineBottom(li))
                    canvas.translate(x0 + 50f, y + (1f - a) * 10f)
                    body.draw(canvas)
                    canvas.restore()
                }
            }
            y += body.height + 50f
            val subA = ((card.reveal - 0.75f) / 0.25f).coerceIn(0f, 1f)
            sub?.let {
                g.withAlpha(canvas, subA, W, H) {
                    g.rect.set(x0 + 50f, y, x1 - 50f, y + it.height + 50f)
                    g.fill.color = g.alpha(accent, 0.14f); canvas.drawRoundRect(g.rect, 30f, 30f, g.fill)
                    g.drawAt(canvas, it, x0 + 70f, y + 25f)
                }
            }
            canvas.restore()
        }
    }

    // ------------------------------------------------------------------ cover

    /** Automatic cover image (1080×1920) with the lesson title in the calm style. */
    fun drawCover(canvas: Canvas) {
        background(canvas)
        val m = scenario.meta
        val x0 = safeLeft + 20f
        val x1 = safeRight - 10f
        var y = safeTop + 40f
        m.series.episode?.let {
            val chip = text.layout("الحلقة $it", 600, 44f, skin.kbBg, TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1, maxLines = 1)
            val bw = text.maxLineWidth(chip) + 70f
            val cx = (x0 + x1) / 2
            g.rect.set(cx - bw / 2, y, cx + bw / 2, y + 76f)
            g.fill.color = palette.focus.toInt(); canvas.drawRoundRect(g.rect, 38f, 38f, g.fill)
            g.drawAt(canvas, chip, cx - 300f, y + 38f - chip.height / 2f)
            y += 120f
        }
        val title = text.layout(m.title, (x1 - x0).toInt(), 104f, palette.text.toInt(), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1, spacing = 1.0f)
        g.drawAt(canvas, title, x0, y); y += title.height + 60f
        scenario.scenes.firstOrNull { it is Scene.HookPredict }?.let { hook ->
            hook as Scene.HookPredict
            y = codeCard(canvas, hook.code, hook.language, x0, y, x1, 50f, 1f) + 50f
            val q = text.layout("توقّع قبل أن أشغّل 👀", (x1 - x0).toInt(), 58f, palette.focus.toInt(), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 1, maxLines = 1)
            g.drawAt(canvas, q, x0, y); y += q.height + 70f
        }
        val n = scenario.cast.size
        val gap = 140f
        var x = (x0 + x1) / 2 + (n - 1) * gap / 2
        for (member in scenario.cast) {
            g.avatar(canvas, member.id, x, y + 60f, 58f)
            val np = text.paint(TextKit.Face.SEMI, 28f, palette.textMuted.toInt()); np.textAlign = Paint.Align.CENTER
            canvas.drawText(member.name, x, y + 160f, np); np.textAlign = Paint.Align.LEFT
            x -= gap
        }
        val series = text.layout(m.series.name ?: "تعلّم البرمجة مع أحمد", (x1 - x0).toInt(), 40f, palette.textMuted.toInt(), TextKit.Face.SEMI, Layout.Alignment.ALIGN_CENTER, 1, maxLines = 1)
        g.drawAt(canvas, series, x0, y + 200f)
    }
}
