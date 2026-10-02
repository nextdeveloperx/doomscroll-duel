// Pure rules for usernames, invites and contact matching. No Firebase here, so they are unit-tested with plain node.
// The username rules mirror the app's `Username.kt` and the invite alphabet mirrors `InviteCode.kt`: if one changes, change the other.

import { createHash } from "node:crypto";

export const USERNAME_MIN = 3;
export const USERNAME_MAX = 20;
const RESERVED = new Set(["admin", "support", "help", "doomscroll", "duel", "official", "moderator", "system"]);

export type UsernameProblem = "too_short" | "too_long" | "bad_characters" | "must_start_with_letter" | "bad_separators" | "reserved";
export type UsernameParse = { ok: true; name: string } | { ok: false; problem: UsernameProblem };

/** Trims, drops a leading "@" and lowercases, then checks the rules. */
export function parseUsername(input: unknown): UsernameParse {
  if (typeof input !== "string") return { ok: false, problem: "too_short" };
  const name = input.trim().replace(/^@/, "").toLowerCase();
  if (name.length < USERNAME_MIN) return { ok: false, problem: "too_short" };
  if (name.length > USERNAME_MAX) return { ok: false, problem: "too_long" };
  if (!/^[a-z0-9_.]+$/.test(name)) return { ok: false, problem: "bad_characters" };
  if (!/^[a-z]/.test(name)) return { ok: false, problem: "must_start_with_letter" };
  if (/[_.]$/.test(name) || name.includes("..") || name.includes("__") || name.includes("._") || name.includes("_.")) {
    return { ok: false, problem: "bad_separators" };
  }
  if (RESERVED.has(name)) return { ok: false, problem: "reserved" };
  return { ok: true, name };
}

export const INVITE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
export const INVITE_LENGTH = 8;
export const INVITE_TTL_MS = 24 * 60 * 60 * 1000;
/** A link shared in a group may be used by several people, but not by an unlimited crowd. */
export const INVITE_MAX_USES = 20;
/** Do not push the same person about the same inviter more than once in this time. */
export const RESEND_COOLDOWN_MS = 60 * 60 * 1000;

export function makeInviteCode(random: () => number = Math.random): string {
  let code = "";
  for (let i = 0; i < INVITE_LENGTH; i++) code += INVITE_ALPHABET[Math.floor(random() * INVITE_ALPHABET.length) % INVITE_ALPHABET.length];
  return code;
}

export function parseInviteCode(input: unknown): string | null {
  if (typeof input !== "string") return null;
  const code = input.trim().toUpperCase();
  return code.length === INVITE_LENGTH && [...code].every((c) => INVITE_ALPHABET.includes(c)) ? code : null;
}

export type InviteVerdict = "ok" | "expired" | "used_up" | "own_invite" | "not_for_you";

export function inviteVerdict(
  invite: { fromUid: string; expiresAtMs: number; uses: number; targetUid?: string | null },
  uid: string,
  nowMs: number,
): InviteVerdict {
  if (invite.fromUid === uid) return "own_invite";
  if (nowMs >= invite.expiresAtMs) return "expired";
  if (invite.uses >= INVITE_MAX_USES) return "used_up";
  if (invite.targetUid && invite.targetUid !== uid) return "not_for_you";
  return "ok";
}

/**
 * The hash used to find a contact. The phone hashes each contact email the same way, so the server never gets the
 * email itself from the phone, and can compare only with the hash it stored for the people who signed in.
 */
export function emailHash(email: string): string {
  return createHash("sha256").update(email.trim().toLowerCase()).digest("hex");
}

export const MAX_CONTACT_HASHES = 1000;
export const HASH_PATTERN = /^[0-9a-f]{64}$/;

/** Keeps only well-formed, distinct hashes, at most [MAX_CONTACT_HASHES]. */
export function cleanHashes(input: unknown): string[] {
  if (!Array.isArray(input)) return [];
  const seen = new Set<string>();
  for (const h of input) {
    if (typeof h === "string" && HASH_PATTERN.test(h)) seen.add(h);
    if (seen.size >= MAX_CONTACT_HASHES) break;
  }
  return [...seen];
}

export function chunk<T>(items: readonly T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size));
  return out;
}
