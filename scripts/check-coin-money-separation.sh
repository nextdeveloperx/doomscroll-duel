#!/usr/bin/env bash
# Hard rule: coins are virtual and can never be bought with real money, never be won back from a purchase, and never
# be cashed out. This guard keeps the money side (Google Play, Pro) and the coin side (earn, spend, stakes) apart:
#   - billing code never mentions coins, and coin code never imports billing code;
#   - the only thing sold through Play is the Pro subscription (no in-app products, so nothing consumable);
#   - the server functions that handle purchases never touch wallets, and coin functions never read purchases.
set -u
cd "$(dirname "$0")/.."
fail=0
A=app/src/main/kotlin/com/doomscrollduel
F=functions/src

say() { echo "FAIL: $*"; fail=1; }

# 1. Billing, entitlement and paywall code must not mention coins at all.
if grep -rniE "coin|wallet|stake" $A/billing $A/domain/billing $A/feature/paywall; then
  say "billing / paywall code mentions coins"
fi

# 2. Coin code must not import billing code.
if grep -rnE "import com\.doomscrollduel\.(billing|domain\.billing|feature\.paywall)" $A/domain/coins; then
  say "coin code imports billing code"
fi

# 3. No in-app (one-time / consumable) products, only subscriptions.
if grep -rnE "ProductType\.INAPP|consumeAsync|ConsumeParams|queryPurchasesAsync\(.*INAPP" $A; then
  say "an in-app product or consume call exists; only the Pro subscription may be sold"
fi

# 4. Server: purchase code never touches wallets or coin rules; coin code never reads purchases or entitlements.
if grep -nE "wallets|coinRules|coin-rules|claimCoins" $F/purchases.ts $F/playMapping.ts $F/entitlementGate.ts; then
  say "purchase code touches coins"
fi
if grep -nE "playMapping|purchases|entitlements|androidpublisher" $F/coinRules.ts $F/claimCoins.ts; then
  say "coin code reads purchases or entitlements"
fi

# 5. No coin earn source can be a purchase, and the catalog has no price in money.
if grep -niE "purchase|iap|money|rupee|inr|usd|cash" $F/coin-rules.json; then
  say "coin-rules.json mentions money"
fi

# 6. Nothing may offer a cash-out.
if grep -rniE "cash.?out|redeem|withdrawal" $A $F --include=*.kt --include=*.ts -l | grep -v "check-coin"; then
  say "cash-out wording found in code"
fi

if [ "$fail" -eq 0 ]; then echo "coins and money are separate"; fi
exit $fail
