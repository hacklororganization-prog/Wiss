package studio.pyreels.app

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * The export path through the real UI: sample → editor → export screen → "Export video" starts the
 * foreground service → the progress card appears → cancel works. Guards against a crash on export.
 */
@HiltAndroidTest
class ExportUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var projects: studio.pyreels.app.data.ProjectRepository

    @Before fun setUp() {
        hilt.inject()
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission("studio.pyreels.app", Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** On timeout the error contains the whole screen (including the export card's status text). */
    private fun waitFor(text: String, ms: Long = 15_000) {
        try {
            compose.waitUntil(ms) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("waiting for «$text». Screen:\n" + compose.onRoot().printToString(40), e)
        }
    }

    @Test
    fun exportStartsAndCancelsWithoutCrash() {
        kotlinx.coroutines.runBlocking { projects.create(TestFixtures.scenario("zig-variables"), null) }
        waitFor("تصدير ونشر")
        compose.onNodeWithText("تصدير ونشر").performClick()
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
