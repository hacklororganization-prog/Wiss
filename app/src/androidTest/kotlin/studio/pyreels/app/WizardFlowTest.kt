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
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * UI flow: wizard → "Generate professional prompt" → the SAF create-document picker is faked and
 * returns a local file → the prompt file is written with all sections.
 */
@HiltAndroidTest
class WizardFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Before fun setUp() { hilt.inject(); Intents.init() }
    @After fun tearDown() { Intents.release() }

    @Test
    fun generatePromptSavesThroughSaf() {
        val target = File(compose.activity.cacheDir, "prompt_test.md").apply { delete(); createNewFile() }
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(target))),
        )
        compose.onNodeWithText("درس جديد").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("إنشاء البرومبت الاحترافي")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("إنشاء البرومبت الاحترافي").performScrollTo().performClick()
        compose.waitUntil(10_000) { target.length() > 1000 }
        val text = target.readText()
        assertTrue(text.contains("## 1. Role and goal"))
        assertTrue(text.contains("## 12. Spoken-text rule"))
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("تم حفظ البرومبت ✓")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun importSampleAndPreview() {
        compose.onNodeWithText("أمثلة جاهزة").performScrollTo()
        compose.onNodeWithText("print").performClick()
        compose.waitUntil(8_000) { compose.onAllNodes(hasText("أول أمر: print")).fetchSemanticsNodes().isNotEmpty() }
    }
}
