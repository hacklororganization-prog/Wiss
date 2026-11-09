package studio.pyreels.core.timeline

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import studio.pyreels.core.model.Limits
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.Scene
import studio.pyreels.core.model.SfxIds
import studio.pyreels.core.model.TraceStep
import studio.pyreels.core.model.firstTeacherId
import studio.pyreels.core.model.typeId
import studio.pyreels.core.speech.SpeakCode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** A sound cue on the SFX track. `seed` drives humanisation (pitch/gain/timing variation). */
data class SfxCue(val timeMs: Long, val id: String, val gain: Float = 1f, val seed: Int = 0)

/** A voice-over segment; the platform synthesises [speech] and the mixer places it at [startMs]. */
data class VoiceCue(val sceneIndex: Int, val castId: String?, val startMs: Long, val durationMs: Long, val display: String, val speech: String)

/** A piece of on-screen text with its time window (used for SRT subtitles and caption checks). */
data class TextCue(val startMs: Long, val endMs: Long, val speaker: String?, val text: String)

/** Real execution result supplied by the Python bridge for a `run` scene. */
data class RunResult(val trace: List<TraceStep>, val output: String, val error: String?)

data class TimedScene(val index: Int, val scene: Scene, val startMs: Long, val durationMs: Long, val stage: Stage) {
    val endMs get() = startMs + durationMs
}

/**
 * The deterministic timeline. Built once from a scenario (+ measured voice durations and real run
 * results) and then sampled with [frameAt] for preview (60 fps) and export (30 fps).
 */
