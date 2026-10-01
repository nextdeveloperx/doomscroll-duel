// Server-side gates that other functions (createDuel, createSquad, ...) call before they create anything.
// The app's own Pro checks only change what the screen shows; THESE are the ones that count.
import { Entitlement, grantsPro } from "./playMapping";

export const FREE_DUELS_PER_DAY = 3;
export type Gate = { allowed: true } | { allowed: false; needs: "UNLIMITED_DUELS" | "SQUAD_BATTLE" | "STRICT_LOCK" };

export const isPro = (e: Pick<Entitlement, "status" | "accessUntilMs"> | undefined, nowMs: number): boolean =>
  e !== undefined && grantsPro(e, nowMs);

/** [countedToday] excludes duels cancelled or expired before the friend accepted. Accepting is never gated. */
export function canCreateDuel(countedToday: number, pro: boolean): Gate {
  return pro || countedToday < FREE_DUELS_PER_DAY ? { allowed: true } : { allowed: false, needs: "UNLIMITED_DUELS" };
}

export const canCreateSquad = (pro: boolean): Gate => (pro ? { allowed: true } : { allowed: false, needs: "SQUAD_BATTLE" });
