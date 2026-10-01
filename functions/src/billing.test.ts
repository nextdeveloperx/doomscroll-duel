import test from "node:test";
import assert from "node:assert/strict";
import { mapSubscription, PlaySubscription, PurchaseRejected, grantsPro, needsAcknowledge } from "./playMapping";
import { canCreateDuel, canCreateSquad, isPro } from "./entitlementGate";
import { earn, emptyTally, dayKey } from "./coinRules";
import rules from "./coin-rules.json";

const NOW = Date.parse("2026-10-01T10:00:00Z");
const DAY = 86_400_000;
const iso = (ms: number) => new Date(ms).toISOString();

function sub(state: string, over: Partial<PlaySubscription> = {}, line: Record<string, unknown> = {}): PlaySubscription {
  return {
    subscriptionState: state,
    externalAccountIdentifiers: { obfuscatedExternalAccountId: "uid1" },
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    lineItems: [{ productId: "doomscroll_pro", expiryTime: iso(NOW + 10 * DAY), autoRenewingPlan: { autoRenewEnabled: true }, offerDetails: { basePlanId: "monthly" }, ...line }],
    ...over,
  };
}

test("active, renewing", () => {
  const e = mapSubscription(sub("SUBSCRIPTION_STATE_ACTIVE"), "uid1", NOW);
  assert.equal(e.status, "ACTIVE");
  assert.equal(e.plan, "MONTHLY");
  assert.equal(e.autoRenewing, true);
  assert.equal(e.accessUntilMs, NOW + 10 * DAY);
  assert.ok(grantsPro(e, NOW));
});

