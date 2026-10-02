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

console.log(`\n${pass} passed, ${fail} failed`);
await env.cleanup();
process.exit(fail ? 1 : 0);
