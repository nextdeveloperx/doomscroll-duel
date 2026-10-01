// Account deletion. Pure plan + a thin Firebase store, so the order and the coverage can be tested without an emulator.
//
// Rules this file keeps:
//  - EVERY place the server keeps per-person data is listed in DATA_REGISTRY, and a test fails when firestore.rules
//    gains a collection that is not listed. Adding a collection without deciding how it is deleted is a failed build.
//  - Steps are idempotent: running the whole thing twice is safe, so a failure part way is fixed by trying again.
//  - The sign-in account is deleted LAST. Until then the person can still sign in and retry.
//  - Another player's own records are not deleted: this person's side is removed or anonymised ("deleted").

export interface DeletionStore {
  /** Best effort: stops Google Play from billing again. Must never throw. */
  cancelSubscriptions(uid: string): Promise<void>;
  /** Deletes every document (and its subcollections) in [collection] whose [field] equals [uid]. */
  deleteWhere(collection: string, field: string, uid: string): Promise<void>;
  deleteTree(path: string): Promise<void>;
  /** Removes this person from every friend's friends list. */
  removeFriendEdges(uid: string): Promise<void>;
  /** Removes this person's side of every duel; a duel with nobody left is deleted. */
  detachFromDuels(uid: string): Promise<void>;
  deleteFiles(prefix: string): Promise<void>;
  deleteAuthUser(uid: string): Promise<void>;
}

export type DataKind = "personal" | "server_only" | "no_personal_data";

/**
 * Where per-person data lives and how deletion handles it. Keys are top-level Firestore collections (plus Storage
 * prefixes and Auth). `docs/play/account-deletion-page.md` and the Play Data safety answers are written from this table.
 */
export const DATA_REGISTRY: Record<string, { kind: DataKind; handledBy: string }> = {
  usernames: { kind: "personal", handledBy: "usernames" },
  users: { kind: "personal", handledBy: "profile + friend_edges" },
  invites: { kind: "personal", handledBy: "invites" },
  duels: { kind: "personal", handledBy: "duels" },
  unlockRequests: { kind: "personal", handledBy: "unlock_requests" },
  entitlements: { kind: "personal", handledBy: "entitlement" },
  wallets: { kind: "personal", handledBy: "wallet" },
  purchaseTokens: { kind: "server_only", handledBy: "purchase_tokens" },
};

export const STORAGE_PREFIXES = (uid: string): string[] => [`proofs/${uid}/`, `avatars/${uid}/`];

export interface Step {
  name: string;
  run: (store: DeletionStore, uid: string) => Promise<void>;
}

export const DELETION_STEPS: readonly Step[] = [
  // Needs the purchase tokens, so it runs before they are deleted.
  { name: "cancel_subscriptions", run: (s, uid) => s.cancelSubscriptions(uid) },
  { name: "unlock_requests", run: async (s, uid) => { await s.deleteWhere("unlockRequests", "fromUid", uid); await s.deleteWhere("unlockRequests", "toUid", uid); } },
  { name: "friend_edges", run: (s, uid) => s.removeFriendEdges(uid) },
  { name: "invites", run: (s, uid) => s.deleteWhere("invites", "fromUid", uid) },
  { name: "duels", run: (s, uid) => s.detachFromDuels(uid) },
  { name: "usernames", run: (s, uid) => s.deleteWhere("usernames", "uid", uid) },
  { name: "purchase_tokens", run: (s, uid) => s.deleteWhere("purchaseTokens", "uid", uid) },
  { name: "entitlement", run: (s, uid) => s.deleteTree(`entitlements/${uid}`) },
  { name: "wallet", run: (s, uid) => s.deleteTree(`wallets/${uid}`) },
  { name: "profile", run: (s, uid) => s.deleteTree(`users/${uid}`) },
  { name: "files", run: async (s, uid) => { for (const p of STORAGE_PREFIXES(uid)) await s.deleteFiles(p); } },
  // Last on purpose: until now the person can still sign in and try again.
  { name: "auth_user", run: (s, uid) => s.deleteAuthUser(uid) },
];

/** Runs every step in order. Throws on the first failure, leaving the sign-in account in place so a retry is possible. */
export async function deleteAllUserData(uid: string, store: DeletionStore): Promise<string[]> {
  const done: string[] = [];
  for (const step of DELETION_STEPS) {
    await step.run(store, uid);
    done.push(step.name);
  }
  return done;
}

export const RECENT_LOGIN_MAX_AGE_MS = 5 * 60 * 1000;

/** The person must have signed in within the last few minutes, so a stolen unlocked phone cannot delete an account. */
export function loginIsRecent(authTimeSeconds: number | undefined, nowMs: number): boolean {
  if (typeof authTimeSeconds !== "number" || !Number.isFinite(authTimeSeconds)) return false;
  const age = nowMs - authTimeSeconds * 1000;
  return age >= -60_000 && age <= RECENT_LOGIN_MAX_AGE_MS;
}
