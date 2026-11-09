package studio.pyreels.app.engine.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.timeline.ChatMessageView
import studio.pyreels.core.timeline.ChatView
import studio.pyreels.core.timeline.MessageKind
import studio.pyreels.core.timeline.Rng
import kotlin.math.max
import kotlin.math.min

/**
 * Messenger screen inside the phone: header with the group, doodle wallpaper, date pill, bubbles
 * with tails, sender names, ticks, reactions, typing bubble and an input bar where Ahmed's
 * message is typed letter by letter before it is sent. RTL layout: Ahmed (outgoing) on the left.
 */
class ChatPainter(private val g: Gfx, private val scenario: Scenario, private val w: Float, private val h: Float) {
    private val s = g.skin
    private val text = g.text

    private val headerTop = 52f
    private val headerH = 112f
    private val inputH = 112f
    private val listTop get() = headerTop + headerH
    private val listBottom get() = h - inputH - 6f
    private val bubbleMax = w * 0.78f
    private val avatarR = 26f
    private val bodySize = 40f
    private val gap = 14f

    private val wallpaper: Bitmap by lazy { makeWallpaper() }

    private fun makeWallpaper(): Bitmap {
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(s.waWall)
        val r = Rng(20_24)
        val glyphs = listOf("{ }", "</>", "( )", "#", "=", "[ ]", ">>>", "def", "if", "+=")
        val tp = text.paint(TextKit.Face.MONO, 28f, s.waDoodle)
        var y = 40f
        var row = 0
        while (y < h + 40) {
            var x = if (row % 2 == 0) 20f else 80f
            while (x < w + 40) {
                c.save(); c.rotate(r.signed() * 25f, x, y)
                c.drawText(glyphs[r.nextInt(glyphs.size)], x, y, tp)
                c.restore()
                x += 120f + r.nextInt(30)
            }
            y += 110f; row++
        }
        return bmp
    }

    fun draw(canvas: Canvas, chat: ChatView, engage: studio.pyreels.core.timeline.EngageChipView? = null) {
        canvas.drawBitmap(wallpaper, 0f, 0f, null)
        drawMessages(canvas, chat)
        drawHeader(canvas, chat)
        drawInput(canvas, chat)
        engage?.let { if (it.appear > 0.01f) drawEngageBar(canvas, it.actions, it.appear, it.heartPulse) }
    }

    /** The like / follow / share bar slides up and temporarily takes the place of the input bar. */
    private fun drawEngageBar(canvas: Canvas, actions: List<String>, appear: Float, heart: Float) {
        val top = h - inputH + (1f - appear) * inputH
        canvas.save()
        canvas.clipRect(0f, h - inputH, w, h)
        g.fill.color = s.waBar
        canvas.drawRect(0f, top, w, h, g.fill)
        val n = actions.size
        val gapX = 12f
        val bw = (w - 28f - gapX * (n - 1)) / n
        val cy = top + inputH / 2 - 4f
        actions.forEachIndexed { i, a ->
            val x1 = w - 14f - i * (bw + gapX) // RTL order: first action on the right
            val x0 = x1 - bw
            g.rect.set(x0, cy - 38f, x1, cy + 38f)
            g.fill.color = if (i == 0) g.alpha(g.c(g.p.focus), 0.22f) else s.waInput
            canvas.drawRoundRect(g.rect, 38f, 38f, g.fill)
            val label = when (a) { "like" -> "إعجاب"; "follow" -> "متابعة"; else -> "مشاركة" }
            val p = text.paint(TextKit.Face.SEMI, 30f, s.waText)
            val lw = p.measureText(label)
            val iconSize = 40f
            val total = lw + iconSize + 14f
            val start = (x0 + x1) / 2 + total / 2 // RTL: icon on the right of the label
            val ix = start - iconSize / 2
            when (a) {
                "like" -> g.drawHeart(canvas, ix, cy, 17f * (1f + 0.15f * heart))
                "follow" -> { canvas.save(); canvas.translate(ix, cy); canvas.scale(0.75f, 0.75f); g.drawFollow(canvas, 0f, 0f, s.waTick); canvas.restore() }
                else -> { canvas.save(); canvas.translate(ix, cy); canvas.scale(0.75f, 0.75f); g.drawShare(canvas, 0f, 0f, s.waFab); canvas.restore() }
            }
            canvas.drawText(label, start - total, cy + 10f, p)
        }
        canvas.restore()
    }

