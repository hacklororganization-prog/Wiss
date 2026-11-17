package studio.pyreels.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import studio.pyreels.app.BuildConfig
import studio.pyreels.app.Prefs
import studio.pyreels.app.data.AvatarStore
import studio.pyreels.app.ui.Navigator
import studio.pyreels.app.ui.components.CalmCard
import studio.pyreels.app.ui.components.CalmTopBar
import studio.pyreels.app.ui.components.Muted
import studio.pyreels.app.ui.theme.LocalUi
import studio.pyreels.core.model.Personas
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import studio.pyreels.app.data.Teacher
import studio.pyreels.app.data.TeacherStore
import studio.pyreels.app.ui.components.TeacherDialog
import studio.pyreels.app.ui.components.SfxPreviewer
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AssistChip
import javax.inject.Inject

@Composable
fun SettingsScreen(nav: Navigator, prefs: Prefs, modifier: Modifier) {
    val ui = LocalUi.current
    val dark by prefs.dark.collectAsState()
    val english by prefs.english.collectAsState()
    val scale by prefs.fontScale.collectAsState()
    Column(modifier) {
        CalmTopBar(ui.t("الإعدادات", "Settings"), { nav.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CalmCard {
                ToggleRow(ui.t("المظهر الداكن الهادئ", "Calm dark theme"), dark, prefs::setDark)
                ToggleRow("English UI", english, prefs::setEnglish)
                Text(ui.t("حجم الخط: ${"%.0f".format(scale * 100)}٪", "Font size: ${"%.0f".format(scale * 100)}%"))
                Slider(scale, prefs::setFontScale, valueRange = 0.85f..1.4f, steps = 10)
            }
            LanguagesCard()
            SoundPreviewCard()
            CalmCard {
                Text(ui.t("الخصوصية", "Privacy"), style = MaterialTheme.typography.titleMedium)
                Text(ui.t("التطبيق يعمل بلا إنترنت تمامًا: بلا تحليلات، بلا شبكة، وبايثون معزول. لا يحتاج صلاحيات تخزين واسعة.", "Fully offline: no analytics, no network, sandboxed Python, no broad storage permission."), color = Muted)
                Text("PyReels Studio ${BuildConfig.VERSION_NAME}", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@HiltViewModel
class TeachersViewModel @Inject constructor(val store: TeacherStore) : ViewModel()

/**
 * The creator's languages (nothing is built in): add any language by name, give it a picture from
 * the gallery, edit or remove it. The group and Ahmed have pictures too. Used in the preview and the export.
 */
@Composable
fun LanguagesCard(vm: TeachersViewModel = hiltViewModel()) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val version by AvatarStore.version.collectAsState()
    val teachers by vm.store.teachers.collectAsState()
    var target by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Teacher?>(null) }
    var adding by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = target
        if (uri != null && key != null) failed = !AvatarStore.save(context, key, uri)
        target = null
    }
    fun pick(key: String) {
        target = key
        failed = false
        runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { failed = true }
    }
    CalmCard {
        Text(ui.t("اللغات والصور", "Languages and pictures"), style = MaterialTheme.typography.titleMedium)
        Text(
            ui.t("أضف أي لغة برمجة تريدها (C++ وRust وغيرها) وصورتها من المعرض. كل لغة تصبح معلّمًا في المحادثة بصورتها واسمها.",
                "Add any programming language you like and its picture from the gallery. Each language becomes a teacher in the chat, with its name and picture."),
            color = Muted, style = MaterialTheme.typography.bodySmall,
        )
        if (failed) Text(ui.t("تعذّرت قراءة الصورة. جرّب صورة أخرى.", "Could not read that picture. Try another one."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        PictureRow(AvatarStore.GROUP, ui.t("صورة المجموعة", "Group picture"), null, 0xFF7FA8A0, version, ::pick)
        PictureRow(Personas.AHMED.id, ui.t("أحمد (المتعلّم)", "Ahmed (learner)"), Personas.AHMED.monogram, Personas.AHMED.accentDark, version, ::pick)
        if (teachers.isEmpty()) Text(ui.t("لم تضف أي لغة بعد.", "No language added yet."), color = Muted)
        teachers.forEach { t ->
            val p = Personas.forLanguage(t.id, t.name)
            PictureRow(t.id, t.name, p.monogram, p.accentDark, version, ::pick, onEdit = { dialog = t }, onDelete = { vm.store.remove(t.id) })
        }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null); Text("  " + ui.t("إضافة لغة", "Add a language"))
        }
    }
    if (adding) TeacherDialog(vm.store, null, onDismiss = { adding = false })
    dialog?.let { TeacherDialog(vm.store, it, onDismiss = { dialog = null }) }
}

@Composable
private fun PictureRow(
    key: String, label: String, monogram: String?, accent: Long, version: Int, onPick: (String) -> Unit,
    onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null,
) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val bitmap = remember(key, version) { AvatarStore.load(context, key) }
    val color = Color(accent)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onPick(key) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) {
            when {
                bitmap != null -> Image(bitmap.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(CircleShape))
                monogram != null -> Text(monogram, color = color, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                else -> Icon(Icons.Filled.Groups, contentDescription = null, tint = color)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(if (bitmap != null) ui.t("صورة من المعرض", "Gallery picture") else ui.t("اضغط لاختيار صورة", "Tap to pick a picture"), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (bitmap != null) IconButton(onClick = { AvatarStore.remove(context, key) }) { Icon(Icons.Filled.Close, contentDescription = ui.t("حذف الصورة", "Remove picture")) }
        onEdit?.let { IconButton(onClick = it) { Icon(Icons.Filled.Edit, contentDescription = ui.t("تعديل", "Edit")) } }
        onDelete?.let { IconButton(onClick = it) { Icon(Icons.Filled.Delete, contentDescription = ui.t("حذف اللغة", "Delete language")) } }
        if (onEdit == null) IconButton(onClick = { onPick(key) }) { Icon(Icons.Filled.PhotoLibrary, contentDescription = ui.t("اختيار من المعرض", "Pick from gallery"), tint = MaterialTheme.colorScheme.primary) }
    }
}

/** Listen to every sound effect before choosing the level. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundPreviewCard() {
    val ui = LocalUi.current
    val previewer = remember { SfxPreviewer() }
    DisposableEffect(Unit) { onDispose { previewer.release() } }
    CalmCard {
        Text(ui.t("المؤثرات الصوتية", "Sound effects"), style = MaterialTheme.typography.titleMedium)
        Text(
            ui.t("لا توجد موسيقى خلفية أبدًا. اضغط لتسمع كل مؤثر؛ ومستواه (بلا / هادئ / عادي) من إعدادات الفيديو في المحرّر.",
                "There is never background music. Tap to hear each effect; its level (off / soft / normal) is in the editor's video settings."),
            color = Muted, style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            studio.pyreels.core.model.SfxIds.ALL.filter { it != studio.pyreels.core.model.SfxIds.NONE && it != studio.pyreels.core.model.SfxIds.TYPE_LOOP }.forEach { id ->
                AssistChip(onClick = { previewer.play(id) }, label = { Text(id) }, leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) })
            }
        }
    }
}
