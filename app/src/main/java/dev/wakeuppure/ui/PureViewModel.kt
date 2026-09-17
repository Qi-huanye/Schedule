package dev.wakeuppure.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.wakeuppure.PureApp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.data.wakeup.json.WakeUpJsonImporter
import dev.wakeuppure.data.wakeup.legacy.LegacyWakeUpImporter
import dev.wakeuppure.data.wakeup.token.*
import dev.wakeuppure.data.wakeup.WakeUpShareRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class PureViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as PureApp).repository
    val schedules = repository.schedules.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val imported = MutableStateFlow<List<ScheduleData>?>(null)
    private var pendingAppearance: String? = null
    private val prefs = application.getSharedPreferences("preferences", 0)
    val appearance = MutableStateFlow(prefs.getString("appearance", "system") ?: "system")
    val experimentalToken = MutableStateFlow(prefs.getBoolean("experimentalToken", false))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val shares = WakeUpShareRepository(WakeUpTokenImporter(AndroidDeviceIdentityProvider(application)))

    fun setAppearance(value: String) { appearance.value = value; prefs.edit().putString("appearance", value).apply() }
    fun setExperimentalToken(value: Boolean) {
        experimentalToken.value = value
        prefs.edit().putBoolean("experimentalToken", value).apply()
        clearImport()
    }
    fun action(block: suspend () -> Unit) { viewModelScope.launch {
        busy.value = true
        try { withContext(Dispatchers.IO) { block() } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = if (e is IllegalArgumentException && e.message.orEmpty().startsWith("ICS 导入失败：")) e.message
            else if (e is IllegalArgumentException) "数据不完整或超出允许范围，请检查日期、周次、节次和作息时间。" else "操作未完成，请重试。原有课表未被覆盖。" }
        finally { busy.value = false }
    } }
    fun saveSchedule(schedule: Schedule, slots: List<TimeSlot>, preserveDates: Boolean = false, weekOffset: Int = 0, done: () -> Unit) = action {
        repository.saveSchedule(schedule, slots, preserveDates, weekOffset); withContext(Dispatchers.Main) { done() }
    }
    fun saveCourse(course: Course, periods: List<CoursePeriod>, slots: List<TimeSlot>, done: () -> Unit) = action {
        repository.saveCourse(course, periods, slots); withContext(Dispatchers.Main) { done() }
    }
    fun deleteCourse(id: Long) = action { repository.deleteCourse(id) }
    fun select(id: Long) = action { repository.selectSchedule(id) }
    fun deleteSchedule(id: Long) = action { repository.deleteSchedule(id) }
    fun clearImport() { imported.value = null; pendingAppearance = null }
    fun parseImport(text: String) = action {
        imported.value = null
        pendingAppearance = null
        val trim = text.trim()
        val root = runCatching { json.parseToJsonElement(trim) as? JsonObject }.getOrNull()
        imported.value = if (root?.get("format")?.jsonPrimitive?.content == "WakeUpPure") {
            val backup = dev.wakeuppure.data.export.ScheduleExporter.restore(trim)
            require(backup.format == "WakeUpPure" && backup.version == 1)
            pendingAppearance = backup.appearance.takeIf { it in listOf("system", "light", "dark") }
            backup.schedules
        } else listOf(shares.parseLocal(trim))
    }
    fun acceptImport(done: () -> Unit) = action {
        val data = requireNotNull(imported.value)
        require(data.isNotEmpty())
        repository.importSchedules(if (data.none { it.schedule.current }) data.mapIndexed { i, d -> d.copy(schedule = d.schedule.copy(current = i == 0)) } else data)
        pendingAppearance?.let { setAppearance(it) }
        imported.value = null
        withContext(Dispatchers.Main) { done() }
    }
    fun saveProfile(text: String) = action {
        val profile = json.decodeFromString<WakeUpProtocolProfile>(text)
        profile.validate()
        prefs.edit().putString("protocolProfile", text).apply()
    }
    fun importToken(code: String) { viewModelScope.launch {
        imported.value = null
        pendingAppearance = null
        busy.value = true
        try {
            val experimental = experimentalToken.value
            val profile = WakeUpProtocolProfiles.resolve(prefs.getString("protocolProfile", null), experimental)
            imported.value = listOf(shares.importToken(code, profile,
                if (experimental) WakeUpIdentityMode.EXPERIMENTAL else WakeUpIdentityMode.DEVICE))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message?.takeIf { it.any { c -> c.code > 127 } }
            ?: "无法解析该 WakeUp 分享口令。可能是口令已失效或分享协议发生变化。" }
        finally { busy.value = false }
    } }
}
