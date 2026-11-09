package studio.pyreels.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import studio.pyreels.app.data.PresetRepository
import studio.pyreels.app.data.ProjectRepository
import studio.pyreels.app.data.SeriesRepository
import studio.pyreels.app.ui.Navigator
import studio.pyreels.app.ui.Route
import studio.pyreels.app.ui.components.CalmCard
import studio.pyreels.app.ui.components.CalmTopBar
import studio.pyreels.app.ui.components.Muted
import studio.pyreels.app.ui.components.SectionTitle
import studio.pyreels.app.ui.theme.LocalUi
import studio.pyreels.core.brief.Brief
import studio.pyreels.core.brief.StylePreset
import studio.pyreels.core.content.ContentRepository
import studio.pyreels.core.model.Limits
import studio.pyreels.core.model.Personas
import studio.pyreels.core.prompt.PromptBuilder
import studio.pyreels.core.templates.Templates
import javax.inject.Inject

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val series: SeriesRepository,
    private val presets: PresetRepository,
) : ViewModel() {
    val brief = MutableStateFlow(Brief())
    val presetList = presets.presets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var initialised = false

    fun init(lessonId: String?, presetId: String?) {
        if (initialised) return
        initialised = true
        viewModelScope.launch {
            val p = presetId?.let { id -> presets.presets.first().firstOrNull { it.id == id } }
            var b = p?.brief ?: Brief()
            val history = series.all()
            val lesson = ContentRepository.curriculum.byId(lessonId) ?: ContentRepository.curriculum.nextFor(history.map { it.lessonId }.toSet())
            if (lesson != null) b = b.copy(lessonId = lesson.id, lessonTitle = lesson.title)
            brief.value = b
        }
    }

    fun update(f: (Brief) -> Brief) { brief.value = f(brief.value).normalized() }

    suspend fun prompt(): String = PromptBuilder.build(PromptBuilder.Input(brief.value, series.all()))

    fun draft(done: (Long) -> Unit) = viewModelScope.launch {
        val b = brief.value
        val lesson = ContentRepository.curriculum.byId(b.lessonId) ?: ContentRepository.curriculum.lessons.first()
        done(projects.create(Templates.draft(lesson, b, series.all()), b))
    }

    fun savePreset(name: String) = viewModelScope.launch {
        presets.save(StylePreset("custom-" + System.currentTimeMillis(), name, brief.value.copy(lessonId = null, lessonTitle = "")))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardScreen(nav: Navigator, route: Route.Wizard, modifier: Modifier, vm: WizardViewModel = hiltViewModel()) {
    val ui = LocalUi.current
    val context = LocalContext.current
    LaunchedEffect(route) { vm.init(route.lessonId, route.presetId) }
    val b by vm.brief.collectAsState()
    val presets by vm.presetList.collectAsState()
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<Uri?>(null) }
    var presetName by remember { mutableStateOf<String?>(null) }
    var lessonMenu by remember { mutableStateOf(false) }

    // Storage Access Framework: the user chooses where the prompt file goes. Never a silent write.
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val text = pendingPrompt
        if (uri != null && text != null) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            saved = uri
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(modifier) {
        CalmTopBar(ui.t("إعداد الدرس", "Lesson setup"), { nav.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (presets.isNotEmpty()) {
                SectionTitle(ui.t("إعداد مسبق", "Preset"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p -> FilterChip(selected = false, onClick = { vm.update { p.brief.copy(lessonId = it.lessonId, lessonTitle = it.lessonTitle) } }, label = { Text(p.name) }) }
                }
            }
            CalmCard {
                SectionTitle(ui.t("درس اليوم", "Today's lesson"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { lessonMenu = true }) { Text(ContentRepository.curriculum.byId(b.lessonId)?.let { "${it.order}. ${it.title}" } ?: ui.t("اختر من المسار", "Pick from roadmap")) }
                    DropdownMenu(expanded = lessonMenu, onDismissRequest = { lessonMenu = false }) {
                        ContentRepository.curriculum.lessons.forEach { l ->
                            DropdownMenuItem(text = { Text("${l.order}. ${l.title}") }, onClick = { lessonMenu = false; vm.update { it.copy(lessonId = l.id, lessonTitle = l.title) } })
                        }
                    }
                }
                OutlinedTextField(b.lessonTitle, { v -> vm.update { it.copy(lessonTitle = v, lessonId = if (ContentRepository.curriculum.byId(it.lessonId)?.title == v) it.lessonId else null) } },
                    label = { Text(ui.t("أو اكتب موضوعًا حرًّا", "Or type a free topic")) }, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Brief.LEVELS.forEach { (k, v) -> FilterChip(selected = b.level == k, onClick = { vm.update { it.copy(level = k) } }, label = { Text(v) }) }
                }
            }
            CalmCard {
                SectionTitle(ui.t("المعلّمون (1–4)", "Teachers (1–4)"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Personas.teachers().forEach { p ->
                        val sel = p.id in b.teachers
                        FilterChip(selected = sel, onClick = {
                            vm.update { br -> br.copy(teachers = if (sel) (br.teachers - p.id).ifEmpty { listOf("python") } else (br.teachers + p.id).take(Limits.MAX_TEACHERS)) }
                        }, label = { Text(p.nameAr) })
                    }
                }
                b.teachers.forEach { id -> val p = Personas.forLanguage(id); Text("• ${p.nameAr}: ${p.personality}", color = Muted, style = MaterialTheme.typography.bodySmall) }
            }
            CalmCard {
                SectionTitle(ui.t("المثال", "Example"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = b.exampleMode == "auto", onClick = { vm.update { it.copy(exampleMode = "auto") } }, label = { Text(ui.t("اقتراح تلقائي", "Auto-suggest")) })
                    FilterChip(selected = b.exampleMode == "custom", onClick = { vm.update { it.copy(exampleMode = "custom") } }, label = { Text(ui.t("مثالي الخاص", "My own")) })
                }
                if (b.exampleMode == "custom") OutlinedTextField(b.customExample, { v -> vm.update { it.copy(customExample = v) } }, label = { Text(ui.t("نص أو كود", "Text or code")) },
                    modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), minLines = 3)
                else ContentRepository.curriculum.byId(b.lessonId)?.let { Text(it.example, fontFamily = FontFamily.Monospace, color = Muted) }
            }
            CalmCard {
                SectionTitle(ui.t("طريقة الشرح", "Teaching method"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Brief.METHODS.forEach { (k, v) ->
                        val sel = k in b.methods
                        FilterChip(selected = sel, onClick = { vm.update { it.copy(methods = if (sel) it.methods - k else it.methods + k) } }, label = { Text(v) })
                    }
                }
                SectionTitle(ui.t("النبرة", "Tone"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Brief.TONES.forEach { (k, v) -> FilterChip(selected = b.tone == k, onClick = { vm.update { it.copy(tone = k) } }, label = { Text(v) }) }
                }
                Text(ui.t("مستوى المزاح: ${b.humor}/5", "Humor level: ${b.humor}/5"))
                Slider(b.humor.toFloat(), { v -> vm.update { it.copy(humor = v.toInt()) } }, valueRange = 0f..5f, steps = 4)
                SectionTitle(ui.t("لغة الفيديو", "Video language"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Brief.STYLES.forEach { (k, v) -> FilterChip(selected = b.languageStyle == k, onClick = { vm.update { it.copy(languageStyle = k) } }, label = { Text(v) }) }
                }
            }
            CalmCard {
                ToggleRow(ui.t("إظهار خطأ شائع", "Include a common mistake"), b.includeMistake) { v -> vm.update { it.copy(includeMistake = v) } }
                ToggleRow(ui.t("تحدٍّ في التعليقات", "Comment challenge"), b.includeChallenge) { v -> vm.update { it.copy(includeChallenge = v) } }
                ToggleRow(ui.t("تعليق صوتي", "Voice-over"), b.voiceOver) { v -> vm.update { it.copy(voiceOver = v) } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = b.teaserMode == "auto", onClick = { vm.update { it.copy(teaserMode = "auto") } }, label = { Text(ui.t("تشويق تلقائي من المسار", "Auto teaser")) })
                    FilterChip(selected = b.teaserMode == "custom", onClick = { vm.update { it.copy(teaserMode = "custom") } }, label = { Text(ui.t("تشويق مخصّص", "Custom teaser")) })
                }
                if (b.teaserMode == "custom") OutlinedTextField(b.customTeaser, { v -> vm.update { it.copy(customTeaser = v) } }, modifier = Modifier.fillMaxWidth())
                Text(ui.t("المدة المستهدفة: ${b.targetSeconds} ث", "Target duration: ${b.targetSeconds} s"))
                Slider(b.targetSeconds.toFloat(), { v -> vm.update { it.copy(targetSeconds = (v / 5).toInt() * 5) } }, valueRange = 60f..120f)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = b.theme == "calm-dark", onClick = { vm.update { it.copy(theme = "calm-dark") } }, label = { Text(ui.t("داكن هادئ", "Calm dark")) })
                    FilterChip(selected = b.theme == "calm-light", onClick = { vm.update { it.copy(theme = "calm-light") } }, label = { Text(ui.t("فاتح هادئ", "Calm light")) })
                }
                OutlinedTextField(b.notes, { v -> vm.update { it.copy(notes = v) } }, label = { Text(ui.t("ملاحظات إضافية", "Extra notes")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
            Button(onClick = {
                scope.launch {
                    pendingPrompt = vm.prompt()
                    createDoc.launch(PromptBuilder.fileName(b))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(ui.t("إنشاء البرومبت الاحترافي", "Generate professional prompt")) }
            OutlinedButton(onClick = { nav.go(Route.Import()) }, modifier = Modifier.fillMaxWidth()) { Text(ui.t("استيراد نتيجة الذكاء الاصطناعي", "Import the AI's result")) }
            OutlinedButton(onClick = { vm.draft { nav.replace(Route.Editor(it)) } }, modifier = Modifier.fillMaxWidth()) { Text(ui.t("مسودة فورية من القوالب (بدون ذكاء اصطناعي)", "Instant draft from templates (no AI)")) }
            TextButton(onClick = { presetName = "" }) { Text(ui.t("حفظ كإعداد مسبق", "Save as preset")) }
            Spacer(Modifier.height(32.dp))
        }
    }

    saved?.let { uri ->
        AlertDialog(
            onDismissRequest = { saved = null },
            title = { Text(ui.t("تم حفظ البرومبت ✓", "Prompt saved ✓")) },
            text = { Text(ui.t("الصقه في أي ذكاء اصطناعي، ثم استورد ملف JSON الناتج.", "Paste it into any AI, then import the JSON it returns.")) },
            confirmButton = {
                Row {
                    TextButton(onClick = { openUri(context, uri, "text/markdown") }) { Text(ui.t("فتح", "Open")) }
                    TextButton(onClick = { shareUri(context, uri, "text/markdown") }) { Text(ui.t("مشاركة", "Share")) }
                    TextButton(onClick = { copyText(context, pendingPrompt ?: ""); saved = null }) { Text(ui.t("نسخ النص", "Copy text")) }
                }
            },
        )
    }
    presetName?.let { name ->
        AlertDialog(
            onDismissRequest = { presetName = null },
            title = { Text(ui.t("اسم الإعداد المسبق", "Preset name")) },
            text = { OutlinedTextField(name, { presetName = it }) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) vm.savePreset(name.trim()); presetName = null }) { Text(ui.t("حفظ", "Save")) } },
            dismissButton = { TextButton(onClick = { presetName = null }) { Text(ui.t("إلغاء", "Cancel")) } },
        )
    }
}

@Composable
fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}

fun openUri(context: Context, uri: Uri, mime: String) {
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
    }
}

fun shareUri(context: Context, uri: Uri, mime: String) {
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
    }
}

fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PyReels", text))
}