    // ------------------------------------------------------------ header

    private fun drawHeader(canvas: Canvas, chat: ChatView) {
        g.fill.color = s.waHeader
        canvas.drawRect(0f, 0f, w, headerTop + headerH, g.fill)
        g.statusBar(canvas, w, s.waHeaderText)
        val cy = headerTop + headerH / 2
        g.iconBack(canvas, w - 38f, cy, 34f, s.waHeaderText, pointRight = true)
        // group avatar: two members side by side inside one circle
        val ax = w - 104f
        // one clean group avatar: a circle with two people (no overlapping mini avatars)
        g.fill.color = g.blend(s.waHeader, 0xFFFFFFFF.toInt(), if (s.dark) 0.16f else 0.28f)
        canvas.drawCircle(ax, cy, 38f, g.fill)
        val person = g.alpha(s.waHeaderText, 0.9f)
        val back = g.alpha(s.waHeaderText, 0.5f)
        g.fill.color = back
        canvas.drawCircle(ax + 11f, cy - 9f, 8f, g.fill)
        g.rect.set(ax - 2f, cy + 1f, ax + 24f, cy + 22f); canvas.drawArc(g.rect, 180f, 180f, true, g.fill)
        g.fill.color = person
        canvas.drawCircle(ax - 6f, cy - 6f, 10f, g.fill)
        g.rect.set(ax - 22f, cy + 6f, ax + 10f, cy + 32f); canvas.drawArc(g.rect, 180f, 180f, true, g.fill)
        val tw = (w - 360f).toInt()
        val name = text.layout(chat.groupName, tw, 36f, s.waHeaderText, TextKit.Face.BOLD, Layout.Alignment.ALIGN_NORMAL, 1, spacing = 0.95f, maxLines = 1)
        val typing = chat.typingFrom?.takeIf { it != "ahmed" }
        val sub = if (typing != null) "${g.nameOf(typing)} يكتب…" else scenario.cast.joinToString("، ") { it.name }
        val subL = text.layout(sub, tw, 26f, if (typing != null) s.waTyping else s.waHeaderSub, TextKit.Face.REGULAR, Layout.Alignment.ALIGN_NORMAL, 1, spacing = 0.95f, maxLines = 1)
        val right = ax - 50f
        g.drawRtl(canvas, name, right - tw, tw.toFloat(), cy - name.height + 6f)
        g.drawRtl(canvas, subL, right - tw, tw.toFloat(), cy + 6f)
        // call icons (left side in RTL)
        g.iconDots(canvas, 34f, cy, 40f, s.waHeaderText)
        g.iconPhone(canvas, 96f, cy, 40f, s.waHeaderText)
        g.iconVideo(canvas, 166f, cy, 44f, s.waHeaderText)
    }

    // ------------------------------------------------------------ input bar

