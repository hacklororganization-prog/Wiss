package studio.pyreels.core.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Current scenario schema identifier. */
const val SCHEMA_ID = "pyreels/1"

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Scenario(
    @EncodeDefault val schema: String = SCHEMA_ID,
    val meta: Meta,
    val cast: List<CastMember>,
    val scenes: List<Scene>,
)

/** Optional multi-episode wrapper: `{ "schema": "pyreels/1", "batch": [ ... ] }`. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ScenarioBatch(
    @EncodeDefault val schema: String = SCHEMA_ID,
    val batch: List<Scenario>,
)

@Serializable
data class Meta(
    val title: String,
    val language: String = "ar",
    val lesson: Lesson,
    val targetSeconds: Int = 90,
    val theme: String = "calm-dark",
    val series: Series = Series(),
    val ambient: String = "none",
    val watermark: Boolean = true,
    val groupName: String? = null,
    /** Publishing kit written by the AI: description, 5 TikTok + 5 Instagram hashtags, pinned comment. */
    val publish: PublishKit? = null,
    /** "phone" (phone mock-up) or "large" (the app screen fills the whole safe area). */
    val frame: String = "phone",
    /** Messenger accent: green, blue, purple, amber. */
    val chatColor: String = "green",
    /** Automatic celebration sticker after the first successful run. */
    val celebrate: Boolean = true,
)

@Serializable
data class PublishKit(
    val description: String,
    val tiktokHashtags: List<String> = emptyList(),
    val instagramHashtags: List<String> = emptyList(),
    val pinnedComment: String? = null,
)

@Serializable
data class Lesson(
    val id: String,
    val concept: String,
    val level: String = "beginner",
)

@Serializable
data class Series(
    val name: String? = null,
    val episode: Int? = null,
    val previousRecap: String? = null,
    val nextTeaser: String? = null,
)

@Serializable
data class CastMember(
    val id: String,
    val role: String,
    val name: String,
    val language: String? = null,
    val voice: VoiceSpec? = null,
)

@Serializable
data class VoiceSpec(
    val pitch: Float = 1.0f,
    val rate: Float = 1.0f,
    val locale: String? = null,
)

@Serializable
data class VarBox(val label: String, val value: String)

@Serializable
data class TraceStep(
    val line: Int,
    val vars: Map<String, JsonElement> = emptyMap(),
    val out: String? = null,
    val iteration: Int? = null,
    val stack: List<String> = emptyList(),
)

/**
 * A scene of the video. The JSON discriminator is `type`.
 * Common optional fields: `durationMs`, `say`, `sfx`, `spoken`, `speakCode`, `transition`.
 */
@Serializable
sealed class Scene {
    abstract val durationMs: Long?
    abstract val say: Boolean
    abstract val sfx: String?
    abstract val spoken: String?
    abstract val speakCode: Boolean
    abstract val transition: String?

    /** Text that is read (voice + captions) for this scene, before code-to-speech. */
    abstract fun readableText(): String

    @Serializable
    @SerialName("hook_predict")
    data class HookPredict(
        val language: String = "python",
        val code: String,
        val question: String = HOOK_QUESTION,
        val holdMs: Long = 2000,
        override val durationMs: Long? = null,
        override val say: Boolean = false,
        override val sfx: String? = null,
        override val spoken: String? = null,
        override val speakCode: Boolean = false,
        override val transition: String? = null,
    ) : Scene() {
        override fun readableText() = question
    }

    @Serializable
    @SerialName("recap")
    data class Recap(
        val text: String,
        override val durationMs: Long? = null,
        override val say: Boolean = false,
        override val sfx: String? = null,
        override val spoken: String? = null,
        override val speakCode: Boolean = false,
        override val transition: String? = null,
    ) : Scene() {
        override fun readableText() = text
    }

    @Serializable
    @SerialName("chat")
    data class Chat(
        val from: String,
        val text: String,
        val code: String? = null,
        val reactions: List<String> = emptyList(),
        val challenge: Boolean = false,
        /** Sticker sent right after the message: party, star, trophy, heart, rocket, thumbs. */
        val sticker: String? = null,
        /** How `code` is shown: "inline" (code box) or "screenshot" (a photo of the editor). */
        val codeStyle: String = "inline",
        override val durationMs: Long? = null,
        override val say: Boolean = false,
        override val sfx: String? = null,
        override val spoken: String? = null,
        override val speakCode: Boolean = false,
        override val transition: String? = null,
    ) : Scene() {
        override fun readableText() = text
    }

    @Serializable
    @SerialName("analogy")
    data class Analogy(
        val title: String,
        val items: List<VarBox> = emptyList(),
        val from: String? = null,
        val text: String? = null,
        override val durationMs: Long? = null,
        override val say: Boolean = false,
        override val sfx: String? = null,
        override val spoken: String? = null,
        override val speakCode: Boolean = false,
        override val transition: String? = null,
    ) : Scene() {
        override fun readableText() = listOfNotNull(title, text).joinToString(". ")
    }