test("yearly base plan", () => {
  assert.equal(mapSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", {}, { offerDetails: { basePlanId: "yearly" } }), "uid1", NOW).plan, "YEARLY");
});

test("cancelled keeps access until the end", () => {
  const e = mapSubscription(sub("SUBSCRIPTION_STATE_CANCELED", {}, { autoRenewingPlan: { autoRenewEnabled: false } }), "uid1", NOW);
  assert.equal(e.status, "CANCELED");
  assert.equal(e.autoRenewing, false);
  assert.ok(grantsPro(e, NOW));
  assert.ok(!grantsPro(e, NOW + 10 * DAY));
});

test("active state with renewal off is shown as cancelled", () => {
  assert.equal(mapSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", {}, { autoRenewingPlan: { autoRenewEnabled: false } }), "uid1", NOW).status, "CANCELED");
});

test("grace keeps access; a grace already past fails closed", () => {
  assert.equal(mapSubscription(sub("SUBSCRIPTION_STATE_IN_GRACE_PERIOD"), "uid1", NOW).status, "GRACE");
  assert.equal(mapSubscription(sub("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", {}, { expiryTime: iso(NOW - 1) }), "uid1", NOW).status, "ON_HOLD");
});

test("hold, pause, pending and expired grant nothing", () => {
  for (const [s, want] of [
    ["SUBSCRIPTION_STATE_ON_HOLD", "ON_HOLD"],
    ["SUBSCRIPTION_STATE_PAUSED", "PAUSED"],
    ["SUBSCRIPTION_STATE_PENDING", "PENDING"],
    ["SUBSCRIPTION_STATE_EXPIRED", "EXPIRED"],
    ["SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED", "EXPIRED"],
    ["SOMETHING_NEW", "NONE"],
  ] as const) {
    const e = mapSubscription(sub(s), "uid1", NOW);
    assert.equal(e.status, want, s);
    assert.ok(!grantsPro(e, NOW), s);
  }
});

test("an active state whose end already passed is expired", () => {
  assert.equal(mapSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", {}, { expiryTime: iso(NOW - 1) }), "uid1", NOW).status, "EXPIRED");
});

test("a token from another account is rejected", () => {
  assert.throws(() => mapSubscription(sub("SUBSCRIPTION_STATE_ACTIVE"), "someone-else", NOW), (e: unknown) => e instanceof PurchaseRejected && e.code === "wrong_account");
  assert.throws(() => mapSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", { externalAccountIdentifiers: {} }), "uid1", NOW), PurchaseRejected);
});

test("a different product is rejected", () => {
  assert.throws(() => mapSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", {}, { productId: "other" }), "uid1", NOW), (e: unknown) => e instanceof PurchaseRejected && e.code === "wrong_product");
});

test("acknowledge only live, unacknowledged purchases", () => {
  assert.ok(needsAcknowledge(sub("SUBSCRIPTION_STATE_ACTIVE")));
  assert.ok(!needsAcknowledge(sub("SUBSCRIPTION_STATE_PENDING")));
  assert.ok(!needsAcknowledge(sub("SUBSCRIPTION_STATE_ACTIVE", { acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" })));
});

test("server gates", () => {
  assert.deepEqual(canCreateDuel(2, false), { allowed: true });
  assert.deepEqual(canCreateDuel(3, false), { allowed: false, needs: "UNLIMITED_DUELS" });
  assert.deepEqual(canCreateDuel(99, true), { allowed: true });
  assert.deepEqual(canCreateSquad(false), { allowed: false, needs: "SQUAD_BATTLE" });
  assert.ok(isPro({ status: "GRACE", accessUntilMs: NOW + 1 }, NOW));
  assert.ok(!isPro({ status: "ON_HOLD", accessUntilMs: NOW + 1 }, NOW));
  assert.ok(!isPro(undefined, NOW));
});

test("coin rules: check-in once a day", () => {
  const a = earn({ source: "daily_check_in" }, emptyTally("d1"), "d1");
  assert.ok(a.ok && a.granted === 10);
  const again = earn({ source: "daily_check_in" }, (a as { tally: ReturnType<typeof emptyTally> }).tally, "d1");
  assert.ok(!again.ok && again.reason === "SOURCE_LIMIT_REACHED");
  assert.ok(earn({ source: "daily_check_in" }, (a as { tally: ReturnType<typeof emptyTally> }).tally, "d2").ok);
});

test("coin rules: cap trims and then refuses; milestones ignore the cap", () => {
  const near = { ...emptyTally("d"), total: rules.dailyEarnCap - 5 };
  const r = earn({ source: "night_pact", completedFully: true }, near, "d");
  assert.ok(r.ok && r.granted === 5);
  const full = { ...emptyTally("d"), total: rules.dailyEarnCap };
  const refused = earn({ source: "daily_check_in" }, full, "d");
  assert.ok(!refused.ok && refused.reason === "DAILY_CAP_REACHED");
  const m = earn({ source: "streak_milestone", streakDays: 7 }, full, "d");
  assert.ok(m.ok && m.granted === 50 && m.tally.total === rules.dailyEarnCap);
  assert.ok(!earn({ source: "streak_milestone", streakDays: 8 }, full, "d").ok);
});

test("coin rules: duel win once per opponent, needs a real duel", () => {
  const w = { source: "duel_win", opponent: "bob", durationMs: 2 * 3_600_000, bothTracked: true };
  const first = earn(w, emptyTally("d"), "d");
  assert.ok(first.ok);
  const second = earn(w, (first as { tally: ReturnType<typeof emptyTally> }).tally, "d");
  assert.ok(!second.ok && second.reason === "OPPONENT_ALREADY_PAID");
  assert.ok(!earn({ ...w, durationMs: 1000 }, emptyTally("d"), "d").ok);
  assert.ok(!earn({ ...w, bothTracked: false }, emptyTally("d"), "d").ok);
});

test("coin rules: no source can be a purchase", () => {
  assert.ok(Object.keys(rules.earn).every((k) => !/purchase|buy|pro|iap/i.test(k)));
  assert.equal(earn({ source: "purchase" }, emptyTally("d"), "d").ok, false);
});

test("day key follows the user's zone", () => {
  const t = Date.parse("2026-10-01T20:00:00Z"); // 01:30 next day in India
  assert.equal(dayKey(t, "Asia/Kolkata"), "2026-10-02");
  assert.equal(dayKey(t, "UTC"), "2026-10-01");
  assert.equal(dayKey(t, "Not/AZone"), "2026-10-02");
});
