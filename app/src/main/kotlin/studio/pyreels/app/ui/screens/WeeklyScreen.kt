package studio.pyreels.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import studio.pyreels.app.data.PresetRepository
import studio.pyreels.app.data.SeriesRepository
import studio.pyreels.app.ui.Navigator
import studio.pyreels.app.ui.Route
import studio.pyreels.app.ui.components.CalmTopBar
import studio.pyreels.app.ui.components.Muted
import studio.pyreels.app.ui.theme.LocalUi
import studio.pyreels.core.brief.Brief
import studio.pyreels.core.brief.EpisodeRecord
import studio.pyreels.core.content.ContentRepository
import studio.pyreels.core.prompt.PromptBuilder
import javax.inject.Inject

@HiltViewModel
class WeeklyViewModel @Inject constructor(private val series: SeriesRepository, private val presets: PresetRepository) : ViewModel() {
    val history = series.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<EpisodeRecord>())
    suspend fun prompt(ids: List<String>): String {
        val lessons = ids.mapNotNull { ContentRepository.curriculum.byId(it) }
        val brief = presets.presets.first().firstOrNull()?.brief ?: Brief()
        return PromptBuilder.build(PromptBuilder.Input(brief.copy(lessonId = lessons.firstOrNull()?.id, lessonTitle = lessons.firstOrNull()?.title ?: ""), series.all(), lessons))
    }
}

@Composable
fun WeeklyScreen(nav: Navigator, modifier: Modifier, vm: WeeklyViewModel = hiltViewModel()) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by vm.history.collectAsState()
    val done = history.map { it.lessonId }.toSet()
    val selected = remember { mutableStateListOf<String>() }
    var pending by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<Uri?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val t = pending
        if (uri != null && t != null) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(t.toByteArray()) }; saved = uri }
    }
    Column(modifier) {
        CalmTopBar(ui.t("الخطة الأسبوعية", "Weekly plan"), { nav.back() })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ui.t("اختر حتى 7 دروس بالترتيب (${selected.size}/7). ستحصل على برومبت واحد يطلب 7 حلقات مترابطة.", "Pick up to 7 lessons in order (${selected.size}/7). You get one prompt asking for 7 linked episodes."), color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selected.clear(); selected += ContentRepository.curriculum.proposeWeek(done).map { it.id } }) { Text(ui.t("اقترح 7 دروس", "Propose next 7")) }
                Button(enabled = selected.isNotEmpty(), onClick = {
                    scope.launch { pending = vm.prompt(selected.toList()); create.launch("prompt_weekly_${selected.first()}_${java.time.LocalDate.now()}.md") }
                }) { Text(ui.t("إنشاء برومبت الأسبوع", "Build weekly prompt")) }
            }
            TextButton(onClick = { nav.go(Route.Import()) }) { Text(ui.t("استيراد الدفعة (7 سيناريوهات)", "Import the batch (7 scenarios)")) }
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            items(ContentRepository.curriculum.lessons) { l ->
                val idx = selected.indexOf(l.id)
                Row(Modifier.fillMaxWidth().clickable {
                    if (idx >= 0) selected.remove(l.id) else if (selected.size < 7) selected.add(l.id)
                }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = idx >= 0, onCheckedChange = null)
                    Text((if (idx >= 0) "${idx + 1}) " else "") + "${l.order}. ${l.title}" + if (l.id in done) " ✓" else "", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    saved?.let { uri ->
        AlertDialog(onDismissRequest = { saved = null }, title = { Text(ui.t("تم حفظ برومبت الأسبوع ✓", "Weekly prompt saved ✓")) },
            confirmButton = { Row {
                TextButton(onClick = { openUri(context, uri, "text/markdown") }) { Text(ui.t("فتح", "Open")) }
                TextButton(onClick = { shareUri(context, uri, "text/markdown") }) { Text(ui.t("مشاركة", "Share")) }
                TextButton(onClick = { copyText(context, pending ?: ""); saved = null }) { Text(ui.t("نسخ النص", "Copy text")) }
            } })
    }
}
