package studio.pyreels.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import studio.pyreels.app.data.EpisodeEntity
import studio.pyreels.app.data.SeriesRepository
import studio.pyreels.app.ui.Navigator
import studio.pyreels.app.ui.components.CalmCard
import studio.pyreels.app.ui.components.CalmTopBar
import studio.pyreels.app.ui.components.Muted
import studio.pyreels.app.ui.theme.LocalUi
import studio.pyreels.core.brief.SeriesMemory
import javax.inject.Inject

@HiltViewModel
class SeriesViewModel @Inject constructor(private val repo: SeriesRepository) : ViewModel() {
    val list = repo.entities.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun update(e: EpisodeEntity) = viewModelScope.launch { repo.update(e) }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun reset() = viewModelScope.launch { repo.reset() }
}

@Composable
fun SeriesScreen(nav: Navigator, modifier: Modifier, vm: SeriesViewModel = hiltViewModel()) {
    val ui = LocalUi.current
    val list by vm.list.collectAsState()
    var editing by remember { mutableStateOf<EpisodeEntity?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    Column(modifier) {
        CalmTopBar(ui.t("ذاكرة السلسلة", "Series memory"), { nav.back() })
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val level = SeriesMemory.ahmedLevel(list.size)
                CalmCard {
                    Text(ui.t("مستوى أحمد: $level/4", "Ahmed's level: $level/4"), style = MaterialTheme.typography.titleMedium)
                    Text(SeriesMemory.ahmedLevelDescription(level), color = Muted)
                    Text(ui.t("كل شيء محفوظ على هذا الجهاز فقط.", "Everything stays on this device."), color = Muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { confirmReset = true }) { Text(ui.t("إعادة ضبط السلسلة", "Reset history")) }
                }
            }
            items(list, key = { it.id }) { e ->
                CalmCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ui.t("الحلقة ${e.episode}: ${e.title}", "Episode ${e.episode}: ${e.title}"), style = MaterialTheme.typography.titleSmall)
                            Text("«${e.keySentence}»", color = Muted)
                            e.mistake?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                        }
                        IconButton(onClick = { editing = e }) { Icon(Icons.Outlined.Edit, ui.t("تعديل", "Edit")) }
                        IconButton(onClick = { vm.delete(e.id) }) { Icon(Icons.Outlined.Delete, ui.t("حذف", "Delete")) }
                    }
                }
            }
        }
    }
    editing?.let { e ->
        var title by remember(e) { mutableStateOf(e.title) }
        var key by remember(e) { mutableStateOf(e.keySentence) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(ui.t("تعديل الحلقة", "Edit episode")) },
            text = { Column { OutlinedTextField(title, { title = it }, label = { Text(ui.t("العنوان", "Title")) }); OutlinedTextField(key, { key = it }, label = { Text(ui.t("جملة اليوم", "Key sentence")) }) } },
            confirmButton = { TextButton(onClick = { vm.update(e.copy(title = title, keySentence = key)); editing = null }) { Text(ui.t("حفظ", "Save")) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(ui.t("إلغاء", "Cancel")) } })
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text(ui.t("حذف كل السجل؟", "Delete all history?")) },
        confirmButton = { TextButton(onClick = { vm.reset(); confirmReset = false }) { Text(ui.t("نعم، احذف", "Yes, delete")) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(ui.t("إلغاء", "Cancel")) } })
}
