package studio.pyreels.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import studio.pyreels.app.data.AppDatabase
import studio.pyreels.app.data.ExportJobEntity
import studio.pyreels.app.data.ProjectRepository
import studio.pyreels.app.data.scenario
import studio.pyreels.app.engine.PreparedVideo
import studio.pyreels.app.engine.VideoEngine
import studio.pyreels.app.engine.export.ExportService
import studio.pyreels.app.ui.Navigator
import studio.pyreels.app.ui.components.CalmCard
import studio.pyreels.app.ui.components.CalmTopBar
import studio.pyreels.app.ui.components.Muted
import studio.pyreels.app.ui.components.ProblemRow
import studio.pyreels.app.ui.components.SectionTitle
import studio.pyreels.app.ui.components.VideoPreview
import studio.pyreels.app.ui.theme.LocalUi
import studio.pyreels.core.quality.QualityChecker
import studio.pyreels.core.quality.QualityReport
import studio.pyreels.core.validate.ScenarioValidator
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val engine: VideoEngine,
    private val db: AppDatabase,
) : ViewModel() {
    val video = MutableStateFlow<PreparedVideo?>(null)
    val report = MutableStateFlow<QualityReport?>(null)
    val jobId = MutableStateFlow<Long?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val job = jobId.flatMapLatest { id -> if (id == null) flowOf(null) else db.jobs().observe(id) }
    private var pid = -1L

    fun load(id: Long) {
        if (pid == id) return
        pid = id
        viewModelScope.launch {
            val s = projects.get(id)?.scenario() ?: return@launch
            val v = engine.quick(s)
            video.value = v
            report.value = QualityChecker.check(s, v.timeline)
        }
    }

    fun start(context: android.content.Context, platform: String, quality: String, pack: Boolean, tree: Uri?, coverMs: Long?) = viewModelScope.launch {
        val id = db.jobs().insert(ExportJobEntity(projectId = pid, platform = platform, quality = quality, publishPack = pack, packTreeUri = tree?.toString(), coverTimeMs = coverMs, state = ExportJobEntity.QUEUED, createdAt = System.currentTimeMillis()))
        jobId.value = id
        ExportService.start(context)
    }

    fun scenarioJson(): String? = video.value?.scenario?.let { ScenarioValidator.encode(it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(nav: Navigator, projectId: Long, modifier: Modifier, vm: ExportViewModel = hiltViewModel()) {
    val ui = LocalUi.current
    val context = LocalContext.current
    LaunchedEffect(projectId) { vm.load(projectId) }
    val video by vm.video.collectAsState()
    val report by vm.report.collectAsState()
    val job by vm.job.collectAsState(initial = null)
    var platform by remember { mutableStateOf("tiktok") }
    var quality by remember { mutableStateOf("standard") }
    var pack by remember { mutableStateOf(true) }
    var tree by remember { mutableStateOf<Uri?>(null) }
    var cover by remember { mutableLongStateOf(-1L) }

    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            tree = uri
        }
    }
    val saveScenario = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = vm.scenarioJson()
        if (uri != null && json != null) context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
    }
    val saveVideoAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val src = job?.outputUri?.let { Uri.parse(it) }
        if (uri != null && src != null) runCatching {
            context.contentResolver.openInputStream(src)?.use { i -> context.contentResolver.openOutputStream(uri, "wt")?.use { o -> i.copyTo(o) } }
        }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val storagePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(modifier) {
        CalmTopBar(ui.t("تصدير ونشر", "Export & publish"), { nav.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            report?.let { r ->
                CalmCard {
                    Text(ui.t("درجة الجودة قبل التصدير: ${r.score}/100", "Pre-export quality score: ${r.score}/100"), style = MaterialTheme.typography.titleMedium)
                    if (r.problems.isEmpty()) Text(ui.t("ممتاز! لا ملاحظات.", "Great! No notes."), color = Muted)
                    r.problems.take(8).forEach { ProblemRow(it) }
                    if (r.problems.size > 8) Text(ui.t("و${r.problems.size - 8} ملاحظات أخرى في المحرّر.", "${r.problems.size - 8} more in the editor."), color = Muted)
                }
            }
            CalmCard {
                SectionTitle(ui.t("المنصّة (1080×1920، 30 إطارًا/ث)", "Platform (1080×1920, 30 fps)"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("tiktok" to "TikTok", "reels" to "Reels", "shorts" to "Shorts").forEach { (k, v) -> FilterChip(selected = platform == k, onClick = { platform = k }, label = { Text(v) }) }
                }
                SectionTitle(ui.t("الجودة", "Quality"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = quality == "standard", onClick = { quality = "standard" }, label = { Text(ui.t("قياسية", "Standard")) })
                    FilterChip(selected = quality == "high", onClick = { quality = "high" }, label = { Text(ui.t("عالية", "High")) })
                }
                ToggleRow(ui.t("حزمة النشر (SRT + نص الوصف + غلاف + مقطع تشويقي)", "Publish pack (SRT + caption + cover + teaser clip)"), pack) { pack = it }
                Row {
                    OutlinedButton(onClick = { pickTree.launch(null) }) { Text(if (tree == null) ui.t("اختيار مجلد للحفظ (اختياري)", "Choose a folder (optional)") else ui.t("المجلد: محدَّد ✓", "Folder: selected ✓")) }
                    if (tree != null) TextButton(onClick = { tree = null }) { Text(ui.t("الافتراضي", "Default")) }
                }
                Text(ui.t("الافتراضي: Movies/PyReelsStudio", "Default: Movies/PyReelsStudio"), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            video?.let { v ->
                CalmCard {
                    SectionTitle(ui.t("إطار الغلاف", "Cover frame"))
                    Text(ui.t("حرّك المؤشر لاختيار إطار الغلاف، أو اتركه للغلاف التلقائي.", "Scrub to pick the cover frame, or leave the automatic cover."), color = Muted, style = MaterialTheme.typography.bodySmall)
                    Row {
                        VideoPreview(v, Modifier.width(150.dp), safeZones = false, onSafeZones = {}, startAtMs = 0, onTime = { cover = it })
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (cover < 0) ui.t("غلاف تلقائي بعنوان الدرس", "Automatic title cover") else ui.t("الإطار عند ${cover / 1000.0} ث", "Frame at ${cover / 1000.0} s"))
                            TextButton(onClick = { cover = -1 }) { Text(ui.t("استخدم الغلاف التلقائي", "Use automatic cover")) }
                        }
                    }
                }
            }
            val running = job?.state == ExportJobEntity.RUNNING || job?.state == ExportJobEntity.QUEUED
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    if (Build.VERSION.SDK_INT < 29 && tree == null && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    vm.start(context, platform, quality, pack, tree, cover.takeIf { it >= 0 })
                },
                enabled = report?.canExport == true && !running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(ui.t("تصدير الفيديو", "Export video")) }
            if (report?.canExport == false) Text(ui.t("أصلح الأخطاء (✖) أولًا.", "Fix the errors (✖) first."), color = MaterialTheme.colorScheme.error)
            job?.let { j ->
                CalmCard {
                    Text(when (j.state) {
                        ExportJobEntity.DONE -> ui.t("✓ اكتمل التصدير", "✓ Export complete")
                        ExportJobEntity.FAILED -> ui.t("✖ فشل التصدير", "✖ Export failed")
                        ExportJobEntity.CANCELLED -> ui.t("أُلغي", "Cancelled")
                        else -> ui.t("جارٍ التصدير…", "Exporting…")
                    }, style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { j.progress }, modifier = Modifier.fillMaxWidth())
                    j.message?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                    if (running) OutlinedButton(onClick = { ExportService.cancel(context) }) { Text(ui.t("إلغاء", "Cancel")) }
                    if (j.state == ExportJobEntity.DONE && j.outputUri != null) {
                        val uri = Uri.parse(j.outputUri)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { openUri(context, uri, "video/mp4") }) { Text(ui.t("فتح", "Open")) }
                            OutlinedButton(onClick = { shareUri(context, uri, "video/mp4") }) { Text(ui.t("مشاركة", "Share")) }
                            OutlinedButton(onClick = { saveVideoAs.launch("${video?.scenario?.meta?.lesson?.id ?: "video"}.mp4") }) { Text(ui.t("حفظ باسم…", "Save as…")) }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { saveScenario.launch("${video?.scenario?.meta?.lesson?.id ?: "lesson"}.pyreels.json") }, modifier = Modifier.fillMaxWidth()) {
                Text(ui.t("نسخة احتياطية: تصدير السيناريو .pyreels.json", "Backup: export scenario .pyreels.json"))
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
