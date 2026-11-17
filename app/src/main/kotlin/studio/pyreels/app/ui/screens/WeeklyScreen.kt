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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateMapOf
import studio.pyreels.app.data.TeacherStore
import studio.pyreels.app.ui.components.TeacherDialog
import studio.pyreels.core.model.Limits
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
import studio.pyreels.core.prompt.PromptBuilder
import javax.inject.Inject

@HiltViewModel
class WeeklyViewModel @Inject constructor(
    private val series: SeriesRepository,
    private val presets: PresetRepository,
    val teacherStore: TeacherStore,
) : ViewModel() {
    val history = series.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<EpisodeRecord>())
    val teachers = teacherStore.teachers

    suspend fun prompt(topics: List<String>, teacherIds: List<String>): String {
        val all = teacherStore.teachers.value.associateBy { it.id }
        val base = presets.presets.first().firstOrNull()?.brief ?: Brief()
        val brief = base.copy(
            teachers = teacherIds, lessonTitle = topics.first(), lessonId = null,
            teacherNames = teacherIds.mapNotNull { id -> all[id]?.let { id to it.name } }.toMap(),
            teacherNotes = teacherIds.mapNotNull { id -> all[id]?.takeIf { it.note.isNotBlank() }?.let { id to it.note } }.toMap(),
        )
        return PromptBuilder.build(PromptBuilder.Input(brief.normalized(), series.all(), topics))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyScreen(nav: Navigator, modifier: Modifier, vm: WeeklyViewModel = hiltViewModel()) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val teachers by vm.teachers.collectAsState()
    val topics = remember { mutableStateMapOf<Int, String>() }
    val chosen = remember { mutableStateListOf<String>() }
    var addingTeacher by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<Uri?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val t = pending
        if (uri != null && t != null) { runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(t.toByteArray()) } }; saved = uri }
    }
    val filled = (0 until 7).mapNotNull { topics[it]?.trim()?.takeIf { t -> t.isNotEmpty() } }
    Column(modifier) {
        CalmTopBar(ui.t("الخطة الأسبوعية", "Weekly plan"), { nav.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(ui.t("اكتب مواضيع حلقات الأسبوع بالترتيب (حتى 7). ستحصل على برومبت واحد يطلب حلقات مترابطة.", "Write the week's episode topics in order (up to 7). You get one prompt asking for linked episodes."), color = Muted)
            Text(ui.t("المعلّمون (لغاتك)", "Teachers (your languages)"), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                teachers.forEach { t ->
                    FilterChip(selected = t.id in chosen, onClick = { if (t.id in chosen) chosen.remove(t.id) else if (chosen.size < Limits.MAX_TEACHERS) chosen.add(t.id) }, label = { Text(t.name) })
                }
                OutlinedButton(onClick = { addingTeacher = true }) { Text(ui.t("+ إضافة لغة", "+ Add a language")) }
            }
            for (i in 0 until 7) {
                OutlinedTextField(topics[i] ?: "", { topics[i] = it.take(80) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(ui.t("الحلقة ${i + 1}", "Episode ${i + 1}")) })
            }
            Button(enabled = filled.isNotEmpty() && chosen.isNotEmpty(), onClick = {
                scope.launch { pending = vm.prompt(filled, chosen.toList()); create.launch("prompt_weekly_${java.time.LocalDate.now()}.md") }
            }, modifier = Modifier.fillMaxWidth()) { Text(ui.t("إنشاء برومبت الأسبوع", "Build weekly prompt")) }
            if (filled.isEmpty() || chosen.isEmpty()) Text(ui.t("اكتب موضوعًا واحدًا على الأقل واختر معلّمًا.", "Write at least one topic and pick a teacher."), color = Muted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { nav.go(Route.Import()) }) { Text(ui.t("استيراد الدفعة (عدة سيناريوهات)", "Import the batch (several scenarios)")) }
        }
    }
    if (addingTeacher) TeacherDialog(vm.teacherStore, null, onDismiss = { addingTeacher = false }, onSaved = { t -> if (t.id !in chosen && chosen.size < Limits.MAX_TEACHERS) chosen.add(t.id) })
    saved?.let { uri ->
        AlertDialog(onDismissRequest = { saved = null }, title = { Text(ui.t("تم حفظ برومبت الأسبوع ✓", "Weekly prompt saved ✓")) },
            confirmButton = { Row {
                TextButton(onClick = { openUri(context, uri, "text/markdown") }) { Text(ui.t("فتح", "Open")) }
                TextButton(onClick = { shareUri(context, uri, "text/markdown") }) { Text(ui.t("مشاركة", "Share")) }
                TextButton(onClick = { copyText(context, pending ?: ""); saved = null }) { Text(ui.t("نسخ النص", "Copy text")) }
            } })
    }
}
