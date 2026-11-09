package studio.pyreels.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import studio.pyreels.app.BuildConfig
import studio.pyreels.app.Prefs
import studio.pyreels.app.ui.Navigator
import studio.pyreels.app.ui.components.CalmCard
import studio.pyreels.app.ui.components.CalmTopBar
import studio.pyreels.app.ui.components.Muted
import studio.pyreels.app.ui.theme.LocalUi

@Composable
fun SettingsScreen(nav: Navigator, prefs: Prefs, modifier: Modifier) {
    val ui = LocalUi.current
    val dark by prefs.dark.collectAsState()
    val english by prefs.english.collectAsState()
    val scale by prefs.fontScale.collectAsState()
    Column(modifier) {
        CalmTopBar(ui.t("الإعدادات", "Settings"), { nav.back() })
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CalmCard {
                ToggleRow(ui.t("المظهر الداكن الهادئ", "Calm dark theme"), dark, prefs::setDark)
                ToggleRow("English UI", english, prefs::setEnglish)
                Text(ui.t("حجم الخط: ${"%.0f".format(scale * 100)}٪", "Font size: ${"%.0f".format(scale * 100)}%"))
                Slider(scale, prefs::setFontScale, valueRange = 0.85f..1.4f, steps = 10)
            }
            CalmCard {
                Text(ui.t("الخصوصية", "Privacy"), style = MaterialTheme.typography.titleMedium)
                Text(ui.t("التطبيق يعمل بلا إنترنت تمامًا: بلا تحليلات، بلا شبكة، وبايثون معزول. لا يحتاج صلاحيات تخزين واسعة.", "Fully offline: no analytics, no network, sandboxed Python, no broad storage permission."), color = Muted)
                Text("PyReels Studio ${BuildConfig.VERSION_NAME}", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
