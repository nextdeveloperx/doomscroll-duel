import test from "node:test";
import assert from "node:assert/strict";
import { askVerdict, isExpired, DAILY_LIMIT, REQUEST_TTL_MS, WINDOW_MS } from "./unlockRules";

const NOW = 1_700_000_000_000;

test("first request is ok", () => assert.equal(askVerdict([], NOW), "ok"));

test("a live pending request blocks another", () => {
  assert.equal(askVerdict([{ createdAtMs: NOW - 1000, status: "pending" }], NOW), "pending");
});

test("a pending request past its TTL no longer blocks, but still counts", () => {
  const past = [{ createdAtMs: NOW - REQUEST_TTL_MS - 1, status: "pending" as const }];
  assert.equal(askVerdict(past, NOW), "ok");
});

test("three requests inside 24h exhaust the quota", () => {
  const past = Array.from({ length: DAILY_LIMIT }, (_, i) => ({
    createdAtMs: NOW - (i + 1) * 3_600_000,
    status: "denied" as const,
  }));
  assert.equal(askVerdict(past, NOW), "quota");
});

test("quota frees up as the oldest request leaves the rolling window", () => {
  const past = [
    { createdAtMs: NOW - WINDOW_MS - 1, status: "denied" as const },
    { createdAtMs: NOW - 2 * 3_600_000, status: "denied" as const },
    { createdAtMs: NOW - 3_600_000, status: "approved" as const },
  ];
  assert.equal(askVerdict(past, NOW), "ok");
});

test("expiry boundary", () => {
  assert.equal(isExpired(NOW - REQUEST_TTL_MS + 1, NOW), false);
  assert.equal(isExpired(NOW - REQUEST_TTL_MS, NOW), true);
});
