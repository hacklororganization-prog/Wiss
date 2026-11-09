package studio.pyreels.app

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The export path through the real UI: sample → editor → export screen → "Export video" starts the
 * foreground service → the progress card appears → cancel works. Guards against a crash on export.
 */
@HiltAndroidTest
class ExportUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Before fun setUp() {
        hilt.inject()
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission("studio.pyreels.app", Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun waitFor(text: String, ms: Long = 15_000) =
        compose.waitUntil(ms) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun exportStartsAndCancelsWithoutCrash() {
        compose.onNodeWithText("أمثلة جاهزة").performScrollTo()
        compose.onNodeWithText("print").performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription("تصدير")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("تصدير").performClick()
        waitFor("تصدير ونشر")
        waitFor("تصدير الفيديو")
        compose.onNodeWithText("تصدير الفيديو").performScrollTo().performClick()
        waitFor("جارٍ التصدير…", 30_000)
        // let it render real frames for a moment, then cancel
        Thread.sleep(6_000)
        compose.onNodeWithText("إلغاء").performScrollTo().performClick()
        waitFor("أُلغي", 30_000)
    }
}
