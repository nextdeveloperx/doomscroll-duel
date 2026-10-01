# Challenge modes

All rules below are implemented as pure Kotlin in `domain/challenge/` and covered by unit tests in
`app/src/test/.../domain/challenge/`. The server (Cloud Functions) runs the same code paths; the phone runs
them too, so offline screens agree with the final result.

Coins are **virtual only**. Nothing here can be bought, cashed out or bet with real money. Only the 1v1 DUEL
moves coins, and only between the two players (zero-sum: no coins are created or destroyed).

---

## Shared rules

### Lifecycle (DUEL, FORFEIT DARE, SQUAD)

```
PENDING --accept--> ACTIVE --time up--> FINISHED
   |                   |
   |                   +--counter off > 10 min--> FORFEITED
   +--decline / creator cancels--> CANCELLED
   +--24 h, no answer-----------> EXPIRED
```

| Status | Meaning | Terminal |
|---|---|---|
| PENDING | Invite sent (squad: lobby open). Lasts 24 hours. No coins held. | no |
| ACTIVE | Accepted. Stakes in escrow. Clock runs. | no |
| FINISHED | Played to the end: a win by lower count, or a tie. | yes |
| FORFEITED | Ended by the 10 minute rule (or both players broke it). | yes |
| CANCELLED | Declined, withdrawn by the creator, or a player could not cover the stake. | yes |
| EXPIRED | Nobody answered in 24 hours. | yes |

A terminal state refuses every further event (`ALREADY_FINAL`). That is the first guard against settling twice.

### The 10 minute rule (`ForfeitRule`)

A player whose accessibility service is switched **off** for **more than 10 minutes in a row** inside a challenge
window is out. Exactly 10:00 is fine. Details:

- Only `SERVICE_DISABLED` gaps count. A dead battery, a reboot or an unknown gap is never punished, because we cannot prove it was deliberate.
- Overlapping or touching gaps are merged, so splitting one long gap into pieces does not help. Several short gaps never add up.
- Gaps are clipped to the challenge window; a gap that is still open counts up to the end of the window.
- The forfeit moment is the instant the 10 minutes ran out. The earliest forfeit moment decides who loses when both players broke the rule.
- Evidence comes from the phone's gap log, uploaded when the app next connects. The server settles a short grace after the end (default 15 minutes) so final syncs can arrive.

### Coins (`CoinLedger`)

