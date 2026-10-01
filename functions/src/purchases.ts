import { createHash } from "crypto";
import { androidpublisher, androidpublisher_v3 } from "@googleapis/androidpublisher";
import { GoogleAuth } from "google-auth-library";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { defineString } from "firebase-functions/params";
import { Entitlement, PlaySubscription, PurchaseRejected, PRODUCT_ID, mapSubscription, needsAcknowledge } from "./playMapping";

const PACKAGE_NAME = defineString("PLAY_PACKAGE_NAME", { default: "com.doomscrollduel" });
const db = () => getFirestore();

let publisher: androidpublisher_v3.Androidpublisher | undefined;
function play(): androidpublisher_v3.Androidpublisher {
  // Uses the function's own service account. That account must be invited in Play Console > Users and permissions
  // with "View financial data" and "Manage orders and subscriptions". See docs/billing.md.
  publisher ??= androidpublisher({
    version: "v3",
    auth: new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] }),
  });
  return publisher;
}

export const tokenHash = (token: string): string => createHash("sha256").update(token).digest("hex");

async function fetchSubscription(token: string): Promise<PlaySubscription> {
  const res = await play().purchases.subscriptionsv2.get({ packageName: PACKAGE_NAME.value(), token });
  return res.data as PlaySubscription;
}

async function acknowledge(token: string, sub: PlaySubscription): Promise<void> {
  if (!needsAcknowledge(sub)) return;
  // Google refunds a purchase that is not acknowledged within 3 days, so this must not be skipped.
  await play().purchases.subscriptions.acknowledge({
    packageName: PACKAGE_NAME.value(),
    subscriptionId: PRODUCT_ID,
    token,
    requestBody: {},
  });
}

/**
 * Checks one purchase token with Google, links it to [uid] (one token, one account), and writes the entitlement.
 * The phone never writes the entitlement: it can only send a token, and a token that does not verify as a live Pro
 * subscription bought by THIS account changes nothing.
 */
export async function verifyAndStore(uid: string, token: string, nowMs: number): Promise<Entitlement> {
  const sub = await fetchSubscription(token);
  const ent = mapSubscription(sub, uid, nowMs);

  const link = db().collection("purchaseTokens").doc(tokenHash(token));
  await db().runTransaction(async (tx) => {
    const existing = await tx.get(link);
    const owner = existing.exists ? (existing.get("uid") as string) : undefined;
    if (owner && owner !== uid) throw new HttpsError("already-exists", "This purchase is already used by another account.");
    // The token is kept server-side only (rules deny all client access) so the safety-net job can look it up again.
    if (!owner) tx.set(link, { uid, token, createdAt: FieldValue.serverTimestamp() });
    tx.set(db().collection("entitlements").doc(uid), { ...ent, tokenHash: tokenHash(token) });
  });

  // A resubscribe or plan change replaces the old token: retire it so it stops pointing at this account.
  if (sub.linkedPurchaseToken) {
    await db().collection("purchaseTokens").doc(tokenHash(sub.linkedPurchaseToken)).set({ uid, supersededBy: tokenHash(token) }, { merge: true });
  }
  await acknowledge(token, sub);
  return ent;
}

/** Called by the app after a purchase and on "Restore". Returns what the server now believes. */
export const verifyPurchase = onCall(async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
  const token = req.data?.purchaseToken;
  if (typeof token !== "string" || token.length < 10 || token.length > 4096) throw new HttpsError("invalid-argument", "purchaseToken");
  try {
    const ent = await verifyAndStore(uid, token, Date.now());
    return { status: ent.status, accessUntilMs: ent.accessUntilMs, serverNowMs: ent.verifiedAtMs };
  } catch (e) {
    if (e instanceof PurchaseRejected) throw new HttpsError(e.code === "wrong_account" ? "permission-denied" : "failed-precondition", e.message);
    if (e instanceof HttpsError) throw e;
    const code = (e as { code?: number }).code;
    if (code === 404 || code === 410) throw new HttpsError("not-found", "Google does not know this purchase.");
    console.error("verifyPurchase failed", e);
    throw new HttpsError("unavailable", "Could not check with Google Play. Try again.");
  }
});

/** Re-reads one token from Google for whoever owns it. Used by RTDN and the safety-net job. */
async function refreshToken(token: string): Promise<void> {
  const link = await db().collection("purchaseTokens").doc(tokenHash(token)).get();
  const uid = link.get("uid") as string | undefined;
  if (!uid) return; // not ours yet: the app will send it through verifyPurchase when the user opens it
  await verifyAndStore(uid, token, Date.now()); // an error here makes Pub/Sub retry
}

/**
 * Real-time developer notifications: Play publishes renew / cancel / grace / hold / revoke / expire events to a
 * Pub/Sub topic. We do not trust the message's content, we only use it as "go look again" for that token.
 */
export const playNotifications = onMessagePublished("play-rtdn", async (event) => {
  const raw = event.data.message.data;
  const body = JSON.parse(Buffer.from(raw, "base64").toString("utf8")) as {
    packageName?: string;
    subscriptionNotification?: { purchaseToken?: string };
    voidedPurchaseNotification?: { purchaseToken?: string };
  };
  if (body.packageName && body.packageName !== PACKAGE_NAME.value()) return;
  const token = body.subscriptionNotification?.purchaseToken ?? body.voidedPurchaseNotification?.purchaseToken;
  if (token) await refreshToken(token);
});

/**
 * Safety net for a lost notification: every 6 hours, look again at entitlements that say "has access" but whose end
 * time has passed or is near, so a renewal we missed does not leave a paying user locked out.
 */
export const refreshStaleEntitlements = onSchedule("every 6 hours", async () => {
  const now = Date.now();
  const snap = await db()
    .collection("entitlements")
    .where("status", "in", ["ACTIVE", "CANCELED", "GRACE", "ON_HOLD"])
    .where("accessUntilMs", "<", now + 6 * 3_600_000)
    .limit(200)
    .get();
  for (const doc of snap.docs) {
    const hash = doc.get("tokenHash") as string | undefined;
    const token = hash ? ((await db().collection("purchaseTokens").doc(hash).get()).get("token") as string | undefined) : undefined;
    try {
      if (token) await verifyAndStore(doc.id, token, now);
      else if ((doc.get("accessUntilMs") as number) < now) await doc.ref.update({ status: "EXPIRED", autoRenewing: false, verifiedAtMs: now });
    } catch (e) {
      // Google could not be reached or says no: if the paid time has passed, access must not linger.
      console.error("refresh failed for", doc.id, e);
      if ((doc.get("accessUntilMs") as number) < now) await doc.ref.update({ status: "EXPIRED", autoRenewing: false, verifiedAtMs: now });
    }
  }
});
