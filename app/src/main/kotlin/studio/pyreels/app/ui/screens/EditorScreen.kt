package studio.pyreels.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import studio.pyreels.app.data.ProjectRepository
import studio.pyreels.app.data.brief
import studio.pyreels.app.data.scenario
import studio.pyreels.app.engine.PreparedVideo
import studio.pyreels.app.engine.VideoEngine
import studio.pyreels.app.ui.Navigator
import studio.pyreels.app.ui.Route
import studio.pyreels.app.ui.components.CalmCard
import studio.pyreels.app.ui.components.CalmTopBar
import studio.pyreels.app.ui.components.Muted
import studio.pyreels.app.ui.components.ProblemRow
import studio.pyreels.app.ui.components.VideoPreview
import studio.pyreels.app.ui.components.fmt
import studio.pyreels.app.ui.theme.LocalUi
import studio.pyreels.core.brief.Brief
import studio.pyreels.core.content.ContentRepository
import studio.pyreels.core.model.Limits
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.Scene
import studio.pyreels.core.model.VarBox
import studio.pyreels.core.model.typeId
import studio.pyreels.core.quality.QualityChecker
import studio.pyreels.core.quality.QualityReport
import studio.pyreels.core.templates.Templates
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val engine: VideoEngine,
) : ViewModel() {
    val scenario = MutableStateFlow<Scenario?>(null)
    val video = MutableStateFlow<PreparedVideo?>(null)
    val canUndo = MutableStateFlow(false)
    val canRedo = MutableStateFlow(false)
    private val undo = ArrayDeque<Scenario>()
    private val redo = ArrayDeque<Scenario>()
    private var projectId = -1L
    private var brief: Brief? = null
    private var saveJob: Job? = null

    fun load(id: Long) {
        if (projectId == id) return
        projectId = id
        viewModelScope.launch {
            val p = projects.get(id) ?: return@launch
            brief = p.brief()
            p.scenario()?.let { set(it, record = false) }
        }
    }

    fun edit(f: (Scenario) -> Scenario) {
        val cur = scenario.value ?: return
        val next = f(cur)
        if (next != cur) set(next, record = true)
    }

    fun editScene(index: Int, s: Scene) = edit { sc -> sc.copy(scenes = sc.scenes.toMutableList().also { it[index] = s }) }

    private fun set(s: Scenario, record: Boolean) {
        scenario.value?.let { if (record) { undo.addLast(it); if (undo.size > 100) undo.removeFirst(); redo.clear() } }
        scenario.value = s
        video.value = engine.quick(s)
        canUndo.value = undo.isNotEmpty(); canRedo.value = redo.isNotEmpty()
        if (record) autosave()
    }

    fun undo() { val prev = undo.removeLastOrNull() ?: return; scenario.value?.let { redo.addLast(it) }; scenario.value = prev; video.value = engine.quick(prev); canUndo.value = undo.isNotEmpty(); canRedo.value = true; autosave() }
    fun redo() { val nx = redo.removeLastOrNull() ?: return; scenario.value?.let { undo.addLast(it) }; scenario.value = nx; video.value = engine.quick(nx); canRedo.value = redo.isNotEmpty(); canUndo.value = true; autosave() }

    private fun autosave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(700); scenario.value?.let { projects.save(projectId, it) } }
    }

    fun move(from: Int, to: Int) = edit { s ->
        if (to !in s.scenes.indices || from !in s.scenes.indices) s
        else s.copy(scenes = s.scenes.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun delete(i: Int) = edit { s -> s.copy(scenes = s.scenes.toMutableList().apply { removeAt(i) }) }

    fun add(type: String) = edit { s ->
        val scene: Scene = when (type) {
            "chat" -> Scene.Chat("ahmed", "سؤال جديد؟")
            "analogy" -> Scene.Analogy(title = "تشبيه جديد", items = listOf(VarBox("x", "5")))
            "code_type" -> Scene.CodeType(code = "print(\"مرحبا\")")
            "run" -> Scene.Run(exec = "real", output = "")
            "common_mistake" -> ContentRepository.errors.errors.first().let { Scene.CommonMistake(code = it.snippet, error = it.message, explain = it.explain, fix = it.fix, fixOutput = it.fixOutput, libraryId = it.id) }
            "recap" -> Scene.Recap("تذكير بالحلقة الماضية")
            "key_sentence" -> Scene.KeySentence("قاعدة اليوم")
            "engage_moment" -> Scene.EngageMoment(text = "إن أفادك الدرس، تابعنا 💛")
            "reveal" -> Scene.Reveal(answer = "?", text = "هذه الإجابة")
            "teaser" -> Scene.Teaser("في الحلقة القادمة…")
            else -> Scene.HookPredict(code = "print(1 + 1)")
        }
        val list = s.scenes.toMutableList()
        val at = if (list.lastOrNull() is Scene.Teaser && type != "teaser") list.lastIndex else list.size
        list.add(at, scene)
        s.copy(scenes = list)
    }

    fun pasteLesson(text: String) = edit { s ->
        val b = (brief ?: Brief()).copy(lessonTitle = s.meta.title, lessonId = s.meta.lesson.id, teachers = s.cast.filter { it.role == "teacher" }.map { it.id })
        val p = Templates.fromPastedText(text, b)
        s.copy(scenes = p.scenes, cast = p.cast)
    }

    fun quality(): QualityReport? {
        val s = scenario.value ?: return null
        return QualityChecker.check(s, engine.quick(s).timeline)
    }

    fun applyFix(fix: String, scene: Int?) = edit { s ->
        when (fix) {
            "split" -> if (scene != null) QualityChecker.splitBubble(s, scene) else s
            "shiftEngage" -> QualityChecker.shiftEngage(s, engine.quick(s).timeline)
            else -> s
        }
    }
}

private fun sceneLabel(type: String, ar: Boolean): String = when (type) {
    "hook_predict" -> if (ar) "هوك: توقّع قبل أن أشغّل" else "Hook: predict"
    "recap" -> if (ar) "تذكير" else "Recap"
    "chat" -> if (ar) "رسالة" else "Message"
    "analogy" -> if (ar) "تشبيه" else "Analogy"
    "code_type" -> if (ar) "كتابة كود" else "Code typing"
    "run" -> if (ar) "تشغيل وتتبّع" else "Run + trace"
    "engage_moment" -> if (ar) "لحظة التفاعل" else "Engage moment"
    "reveal" -> if (ar) "كشف الإجابة" else "Reveal"
    "common_mistake" -> if (ar) "خطأ شائع" else "Common mistake"
    "key_sentence" -> if (ar) "جملة اليوم" else "Sentence of the day"
    "teaser" -> if (ar) "تشويق" else "Teaser"
    else -> type
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(nav: Navigator, projectId: Long, safeZones: Boolean, onSafeZones: (Boolean) -> Unit, modifier: Modifier, vm: EditorViewModel = hiltViewModel()) {
    val ui = LocalUi.current
    LaunchedEffect(projectId) { vm.load(projectId) }
    val scenario by vm.scenario.collectAsState()
    val video by vm.video.collectAsState()
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()
    var expanded by remember { mutableStateOf<Int?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<QualityReport?>(null) }

    Column(modifier) {
        CalmTopBar(scenario?.meta?.title ?: "", { nav.back() }) {
            IconButton(onClick = vm::undo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, ui.t("تراجع", "Undo")) }
            IconButton(onClick = vm::redo, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, ui.t("إعادة", "Redo")) }
            IconButton(onClick = { paste = "" }) { Icon(Icons.Outlined.ContentPaste, ui.t("لصق نص الدرس", "Paste lesson text")) }
            IconButton(onClick = { report = vm.quality() }) { Icon(Icons.Outlined.Verified, ui.t("فحص الجودة", "Quality check")) }
            IconButton(onClick = { nav.go(Route.Preview(projectId)) }) { Icon(Icons.Outlined.Fullscreen, ui.t("معاينة كاملة", "Full preview")) }
            IconButton(onClick = { nav.go(Route.Export(projectId)) }) { Icon(Icons.Outlined.IosShare, ui.t("تصدير", "Export")) }
        }
        val s = scenario
        val v = video
        if (s == null || v == null) { Text(ui.t("يحمّل…", "Loading…"), Modifier.padding(16.dp)); return@Column }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VideoPreview(v, Modifier.width(170.dp), safeZones = safeZones, onSafeZones = onSafeZones)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DurationBar(v.timeline.totalMs, s.meta.targetSeconds * 1000L)
                Text(ui.t("${s.scenes.size} مشهد · المدة ${fmt(v.timeline.totalMs)}", "${s.scenes.size} scenes · ${fmt(v.timeline.totalMs)}"), style = MaterialTheme.typography.bodySmall, color = Muted)
                if (!v.timeline.fit.reachable) Text(ui.t("⚠ لا يمكن ضبط المدة على الهدف؛ أضف أو احذف مشاهد.", "⚠ Duration cannot reach the target; add or remove scenes."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Text(ui.t("اسحب ↕ لإعادة الترتيب، واسحب البطاقة جانبًا للحذف.", "Drag ↕ to reorder, swipe a card to delete."), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(s.scenes, key = { i, _ -> i }) { i, sc ->
                    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) { vm.delete(i); true } else false
                    })
                    // index keys keep text fields focused while typing; after a delete the slot shows the next scene
                    LaunchedEffect(dismiss.currentValue) { if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) dismiss.reset() }
                    SwipeToDismissBox(state = dismiss, backgroundContent = {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f), RoundedCornerShape(22.dp)).padding(20.dp), contentAlignment = Alignment.CenterStart) {
                            Text(ui.t("حذف (يمكن التراجع)", "Delete (undo available)"))
                        }
                    }) {
                        SceneCard(i, sc, s, expanded == i, { expanded = if (expanded == i) null else i }, vm)
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                FloatingActionButton(onClick = { addMenu = true }) { Icon(Icons.Filled.Add, ui.t("إضافة مشهد", "Add scene")) }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    listOf("chat", "analogy", "code_type", "run", "common_mistake", "reveal", "engage_moment", "key_sentence", "recap", "teaser", "hook_predict").forEach { t ->
                        DropdownMenuItem(text = { Text(sceneLabel(t, !ui.english)) }, onClick = { addMenu = false; vm.add(t) })
                    }
                }
            }
        }
    }

    paste?.let { t ->
        AlertDialog(
            onDismissRequest = { paste = null },
            title = { Text(ui.t("لصق نص الدرس", "Paste lesson text")) },
            text = {
                Column {
                    Text(ui.t("الصق شرحًا عاديًا؛ الأسئلة تصبح رسائل أحمد، والشرح رسائل المعلّم، والكود مشهد كتابة وتشغيل.", "Paste a plain explanation: questions become Ahmed's messages, sentences the teacher's, code becomes typing + run."), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(t, { paste = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp), minLines = 6)
                }
            },
            confirmButton = { TextButton(onClick = { if (t.isNotBlank()) vm.pasteLesson(t); paste = null }) { Text(ui.t("اقتراح المشاهد", "Propose scenes")) } },
            dismissButton = { TextButton(onClick = { paste = null }) { Text(ui.t("إلغاء", "Cancel")) } },
        )
    }

    report?.let { r ->
        ModalBottomSheet(onDismissRequest = { report = null }) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ui.t("درجة الجودة: ${r.score}/100", "Quality score: ${r.score}/100"), style = MaterialTheme.typography.headlineSmall)
                Text(if (r.canExport) ui.t("يمكن التصدير. الملاحظات أدناه نصائح فقط.", "Export allowed. Notes below are tips only.") else ui.t("يوجد أخطاء تمنع التصدير.", "There are blocking errors."), color = Muted)
                r.problems.forEach { p ->
                    ProblemRow(p)
                    if (p.fix == "split" || p.fix == "shiftEngage") {
                        TextButton(onClick = { vm.applyFix(p.fix, p.sceneIndex); report = vm.quality() }) {
                            Text(if (p.fix == "split") ui.t("قسّم الفقاعة", "Split bubble") else ui.t("انقل لحظة التفاعل", "Move engage moment"))
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun DurationBar(totalMs: Long, targetMs: Long) {
    val cap = Limits.HARD_CAP_SECONDS * 1000f
    val primary = MaterialTheme.colorScheme.primary
    val warn = MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        val w = size.width
        drawRoundRect(track, size = Size(w, size.height), cornerRadius = CornerRadius(12f))
        val frac = (totalMs / cap).coerceIn(0f, 1f)
        val color = if (totalMs > Limits.WARN_SECONDS * 1000) warn else primary
        // RTL: fills from the right
        drawRoundRect(color.copy(alpha = 0.7f), topLeft = Offset(w * (1 - frac), 0f), size = Size(w * frac, size.height), cornerRadius = CornerRadius(12f))
        val tx = w * (1 - targetMs / cap)
        drawLine(muted, Offset(tx, 0f), Offset(tx, size.height), strokeWidth = 4f)
    }
}

@Composable
private fun SceneCard(i: Int, sc: Scene, s: Scenario, open: Boolean, toggle: () -> Unit, vm: EditorViewModel) {
    val ui = LocalUi.current
    var drag by remember { mutableFloatStateOf(0f) }
    CalmCard(Modifier.clickable { toggle() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.DragHandle, ui.t("اسحب لإعادة الترتيب", "Drag to reorder"),
                Modifier.size(28.dp).pointerInput(i) {
                    detectDragGesturesAfterLongPress(onDragEnd = { drag = 0f }, onDragCancel = { drag = 0f }) { change, amount ->
                        change.consume()
                        drag += amount.y
                        if (drag > 140f) { vm.move(i, i + 1); drag = 0f }
                        if (drag < -140f) { vm.move(i, i - 1); drag = 0f }
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${i + 1}. ${sceneLabel(sc.typeId(), !ui.english)}" + (if (sc is Scene.Chat) " · ${s.cast.firstOrNull { it.id == sc.from }?.name ?: sc.from}" else ""), style = MaterialTheme.typography.titleSmall)
                Text(summary(sc), style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2)
            }
            IconButton(onClick = { vm.move(i, i - 1) }) { Icon(Icons.Outlined.KeyboardArrowUp, ui.t("لأعلى", "Up")) }
            IconButton(onClick = { vm.move(i, i + 1) }) { Icon(Icons.Outlined.KeyboardArrowDown, ui.t("لأسفل", "Down")) }
        }
        if (open) SceneFields(i, sc, s, vm)
    }
}

private fun summary(sc: Scene): String = when (sc) {
    is Scene.HookPredict -> sc.code.lines().first()
    is Scene.CodeType -> sc.code.lines().first()
    is Scene.Run -> "▶ " + sc.output.lines().firstOrNull().orEmpty()
    is Scene.CommonMistake -> sc.error
    is Scene.Reveal -> "${sc.answer} — ${sc.text}"
    is Scene.Analogy -> sc.title
    else -> sc.readableText()
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false, onChange: (String) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        local, { local = it; onChange(it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        textStyle = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SceneFields(i: Int, sc: Scene, s: Scenario, vm: EditorViewModel) {
    val ui = LocalUi.current
    var libraryOpen by remember { mutableStateOf(false) }
    val set: (Scene) -> Unit = { vm.editScene(i, it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (sc) {
            is Scene.HookPredict -> { Field(ui.t("كود الهوك", "Hook code"), sc.code, true) { set(sc.copy(code = it)) } }
            is Scene.Recap -> Field(ui.t("النص", "Text"), sc.text) { set(sc.copy(text = it)) }
            is Scene.KeySentence -> Field(ui.t("جملة اليوم (≤ 10 كلمات)", "Sentence (≤ 10 words)"), sc.text) { set(sc.copy(text = it)) }
            is Scene.Teaser -> { Field(ui.t("التشويق", "Teaser"), sc.text) { set(sc.copy(text = it)) }; Field(ui.t("التحدّي", "Challenge"), sc.challenge ?: "") { set(sc.copy(challenge = it.ifBlank { null })) } }
            is Scene.Chat -> {
                CastPicker(s, sc.from) { set(sc.copy(from = it)) }
                Field(ui.t("الرسالة", "Message"), sc.text) { set(sc.copy(text = it)) }
                ToggleRow(ui.t("تحدٍّ للتعليقات", "Comment challenge"), sc.challenge) { set(sc.copy(challenge = it)) }
            }
            is Scene.Analogy -> {
                Field(ui.t("عنوان التشبيه", "Analogy title"), sc.title) { set(sc.copy(title = it)) }
                Field(ui.t("الصناديق (اسم=قيمة في كل سطر)", "Boxes (label=value per line)"), sc.items.joinToString("\n") { "${it.label}=${it.value}" }, true) { t ->
                    set(sc.copy(items = t.lines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { VarBox(it[0].trim().take(16), it[1].trim().take(16)) } }.take(4)))
                }
                Field(ui.t("نص", "Text"), sc.text ?: "") { set(sc.copy(text = it.ifBlank { null })) }
            }
            is Scene.CodeType -> {
                Field(ui.t("الكود", "Code"), sc.code, true) { set(sc.copy(code = it)) }
                Field(ui.t("أسطر التركيز (مثل 1,2)", "Focus lines (e.g. 1,2)"), sc.focusLines.joinToString(",")) { t -> set(sc.copy(focusLines = t.split(',').mapNotNull { it.trim().toIntOrNull() }.take(4))) }
                ToggleRow(ui.t("خطأ كتابة واقعي ثم تصحيح", "Realistic typo then fix"), sc.typo) { set(sc.copy(typo = it)) }
            }
            is Scene.Run -> {
                ToggleRow(ui.t("تنفيذ حقيقي (بايثون)", "Real execution (Python)"), sc.exec == "real") { set(sc.copy(exec = if (it) "real" else "simulated")) }
                Field(ui.t("المخرجات المتوقعة", "Expected output"), sc.output, true) { set(sc.copy(output = it)) }
                Field(ui.t("خطأ (اختياري)", "Error (optional)"), sc.error ?: "", true) { set(sc.copy(error = it.ifBlank { null })) }
                if (sc.trace.isEmpty()) TextButton(onClick = {
                    val code = sc.code ?: s.scenes.take(i).filterIsInstance<Scene.CodeType>().lastOrNull()?.code ?: ""
                    set(sc.copy(trace = Templates.simpleTrace(code)))
                }) { Text(ui.t("توليد تتبّع بسيط", "Generate a simple trace")) }
            }
            is Scene.EngageMoment -> {
                CastPicker(s, sc.from) { set(sc.copy(from = it)) }
                Field(ui.t("الرسالة الدافئة", "Warm message"), sc.text) { set(sc.copy(text = it)) }
                Field(ui.t("جملة العودة", "Bridge line"), sc.bridge) { set(sc.copy(bridge = it)) }
            }
            is Scene.Reveal -> { Field(ui.t("الإجابة", "Answer"), sc.answer) { set(sc.copy(answer = it)) }; Field(ui.t("الشرح", "Explanation"), sc.text) { set(sc.copy(text = it)) } }
            is Scene.CommonMistake -> {
                OutlinedButton(onClick = { libraryOpen = true }) { Text(ui.t("اختر من مكتبة الأخطاء", "Pick from errors library")) }
                Field(ui.t("الكود الخاطئ", "Faulty code"), sc.code, true) { set(sc.copy(code = it)) }
                Field(ui.t("رسالة الخطأ", "Error message"), sc.error, true) { set(sc.copy(error = it)) }
                Field(ui.t("شرح هادئ", "Calm explanation"), sc.explain) { set(sc.copy(explain = it)) }
                Field(ui.t("الإصلاح", "Fix"), sc.fix, true) { set(sc.copy(fix = it)) }
            }
        }
        ToggleRow(ui.t("قراءة صوتية", "Read aloud"), sc.say) { v ->
            set(when (sc) {
                is Scene.HookPredict -> sc.copy(say = v); is Scene.Recap -> sc.copy(say = v); is Scene.Chat -> sc.copy(say = v)
                is Scene.Analogy -> sc.copy(say = v); is Scene.CodeType -> sc.copy(say = v); is Scene.Run -> sc.copy(say = v)
                is Scene.EngageMoment -> sc.copy(say = v); is Scene.Reveal -> sc.copy(say = v); is Scene.CommonMistake -> sc.copy(say = v)
                is Scene.KeySentence -> sc.copy(say = v); is Scene.Teaser -> sc.copy(say = v)
            })
        }
    }
    if (libraryOpen && sc is Scene.CommonMistake) {
        val list = ContentRepository.errors.relevant(s.meta.lesson.id, ContentRepository.curriculum, max = 60).ifEmpty { ContentRepository.errors.errors }
        AlertDialog(
            onDismissRequest = { libraryOpen = false },
            title = { Text(ui.t("مكتبة الأخطاء الشائعة", "Common errors library")) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(list) { _, e ->
                        Column(Modifier.fillMaxWidth().clickable {
                            set(sc.copy(code = e.snippet, error = e.message, explain = e.explain, fix = e.fix, fixOutput = e.fixOutput, libraryId = e.id)); libraryOpen = false
                        }.padding(vertical = 8.dp)) {
                            Text(e.message, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            Text(e.explain, style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { libraryOpen = false }) { Text(ui.t("إغلاق", "Close")) } },
        )
    }
}

@Composable
private fun CastPicker(s: Scenario, selected: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        s.cast.forEach { c ->
            val sel = c.id == selected
            Button(onClick = { onPick(c.id) }, enabled = !sel) { Text(c.name) }
        }
    }
}

@Suppress("unused")
private fun jsonValue(v: String) = v.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(v)
