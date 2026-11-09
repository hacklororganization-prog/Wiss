package studio.pyreels.app.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import studio.pyreels.app.data.ProjectRepository
import studio.pyreels.app.data.scenario
import studio.pyreels.app.engine.PreparedVideo
import studio.pyreels.app.engine.VideoEngine
import studio.pyreels.app.ui.Navigator
import studio.pyreels.app.ui.Route
import studio.pyreels.app.ui.components.CalmTopBar
import studio.pyreels.app.ui.components.Muted
import studio.pyreels.app.ui.components.VideoPreview
import studio.pyreels.app.ui.theme.LocalUi
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(private val projects: ProjectRepository, private val engine: VideoEngine) : ViewModel() {
    val video = MutableStateFlow<PreparedVideo?>(null)
    val preparing = MutableStateFlow(false)
    private var loaded = -1L

    fun load(context: Context, id: Long) {
        if (loaded == id) return
        loaded = id
        viewModelScope.launch {
            val s = projects.get(id)?.scenario() ?: return@launch
            video.value = engine.quick(s) // instant preview
            preparing.value = true
            video.value = engine.prepare(context.applicationContext, s) // then real voice + real Python
            preparing.value = false
        }
    }
}

@Composable
fun PreviewScreen(nav: Navigator, projectId: Long, safeZones: Boolean, onSafeZones: (Boolean) -> Unit, modifier: Modifier, vm: PreviewViewModel = hiltViewModel()) {
    val ui = LocalUi.current
    val context = LocalContext.current
    LaunchedEffect(projectId) { vm.load(context, projectId) }
    val video by vm.video.collectAsState()
    val preparing by vm.preparing.collectAsState()
    Column(modifier) {
        CalmTopBar(video?.scenario?.meta?.title ?: ui.t("معاينة", "Preview"), { nav.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (preparing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(ui.t("يجهّز الصوت وتنفيذ بايثون الحقيقي…", "Preparing voice and real Python execution…"), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            video?.let { v ->
                VideoPreview(v, Modifier.fillMaxWidth(), autoPlay = !preparing, safeZones = safeZones, onSafeZones = onSafeZones)
                v.notes.forEach { Text("ℹ $it", style = MaterialTheme.typography.bodySmall, color = Muted) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { nav.replace(Route.Editor(projectId)) }, modifier = Modifier.weight(1f)) { Text(ui.t("تحرير", "Edit")) }
                Button(onClick = { nav.go(Route.Export(projectId)) }, modifier = Modifier.weight(1f)) { Text(ui.t("تصدير", "Export")) }
            }
        }
    }
}
