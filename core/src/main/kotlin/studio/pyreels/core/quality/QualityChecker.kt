package studio.pyreels.core.quality

import studio.pyreels.core.model.Limits
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.Scene
import studio.pyreels.core.model.typeId
import studio.pyreels.core.timeline.DurationEstimator
import studio.pyreels.core.timeline.Timeline
import studio.pyreels.core.validate.Problem
import studio.pyreels.core.validate.ScenarioValidator
import studio.pyreels.core.validate.Severity
import kotlin.math.abs
import kotlin.math.roundToInt

data class QualityReport(val score: Int, val problems: List<Problem>, val totalMs: Long) {
    val blocking get() = problems.filter { it.severity == Severity.ERROR }
    val canExport get() = blocking.isEmpty()
}

/**
 * Pre-export quality checker and retention-oriented "boredom detector" (sections 8 and 11.5).
 * Only hard errors (schema/structure, > 180 s) block export; everything else is a warning with a tip.
 */
object QualityChecker {

    fun check(scenario: Scenario, timeline: Timeline, loudnessBlocks: List<Double>? = null): QualityReport {
        val p = ArrayList<Problem>()
        p += ScenarioValidator.semantic(scenario)
        val total = timeline.totalMs
        val sec = total / 1000.0
        val target = scenario.meta.targetSeconds * 1000L

        // Duration
        when {
            total > Limits.HARD_CAP_SECONDS * 1000L -> p += Problem(Severity.ERROR, "q.cap", "المدة ${fmt(sec)} ث تتجاوز الحد الأقصى ${Limits.HARD_CAP_SECONDS} ث. احذف بعض المشاهد.", "المدة الكلية")
            total > Limits.WARN_SECONDS * 1000L -> p += Problem(Severity.WARNING, "q.long", "المدة ${fmt(sec)} ث أطول من ${Limits.WARN_SECONDS} ث؛ الفيديو القصير يُكمَل أكثر.", "المدة الكلية")
        }
        if (total < target * (1 - Limits.TARGET_TOLERANCE) || total > target * (Limits.CALM_STRETCH + Limits.TARGET_TOLERANCE)) {
            p += Problem(Severity.WARNING, "q.target", "المدة ${fmt(sec)} ث بعيدة عن الهدف ${target / 1000} ث (± 10 %).", "المدة الكلية")
        }

        // Hook in the first 3 s
        val first = timeline.scenes.firstOrNull()
        if (first == null || first.scene !is Scene.HookPredict || first.durationMs > Limits.HOOK_MS) {
            p += Problem(Severity.WARNING, "q.hook", "الهوك يجب أن يظهر في أول 3 ثوانٍ.", "المشهد 1", 0)
        }

        // Engagement window
        timeline.scenes.firstOrNull { it.scene is Scene.EngageMoment }?.let { e ->
            if (e.startMs !in Limits.ENGAGE_WINDOW_START_MS..Limits.ENGAGE_WINDOW_END_MS) {
                p += Problem(
                    Severity.WARNING, "q.engage", "لحظة التفاعل تبدأ عند ${fmt(e.startMs / 1000.0)} ث، والأفضل بين 45 و55 ث (بعد أول نجاح صغير).",
                    "المشهد ${e.index + 1} (engage_moment)", e.index, fix = "shiftEngage",
                )
            }
            val firstRun = timeline.scenes.firstOrNull { it.scene is Scene.Run }
            if (firstRun != null && firstRun.index > e.index) {
                p += Problem(Severity.INFO, "q.engageBeforeWin", "لحظة التفاعل تأتي قبل أول تشغيل ناجح للكود.", "المشهد ${e.index + 1}", e.index, fix = "shiftEngage")
            }
        }

        // Reading speed per on-screen text
        for (ts in timeline.scenes) {
            val sc = ts.scene
            val text = when (sc) {
                is Scene.Chat -> sc.text; is Scene.Recap -> sc.text; is Scene.KeySentence -> sc.text; is Scene.Teaser -> sc.text
                is Scene.Reveal -> sc.text; is Scene.CommonMistake -> sc.explain; is Scene.Analogy -> sc.text ?: sc.title; else -> null
            } ?: continue
            val words = DurationEstimator.words(text)
            val visible = when (sc) {
                is Scene.Chat -> ts.durationMs - DurationEstimator.chatTypingMs(sc.text, ts.durationMs, sc.from == "ahmed")
                is Scene.CommonMistake -> ts.durationMs * 6 / 10
                else -> ts.durationMs - 600
            }.coerceAtLeast(1)
            val wps = words / (visible / 1000.0)
            if (wps > Limits.WORDS_PER_SECOND + 0.2) {
                p += Problem(
                    Severity.WARNING, "q.fast", "القراءة سريعة (${fmt(wps)} كلمة/ث، الحد ${Limits.WORDS_PER_SECOND}). اختصر النص أو قسّمه.",
                    "المشهد ${ts.index + 1} (${sc.typeId()})", ts.index, fix = if (sc is Scene.Chat) "split" else null,
                )
            }
            if (sc is Scene.Chat) {
                val longest = sc.text.lines().maxOf { it.length }
                if (longest > Limits.MAX_BUBBLE_CHARS) {
                    p += Problem(Severity.WARNING, "q.dense", "سطر كثيف جدًا ($longest حرفًا).", "المشهد ${ts.index + 1} (chat)", ts.index, fix = "split")
                }
            }
        }

        // Static scenes: gaps without any visual event (message, typing, trace step, card, sound cue)
        val events = (timeline.sfx.map { it.timeMs } + timeline.texts.map { it.startMs } + timeline.scenes.map { it.startMs } + total).sorted()
        for (k in 1 until events.size) {
            val gap = events[k] - events[k - 1]
            if (gap > Limits.MAX_STATIC_MS) {
                val ts = timeline.sceneAt(events[k - 1])
                p += Problem(
                    Severity.WARNING, "q.static", "لقطة ثابتة ${fmt(gap / 1000.0)} ث بلا أي تغيير مرئي (الحد 8 ث).",
                    "المشهد ${ts.index + 1} (${ts.scene.typeId()})", ts.index,
                )
            }
        }

        // Silent gaps (no voice and no SFX) when there is no ambient bed
        if (false) {
            val audio = (timeline.sfx.map { it.timeMs to it.timeMs + 300 } + timeline.voices.map { it.startMs to it.startMs + it.durationMs }).sortedBy { it.first }
            var cursor = 0L
            for ((s, e) in audio) {
                if (s - cursor > Limits.MAX_SILENT_GAP_MS) {
                    val ts = timeline.sceneAt(cursor)
                    p += Problem(Severity.INFO, "q.silence", "صمت تام ${fmt((s - cursor) / 1000.0)} ث. فعّل الخلفية الصوتية الهادئة.", "المشهد ${ts.index + 1}", ts.index)
                }
                cursor = maxOf(cursor, e)
            }
        }

        // Simultaneous moving elements (sampled at 5 fps)
        var worst = 0; var worstAt = 0L
        var t = 0L
        while (t < total) {
            val n = movers(timeline, t)
            if (n > worst) { worst = n; worstAt = t }
            t += 200
        }
        if (worst > Limits.MAX_SIMULTANEOUS_MOVERS) {
            val ts = timeline.sceneAt(worstAt)
            p += Problem(Severity.WARNING, "q.movers", "$worst عناصر تتحرك معًا (الحد ${Limits.MAX_SIMULTANEOUS_MOVERS}).", "المشهد ${ts.index + 1}", ts.index)
        }

        // Loudness jumps
        loudnessBlocks?.let { blocks ->
            var jumps = 0
            for (k in 1 until blocks.size) if (blocks[k] > -50 && blocks[k - 1] > -50 && blocks[k] - blocks[k - 1] > 10) jumps++
            if (jumps > 0) p += Problem(Severity.WARNING, "q.loud", "$jumps قفزات مفاجئة في ارتفاع الصوت. خفّض المؤثرات.", "الصوت")
        }

        // Required content for retention
        val types = scenario.scenes.map { it.typeId() }
        if ("key_sentence" !in types) p += Problem(Severity.WARNING, "q.key", "لا توجد جملة اليوم.", "المشاهد")
        if ("teaser" !in types) p += Problem(Severity.WARNING, "q.teaser", "لا يوجد تشويق للحلقة القادمة.", "المشاهد")

        val unique = p.distinctBy { Triple(it.code, it.sceneIndex, it.message) }
        val penalty = unique.sumOf { when (it.severity) { Severity.ERROR -> 25; Severity.WARNING -> 6; Severity.INFO -> 2 }.toInt() }
        return QualityReport((100 - penalty).coerceIn(0, 100), unique.sortedBy { it.severity.ordinal }, total)
    }

