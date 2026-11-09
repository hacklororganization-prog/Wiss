package studio.pyreels.core.timeline

/**
 * Pure render model of a single frame. Produced by [Timeline.frameAt] and drawn by the platform
 * renderer. Containing only data makes preview and export identical and the timeline testable.
 */
enum class Stage { HOOK, CARD, CHAT, CODE }

class Frame(
    val tMs: Long,
    val totalMs: Long,
    val sceneIndex: Int,
    val sceneType: String,
    val layers: List<Layer>,
    val caption: CaptionView?,
    val engageChip: EngageChipView?,
    val title: String,
    val episode: Int?,
    val watermark: Boolean,
    val theme: String,
) {
    val progress: Float get() = if (totalMs <= 0) 0f else (tMs.toFloat() / totalMs).coerceIn(0f, 1f)
}

/** One stage drawn with an alpha and an offset (for fade / soft slide / gentle zoom transitions). */
class Layer(
    val stage: Stage,
    val alpha: Float,
    val offsetY: Float,
    val scale: Float,
    val chat: ChatView? = null,
    val code: CodeView? = null,
    val hook: HookView? = null,
    val card: CardView? = null,
)

// ---------------- Chat ----------------

enum class MessageKind { TEXT, ANALOGY, REVEAL, ENGAGE }

class ChatMessageView(
    val id: Int,
    val from: String,
    val outgoing: Boolean,
    val kind: MessageKind,
    val text: String,
    val code: String?,
    val appear: Float,
    val timeLabel: String,
    val ticksRead: Boolean,
    val reactions: List<String>,
    val reactionPulse: Float,
    val analogyTitle: String? = null,
    val analogyItems: List<Pair<String, String>> = emptyList(),
    val analogyVisible: Int = 0,
    val revealAnswer: String? = null,
    val revealCode: String? = null,
    val challenge: Boolean = false,
    /** Word index highlighted by voice karaoke (-1 = none). */
    val karaokeWord: Int = -1,
)

class ChatView(
    val groupName: String,
    val members: List<String>,
    val messages: List<ChatMessageView>,
    val typingFrom: String?,
    val typingPhase: Float,
    /** 0..1 eased scroll of the newest message (renderer lifts the list by its height × (1-scroll)). */
    val scroll: Float,
    /** Ahmed's message being typed in the input bar (null when the bar is empty). */
    val composeText: String? = null,
    val composeChars: Int = 0,
)

// ---------------- Code ----------------

enum class TermKind { CMD, OUT, ERR, OK }

class TermLine(val text: String, val visibleChars: Int, val kind: TermKind)

class VarView(val name: String, val value: String, val previous: String?, val change: Float)

class NoteView(val from: String, val text: String, val appear: Float, val karaokeWord: Int = -1)

class CodeView(
    val language: String,
    val fileName: String,
    val text: String,
    val cursorVisible: Boolean,
    val cursorIndex: Int,
    /** 1-based line executing (run) or -1. */
    val activeLine: Int,
    val activeLineMix: Float,
    val focusLines: List<Int>,
    val dim: Float,
    val zoom: Float,
    val terminal: List<TermLine>,
    val terminalVisible: Float,
    val vars: List<VarView>,
    val iteration: Int?,
    val stack: List<String>,
    val note: NoteView?,
    val running: Boolean,
    val errorLine: Int = -1,
    /** On-screen keyboard slide (0 hidden … 1 shown). */
    val keyboard: Float = 0f,
    /** Key pressed right now (for the keyboard highlight), '\b' = backspace. */
    val pressedKey: Char? = null,
    /** Soft press pulse of the ▶ run button. */
    val runPress: Float = 0f,
    val modified: Boolean = false,
    /** Line executed before [activeLine]: the highlight band glides from it. */
    val previousLine: Int = -1,
)

// ---------------- Hook / cards ----------------

class HookView(
    val code: String,
    val language: String,
    val question: String,
    val enter: Float,
    val pulse: Float,
    val questionAppear: Float,
    /** Gentle countdown ring 0..1 (1 = full). */
    val ring: Float,
    /** 0..1 staggered reveal of the code lines. */
    val lineReveal: Float = 1f,
)

enum class CardKind { RECAP, KEY_SENTENCE, TEASER }

class CardView(
    val kind: CardKind,
    val label: String,
    val text: String,
    val sub: String?,
    val appear: Float,
    val zoom: Float,
    /** 0..1 reveal of the body after the label (line by line). */
    val reveal: Float = 1f,
)

class EngageChipView(val actions: List<String>, val appear: Float, val heartPulse: Float)

class CaptionView(val words: List<String>, val activeWord: Int, val appear: Float, val rtl: Boolean)
