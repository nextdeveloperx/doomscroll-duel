import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { readFileSync } from "node:fs";
import { doc, setDoc, getDoc, getDocs, collection, deleteDoc, updateDoc, writeBatch, serverTimestamp, query, orderBy, limit, where } from "firebase/firestore";

const rules = readFileSync(process.env.RULES, "utf8");
const env = await initializeTestEnvironment({ projectId: "demo-duel", firestore: { rules, host: "127.0.0.1", port: 8080 } });

let pass = 0, fail = 0;
async function check(name, fn) {
  try { await fn(); pass++; console.log("ok   ", name); } catch (e) { fail++; console.log("FAIL ", name, "-", String(e.message).split("\n")[0]); }
}

const alice = env.authenticatedContext("alice").firestore();
const bob = env.authenticatedContext("bob").firestore();
const anon = env.unauthenticatedContext().firestore();

// --- profile creation the way the app does it (one batch: username + profile + directory)
async function makeProfile(db, uid, name, display) {
  const b = writeBatch(db);
  b.set(doc(db, "usernames", name), { uid, createdAt: serverTimestamp() });
  b.set(doc(db, "users", uid), { username: name, displayName: display, avatarColor: "pink", createdAt: serverTimestamp() });
  b.set(doc(db, "directory", uid), { username: name, displayName: display, createdAt: serverTimestamp() });
  await b.commit();
}