class Timeline private constructor(
    val scenario: Scenario,
    val scenes: List<TimedScene>,
    val fit: DurationFitter.Fit,
    private val runResults: Map<Int, RunResult>,
    private val voiceMs: Map<Int, Long>,
) {
    val totalMs: Long = scenes.lastOrNull()?.endMs ?: 0L

    private val teacher = scenario.firstTeacherId() ?: "python"
    private val messages = ArrayList<PlannedMessage>()
    private val typing = HashMap<Int, TypingPlan>()
    private val runs = HashMap<Int, RunPlan>()
    private val mistakes = HashMap<Int, MistakePlan>()
    val sfx = ArrayList<SfxCue>()
    val voices = ArrayList<VoiceCue>()
    val texts = ArrayList<TextCue>()

    init { plan() }

    // ---------------------------------------------------------------- planning

    private class PlannedMessage(
        val id: Int, val sceneIndex: Int, val from: String, val kind: MessageKind, val text: String, val code: String?,
        val typingStart: Long, val appearAt: Long, val reactions: List<String>, val challenge: Boolean,
        val analogyTitle: String? = null, val analogyItems: List<Pair<String, String>> = emptyList(),
        val itemTimes: List<Long> = emptyList(), val revealAnswer: String? = null, val revealCode: String? = null,
        val voice: VoiceCue? = null,
        val sticker: String? = null,
        val codeScreenshot: Boolean = false,
    )

    /** Edit snapshots of the editor while typing: at time[i] the editor shows text[i]. */
    private class TypingPlan(val times: LongArray, val texts: Array<String>, val start: Long, val end: Long, val focus: List<Int>, val language: String)

    private class RunPlan(
        val code: String, val language: String, val start: Long, val stepTimes: LongArray, val steps: List<TraceStep>,
        val outLines: List<Pair<Long, String>>, val errorAt: Long?, val error: String?, val end: Long,
    )

    private class MistakePlan(
        val code: String, val fix: String, val language: String, val error: String, val explain: String, val from: String,
        val start: Long, val codeEnd: Long, val errorAt: Long, val noteAt: Long, val fixTyping: TypingPlan, val okAt: Long, val okText: String?,
        val voice: VoiceCue?,
    )

    private fun plan() {
        var msgId = 0
        var lastCode: String? = null
        var lastLang = "python"
        for (ts in scenes) {
            val sc = ts.scene
            val s = ts.startMs
            val d = ts.durationMs
            val i = ts.index
            val voice = voiceCue(ts)
            voice?.let { voices += it }
            when (sc) {
                is Scene.HookPredict -> {
                    sfx += SfxCue(s, sc.sfx ?: SfxIds.HOOK_ENTER, 0.9f, i)
                    val ringStart = s + d - sc.holdMs.coerceIn(1500, 2500)
                    // Gentle 1 Hz ticks during the suspense (never faster than 1 Hz).
                    var tick = ringStart + 1000
                    while (tick < s + d - 200) { sfx += SfxCue(tick, SfxIds.TICK, 0.35f, i * 31 + tick.toInt()); tick += 1000 }
                    texts += TextCue(s + 700, s + d, null, sc.question)
                }
                is Scene.Recap, is Scene.KeySentence, is Scene.Teaser -> {
                    sfx += SfxCue(s, sc.sfx ?: SfxIds.SOFT_WHOOSH, 0.5f, i)
                    if (sc is Scene.KeySentence) sfx += SfxCue(s + 450, SfxIds.SUCCESS_CHIME, 0.45f, i)
                    texts += TextCue(s + 300, s + d - 200, null, sc.readableText())
                    if (sc is Scene.Teaser && !sc.challenge.isNullOrBlank()) texts += TextCue(s + 1800, s + d - 200, null, sc.challenge)
                }
                is Scene.Chat -> {
                    val typingMs = DurationEstimator.chatTypingMs(sc.text, d, sc.from == "ahmed")
                    val appear = s + typingMs
                    val outgoing = sc.from == "ahmed"
                    sfx += SfxCue(appear, sc.sfx ?: if (outgoing) SfxIds.MSG_SEND else SfxIds.MSG_RECV, 0.8f, i)
                    if (sc.reactions.isNotEmpty()) sfx += SfxCue(appear + 900, SfxIds.HEART_PULSE, 0.5f, i)
                    messages += PlannedMessage(msgId++, i, sc.from, MessageKind.TEXT, sc.text, sc.code, s, appear, sc.reactions, sc.challenge, voice = voice, codeScreenshot = sc.codeStyle == "screenshot")
                    texts += TextCue(appear, s + d, sc.from, sc.text)
                    val sticker = sc.sticker ?: if (i == celebrationIndex(scenario)) "party" else null
                    if (sticker != null) {
                        val at = appear + 1_000
                        messages += PlannedMessage(msgId++, i, sc.from, MessageKind.STICKER, "", null, at, at, emptyList(), false, sticker = sticker)
                        sfx += SfxCue(at, SfxIds.SUCCESS_CHIME, 0.4f, i + 900)
                    }
                }
                is Scene.Analogy -> {
                    val from = sc.from ?: teacher
                    val appear = s + min(700, d / 4)
                    val n = sc.items.size
                    val itemsStart = appear + 700
                    val span = max(0L, d - (itemsStart - s) - 900)
                    val times = List(n) { k -> itemsStart + if (n <= 1) 0 else span * k / n }
                    sfx += SfxCue(appear, sc.sfx ?: SfxIds.MSG_RECV, 0.8f, i)
                    times.forEachIndexed { k, t -> sfx += SfxCue(t, SfxIds.POP, 0.45f, i * 7 + k) }
                    messages += PlannedMessage(
                        msgId++, i, from, MessageKind.ANALOGY, sc.text ?: "", null, s, appear, emptyList(), false,
                        sc.title, sc.items.map { it.label to it.value }, times, voice = voice,
                    )
                    texts += TextCue(appear, s + d, from, listOfNotNull(sc.title, sc.text).joinToString(" — "))
                }
                is Scene.CodeType -> {
                    val plan = typingPlan(sc.code, sc.typing, sc.typo, s + 350, s + d - 1_000, sc.focusLines, sc.language, i)
                    typing[i] = plan
                    var k = 0
                    while (k < plan.times.size) {
                        sfx += SfxCue(plan.times[k], SfxIds.KEY_TAP, 0.28f, i * 1009 + k)
                        k += if (plan.times.size > 300) 2 else 1
                    }
                    lastCode = sc.code; lastLang = sc.language
                    sc.caption?.let { texts += TextCue(s + 300, s + d, teacher, it) }
                }
                is Scene.Run -> {
                    val code = sc.code ?: lastCode ?: ""
                    lastCode = code; lastLang = sc.language
                    val rr = runResults[i]
                    val steps = rr?.trace?.takeIf { it.isNotEmpty() } ?: sc.trace
                    val output = rr?.output ?: sc.output
                    val error = rr?.error ?: sc.error
                    val runStart = s + 500
                    val tail = 600L + min(1500L, output.length * 25L) + 900
                    val span = max(0L, d - 500 - tail)
                    val stepDur = if (steps.isEmpty()) 0 else span / steps.size
                    val stepTimes = LongArray(steps.size) { runStart + it * stepDur }
                    val outs = ArrayList<Pair<Long, String>>()
                    val declaredOut = steps.mapIndexedNotNull { k, st -> st.out?.let { stepTimes[k] + stepDur / 3 to it } }
                    if (declaredOut.isNotEmpty()) outs += declaredOut
                    else if (output.isNotEmpty()) {
                        val at = runStart + span
                        output.lines().forEachIndexed { k, line -> outs += (at + k * 350L) to line }
                    }
                    val errorAt = error?.let { runStart + span + 200 }
                    sfx += SfxCue(s + 200, sc.sfx ?: SfxIds.RUN_START, 0.6f, i)
                    stepTimes.forEachIndexed { k, t -> if (k > 0) sfx += SfxCue(t, SfxIds.TICK, 0.25f, i * 13 + k) }
                    val lastOut = outs.lastOrNull()?.first ?: (runStart + span)
                    if (error != null) sfx += SfxCue(errorAt!!, SfxIds.GENTLE_ERROR, 0.55f, i)
                    else if (output.isNotEmpty()) sfx += SfxCue(lastOut + 500, SfxIds.SUCCESS_CHIME, 0.5f, i)
                    runs[i] = RunPlan(code, sc.language, runStart, stepTimes, steps, outs, errorAt, error, s + d)
                    outs.forEach { (t, line) -> texts += TextCue(t, s + d, null, line) }
                    sc.caption?.let { texts += TextCue(s + 300, s + d, teacher, it) }
                }
                is Scene.EngageMoment -> {
                    val appear = s + 700
                    sfx += SfxCue(appear, sc.sfx ?: SfxIds.MSG_RECV, 0.8f, i)
                    sfx += SfxCue(appear + 900, SfxIds.HEART_PULSE, 0.5f, i)
                    messages += PlannedMessage(msgId++, i, sc.from, MessageKind.ENGAGE, sc.text, null, s, appear, listOf("heart"), false, voice = voice)
                    val bridgeTyping = s + d - 1_900
                    messages += PlannedMessage(msgId++, i, sc.from, MessageKind.TEXT, sc.bridge, null, bridgeTyping, bridgeTyping + 600, emptyList(), false)
                    sfx += SfxCue(bridgeTyping + 600, SfxIds.MSG_RECV, 0.7f, i + 500)
                    texts += TextCue(appear, bridgeTyping + 600, sc.from, sc.text)
                    texts += TextCue(bridgeTyping + 600, s + d, sc.from, sc.bridge)
                }
                is Scene.Reveal -> {
                    val from = sc.from ?: teacher
                    val appear = s + min(800, d / 4)
                    val hook = scenario.scenes.filterIsInstance<Scene.HookPredict>().firstOrNull()
                    sfx += SfxCue(appear, sc.sfx ?: SfxIds.MSG_RECV, 0.8f, i)
                    sfx += SfxCue(appear + 600, SfxIds.SUCCESS_CHIME, 0.5f, i)
                    messages += PlannedMessage(msgId++, i, from, MessageKind.REVEAL, sc.text, null, s, appear, emptyList(), false, revealAnswer = sc.answer, revealCode = hook?.code, voice = voice)
                    texts += TextCue(appear, s + d, from, "${sc.answer} — ${sc.text}")
                }
                is Scene.CommonMistake -> {
                    val from = sc.from ?: teacher
                    val codeEnd = s + 1_200
                    val errorAt = codeEnd + 500
                    val noteAt = errorAt + 900
                    val fixStart = max(noteAt + DurationEstimator.readMs(sc.explain) - 400, noteAt + 1_500).coerceAtMost(s + d - 2_400)
                    val fixPlan = typingPlan(sc.fix, "steady", false, fixStart, s + d - 1_700, emptyList(), sc.language, i, fast = true)
                    val okAt = fixPlan.end + 500
                    sfx += SfxCue(s + 200, sc.sfx ?: SfxIds.RUN_START, 0.5f, i)
                    sfx += SfxCue(errorAt, SfxIds.GENTLE_ERROR, 0.55f, i)
                    sfx += SfxCue(noteAt, SfxIds.MSG_RECV, 0.6f, i + 77)
                    sfx += SfxCue(okAt + 300, SfxIds.SUCCESS_CHIME, 0.5f, i)
                    mistakes[i] = MistakePlan(sc.code, sc.fix, sc.language, sc.error, sc.explain, from, s, codeEnd, errorAt, noteAt, fixPlan, okAt, sc.fixOutput, voice)
                    texts += TextCue(errorAt, noteAt, null, sc.error)
                    texts += TextCue(noteAt, s + d, from, sc.explain)
                    lastCode = sc.fix; lastLang = sc.language
                }
            }
        }
        sfx.sortBy { it.timeMs }
        texts.sortBy { it.startMs }
    }

    private fun voiceCue(ts: TimedScene): VoiceCue? {
        val sc = ts.scene
        if (!sc.say) return null
        val display = sc.readableText().ifBlank { return null }
        val speech = sc.spoken ?: if (sc.speakCode) SpeakCode.toSpeech(display) else SpeakCode.cleanForSpeech(display)
        val start = when (sc) {
            is Scene.Chat -> ts.startMs + DurationEstimator.chatTypingMs(sc.text, ts.durationMs, sc.from == "ahmed")
            is Scene.EngageMoment, is Scene.Analogy, is Scene.Reveal -> ts.startMs + 700
            is Scene.CommonMistake -> ts.startMs + 1_200 + 500 + 900
            is Scene.HookPredict -> ts.startMs + 700
            else -> ts.startMs + 350
        }
        val dur = voiceMs[ts.index] ?: estimateSpeechMs(speech)
        val cast = when (sc) {
            is Scene.Chat -> sc.from
            is Scene.EngageMoment -> sc.from
            is Scene.Analogy -> sc.from ?: teacher
            is Scene.Reveal -> sc.from ?: teacher
            is Scene.CommonMistake -> sc.from ?: teacher
            else -> teacher
        }
        return VoiceCue(ts.index, cast, start, dur, display, speech)
    }

    private fun typingPlan(
        code: String, mode: String, typo: Boolean, start: Long, deadline: Long, focus: List<Int>, language: String, seed: Int,
        fast: Boolean = false,
    ): TypingPlan {
        val rnd = Rng(seed * 7919L + code.length)
        val times = ArrayList<Long>()
        val texts = ArrayList<String>()
        if (mode == "instant") {
            return TypingPlan(longArrayOf(start), arrayOf(code), start, start, focus, language)
        }
        // Natural typing: variable speed, line pauses, occasional thinking pauses, optional typo-and-fix.
        val typoAt = if (typo) findTypoPosition(code) else -1
        var t = 0L
        val sb = StringBuilder()
        var k = 0
        while (k < code.length) {
            val c = code[k]
            if (k == typoAt && k + 1 < code.length) {
                // type the two letters swapped, pause, delete both, then continue normally
                sb.append(code[k + 1]); t += 70; times += t; texts += sb.toString()
                sb.append(c); t += 70; times += t; texts += sb.toString()
                t += 380
                sb.setLength(sb.length - 1); t += 110; times += t; texts += sb.toString()
                sb.setLength(sb.length - 1); t += 110; times += t; texts += sb.toString()
                t += 150
            }
            val base = when {
                mode == "steady" || fast -> 50L
                c == ' ' && k > 0 && code[k - 1] == ' ' -> 14L // indentation is fast
                else -> 62L + rnd.nextInt(36)
            }
            t += base
            if (k > 0 && code[k - 1] == '\n') t += if (fast) 90 else 240
            if (!fast && mode == "natural" && rnd.nextInt(100) < 4) t += 260 + rnd.nextInt(200)
            sb.append(c)
            times += t; texts += sb.toString()
            k++
        }
        val natural = max(1L, t)
        val available = max(400L, deadline - start)
        val scale = if (natural > available) available.toDouble() / natural else 1.0
        val arr = LongArray(times.size) { start + (times[it] * scale).roundToLong() }
        val end = arr.lastOrNull() ?: start
        return TypingPlan(arr, texts.toTypedArray(), start, end, focus, language)
    }

    private fun findTypoPosition(code: String): Int {
        val m = Regex("[A-Za-z_]{4,}").find(code) ?: return -1
        return m.range.first + 1
    }

    // ---------------------------------------------------------------- sampling

    fun sceneAt(t: Long): TimedScene {
        if (scenes.isEmpty()) error("empty timeline")
        var lo = 0; var hi = scenes.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (scenes[mid].startMs <= t) lo = mid else hi = mid - 1
        }
        return scenes[lo]
    }

    fun frameAt(tIn: Long): Frame {
        val t = tIn.coerceIn(0, max(0, totalMs - 1))
        val ts = sceneAt(t)
        val local = t - ts.startMs
        val prev = scenes.getOrNull(ts.index - 1)
        val layers = ArrayList<Layer>(2)
        val transition = ts.scene.transition ?: "fade"
        if (prev != null && prev.stage != ts.stage && local < Easing.STAGE_MS) {
            val mix = Easing.window(local, 0, Easing.STAGE_MS)
            layers += layerFor(prev, prev.endMs - 1, 1f - mix, transition, entering = false, mix = mix)
            layers += layerFor(ts, t, mix, transition, entering = true, mix = mix)
        } else {
            layers += layerFor(ts, t, 1f, transition, entering = true, mix = 1f)
        }
        val m = scenario.meta
        return Frame(
            t, totalMs, ts.index, ts.scene.typeId(), layers, captionAt(t), engageChipAt(ts, t),
            m.title, m.series.episode, m.watermark, m.theme,
        )
    }

    private fun layerFor(ts: TimedScene, t: Long, alpha: Float, transition: String, entering: Boolean, mix: Float): Layer {
        val offset = when (transition) {
            "slide" -> if (entering) (1f - mix) * 60f else -mix * 30f
            else -> if (entering) (1f - mix) * Easing.RISE_PX else 0f
        }
        val scale = if (transition == "zoom") (if (entering) 0.96f + 0.04f * mix else 1f + 0.02f * mix) else 1f
        return when (ts.stage) {
            Stage.CHAT -> Layer(Stage.CHAT, alpha, offset, scale, chat = chatAt(t))
            Stage.CODE -> Layer(Stage.CODE, alpha, offset, scale, code = codeAt(ts, t))
            Stage.HOOK -> Layer(Stage.HOOK, alpha, offset, scale, hook = hookAt(ts, t))
            Stage.CARD -> Layer(Stage.CARD, alpha, offset, scale, card = cardAt(ts, t))
        }
    }

    private fun hookAt(ts: TimedScene, t: Long): HookView {
        val sc = ts.scene as Scene.HookPredict
        val l = t - ts.startMs
        val hold = sc.holdMs.coerceIn(1500, 2500)
        val ringStart = ts.durationMs - hold
        return HookView(
            sc.code, sc.language, sc.question,
            enter = Easing.window(l, 0, 650),
            pulse = Easing.pulse(l, 450, 900),
            questionAppear = Easing.window(l, 650, Easing.ENTER_MS),
            ring = 1f - Easing.linear(l, ringStart, hold),
            lineReveal = Easing.linear(l, 80, 700),
        )
    }

    private fun cardAt(ts: TimedScene, t: Long): CardView {
        val l = t - ts.startMs
        val appear = Easing.window(l, 0, Easing.ENTER_MS)
        val exitStart = ts.durationMs - Easing.EXIT_MS
        val alpha = appear * (1f - Easing.window(l, exitStart, Easing.EXIT_MS))
        val zoom = Easing.linear(l, 0, ts.durationMs)
        return when (val sc = ts.scene) {
            is Scene.Recap -> CardView(CardKind.RECAP, "تذكير من الحلقة السابقة", sc.text, null, alpha, zoom, Easing.linear(l, 200, 900))
            is Scene.KeySentence -> CardView(CardKind.KEY_SENTENCE, "جملة اليوم", sc.text, null, alpha, zoom, Easing.linear(l, 200, 900))
            is Scene.Teaser -> CardView(CardKind.TEASER, "في الحلقة القادمة", sc.text, sc.challenge, alpha, zoom, Easing.linear(l, 200, 900))
            else -> CardView(CardKind.RECAP, "", sc.readableText(), null, alpha, zoom, Easing.linear(l, 200, 900))
        }
    }

    private fun chatAt(t: Long): ChatView {
        val visible = ArrayList<ChatMessageView>()
        var typingFrom: String? = null
        var typingPhase = 0f
        var scroll = 1f
        var composeText: String? = null
        var composeChars = 0
        // Binary search the last message that has started typing.
        var last = -1
        var lo = 0; var hi = messages.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (messages[mid].typingStart <= t) { last = mid; lo = mid + 1 } else hi = mid - 1
        }
        val first = max(0, last - 13)
        for (k in first..last) {
            val m = messages[k]
            if (m.appearAt > t) {
                typingFrom = m.from
                typingPhase = ((t - m.typingStart) % 1200L) / 1200f
                if (m.from == "ahmed") {
                    composeText = m.text
                    composeChars = (m.text.length * Easing.linear(t, m.typingStart + 150, m.appearAt - m.typingStart - 250)).toInt()
                }
                continue
            }
            val since = t - m.appearAt
            val appear = Easing.window(since, 0, Easing.ENTER_MS)
            if (k == last) scroll = appear
            val itemsVisible = m.itemTimes.count { it <= t }
            val karaoke = m.voice?.let { v -> wordIndex(v, t) } ?: -1
            visible += ChatMessageView(
                id = m.id, from = m.from, outgoing = m.from == "ahmed", kind = m.kind, text = m.text, code = m.code,
                appear = appear, timeLabel = clockLabel(m.id), ticksRead = since > 700,
                reactions = if (since > 900) m.reactions else emptyList(),
                reactionPulse = Easing.pulse(since, 900, 900),
                analogyTitle = m.analogyTitle, analogyItems = m.analogyItems, analogyVisible = itemsVisible,
                revealAnswer = m.revealAnswer, revealCode = m.revealCode, challenge = m.challenge, karaokeWord = karaoke,
                sticker = m.sticker, codeScreenshot = m.codeScreenshot, ageMs = since,
            )
        }
        val members = scenario.cast.map { it.name }
        return ChatView(scenario.meta.groupName ?: "مجموعة ${scenario.meta.title}", members, visible, typingFrom, typingPhase, scroll, composeText, composeChars)
    }

    private fun clockLabel(id: Int): String {
        val minutes = 21 * 60 + 14 + id / 3
        val h = (minutes / 60) % 12
        return "${if (h == 0) 12 else h}:${(minutes % 60).toString().padStart(2, '0')} م"
    }

    private fun codeAt(ts: TimedScene, t: Long): CodeView {
        val sc = ts.scene
        val l = t - ts.startMs
        val teacherNote = { text: String?, appearAt: Long, voice: VoiceCue? ->
            text?.let { NoteView(teacher, it, Easing.window(t, appearAt, Easing.ENTER_MS), voice?.let { v -> wordIndex(v, t) } ?: -1) }
        }
        when (sc) {
            is Scene.CodeType -> {
                val p = typing.getValue(ts.index)
                val (text, idx) = snapshot(p, t)
                val done = t >= p.end
                val kb = Easing.window(t, ts.startMs, 380) * (1f - Easing.window(t, p.end + 250, 420))
                val pressed = pressedKey(p, idx, t)
                val focusMix = if (done) Easing.window(t, p.end + 200, 450) else 0f
                val voice = voices.firstOrNull { it.sceneIndex == ts.index }
                return CodeView(
                    p.language, fileName(p.language), text,
                    cursorVisible = !done || ((t / 530) % 2 == 0L), // soft cursor, < 1 Hz blink
                    cursorIndex = text.length, activeLine = if (done && p.focus.isNotEmpty()) p.focus.first() else -1,
                    activeLineMix = focusMix, focusLines = p.focus, dim = if (p.focus.isNotEmpty()) 0.55f * focusMix else 0f,
                    zoom = if (p.focus.isNotEmpty()) 0.06f * focusMix else 0f,
                    terminal = emptyList(), terminalVisible = 0f, vars = emptyList(), iteration = null, stack = emptyList(),
                    note = teacherNote(sc.caption, ts.startMs + 300, voice), running = false,
                    keyboard = kb, pressedKey = pressed, modified = !done,
                )
            }
            is Scene.Run -> {
                val p = runs.getValue(ts.index)
                var step = -1
                for (k in p.stepTimes.indices) if (p.stepTimes[k] <= t) step = k
                val active = if (step >= 0) p.steps[step].line else -1
                val stepStart = if (step >= 0) p.stepTimes[step] else p.start
                val vars = varsAt(p.steps, step, t - stepStart)
                val term = ArrayList<TermLine>()
                if (t >= p.start - 300) term += TermLine("$ ${runCommand(p.language)}", visibleChars(t, p.start - 300, runCommand(p.language).length + 2, 18), TermKind.CMD)
                for ((at, line) in p.outLines) if (at <= t) term += TermLine(line, visibleChars(t, at, line.length, 28), TermKind.OUT)
                if (p.errorAt != null && t >= p.errorAt && p.error != null) term += TermLine(p.error, visibleChars(t, p.errorAt, p.error.length, 14), TermKind.ERR)
                val finished = p.stepTimes.isEmpty() || t > p.stepTimes.last() + 900
                val voice = voices.firstOrNull { it.sceneIndex == ts.index }
                return CodeView(
                    p.language, fileName(p.language), p.code, cursorVisible = false, cursorIndex = -1,
                    activeLine = if (finished && p.stepTimes.isNotEmpty()) -1 else active,
                    activeLineMix = if (step >= 0) Easing.window(t, stepStart, 420) else 0f,
                    focusLines = emptyList(), dim = if (active > 0 && !finished) 0.5f else 0f, zoom = 0f,
                    terminal = term.takeLast(Limits.MAX_OUTPUT_LINES + 1), terminalVisible = Easing.window(l, 0, Easing.ENTER_MS),
                    vars = vars, iteration = if (step >= 0) p.steps[step].iteration else null,
                    stack = if (step >= 0) p.steps[step].stack else emptyList(),
                    previousLine = if (step > 0) p.steps[step - 1].line else -1,
                    note = teacherNote(sc.caption, ts.startMs + 300, voice), running = !finished,
                    runPress = Easing.pulse(t, ts.startMs + 80, 520),
                    errorLine = if (p.errorAt != null && t >= p.errorAt) p.steps.lastOrNull()?.line ?: 1 else -1,
                )
            }
            is Scene.CommonMistake -> {
                val p = mistakes.getValue(ts.index)
                val fixing = t >= p.fixTyping.start
                val text = if (!fixing) p.code.take(((p.code.length * Easing.linear(t, p.start, p.codeEnd - p.start)).toInt())) else snapshot(p.fixTyping, t).first
                val term = ArrayList<TermLine>()
                val cmd = "$ ${runCommand(p.language)}"
                if (t >= p.codeEnd) term += TermLine(cmd, visibleChars(t, p.codeEnd, cmd.length, 14), TermKind.CMD)
                if (t >= p.errorAt) term += TermLine(p.error, visibleChars(t, p.errorAt, p.error.length, 12), TermKind.ERR)
                if (t >= p.okAt) {
                    term += TermLine(cmd, visibleChars(t, p.okAt, cmd.length, 14), TermKind.CMD)
                    term += TermLine(p.okText ?: "✓", visibleChars(t, p.okAt + 300, (p.okText ?: "✓").length, 25), TermKind.OK)
                }
                return CodeView(
                    p.language, fileName(p.language), text, cursorVisible = t < p.codeEnd || (fixing && t < p.fixTyping.end), cursorIndex = text.length,
                    activeLine = -1, activeLineMix = 0f, focusLines = emptyList(), dim = 0f, zoom = 0f,
                    terminal = term.takeLast(Limits.MAX_OUTPUT_LINES + 1), terminalVisible = Easing.window(l, 0, Easing.ENTER_MS),
                    vars = emptyList(), iteration = null, stack = emptyList(),
                    note = NoteView(p.from, p.explain, Easing.window(t, p.noteAt, Easing.ENTER_MS), p.voice?.let { wordIndex(it, t) } ?: -1)
                        .takeIf { t >= p.noteAt },
                    running = false, errorLine = if (t >= p.errorAt && !fixing) 1 else -1,
                    keyboard = if (fixing) Easing.window(t, p.fixTyping.start - 400, 380) * (1f - Easing.window(t, p.fixTyping.end + 200, 400)) else 0f,
                    pressedKey = if (fixing) pressedKey(p.fixTyping, snapshot(p.fixTyping, t).second, t) else null,
                    runPress = maxOf(Easing.pulse(t, p.codeEnd - 200, 520), Easing.pulse(t, p.okAt - 250, 520)),
                    modified = fixing && t < p.fixTyping.end,
                )
            }
            else -> error("not a code scene")
        }
    }

    private fun pressedKey(p: TypingPlan, idx: Int, t: Long): Char? {
        if (idx < 0 || t - p.times[idx] > 110) return null
        val cur = p.texts[idx]
        val prev = if (idx > 0) p.texts[idx - 1] else ""
        return if (cur.length < prev.length) '\b' else cur.lastOrNull()
    }

    private fun snapshot(p: TypingPlan, t: Long): Pair<String, Int> {
        if (p.times.isEmpty() || t < p.times[0]) return "" to -1
        var lo = 0; var hi = p.times.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (p.times[mid] <= t) lo = mid else hi = mid - 1
        }
        return p.texts[lo] to lo
    }

    private fun varsAt(steps: List<TraceStep>, step: Int, sinceStep: Long): List<VarView> {
        if (step < 0) return emptyList()
        val current = LinkedHashMap<String, String>()
        val previous = LinkedHashMap<String, String>()
        for (k in 0..step) {
            if (k == step) previous.putAll(current)
            steps[k].vars.forEach { (n, v) -> current[n] = render(v) }
        }
        val change = Easing.window(sinceStep, 0, 600)
        return current.map { (n, v) -> VarView(n, v, previous[n]?.takeIf { it != v }, if (previous[n] != v) change else 1f) }
    }

    private fun render(v: JsonElement): String = if (v is JsonPrimitive && v.isString) "\"${v.content}\"" else v.toString()

    private fun visibleChars(t: Long, start: Long, len: Int, msPerChar: Int): Int =
        if (t < start) 0 else min(len, ((t - start) / msPerChar).toInt() + 1)

    private fun engageChipAt(ts: TimedScene, t: Long): EngageChipView? {
        val sc = ts.scene as? Scene.EngageMoment ?: return null
        val l = t - ts.startMs
        val start = 1_300L
        // the chip leaves before the bridge line starts typing (bridge typing starts at d - 1900)
        val dur = minOf(4_000L, ts.durationMs - 1_900L - start - 150L).coerceAtLeast(1_500L)
        if (l < start || l > start + dur) return null
        val appear = Easing.window(l, start, 450) * (1f - Easing.window(l, start + dur - 450, 450))
        return EngageChipView(sc.actions, appear, Easing.pulse(l, start + 200, 1_000))
    }

    private fun captionAt(t: Long): CaptionView? {
        val v = voices.firstOrNull { t >= it.startMs && t < it.startMs + it.durationMs + 300 } ?: return null
        val sc = scenes[v.sceneIndex].scene
        // Captions only where the spoken text is not already on screen.
        if (sc.spoken == null) return null
        val words = v.speech.split(' ').filter { it.isNotBlank() }
        val appear = Easing.window(t, v.startMs, 300) * (1f - Easing.window(t, v.startMs + v.durationMs, 300))
        return CaptionView(words, wordIndexOf(words, v.startMs, v.durationMs, t), appear, rtl = true)
    }

    /** Word currently being spoken in a voice cue (karaoke). */
    fun wordIndex(v: VoiceCue, t: Long): Int {
        if (t < v.startMs || t > v.startMs + v.durationMs) return -1
        return wordIndexOf(v.display.split(' ').filter { it.isNotBlank() }, v.startMs, v.durationMs, t)
    }

    private fun wordIndexOf(words: List<String>, start: Long, dur: Long, t: Long): Int {
        if (words.isEmpty() || t < start) return -1
        val total = words.sumOf { it.length + 1 }.toDouble()
        var acc = 0.0
        val x = (t - start).toDouble() / max(1L, dur) * total
        words.forEachIndexed { i, w ->
            acc += w.length + 1
            if (x < acc) return i
        }
        return words.lastIndex
    }

    /** Word timings of all voice cues (for SRT and captions). */
    fun wordTimings(v: VoiceCue): List<Triple<String, Long, Long>> {
        val words = v.display.split(' ').filter { it.isNotBlank() }
        val total = words.sumOf { it.length + 1 }.toDouble().coerceAtLeast(1.0)
        var acc = 0.0
        return words.map { w ->
            val s = v.startMs + (acc / total * v.durationMs).roundToLong()
            acc += w.length + 1
            Triple(w, s, v.startMs + (acc / total * v.durationMs).roundToLong())
        }
    }

    companion object {
        /** The chat that receives the automatic celebration sticker: Ahmed's first message after the first run. */
        fun celebrationIndex(s: Scenario): Int {
            if (!s.meta.celebrate) return -1
            val firstRun = s.scenes.indexOfFirst { it is Scene.Run }
            if (firstRun < 0) return -1
            return (firstRun + 1 until s.scenes.size).firstOrNull { k -> val sc = s.scenes[k]; sc is Scene.Chat && sc.from == "ahmed" && sc.sticker == null } ?: -1
        }

        fun stageOf(scene: Scene): Stage = when (scene) {
            is Scene.HookPredict -> Stage.HOOK
            is Scene.Recap, is Scene.KeySentence, is Scene.Teaser -> Stage.CARD
            is Scene.CodeType, is Scene.Run, is Scene.CommonMistake -> Stage.CODE
            else -> Stage.CHAT
        }

        fun fileName(language: String) = when (language) {
            "python" -> "main.py"; "java" -> "Main.java"; "cpp" -> "main.cpp"; "c" -> "main.c"; "javascript" -> "main.js"
            "typescript" -> "main.ts"; "kotlin" -> "Main.kt"; "csharp" -> "Program.cs"; "go" -> "main.go"; "rust" -> "main.rs"
            "php" -> "index.php"; "swift" -> "main.swift"; else -> "main.txt"
        }

        fun runCommand(language: String) = when (language) {
            "python" -> "python main.py"; "java" -> "java Main.java"; "cpp" -> "g++ main.cpp && ./a.out"; "c" -> "gcc main.c && ./a.out"
            "javascript" -> "node main.js"; "typescript" -> "ts-node main.ts"; "kotlin" -> "kotlin Main.kt"; "csharp" -> "dotnet run"
            "go" -> "go run main.go"; "rust" -> "cargo run"; "php" -> "php index.php"; "swift" -> "swift main.swift"; else -> "run"
        }

        /** Rough TTS duration estimate used before real synthesis (≈ 13 chars/s for Arabic). */
        fun estimateSpeechMs(text: String): Long = (text.length * 75L + 400).coerceAtLeast(800)

        /**
         * Builds the timeline. [voiceMs] holds measured TTS durations per scene index, [runResults]
         * holds real Python execution results per scene index.
         */
        fun build(
            scenario: Scenario,
            voiceMs: Map<Int, Long> = emptyMap(),
            runResults: Map<Int, RunResult> = emptyMap(),
            targetMs: Long = scenario.meta.targetSeconds * 1000L,
        ): Timeline {
            val celebrate = celebrationIndex(scenario)
            val bounds = scenario.scenes.mapIndexed { i, s ->
                val voice = if (s.say) voiceMs[i] ?: estimateSpeechMs(s.spoken ?: s.readableText()) else null
                val b = DurationEstimator.bounds(s, voice)
                // a sticker needs ~1.4 s of screen time after its message
                if ((s is Scene.Chat && s.sticker != null) || i == celebrate) SceneBounds(b.minMs + 1_400, b.idealMs + 1_400, b.maxMs + 1_400) else b
            }
            val fit = DurationFitter.fit(bounds, DurationFitter.calmTarget(bounds, targetMs))
            var start = 0L
            val timed = scenario.scenes.mapIndexed { i, s ->
                TimedScene(i, s, start, fit.durations[i], stageOf(s)).also { start += fit.durations[i] }
            }
            return Timeline(scenario, timed, fit, runResults, voiceMs)
        }
    }
}

/** Small deterministic PRNG (xorshift) so every render of a scenario is identical. */
class Rng(seed: Long) {
    private var x = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed xor 0x5DEECE66DL
    fun nextLong(): Long { x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17); return x }
    fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()
    fun nextFloat(): Float = ((nextLong() ushr 40).toFloat() / (1 shl 24).toFloat())
    /** Uniform in [-1, 1). */
    fun signed(): Float = nextFloat() * 2f - 1f
}
