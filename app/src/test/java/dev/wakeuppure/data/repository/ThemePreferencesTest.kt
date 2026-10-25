package dev.wakeuppure.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.wakeuppure.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ThemePreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() { context.getSharedPreferences("preferences", 0).edit().clear().commit() }

    @Test fun defaultsPreserveExistingSystemColors() {
        assertEquals(ThemeSettings(), ThemePreferences(context).read())
    }

    @Test fun presetAndDynamicChoiceSurviveRecreation() {
        val settings = ThemeSettings(ThemeSource.PRESET, "rose")
        ThemePreferences(context).save(settings)
        assertEquals(settings, ThemePreferences(context).read())
        ThemePreferences(context).save(settings.copy(source = ThemeSource.SYSTEM))
        assertEquals(settings.copy(source = ThemeSource.SYSTEM), ThemePreferences(context).read())
    }

    @Test fun invalidStoredKeysHaveSafeDefaults() {
        context.getSharedPreferences("preferences", 0).edit().putString("theme_source", "invalid").putString("theme_preset", "unknown").commit()
        assertEquals(ThemeSettings(), ThemePreferences(context).read())
    }

    @Test fun interfaceSizeSurvivesRecreationAndColorChanges() {
        val preferences = ThemePreferences(context)
        preferences.save(ThemeSettings(interfaceScalePercent = 75))
        assertEquals(75, ThemePreferences(context).read().interfaceScalePercent)
        preferences.save(preferences.read().copy(source = ThemeSource.PRESET, preset = "blue"))
        assertEquals(75, ThemePreferences(context).read().interfaceScalePercent)
        preferences.save(preferences.read().copy(interfaceScalePercent = InterfaceSize.DEFAULT_PERCENT))
        assertEquals(100, ThemePreferences(context).read().interfaceScalePercent)
    }

    @Test fun invalidStoredSizeIsClampedToSupportedRange() {
        val storage = context.getSharedPreferences("preferences", 0)
        storage.edit().putInt("interface_scale_percent", 0).commit()
        assertEquals(InterfaceSize.MIN_PERCENT, ThemePreferences(context).read().interfaceScalePercent)
        storage.edit().putInt("interface_scale_percent", 1000).commit()
        assertEquals(InterfaceSize.MAX_PERCENT, ThemePreferences(context).read().interfaceScalePercent)
    }

    @Test fun hidingNonCurrentWeekCoursesSurvivesRecreationAndOtherVisualChanges() {
        val preferences = ThemePreferences(context)
        assertTrue(preferences.read().showNonCurrentWeekCourses)
        preferences.save(preferences.read().copy(showNonCurrentWeekCourses = false))
        val recreated = ThemePreferences(context)
        assertFalse(recreated.read().showNonCurrentWeekCourses)
        recreated.save(recreated.read().copy(preset = "blue", interfaceScalePercent = 90))
        assertFalse(ThemePreferences(context).read().showNonCurrentWeekCourses)
    }
}
