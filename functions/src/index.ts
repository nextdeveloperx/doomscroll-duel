import { initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { askVerdict, isExpired, PastRequest, Status, WINDOW_MS } from "./unlockRules";

initializeApp();
const db = getFirestore();

async function tokensOf(uid: string): Promise<string[]> {
  const snap = await db.collection("users").doc(uid).collection("fcmTokens").get();
  return snap.docs.map((d) => d.id);
}

async function push(uid: string, data: Record<string, string>): Promise<void> {
  const tokens = await tokensOf(uid);
  if (tokens.length === 0) return;
  // Data-only, high priority: the app builds the Approve/Deny notification itself.
  const res = await getMessaging().sendEachForMulticast({ tokens, data, android: { priority: "high" } });
  const dead = res.responses
    .map((r, i) => ({ r, token: tokens[i] }))
    .filter(({ r }) => !r.success && r.error?.code === "messaging/registration-token-not-registered");
  await Promise.all(
    dead.map(({ token }) => db.collection("users").doc(uid).collection("fcmTokens").doc(token).delete()),
  );
}

/** The caller asks one friend to lift their Strict Lock. Server decides quota, so a modified app cannot cheat it. */
export const requestUnlock = onCall(async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
  const buddyUid = req.data?.buddyUid;
  if (typeof buddyUid !== "string" || buddyUid === uid) throw new HttpsError("invalid-argument", "buddyUid");

  const friend = await db.collection("users").doc(uid).collection("friends").doc(buddyUid).get();
  if (!friend.exists) throw new HttpsError("failed-precondition", "Not friends.");

  const nowMs = Date.now();
  const ref = db.collection("unlockRequests").doc();

  await db.runTransaction(async (tx) => {
    const recent = await tx.get(
      db.collection("unlockRequests")
        .where("fromUid", "==", uid)
        .where("createdAt", ">", Timestamp.fromMillis(nowMs - WINDOW_MS)),
    );
    const past: PastRequest[] = recent.docs.map((d) => ({
      createdAtMs: (d.get("createdAt") as Timestamp).toMillis(),
      status: d.get("status") as Status,
    }));
    const verdict = askVerdict(past, nowMs);
    if (verdict === "quota") throw new HttpsError("resource-exhausted", "3 requests per day used.");
    if (verdict === "pending") throw new HttpsError("already-exists", "One request is still waiting.");
    tx.create(ref, { fromUid: uid, toUid: buddyUid, status: "pending", createdAt: Timestamp.fromMillis(nowMs) });
  });

  const me = await db.collection("users").doc(uid).get();
  await push(buddyUid, {
    type: "unlock_request",
    requestId: ref.id,
    fromName: String(me.get("displayName") ?? "Dost"),
    serverNowMs: String(nowMs),
  });
  return { requestId: ref.id, serverNowMs: nowMs };
});

/** Only the chosen friend can answer, only while the request is pending and fresh. */
export const respondUnlock = onCall(async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
  const { requestId, approve } = req.data ?? {};
  if (typeof requestId !== "string" || typeof approve !== "boolean") throw new HttpsError("invalid-argument", "args");

  const ref = db.collection("unlockRequests").doc(requestId);
  const nowMs = Date.now();
  const fromUid = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "No such request.");
    if (snap.get("toUid") !== uid) throw new HttpsError("permission-denied", "Not your request.");
    if (snap.get("status") !== "pending") throw new HttpsError("failed-precondition", "Already answered.");
    if (isExpired((snap.get("createdAt") as Timestamp).toMillis(), nowMs)) {
      tx.update(ref, { status: "expired" });
      throw new HttpsError("deadline-exceeded", "Request expired.");
    }
    tx.update(ref, { status: approve ? "approved" : "denied", answeredAt: Timestamp.fromMillis(nowMs) });
    return snap.get("fromUid") as string;
  });

  await push(fromUid, {
    type: "unlock_response",
    requestId,
    approved: String(approve),
    approvedAtMs: String(nowMs),
    serverNowMs: String(nowMs),
  });
  return { ok: true };
});

export { verifyPurchase, playNotifications, refreshStaleEntitlements } from "./purchases";
export { claimCheckIn } from "./claimCoins";
export { deleteAccount } from "./deleteAccount";
