package studio.pyreels.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import studio.pyreels.app.R
import studio.pyreels.core.model.Palette
import studio.pyreels.core.model.Tokens

val Cairo = FontFamily(
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_semibold, FontWeight.SemiBold),
    Font(R.font.cairo_bold, FontWeight.Bold),
)
val Mono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal), Font(R.font.jetbrains_mono_bold, FontWeight.Bold))

/** Bilingual UI text: Arabic first, English secondary. */
class Ui(val english: Boolean) {
    fun t(ar: String, en: String) = if (english) en else ar
}

val LocalUi = compositionLocalOf { Ui(false) }
val LocalPalette = compositionLocalOf { Tokens.DARK }

fun Long.color() = Color(this.toInt())

@Composable
fun PyReelsTheme(dark: Boolean, english: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val p: Palette = if (dark) Tokens.DARK else Tokens.LIGHT
    val scheme = if (dark) darkColorScheme(
        primary = p.focus.color(), onPrimary = p.background.color(), background = p.background.color(), onBackground = p.text.color(),
        surface = p.surface.color(), onSurface = p.text.color(), surfaceVariant = p.surfaceHigh.color(), onSurfaceVariant = p.textMuted.color(),
        secondary = p.tickRead.color(), error = p.terminalError.color(), outline = p.lineNumber.color(),
        surfaceContainer = p.surface.color(), surfaceContainerHigh = p.surfaceHigh.color(), surfaceContainerLow = p.surface.color(),
    ) else lightColorScheme(
        primary = p.focus.color(), onPrimary = p.surface.color(), background = p.background.color(), onBackground = p.text.color(),
        surface = p.surface.color(), onSurface = p.text.color(), surfaceVariant = p.surfaceHigh.color(), onSurfaceVariant = p.textMuted.color(),
        secondary = p.tickRead.color(), error = p.terminalError.color(), outline = p.lineNumber.color(),
        surfaceContainer = p.surface.color(), surfaceContainerHigh = p.surfaceHigh.color(), surfaceContainerLow = p.surface.color(),
    )
    val s = fontScale
    val base = TextStyle(fontFamily = Cairo)
    val typography = Typography(
        headlineSmall = base.copy(fontSize = (24 * s).sp, fontWeight = FontWeight.Bold),
        titleLarge = base.copy(fontSize = (21 * s).sp, fontWeight = FontWeight.Bold),
        titleMedium = base.copy(fontSize = (17 * s).sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.copy(fontSize = (15 * s).sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.copy(fontSize = (16 * s).sp),
        bodyMedium = base.copy(fontSize = (15 * s).sp),
        bodySmall = base.copy(fontSize = (13 * s).sp),
        labelLarge = base.copy(fontSize = (15 * s).sp, fontWeight = FontWeight.SemiBold),
        labelMedium = base.copy(fontSize = (13 * s).sp, fontWeight = FontWeight.SemiBold),
        labelSmall = base.copy(fontSize = (12 * s).sp),
    )
    CompositionLocalProvider(LocalUi provides Ui(english), LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