    private fun drawInput(canvas: Canvas, chat: ChatView) {
        val top = h - inputH
        g.fill.color = s.waBar
        canvas.drawRect(0f, top, w, h, g.fill)
        val cy = top + inputH / 2 - 4f
        // round button (left in RTL): mic, or send while Ahmed types
        val composing = chat.composeText != null
        g.fill.color = s.waFab
        canvas.drawCircle(52f, cy, 40f, g.fill)
        // the mic shrinks away and the send arrow grows in as the first letters are typed
        val morph = if (composing) (chat.composeChars / 2f).coerceIn(0f, 1f) else 0f
        if (morph < 1f) { canvas.save(); canvas.scale(1f - morph, 1f - morph, 52f, cy); g.iconMic(canvas, 52f, cy, 46f, 0xFFFFFFFF.toInt()); canvas.restore() }
        if (morph > 0f) { canvas.save(); canvas.rotate(-30f * (1f - morph), 52f, cy); canvas.scale(morph, morph, 52f, cy); g.iconSend(canvas, 50f, cy, 40f, 0xFFFFFFFF.toInt()); canvas.restore() }
        // field
        val fx0 = 104f; val fx1 = w - 14f
        g.rect.set(fx0, cy - 38f, fx1, cy + 38f)
        g.fill.color = s.waInput
        canvas.drawRoundRect(g.rect, 38f, 38f, g.fill)
        g.iconSmile(canvas, fx1 - 40f, cy, 40f, s.waIcon)
        g.iconCamera(canvas, fx0 + 40f, cy, 38f, s.waIcon)
        if (!composing) g.iconClip(canvas, fx0 + 96f, cy, 38f, s.waIcon)
        val fieldW = (fx1 - fx0 - 150f).toInt()
        val content = chat.composeText?.take(chat.composeChars)
        val l = if (content.isNullOrEmpty()) {
            text.layout("رسالة", fieldW, 32f, s.waIcon, TextKit.Face.REGULAR, Layout.Alignment.ALIGN_NORMAL, 1, maxLines = 1)
        } else {
            // show the end of a long message, like a single-line field scrolls
            text.layout(content.replace("`", "").takeLast(30), fieldW, 32f, s.waText, TextKit.Face.REGULAR, Layout.Alignment.ALIGN_NORMAL, 1, maxLines = 1)
        }
        g.drawRtl(canvas, l, fx1 - 76f - fieldW, fieldW.toFloat(), cy - l.height / 2f)
        if (!content.isNullOrEmpty()) {
            // caret after the last typed letter (left end of an RTL line)
            val caretX = fx1 - 76f - text.maxLineWidth(l) - 4f
            g.fill.color = s.waFab
            canvas.drawRect(caretX - 3f, cy - 20f, caretX, cy + 20f, g.fill)
        }
    }

    // ------------------------------------------------------------ messages

    private class Laid(
        val body: StaticLayout?, val name: StaticLayout?, val code: StaticLayout?,
        val bubbleW: Float, val bubbleH: Float, val totalH: Float,
    )

    private val laid = HashMap<Int, Laid>()

    private fun codeLayout(code: String, width: Int, size: Float = 32f) =
        text.layout(code, width, size, g.c(g.p.codeText), TextKit.Face.MONO, Layout.Alignment.ALIGN_NORMAL, 0, spacing = 1.1f)

    private fun layoutOf(m: ChatMessageView, grouped: Boolean = false): Laid {
        val key = if (grouped) -m.id - 1 else m.id
        laid[key]?.let { if (m.karaokeWord < 0) return it }
        val inner = (bubbleMax - 44f).toInt()
        val body = if (m.text.isBlank()) null else text.layout(
            m.text.replace("`", ""), inner, bodySize, s.waText, TextKit.Face.REGULAR, Layout.Alignment.ALIGN_NORMAL, 1,
            m.karaokeWord, g.c(g.p.focus), spacing = 0.92f,
        )
        val name = if (m.outgoing || grouped) null else text.layout(g.nameOf(m.from), inner, 30f, g.accent(m.from), TextKit.Face.BOLD, Layout.Alignment.ALIGN_NORMAL, 1, spacing = 0.9f, maxLines = 1)
        val code = m.code?.let { codeLayout(it, inner - 30) }
        var contentW = max(body?.let { text.maxLineWidth(it) } ?: 0f, name?.let { text.maxLineWidth(it) } ?: 0f)
        code?.let { contentW = max(contentW, text.maxLineWidth(it) + 30f) }
        var hh = 16f
        name?.let { hh += it.height + 2f }
        body?.let { hh += it.height }
        code?.let { hh += it.height + 34f }
        when (m.kind) {
            MessageKind.ANALOGY -> { contentW = bubbleMax; hh += 70f + (if (m.analogyItems.isEmpty()) 0f else 168f) }
            MessageKind.REVEAL -> {
                contentW = bubbleMax
                hh += 150f + (m.revealCode?.let { codeLayout(it, inner - 30, 28f).height + 40f } ?: 0f)
            }
            else -> Unit
        }
        if (m.challenge) hh += 52f
        hh += 34f // time row
        val bw = min(bubbleMax, max(contentW + 44f, if (m.outgoing) 200f else 170f))
        val total = hh + (if (m.reactions.isNotEmpty()) 30f else 0f)
        return Laid(body, name, code, bw, hh, total).also { laid[key] = it }
    }

