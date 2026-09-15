package app.bodyfit.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules a client must not be trusted to enforce. Each test here is a way one account
 * could read another's health data if the server got it wrong.
 */
class MockSyncApiTest {

    private var clock = 1_000L
    private val api = MockSyncApi { clock }

    private fun day(date: String, steps: Int = 0, water: Int = 0, at: Long = 0) =
        DayDto(date, steps, 0, 0, 0.0, water, at)

    @Test
    fun `a stale push is rejected and the stored day survives`() = runBlocking {
        api.signInAs("owner", "Owner")
        api.push(listOf(day("2026-09-08", steps = 9000, at = 500)))
        val stored = api.pull(null).days.single()

        val result = api.push(listOf(day("2026-09-08", steps = 10, at = stored.updatedAt - 1)))
        assertEquals(listOf("2026-09-08"), result.rejected)
        assertEquals(9000, api.pull(null).days.single().steps)
    }

    @Test
    fun `the server stamps updatedAt and ignores what the client claims`() = runBlocking {
        api.signInAs("owner", "Owner")
        api.push(listOf(day("2026-09-08", steps = 1, at = 9_999_999)))
        assertTrue(api.pull(null).days.single().updatedAt < 9_999_999)
    }

    @Test
    fun `the cursor returns only what changed after it`() = runBlocking {
        api.signInAs("owner", "Owner")
        api.push(listOf(day("2026-09-07", steps = 100)))
        val first = api.pull(null)
        assertEquals(1, first.days.size)

        clock += 10
        api.push(listOf(day("2026-09-08", steps = 200)))
        val second = api.pull(first.cursor)
        assertEquals(listOf("2026-09-08"), second.days.map { it.date })
    }

    @Test
    fun `a shared read is filtered to the grant scope`() = runBlocking {
        api.signInAs("owner", "Owner")
        api.push(listOf(day("2026-09-08", steps = 9000, water = 2000)))
        val invite = api.createInvite(setOf(SharedMetric.STEPS), clock + 10_000)

        api.signInAs("coach", "Coach")
        api.claimInvite(invite.code)
        val summary = api.sharedSummary("owner")

        assertEquals(9000, summary.steps)
        // Not in scope, so absent rather than zero: zero would read as "drank nothing".
        assertNull(summary.waterMl)
    }

    @Test
    fun `revoking blocks the very next read`() = runBlocking {
        api.signInAs("owner", "Owner")
        api.push(listOf(day("2026-09-08", steps = 9000)))
        val invite = api.createInvite(setOf(SharedMetric.STEPS), clock + 10_000)
        api.signInAs("coach", "Coach")
        val grant = api.claimInvite(invite.code)
        assertEquals(9000, api.sharedSummary("owner").steps)

        api.signInAs("owner", "Owner")
        api.revokeGrant(grant.id)

        api.signInAs("coach", "Coach")
        assertThrowsNotAuthorised { api.sharedSummary("owner") }
    }

    @Test
    fun `a viewer cannot revoke a grant they do not own`() = runBlocking {
        api.signInAs("owner", "Owner")
        val invite = api.createInvite(setOf(SharedMetric.STEPS), clock + 10_000)
        api.signInAs("coach", "Coach")
        val grant = api.claimInvite(invite.code)
        assertThrowsNotAuthorised { api.revokeGrant(grant.id) }
    }

    @Test
    fun `reading someone who never shared with you fails`() = runBlocking {
        api.signInAs("owner", "Owner")
        api.push(listOf(day("2026-09-08", steps = 9000)))
        api.signInAs("stranger", "Stranger")
        assertThrowsNotAuthorised { api.sharedSummary("owner") }
    }

    @Test
    fun `an invite code is single use and expires`() = runBlocking {
        api.signInAs("owner", "Owner")
        val invite = api.createInvite(setOf(SharedMetric.STEPS), clock + 10_000)
        api.signInAs("coach", "Coach")
        api.claimInvite(invite.code)
        api.signInAs("other", "Other")
        assertThrowsNotAuthorised { api.claimInvite(invite.code) }

        api.signInAs("owner", "Owner")
        val expiring = api.createInvite(setOf(SharedMetric.STEPS), clock + 5)
        clock += 100
        api.signInAs("late", "Late")
        assertThrowsNotAuthorised { api.claimInvite(expiring.code) }
    }

    @Test
    fun `a leaderboard ranks members and excludes non-members`() = runBlocking {
        api.signInAs("owner", "Owner")
        api.push(listOf(day("2026-09-08", steps = 5000)))
        val group = api.createGroup("Morning walk")
        val invite = api.createGroupInvite(group.id)

        api.signInAs("friend", "Friend")
        api.push(listOf(day("2026-09-08", steps = 12000)))
        api.claimInvite(invite.code)

        val rows = api.leaderboard(group.id, "any")
        assertEquals(listOf("Friend", "Owner"), rows.map { it.displayName })
        assertEquals(listOf(1, 2), rows.map { it.rank })

        api.signInAs("outsider", "Outsider")
        assertThrowsNotAuthorised { api.leaderboard(group.id, "any") }
    }

    @Test
    fun `a share with no metrics is refused`() = runBlocking {
        api.signInAs("owner", "Owner")
        try {
            api.createInvite(emptySet(), clock + 1000)
            throw AssertionError("expected an empty scope to be refused")
        } catch (expected: IllegalArgumentException) {
            // A share that exposes nothing is a mistake, not a valid configuration.
        }
    }

    private inline fun assertThrowsNotAuthorised(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected NotAuthorised")
        } catch (expected: NotAuthorised) {
            // as intended
        }
    }
}
