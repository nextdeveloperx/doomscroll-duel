import { test } from "node:test";
import assert from "node:assert/strict";
import {
  chunk, cleanHashes, emailHash, INVITE_ALPHABET, INVITE_LENGTH, INVITE_MAX_USES, inviteVerdict, makeInviteCode,
  parseInviteCode, parseUsername,
} from "./socialRules";

test("usernames follow the same rules as the app", () => {
  assert.deepEqual(parseUsername("  @Vikas.K_1 "), { ok: true, name: "vikas.k_1" });
  assert.deepEqual(parseUsername("ab"), { ok: false, problem: "too_short" });
  assert.deepEqual(parseUsername("a".repeat(21)), { ok: false, problem: "too_long" });
  assert.deepEqual(parseUsername("vikas!"), { ok: false, problem: "bad_characters" });
  assert.deepEqual(parseUsername("1vikas"), { ok: false, problem: "must_start_with_letter" });
  assert.deepEqual(parseUsername("vi..kas"), { ok: false, problem: "bad_separators" });
  assert.deepEqual(parseUsername("vikas_"), { ok: false, problem: "bad_separators" });
  assert.deepEqual(parseUsername("Admin"), { ok: false, problem: "reserved" });
  assert.deepEqual(parseUsername(42), { ok: false, problem: "too_short" });
});

test("invite codes use the safe alphabet and the right length", () => {
  for (let i = 0; i < 200; i++) {
    const code = makeInviteCode();
    assert.equal(code.length, INVITE_LENGTH);
    assert.ok([...code].every((c) => INVITE_ALPHABET.includes(c)));
  }
  assert.equal(parseInviteCode(" abcd2345 "), "ABCD2345");
  assert.equal(parseInviteCode("ABCD0O1I"), null); // look-alike letters are not in the alphabet
  assert.equal(parseInviteCode("ABC"), null);
  assert.equal(parseInviteCode(undefined), null);
});

test("an invite is judged in a fixed order", () => {
  const base = { fromUid: "a", expiresAtMs: 1_000, uses: 0, targetUid: null };
  assert.equal(inviteVerdict(base, "b", 500), "ok");
  assert.equal(inviteVerdict(base, "a", 500), "own_invite");
  assert.equal(inviteVerdict(base, "b", 1_000), "expired");
  assert.equal(inviteVerdict({ ...base, uses: INVITE_MAX_USES }, "b", 500), "used_up");
  assert.equal(inviteVerdict({ ...base, targetUid: "c" }, "b", 500), "not_for_you");
  assert.equal(inviteVerdict({ ...base, targetUid: "b" }, "b", 500), "ok");
});

test("email hashes ignore case and spaces", () => {
  assert.equal(emailHash(" Vikas@Gmail.com "), emailHash("vikas@gmail.com"));
  assert.match(emailHash("x@y.z"), /^[0-9a-f]{64}$/);
  assert.notEqual(emailHash("a@b.c"), emailHash("a@b.d"));
});

test("contact hashes are cleaned and capped", () => {
  const good = emailHash("a@b.c");
  assert.deepEqual(cleanHashes([good, good, "nope", 7, good.toUpperCase()]), [good]);
  assert.deepEqual(cleanHashes("x"), []);
  const many = Array.from({ length: 1500 }, (_, i) => emailHash(`u${i}@x.y`));
  assert.equal(cleanHashes(many).length, 1000);
});

test("chunk splits evenly", () => {
  assert.deepEqual(chunk([1, 2, 3, 4, 5], 2), [[1, 2], [3, 4], [5]]);
  assert.deepEqual(chunk([], 3), []);
});
