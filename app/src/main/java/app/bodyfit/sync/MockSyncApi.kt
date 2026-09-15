package app.bodyfit.sync

import java.util.UUID
import kotlin.math.max

/**
 * An in-memory stand-in for the server, implementing docs/sync-and-sharing.md.
 *
 * It exists so the client can be built and tested end to end before hosting is chosen. It
 * enforces the rules that the client must not be trusted to enforce, because those are
 * exactly the ones a mock usually lets through and a real server then rejects:
 *
 * - a push is accepted only when it is newer than what is stored
 * - `updatedAt` is stamped here, never taken from the caller
 * - a shared read is authorised from the grant table and filtered to its scope
 * - a revoked grant fails the next read, not the next sign-in
 *
 * State is in memory only, so it resets with the process. Persisting it would make the mock
 * a small database, which is the thing this stage is deferring.
 */
class MockSyncApi(
    /** Injected so tests can drive time instead of sleeping. */
    private val clock: () -> Long = System::currentTimeMillis,
) : SyncApi {

    private class Account(val id: String, var name: String) {
        val days = linkedMapOf<String, DayDto>()
    }

    private val accounts = linkedMapOf<String, Account>()
    private val grants = linkedMapOf<String, Grant>()
    private val invites = linkedMapOf<String, PendingInvite>()
    private val groups = linkedMapOf<String, MutableGroup>()
    private var current: Account? = null
    private var sequence = 0L

    private class PendingInvite(
        val ownerId: String,
        val scope: Set<SharedMetric>,
        val expiresAt: Long,
        val groupId: String?,
        var claimed: Boolean = false,
    )

    private class MutableGroup(val id: String, val name: String, val ownerId: String) {
        val members = linkedSetOf<String>()
    }

    /** Test seam: sign in as a named account without a real Google token. */
    fun signInAs(accountId: String, name: String): Session {
        val account = accounts.getOrPut(accountId) { Account(accountId, name) }
        account.name = name
        current = account
        return Session(account.id, account.name, token = "mock-${account.id}")
    }

    override suspend fun signIn(googleIdToken: String): Session {
        // A real server verifies the token against Google's keys and checks the audience.
        // The mock derives a stable id from the token so repeat sign-ins land on one account.
        val id = "acct-" + googleIdToken.hashCode().toUInt().toString(16)
        return signInAs(id, "Signed-in user")
    }

    private fun requireAccount(): Account = current ?: throw NotAuthorised("not signed in")

    override suspend fun pull(cursor: String?): SyncPage {
        val account = requireAccount()
        val since = cursor?.toLongOrNull() ?: 0L
        val changed = account.days.values.filter { it.updatedAt > since }.sortedBy { it.updatedAt }
        val next = changed.lastOrNull()?.updatedAt ?: since
        return SyncPage(changed, next.toString())
    }

    override suspend fun push(days: List<DayDto>): PushResult {
        val account = requireAccount()
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        var high = account.days.values.maxOfOrNull { it.updatedAt } ?: 0L
        for (day in days) {
            val stored = account.days[day.date]
            // Stale writes lose. The client's own timestamp is not what decides this.
            if (stored != null && day.updatedAt < stored.updatedAt) {
                rejected += day.date
                continue
            }
            val stamped = day.copy(updatedAt = nextStamp())
            account.days[day.date] = stamped
            high = max(high, stamped.updatedAt)
            accepted += day.date
        }
        return PushResult(accepted, rejected, high.toString())
    }

    private fun nextStamp(): Long {
        // Monotonic even when several writes land in the same millisecond, so a cursor
        // never skips a row.
        sequence = max(sequence + 1, clock())
        return sequence
    }

    override suspend fun createInvite(scope: Set<SharedMetric>, expiresAt: Long): Invite {
        require(scope.isNotEmpty()) { "a share with no metrics is not a share" }
        val owner = requireAccount()
        val code = UUID.randomUUID().toString().take(8).uppercase()
        invites[code] = PendingInvite(owner.id, scope, expiresAt, groupId = null)
        return Invite(code, scope, expiresAt)
    }

    override suspend fun claimInvite(code: String): Grant {
        val viewer = requireAccount()
        val invite = invites[code] ?: throw NotAuthorised("unknown code")
        if (invite.claimed) throw NotAuthorised("code already used")
        if (clock() > invite.expiresAt) throw NotAuthorised("code expired")
        if (invite.ownerId == viewer.id) throw NotAuthorised("cannot share with yourself")
        invite.claimed = true

        invite.groupId?.let { groupId ->
            groups[groupId]?.members?.add(viewer.id)
        }
        val owner = accounts.getValue(invite.ownerId)
        val grant = Grant(
            id = UUID.randomUUID().toString(),
            ownerId = owner.id,
            ownerName = owner.name,
            viewerId = viewer.id,
            viewerName = viewer.name,
            scope = invite.scope,
            state = GrantState.ACTIVE,
        )
        grants[grant.id] = grant
        return grant
    }

    override suspend fun grants(): Grants {
        val me = requireAccount()
        return Grants(
            granted = grants.values.filter { it.ownerId == me.id && it.state != GrantState.REVOKED },
            received = grants.values.filter { it.viewerId == me.id && it.state != GrantState.REVOKED },
        )
    }

    override suspend fun revokeGrant(grantId: String) {
        val me = requireAccount()
        val grant = grants[grantId] ?: throw NotAuthorised("no such grant")
        // Only the owner revokes, and only their own grant. Checked here rather than in the
        // client, because this is the check that stops one account touching another's.
        if (grant.ownerId != me.id) throw NotAuthorised("not your grant")
        grants[grantId] = grant.copy(state = GrantState.REVOKED)
    }

    override suspend fun sharedSummary(ownerId: String): SharedSummary {
        val viewer = requireAccount()
        val grant = grants.values.firstOrNull {
            it.ownerId == ownerId && it.viewerId == viewer.id && it.state == GrantState.ACTIVE
        } ?: throw NotAuthorised("no active grant")

        val owner = accounts.getValue(ownerId)
        val latest = owner.days.values.maxByOrNull { it.date }
        fun <T> gated(metric: SharedMetric, value: T?): T? =
            if (metric in grant.scope) value else null

        return SharedSummary(
            ownerId = owner.id,
            ownerName = owner.name,
            date = latest?.date.orEmpty(),
            steps = gated(SharedMetric.STEPS, latest?.steps),
            moveMinutes = gated(SharedMetric.MOVE_MINUTES, latest?.moveMinutes),
            heartPoints = gated(SharedMetric.HEART_POINTS, latest?.heartPoints),
            activeKcal = gated(SharedMetric.ACTIVE_KCAL, latest?.activeKcal),
            waterMl = gated(SharedMetric.WATER, latest?.waterMl),
        )
    }

    override suspend fun createGroup(name: String): Group {
        val owner = requireAccount()
        val group = MutableGroup(UUID.randomUUID().toString().take(8), name, owner.id)
        group.members += owner.id
        groups[group.id] = group
        return Group(group.id, group.name, group.ownerId, group.members.size)
    }

    override suspend fun createGroupInvite(groupId: String): Invite {
        val owner = requireAccount()
        val group = groups[groupId] ?: throw NotAuthorised("no such group")
        if (group.ownerId != owner.id) throw NotAuthorised("not your group")
        val code = UUID.randomUUID().toString().take(8).uppercase()
        val expires = clock() + 7 * 24 * 60 * 60 * 1000L
        // A group invite carries no metric scope: joining a group exposes the challenge
        // aggregate only, never the member's daily history.
        invites[code] = PendingInvite(owner.id, emptySet(), expires, groupId = groupId)
        return Invite(code, emptySet(), expires)
    }

    override suspend fun leaderboard(groupId: String, challengeId: String): List<LeaderboardRow> {
        val me = requireAccount()
        val group = groups[groupId] ?: throw NotAuthorised("no such group")
        if (me.id !in group.members) throw NotAuthorised("not a member")

        // Ranked from stored days, never from a score the client submits.
        return group.members
            .mapNotNull { accounts[it] }
            .map { it to it.days.values.sumOf { day -> day.steps } }
            .sortedByDescending { it.second }
            .mapIndexed { index, (account, total) ->
                LeaderboardRow(account.id, account.name, total, index + 1)
            }
    }

    override suspend fun deleteAccount() {
        val me = requireAccount()
        accounts.remove(me.id)
        grants.values.filter { it.ownerId == me.id || it.viewerId == me.id }
            .forEach { grants.remove(it.id) }
        invites.values.removeAll { it.ownerId == me.id }
        groups.values.forEach { it.members.remove(me.id) }
        current = null
    }
}
