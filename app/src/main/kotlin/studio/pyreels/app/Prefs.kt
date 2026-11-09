package studio.pyreels.app

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** UI preferences (per device). */
@Singleton
class Prefs @Inject constructor(@ApplicationContext context: Context) {
    private val sp = context.getSharedPreferences("pyreels", Context.MODE_PRIVATE)

    private val _dark = MutableStateFlow(sp.getBoolean("dark", true))
    private val _english = MutableStateFlow(sp.getBoolean("english", false))
    private val _fontScale = MutableStateFlow(sp.getFloat("fontScale", 1f))
    private val _safeZones = MutableStateFlow(sp.getBoolean("safeZones", false))
    val dark: StateFlow<Boolean> = _dark
    val english: StateFlow<Boolean> = _english
    val fontScale: StateFlow<Float> = _fontScale
    val safeZones: StateFlow<Boolean> = _safeZones

    fun setDark(v: Boolean) { _dark.value = v; sp.edit().putBoolean("dark", v).apply() }
    fun setEnglish(v: Boolean) { _english.value = v; sp.edit().putBoolean("english", v).apply() }
    fun setFontScale(v: Float) { _fontScale.value = v; sp.edit().putFloat("fontScale", v).apply() }
    fun setSafeZones(v: Boolean) { _safeZones.value = v; sp.edit().putBoolean("safeZones", v).apply() }
}
