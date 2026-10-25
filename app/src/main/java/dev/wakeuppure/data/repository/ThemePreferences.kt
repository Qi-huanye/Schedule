package dev.wakeuppure.data.repository

import android.content.Context
import dev.wakeuppure.domain.model.*

/** Device-local visual preferences, independent of schedules and portable backups. */
class ThemePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("preferences", 0)
    fun read() = ThemeSettings(
        source = runCatching { ThemeSource.valueOf(preferences.getString("theme_source", null).orEmpty()) }.getOrDefault(ThemeSource.SYSTEM),
        preset = ThemePresets.find(preferences.getString("theme_preset", null).orEmpty()).id,
        interfaceScalePercent = InterfaceSize.clamp(preferences.getInt("interface_scale_percent", InterfaceSize.DEFAULT_PERCENT)),
    )
    fun save(settings: ThemeSettings) {
        preferences.edit().putString("theme_source", settings.source.name)
            .putString("theme_preset", ThemePresets.find(settings.preset).id)
            .putInt("interface_scale_percent", InterfaceSize.clamp(settings.interfaceScalePercent)).apply()
    }
}
