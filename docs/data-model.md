# Data model

## On the phone (Room, `doomscroll_duel.db`)

`reel_counts`, one row per local day and app.

| column | type | meaning |
|---|---|---|
| `date` | TEXT | local calendar day, `yyyy-MM-dd` (primary key part 1) |
| `package_name` | TEXT | one of the four tracked packages (primary key part 2) |
| `count` | INTEGER | reels counted that day in that app |
| `updated_at` | INTEGER | wall-clock millis of the last change |

- A new local day creates new rows, which is the reset at local midnight. Nothing is deleted at midnight.
- Rows older than 400 days are deleted (`RoomReelRepository.RETENTION_DAYS`).
- The counter writes a zero row for each app each day it runs. A day with no row means "the counter was
  not running", which ends a streak (see `StreakCalculator`).
- No video, caption, username, text or per-swipe time is ever stored.

## In Firestore (friends and duels)

```
usernames/{usernameLower}              { uid }                         read: get only. Written by function.
users/{uid}                            { username, displayName, avatarColor, createdAt }
users/{uid}/friends/{friendUid}        { since }                       written by function on both sides
invites/{code}                         { ownerUid, expiresAt, usesLeft } functions only
duels/{duelId}                         { players: [uidA, uidB], status: pending|active|finished,
                                          startAt, endAt, stakeCoins, reelLimit }   functions only
duels/{duelId}/counts/{uid}            { uid, total, perApp{instagram,youtube,facebook,snapchat},
                                          dateKey, updatedAt }
```

- `counts` is readable by the two players only while `status == 'active'`; each player writes only their
  own document. See `firebase/firestore.rules`.
- Friends are added by username or invite link. No phone contacts are read or uploaded.
- Coins are virtual and written only by Cloud Functions.
