package studio.pyreels.core.publish

import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.Scene
import studio.pyreels.core.model.castById
import studio.pyreels.core.timeline.Timeline

/**
 * Text pieces of the Publish Pack (section 11.2): word-timed SRT subtitles and the caption file.
 * The video, teaser clip and cover image are rendered by the platform layer from the same timeline.
 */
object PublishPack {

    /**
     * SRT built from every on-screen text cue. Long texts are split into chunks of at most
     * [maxWords] words, each timed proportionally (or by the voice word timings when available).
     */
    fun srt(timeline: Timeline, maxWords: Int = 6): String {
        val cues = ArrayList<Triple<Long, Long, String>>()
        val voiced = timeline.voices.associateBy { it.sceneIndex }
        for (c in timeline.texts) {
            val words = c.text.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) continue
            val name = c.speaker?.let { timeline.scenario.castById(it)?.name }
            val chunks = words.chunked(maxWords)
            val scene = timeline.sceneAt(c.startMs)
            val voice = voiced[scene.index]?.takeIf { it.display.trim() == c.text.trim() }
            if (voice != null) {
                val timing = timeline.wordTimings(voice)
                var w = 0
                for (chunk in chunks) {
                    val s = timing.getOrNull(w)?.second ?: c.startMs
                    val e = timing.getOrNull(w + chunk.size - 1)?.third ?: c.endMs
                    cues += Triple(s, e, prefix(name, w == 0) + chunk.joinToString(" "))
                    w += chunk.size
                }
            } else {
                val dur = (c.endMs - c.startMs).coerceAtLeast(600)
                val totalChars = words.sumOf { it.length + 1 }.toDouble()
                var acc = 0
                chunks.forEachIndexed { k, chunk ->
                    val len = chunk.sumOf { it.length + 1 }
                    val s = c.startMs + (dur * acc / totalChars).toLong()
                    val e = c.startMs + (dur * (acc + len) / totalChars).toLong()
                    cues += Triple(s, e, prefix(name, k == 0) + chunk.joinToString(" "))
                    acc += len
                }
            }
        }
        cues.sortBy { it.first }
        // No overlaps: each cue ends before the next starts.
        val fixed = cues.mapIndexed { i, (s, e, t) ->
            val next = cues.getOrNull(i + 1)?.first
            Triple(s, if (next != null && e > next) maxOf(s + 300, next - 1) else e, t)
        }
        return buildString {
            fixed.forEachIndexed { i, (s, e, t) ->
                append(i + 1).append('\n').append(ts(s)).append(" --> ").append(ts(e)).append('\n')
                append('‫').append(t).append('‬').append("\n\n") // RTL embedding for players
            }
        }
    }

    private fun prefix(name: String?, first: Boolean) = if (name != null && first) "$name: " else ""

    fun ts(ms: Long): String {
        val h = ms / 3_600_000; val m = (ms / 60_000) % 60; val s = (ms / 1000) % 60; val r = ms % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, r)
    }

    data class Caption(val hookLine: String, val description: String, val hashtags: List<String>, val pinnedComment: String) {
        fun render() = buildString {
            append(hookLine).append("\n\n").append(description).append("\n\n")
            append(hashtags.joinToString(" ")).append("\n\n")
            append("📌 تعليق مثبّت:\n").append(pinnedComment).append('\n')
        }
    }

    fun caption(s: Scenario): Caption {
        val hook = s.scenes.filterIsInstance<Scene.HookPredict>().firstOrNull()
        val key = s.scenes.filterIsInstance<Scene.KeySentence>().firstOrNull()?.text
        val teaser = s.scenes.filterIsInstance<Scene.Teaser>().firstOrNull()
        val challenge = s.scenes.filterIsInstance<Scene.Chat>().lastOrNull { it.challenge }?.text ?: teaser?.challenge
        val langs = s.cast.filter { it.role == "teacher" }.mapNotNull { it.language ?: it.id }
        val hookLine = "توقّع قبل أن أشغّل 👀 " + (hook?.code?.lines()?.firstOrNull()?.let { "«$it» … ماذا سيحدث؟" } ?: "")
        val ep = s.meta.series.episode?.let { "الحلقة $it: " } ?: ""
        val description = "$ep${s.meta.title} — درس قصير وهادئ مع أحمد.\n" + (key?.let { "جملة اليوم: $it" } ?: "")
        val tags = LinkedHashSet<String>()
        tags += "#تعلم_البرمجة"; tags += "#برمجة"; tags += "#للمبتدئين"
        langs.forEach { l -> tags += "#" + l.replace("+", "p").replace("#", "sharp").lowercase() }
        tags += "#" + s.meta.lesson.concept.replace(Regex("[^\\p{L}\\p{N}_]"), "_")
        tags += "#coding"; tags += "#learntocode"
        val pinned = (challenge ?: "ما ناتج الكود في بداية الفيديو؟ اكتب توقّعك 👇") + "\n" + (teaser?.text ?: "")
        return Caption(hookLine.trim(), description.trim(), tags.toList(), pinned.trim())
    }

    /**
     * Time windows of the 10–15 s promo teaser clip: the hook, the analogy/explanation build-up and
     * the start of the code typing, then the "next episode" card. The reveal (answer) is never included.
     */
    fun teaserWindow(timeline: Timeline, targetMs: Long = 12_000): List<LongRange> {
        val hook = timeline.scenes.first()
        val ranges = ArrayList<LongRange>()
        ranges += hook.startMs until hook.endMs
        var used = hook.durationMs
        val end = timeline.scenes.lastOrNull { it.scene is Scene.Teaser }
        val endLen = end?.let { minOf(it.durationMs, 2_500L) } ?: 0L
        val revealAt = timeline.scenes.firstOrNull { it.scene is Scene.Reveal || it.scene is Scene.Run }?.startMs ?: timeline.totalMs
        val middle = listOfNotNull(
            timeline.scenes.firstOrNull { it.scene is Scene.Analogy },
            timeline.scenes.firstOrNull { it.scene is Scene.CodeType },
        ).filter { it.endMs <= revealAt }
        for (m in middle) {
            val room = targetMs - endLen - used
            if (room < 1_000) break
            val len = minOf(room, m.durationMs)
            ranges += m.startMs until m.startMs + len
            used += len
        }
        end?.let { ranges += it.startMs until it.startMs + endLen; used += endLen }
        return ranges
    }
}
