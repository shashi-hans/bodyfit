package app.bodyfit.sync

import app.bodyfit.data.DailyRecord
import app.bodyfit.data.HealthDao
import app.bodyfit.data.HourlyRecord
import app.bodyfit.data.WaterEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory stand-in for the Room DAO. Only the day side is needed here. */
private class FakeDao(seed: List<DailyRecord> = emptyList()) : HealthDao {
    val days = linkedMapOf<String, DailyRecord>().apply { seed.forEach { put(it.date, it) } }

    override suspend fun getDay(date: String): DailyRecord? = days[date]
    override suspend fun upsertDay(record: DailyRecord) { days[record.date] = record }
    override suspend fun changedSince(since: Long): List<DailyRecord> =
        days.values.filter { it.updatedAt > since }.sortedBy { it.updatedAt }

    override fun observeDay(date: String): Flow<DailyRecord?> = flowOf(days[date])
    override fun observeRange(from: String, to: String): Flow<List<DailyRecord>> =
        flowOf(days.values.filter { it.date in from..to })
    override fun observeAllDays(): Flow<List<DailyRecord>> = flowOf(days.values.toList())
    override suspend fun allDaysOnce(): List<DailyRecord> = days.values.toList()
    override fun observeWaterEntries(date: String): Flow<List<WaterEntry>> = flowOf(emptyList())
    override fun observeHours(date: String): Flow<List<HourlyRecord>> = flowOf(emptyList())
    override suspend fun getHour(date: String, hour: Int): HourlyRecord? = null
    override suspend fun upsertHour(record: HourlyRecord) = Unit
    override suspend fun pruneHoursBefore(cutoff: String) = Unit
    override suspend fun deleteWaterEntry(id: Long) = Unit
    override suspend fun deleteWaterForDates(dates: List<String>) = Unit
    override suspend fun allHoursOnce(): List<HourlyRecord> = emptyList()
    override suspend fun deleteHoursForDates(dates: List<String>) = Unit
    override suspend fun waterTotal(date: String): Int = 0
    override suspend fun allWaterEntries(): List<WaterEntry> = emptyList()
    override suspend fun insertWaterEntry(entry: WaterEntry): Long = 0
}

/** Records what was pushed and answers with whatever the test wants. */
private class StubApi(
    var pullPages: MutableList<SyncPage> = mutableListOf(),
    var rejectDates: Set<String> = emptySet(),
) : SyncApi {
    val pushed = mutableListOf<List<DayDto>>()
    var cursorSeen: String? = null

    override suspend fun pull(cursor: String?): SyncPage {
        cursorSeen = cursor
        return if (pullPages.isEmpty()) SyncPage(emptyList(), "c0") else pullPages.removeAt(0)
    }

    override suspend fun push(days: List<DayDto>): PushResult {
        pushed += days
        val rejected = days.map { it.date }.filter { it in rejectDates }
        val accepted = days.map { it.date }.filterNot { it in rejectDates }
        return PushResult(accepted, rejected, "c1")
    }

    override suspend fun signIn(googleIdToken: String) = throw NotImplementedError()
    override suspend fun createInvite(scope: Set<SharedMetric>, expiresAt: Long) = throw NotImplementedError()
    override suspend fun claimInvite(code: String) = throw NotImplementedError()
    override suspend fun grants() = throw NotImplementedError()
    override suspend fun revokeGrant(grantId: String) = throw NotImplementedError()
    override suspend fun sharedSummary(ownerId: String) = throw NotImplementedError()
    override suspend fun createGroup(name: String) = throw NotImplementedError()
    override suspend fun createGroupInvite(groupId: String) = throw NotImplementedError()
    override suspend fun leaderboard(groupId: String, challengeId: String) = throw NotImplementedError()
    override suspend fun deleteAccount() = throw NotImplementedError()
}

class SyncRepositoryTest {

