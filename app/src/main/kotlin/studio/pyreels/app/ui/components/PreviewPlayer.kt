package studio.pyreels.app.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import studio.pyreels.app.engine.PreparedVideo
import studio.pyreels.core.audio.AudioMixer
import kotlin.concurrent.thread

/**
 * Preview playback. The audio track (rendered by the same streaming mixer as the export) is the
 * master clock, so the preview's picture and sound are in sync. Falls back to the system clock.
 */
class PreviewPlayer {
    @Volatile private var track: AudioTrack? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var running = false
    private var startMs = 0L
    private var startNanos = 0L
    private var writtenFrames = 0L

    fun play(video: PreparedVideo, fromMs: Long, withAudio: Boolean) {
        stop()
        startMs = fromMs
        startNanos = System.nanoTime()
        running = true
        if (!withAudio) return
        try {
            val rate = 48_000
            val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBuf * 2, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = t
            writtenFrames = 0
            worker = thread(name = "preview-audio", isDaemon = true) {
                val mixer = AudioMixer(video.timeline, video.voicePcm)
                mixer.prepare()
                mixer.seek(fromMs * rate / 1000)
                val f = FloatArray(2048)
                val s = ShortArray(2048)
                t.play()
                startNanos = System.nanoTime()
                while (running) {
                    val n = mixer.read(f)
                    if (n <= 0) break
                    for (i in 0 until n) s[i] = (f[i] * 32767).toInt().coerceIn(-32768, 32767).toShort()
                    val w = t.write(s, 0, n)
                    if (w < 0) break
                    writtenFrames += w
                }
            }
        } catch (_: Throwable) {
            track = null
        }
    }

    /** Current preview time: from the audio head position when audio plays, else the system clock. */
    fun positionMs(): Long {
        val t = track
        if (t != null && t.playState == AudioTrack.PLAYSTATE_PLAYING) {
            val head = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            if (head > 0) return startMs + head * 1000 / 48_000
        }
        return startMs + (System.nanoTime() - startNanos) / 1_000_000
    }

    fun stop() {
        running = false
        worker?.let { runCatching { it.join(300) } }
        worker = null
        track?.let { runCatching { it.pause(); it.flush(); it.release() } }
        track = null
    }
}