    private fun drawMessages(canvas: Canvas, chat: ChatView) {
        canvas.save()
        canvas.clipRect(0f, listTop, w, listBottom + 4f)
        var y = listBottom - 10f
        val typer = chat.typingFrom?.takeIf { it != "ahmed" }
        if (typer != null) {
            val th = 76f
            drawTypingBubble(canvas, typer, chat.typingPhase, y - th)
            y -= th + gap
        }
        val msgs = chat.messages
        if (msgs.isNotEmpty()) {
            // consecutive messages from the same sender are grouped (no repeated name/avatar/tail)
            fun grouped(k: Int) = k > 0 && msgs[k - 1].from == msgs[k].from && msgs[k - 1].reactions.isEmpty() && msgs[k].kind == MessageKind.TEXT
            val lastK = msgs.lastIndex
            val newest = layoutOf(msgs[lastK], grouped(lastK))
            val shift = (1f - chat.scroll) * (newest.totalH + (if (grouped(lastK)) 6f else gap))
            var firstTop = y
            var reachedFirst = false
            for (k in msgs.indices.reversed()) {
                val m = msgs[k]
                val gr = grouped(k)
                val l = layoutOf(m, gr)
                val isNewest = k == lastK
                val top = y - l.totalH + (if (isNewest) 0f else shift)
                if (top + l.totalH < listTop) break
                if (isNewest && m.appear < 1f) {
                    // soft entrance: fade, small rise and a gentle grow from the bubble's tail corner
                    g.withAlpha(canvas, m.appear, w, h) {
                        canvas.save()
                        val sc = 0.9f + 0.1f * m.appear
                        val pivotX = if (m.outgoing) 30f else w - 2 * avatarR - 30f
                        canvas.translate(0f, (1f - m.appear) * 8f)
                        canvas.scale(sc, sc, pivotX, top + l.bubbleH)
                        drawMessage(canvas, m, l, top, gr)
                        canvas.restore()
                    }
                } else drawMessage(canvas, m, l, top, gr)
                firstTop = top
                if (k == 0) reachedFirst = true
                y = y - l.totalH - (if (gr) 6f else gap)
            }
            // date pill + encryption notice above the first message when there is room
            if (reachedFirst) drawSystem(canvas, firstTop - 22f - sysHeight)
        } else drawSystem(canvas, listTop + 24f)
        canvas.restore()
    }

    // ------------------------------------------------------------ system messages (start of the chat)

    private class SysItem(val layout: StaticLayout, val bg: Int, val fg: Int, val lock: Boolean, val pillW: Float, val pillH: Float)

    private val sysItems: List<SysItem> by lazy {
        val teachers = scenario.cast.filter { it.role == "teacher" }
        val creator = teachers.firstOrNull()?.let { g.nameOf(it.id) } ?: "المعلّم"
        val group = scenario.meta.groupName ?: "مجموعة ${scenario.meta.title}"
        val items = ArrayList<SysItem>()
        fun add(t: String, bg: Int, fg: Int, size: Float, lock: Boolean = false, maxW: Float = w - 140f) {
            val iconW = if (lock) size + 10f else 0f
            val l = text.layout(t, (maxW - iconW).toInt(), size, fg, TextKit.Face.REGULAR, Layout.Alignment.ALIGN_CENTER, 1, spacing = 0.95f)
            val pw = text.maxLineWidth(l) + iconW + 44f
            items += SysItem(l, bg, fg, lock, pw, l.height + 22f)
        }
        add("اليوم", s.waPill, s.waTime, 26f)
        add("الرسائل والمكالمات مشفّرة تمامًا بين الطرفين. لا يمكن لأحد خارج هذه الدردشة قراءتها أو الاستماع إليها.", s.waNotice, s.waNoticeText, 24f, lock = true, maxW = w - 120f)
        add("أنشأ $creator المجموعة", s.waPill, s.waTime, 24f)
        @Suppress("UNUSED_VARIABLE") val unusedGroup = group
        add("أضاف $creator أحمد", s.waPill, s.waTime, 24f)
        teachers.drop(1).forEach { add("أضاف $creator ${g.nameOf(it.id)}", s.waPill, s.waTime, 24f) }
        items
    }