    private fun day(date: String, steps: Int, water: Int = 0, at: Long = 0) =
        DailyRecord(date, steps, 0, 0, 0.0, water, at)

    private fun dto(date: String, steps: Int, water: Int = 0, at: Long = 500) =
        DayDto(date, steps, 0, 0, 0.0, water, at)

    @Test
    fun `a day pulled from the server is not pushed straight back`() = runBlocking {
        // The loop this guards against: stamp a pulled row with now(), push it, the server
        // accepts and re-stamps, the other device pulls and pushes it again, forever.
        val dao = FakeDao()
        val api = StubApi(mutableListOf(SyncPage(listOf(dto("2026-09-08", 9_000)), "c1")))
        val outcome = SyncRepository(dao, api, InMemorySyncState(), now = { 10_000 }).syncOnce()

        assertEquals(1, outcome.applied)
        assertEquals(0, outcome.pushed)
        assertTrue("nothing should have been pushed", api.pushed.isEmpty())
        assertEquals(9_000, dao.days.getValue("2026-09-08").steps)
    }

    @Test
    fun `a merge that adds local data is pushed back`() = runBlocking {
        // Local walked further than the server knows, so the merged row is worth sending.
        val dao = FakeDao(listOf(day("2026-09-08", steps = 12_000, at = 400)))
        val api = StubApi(mutableListOf(SyncPage(listOf(dto("2026-09-08", 9_000)), "c1")))
        val outcome = SyncRepository(dao, api, InMemorySyncState(), now = { 10_000 }).syncOnce()

        assertEquals(1, outcome.pushed)
        assertEquals(12_000, api.pushed.single().single().steps)
    }

    @Test
    fun `a purely local day is pushed`() = runBlocking {
        val dao = FakeDao(listOf(day("2026-09-08", steps = 5_000, at = 400)))
        val outcome = SyncRepository(dao, StubApi(), InMemorySyncState(), now = { 10_000 }).syncOnce()
        assertEquals(1, outcome.pushed)
    }

    @Test
    fun `a rejected day is offered again next time`() = runBlocking {
        // The older row is rejected and the newer accepted. Advancing to the newer stamp
        // would step over the rejection and it would never be sent again.
        val dao = FakeDao(
            listOf(
                day("2026-09-07", steps = 1_000, at = 100),
                day("2026-09-08", steps = 2_000, at = 900),
            ),
        )
        val api = StubApi(rejectDates = setOf("2026-09-07"))
        val state = InMemorySyncState()
        val repo = SyncRepository(dao, api, state, now = { 10_000 })

        val first = repo.syncOnce()
        assertEquals(1, first.rejected)

        api.rejectDates = emptySet()
        val second = repo.syncOnce()
        assertTrue(
            "the rejected day should be retried",
            api.pushed.last().any { it.date == "2026-09-07" },
        )
        // Holding the watermark below the rejection also re-offers the accepted day that
        // was newer than it. That re-send is idempotent, and it is the price of never
        // stepping over a rejection; the alternative is tracking rejected dates as extra
        // state for no real gain.
        assertEquals(2, second.pushed)
    }

    @Test
    fun `an idle sync does no work`() = runBlocking {
        val dao = FakeDao()
        val api = StubApi()
        val outcome = SyncRepository(dao, api, InMemorySyncState(), now = { 10_000 }).syncOnce()
        assertEquals(SyncOutcome(pulled = 0, applied = 0, pushed = 0, rejected = 0), outcome)
        assertTrue(api.pushed.isEmpty())
    }

    @Test
    fun `the cursor is carried into the next pull`() = runBlocking {
        val dao = FakeDao()
        val api = StubApi(mutableListOf(SyncPage(emptyList(), "cursor-9")))
        val state = InMemorySyncState()
        val repo = SyncRepository(dao, api, state, now = { 10_000 })
        repo.syncOnce()
        repo.syncOnce()
        assertEquals("cursor-9", api.cursorSeen)
    }
}
