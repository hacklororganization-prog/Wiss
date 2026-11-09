package studio.pyreels.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import studio.pyreels.app.engine.render.FrameRenderer
import studio.pyreels.core.Resources
import studio.pyreels.core.model.typeId
import studio.pyreels.core.timeline.Timeline
import studio.pyreels.core.validate.ScenarioValidator
import java.io.File

/**
 * Renders representative frames (every scene type + cover) to JPEG files so CI can publish them
 * for visual review. Files: <app external files>/snapshots/.
 */
@RunWith(AndroidJUnit4::class)
class RenderSnapshotsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun renderSnapshots() {
        val dir = File(context.filesDir, "snapshots").apply { deleteRecursively(); mkdirs() }
        var count = 0
        for ((sample, prefix) in listOf("samples/02-variables.pyreels.json" to "a", "samples/03-if.pyreels.json" to "b", "samples/01-print.pyreels.json" to "c")) {
            val s = ScenarioValidator.parse(Resources.text(sample)).scenarios.single()
            val tl = Timeline.build(s)
            val r = FrameRenderer(context, s).apply { setChapters(tl) }
            val bmp = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            fun save(name: String) {
                File(dir, "$prefix-$name.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 78, it) }
                count++
            }
            val seen = HashSet<String>()
            for (ts in tl.scenes) {
                val type = ts.scene.typeId()
                val key = if (type == "chat") "chat${ts.index}" else type
                if (prefix == "b" && type != "chat" && type != "code_type") continue
                if (prefix == "c" && !(type == "chat" && (ts.scene as studio.pyreels.core.model.Scene.Chat).code != null)) continue
                if (!seen.add(key) && type != "chat") continue
                if (type == "chat" && seen.count { it.startsWith("chat") } > 3) continue
                for (frac in if (type == "code_type" || type == "run" || type == "common_mistake") listOf(0.45f, 0.92f) else listOf(0.85f)) {
                    c.save(); c.scale(0.5f, 0.5f)
                    r.draw(c, tl.frameAt(ts.startMs + (ts.durationMs * frac).toLong()))
                    c.restore()
                    save("%02d-%s-%d".format(ts.index, type, (frac * 100).toInt()))
                }
            }
            if (prefix == "a") {
                // Ahmed typing in the input bar
                val ahmed = tl.scenes.first { it.scene is studio.pyreels.core.model.Scene.Chat && (it.scene as studio.pyreels.core.model.Scene.Chat).from == "ahmed" && it.index > 3 }
                c.save(); c.scale(0.5f, 0.5f); r.draw(c, tl.frameAt(ahmed.startMs + 600)); c.restore(); save("compose")
                c.save(); c.scale(0.5f, 0.5f); r.drawCover(c); c.restore(); save("cover")
                // animation midpoints: bubble entrance, gliding run band, card reveal, hook line stagger
                fun at(name: String, t: Long) { c.save(); c.scale(0.5f, 0.5f); r.draw(c, tl.frameAt(t)); c.restore(); save(name) }
                val firstTeacher = tl.scenes.first { it.scene is studio.pyreels.core.model.Scene.Chat && (it.scene as studio.pyreels.core.model.Scene.Chat).from != "ahmed" }
                val appearAt = tl.sfx.first { it.timeMs >= firstTeacher.startMs && it.id == "msg_recv" }.timeMs
                at("anim-bubble", appearAt + 150)
                val run = tl.scenes.first { it.scene is studio.pyreels.core.model.Scene.Run }
                at("anim-run", run.startMs + run.durationMs / 3)
                val key = tl.scenes.first { it.scene is studio.pyreels.core.model.Scene.KeySentence }
                at("anim-card", key.startMs + 550)
                at("anim-hook", 420)
                val eng = tl.scenes.first { it.scene is studio.pyreels.core.model.Scene.EngageMoment }
                at("anim-engage", eng.startMs + 2_600)
                at("start-chat", tl.scenes[2].startMs + tl.scenes[2].durationMs - 200)
                val win = tl.scenes[Timeline.celebrationIndex(s)]
                at("sticker", win.endMs - 500)
                // large frame + blue chat colour variant
                val v = s.copy(meta = s.meta.copy(frame = "large", chatColor = "blue"))
                val tv = Timeline.build(v)
                val rv = FrameRenderer(context, v).apply { setChapters(tv) }
                c.save(); c.scale(0.5f, 0.5f); rv.draw(c, tv.frameAt(win.endMs - 300)); c.restore(); save("large-blue")
            }
        }
        assertTrue(count > 10)
    }
}
