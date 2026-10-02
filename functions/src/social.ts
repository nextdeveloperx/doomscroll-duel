// Accounts, usernames, friends, invites and contact matching. Everything here is written by the server only:
// the Firestore rules give clients no write access to usernames, invites or friend edges (see firebase/firestore.rules).
//
// Return values, not errors, carry the outcomes a person can cause on purpose (a taken username, an expired invite),
// so the app can show a friendly line. Errors are for a broken request or a person who is not signed in.

import { getFirestore, Timestamp, FieldValue, Firestore, Transaction } from "firebase-admin/firestore";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { push } from "./push";
import {
  chunk, cleanHashes, emailHash, INVITE_TTL_MS, inviteVerdict, makeInviteCode, parseInviteCode,
  parseUsername, RESEND_COOLDOWN_MS,
} from "./socialRules";

// getFirestore() is read lazily: index.ts calls initializeApp() after its imports have run.
const db = (): Firestore => getFirestore();

function needUid(req: CallableRequest): string {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
  return uid;
}

interface Profile { uid: string; username: string; displayName: string }

async function profileOf(uid: string): Promise<Profile | null> {
  const snap = await db().collection("users").doc(uid).get();
  const username = snap.get("username");
  if (!snap.exists || typeof username !== "string") return null;
  return { uid, username, displayName: String(snap.get("displayName") ?? username) };
}

function asFriend(p: Profile) {
  return { uid: p.uid, username: p.username, displayName: p.displayName };
}

/** Writes both sides of a friendship inside the caller's transaction. */
function linkFriends(tx: Transaction, a: Profile, b: Profile): void {
  const now = Timestamp.now();
  tx.set(db().collection("users").doc(a.uid).collection("friends").doc(b.uid), { ...asFriend(b), since: now });
  tx.set(db().collection("users").doc(b.uid).collection("friends").doc(a.uid), { ...asFriend(a), since: now });
}

// ----- username and profile -----------------------------------------------------------------------------------------

/** Is this username free? Cheap check for the typing screen; the claim in [completeProfile] is the real decision. */
export const checkUsername = onCall(async (req) => {
  needUid(req);
  const parsed = parseUsername(req.data?.username);
  if (!parsed.ok) return { available: false, problem: parsed.problem };
  const taken = (await db().collection("usernames").doc(parsed.name).get()).exists;
  return { available: !taken, username: parsed.name, problem: taken ? "taken" : null };
});

/**
 * First sign-in: claims the username and creates the profile in one transaction, so two people can never get the same
 * name. The email hash comes from the verified sign-in token (never from the app), so contact matching cannot be faked.
 */
export const completeProfile = onCall(async (req) => {
  const uid = needUid(req);
  const parsed = parseUsername(req.data?.username);
  if (!parsed.ok) return { result: "invalid", problem: parsed.problem };

  const rawName = typeof req.data?.displayName === "string" ? req.data.displayName.trim() : "";
  const tokenName = typeof req.auth?.token?.name === "string" ? req.auth.token.name.trim() : "";
  const displayName = (rawName || tokenName || parsed.name).slice(0, 30);
  const email = req.auth?.token?.email_verified === true && typeof req.auth.token.email === "string" ? req.auth.token.email : null;

  const userRef = db().collection("users").doc(uid);
  const nameRef = db().collection("usernames").doc(parsed.name);
  const outcome = await db().runTransaction(async (tx) => {
    const [user, name] = await Promise.all([tx.get(userRef), tx.get(nameRef)]);
    if (user.exists && typeof user.get("username") === "string") return "has_profile" as const;
    if (name.exists) return "taken" as const;
    tx.set(nameRef, { uid, createdAt: Timestamp.now() });
    tx.set(db().collection("directory").doc(uid), { username: parsed.name, displayName, createdAt: Timestamp.now() });
    tx.set(
      userRef,
      {
        username: parsed.name,
        displayName,
        avatarColor: "pink",
        createdAt: Timestamp.now(),
        ...(email ? { emailHash: emailHash(email) } : {}),
      },
      { merge: true },
    );
    return "created" as const;
  });
  return outcome === "created" ? { result: "created", username: parsed.name, displayName } : { result: outcome };
});

