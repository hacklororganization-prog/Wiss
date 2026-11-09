package studio.pyreels.app.engine.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import android.view.Surface
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import studio.pyreels.app.engine.PreparedVideo
import studio.pyreels.app.engine.render.FrameRenderer
import studio.pyreels.core.audio.AudioMixer
import studio.pyreels.core.audio.MixSettings
import studio.pyreels.core.model.Limits
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Deterministic offline exporter: every frame is rendered from the timeline onto the encoder's
 * input Surface (hardware Canvas), encoded as H.264 High; the soundtrack is mixed sample-accurately
 * from the same timeline and encoded as AAC. Presentation times derive from the frame index and
 * the sample count, so audio and video cannot drift. Nothing is screen-recorded.
 */
class VideoExporter(private val context: Context) {

    data class Options(
        val width: Int = Limits.WIDTH,
        val height: Int = Limits.HEIGHT,
        val fps: Int = Limits.FPS,
        val videoBitrate: Int = 8_000_000,
        val audioBitrate: Int = 160_000,
        /** Time ranges of the timeline to export (null = everything). Used for the promo teaser clip. */
        val ranges: List<LongRange>? = null,
        val mix: MixSettings = MixSettings(),
    ) {
        companion object {
            fun standard() = Options()
            fun high() = Options(videoBitrate = 14_000_000, audioBitrate = 192_000)
        }
    }

    data class Verification(
        val width: Int, val height: Int, val durationMs: Long, val fps: Double, val hasAudio: Boolean,
        val audioDurationMs: Long, val ok: Boolean, val problems: List<String>,
    )

    private val sampleRate = 48_000
    private val channels = 2

