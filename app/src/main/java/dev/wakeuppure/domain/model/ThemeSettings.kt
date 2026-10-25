package dev.wakeuppure.domain.model

enum class ThemeSource { SYSTEM, PRESET }

data class ThemeSettings(
    val source: ThemeSource = ThemeSource.SYSTEM,
    val preset: String = "teal",
    val interfaceScalePercent: Int = InterfaceSize.DEFAULT_PERCENT,
    val showNonCurrentWeekCourses: Boolean = true,
)

object InterfaceSize {
    const val MIN_PERCENT = 60
    const val MAX_PERCENT = 140
    const val DEFAULT_PERCENT = 100
    const val STEP_PERCENT = 5
    fun clamp(percent: Int) = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
}

data class ThemePreset(val id: String, val name: String, val seed: Int)

object ThemePresets {
    val all = listOf(
        ThemePreset("teal", "青苔", 0xFF176B60.toInt()),
        ThemePreset("blue", "晴空", 0xFF3B5BA9.toInt()),
        ThemePreset("violet", "鸢尾", 0xFF7450A0.toInt()),
        ThemePreset("rose", "莓果", 0xFFA43F68.toInt()),
        ThemePreset("amber", "暖杏", 0xFF946023.toInt()),
        ThemePreset("green", "新叶", 0xFF526B30.toInt()),
    )
    fun find(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}