    private val sysGap = 12f
    private val sysHeight: Float by lazy { sysItems.sumOf { (it.pillH + sysGap).toDouble() }.toFloat() }

    /** Draws the system messages from [top]; they are a normal part of the list, so nothing overlaps them. */
    private fun drawSystem(canvas: Canvas, top: Float) {
        var y = top
        for (it in sysItems) {
            val x0 = w / 2 - it.pillW / 2
            g.fill.color = g.alpha(0xFF000000.toInt(), if (s.dark) 0.18f else 0.06f)
            canvas.drawRoundRect(x0, y + 2f, x0 + it.pillW, y + it.pillH + 2f, 14f, 14f, g.fill)
            g.rect.set(x0, y, x0 + it.pillW, y + it.pillH)
            g.fill.color = it.bg; canvas.drawRoundRect(g.rect, 14f, 14f, g.fill)
            val textW = text.maxLineWidth(it.layout)
            val iconW = if (it.lock) 34f else 0f
            // the layout is centred inside its own width; shift so that [icon + text] is centred in the pill
            val textCenter = w / 2 - iconW / 2
            g.drawAt(canvas, it.layout, textCenter - it.layout.width / 2f, y + 11f)
            if (it.lock) {
                val firstLineW = it.layout.getLineWidth(0)
                val lx = textCenter + firstLineW / 2 + 18f
                val ly = y + 11f + (it.layout.getLineTop(0) + it.layout.getLineBottom(0)) / 2f
                g.iconLock(canvas, lx, ly, 22f, it.fg)
            }
            @Suppress("UNUSED_VARIABLE") val unused = textW
            y += it.pillH + sysGap
        }
    }

    private fun bubble(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, outgoing: Boolean, color: Int, tail: Boolean = true) {
        val r = 24f
        g.fill.color = color
        g.rect.set(x0, y0, x1, y1)
        canvas.drawRoundRect(g.rect, r, r, g.fill)
        if (!tail) return
        // tail at the top outer corner
        g.path.reset()
        if (outgoing) {
            canvas.drawRect(x0, y0, x0 + r, y0 + r, g.fill)
            g.path.moveTo(x0 + 2f, y0); g.path.lineTo(x0 - 16f, y0); g.path.quadTo(x0 - 4f, y0 + 6f, x0 + 2f, y0 + 22f)
        } else {
            canvas.drawRect(x1 - r, y0, x1, y0 + r, g.fill)
            g.path.moveTo(x1 - 2f, y0); g.path.lineTo(x1 + 16f, y0); g.path.quadTo(x1 + 4f, y0 + 6f, x1 - 2f, y0 + 22f)
        }
        g.path.close()
        canvas.drawPath(g.path, g.fill)
    }