await check("alice creates her profile + directory entry", () => assertSucceeds(makeProfile(alice, "alice", "alice_a", "Alice")));
await check("bob creates his profile + directory entry", () => assertSucceeds(makeProfile(bob, "bob", "bob_b", "Bob")));
await check("username already taken is refused", () => assertFails(makeProfile(alice, "alice", "bob_b", "Alice2")));
await check("signed-out cannot list the directory", () => assertFails(getDocs(collection(anon, "directory"))));
await check("signed-in can list the directory (People list)", () => assertSucceeds(getDocs(query(collection(alice, "directory"), orderBy("username"), limit(500)))));
await check("directory entry cannot carry an email", () => assertFails(setDoc(doc(alice, "directory", "alice"), { username: "alice_a", displayName: "A", email: "x@y.z", createdAt: serverTimestamp() })));
await check("alice reads own profile", () => assertSucceeds(getDoc(doc(alice, "users", "alice"))));
await check("alice cannot read bob's profile (not friends)", () => assertFails(getDoc(doc(alice, "users", "bob"))));
await check("push token: create, then update", async () => {
  await assertSucceeds(setDoc(doc(alice, "users", "alice", "fcmTokens", "t1"), { createdAt: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(alice, "users", "alice", "fcmTokens", "t1"), { createdAt: serverTimestamp() }));
});

// --- invite flow
await check("alice invites bob (inbox + sentInvites batch)", () => {
  const b = writeBatch(alice);
  b.set(doc(alice, "users", "bob", "inbox", "alice"), { fromUid: "alice", fromUsername: "alice_a", fromName: "Alice", createdAt: serverTimestamp() });
  b.set(doc(alice, "users", "alice", "sentInvites", "bob"), { createdAt: serverTimestamp() });
  return assertSucceeds(b.commit());
});
await check("a second invite to the same person is refused", () =>
  assertFails(setDoc(doc(alice, "users", "bob", "inbox", "alice"), { fromUid: "alice", fromUsername: "alice_a", fromName: "Alice", createdAt: serverTimestamp() })));
await check("cannot invite pretending to be someone else", () =>
  assertFails(setDoc(doc(bob, "users", "alice", "inbox", "alice"), { fromUid: "alice", fromUsername: "alice_a", fromName: "Alice", createdAt: serverTimestamp() })));
await check("cannot invite with a made-up username", () =>
  assertFails(setDoc(doc(bob, "users", "alice", "inbox", "bob"), { fromUid: "bob", fromUsername: "someone_else", fromName: "Bob", createdAt: serverTimestamp() })));
await check("only bob reads his inbox", async () => {
  await assertSucceeds(getDocs(collection(bob, "users", "bob", "inbox")));
  await assertFails(getDocs(collection(alice, "users", "bob", "inbox")));
});
await check("alice cannot make herself bob's friend without bob accepting", () => {
  const b = writeBatch(alice);
  b.set(doc(alice, "users", "bob", "friends", "alice"), { uid: "alice", username: "alice_a", displayName: "Alice", since: serverTimestamp() });
  return assertFails(b.commit());
});
await check("bob accepts: both edges + inbox delete in one batch", () => {
  const b = writeBatch(bob);
  b.set(doc(bob, "users", "bob", "friends", "alice"), { uid: "alice", username: "alice_a", displayName: "Alice", since: serverTimestamp() });
  b.set(doc(bob, "users", "alice", "friends", "bob"), { uid: "bob", username: "bob_b", displayName: "Bob", since: serverTimestamp() });
  b.delete(doc(bob, "users", "bob", "inbox", "alice"));
  return assertSucceeds(b.commit());
});
await check("alice now reads bob's profile (friends)", () => assertSucceeds(getDoc(doc(alice, "users", "bob"))));
await check("a forged name on a friend edge is refused", async () => {
  await makeProfile(env.authenticatedContext("carol").firestore(), "carol", "carol_c", "Carol");
  const carol = env.authenticatedContext("carol").firestore();
  await assertSucceeds(setDoc(doc(carol, "users", "bob", "inbox", "carol"), { fromUid: "carol", fromUsername: "carol_c", fromName: "Carol", createdAt: serverTimestamp() }));
  const b = writeBatch(bob);
  b.set(doc(bob, "users", "bob", "friends", "carol"), { uid: "carol", username: "fake", displayName: "Carol", since: serverTimestamp() });
  b.set(doc(bob, "users", "carol", "friends", "bob"), { uid: "bob", username: "bob_b", displayName: "Bob", since: serverTimestamp() });
  b.delete(doc(bob, "users", "bob", "inbox", "carol"));
  await assertFails(b.commit());
});
await check("bob declines carol (deletes inbox doc)", () => assertSucceeds(deleteDoc(doc(bob, "users", "bob", "inbox", "carol"))));
await check("friends list cannot be edited directly", () => assertFails(deleteDoc(doc(bob, "users", "bob", "friends", "alice"))));


// --- duels (alice and bob are friends now)
const duelData = (over = {}) => ({
  players: ["alice", "bob"], challengerUid: "alice", opponentUid: "bob", challengerName: "Alice", opponentName: "Bob",
  status: "pending", reelLimit: 100, hours: 24, stakeCoins: 50, createdAt: serverTimestamp(), ...over,
});
const duelRef = doc(collection(alice, "duels"));
const duelId = duelRef.id;
await check("alice challenges her friend bob", () => assertSucceeds(setDoc(duelRef, duelData())));
await check("challenging someone who is not a friend is refused", () =>
  assertFails(setDoc(doc(collection(alice, "duels")), duelData({ players: ["alice", "carol"], opponentUid: "carol", opponentName: "Carol" }))));
await check("a challenge with a made-up stake is refused", () =>
  assertFails(setDoc(doc(collection(alice, "duels")), duelData({ stakeCoins: 100000 }))));
await check("a challenge that starts already active is refused", () =>
  assertFails(setDoc(doc(collection(alice, "duels")), duelData({ status: "active" }))));
await check("only the two players can read the duel", async () => {
  await assertSucceeds(getDoc(doc(bob, "duels", duelId)));
  await assertFails(getDoc(doc(env.authenticatedContext("carol").firestore(), "duels", duelId)));
});
await check("each player lists their duels", () => assertSucceeds(getDocs(query(collection(bob, "duels"), where("players", "array-contains", "bob")))));
await check("the challenger cannot accept their own challenge", () =>
  assertFails(updateDoc(doc(alice, "duels", duelId), { status: "active", startAt: serverTimestamp() })));
await check("counts cannot be written before the duel is active", () =>
  assertFails(setDoc(doc(alice, "duels", duelId, "counts", "alice"), { uid: "alice", total: 1, perApp: { instagram: 1, youtube: 0, facebook: 0, snapchat: 0 }, dateKey: "2026-10-02", updatedAt: serverTimestamp() })));
await check("bob accepts: start comes from the server clock", () =>
  assertSucceeds(updateDoc(doc(bob, "duels", duelId), { status: "active", startAt: serverTimestamp() })));
await check("bob cannot change the settings while accepting", async () => {
  const other = doc(collection(alice, "duels"));
  await setDoc(other, duelData());
  await assertFails(updateDoc(doc(bob, "duels", other.id), { status: "active", startAt: serverTimestamp(), reelLimit: 1000 }));
});
const countOf = (uid, total) => ({ uid, total, perApp: { instagram: total, youtube: 0, facebook: 0, snapchat: 0 }, dateKey: "2026-10-02", updatedAt: serverTimestamp() });
await check("both players write only their own count", async () => {
  await assertSucceeds(setDoc(doc(alice, "duels", duelId, "counts", "alice"), countOf("alice", 12)));
  await assertSucceeds(setDoc(doc(bob, "duels", duelId, "counts", "bob"), countOf("bob", 30)));
  await assertFails(setDoc(doc(alice, "duels", duelId, "counts", "bob"), countOf("bob", 1)));
});
await check("a count never goes down", () => assertFails(setDoc(doc(alice, "duels", duelId, "counts", "alice"), countOf("alice", 5))));
await check("a count whose total does not match the apps is refused", () =>
  assertFails(setDoc(doc(alice, "duels", duelId, "counts", "alice"), { ...countOf("alice", 20), total: 99 })));
await check("each player reads the other's count while active", () => assertSucceeds(getDoc(doc(alice, "duels", duelId, "counts", "bob"))));
await check("finishing before the end time is refused", () => assertFails(updateDoc(doc(alice, "duels", duelId), { status: "finished" })));
await check("either player can drop a running battle", async () => {
  const other = doc(collection(alice, "duels"));
  await setDoc(other, duelData());
  await updateDoc(doc(bob, "duels", other.id), { status: "active", startAt: serverTimestamp() });
  await assertSucceeds(updateDoc(doc(alice, "duels", other.id), { status: "cancelled" }));
});
await check("a stranger cannot drop someone else's battle", async () => {
  const other = doc(collection(alice, "duels"));
  await setDoc(other, duelData());
  await updateDoc(doc(bob, "duels", other.id), { status: "active", startAt: serverTimestamp() });
  await assertFails(updateDoc(doc(env.authenticatedContext("carol").firestore(), "duels", other.id), { status: "cancelled" }));
});
await check("declining a challenge", async () => {
  const other = doc(collection(alice, "duels"));
  await setDoc(other, duelData());
  await assertSucceeds(updateDoc(doc(bob, "duels", other.id), { status: "declined" }));
});

// --- contacts index (one hash, one document, no listing)
const hashA = "a".repeat(64);
await check("a person registers their email hash", () => assertSucceeds(setDoc(doc(alice, "emailIndex", hashA), { uid: "alice", createdAt: serverTimestamp() })));
await check("a hash can be looked up one at a time", () => assertSucceeds(getDoc(doc(bob, "emailIndex", hashA))));
await check("the index cannot be listed", () => assertFails(getDocs(collection(bob, "emailIndex"))));
await check("a hash cannot point at somebody else", () => assertFails(setDoc(doc(bob, "emailIndex", "b".repeat(64)), { uid: "alice", createdAt: serverTimestamp() })));
await check("an existing hash cannot be taken over", () => assertFails(setDoc(doc(bob, "emailIndex", hashA), { uid: "bob", createdAt: serverTimestamp() })));
await check("a malformed hash is refused", () => assertFails(setDoc(doc(bob, "emailIndex", "not-a-hash"), { uid: "bob", createdAt: serverTimestamp() })));


// --- broadcast rooms (alice hosts; bob is her friend; carol is not)
const carolDb = env.authenticatedContext("carol").firestore();
const roomRef = doc(collection(alice, "broadcasts"));
const room = roomRef.id;
const memberDoc = (uid, name, username) => ({ uid, name, username, joinedAt: serverTimestamp(), seenAt: serverTimestamp() });
await check("alice opens a room", () => assertSucceeds(setDoc(roomRef, { hostUid: "alice", hostName: "Alice", title: "Alice ka broadcast", createdAt: serverTimestamp(), seenAt: serverTimestamp() })));
await check("a room cannot be made in someone else's name", () => assertFails(setDoc(doc(collection(bob, "broadcasts")), { hostUid: "alice", hostName: "Alice", title: "x", createdAt: serverTimestamp(), seenAt: serverTimestamp() })));
await check("the host joins her own room", () => assertSucceeds(setDoc(doc(alice, "broadcasts", room, "members", "alice"), memberDoc("alice", "Alice", "alice_a"))));
await check("a friend of the host joins", () => assertSucceeds(setDoc(doc(bob, "broadcasts", room, "members", "bob"), memberDoc("bob", "Bob", "bob_b"))));
await check("anybody signed in can join a room", () => assertSucceeds(setDoc(doc(carolDb, "broadcasts", room, "members", "carol"), memberDoc("carol", "Carol", "carol_c"))));
await check("the host removes a person, who then cannot come back", async () => {
  await assertFails(setDoc(doc(carolDb, "broadcasts", room, "kicked", "carol"), { uid: "carol", createdAt: serverTimestamp() })); // not the host
  await assertSucceeds(setDoc(doc(alice, "broadcasts", room, "kicked", "carol"), { uid: "carol", createdAt: serverTimestamp() }));
  await assertSucceeds(deleteDoc(doc(alice, "broadcasts", room, "members", "carol")));
  await assertFails(setDoc(doc(carolDb, "broadcasts", room, "members", "carol"), memberDoc("carol", "Carol", "carol_c")));
});
await check("a member invites a person to the room; one invite per sender, then 30 seconds", async () => {
  const invite = (extra = {}) => ({ fromUid: "bob", fromName: "Bob", roomId: room, title: "Alice ka broadcast", createdAt: serverTimestamp(), ...extra });
  await assertSucceeds(setDoc(doc(bob, "users", "carol", "broadcastInvites", "bob"), invite()));
  await assertFails(setDoc(doc(bob, "users", "carol", "broadcastInvites", "bob"), invite())); // too soon to send again
  await assertFails(setDoc(doc(bob, "users", "carol", "broadcastInvites", "alice"), invite({ fromUid: "alice" }))); // not in my name
  await assertFails(setDoc(doc(bob, "users", "carol", "broadcastInvites", "bob"), invite({ title: "made up" }))); // wrong title
  await assertFails(getDocs(collection(bob, "users", "carol", "broadcastInvites"))); // only carol reads hers
  await assertSucceeds(getDocs(collection(carolDb, "users", "carol", "broadcastInvites")));
  await assertSucceeds(deleteDoc(doc(carolDb, "users", "carol", "broadcastInvites", "bob")));
});
await check("a person who is not in the room cannot invite to it", () =>
  assertFails(setDoc(doc(carolDb, "users", "bob", "broadcastInvites", "carol"), { fromUid: "carol", fromName: "Carol", roomId: room, title: "Alice ka broadcast", createdAt: serverTimestamp() })));
await check("nobody joins as somebody else", () => assertFails(setDoc(doc(bob, "broadcasts", room, "members", "alice"), memberDoc("alice", "Alice", "alice_a"))));
await check("a member refreshes only their own time", async () => {
  await assertSucceeds(updateDoc(doc(bob, "broadcasts", room, "members", "bob"), { seenAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(bob, "broadcasts", room, "members", "bob"), { name: "Someone else" }));
});
await check("the room list can be read by a signed-in person", () => assertSucceeds(getDocs(query(collection(carolDb, "broadcasts"), orderBy("seenAt", "desc"), limit(30)))));
const sig = (from, to, type, extra = {}) => ({ from, to, type, createdAt: serverTimestamp(), ...extra });
await check("bob sends alice a connection message", () => assertSucceeds(setDoc(doc(collection(bob, "broadcasts", room, "signals")), sig("bob", "alice", "offer", { sdp: "v=0..." }))));
await check("a message cannot be sent in someone else's name", () => assertFails(setDoc(doc(collection(bob, "broadcasts", room, "signals")), sig("alice", "bob", "offer", { sdp: "v=0..." }))));
await check("a stranger cannot send into the room", () => assertFails(setDoc(doc(collection(carolDb, "broadcasts", room, "signals")), sig("carol", "alice", "offer", { sdp: "v=0..." }))));
await check("only the addressee reads and deletes messages", async () => {
  await assertSucceeds(getDocs(query(collection(alice, "broadcasts", room, "signals"), where("to", "==", "alice"))));
  await assertFails(getDocs(query(collection(bob, "broadcasts", room, "signals"), where("to", "==", "alice"))));
  const snap = await getDocs(query(collection(alice, "broadcasts", room, "signals"), where("to", "==", "alice")));
  await assertFails(deleteDoc(doc(bob, "broadcasts", room, "signals", snap.docs[0].id)));
  await assertSucceeds(deleteDoc(doc(alice, "broadcasts", room, "signals", snap.docs[0].id)));
});
await check("a member leaves; the host can also remove someone", async () => {
  await assertSucceeds(deleteDoc(doc(bob, "broadcasts", room, "members", "bob")));
  await setDoc(doc(bob, "broadcasts", room, "members", "bob"), memberDoc("bob", "Bob", "bob_b"));
  await assertSucceeds(deleteDoc(doc(alice, "broadcasts", room, "members", "bob")));
});
await check("only the host can mark the room closed, only on, and nothing else with it", async () => {
  await assertFails(updateDoc(doc(bob, "broadcasts", room), { closed: true })); // a member
  await assertFails(updateDoc(doc(carolDb, "broadcasts", room), { closed: true })); // a stranger
  await assertFails(updateDoc(doc(alice, "broadcasts", room), { closed: false })); // cannot be switched off
  await assertFails(updateDoc(doc(alice, "broadcasts", room), { closed: true, title: "changed" })); // nothing else rides along
  await assertSucceeds(updateDoc(doc(alice, "broadcasts", room), { closed: true }));
});
await check("only the host closes the room", async () => {
  await assertFails(deleteDoc(doc(bob, "broadcasts", room)));
  await assertSucceeds(deleteDoc(doc(alice, "broadcasts", room)));
});

// --- coin wallet ops (append-only ledger under wallets/{uid}/ops/{opId}; alice and bob are friends from above)
// A fresh ACTIVE duel for the hold tests, created the normal (rules-checked) way.
const holdDuelRef = doc(collection(alice, "duels"));
const holdDuelId = holdDuelRef.id;
await check("set up an active duel for the hold tests", async () => {
  await setDoc(holdDuelRef, duelData());
  await updateDoc(doc(bob, "duels", holdDuelId), { status: "active", startAt: serverTimestamp() });
});
await check("alice escrows her own stake (hold)", () =>
  assertSucceeds(setDoc(doc(alice, "wallets", "alice", "ops", `${holdDuelId}:hold:alice`), { kind: "hold", amount: 50, duelId: holdDuelId, at: serverTimestamp() })));
await check("the same hold id cannot be applied twice", () =>
  assertFails(setDoc(doc(alice, "wallets", "alice", "ops", `${holdDuelId}:hold:alice`), { kind: "hold", amount: 50, duelId: holdDuelId, at: serverTimestamp() })));
await check("alice cannot hold bob's stake for him", () =>
  assertFails(setDoc(doc(alice, "wallets", "bob", "ops", `${holdDuelId}:hold:bob`), { kind: "hold", amount: 50, duelId: holdDuelId, at: serverTimestamp() })));
await check("the hold amount must match the duel's stake", () =>
  assertFails(setDoc(doc(bob, "wallets", "bob", "ops", `${holdDuelId}:hold:bob`), { kind: "hold", amount: 999, duelId: holdDuelId, at: serverTimestamp() })));
await check("hold ops cannot be edited or deleted", async () => {
  await assertFails(updateDoc(doc(alice, "wallets", "alice", "ops", `${holdDuelId}:hold:alice`), { amount: 1 }));
  await assertFails(deleteDoc(doc(alice, "wallets", "alice", "ops", `${holdDuelId}:hold:alice`)));
});
await check("nobody else reads alice's wallet ops", () => assertFails(getDocs(collection(bob, "wallets", "alice", "ops"))));

// A FINISHED duel, seeded directly (bypassing rules, like a Cloud Function / the passage of real time would) so the
// settle-time rules (release, transfer) can be tested without waiting out a real multi-hour duel window.
const settleDuelId = "settle-test-duel";
const settleStake = 50;
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, "duels", settleDuelId), duelData({ status: "finished", startAt: serverTimestamp() }));
  await setDoc(doc(db, "duels", settleDuelId, "counts", "alice"), countOf("alice", 10)); // alice: fewer reels, alice wins
  await setDoc(doc(db, "duels", settleDuelId, "counts", "bob"), countOf("bob", 40));
});
await check("alice (the winner) reclaims her own held stake (release)", () =>
  assertSucceeds(setDoc(doc(alice, "wallets", "alice", "ops", `${settleDuelId}:settle:release:alice`), { kind: "release", amount: settleStake, duelId: settleDuelId, at: serverTimestamp() })));
