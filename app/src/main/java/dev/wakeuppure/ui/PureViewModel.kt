package dev.wakeuppure.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.wakeuppure.PureApp
import dev.wakeuppure.data.repository.ScheduleRepository
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.data.wakeup.json.WakeUpJsonImporter
import dev.wakeuppure.data.wakeup.legacy.LegacyWakeUpImporter
import dev.wakeuppure.data.wakeup.token.*
import dev.wakeuppure.data.wakeup.WakeUpShareRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class PureViewModel(application: Application, val repository: ScheduleRepository) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, (application as PureApp).repository)
    private val stored = repository.schedules.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private class Edit(val change: (Schedule) -> Schedule)
    // Quick setting changes shown on top of the stored data until the database reports them.
    private val pendingEdits = MutableStateFlow<Map<Long, List<Edit>>>(emptyMap())
    val schedules = combine(stored, pendingEdits) { list, edits ->
        if (edits.isEmpty()) list else list.map { data ->
            edits[data.schedule.id]?.let { pending -> data.copy(schedule = pending.fold(data.schedule) { s, edit -> edit.change(s) }) } ?: data
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    // Every database write runs one at a time in call order: viewModelScope starts coroutines on the
    // main thread right away, so the fair mutex queues them as they were requested.
    private val writes = Mutex()
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
        try { writes.withLock { withContext(Dispatchers.IO) { block() } } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = if (e is IllegalArgumentException && e.message.orEmpty().startsWith("ICS 导入失败：")) e.message
            else if (e is IllegalArgumentException) "数据不完整或超出允许范围，请检查日期、周次、节次和作息时间。" else "操作未完成，请重试。原有课表未被覆盖。" }
        finally { busy.value = false }
    } }
    /**
     * Switches and quick sheets: the change shows immediately and is applied to the latest stored
     * schedule in the background. It never sets [busy], which would briefly disable every control on
     * the page and make neighbouring rows flash.
     */
    fun updateSchedule(id: Long, change: (Schedule) -> Schedule) {
        val edit = Edit(change)
        pendingEdits.update { it + (id to it[id].orEmpty() + edit) }
        viewModelScope.launch {
            try {
                writes.withLock {
                    withContext(Dispatchers.IO) {
                        val current = repository.snapshot().firstOrNull { it.schedule.id == id } ?: return@withContext
                        repository.saveSchedule(change(current.schedule).copy(updatedAt = System.currentTimeMillis()), current.timeSlots)
                    }
                }
                // Keep showing the edit until the observed data includes it, so the control never
                // flips back to its old value in between.
                withTimeoutOrNull(2_000) {
                    stored.first { list -> list.find { it.schedule.id == id }?.schedule?.let { sameSettings(change(it), it) } ?: true }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error.value = "设置保存失败，请重试。"
            } finally {
                pendingEdits.update { edits ->
                    val rest = edits[id].orEmpty().filterNot { it === edit }
                    if (rest.isEmpty()) edits - id else edits + (id to rest)
                }
            }
        }
    }

    private fun sameSettings(a: Schedule, b: Schedule) = a.copy(updatedAt = 0) == b.copy(updatedAt = 0)

    internal fun hasPendingEdits() = pendingEdits.value.isNotEmpty()

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
