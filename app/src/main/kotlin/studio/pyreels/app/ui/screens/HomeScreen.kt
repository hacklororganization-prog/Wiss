package studio.pyreels.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import studio.pyreels.app.data.PresetRepository
import studio.pyreels.app.data.ProjectEntity
import studio.pyreels.app.data.ProjectRepository
import studio.pyreels.app.data.SeriesRepository
import studio.pyreels.app.data.scenario
import studio.pyreels.app.engine.PreparedVideo
import studio.pyreels.app.engine.VideoEngine
import studio.pyreels.app.ui.components.VideoPreview
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.width
import studio.pyreels.app.ui.Navigator
import studio.pyreels.app.ui.Route
import studio.pyreels.app.ui.components.CalmCard
import studio.pyreels.app.ui.components.CalmTopBar
import studio.pyreels.app.ui.components.Muted
import studio.pyreels.app.ui.components.SectionTitle
import studio.pyreels.app.ui.theme.LocalUi
import studio.pyreels.core.brief.EpisodeRecord
import studio.pyreels.core.brief.SeriesMemory
import studio.pyreels.core.brief.StylePreset
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val engine: VideoEngine,
    series: SeriesRepository,
    presets: PresetRepository,
    teacherStore: studio.pyreels.app.data.TeacherStore,
) : ViewModel() {
    val teachers = teacherStore.teachers
    val list: StateFlow<List<ProjectEntity>> = projects.projects.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history: StateFlow<List<EpisodeRecord>> = series.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val presetList: StateFlow<List<StylePreset>> = presets.presets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val seriesRepo = series

    fun delete(id: Long) = viewModelScope.launch { projects.delete(id) }

    /** Live preview of the most recent project for the "continue" card. */
    val latest: StateFlow<Pair<ProjectEntity, PreparedVideo>?> = projects.projects.map { l ->
        l.firstOrNull()?.let { p -> p.scenario()?.let { p to engine.quick(it) } }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun HomeScreen(nav: Navigator, modifier: Modifier, vm: HomeViewModel = hiltViewModel()) {
    val ui = LocalUi.current
    val projects by vm.list.collectAsState()
    val history by vm.history.collectAsState()
    val presets by vm.presetList.collectAsState()
    val latest by vm.latest.collectAsState()
    val teachers by vm.teachers.collectAsState()

    Column(modifier) {
        CalmTopBar("PyReels Studio", null) {
            IconButton(onClick = { nav.go(Route.Series) }) { Icon(Icons.Outlined.History, ui.t("ذاكرة السلسلة", "Series memory")) }
            IconButton(onClick = { nav.go(Route.Settings) }) { Icon(Icons.Outlined.Settings, ui.t("الإعدادات", "Settings")) }
        }
        LazyColumn(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            latest?.let { (p, video) ->
                item {
                    CalmCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            // respects the system "remove animations" accessibility setting
                            VideoPreview(video, Modifier.width(120.dp), autoPlay = android.animation.ValueAnimator.areAnimatorsEnabled(), safeZones = false, onSafeZones = {}, controls = false, muted = true, loop = true, fps = 12)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(ui.t("تابع من حيث توقفت", "Continue where you left off"), color = Muted, style = MaterialTheme.typography.labelMedium)
                                Text(p.title, style = MaterialTheme.typography.titleMedium)
                                Text((p.episode?.let { ui.t("الحلقة $it", "Episode $it") } ?: p.lessonId), color = Muted, style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { nav.go(Route.Editor(p.id)) }, modifier = Modifier.fillMaxWidth()) { Text(ui.t("متابعة التحرير", "Continue editing")) }
                                OutlinedButton(onClick = { nav.go(Route.Export(p.id)) }, modifier = Modifier.fillMaxWidth()) { Text(ui.t("تصدير ونشر", "Export & publish")) }
                            }
                        }
                    }
                }
            }
            if (teachers.isEmpty()) item {
                CalmCard {
                    Text(ui.t("ابدأ بإضافة لغتك الأولى", "Start by adding your first language"), style = MaterialTheme.typography.titleMedium)
                    Text(ui.t("اكتب اسم أي لغة برمجة (C++ أو Rust أو غيرها) واختر صورتها من المعرض؛ ثم أنشئ درسك الأول.", "Type the name of any programming language and pick its picture from the gallery; then create your first lesson."), color = Muted)
                    Button(onClick = { nav.go(Route.Settings) }) { Text(ui.t("إضافة لغة", "Add a language")) }
                }
            } else if (history.isNotEmpty()) item {
                CalmCard {
                    Text(
                        ui.t("أنجزت ${history.size} حلقة · مستوى أحمد ${SeriesMemory.ahmedLevel(history.size)}/4",
                            "${history.size} episodes done · Ahmed level ${SeriesMemory.ahmedLevel(history.size)}/4"),
                        color = Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { nav.go(Route.Wizard()) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.padding(3.dp)); Text(ui.t("درس جديد", "New lesson"))
                    }
                    OutlinedButton(onClick = { nav.go(Route.Import()) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.padding(3.dp)); Text(ui.t("استيراد سيناريو", "Import scenario"))
                    }
                }
            }
            item {
                OutlinedButton(onClick = { nav.go(Route.Weekly) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.padding(3.dp)); Text(ui.t("الخطة الأسبوعية (7 حلقات)", "Weekly plan (7 episodes)"))
                }
            }
            if (presets.isNotEmpty()) item {
                SectionTitle(ui.t("أنماطي المحفوظة", "My saved styles"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets, key = { it.id }) { p -> AssistChip(onClick = { nav.go(Route.Wizard(presetId = p.id)) }, label = { Text(p.name) }) }
                }
            }
            item { SectionTitle(ui.t("مشاريعي", "My projects")) }
            if (projects.isEmpty()) item { Text(ui.t("لا توجد مشاريع بعد. أنشئ درسًا جديدًا أو استورد سيناريو.", "No projects yet. Create a lesson or import a scenario."), color = Muted) }
            items(projects, key = { it.id }) { p ->
                CalmCard(Modifier.clickable { nav.go(Route.Editor(p.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.title, style = MaterialTheme.typography.titleSmall)
                            Text((p.episode?.let { ui.t("الحلقة $it · ", "Episode $it · ") } ?: "") + p.lessonId + (if (p.lastExportUri != null) ui.t(" · مُصدَّر", " · exported") else ""), color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { vm.delete(p.id) }) { Icon(Icons.Outlined.Delete, ui.t("حذف المشروع", "Delete project")) }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}