    private fun drawMessage(canvas: Canvas, m: ChatMessageView, l: Laid, top: Float, grouped: Boolean = false) {
        val x0 = if (m.outgoing) 30f else w - 2 * avatarR - 30f - l.bubbleW
        val x1 = x0 + l.bubbleW
        val y1 = top + l.bubbleH
        val color = if (m.outgoing) s.waOut else s.waIn
        bubble(canvas, x0, top, x1, y1, m.outgoing, color, tail = !grouped)
        if (m.challenge) { g.stroke.color = g.alpha(g.c(g.p.focus), 0.8f); g.stroke.strokeWidth = 3f; g.rect.set(x0, top, x1, y1); canvas.drawRoundRect(g.rect, 24f, 24f, g.stroke) }
        if (!m.outgoing && !grouped) g.avatar(canvas, m.from, w - avatarR - 12f, top + avatarR + 2f, avatarR)
        val ix = x0 + 22f
        val iw = l.bubbleW - 44f
        var y = top + 12f
        l.name?.let { g.drawRtl(canvas, it, ix, iw, y); y += it.height + 2f }
        l.body?.let { g.drawRtl(canvas, it, ix, iw, y); y += it.height }
        l.code?.let { cl ->
            y += 10f
            g.rect.set(ix, y, ix + iw, y + cl.height + 22f)
            g.fill.color = g.c(g.p.codeBg); canvas.drawRoundRect(g.rect, 14f, 14f, g.fill)
            drawHighlighted(canvas, m.code ?: "", ix + 16f, y + 11f, 32f)
            y += cl.height + 34f
        }
        when (m.kind) {
            MessageKind.ANALOGY -> y = drawAnalogy(canvas, m, ix, iw, y)
            MessageKind.REVEAL -> y = drawReveal(canvas, m, ix, iw, y)
            else -> Unit
        }
        if (m.challenge) {
            val chip = text.layout("💬 تحدٍّ للتعليقات", iw.toInt(), 28f, g.c(g.p.focus), TextKit.Face.SEMI, Layout.Alignment.ALIGN_NORMAL, 1, maxLines = 1)
            g.drawRtl(canvas, chip, ix, iw, y + 10f)
        }
        // time + ticks at the bottom-left corner (RTL end)
        val tp = text.paint(TextKit.Face.REGULAR, 22f, s.waTime)
        var tx = x0 + 18f
        if (m.outgoing) { drawTicks(canvas, tx, y1 - 30f, m.ticksRead); tx += 36f }
        canvas.drawText(m.timeLabel, tx, y1 - 12f, tp)
        if (m.reactions.isNotEmpty()) {
            val sc = 1f + 0.18f * m.reactionPulse
            val emoji = m.reactions.joinToString("") { reactionEmoji(it) }
            val ep = text.paint(TextKit.Face.REGULAR, 26f * sc, s.waText)
            val pw = ep.measureText(emoji) + 26f
            val rx = x1 - pw - 18f
            g.rect.set(rx, y1 - 8f, rx + pw, y1 + 34f)
            g.fill.color = if (s.dark) g.blend(color, 0xFF000000.toInt(), 0.25f) else s.waPill
            canvas.drawRoundRect(g.rect, 21f, 21f, g.fill)
            g.stroke.color = s.waWall; g.stroke.strokeWidth = 3f; canvas.drawRoundRect(g.rect, 21f, 21f, g.stroke)
            canvas.drawText(emoji, rx + 13f, y1 + 22f, ep)
        }
    }

    private fun reactionEmoji(id: String) = when (id) { "heart" -> "❤️"; "like" -> "👍"; "laugh" -> "😄"; "wow" -> "😮"; "clap" -> "👏"; else -> "❤️" }

    private fun drawTicks(canvas: Canvas, x: Float, y: Float, read: Boolean) {
        g.stroke.color = if (read) s.waTick else s.waTime; g.stroke.strokeWidth = 3f
        for (k in 0..1) {
            val ox = x + k * 10f
            g.path.reset(); g.path.moveTo(ox, y + 9f); g.path.lineTo(ox + 6f, y + 15f); g.path.lineTo(ox + 18f, y + 2f)
            canvas.drawPath(g.path, g.stroke)
        }
    }

    private fun drawTypingBubble(canvas: Canvas, from: String, phase: Float, top: Float) {
        val bw = 130f
        val x1 = w - 2 * avatarR - 30f
        bubble(canvas, x1 - bw, top, x1, top + 76f, false, s.waIn)
        g.avatar(canvas, from, w - avatarR - 12f, top + avatarR + 2f, avatarR)
        for (i in 0..2) {
            val local = ((phase * 3f - i * 0.5f) % 3f + 3f) % 3f
            val a = 0.35f + 0.65f * (if (local < 1f) (1f - kotlin.math.abs(local - 0.5f) * 2f) else 0f)
            g.fill.color = g.alpha(s.waTime, a)
            canvas.drawCircle(x1 - bw + 38f + i * 27f, top + 38f - a * 4f, 8f, g.fill)
        }
    }