await check("bob (the loser) cannot release his own held stake back to himself", () =>
  assertFails(setDoc(doc(bob, "wallets", "bob", "ops", `${settleDuelId}:settle:release:bob`), { kind: "release", amount: settleStake, duelId: settleDuelId, at: serverTimestamp() })));
await check("alice (the winner) credits her own wallet with bob's stake (transfer credit)", () =>
  assertSucceeds(setDoc(doc(alice, "wallets", "alice", "ops", `${settleDuelId}:settle:transfer`), { kind: "transfer_credit", amount: settleStake, duelId: settleDuelId, at: serverTimestamp() })));
await check("alice (the winner) debits bob's held stake (transfer debit)", () =>
  assertSucceeds(setDoc(doc(alice, "wallets", "bob", "ops", `${settleDuelId}:settle:transfer`), { kind: "transfer_debit", amount: settleStake, duelId: settleDuelId, at: serverTimestamp() })));
await check("bob (the loser) cannot debit his own stake away himself", () =>
  assertFails(setDoc(doc(bob, "wallets", "bob", "ops", `${settleDuelId}:settle:transfer:retry`), { kind: "transfer_debit", amount: settleStake, duelId: settleDuelId, at: serverTimestamp() })));
await check("a stranger cannot touch either wallet", () =>
  assertFails(setDoc(doc(carolDb, "wallets", "bob", "ops", `${settleDuelId}:settle:transfer:carol`), { kind: "transfer_debit", amount: settleStake, duelId: settleDuelId, at: serverTimestamp() })));

