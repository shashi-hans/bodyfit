# Sync and sharing

Spec for turning Body Fit from an on-device tracker into an account-backed one: live
sharing with a coach or family member, private-group challenges, multi-device sync, and
revocation that actually revokes.

Status: contract agreed, hosting not chosen. The client is built against a local mock that
implements this document, so the server can be written later without reopening the app.

## Decisions already made

| Decision | Choice |
| --- | --- |
| Hosting | Undecided. API-first: this contract is the fixed point, hosting follows. |
| Sign-in | Google. The server verifies a Google ID token and keys the account on its `sub`. |
| Sharing | Invite code. The viewer holds their own account, so every read is attributable. |
| Groups | Private only. A user creates a group and invites people; no public boards. |

## What does not change

The phone stays the source of truth. Room remains the local store, the tracker keeps
writing to it with no network, and every screen renders from local data. Sync is an
addition on top, not a replacement: with the network off the app behaves exactly as it does
today. This is what makes the offline story survive the feature.

## Data model

Server tables. `user_id` is internal; the Google `sub` is stored once and never used as a
foreign key elsewhere.

```
account        id, google_sub (unique), created_at, display_name, deleted_at
device         id, account_id, label, last_seen_at
daily_record   account_id, date, steps, move_minutes, heart_points,
               active_kcal, water_ml, updated_at, updated_by_device
grant          id, owner_id, viewer_id, scope, state, created_at,
               accepted_at, revoked_at
invite         code (unique), owner_id, scope, expires_at, claimed_by, claimed_at
group          id, owner_id, name, created_at
group_member   group_id, account_id, joined_at, state
challenge      id, group_id, metric, target, starts_on, ends_on
consent        id, account_id, purpose, granted_at, withdrawn_at, policy_version
audit          id, actor_id, action, subject_id, at, detail
```

`daily_record` is keyed `(account_id, date)`, the same grain as the local table. Nothing
finer syncs: individual water entries stay on the device. A day is small, self-describing
and idempotent to overwrite, which is what makes the sync rule below simple enough to
trust.

`scope` on a grant and an invite is an explicit list of metrics, for example
`["steps","heart_points"]`. There is no "everything" value: a wildcard is how a share
quietly grows past what the owner agreed to.

## Sync

Last-write-wins per `(account_id, date)`, resolved on `updated_at`.

- **Push**: the client sends days whose local `updated_at` is newer than its last
  acknowledged push. The server accepts a day only if the incoming `updated_at` is newer
  than the stored one.
- **Pull**: `GET /sync?since=<cursor>` returns days changed after the cursor, plus a new
  cursor. The cursor is server-assigned and opaque.
- **Conflicts**: two devices editing the same day is real — a phone counting steps while a
  tablet logs water. Last-write-wins loses the tablet's water. So the client merges per
  field before pushing: sensor fields (steps, move minutes, heart points, active kcal) are
  taken from the device that owns the higher value for that day, and water is summed from
  local entries, which is already how the local total is derived. Only the merged day goes
  up.
- **Clock skew**: `updated_at` is set by the server on write, never trusted from the
  client. The client sends its own timestamp only as a tiebreak hint.
- **No echo**: a pulled day the client had nothing to add to is stamped at the push
  watermark, not at the current time. Stamping it now would put it in the next push, the
  server would accept and re-stamp it, the other device would pull and push it back, and
  two devices would trade the same unchanged day forever.
- **Rejections**: the push watermark stops below the oldest rejected day, so a rejection is
  always offered again. Anything newer than it is re-sent too; that is idempotent and
  cheaper than tracking rejected dates as separate state.

A day's steps can only ever grow within a day, which is why "higher value wins" is safe for
sensor fields and not a guess.

## Sharing and revocation

1. Owner creates an invite: `POST /invites {scope, expires_at}` returns a short code.
2. Owner sends the code out of band. The code is single-use and expires; an unclaimed code
   is not access.
3. Viewer signs in and claims it: `POST /invites/{code}/claim`. This creates a `grant` in
   state `active` and writes an `audit` row.