    fun movers(timeline: Timeline, t: Long): Int {
        val f = timeline.frameAt(t)
        var n = 0
        if (f.layers.size > 1) n++ // stage transition
        for (l in f.layers) {
            l.chat?.let { c ->
                n += c.messages.count { it.appear in 0.01f..0.99f }
                if (c.typingFrom != null) n++
                n += c.messages.count { it.reactionPulse > 0.01f }
            }
            l.code?.let { c ->
                if (c.cursorVisible && c.text.isNotEmpty() && c.running.not() && c.activeLine < 0) n += 0
                n += c.vars.count { it.change in 0.01f..0.99f }
                if (c.note != null && c.note.appear in 0.01f..0.99f) n++
            }
            l.card?.let { if (it.appear in 0.01f..0.99f) n++ }
            l.hook?.let { h -> if (h.pulse > 0.01f) n++; if (h.ring in 0.01f..0.99f) n++ }
        }
        f.engageChip?.let { if (it.appear in 0.01f..0.99f || it.heartPulse > 0.01f) n++ }
        return n
    }

    private fun fmt(d: Double) = if (d >= 10) d.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", d)

    // ------------------------------------------------------------ one-tap fixes

    /** Splits a long chat bubble into two messages at the sentence boundary nearest the middle. */
    fun splitBubble(s: Scenario, index: Int): Scenario {
        val sc = s.scenes.getOrNull(index) as? Scene.Chat ?: return s
        val text = sc.text
        val candidates = Regex("[.!؟?،,]\\s").findAll(text).map { it.range.last }.toList()
        val mid = text.length / 2
        val cut = candidates.minByOrNull { abs(it - mid) } ?: text.lastIndexOf(' ', mid).takeIf { it > 0 } ?: return s
        val a = text.substring(0, cut).trim()
        val b = text.substring(cut).trim()
        if (a.isEmpty() || b.isEmpty()) return s
        val list = s.scenes.toMutableList()
        list[index] = sc.copy(text = a, reactions = emptyList(), challenge = false)
        list.add(index + 1, sc.copy(text = b, sfx = null))
        return s.copy(scenes = list)
    }

    /** Moves the engage moment to the scene boundary closest to 48 s that follows the first run. */
    fun shiftEngage(s: Scenario, timeline: Timeline): Scenario {
        val idx = s.scenes.indexOfFirst { it is Scene.EngageMoment }
        if (idx < 0) return s
        val engage = s.scenes[idx]
        val without = s.scenes.toMutableList().also { it.removeAt(idx) }
        val firstRun = without.indexOfFirst { it is Scene.Run }.let { if (it < 0) 2 else it + 1 }
        // boundary times computed from the existing timeline minus the engage duration
        val engageDur = timeline.scenes[idx].durationMs
        var best = firstRun; var bestDiff = Long.MAX_VALUE
        var acc = 0L
        val starts = timeline.scenes.filter { it.index != idx }.map { it.durationMs }
        for (k in 0..without.size) {
            if (k >= firstRun && k <= without.size - 2) {
                val d = abs(acc - 48_000)
                if (d < bestDiff) { bestDiff = d; best = k }
            }
            if (k < starts.size) acc += starts[k]
        }
        @Suppress("UNUSED_VARIABLE") val unused = engageDur
        without.add(best.coerceIn(1, without.size - 1), engage)
        return s.copy(scenes = without)
    }
}
