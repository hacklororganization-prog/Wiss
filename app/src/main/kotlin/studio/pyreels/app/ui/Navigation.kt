package studio.pyreels.app.ui

import androidx.compose.runtime.mutableStateListOf

/** Screens of the app. A tiny back-stack keeps the APK small (no navigation library). */
sealed interface Route {
    data object Home : Route
    data class Wizard(val lessonId: String? = null, val presetId: String? = null) : Route
    data class Import(val text: String? = null) : Route
    data class Editor(val projectId: Long) : Route
    data class Preview(val projectId: Long) : Route
    data class Export(val projectId: Long) : Route
    data object Weekly : Route
    data object Series : Route
    data object Settings : Route
}

class Navigator {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current: Route get() = stack.last()
    fun go(r: Route) { stack.add(r) }
    fun replace(r: Route) { if (stack.size > 1) stack.removeAt(stack.lastIndex); stack.add(r) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
}