4. Viewer reads: `GET /shared/{owner_id}/summary` returns only the metrics in `scope`.
5. Owner revokes: `DELETE /grants/{id}` sets `revoked_at`. The next request from the viewer
   returns 403.

Revocation is enforced server-side on every read, not by a flag the viewer's app is trusted
to honour. The viewer's app holds no local copy of shared data beyond the current screen,
so revocation takes effect on their next refresh rather than the next login.

What revocation cannot do: recall what the viewer already saw, screenshotted or wrote down.
The consent screen says this in plain words, because a promise the system cannot keep is
worse than no promise.

## Groups and challenges

A group is private and invite-only, same invite mechanism with a group scope. A challenge
names one metric, a target and a date range. Ranking is computed server-side from synced
`daily_record` rows, never from a client-submitted score.

Members see each other's total for the challenge metric over the challenge window, and
nothing else. Joining a group is not a grant: it does not expose daily history, only the
aggregate the challenge is about.

Step counts come from a sensor on a device the user controls, so a rooted phone can lie.
Mitigations, not prevention: reject days above a plausible ceiling, rate-limit pushes,
and flag accounts whose daily deltas jump implausibly. Say so in the challenge rules rather
than implying the numbers are verified.

## Consent, retention and residency

Health data is sensitive personal data under the DPDP Act 2023. This feature moves it off
the user's phone, so the obligations start here rather than being a later concern.

- Consent is per purpose and recorded in `consent` with the policy version: syncing your
  own data across devices is one purpose, sharing with a named viewer is another, and
  joining a group is a third. Withdrawing one does not withdraw the others.
- All storage and processing stays in ap-south-1. Any hosting choice that cannot commit to
  that in writing is out, including a BaaS whose default is multi-region.
- Deleting the account deletes the rows, not just a flag: `daily_record`, `grant`,
  `group_member` and `invite` go, and the `audit` rows keep only the actor id and action.
- Retention: sync data for an account with no device seen in 18 months is deleted after a
  notice to the account's email.
- The Play Store health-data declaration has to be updated before a build with this feature
  ships, and the privacy policy needs a section on who a share exposes data to.

## Security notes

- The Google ID token is verified against Google's published keys on every sign-in, and the
  `aud` claim is checked against our client id. A token the client says is valid is not.
- Every shared read is authorised from the `grant` table by owner and viewer, never from an
  id in the request path. This is the IDOR case that leaks other people's health data.
- No PII in logs: log the account id, never the email, display name or any metric value.
- The invite code is a bearer credential until claimed: short, single-use, expiring, and
  rate-limited on the claim endpoint to stop code guessing.
- Tokens live in `EncryptedSharedPreferences` on the device, not in the Room database that
  the backup rules copy.

## API

```
POST   /auth/google         {id_token} -> {session, account}
GET    /sync?since=<cursor> -> {days[], cursor}
POST   /sync                {days[]} -> {accepted[], rejected[], cursor}
POST   /invites             {scope, expires_at} -> {code, expires_at}
POST   /invites/{code}/claim -> {grant}
GET    /grants              -> {granted[], received[]}
DELETE /grants/{id}         -> 204
GET    /shared/{owner}/summary -> {metrics limited to grant scope}
POST   /groups              {name} -> {group}
POST   /groups/{id}/invites -> {code}
GET    /groups/{id}/leaderboard?challenge=<id> -> {rows[]}
DELETE /account             -> 204, deletes as described above
```

## Client changes

- `INTERNET` permission, which the app does not currently declare.
- A sync worker on WorkManager: on app foreground, after a day rolls over, and on a
  periodic constraint of unmetered network. Never on every sensor tick.
- A signed-out mode that is fully functional. Sign-in is opt-in, and refusing it costs the
  user nothing except the shared features.
- Screens: sign-in, a share list with revoke, an invite-claim flow, group list and
  leaderboard.

## Build order

1. Local mock implementing this contract, so the client is exercised end to end.
2. Sync of own data, signed in on one device. Proves the merge rule.
3. Second device. Proves conflict handling.
4. Invite, grant, shared view, revoke.
5. Groups, challenges, leaderboard.

Each step is usable on its own, and each one that ships needs its consent copy written
before it does.
