package dev.wakeuppure.data.local

import dev.wakeuppure.domain.model.Schedule
import dev.wakeuppure.domain.model.ScheduleData
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test

class PreparedTimetableTest {
    private val data = ScheduleData(Schedule(name = "课表", semesterStartDate = "2026-10-19"))

    @Test fun uncachedLayoutIsPreparedOffTheCallingThread() = runBlocking {
        val caller = Thread.currentThread()
        var loaderThread: Thread? = null
        val prepared = PreparedTimetable(data, { loaderThread = Thread.currentThread(); null }, {})
        assertEquals(5, prepared.prepareWeek(5).week)
        assertNotEquals(caller, loaderThread)
    }

    @Test fun nearbyPreparationPrioritizesTheRequestedWeek() = runBlocking {
        val reads = mutableListOf<Int>()
        val prepared = PreparedTimetable(data, { reads += it; null }, {})
        prepared.prepareAround(5)
        assertEquals(listOf(5, 4, 6), reads)
    }

    @Test fun cachedPreparationDoesNotReadOrCalculateAgain() = runBlocking {
        var reads = 0
        var writes = 0
        val prepared = PreparedTimetable(data, { reads++; null }, { writes++ })
        val first = prepared.prepareWeek(5)
        assertSame(first, prepared.prepareWeek(5))
        assertEquals(1, reads)
        assertEquals(1, writes)
    }

    @Test fun simultaneousPagerAndPrefetchRequestsLoadAndSaveAWeekOnlyOnce() = runBlocking {
        var reads = 0
        var writes = 0
        val prepared = PreparedTimetable(data, { reads++; delay(30); null }, { writes++ })
        val results = List(12) { async { prepared.prepareWeek(5) } }.awaitAll()
        results.forEach { assertSame(results.first(), it) }
        assertEquals(1, reads)
        assertEquals(1, writes)
    }
}
