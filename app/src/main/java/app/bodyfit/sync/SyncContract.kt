package app.bodyfit.sync

/**
 * The server contract, as agreed in docs/sync-and-sharing.md.
 *
 * Hosting is not chosen yet, so this interface is the fixed point: the app is built and
 * tested against [MockSyncApi], and a real client swaps in without touching a screen.
 * Everything here is suspending and may fail; callers treat failure as "try later", never
 * as "the data is gone", because the phone remains the source of truth.
 */
interface SyncApi {

    /** Exchanges a Google ID token for a session. The server verifies it with Google. */
    suspend fun signIn(googleIdToken: String): Session

    /** Days changed on the server after [cursor]. A null cursor means everything. */
    suspend fun pull(cursor: String?): SyncPage

    /** Sends locally changed days. The server keeps the newer of the two per date. */
    suspend fun push(days: List<DayDto>): PushResult

    suspend fun createInvite(scope: Set<SharedMetric>, expiresAt: Long): Invite

    /** Claims someone else's invite. The caller becomes the viewer on a new grant. */
    suspend fun claimInvite(code: String): Grant

    /** Grants this account issued, and grants issued to it. */
    suspend fun grants(): Grants

    suspend fun revokeGrant(grantId: String)

    /** Reads a shared account, limited to the metrics on the grant. */
    suspend fun sharedSummary(ownerId: String): SharedSummary

    suspend fun createGroup(name: String): Group

    suspend fun createGroupInvite(groupId: String): Invite

    suspend fun leaderboard(groupId: String, challengeId: String): List<LeaderboardRow>

    /** Deletes the account and its rows. Irreversible, and the UI must say so. */
    suspend fun deleteAccount()
}

data class Session(val accountId: String, val displayName: String, val token: String)

/**
 * A day, at the same grain as the local table.
 *
 * Individual water entries are deliberately not synced: a day is small, self-describing
 * and idempotent to overwrite, which is what keeps the merge rule simple enough to trust.
 */
data class DayDto(
    val date: String,
    val steps: Int,
    val moveMinutes: Int,
    val heartPoints: Int,
    val activeKcal: Double,
    val waterMl: Int,
    /** Set by the server on write. Sent by the client only as a tiebreak hint. */
    val updatedAt: Long,
)

data class SyncPage(val days: List<DayDto>, val cursor: String)

data class PushResult(val accepted: List<String>, val rejected: List<String>, val cursor: String)

/** Metrics a share can expose. There is no wildcard: a share cannot quietly grow. */
enum class SharedMetric { STEPS, MOVE_MINUTES, HEART_POINTS, ACTIVE_KCAL, WATER }

enum class GrantState { PENDING, ACTIVE, REVOKED }

data class Grant(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val viewerId: String,
    val viewerName: String,
    val scope: Set<SharedMetric>,
    val state: GrantState,
)

data class Grants(val granted: List<Grant>, val received: List<Grant>)

data class Invite(val code: String, val scope: Set<SharedMetric>, val expiresAt: Long)

/** Only the metrics on the grant are populated; the rest are null, not zero. */
data class SharedSummary(
    val ownerId: String,
    val ownerName: String,
    val date: String,
    val steps: Int?,
    val moveMinutes: Int?,
    val heartPoints: Int?,
    val activeKcal: Double?,
    val waterMl: Int?,
)

data class Group(val id: String, val name: String, val ownerId: String, val memberCount: Int)

data class LeaderboardRow(val accountId: String, val displayName: String, val total: Int, val rank: Int)

/** Thrown when the server refuses a read the caller is not entitled to. */
class NotAuthorised(message: String) : Exception(message)
