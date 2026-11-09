package studio.pyreels.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.pyreels.app.data.AppDatabase
import studio.pyreels.app.engine.export.ExportService
import studio.pyreels.app.ui.Navigator
import studio.pyreels.app.ui.Route
import studio.pyreels.app.ui.screens.EditorScreen
import studio.pyreels.app.ui.screens.ExportScreen
import studio.pyreels.app.ui.screens.HomeScreen
import studio.pyreels.app.ui.screens.ImportScreen
import studio.pyreels.app.ui.screens.PreviewScreen
import studio.pyreels.app.ui.screens.SeriesScreen
import studio.pyreels.app.ui.screens.SettingsScreen
import studio.pyreels.app.ui.screens.WeeklyScreen
import studio.pyreels.app.ui.screens.WizardScreen
import studio.pyreels.app.ui.theme.PyReelsTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var prefs: Prefs
    @Inject lateinit var db: AppDatabase

    private val incoming = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        // Resume exports interrupted by process death.
        lifecycleScope.launch {
            val pending = withContext(Dispatchers.IO) { db.jobs().unfinished() }
            if (pending.isNotEmpty()) ExportService.start(this@MainActivity)
        }
        setContent {
            val dark by prefs.dark.collectAsState()
            val english by prefs.english.collectAsState()
            val fontScale by prefs.fontScale.collectAsState()
            PyReelsTheme(dark = dark, english = english, fontScale = fontScale) {
                CompositionLocalProvider(LocalLayoutDirection provides if (english) LayoutDirection.Ltr else LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) {
                        AppRoot(prefs, incoming.value) { incoming.value = null }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        i ?: return
        val text = when (i.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT) ?: (if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM))?.let { read(it) }
            Intent.ACTION_VIEW -> i.data?.let { read(it) }
            else -> null
        }
        if (text != null) incoming.value = text
    }

    private fun read(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()
}

@Composable
fun AppRoot(prefs: Prefs, incoming: String?, consumed: () -> Unit) {
    val nav = remember { Navigator() }
    LaunchedEffect(incoming) {
        if (incoming != null) { nav.go(Route.Import(incoming)); consumed() }
    }
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    val safeZones by prefs.safeZones.collectAsState()
    val mod = Modifier.fillMaxSize().safeDrawingPadding()
    when (val r = nav.current) {
        Route.Home -> HomeScreen(nav, mod)
        is Route.Wizard -> WizardScreen(nav, r, mod)
        is Route.Import -> ImportScreen(nav, r.text, mod)
        is Route.Editor -> EditorScreen(nav, r.projectId, safeZones, prefs::setSafeZones, mod)
        is Route.Preview -> PreviewScreen(nav, r.projectId, safeZones, prefs::setSafeZones, mod)
        is Route.Export -> ExportScreen(nav, r.projectId, mod)
        Route.Weekly -> WeeklyScreen(nav, mod)
        Route.Series -> SeriesScreen(nav, mod)
        Route.Settings -> SettingsScreen(nav, prefs, mod)
    }
}
