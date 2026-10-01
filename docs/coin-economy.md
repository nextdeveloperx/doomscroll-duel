# Coin economy

Coins are **virtual**. They are earned by playing, spent inside the app, and moved between friends as duel stakes.
They are never sold, never given by a subscription, and never paid out.

- No in-app product sells coins. The only thing sold through Google Play is the Pro subscription.
- Pro gives no coins and no coin bonus. Pro skins are free to wear while Pro, and cannot be bought with coins.
- Coins cannot be cashed out, gifted, or exchanged for anything outside the app.
- Because coins cannot be bought, a duel stake is not betting. The stake only moves coins that were earned by playing.
- `scripts/check-coin-money-separation.sh` fails the build if billing code mentions coins, coin code imports billing,
  an in-app product or consume call appears, or the purchase functions touch wallets.

Source of truth: `app/.../domain/coins/CoinRules.kt` (app) and `functions/src/coin-rules.json` (server).
`CoinRulesParityTest` fails when the two disagree.

## Earning

| Source | Coins | Limit per day | Counts toward the daily cap | Conditions |
|---|---|---|---|---|
| Daily check-in | 10 | 1 | yes | Tap check-in in the app. |
| Win a duel | 20 (bonus; the loser's stake also moves to you) | 3 wins | yes | Duel lasted at least 1 hour, both players were tracking, and you were not already paid for a win over the same opponent today. |
| Streak day | 5 | 1 | yes | Stayed under your daily reel limit, streak of 2 days or more. |
| Night Pact finished | 30 | 1 | yes | Zero reels from start to end, pact completed fully. |
| Streak milestone | 50 at 7 days, 200 at 30, 500 at 100 | once per milestone | no | Paid once when the streak reaches that length. A streak has to be rebuilt from zero to earn it again. |
| Welcome coins | 50 | once ever | no | Given by the server when the account is created. |

- **Daily earn cap: 150 coins.** The natural maximum without milestones is 105 (10 + 3x20 + 5 + 30), so the cap only
  stops farming. When a grant would pass the cap, only the part that fits is paid; after that, nothing more that day.
- A day is the player's local calendar day (the server uses the player's saved time zone, India time if none).
- The same event can never pay twice (every grant has a reference id; a repeat is ignored).
- Wins against the same opponent pay once per day, so two friends cannot trade wins to farm coins.

## Spending

| Use | Cost | Notes |
|---|---|---|
| Duel stake | 25, 50 or 100 | Held in escrow when the duel starts, moved to the winner, refunded on a tie, cancel or expiry. Total coins in the system do not change. |
| Brain skin: Cool | 150 | Bought once, kept. |
| Brain skin: Sleepy | 250 | Bought once, kept. |
| Brain skin: Gold | Pro only | Free to wear while Pro. Falls back to the default skin when Pro ends; it is not deleted. |
| Brain skin: Neon | Pro only | Same as Gold. |
| Theme: Sunset | 200 | Bought once, kept. |
| Theme: Midnight | 400 | Bought once, kept. |
| Roast sticker: basic | 20 per send | Used up each time. |
| Roast sticker: spicy | 50 per send | Used up each time. |

A balance can never go below zero; a purchase that cannot be paid is refused and changes nothing.

## Where it is enforced
- App: `CoinWallet` (pure functions, 19 tests) for showing numbers and previewing offline.
- Server: `functions/src/coinRules.ts` + `claimCheckIn` (daily check-in is the only coin source a phone can ask for).
  Win, streak, milestone and Night Pact coins are paid by the settlement functions using the same `earn()` rules.
  `wallets/{uid}` is readable by its owner and writable by no client (`firestore.rules`).
