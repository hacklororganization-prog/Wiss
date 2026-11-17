package studio.pyreels.app.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import studio.pyreels.core.audio.Synth

/** Plays one sound effect at its designed level (settings: "listen before you choose"). Never throws. */
class SfxPreviewer {
    private var track: AudioTrack? = null

    @Synchronized
    fun play(id: String) {
        release()
        try {
            val data = Synth.sound(id, 2)
            if (data.isEmpty()) return
            val pcm = ShortArray(data.size) { (data[it] * 0.8f * 32767).toInt().coerceIn(-32768, 32767).toShort() }
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(Synth.SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(pcm, 0, pcm.size)
            t.play()
            track = t
        } catch (_: Throwable) {
            track = null
        }
    }

    @Synchronized
    fun release() {
        track?.let { runCatching { it.stop() }; runCatching { it.release() } }
        track = null
    }
}
