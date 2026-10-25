package dev.wakeuppure.data.local

import android.content.Context
import android.util.AtomicFile
import dev.wakeuppure.domain.model.ScheduleData
import dev.wakeuppure.domain.usecase.TimetableLayout
import dev.wakeuppure.domain.usecase.TimetableWeekLayout
import dev.wakeuppure.domain.usecase.WeekCalculator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.CRC32

/** A projection of one exact source snapshot. Accessing a warmed week performs no disk IO. */
class PreparedTimetable internal constructor(
    val data: ScheduleData,
    private val load: suspend (Int) -> TimetableWeekLayout?,
    private val save: (TimetableWeekLayout) -> Unit,
) {
    private val loading = Array(data.schedule.maxWeeks + 1) { Mutex() }
    private val weeks = object : LinkedHashMap<Int, TimetableWeekLayout>(12, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, TimetableWeekLayout>?) = size > 12
    }

    internal fun cached(week: Int): TimetableWeekLayout? = synchronized(weeks) { weeks[week] }

    internal fun remember(layout: TimetableWeekLayout): TimetableWeekLayout = synchronized(weeks) {
        weeks.getOrPut(layout.week) { layout }
    }

    fun week(week: Int): TimetableWeekLayout = cached(week)
        ?: remember(TimetableLayout.calculate(data, week)).also(save)

    /** Used by the pager: a cache miss must never calculate a dense week during a UI frame. */
    suspend fun prepareWeek(week: Int): TimetableWeekLayout = withContext(Dispatchers.Default) {
        require(week in 1..data.schedule.maxWeeks)
        cached(week) ?: loading[week].withLock {
            cached(week) ?: (load(week)?.let(::remember)
                ?: remember(TimetableLayout.calculate(data, week)).also(save))
        }
    }

    /** Read ahead of pager composition, with the requested week first. */
    suspend fun prepareAround(week: Int, radius: Int = 1) = withContext(Dispatchers.Default) {
        val current = week.coerceIn(1, data.schedule.maxWeeks)
        // Prioritise the requested page instead of blocking it behind the oldest neighbour.
        for (target in (maxOf(1, current - radius)..minOf(data.schedule.maxWeeks, current + radius))
            .sortedBy { kotlin.math.abs(it - current) }) {
            ensureActive()
            if (cached(target) == null) prepareWeek(target)
        }
    }
}

/** Disposable projections only: the database and ScheduleCache remain the source of course data. */
class TimetableCache(context: Context) {
    private val directory = File(context.noBackupFilesDir, "timetable-layouts")
    private val codec = Json { encodeDefaults = true }
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sources = object : LinkedHashMap<String, PreparedTimetable>(3, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PreparedTimetable>?) = size > 3
    }

    suspend fun prepare(data: ScheduleData, date: LocalDate): PreparedTimetable = withContext(Dispatchers.Default) {
        // Full content, not updatedAt: editing a course does not necessarily update its schedule row.
        val key = MessageDigest.getInstance("SHA-256").digest(codec.encodeToString(data).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val prepared = synchronized(sources) {
            sources.getOrPut(key) {
                PreparedTimetable(data,
                    load = { week -> withContext(Dispatchers.IO) { writes.withLock { read(key, week) } } },
                    save = { layout -> persist(key, layout) })
            }
        }
        val current = WeekCalculator.week(data.schedule.semesterStartDate, date).coerceIn(1, data.schedule.maxWeeks)
        // Only the visible week gates startup. The pager prefetches neighbours after first draw.
        prepared.prepareWeek(current)
        prepared
    }

    private fun file(key: String, week: Int) = AtomicFile(File(directory, "$key-$week.week"))

    private fun read(key: String, week: Int): TimetableWeekLayout? = runCatching {
        val entry = file(key, week)
        require(entry.baseFile.length() in 1..MAX_BYTES + 256)
        DataInputStream(entry.openRead().buffered()).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION)
            require(input.readUTF() == key && input.readInt() == week)
            val length = input.readInt()
            require(length in 1..MAX_BYTES)
            val checksum = input.readLong()
            val bytes = ByteArray(length).also(input::readFully)
            require(input.read() == -1 && CRC32().apply { update(bytes) }.value == checksum)
            codec.decodeFromString<TimetableWeekLayout>(bytes.toString(Charsets.UTF_8))
                .also { require(it.week == week && it.days.isNotEmpty() && it.slots.isNotEmpty()) }
        }
    }.getOrNull()

    private fun persist(key: String, layout: TimetableWeekLayout) {
        writer.launch {
            writes.withLock {
                // Cache failures must never fail an edit or prevent opening the real timetable.
                runCatching {
                    val bytes = codec.encodeToString(layout).toByteArray()
                    if (bytes.size > MAX_BYTES) return@runCatching
                    if (!directory.isDirectory && !directory.mkdirs()) return@runCatching
                    val entry = file(key, layout.week)
                    var stream: FileOutputStream? = null
                    try {
                        stream = entry.startWrite()
                        DataOutputStream(stream).apply {
                            writeInt(MAGIC)
                            writeInt(VERSION)
                            writeUTF(key)
                            writeInt(layout.week)
                            writeInt(bytes.size)
                            writeLong(CRC32().apply { update(bytes) }.value)
                            write(bytes)
                            flush()
                        }
                        entry.finishWrite(stream)
                    } catch (e: Exception) {
                        entry.failWrite(stream)
                        throw e
                    }
                    directory.listFiles { item -> item.name.endsWith(".week") }
                        ?.sortedByDescending { it.lastModified() }?.drop(48)?.forEach { it.delete() }
                }
            }
        }
    }

    private companion object {
        // AtomicFile requires caller synchronization, including reads, across cache instances.
        val writes = Mutex()
        const val MAGIC = 0x57505454
        const val VERSION = 1
        const val MAX_BYTES = 4 * 1024 * 1024
    }
}
