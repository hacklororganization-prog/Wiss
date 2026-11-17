package studio.pyreels.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import studio.pyreels.app.data.AvatarStore
import studio.pyreels.app.data.Teacher
import studio.pyreels.app.data.TeacherStore
import studio.pyreels.app.ui.theme.LocalUi

/** Add or edit a language: its name, an optional picture from the gallery, and an optional personality note. */
@Composable
fun TeacherDialog(store: TeacherStore, initial: Teacher?, onDismiss: () -> Unit, onSaved: (Teacher) -> Unit = {}) {
    val ui = LocalUi.current
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { picked = uri; failed = false } }
    val hasPicture = picked != null || (initial != null && AvatarStore.has(context, initial.id))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) ui.t("إضافة لغة", "Add a language") else ui.t("تعديل اللغة", "Edit language")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name, { name = it.take(20) }, singleLine = true, modifier = fillW,
                    label = { Text(ui.t("اسم اللغة (أي لغة)", "Language name (any language)")) },
                    supportingText = { Text(ui.t("مثل: C++ أو Rust أو أي لغة تريدها", "e.g. C++, Rust, or any language you like")) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { failed = true }
                    }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Text("  " + if (hasPicture) ui.t("تغيير الصورة", "Change picture") else ui.t("اختيار صورة من المعرض", "Pick a picture"))
                    }
                }
                if (picked != null) Text(ui.t("تم اختيار صورة ✓", "Picture selected ✓"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (failed) Text(ui.t("تعذّر فتح المعرض.", "Could not open the gallery."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    note, { note = it.take(160) }, modifier = fillW, minLines = 2,
                    label = { Text(ui.t("شخصية المعلّم (اختياري)", "Teacher personality (optional)")) },
                    supportingText = { Text(ui.t("مثال: صارم لكنه لطيف، يحب المقارنات", "e.g. strict but kind, loves comparisons")) },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val saved = store.upsert(initial?.id, name, note)
                picked?.let { AvatarStore.save(context, saved.id, it) }
                onSaved(saved)
                onDismiss()
            }) { Text(ui.t("حفظ", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(ui.t("إلغاء", "Cancel")) } },
    )
}

private val fillW = androidx.compose.ui.Modifier.fillMaxWidth()
