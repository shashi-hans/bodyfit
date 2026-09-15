package app.bodyfit.sync

import app.bodyfit.data.HealthDao

/** What one sync run did, for the settings screen and for tests. */
data class SyncOutcome(
    val pulled: Int,
    val applied: Int,
    val pushed: Int,
    val rejected: Int,
)

/**
 * One round of sync: pull, merge, push.
 *
 * Two watermarks are kept, and keeping them apart is the point. `pullCursor` is the
 * server's, opaque and only ever echoed back. `pushedThrough` is a local `updatedAt`
 * reading. They come from different clocks and are never compared with each other.
 *
 * A run that fails changes nothing locally beyond what it already applied, and the next run
 * repeats from the same watermarks. Sync is safe to retry and safe to abandon; the phone's
 * copy is never waiting on the server to be correct.
 */
class SyncRepository(
    private val dao: HealthDao,
    private val api: SyncApi,
    private val state: SyncStateStore,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * Diagnostics sink. Kept as a lambda so this class has no Android dependency and can
     * be unit tested; the caller passes something that writes to logcat.
     */
    private val log: (String) -> Unit = {},
) {

    suspend fun syncOnce(): SyncOutcome {
        val watermark = state.pushedThrough()

        val page = api.pull(state.pullCursor())
        var applied = 0
        for (dto in page.days) {
            val local = dao.getDay(dto.date)
            val merged = if (local == null) DayMerge.toRecord(dto) else DayMerge.merge(local, dto)
            if (local != null && merged == local) continue

            // A row the server already agrees with is stamped at the watermark, not at
            // now(). Stamping it now() would put it back in the next push, the server would
            // accept and re-stamp it, the other device would pull and push it again, and
            // the two would trade the same unchanged day forever. Only a row the merge
            // actually added something to is worth sending back.
            val inSync = !DayMerge.differs(merged, dto)
            dao.upsertDay(merged.copy(updatedAt = if (inSync) watermark else now()))
            applied++
        }
        state.setPullCursor(page.cursor)

        val pending = dao.changedSince(watermark)
        if (pending.isEmpty()) {
            return SyncOutcome(page.days.size, applied, pushed = 0, rejected = 0)
        }

        val result = api.push(pending.map(DayMerge::toDto))
        val acceptedDates = result.accepted.toSet()
        val rejectedDates = result.rejected.toSet()
        // The watermark stops below the oldest rejection. Taking the highest accepted
        // stamp instead would step over a rejected row that happened to be older, and that
        // row would never be offered again.
        val oldestRejected = pending.filter { it.date in rejectedDates }.minOfOrNull { it.updatedAt }
        val highestAccepted = pending.filter { it.date in acceptedDates }.maxOfOrNull { it.updatedAt }
        val advanced = when {
            highestAccepted == null -> null
            oldestRejected == null -> highestAccepted
            else -> minOf(highestAccepted, oldestRejected - 1)
        }
        if (advanced != null && advanced > watermark) state.setPushedThrough(advanced)
        if (rejectedDates.isNotEmpty()) {
            log("server kept its newer copy of ${rejectedDates.size} day(s)")
        }
        return SyncOutcome(page.days.size, applied, result.accepted.size, result.rejected.size)
    }
}