    suspend fun export(video: PreparedVideo, out: File, options: Options, progress: (Float) -> Unit): Verification {
        val timeline = video.timeline
        val ranges = options.ranges ?: listOf(0L until timeline.totalMs)
        val totalMs = ranges.sumOf { it.last - it.first + 1 }
        val totalFrames = (totalMs * options.fps / 1000).toInt()
        val renderer = FrameRenderer(context, video.scenario).apply { setChapters(timeline) }

        val mixer = AudioMixer(timeline, video.voicePcm, options.mix)
        mixer.prepare()

        out.parentFile?.mkdirs()
        if (out.exists()) out.delete()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vEnc = createVideoEncoder(options)
        val surface: Surface = vEnc.createInputSurface()
        vEnc.start()
        val aEnc = createAudioEncoder(options)
        aEnc.start()

        var vTrack = -1; var aTrack = -1; var muxStarted = false
        val pendingSamples = ArrayList<Triple<Int, ByteBuffer, MediaCodec.BufferInfo>>() // (0=video/1=audio) before mux start
        val info = MediaCodec.BufferInfo()
        var videoOutCount = 0
        var lastOrigPts = -1L
        var reorderDetected = false

        fun writeSample(kind: Int, buf: ByteBuffer, bi: MediaCodec.BufferInfo) {
            if (!muxStarted) {
                val copy = ByteBuffer.allocateDirect(bi.size)
                buf.position(bi.offset); buf.limit(bi.offset + bi.size); copy.put(buf); copy.flip()
                val cbi = MediaCodec.BufferInfo().apply { set(0, bi.size, bi.presentationTimeUs, bi.flags) }
                pendingSamples += Triple(kind, copy, cbi)
                return
            }
            muxer.writeSampleData(if (kind == 0) vTrack else aTrack, buf, bi)
        }

        fun maybeStartMuxer() {
            if (!muxStarted && vTrack >= 0 && aTrack >= 0) {
                muxer.start(); muxStarted = true
                for ((k, b, bi) in pendingSamples) muxer.writeSampleData(if (k == 0) vTrack else aTrack, b, bi)
                pendingSamples.clear()
            }
        }

        fun drainVideo(endOfStream: Boolean) {
            var waits = 0
            while (true) {
                val idx = vEnc.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || ++waits > 500) return
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { vTrack = muxer.addTrack(vEnc.outputFormat); maybeStartMuxer() }
                    idx >= 0 -> {
                        val buf = vEnc.getOutputBuffer(idx)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0) {
                            if (lastOrigPts >= 0 && info.presentationTimeUs < lastOrigPts) reorderDetected = true
                            lastOrigPts = info.presentationTimeUs
                            // deterministic PTS from the frame index (encoder emits frames in order: no B-frames)
                            info.presentationTimeUs = videoOutCount * 1_000_000L / options.fps
                            videoOutCount++
                            writeSample(0, buf, info)
                        }
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        vEnc.releaseOutputBuffer(idx, false)
                        if (eos) return
                    }
                }
            }
        }

        var audioSamplesFed = 0L
        val audioTotal = totalMs * sampleRate / 1000
        val mono = FloatArray(1024)
        var rangeIdx = 0
        var rangePos = ranges[0].first * sampleRate / 1000
        mixer.seek(rangePos)
        var audioEosSent = false

        fun feedAudio(untilSample: Long) {
            while (!audioEosSent && audioSamplesFed < untilSample) {
                val inIdx = aEnc.dequeueInputBuffer(0)
                if (inIdx < 0) return
                val inBuf = aEnc.getInputBuffer(inIdx)!!
                inBuf.clear()
                val maxFrames = min(mono.size, inBuf.capacity() / (2 * channels))
                var want = min(maxFrames.toLong(), audioTotal - audioSamplesFed).toInt()
                if (want <= 0) {
                    aEnc.queueInputBuffer(inIdx, 0, 0, audioSamplesFed * 1_000_000L / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    audioEosSent = true; return
                }
                // stay within the current range
                val rangeEnd = (ranges[rangeIdx].last + 1) * sampleRate / 1000
                want = min(want.toLong(), rangeEnd - rangePos).toInt().coerceAtLeast(0)
                if (want == 0) {
                    rangeIdx = min(rangeIdx + 1, ranges.lastIndex)
                    rangePos = ranges[rangeIdx].first * sampleRate / 1000
                    mixer.seek(rangePos)
                    aEnc.queueInputBuffer(inIdx, 0, 0, audioSamplesFed * 1_000_000L / sampleRate, 0)
                    continue
                }
                val got = mixer.read(mono, want)
                val sb = inBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val fadeLen = sampleRate / 100 // 10 ms fades at range joins avoid clicks
                for (i in 0 until got) {
                    var s = mono[i]
                    if (ranges.size > 1) {
                        val fromStart = rangePos + i - ranges[rangeIdx].first * sampleRate / 1000
                        val toEnd = rangeEnd - (rangePos + i)
                        if (fromStart < fadeLen) s *= fromStart.toFloat() / fadeLen
                        if (toEnd < fadeLen) s *= toEnd.toFloat() / fadeLen
                    }
                    val v = (s.coerceIn(-1f, 1f) * 32767).toInt().toShort()
                    sb.put(v); sb.put(v)
                }
                aEnc.queueInputBuffer(inIdx, 0, got * 2 * channels, audioSamplesFed * 1_000_000L / sampleRate, 0)
                audioSamplesFed += got
                rangePos += got
            }
        }

        fun drainAudio(endOfStream: Boolean) {
            var waits = 0
            while (true) {
                val idx = aEnc.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || !audioEosSent || ++waits > 500) return
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { aTrack = muxer.addTrack(aEnc.outputFormat); maybeStartMuxer() }
                    idx >= 0 -> {
                        val buf = aEnc.getOutputBuffer(idx)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0) writeSample(1, buf, info)
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        aEnc.releaseOutputBuffer(idx, false)
                        if (eos) return
                    }
                }
            }
        }

        try {
            var frame = 0
            for (range in ranges) {
                val rangeFrames = ((range.last - range.first + 1) * options.fps / 1000).toInt()
                for (k in 0 until rangeFrames) {
                    currentCoroutineContext().ensureActive()
                    val t = range.first + k * 1000L / options.fps
                    val canvas = if (Build.VERSION.SDK_INT >= 23) surface.lockHardwareCanvas() else surface.lockCanvas(null)
                    try {
                        canvas.save()
                        canvas.scale(options.width / Limits.WIDTH.toFloat(), options.height / Limits.HEIGHT.toFloat())
                        renderer.draw(canvas, timeline.frameAt(t))
                        canvas.restore()
                    } finally {
                        surface.unlockCanvasAndPost(canvas)
                    }
                    frame++
                    // keep the encoder pipeline short (memory) and in lock-step with the audio
                    var spins = 0
                    while (frame - videoOutCount > 6 && spins < 400) { drainVideo(false); if (frame - videoOutCount > 6) Thread.sleep(2); spins++ }
                    drainVideo(false)
                    feedAudio(frame.toLong() * sampleRate / options.fps)
                    drainAudio(false)
                    if (frame % 15 == 0) progress(frame.toFloat() / totalFrames)
                }
            }
            vEnc.signalEndOfInputStream()
            drainVideo(true)
            var guard = 0
            while (!audioEosSent && guard++ < 10_000) { feedAudio(audioTotal + 1); drainAudio(false) }
            drainAudio(true)
            progress(1f)
        } finally {
            runCatching { vEnc.stop() }; runCatching { vEnc.release() }
            runCatching { aEnc.stop() }; runCatching { aEnc.release() }
            runCatching { surface.release() }
            runCatching { if (muxStarted) muxer.stop() }; runCatching { muxer.release() }
        }
        val v = verify(out, options, totalMs)
        return if (reorderDetected) v.copy(ok = false, problems = v.problems + "encoder reordered frames") else v
    }

    private fun createVideoEncoder(o: Options): MediaCodec {
        val mime = MediaFormat.MIMETYPE_VIDEO_AVC
        val format = MediaFormat.createVideoFormat(mime, o.width, o.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, o.videoBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, o.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            if (Build.VERSION.SDK_INT >= 29) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
        }
        val codecName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
        val codec = if (codecName != null) MediaCodec.createByCodecName(codecName) else MediaCodec.createEncoderByType(mime)
        val caps = codec.codecInfo.getCapabilitiesForType(mime)
        val high = caps.profileLevels.firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh }
        if (high != null) {
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
            format.setInteger(MediaFormat.KEY_LEVEL, min(high.level, MediaCodecInfo.CodecProfileLevel.AVCLevel41).coerceAtLeast(MediaCodecInfo.CodecProfileLevel.AVCLevel4))
        }
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            // Some encoders reject explicit profile/level: fall back to the encoder's defaults.
            format.removeKeyCompat(MediaFormat.KEY_PROFILE); format.removeKeyCompat(MediaFormat.KEY_LEVEL)
            codec.reset()
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        return codec
    }

    private fun MediaFormat.removeKeyCompat(key: String) {
        if (Build.VERSION.SDK_INT >= 29) removeKey(key) else setInteger(key, 0)
    }

    private fun createAudioEncoder(o: Options): MediaCodec {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, o.audioBitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 2 * channels * 4)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return codec
    }

    /** Verifies resolution, fps, duration and audio/video sync of the written file. */
    fun verify(file: File, o: Options, expectedMs: Long): Verification {
        val problems = ArrayList<String>()
        val r = MediaMetadataRetriever()
        var w = 0; var h = 0; var dur = 0L; var fps = 0.0; var hasAudio = false
        try {
            r.setDataSource(file.absolutePath)
            w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            if (Build.VERSION.SDK_INT >= 28) {
                val frames = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull() ?: 0
                if (dur > 0 && frames > 0) fps = frames * 1000.0 / dur
            }
        } catch (e: Exception) {
            problems += "metadata: ${e.message}"
        } finally { runCatching { r.release() } }
        var audioDur = 0L
        var videoDur = 0L
        runCatching {
            val ex = MediaExtractor(); ex.setDataSource(file.absolutePath)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val d = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) / 1000 else 0
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) audioDur = d else videoDur = d
                if (fps == 0.0 && f.containsKey(MediaFormat.KEY_FRAME_RATE)) fps = f.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
            }
            ex.release()
        }
        if (w != o.width || h != o.height) problems += "resolution ${w}x$h"
        if (kotlin.math.abs(dur - expectedMs) > 250) problems += "duration $dur ms (expected $expectedMs)"
        if (fps > 0 && kotlin.math.abs(fps - o.fps) > 1.0) problems += "fps ${"%.2f".format(fps)}"
        if (!hasAudio) problems += "no audio track"
        if (videoDur > 0 && audioDur > 0 && kotlin.math.abs(videoDur - audioDur) > 120) problems += "audio/video length differ by ${kotlin.math.abs(videoDur - audioDur)} ms"
        if (dur > Limits.HARD_CAP_SECONDS * 1000L + 500) problems += "longer than ${Limits.HARD_CAP_SECONDS} s"
        return Verification(w, h, dur, fps, hasAudio, audioDur, problems.isEmpty(), problems)
    }
}