    @Serializable
    @SerialName("code_type")
    data class CodeType(
        val language: String = "python",
        val code: String,
        val typing: String = "natural",
        val focusLines: List<Int> = emptyList(),
        val typo: Boolean = false,
        val caption: String? = null,
        override val durationMs: Long? = null,
        override val say: Boolean = false,
        override val sfx: String? = null,
        override val spoken: String? = null,
        override val speakCode: Boolean = false,
        override val transition: String? = null,
    ) : Scene() {
        override fun readableText() = caption ?: ""
    }

    @Serializable
    @SerialName("run")
    data class Run(
        val language: String = "python",
        val exec: String = "simulated",
        val code: String? = null,
        val trace: List<TraceStep> = emptyList(),
        val output: String = "",
        val error: String? = null,
        val caption: String? = null,
        override val durationMs: Long? = null,
        override val say: Boolean = false,
        override val sfx: String? = null,
        override val spoken: String? = null,
        override val speakCode: Boolean = false,
        override val transition: String? = null,
    ) : Scene() {
        override fun readableText() = caption ?: ""
    }

    @Serializable
    @SerialName("engage_moment")
    data class EngageMoment(
        val from: String = "python",
        val text: String,
        val actions: List<String> = listOf("like", "follow", "share"),
        val bridge: String = "حسنًا يا أحمد، نكمل!",
        override val durationMs: Long? = null,
        override val say: Boolean = false,
        override val sfx: String? = null,
        override val spoken: String? = null,
        override val speakCode: Boolean = false,
        override val transition: String? = null,
    ) : Scene() {
        override fun readableText() = "$text. $bridge"
    }

    @Serializable
    @SerialName("reveal")
    data class Reveal(
        val answer: String,
        val text: String,
        val from: String? = null,
        override val durationMs: Long? = null,
        override val say: Boolean = false,
        override val sfx: String? = null,
        override val spoken: String? = null,
        override val speakCode: Boolean = false,
        override val transition: String? = null,
    ) : Scene() {
        override fun readableText() = text
    }

    @Serializable
    @SerialName("common_mistake")
    data class CommonMistake(
        val language: String = "python",
        val code: String,
        val error: String,
        val explain: String,
        val fix: String,
        val fixOutput: String? = null,
        val libraryId: String? = null,
        val from: String? = null,
        override val durationMs: Long? = null,
        override val say: Boolean = false,
        override val sfx: String? = null,
        override val spoken: String? = null,
        override val speakCode: Boolean = false,
        override val transition: String? = null,
    ) : Scene() {
        override fun readableText() = explain
    }

    @Serializable
    @SerialName("key_sentence")
    data class KeySentence(
        val text: String,
        override val durationMs: Long? = null,
        override val say: Boolean = false,
        override val sfx: String? = null,
        override val spoken: String? = null,
        override val speakCode: Boolean = false,
        override val transition: String? = null,
    ) : Scene() {
        override fun readableText() = text
    }

    @Serializable
    @SerialName("teaser")
    data class Teaser(
        val text: String,
        val challenge: String? = null,
        override val durationMs: Long? = null,
        override val say: Boolean = false,
        override val sfx: String? = null,
        override val spoken: String? = null,
        override val speakCode: Boolean = false,
        override val transition: String? = null,
    ) : Scene() {
        override fun readableText() = text
    }

    companion object {
        const val HOOK_QUESTION = "ماذا تتوقع أن يحدث؟"
    }
}

/** Stable scene type ids, in the order used by the schema and docs. */
val SCENE_TYPES = listOf(
    "hook_predict", "recap", "chat", "analogy", "code_type", "run",
    "engage_moment", "reveal", "common_mistake", "key_sentence", "teaser",
)

fun Scene.typeId(): String = when (this) {
    is Scene.HookPredict -> "hook_predict"
    is Scene.Recap -> "recap"
    is Scene.Chat -> "chat"
    is Scene.Analogy -> "analogy"
    is Scene.CodeType -> "code_type"
    is Scene.Run -> "run"
    is Scene.EngageMoment -> "engage_moment"
    is Scene.Reveal -> "reveal"
    is Scene.CommonMistake -> "common_mistake"
    is Scene.KeySentence -> "key_sentence"
    is Scene.Teaser -> "teaser"
}

/** Who speaks a scene (cast id), when it is a person. */
fun Scene.speaker(scenario: Scenario): String? = when (this) {
    is Scene.Chat -> from
    is Scene.EngageMoment -> from
    is Scene.Analogy -> from ?: scenario.firstTeacherId()
    is Scene.Reveal -> from ?: scenario.firstTeacherId()
    is Scene.CommonMistake -> from ?: scenario.firstTeacherId()
    else -> scenario.firstTeacherId()
}

fun Scenario.firstTeacherId(): String? = cast.firstOrNull { it.role == "teacher" }?.id
fun Scenario.castById(id: String?): CastMember? = cast.firstOrNull { it.id == id }
