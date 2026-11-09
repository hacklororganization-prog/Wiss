package studio.pyreels.app.engine.export

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.pyreels.app.MainActivity
import studio.pyreels.app.PyReelsApp
import studio.pyreels.app.R
import studio.pyreels.app.data.AppDatabase
import studio.pyreels.app.data.ExportJobEntity
import studio.pyreels.app.data.ProjectRepository
import studio.pyreels.app.data.SeriesRepository
import studio.pyreels.app.data.scenario
import studio.pyreels.app.engine.VideoEngine
import studio.pyreels.app.engine.render.FrameRenderer
import studio.pyreels.core.model.Limits
import studio.pyreels.core.publish.PublishPack
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Foreground export service: progress notification with cancel, memory-safe streaming render, and
 * resume after process death (jobs are persisted in Room and restarted from frame 0 — rendering is
 * deterministic, see docs/DECISIONS.md #10).
 */
@AndroidEntryPoint
class ExportService : Service() {

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var series: SeriesRepository
    @Inject lateinit var engine: VideoEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var current: Job? = null
    private var currentJobId: Long = -1
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            current?.cancel(CancellationException("cancelled"))
            return START_NOT_STICKY
        }
        // If the system refuses the foreground promotion the export still runs while the app is open.
        runCatching { startInForeground(notification(getString(R.string.export_channel), 0, indeterminate = true)) }
        if (current?.isActive == true) return START_STICKY
        current = scope.launch { runQueue() }
        return START_STICKY
    }

    private suspend fun runQueue() {
        acquireWakeLock()
        try {
            val attempts = getSharedPreferences("export_attempts", MODE_PRIVATE)
            while (true) {
                val job = db.jobs().unfinished().firstOrNull() ?: break
                currentJobId = job.id
                // A job that already started twice and never finished took the process down with
                // it: stop retrying instead of crashing on every launch.
                val key = "job_${job.id}"
                val tries = attempts.getInt(key, 0)
                if (tries >= 2) {
                    db.jobs().update(job.copy(state = ExportJobEntity.FAILED, message = "توقّف التصدير مرتين بشكل غير متوقع. جرّب الجودة القياسية أو أعد تشغيل الهاتف."))
                    continue
                }
                attempts.edit().putInt(key, tries + 1).commit()
                runJob(job)
                attempts.edit().remove(key).apply()
            }
        } catch (t: Throwable) {
            studio.pyreels.app.CrashLog.record(this, t, "export-queue")
        } finally {
            releaseWakeLock()
            runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
            stopSelf()
        }
    }

    private suspend fun runJob(jobIn: ExportJobEntity) {
        var job = jobIn.copy(state = ExportJobEntity.RUNNING, progress = 0f, message = "يجهّز الصوت والكود…")
        db.jobs().update(job)
        try {
            val project = projects.get(job.projectId) ?: error("المشروع غير موجود")
            val scenario = project.scenario() ?: error("السيناريو غير صالح")
            val prepared = engine.prepare(this, scenario)
            if (prepared.timeline.totalMs > Limits.HARD_CAP_SECONDS * 1000L) error("المدة تتجاوز ${Limits.HARD_CAP_SECONDS} ثانية")
            val options = if (job.quality == "high") VideoExporter.Options.high() else VideoExporter.Options.standard()
            val exporter = VideoExporter(this)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val base = "${scenario.meta.lesson.id}_${job.platform}_$stamp"
            val out = File(Storage.exportsDir(this), "$base.mp4")
            var lastPct = -1
            val packSteps = if (job.publishPack) 0.15f else 0f
            val verification = exporter.export(prepared, out, options) { p ->
                val pct = (p * (1f - packSteps) * 100).toInt()
                if (pct != lastPct) {
                    lastPct = pct
                    notify(notification("تصدير «${scenario.meta.title}»", pct, false))
                    scope.launch { runCatching { db.jobs().update(job.copy(progress = pct / 100f, message = "يرسم الإطارات… $pct٪")) } }
                }
            }
            if (!verification.ok) error("فشل التحقق من الملف: ${verification.problems.joinToString()}")
            val tree = job.packTreeUri?.let { Uri.parse(it) }
            val videoUri = if (tree != null) Storage.saveToTree(this, tree, out, "$base.mp4", Storage.Kind.VIDEO)
            else Storage.saveToMediaStore(this, out, "$base.mp4", Storage.Kind.VIDEO)
            videoUri ?: error("تعذّر حفظ الفيديو")
            if (job.publishPack) {
                job = job.copy(message = "يجهّز حزمة النشر…"); db.jobs().update(job)
                writePublishPack(prepared, base, tree, job.coverTimeMs)
            }
            projects.setExport(project.id, videoUri.toString())
            series.record(project.id, scenario)
            job = job.copy(state = ExportJobEntity.DONE, progress = 1f, outputUri = videoUri.toString(), message = "تم الحفظ والتحقق: ${verification.width}×${verification.height}، ${"%.1f".format(verification.durationMs / 1000.0)} ث")
            db.jobs().update(job)
            notifyDone(scenario.meta.title, videoUri)
            out.delete()
        } catch (e: CancellationException) {
            // the coroutine is already cancelled: the state must still be written
            withContext(NonCancellable) { runCatching { db.jobs().update(job.copy(state = ExportJobEntity.CANCELLED, message = "أُلغي التصدير")) } }
        } catch (t: Throwable) {
            studio.pyreels.app.CrashLog.record(this, t, "export")
            runCatching { db.jobs().update(job.copy(state = ExportJobEntity.FAILED, message = "تعذّر التصدير: " + (t.message ?: t.javaClass.simpleName))) }
        }
    }

    private suspend fun writePublishPack(prepared: studio.pyreels.app.engine.PreparedVideo, base: String, tree: Uri?, coverTimeMs: Long?) {
        val dir = Storage.exportsDir(this)
        fun save(file: File, name: String, kind: Storage.Kind) {
            if (tree != null) Storage.saveToTree(this, tree, file, name, kind) else Storage.saveToMediaStore(this, file, name, kind)
            file.delete()
        }
        // SRT (word-timed chunks, RTL-embedded)
        val srt = File(dir, "$base.srt").apply { writeText(PublishPack.srt(prepared.timeline), Charsets.UTF_8) }
        save(srt, "$base.srt", Storage.Kind.SRT)
        // caption text
        val cap = File(dir, "${base}_caption.txt").apply { writeText(PublishPack.caption(prepared.scenario).render(), Charsets.UTF_8) }
        save(cap, "${base}_caption.txt", Storage.Kind.TEXT)
        // cover
        val bmp = Bitmap.createBitmap(Limits.WIDTH, Limits.HEIGHT, Bitmap.Config.ARGB_8888)
        val renderer = FrameRenderer(this, prepared.scenario).apply { setChapters(prepared.timeline) }
        val canvas = Canvas(bmp)
        if (coverTimeMs != null) renderer.draw(canvas, prepared.timeline.frameAt(coverTimeMs)) else renderer.drawCover(canvas)
        val coverFile = File(dir, "${base}_cover.png")
        coverFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        save(coverFile, "${base}_cover.png", Storage.Kind.IMAGE)
        // teaser clip (hook + build-up, no spoilers)
        val teaser = File(dir, "${base}_teaser.mp4")
        val v = VideoExporter(this).export(prepared, teaser, VideoExporter.Options(ranges = PublishPack.teaserWindow(prepared.timeline))) { }
        if (v.ok || v.problems.all { it.startsWith("duration") }) save(teaser, "${base}_teaser.mp4", Storage.Kind.VIDEO)
    }

    private fun startInForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }

    private fun notification(title: String, pct: Int, indeterminate: Boolean): Notification {
        val cancel = PendingIntent.getService(this, 1, Intent(this, ExportService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, PyReelsApp.EXPORT_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(if (indeterminate) "يجهّز…" else "$pct٪")
            .setProgress(100, pct, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إلغاء", cancel)
            .build()
    }

    private fun notify(n: Notification) {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n) }
    }

    private fun notifyDone(title: String, uri: Uri) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pi = PendingIntent.getActivity(this, 3, view, PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, PyReelsApp.EXPORT_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("اكتمل: $title")
            .setContentText("محفوظ في DCIM/${Storage.FOLDER}")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, n) }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pyreels:export").apply { acquire(20 * 60 * 1000L) }
    }

    private fun releaseWakeLock() { runCatching { wakeLock?.release() }; wakeLock = null }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "studio.pyreels.export.CANCEL"
        private const val NOTIFICATION_ID = 4107

        fun start(context: Context) {
            val intent = Intent(context, ExportService::class.java)
            // Some systems refuse a foreground-service start (battery saver, background launch):
            // fall back to a plain start, and never let the refusal crash the caller.
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                runCatching { context.startService(intent) }.onFailure { studio.pyreels.app.CrashLog.record(context, it, "export-start") }
            }
        }

        fun cancel(context: Context) {
            runCatching { context.startService(Intent(context, ExportService::class.java).setAction(ACTION_CANCEL)) }
        }
    }
}
