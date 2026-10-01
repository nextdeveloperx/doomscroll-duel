// Server copy of the coin rules. The numbers live in coin-rules.json, which the app's CoinRulesParityTest also reads.
// Pure: no Firebase here, so the rules can be tested without an emulator.
import rules from "./coin-rules.json";

export type EarnSourceId = keyof typeof rules.earn;

export interface Tally {
  day: string;
  total: number;
  counts: Record<string, number>;
  paidOpponents: string[];
}

export const emptyTally = (day: string): Tally => ({ day, total: 0, counts: {}, paidOpponents: [] });

export type EarnOutcome =
  | { ok: true; granted: number; tally: Tally }
  | { ok: false; reason: "UNKNOWN_SOURCE" | "NOT_ELIGIBLE" | "SOURCE_LIMIT_REACHED" | "DAILY_CAP_REACHED" | "OPPONENT_ALREADY_PAID" };

export interface EarnInput {
  source: string;
  opponent?: string;
  durationMs?: number;
  bothTracked?: boolean;
  streakDays?: number;
  completedFully?: boolean;
}

/** Same decisions as CoinWallet.earn in the app. Idempotency (refId) is the caller's job, done in a transaction. */
export function earn(input: EarnInput, tallyIn: Tally, day: string): EarnOutcome {
  const def = (rules.earn as Record<string, { coins: number; perDayLimit: number; countsTowardDailyCap: boolean }>)[input.source];
  if (!def) return { ok: false, reason: "UNKNOWN_SOURCE" };
  const tally = tallyIn.day === day ? tallyIn : emptyTally(day);

  let base = def.coins;
  switch (input.source) {
    case "duel_win":
      if (!input.bothTracked || (input.durationMs ?? 0) < rules.duelWinMinDurationMs) return { ok: false, reason: "NOT_ELIGIBLE" };
      if (!input.opponent) return { ok: false, reason: "NOT_ELIGIBLE" };
      if (tally.paidOpponents.includes(input.opponent)) return { ok: false, reason: "OPPONENT_ALREADY_PAID" };
      break;
    case "streak_day":
      if ((input.streakDays ?? 0) < rules.streakDayMin) return { ok: false, reason: "NOT_ELIGIBLE" };
      break;
    case "night_pact":
      if (!input.completedFully) return { ok: false, reason: "NOT_ELIGIBLE" };
      break;
    case "streak_milestone": {
      const m = (rules.streakMilestones as Record<string, number>)[String(input.streakDays)];
      if (m === undefined) return { ok: false, reason: "NOT_ELIGIBLE" };
      base = m;
      break;
    }
  }

  if ((tally.counts[input.source] ?? 0) >= def.perDayLimit) return { ok: false, reason: "SOURCE_LIMIT_REACHED" };
  const room = def.countsTowardDailyCap ? rules.dailyEarnCap - tally.total : base;
  if (room <= 0) return { ok: false, reason: "DAILY_CAP_REACHED" };
  const granted = Math.min(base, room);
  return {
    ok: true,
    granted,
    tally: {
      day,
      total: tally.total + (def.countsTowardDailyCap ? granted : 0),
      counts: { ...tally.counts, [input.source]: (tally.counts[input.source] ?? 0) + 1 },
      paidOpponents: input.source === "duel_win" && input.opponent ? [...tally.paidOpponents, input.opponent] : tally.paidOpponents,
    },
  };
}

/** The local day ("2026-10-01") in [timeZone]. Falls back to India time when the zone is missing or invalid. */
export function dayKey(nowMs: number, timeZone?: string): string {
  const tz = timeZone ?? "Asia/Kolkata";
  try {
    return new Intl.DateTimeFormat("en-CA", { timeZone: tz, year: "numeric", month: "2-digit", day: "2-digit" }).format(nowMs);
  } catch {
    return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata", year: "numeric", month: "2-digit", day: "2-digit" }).format(nowMs);
  }
}