// ----- invites ------------------------------------------------------------------------------------------------------

async function newInvite(fromUid: string, targetUid: string | null): Promise<{ code: string; expiresAtMs: number }> {
  const expiresAtMs = Date.now() + INVITE_TTL_MS;
  for (let attempt = 0; attempt < 6; attempt++) {
    const code = makeInviteCode();
    try {
      await db().collection("invites").doc(code).create({
        fromUid,
        targetUid,
        uses: 0,
        createdAt: Timestamp.now(),
        expiresAtMs,
        expiresAt: Timestamp.fromMillis(expiresAtMs),
      });
      return { code, expiresAtMs };
    } catch (e) {
      if ((e as { code?: number }).code !== 6) throw e; // 6 = ALREADY_EXISTS: pick another code
    }
  }
  throw new HttpsError("internal", "Could not make an invite code.");
}

/** A link the person can share anywhere. Good for 24 hours. */
export const createInvite = onCall(async (req) => {
  const uid = needUid(req);
  if (!(await profileOf(uid))) throw new HttpsError("failed-precondition", "Choose a username first.");
  return newInvite(uid, null);
});

export const acceptInvite = onCall(async (req) => {
  const uid = needUid(req);
  const code = parseInviteCode(req.data?.code);
  if (!code) return { result: "invalid" };
  const me = await profileOf(uid);
  if (!me) throw new HttpsError("failed-precondition", "Choose a username first.");

  const inviteRef = db().collection("invites").doc(code);
  const outcome = await db().runTransaction(async (tx) => {
    const invite = await tx.get(inviteRef);
    if (!invite.exists) return { result: "invalid" as const };
    const fromUid = invite.get("fromUid") as string;
    const verdict = inviteVerdict(
      { fromUid, expiresAtMs: invite.get("expiresAtMs") as number, uses: invite.get("uses") as number, targetUid: invite.get("targetUid") as string | null },
      uid,
      Date.now(),
    );
    if (verdict !== "ok") return { result: verdict };
    const inviterSnap = await tx.get(db().collection("users").doc(fromUid));
    const inviterName = inviterSnap.get("username");
    if (typeof inviterName !== "string") return { result: "invalid" as const };
    const inviter: Profile = { uid: fromUid, username: inviterName, displayName: String(inviterSnap.get("displayName") ?? inviterName) };
    const edge = await tx.get(db().collection("users").doc(uid).collection("friends").doc(fromUid));
    if (edge.exists) return { result: "already_friends" as const, friend: asFriend(inviter) };
    linkFriends(tx, me, inviter);
    tx.update(inviteRef, { uses: FieldValue.increment(1) });
    return { result: "accepted" as const, friend: asFriend(inviter) };
  });
  if (outcome.result === "accepted" && "friend" in outcome) {
    await push(outcome.friend.uid, { type: "friend_joined", fromName: me.displayName, fromUsername: me.username });
  }
  return outcome;
});

/** Friend by username, no code needed. */
export const addFriendByUsername = onCall(async (req) => {
  const uid = needUid(req);
  const parsed = parseUsername(req.data?.username);
  if (!parsed.ok) return { result: "not_found" };
  const me = await profileOf(uid);
  if (!me) throw new HttpsError("failed-precondition", "Choose a username first.");
  const nameSnap = await db().collection("usernames").doc(parsed.name).get();
  const otherUid = nameSnap.get("uid");
  if (!nameSnap.exists || typeof otherUid !== "string") return { result: "not_found" };
  if (otherUid === uid) return { result: "self" };
  const other = await profileOf(otherUid);
  if (!other) return { result: "not_found" };

  const outcome = await db().runTransaction(async (tx) => {
    const edge = await tx.get(db().collection("users").doc(uid).collection("friends").doc(otherUid));
    if (edge.exists) return { result: "already_friends" as const, friend: asFriend(other) };
    linkFriends(tx, me, other);
    return { result: "added" as const, friend: asFriend(other) };
  });
  if (outcome.result === "added") {
    await push(otherUid, { type: "friend_joined", fromName: me.displayName, fromUsername: me.username });
  }
  return outcome;
});

