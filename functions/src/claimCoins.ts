import { getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import rules from "./coin-rules.json";
import { dayKey, earn, emptyTally, Tally } from "./coinRules";

/**
 * Daily check-in. The only coin source a phone can ask for directly: wins, streaks and Night Pacts are paid by the
 * settlement functions (using the same `earn` rules), because they depend on facts the server must decide.
 * Coins live in wallets/{uid}, which clients can read but never write.
 */
export const claimCheckIn = onCall(async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
  const db = getFirestore();
  const nowMs = Date.now();
  const zone = (await db.collection("users").doc(uid).get()).get("timeZone") as string | undefined;
  const day = dayKey(nowMs, zone);
  const ref = db.collection("wallets").doc(uid);
  const refId = `checkin:${day}`;

  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const balance = (snap.get("balance") as number | undefined) ?? 0;
    const tally = (snap.get("tally") as Tally | undefined) ?? emptyTally(day);
    const applied = (snap.get("appliedRefs") as string[] | undefined) ?? [];
    if (applied.includes(refId)) return { granted: 0, balance, alreadyClaimed: true };
    const out = earn({ source: "daily_check_in" }, tally, day);
    if (!out.ok) return { granted: 0, balance, alreadyClaimed: out.reason === "SOURCE_LIMIT_REACHED", reason: out.reason };
    tx.set(ref, { balance: balance + out.granted, tally: out.tally, appliedRefs: [...applied, refId].slice(-500) }, { merge: true });
    return { granted: out.granted, balance: balance + out.granted, alreadyClaimed: false };
  });
});

export const WELCOME_COINS = rules.welcomeCoins;
