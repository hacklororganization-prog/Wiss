package studio.pyreels.app.ui.screens

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import studio.pyreels.app.data.ProjectRepository
import studio.pyreels.app.data.SeriesRepository
import studio.pyreels.app.ui.Navigator
import studio.pyreels.app.ui.Route
import studio.pyreels.app.ui.components.CalmCard
import studio.pyreels.app.ui.components.CalmTopBar
import studio.pyreels.app.ui.components.Muted
import studio.pyreels.app.ui.components.ProblemRow
import studio.pyreels.app.ui.theme.LocalUi
import studio.pyreels.core.prompt.PromptBuilder
import studio.pyreels.core.validate.AutoRepair
import studio.pyreels.core.validate.ParseResult
import studio.pyreels.core.validate.ScenarioValidator
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val series: SeriesRepository,
) : ViewModel() {
    fun import(r: ParseResult, done: (List<Long>) -> Unit) = viewModelScope.launch {
        val ids = projects.createAll(r.scenarios, null)
        // A weekly batch updates the series history in order.
        if (r.isBatch) r.scenarios.zip(ids).forEach { (s, id) -> series.record(id, s) }
        done(ids)
    }
}

@Composable
fun ImportScreen(nav: Navigator, initial: String?, modifier: Modifier, vm: ImportViewModel = hiltViewModel()) {
    val ui = LocalUi.current
    val context = LocalContext.current
    var text by remember { mutableStateOf(initial ?: "") }
    var result by remember { mutableStateOf<ParseResult?>(null) }
    var repairs by remember { mutableStateOf<List<String>>(emptyList()) }
    var info by remember { mutableStateOf<String?>(null) }

    fun validate() { result = if (text.isBlank()) null else ScenarioValidator.parse(text) }
    LaunchedEffect(initial) { if (initial != null) validate() }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            validate()
        }
    }
    val saveRepair = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val r = result
        if (uri != null && r != null) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(PromptBuilder.repairPrompt(text, r.problems).toByteArray()) }
            info = ui.t("تم حفظ برومبت الإصلاح. أرسله للذكاء الاصطناعي ثم استورد الرد.", "Repair prompt saved. Send it to the AI, then import its reply.")
        }
    }

    Column(modifier) {
        CalmTopBar(ui.t("استيراد سيناريو", "Import scenario"), { nav.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) {
                    Text(ui.t("اختيار ملف .pyreels.json", "Pick .pyreels.json"))
                }
                OutlinedButton(onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                    text = clip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                    validate()
                }, modifier = Modifier.weight(1f)) { Text(ui.t("لصق من الحافظة", "Paste from clipboard")) }
            }
            OutlinedTextField(
                text, { text = it; result = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 300.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                label = { Text("JSON") },
            )
            OutlinedButton(onClick = { validate() }, modifier = Modifier.fillMaxWidth()) { Text(ui.t("تحقّق", "Validate")) }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (repairs.isNotEmpty()) CalmCard {
                Text(ui.t("إصلاحات تلقائية طُبّقت:", "Auto-repairs applied:"), style = MaterialTheme.typography.titleSmall)
                repairs.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            result?.let { r ->
                CalmCard {
                    Text(
                        if (r.ok) ui.t("✓ السيناريو صالح" + (if (r.isBatch) " (${r.scenarios.size} حلقات)" else ""), "✓ Scenario is valid" + (if (r.isBatch) " (${r.scenarios.size} episodes)" else ""))
                        else ui.t("✖ ${r.errors.size} خطأ يجب إصلاحه", "✖ ${r.errors.size} errors to fix"),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    if (r.problems.isEmpty()) Text(ui.t("لا توجد ملاحظات.", "No notes."), color = Muted)
                    r.problems.take(60).forEach { ProblemRow(it) }
                }
                if (r.ok) {
                    Button(onClick = {
                        vm.import(r) { ids -> if (ids.size == 1) nav.replace(Route.Preview(ids.first())) else nav.home() }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (r.isBatch) ui.t("إنشاء ${r.scenarios.size} مشاريع", "Create ${r.scenarios.size} projects") else ui.t("استيراد ومعاينة", "Import and preview")) }
                } else {
                    Button(onClick = {
                        val fixed = AutoRepair.repair(text)
                        text = fixed.text; repairs = fixed.applied; validate()
                    }, modifier = Modifier.fillMaxWidth()) { Text(ui.t("إصلاح تلقائي آمن", "Safe auto-repair")) }
                    OutlinedButton(onClick = { saveRepair.launch("repair_prompt.md") }, modifier = Modifier.fillMaxWidth()) { Text(ui.t("إنشاء برومبت إصلاح", "Create a repair prompt")) }
                }
            }
        }
    }
}
