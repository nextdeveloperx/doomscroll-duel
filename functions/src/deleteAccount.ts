import { createHash } from "crypto";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { DeletionStore, deleteAllUserData, loginIsRecent } from "./deletion";
import { cancelSubscription } from "./purchases";

const BATCH = 200;

function firebaseStore(): DeletionStore {
  const db = getFirestore();
  return {
    async cancelSubscriptions(uid) {
      try {
        const tokens = await db.collection("purchaseTokens").where("uid", "==", uid).get();
        for (const doc of tokens.docs) {
          const token = doc.get("token") as string | undefined;
          if (token) await cancelSubscription(token).catch((e) => console.warn("cancel failed", hashOf(uid), e?.code ?? e));
        }
      } catch (e) {
        console.warn("cancelSubscriptions failed", hashOf(uid), e); // best effort only, never blocks the deletion
      }
    },
    async deleteWhere(collection, field, uid) {
      for (;;) {
        const snap = await db.collection(collection).where(field, "==", uid).limit(BATCH).get();
        if (snap.empty) return;
        for (const doc of snap.docs) await db.recursiveDelete(doc.ref);
      }
    },
    async deleteTree(path) {
      await db.recursiveDelete(db.doc(path));
    },
    async removeFriendEdges(uid) {
      const mine = await db.collection("users").doc(uid).collection("friends").get();
      for (const edge of mine.docs) await db.doc(`users/${edge.id}/friends/${uid}`).delete();
    },
    async detachFromDuels(uid) {
      for (;;) {
        const snap = await db.collection("duels").where("players", "array-contains", uid).limit(BATCH).get();
        if (snap.empty) return;
        for (const doc of snap.docs) {
          const others = (doc.get("players") as string[]).filter((p) => p !== uid && p !== DELETED);
          await db.doc(`${doc.ref.path}/counts/${uid}`).delete();
          if (others.length === 0) await db.recursiveDelete(doc.ref);
          // The other player keeps their own history; this person's side becomes an anonymous "deleted" entry.
          else await doc.ref.update({ players: [...others, DELETED], deletedAt: FieldValue.serverTimestamp() });
        }
      }
    },
    async deleteFiles(prefix) {
      await getStorage().bucket().deleteFiles({ prefix, force: true });
    },
    async deleteAuthUser(uid) {
      try {
        await getAuth().deleteUser(uid);
      } catch (e) {
        if ((e as { code?: string }).code !== "auth/user-not-found") throw e;
      }
    },
  };
}

const DELETED = "deleted";
const hashOf = (uid: string) => createHash("sha256").update(uid).digest("hex").slice(0, 12);

/**
 * Deletes the caller's account and every piece of their data on the server. The caller must have signed in within the
 * last 5 minutes (so a borrowed unlocked phone cannot delete an account), and must say `confirm: true`.
 * Logs only a short hash of the uid, never names or counts.
 */
export const deleteAccount = onCall({ timeoutSeconds: 300, memory: "512MiB" }, async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
  if (req.data?.confirm !== true) throw new HttpsError("invalid-argument", "confirm");
  if (!loginIsRecent(req.auth?.token?.auth_time, Date.now())) {
    throw new HttpsError("unauthenticated", "recent-login-required");
  }
  try {
    const steps = await deleteAllUserData(uid, firebaseStore());
    console.info("account deleted", hashOf(uid), steps.length);
    return { deleted: true };
  } catch (e) {
    console.error("account deletion failed", hashOf(uid), e);
    throw new HttpsError("internal", "deletion-failed");
  }
});