// ----- contacts -----------------------------------------------------------------------------------------------------

/**
 * Which of the phone's contacts already use the app? The phone sends only SHA-256 hashes of contact emails. The server
 * compares them with the hash stored for each person who signed in, returns the matches, and keeps neither the list
 * nor the result.
 */
export const matchContacts = onCall(async (req) => {
  const uid = needUid(req);
  const hashes = cleanHashes(req.data?.hashes);
  if (hashes.length === 0) return { matches: [] };

  const friendsSnap = await db().collection("users").doc(uid).collection("friends").get();
  const friendIds = new Set(friendsSnap.docs.map((d) => d.id));

  const matches: Array<{ uid: string; username: string; displayName: string; isFriend: boolean }> = [];
  const snaps = await Promise.all(chunk(hashes, 30).map((part) => db().collection("users").where("emailHash", "in", part).get()));
  for (const snap of snaps) {
    for (const doc of snap.docs) {
      const username = doc.get("username");
      if (doc.id === uid || typeof username !== "string") continue;
      matches.push({ uid: doc.id, username, displayName: String(doc.get("displayName") ?? username), isFriend: friendIds.has(doc.id) });
    }
  }
  return { matches };
});

/**
 * "Join karo": a personal invite to someone who already has the app. They get a push with the code; tapping it opens the
 * app and accepts. At most one push per person per hour, so nobody can be spammed.
 */
export const inviteUser = onCall(async (req) => {
  const uid = needUid(req);
  const targetUid = req.data?.targetUid;
  if (typeof targetUid !== "string" || targetUid === uid) throw new HttpsError("invalid-argument", "targetUid");
  const [me, target] = await Promise.all([profileOf(uid), profileOf(targetUid)]);
  if (!me) throw new HttpsError("failed-precondition", "Choose a username first.");
  if (!target) return { result: "not_found" };

  const edge = await db().collection("users").doc(uid).collection("friends").doc(targetUid).get();
  if (edge.exists) return { result: "already_friends" };

  const now = Date.now();
  const sendRef = db().collection("users").doc(uid).collection("inviteSends").doc(targetUid);
  const previous = await sendRef.get();
  if (previous.exists && now - (previous.get("atMs") as number) < RESEND_COOLDOWN_MS && (previous.get("expiresAtMs") as number) > now) {
    return { result: "already_sent", code: previous.get("code") as string };
  }

  const invite = await newInvite(uid, targetUid);
  await sendRef.set({ code: invite.code, atMs: now, expiresAtMs: invite.expiresAtMs });
  await push(targetUid, { type: "friend_invite", code: invite.code, fromName: me.displayName, fromUsername: me.username });
  return { result: "sent", code: invite.code };
});


/**
 * Stores the contact-matching hash for a profile the app created itself (while `completeProfile` was not deployed). The hash
 * comes from the verified sign-in token, never from the app, so it cannot be forged. Safe to call any number of times.
 */
export const registerEmailHash = onCall(async (req) => {
  const uid = needUid(req);
  const token = req.auth?.token;
  if (token?.email_verified !== true || typeof token.email !== "string") return { ok: false };
  const ref = db().collection("users").doc(uid);
  const snap = await ref.get();
  if (!snap.exists || typeof snap.get("username") !== "string") return { ok: false };
  await ref.set({ emailHash: emailHash(token.email) }, { merge: true });
  return { ok: true };
});
