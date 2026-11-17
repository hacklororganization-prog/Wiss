package studio.pyreels.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.pyreels.app.data.ProjectRepository
import studio.pyreels.app.data.TeacherStore
import java.io.File
import javax.inject.Inject

/**
 * UI flow with a language the app has never heard of: the creator adds a teacher (a language name),
 * writes a topic, generates the prompt (the SAF create-document picker is faked and returns a
 * local file) — and nothing is pre-filled or built in.
 */
@HiltAndroidTest
class WizardFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var teachers: TeacherStore
    @Inject lateinit var projects: ProjectRepository

    @Before fun setUp() { hilt.inject(); Intents.init() }
    @After fun tearDown() { Intents.release() }

    @Test
    fun startsEmptyWithoutAnyBuiltInContent() {
        assertTrue("no built-in languages", teachers.teachers.value.isEmpty())
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("ابدأ بإضافة لغتك الأولى")).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(compose.onAllNodes(hasText("أمثلة جاهزة")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun generatePromptForAnyLanguageSavesThroughSaf() {
        teachers.upsert(null, "Zig", "")
        val target = File(compose.activity.cacheDir, "prompt_test.md").apply { delete(); createNewFile() }
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(target))),
        )
        compose.onNodeWithText("درس جديد").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("موضوع الدرس (اكتبه بحرية)")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("موضوع الدرس (اكتبه بحرية)").performTextInput("المتغيرات")
        compose.onNodeWithText("Zig").performScrollTo().performClick()
        compose.onNodeWithText("إنشاء البرومبت الاحترافي").performScrollTo().performClick()
        compose.waitUntil(10_000) { target.length() > 1000 }
        val text = target.readText()
        assertTrue(text.contains("## 1. Role and goal"))
        assertTrue(text.contains("## 12. Spoken-text rule"))
        assertTrue("teacher id and name are in the prompt", text.contains("`zig` = Zig"))
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("تم حفظ البرومبت ✓")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun openAProjectInTheEditor() {
        runBlocking { projects.create(TestFixtures.scenario("zig-variables"), null) }
        compose.waitUntil(8_000) { compose.onAllNodes(hasText("متابعة التحرير")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("متابعة التحرير").performClick()
        compose.waitUntil(8_000) { compose.onAllNodes(hasText("مشهد", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }
}
