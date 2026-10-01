// Pure rules for friend unlock. Kept free of Firebase imports so they can be unit tested with node:test.
// Must match domain/blocking/UnlockRequests.kt in the app.

export const DAILY_LIMIT = 3;
export const WINDOW_MS = 24 * 60 * 60 * 1000; // rolling 24 hours, not a calendar day
export const REQUEST_TTL_MS = 30 * 60 * 1000;

export type Status = "pending" | "approved" | "denied" | "expired";

export interface PastRequest {
  createdAtMs: number;
  status: Status;
}

export type AskVerdict = "ok" | "quota" | "pending";

/** Requests that count toward the quota are all those created in the last 24 h. */
export function askVerdict(past: PastRequest[], nowMs: number): AskVerdict {
  const recent = past.filter((r) => nowMs - r.createdAtMs < WINDOW_MS);
  if (recent.some((r) => r.status === "pending" && nowMs - r.createdAtMs < REQUEST_TTL_MS)) return "pending";
  if (recent.length >= DAILY_LIMIT) return "quota";
  return "ok";
}

export function isExpired(createdAtMs: number, nowMs: number): boolean {
  return nowMs - createdAtMs >= REQUEST_TTL_MS;
}