// A tied duel: both stakes simply come back (no transfer either way).
const tieDuelId = "tie-test-duel";
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, "duels", tieDuelId), duelData({ status: "finished", startAt: serverTimestamp() }));
  await setDoc(doc(db, "duels", tieDuelId, "counts", "alice"), countOf("alice", 20));
  await setDoc(doc(db, "duels", tieDuelId, "counts", "bob"), countOf("bob", 20));
});
await check("a tied duel refunds both players, nobody is a loser", async () => {
  await assertSucceeds(setDoc(doc(alice, "wallets", "alice", "ops", `${tieDuelId}:settle:release:alice`), { kind: "release", amount: settleStake, duelId: tieDuelId, at: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(bob, "wallets", "bob", "ops", `${tieDuelId}:settle:release:bob`), { kind: "release", amount: settleStake, duelId: tieDuelId, at: serverTimestamp() }));
});
await check("a tie has no transfer for either side", async () => {
  await assertFails(setDoc(doc(alice, "wallets", "alice", "ops", `${tieDuelId}:settle:transfer`), { kind: "transfer_credit", amount: settleStake, duelId: tieDuelId, at: serverTimestamp() }));
  await assertFails(setDoc(doc(alice, "wallets", "bob", "ops", `${tieDuelId}:settle:transfer`), { kind: "transfer_debit", amount: settleStake, duelId: tieDuelId, at: serverTimestamp() }));
});

// --- leaderboard fields on the public directory
await check("alice reports her own weekly coins", () =>
  assertSucceeds(updateDoc(doc(alice, "directory", "alice"), { weeklyCoins: 100, weeklyPeriodKey: "2026-W41" })));
await check("alice cannot report bob's weekly coins", () =>
  assertFails(updateDoc(doc(alice, "directory", "bob"), { weeklyCoins: 100, weeklyPeriodKey: "2026-W41" })));
await check("the weekly score cannot go down within the same week", () =>
  assertFails(updateDoc(doc(alice, "directory", "alice"), { weeklyCoins: 10, weeklyPeriodKey: "2026-W41" })));
await check("a new week may reset the score to a lower number", () =>
  assertSucceeds(updateDoc(doc(alice, "directory", "alice"), { weeklyCoins: 0, weeklyPeriodKey: "2026-W42" })));
await check("monthly coins work the same way, independently of weekly", () =>
  assertSucceeds(updateDoc(doc(alice, "directory", "alice"), { monthlyCoins: 500, monthlyPeriodKey: "2026-10" })));
await check("the directory leaderboard can be queried sorted by score", () =>
  assertSucceeds(getDocs(query(collection(bob, "directory"), orderBy("monthlyCoins", "desc"), limit(50)))));

// --- public profile: bio and social handles on the directory
await check("alice saves her bio and handles", () =>
  assertSucceeds(updateDoc(doc(alice, "directory", "alice"), { bio: "Reels kam, life zyada", instagram: "alice_a", youtube: "alice", snapchat: "", facebook: "" })));
await check("alice cannot write bob's bio", () =>
  assertFails(updateDoc(doc(alice, "directory", "bob"), { bio: "hacked" })));
await check("a bio longer than 150 characters is refused", () =>
  assertFails(updateDoc(doc(alice, "directory", "alice"), { bio: "x".repeat(151) })));
await check("a handle longer than 40 characters is refused", () =>
  assertFails(updateDoc(doc(alice, "directory", "alice"), { instagram: "x".repeat(41) })));
await check("a bio write cannot carry the username along", () =>
  assertFails(updateDoc(doc(alice, "directory", "alice"), { bio: "hi", username: "someone_else" })));
await check("a bio write cannot carry a score along", () =>
  assertFails(updateDoc(doc(alice, "directory", "alice"), { bio: "hi", monthlyCoins: 999999, monthlyPeriodKey: "2026-10" })));
await check("a bio must be a string", () =>
  assertFails(updateDoc(doc(alice, "directory", "alice"), { bio: 123 })));
await check("anybody signed in can read bob's profile entry", () =>
  assertSucceeds(getDoc(doc(alice, "directory", "bob"))));

// --- Google account photo on the directory (ProfilePhotoSync), used by every leaderboard row
const erin = env.authenticatedContext("erin").firestore();
await check("a profile can be created WITH a photoUrl, by its own owner", async () => {
  const b = writeBatch(erin);
  b.set(doc(erin, "usernames", "erin_e"), { uid: "erin", createdAt: serverTimestamp() });
  b.set(doc(erin, "users", "erin"), { username: "erin_e", displayName: "Erin", avatarColor: "pink", createdAt: serverTimestamp() });
  b.set(doc(erin, "directory", "erin"), { username: "erin_e", displayName: "Erin", createdAt: serverTimestamp(), photoUrl: "https://lh3.googleusercontent.com/a/erin" });
  await assertSucceeds(b.commit());
});
await check("nobody else can create dave's directory entry with a photo on his behalf", async () => {
  const b = writeBatch(alice);
  b.set(doc(alice, "usernames", "dave_d"), { uid: "dave", createdAt: serverTimestamp() });
  b.set(doc(alice, "users", "dave"), { username: "dave_d", displayName: "Dave", avatarColor: "pink", createdAt: serverTimestamp() });
  b.set(doc(alice, "directory", "dave"), { username: "dave_d", displayName: "Dave", createdAt: serverTimestamp(), photoUrl: "https://example.com/not-mine" });
  await assertFails(b.commit()); // written from alice's context for uid "dave" — refused regardless of photoUrl
});
await check("alice syncs her own Google photo", () =>
  assertSucceeds(updateDoc(doc(alice, "directory", "alice"), { photoUrl: "https://lh3.googleusercontent.com/a/alice-new" })));
await check("alice cannot set bob's photo", () =>
  assertFails(updateDoc(doc(alice, "directory", "bob"), { photoUrl: "https://lh3.googleusercontent.com/a/not-bobs" })));
await check("a photoUrl write cannot sneak the display name along", () =>
  assertFails(updateDoc(doc(alice, "directory", "alice"), { photoUrl: "https://example.com/p.png", displayName: "Not Alice" })));
await check("a photoUrl over 300 characters is refused", () =>
  assertFails(updateDoc(doc(alice, "directory", "alice"), { photoUrl: "https://example.com/" + "p".repeat(300) })));
await check("a photoUrl must be a string", () =>
  assertFails(updateDoc(doc(alice, "directory", "alice"), { photoUrl: 12345 })));
await check("clearing a photo (empty string) is allowed", () =>
  assertSucceeds(updateDoc(doc(alice, "directory", "alice"), { photoUrl: "" })));

// --- reel milestone rewards (1 coin per 10 valid reels, separate op kind, separate id namespace from duel ops)
await check("alice credits her own milestone 1", () =>
  assertSucceeds(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:1"), { kind: "reel_milestone", amount: 1, milestone: 1, at: serverTimestamp() })));
await check("the same milestone cannot be credited twice", () =>
  assertFails(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:1"), { kind: "reel_milestone", amount: 1, milestone: 1, at: serverTimestamp() })));
await check("alice cannot credit bob a milestone", () =>
  assertFails(setDoc(doc(alice, "wallets", "bob", "ops", "reel_milestone:1"), { kind: "reel_milestone", amount: 1, milestone: 1, at: serverTimestamp() })));
await check("a milestone cannot pay 3 coins", () =>
  assertFails(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:2"), { kind: "reel_milestone", amount: 3, milestone: 2, at: serverTimestamp() })));
await check("an odd milestone cannot pay 2 coins", () =>
  assertFails(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:1"), { kind: "reel_milestone", amount: 2, milestone: 1, at: serverTimestamp() })));
await check("a milestone cannot be skipped: 3 is refused while 2 has never been credited", () =>
  assertFails(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:3"), { kind: "reel_milestone", amount: 1, milestone: 3, at: serverTimestamp() })));
await check("the opId must match the milestone field", () =>
  assertFails(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:2"), { kind: "reel_milestone", amount: 1, milestone: 5, at: serverTimestamp() })));
await check("milestone 2 succeeds once 1 already exists", () =>
  assertSucceeds(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:2"), { kind: "reel_milestone", amount: 1, milestone: 2, at: serverTimestamp() })));
await check("milestone 3 pays 1 coin and milestone 4 pays 2 (1.5 coins per 10 reels on average)", async () => {
  await assertSucceeds(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:3"), { kind: "reel_milestone", amount: 1, milestone: 3, at: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:4"), { kind: "reel_milestone", amount: 2, milestone: 4, at: serverTimestamp() }));
});
await check("milestone 5 (odd) cannot pay 2 coins", () =>
  assertFails(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:5"), { kind: "reel_milestone", amount: 2, milestone: 5, at: serverTimestamp() })));
await check("an older phone may still pay 1 coin on an even milestone", async () => {
  await assertSucceeds(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:5"), { kind: "reel_milestone", amount: 1, milestone: 5, at: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:6"), { kind: "reel_milestone", amount: 1, milestone: 6, at: serverTimestamp() }));
});
await check("milestone ops cannot be edited or deleted", async () => {
  await assertFails(updateDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:2"), { amount: 99 }));
  await assertFails(deleteDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:2")));
});
await check("a milestone op cannot carry a duelId (keeps the two op shapes separate)", () =>
  assertFails(setDoc(doc(alice, "wallets", "alice", "ops", "reel_milestone:3"), { kind: "reel_milestone", amount: 1, milestone: 3, duelId: "x", at: serverTimestamp() })));
await check("a duel op cannot carry a milestone field either", () =>
  assertFails(setDoc(doc(alice, "wallets", "alice", "ops", "sneaky:hold:alice"), { kind: "hold", amount: 50, duelId: holdDuelId, milestone: 1, at: serverTimestamp() })));
await check("nobody else reads alice's milestone ops", () => assertFails(getDocs(collection(bob, "wallets", "alice", "ops"))));

// --- reel milestones contribute to the leaderboard the same way duel winnings do (wonOps now includes both kinds)
await check("a reel-milestone credit is visible alongside duel winnings in alice's own ops", async () => {
  // Exactly how many reel_milestone docs alice has by this point depends on how many milestone tests ran earlier
  // in this file (several, above) — what this check actually verifies is the whereIn("kind", [...]) query the
  // leaderboard repository runs: both kinds come back together, and nothing else does.
  const ops = await getDocs(query(collection(alice, "wallets", "alice", "ops"), where("kind", "in", ["transfer_credit", "reel_milestone"])));
  const kinds = new Set(ops.docs.map((d) => d.data().kind));
  assertEquals([...kinds].sort(), ["reel_milestone", "transfer_credit"]);
});

function assertEquals(actual, expected) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
}

console.log(`\n${pass} passed, ${fail} failed`);
await env.cleanup();
process.exit(fail ? 1 : 0);
