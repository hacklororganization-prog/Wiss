package studio.pyreels.core.model

/**
 * Design tokens shared by the Compose UI and the video renderer (ARGB). Two calm themes only:
 * soft blue-gray dark (never pure black) and a warm light one. Text is warm off-white / ink, never
 * pure white / black. One accent per teacher (see [Personas]) and ONE highlight colour for code.
 */
data class Palette(
    val id: String,
    val background: Long,
    val backgroundTop: Long,
    val surface: Long,
    val surfaceHigh: Long,
    val text: Long,
    val textMuted: Long,
    val bubbleIn: Long,
    val bubbleOut: Long,
    val bubbleOutText: Long,
    val header: Long,
    val codeBg: Long,
    val codeText: Long,
    val codeKeyword: Long,
    val codeString: Long,
    val codeNumber: Long,
    val codeComment: Long,
    val codeFunction: Long,
    val highlight: Long,
    val lineNumber: Long,
    val terminalBg: Long,
    val terminalText: Long,
    val terminalError: Long,
    val terminalOk: Long,
    val focus: Long,
    val tickRead: Long,
    val safeZone: Long,
    val isDark: Boolean,
)

object Tokens {
    val DARK = Palette(
        id = Themes.DARK,
        background = 0xFF1B222C, backgroundTop = 0xFF222B37, surface = 0xFF242E3A, surfaceHigh = 0xFF2C3846,
        text = 0xFFEDE7DC, textMuted = 0xFFA9B2BD,
        bubbleIn = 0xFF29333F, bubbleOut = 0xFF234B45, bubbleOutText = 0xFFEAF2EC, header = 0xFF202934,
        codeBg = 0xFF1F2630, codeText = 0xFFE3DED4, codeKeyword = 0xFFC9A7E8, codeString = 0xFFA8D5A2,
        codeNumber = 0xFFF0B98D, codeComment = 0xFF7D8894, codeFunction = 0xFF8FC1E8,
        highlight = 0xFF3A4A3A, lineNumber = 0xFF5C6773,
        terminalBg = 0xFF161C24, terminalText = 0xFFD6D9D2, terminalError = 0xFFE8A87C, terminalOk = 0xFF9FD3A6,
        focus = 0xFFE8C872, tickRead = 0xFF8FC9E8, safeZone = 0x33E8A87C, isDark = true,
    )
    val LIGHT = Palette(
        id = Themes.LIGHT,
        background = 0xFFF3EEE6, backgroundTop = 0xFFF8F4EE, surface = 0xFFFFFBF5, surfaceHigh = 0xFFEDE6DA,
        text = 0xFF2C2A26, textMuted = 0xFF6E6A62,
        bubbleIn = 0xFFFFFBF5, bubbleOut = 0xFFDCEBDD, bubbleOutText = 0xFF23302A, header = 0xFFE9E2D6,
        codeBg = 0xFF2A313B, codeText = 0xFFE7E2D8, codeKeyword = 0xFFC9A7E8, codeString = 0xFFA8D5A2,
        codeNumber = 0xFFF0B98D, codeComment = 0xFF8A949E, codeFunction = 0xFF8FC1E8,
        highlight = 0xFF3D4D3E, lineNumber = 0xFF6A7581,
        terminalBg = 0xFF22282F, terminalText = 0xFFDCDDD6, terminalError = 0xFFE8A87C, terminalOk = 0xFF9FD3A6,
        focus = 0xFFC08A1E, tickRead = 0xFF3A8FC0, safeZone = 0x33C0601E, isDark = false,
    )

    fun palette(theme: String?) = if (theme == Themes.LIGHT) LIGHT else DARK

    fun accent(persona: Persona, palette: Palette) = if (palette.isDark) persona.accentDark else persona.accentLight

    /** Typography sizes in video pixels (1080×1920 frame), designed for phone viewing. */
    object Type {
        const val BUBBLE = 46f
        const val NAME = 34f
        const val TIME = 26f
        const val CODE = 40f
        const val TERMINAL = 36f
        const val CARD_LABEL = 40f
        const val CARD_TEXT = 70f
        const val KEY_SENTENCE = 82f
        const val HEADER = 42f
        const val CHIP = 34f
        const val CAPTION = 50f
    }
}
