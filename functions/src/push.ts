import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

/** Sends a data-only, high-priority push to every device the person is signed in on, and forgets dead tokens. */
export async function push(uid: string, data: Record<string, string>): Promise<void> {
  const db = getFirestore();
  const snap = await db.collection("users").doc(uid).collection("fcmTokens").get();
  const tokens = snap.docs.map((d) => d.id);
  if (tokens.length === 0) return;
  const res = await getMessaging().sendEachForMulticast({ tokens, data, android: { priority: "high" } });
  const dead = res.responses
    .map((r, i) => ({ r, token: tokens[i] }))
    .filter(({ r }) => !r.success && r.error?.code === "messaging/registration-token-not-registered");
  await Promise.all(dead.map(({ token }) => db.collection("users").doc(uid).collection("fcmTokens").doc(token).delete()));
}
