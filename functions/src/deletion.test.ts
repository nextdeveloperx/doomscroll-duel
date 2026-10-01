import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { DATA_REGISTRY, DELETION_STEPS, DeletionStore, deleteAllUserData, loginIsRecent, RECENT_LOGIN_MAX_AGE_MS, STORAGE_PREFIXES } from "./deletion";

function recorder(failAt?: string) {
  const calls: string[] = [];
  const maybeFail = (name: string) => {
    if (failAt === name) throw new Error("boom " + name);
  };
  const store: DeletionStore = {
    async cancelSubscriptions(u) { calls.push(`cancel:${u}`); maybeFail("cancel"); },
    async deleteWhere(c, f, u) { calls.push(`where:${c}.${f}:${u}`); maybeFail(`where:${c}`); },
    async deleteTree(p) { calls.push(`tree:${p}`); maybeFail(`tree:${p}`); },
    async removeFriendEdges(u) { calls.push(`friends:${u}`); maybeFail("friends"); },
    async detachFromDuels(u) { calls.push(`duels:${u}`); maybeFail("duels"); },
    async deleteFiles(p) { calls.push(`files:${p}`); maybeFail("files"); },
    async deleteAuthUser(u) { calls.push(`auth:${u}`); maybeFail("auth"); },
  };
  return { calls, store };
}

test("every step runs and the sign-in account goes last", async () => {
  const { calls, store } = recorder();
  const done = await deleteAllUserData("u1", store);
  assert.deepEqual(done, DELETION_STEPS.map((s) => s.name));
  assert.equal(calls[calls.length - 1], "auth:u1");
  assert.equal(done[done.length - 1], "auth_user");
});

test("subscriptions are cancelled before purchase tokens are deleted", async () => {
  const { calls, store } = recorder();
  await deleteAllUserData("u1", store);
  assert.ok(calls.indexOf("cancel:u1") < calls.indexOf("where:purchaseTokens.uid:u1"));
});

test("a failure stops the run before the sign-in account is touched, so a retry is possible", async () => {
  for (const failAt of ["duels", "tree:users/u1", "files", "where:purchaseTokens"]) {
    const { calls, store } = recorder(failAt);
    await assert.rejects(deleteAllUserData("u1", store));
    assert.ok(!calls.some((c) => c.startsWith("auth:")), failAt);
  }
});

test("running it twice is safe (the fake store accepts repeats)", async () => {
  const { store } = recorder();
  await deleteAllUserData("u1", store);
  await deleteAllUserData("u1", store);
});

test("files, profile, wallet and entitlement of exactly this person are removed", async () => {
  const { calls, store } = recorder();
  await deleteAllUserData("abc", store);
  for (const want of ["tree:users/abc", "tree:wallets/abc", "tree:entitlements/abc", "files:proofs/abc/", "files:avatars/abc/", "friends:abc", "duels:abc"]) {
    assert.ok(calls.includes(want), want);
  }
  assert.ok(calls.every((c) => !c.includes("other")));
  assert.deepEqual(STORAGE_PREFIXES("abc"), ["proofs/abc/", "avatars/abc/"]);
});

test("unlock requests are deleted from both sides", async () => {
  const { calls, store } = recorder();
  await deleteAllUserData("u9", store);
  assert.ok(calls.includes("where:unlockRequests.fromUid:u9"));
  assert.ok(calls.includes("where:unlockRequests.toUid:u9"));
});

test("every top-level collection in firestore.rules is in the deletion registry", () => {
  const rules = readFileSync(join(__dirname, "..", "..", "firebase", "firestore.rules"), "utf8");
  const found = new Set([...rules.matchAll(/^ {4}match \/([A-Za-z][A-Za-z0-9_]*)\//gm)].map((m) => m[1]));
  assert.ok(found.size >= 6, "rules parse looks wrong: " + [...found].join(","));
  const missing = [...found].filter((c) => !(c in DATA_REGISTRY));
  assert.deepEqual(missing, [], `collections without a deletion decision: ${missing.join(", ")}. Add them to DATA_REGISTRY and DELETION_STEPS.`);
});

test("every personal registry entry is handled by a real step", () => {
  const stepNames = new Set(DELETION_STEPS.map((s) => s.name));
  for (const [name, info] of Object.entries(DATA_REGISTRY)) {
    if (info.kind === "no_personal_data") continue;
    for (const part of info.handledBy.split("+").map((p) => p.trim())) assert.ok(stepNames.has(part), `${name} -> ${part}`);
  }
});

test("login must be recent", () => {
  const now = Date.parse("2026-10-01T10:00:00Z");
  const sec = (ms: number) => Math.floor(ms / 1000);
  assert.ok(loginIsRecent(sec(now - 60_000), now));
  assert.ok(!loginIsRecent(sec(now - RECENT_LOGIN_MAX_AGE_MS - 10_000), now));
  assert.ok(!loginIsRecent(undefined, now));
  assert.ok(!loginIsRecent(NaN, now));
  assert.ok(!loginIsRecent(sec(now + 3_600_000), now)); // a time in the future is not "recent"
});