Operations: `Hold` (available to escrow), `Release` (escrow back), `Transfer` (loser's escrow to winner).
Every operation has a stable id such as `duel42:settle:transfer`. Applying an id twice does nothing, and a batch
is all-or-nothing. Together with the terminal-state check this makes double settlement harmless, and the total
number of coins never changes (tested).

### Pro

Pro is needed to **create** SQUAD and STRICT LOCK. Joining a squad is free. If Pro lapses, a running lock or squad
finishes normally; new ones cannot be created.

---

## 1. 1v1 DUEL (free)

**Rules.** Two players. The creator picks a reel limit (10 to 500), a duration (6 hours, 24 hours or 7 days) and
a stake (1 to 1000 coins; the UI offers 25, 50, 100). The invite is open for 24 hours. When the opponent accepts,
the clock starts and both stakes move to escrow in one transaction. The reel count that matters is the one
counted between the accept time and the end time.

**Winner.** The **lower reel count** wins both stakes. If both players are over the limit, the lower count still
wins. (With one shared limit, a player over the limit always has a higher count than a player under it, so the
limit never changes who wins. It only drives the brain, the HP bar and the warning.)

**Tie-break.** None needed beyond equality: equal counts are a **tie** and both stakes are returned. Equal counts
over the limit are also a tie.

**Forfeit.** The 10 minute rule. If one player breaks it, the other wins the pot even with a higher count. If both
break it, the one who crossed first loses; if they crossed at the same instant it is a **mutual forfeit** and both
stakes are returned. A live forfeit ends the duel immediately; one found at the end is applied at settlement.

**Edge cases.**

| Case | Result |
|---|---|
| Accept after 24 hours | refused (`INVITE_EXPIRED`); the scheduled `Expire` ends it |
| Not enough coins when accepting (either player) | CANCELLED (`INSUFFICIENT_FUNDS`), nothing held |
| Exactly enough coins | allowed |
| Creator cancels after it was accepted | refused. An active duel ends only by playing out or forfeit |
| Decline / cancel while pending | CANCELLED, no coins moved |
| Settle before the end time | refused (`TOO_EARLY`) |
| Settle twice, or settle after a live forfeit | second one refused; even if it slipped through, the ledger refuses the duplicate ids |
| Dead phone for hours | not a forfeit, the count decides |
| Counter off exactly 10:00 | not a forfeit |
| Zero reels each | tie |
| Player is not in the duel | refused (`NOT_A_PLAYER`) |

**UI states** (`DuelViewState`, from one player's point of view): `InviteReceived`, `InviteSent`, `Running`
(time left, my count, opponent's count, who leads, pot), `Won` / `Lost` (each with `byForfeit`), `Tied`,
`MutualForfeit`, `Cancelled` (reason, by me or not), `Expired`, `NotMine`.
The opponent's count is shown only in `Running`, matching the server rule that it is readable only while active.

**Stored data** (Firestore `duels/{id}`; functions write, players read their own):
`mode, players[creator, opponent], config{reelLimit, durationHours, stake}, status, invitedAt, expiresAt,
startedAt, endsAt, settledAt, result{type: win|tie|mutualForfeit, winner, loser, winnerReels, loserReels, reason},
cancelReason, cancelledBy`. Sub-documents: `counts/{uid}` (running total, per app, dateKey; see `firestore.rules`),
`gaps/{uid}` (list of {start, end, cause}). Wallet: `wallets/{uid}{available, held}` and
`ledger/{opId}{type, player, amount, duelId, at}` where the document id is the op id, so a second write fails.

---

## 2. SQUAD BATTLE (Pro to create)

**Rules.** Two squads of 3 to 10 members face off. A Pro leader creates the squad (side A) and the battle; the
other leader (also Pro) opens the challenge link and claims side B. Members of either side join through that
side's invite link; joining is free. A leader taps ready when the squad has 3 to 10 members; after that nobody
joins or leaves. When both leaders are ready the battle starts. The lobby is open for 24 hours. No coins.

**Winner.** The squad with the **lower average reels per member**. The average is compared exactly as
`total_A x members_B` against `total_B x members_A`, so squads of different sizes are compared with no rounding.

**Tie-break.** If the averages are equal, the squad whose **worst member** watched fewer reels wins. If that is
equal too, it is a **draw**.

**Disqualified members.** A member who never reported, or whose counter was off for over 10 minutes, is scored at
the per-member limit (or their real count if higher). A squad cannot forfeit as a whole.

**Leaderboard.** Members sorted by fewest reels; equal counts share a rank. The **weakest member** (most reels)
is highlighted; tied weakest members are all highlighted; if everybody is on the same number nobody is singled out.

**Edge cases.**

| Case | Result |
|---|---|
| Free user tries to create or claim a side | refused (`LEADER_NEEDS_PRO`) |
| Squad already has 10 | `SQUAD_FULL` |
| Fewer than 3 when the leader taps ready | `ROSTER_TOO_SMALL` |
| Same person in both squads or twice | `ALREADY_IN_BATTLE` |
| Join after the roster is locked | `ROSTER_LOCKED` |
| A member leaves before lock | allowed; the leader cannot leave, only cancel |
| Lobby open 24 hours with no start | EXPIRED |
| Leader cancels before start | CANCELLED |
| Settling twice | second refused |

**UI states.** Lobby (`Forming`: members list, who is ready, time left, invite link), `Active` (live leaderboard
with weakest highlighted, both squad averages), `Finished` (`Win(side, decidedBy)` or `Draw`), `Cancelled`, `Expired`.

**Stored data** (`squads/{battleId}`): `config{reelLimitPerMember, durationHours}`, `sideA/sideB{name, leaderUid,
memberUids[], rosterLocked}`, `status`, `openedAt, expiresAt, startedAt, endsAt, settledAt`,
`result{type, side, decidedBy}`. Sub-collection `memberReports/{uid}{reels, gaps[]}`.

---

## 3. NIGHT PACT (free)

**Rules.** Two or more friends agree on zero reels between **23:00 and 06:00** in each person's own local time.
The creator is in from the start; the pact becomes active at the first "yes"; invites stay open 24 hours. A "night"
is named after the evening it starts on (the night of 1 October is 23:00 on the 1st to 06:00 on the 2nd). Someone
who joins after a night's window has started takes part from the next night.

**Verdict per person per night.**

| Verdict | When |
|---|---|
| Kept | zero reels counted in the window and no counter-off gap over 10 minutes |
| Broken(n) | any counted reel in the window |
| Unverified | no report arrived, or the counter was off for over 10 minutes inside the window |
| NotParticipating | not in the pact that night |

Unverified breaks the night too. Otherwise switching the counter off at 11 PM would be a free pass.

**Winner / score.** There is no winner. The **pact streak** is the number of nights in a row that **everyone** who
took part kept it (and at least two people took part). One broken or unverified night, or a missing night in
between, resets the current streak to zero; the best streak is remembered.

**Edge cases.**

| Case | Result |
|---|---|
| 23:00 sharp / 06:00 sharp | 23:00 is inside, 06:00 is outside |
| Daylight saving night | the window is an hour shorter or longer in real time, not broken |
| Members in different time zones | each judged in their own night, matched by the evening's date |
| Someone leaves | allowed; if fewer than two remain the pact ENDS (`TOO_FEW_MEMBERS`) |
| Everybody declines | CANCELLED |
| Nobody answers in 24 hours | EXPIRED |
| A night is evaluated twice | recorded once, the first record stands |
| Dead phone overnight | not a break (counter off is only punished when the service was switched off) |

**UI states.** Pending invite, Active (tonight: window time left and who has kept it so far; current and best
streak), Ended (reason), Cancelled, Expired; per night: Kept, Broken (who), Unverified (who).

**Stored data.** Firestore `pacts/{id}`: `members[{uid, zone, status, joinedAt|leftAt}]`, `state`, `startedAt`,
`inviteExpiresAt`, `streak{current, best}`; `pacts/{id}/nights/{yyyy-MM-dd}{verdicts{uid: kept|broken(n)|unverified(reason)|notParticipating}}`;
`pacts/{id}/reports/{night_uid}{reelsInWindow, gaps[]}`. **On the phone** (Room, new table `night_counts(night TEXT PRIMARY KEY, reels INTEGER)`):
when a reel is counted and the local time is inside the window, the counter adds one for that night. **No time
of any reel is stored.** (Room migration 1 to 2; not added yet, see "Not built yet".)

---

## 4. FORFEIT DARE (free)

**Rules.** A duel with **no coin stake** (same limit, duration, 10 minute rule and lower-count-wins). When it ends,
the **loser** does a dare the **winner picks from a curated list** and uploads a photo or a video of at most 10
seconds as proof. The winner approves or rejects. Each step has 24 hours.

| Step | Who | Time | If late |
|---|---|---|---|
| Pick a dare | winner | 24 h after the duel ends | no dare (`NO_PICK`) |
| Send proof | loser | 24 h after the pick | dare FAILED (`PROOF_DEADLINE_MISSED`) |
| Review | winner | 24 h after the proof | auto-approved, so the loser is never stuck |

**Winner / outcome.** The duel decides who owes the dare. A player who lost by forfeit (counter off) owes it
like any loser. A tie, a mutual forfeit, or a duel that was never played gives **no dare**.

**Rejecting.** The loser may send proof twice. One rejection gives at least 6 more hours (or the rest of the
original 24 hours, whichever is longer). A second rejection ends the dare as FAILED (`REJECTED_TWICE`).

**Safety.**

- The winner can only pick from `DareCatalog`: 16 reviewed dares, no free text. Every dare is photo or at most 10 seconds of video, at home, involves nobody else, nothing physical or dangerous, nothing about bodies, looks, religion, caste or money, no food beyond water. A unit test scans each dare's review text against banned words, so a bad dare cannot be added by accident.
- **Skip dare.** The loser can skip before sending proof, with no penalty. After sending proof, the winner decides. The winner can also **waive** the dare at any point.
- **Report.** Either player can report a dare or a proof (`UNSAFE_DARE, UNSAFE_PROOF, HARASSMENT, NUDITY, OTHER`). A report freezes the dare (state `Reported`) and hides the proof from both players until a moderator decides.
- Proof is private to the two players, deleted after 7 days whatever the outcome, and location data is stripped before upload.

**UI states** (`DareState`): `WaitingForDuel`, `NoDare(reason)`, `AwaitingPick` (winner: pick screen; loser: "waiting"),
`AwaitingProof` (loser: camera, deadline, skip, report), `ProofSubmitted` (winner: approve, reject, report),
`Completed(autoApproved)`, `Failed(reason)`, `Skipped(by)`, `Reported`.

**Stored data.** Firestore `dares/{duelId}`: `state`, `winnerUid`, `loserUid`, `dareId`, `pickBy`, `dueBy`,
`reviewBy`, `submissions`, `proof{storagePath, type, submittedAt}`, `report{by, reason, at}`. Proof files in private
storage `proofs/{duelId}/{uid}/...` readable only by the two players, with a 7 day lifecycle rule.
User-facing dare text lives in `strings.xml` as `dare_<id>`.

---

## 5. STRICT LOCK (Pro to set up)

**Rules.** A personal mode. The user sets a **daily reel cap** (10 to 1000), a **lock length** (until local
midnight, or 1 to 12 hours) and optionally one **unlock buddy**. When the day's reels reach the cap, the **reel
screens** of the tracked apps are blocked until the lock timer ends (only the reel surfaces, never a whole app).
The reel that reaches the cap is already counted; the lock starts right after it.

- Until midnight: the lock ends at local midnight and the new day starts fresh.
- Timed lock: when it ends the user gets a fresh allowance of one cap, counted from the total at lock time.

**While locked** the lock cannot be switched off, shortened or loosened. The cap cannot be changed. Outside a
lock, **lowering** the cap applies at once and **raising** it applies from tomorrow, so nobody can raise the cap
just before hitting it.

**Friend unlock** is the only exception. Only the chosen buddy can approve. Each approval is a **15 minute pass**
(never longer than the lock itself); at most **2 passes per lock** by default (0 to 5 configurable). One pass at
a time. Reels during a pass are still counted. With no buddy chosen the lock is absolute.

**Clock tampering.** Remaining time uses the smaller of wall-clock elapsed and monotonic elapsed (same boot), so
moving the phone clock forward does not shorten a lock. After a reboot only the wall clock is available, and a
clock moved back only makes the lock longer.

**Honest limit.** Android lets a user turn the accessibility service off or uninstall the app. The lock cannot
prevent that. The app shows "enforcement lost" while the service is off and the lock keeps running; it is a
commitment tool, not parental control.

**Winner.** None. **Edge cases.**

| Case | Result |
|---|---|
| Cap reached exactly | locks (`>=`) |
| More reels while locked | no change |
| Lock ends mid-day, timed lock | baseline moves to the total at lock time |
| Yesterday's Idle state | rolls to today with baseline 0 |
| Friend who is not the buddy tries | `NOT_THE_BUDDY` |
| Third pass | `NO_PASSES_LEFT`, and "ask a friend" is hidden |
| Pro lapses while locked | lock continues, no new lock can be created |

**UI states.** `Idle` (reels left until the cap), `Locked` (countdown, ask-a-friend if allowed), `FriendPass`
(countdown of the pass), and the decision the blocker uses: `Allow` or `Block(remaining, canAskFriend)`.

**Stored data.** On the phone (Room, new table `strict_lock`, one row): `config{dailyCap, lockLength, buddyUid,
maxPasses, pendingCap{cap, from}}` and `state{type, day, baseline|lockedAt, endsAt, totalAtLock, passesUsed,
passUntil, lockedAtElapsedMs, bootId}`. Mirrored to Firestore `users/{uid}/strictLock` so the buddy can approve:
`unlockRequests/{id}{fromUid, buddyUid, requestedAt, status, passUntil}`; only the buddy can write `status`.

---

## Not built yet (be clear about what is and is not done)

Done and tested: all models, state machines, settlement, ledger, scoring, streaks, lock policy, dare catalog.

Not built yet:
- Cloud Functions that call these on a schedule (expire, settle, tick), and the Firestore rules for the new collections (`duels` counts rules exist; squads, pacts, dares, unlock requests do not).
- The Room tables and migration for `night_counts` and `strict_lock`, and recording night reels in the counter.
- The screens for squad lobby, night pact, dare flow and strict lock settings.
- Strict Lock **enforcement**: closing the reel screen from the accessibility service (for example `performGlobalAction`) needs a Play declaration update and a decision on how it fits the "read-only counter" privacy promise. `scripts/check-service-privacy.sh` still forbids node actions and gestures.
- Photo and video capture and upload, and the moderation side of reports.
- Play Billing for Pro. Coins are not for sale, which keeps them out of real-money gambling rules.
