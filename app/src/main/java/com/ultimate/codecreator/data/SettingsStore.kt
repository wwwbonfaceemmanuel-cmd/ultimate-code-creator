package com.ultimate.codecreator.data

import android.content.SharedPreferences
import com.ultimate.codecreator.model.AppSettings
import com.ultimate.codecreator.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SettingsStore {
    val settings: StateFlow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

class PreferencesSettingsStore(private val prefs: SharedPreferences) : SettingsStore {
    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value).validated()
        _settings.value = updated
        prefs.edit()
            .putString(KEY_THEME, updated.themeMode.name)
            .putBoolean(KEY_PREVIEW_JS, updated.previewJavaScriptEnabled)
            .putInt(KEY_FONT_SIZE, updated.editorFontSizeSp)
            .putBoolean(KEY_WORD_WRAP, updated.editorWordWrap)
            .apply()
    }

    private fun read(): AppSettings = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM),
        previewJavaScriptEnabled = prefs.getBoolean(KEY_PREVIEW_JS, false),
        editorFontSizeSp = prefs.getInt(KEY_FONT_SIZE, 13),
        editorWordWrap = prefs.getBoolean(KEY_WORD_WRAP, true)
    ).validated()

    private fun AppSettings.validated() = copy(editorFontSizeSp = editorFontSizeSp.coerceIn(10, 22))

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_PREVIEW_JS = "preview_js_enabled"
        private const val KEY_FONT_SIZE = "editor_font_size"
        private const val KEY_WORD_WRAP = "editor_word_wrap"
    }
}