    private fun drawAnalogy(canvas: Canvas, m: ChatMessageView, x: Float, w: Float, yIn: Float): Float {
        var y = yIn + 8f
        m.analogyTitle?.let {
            val l = text.layout("💡 $it", w.toInt(), 34f, g.c(g.p.focus), TextKit.Face.BOLD, Layout.Alignment.ALIGN_NORMAL, 1, spacing = 0.9f)
            g.drawRtl(canvas, l, x, w, y); y += l.height + 14f
        }
        val items = m.analogyItems
        if (items.isEmpty()) return y
        val n = items.size
        val gapX = 14f
        val bw = (w - gapX * (n - 1)) / n
        val accent = g.accent(m.from)
        for (i in 0 until n) {
            if (i >= m.analogyVisible) continue
            val (label, value) = items[i]
            val bx = x + w - (i + 1) * bw - i * gapX // first item on the right (RTL reading order)
            // the box: a lid line and a label tag, so it reads as "a labeled box"
            g.rect.set(bx, y + 40f, bx + bw, y + 150f)
            g.fill.color = if (s.dark) g.blend(s.waIn, accent, 0.10f) else g.blend(0xFFFFFFFF.toInt(), accent, 0.10f)
            canvas.drawRoundRect(g.rect, 16f, 16f, g.fill)
            g.stroke.color = g.alpha(accent, 0.8f); g.stroke.strokeWidth = 3f
            canvas.drawRoundRect(g.rect, 16f, 16f, g.stroke)
            canvas.drawLine(bx + 10f, y + 62f, bx + bw - 10f, y + 62f, g.stroke)
            // tag with the label
            val tag = text.layout(label, (bw - 20).toInt(), 24f, s.waText, TextKit.Face.SEMI, Layout.Alignment.ALIGN_CENTER, 2, spacing = 0.9f, maxLines = 1)
            val tw = min(bw - 10f, text.maxLineWidth(tag) + 24f)
            g.rect2.set(bx + bw / 2 - tw / 2, y + 4f, bx + bw / 2 + tw / 2, y + 44f)
            g.fill.color = g.alpha(accent, 0.25f); canvas.drawRoundRect(g.rect2, 12f, 12f, g.fill)
            g.drawAt(canvas, tag, bx + 10f, y + 24f - tag.height / 2f)
            val v = text.layout(value, (bw - 16).toInt(), 34f, s.waText, TextKit.Face.MONO_BOLD, Layout.Alignment.ALIGN_CENTER, 2, spacing = 0.9f, maxLines = 2)
            g.drawAt(canvas, v, bx + 8f, y + 106f - v.height / 2f)
        }
        return y + 160f
    }

    private fun drawReveal(canvas: Canvas, m: ChatMessageView, x: Float, w: Float, yIn: Float): Float {
        var y = yIn + 10f
        m.revealCode?.let { code ->
            val cl = codeLayout(code, (w - 30).toInt(), 28f)
            g.rect.set(x, y, x + w, y + cl.height + 22f)
            g.fill.color = g.c(g.p.codeBg); canvas.drawRoundRect(g.rect, 14f, 14f, g.fill)
            drawHighlighted(canvas, code, x + 16f, y + 11f, 28f)
            y += cl.height + 36f
        }
        g.rect.set(x, y, x + w, y + 132f)
        g.fill.color = g.alpha(g.c(g.p.terminalOk), 0.14f); canvas.drawRoundRect(g.rect, 18f, 18f, g.fill)
        val label = text.layout("✓ الإجابة", w.toInt() - 30, 28f, g.c(g.p.terminalOk), TextKit.Face.BOLD, Layout.Alignment.ALIGN_NORMAL, 1, maxLines = 1)
        g.drawRtl(canvas, label, x, w - 20f, y + 8f)
        val ans = text.layout(m.revealAnswer ?: "", w.toInt() - 20, 52f, g.c(g.p.focus), TextKit.Face.BOLD, Layout.Alignment.ALIGN_CENTER, 2, spacing = 0.9f, maxLines = 2)
        g.drawAt(canvas, ans, x + 10f, y + 84f - ans.height / 2f)
        return y + 146f
    }

    /** Highlighted LTR code at (x, y) top-left. */
    fun drawHighlighted(canvas: Canvas, code: String, x: Float, y: Float, size: Float) {
        val lh = size * 1.32f
        for ((i, line) in code.split('\n').withIndex()) {
            var cx = x
            val base = y + i * lh + size
            for (tok in SyntaxHighlighter.tokens(line, "python")) {
                val p = text.paint(TextKit.Face.MONO, size, SyntaxHighlighter.color(tok.kind, g.p))
                canvas.drawText(tok.text, cx, base, p)
                cx += p.measureText(tok.text)
            }
        }
    }

    @Suppress("unused") private val keepPaint = Paint()
}
