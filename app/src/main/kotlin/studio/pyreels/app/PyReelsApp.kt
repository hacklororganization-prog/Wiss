package studio.pyreels.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import studio.pyreels.app.data.PresetRepository
import javax.inject.Inject

@HiltAndroidApp
class PyReelsApp : Application() {
    @Inject lateinit var presets: PresetRepository

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(EXPORT_CHANNEL, getString(R.string.export_channel), NotificationManager.IMPORTANCE_LOW))
        appScope.launch { presets.ensureDefaults() }
    }

    companion object {
        const val EXPORT_CHANNEL = "export"
    }
}
