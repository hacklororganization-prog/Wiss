package studio.pyreels.app

import androidx.test.platform.app.InstrumentationRegistry
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.validate.ScenarioValidator

/** Test-only scenarios (assets of the androidTest APK; the app itself ships none). */
object TestFixtures {
    fun text(name: String): String =
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/$name.json").use { it.readBytes().toString(Charsets.UTF_8) }

    fun scenario(name: String): Scenario = ScenarioValidator.parse(text(name)).scenarios.single()
}
