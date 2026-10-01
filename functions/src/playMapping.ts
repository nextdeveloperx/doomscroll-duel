// Pure mapping from a Google Play "subscriptionsv2" resource to our entitlement. No Firebase, no network, so it can
// be tested with plain objects. Field names follow the Play Developer API (purchases.subscriptionsv2.get).
//
// NOT yet checked against a real purchase: run one with a license-tester account and compare (see docs/billing.md).

export const PRODUCT_ID = "doomscroll_pro";

export type Status = "NONE" | "PENDING" | "ACTIVE" | "CANCELED" | "GRACE" | "ON_HOLD" | "PAUSED" | "EXPIRED";
export type Plan = "MONTHLY" | "YEARLY";

export interface Entitlement {
  status: Status;
  plan: Plan | null;
  accessUntilMs: number;
  autoRenewing: boolean;
  verifiedAtMs: number;
  testPurchase: boolean;
}

export interface PlaySubscription {
  subscriptionState?: string;
  acknowledgementState?: string;
  linkedPurchaseToken?: string;
  testPurchase?: unknown;
  externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
  lineItems?: Array<{
    productId?: string;
    expiryTime?: string;
    autoRenewingPlan?: { autoRenewEnabled?: boolean } | null;
    offerDetails?: { basePlanId?: string } | null;
  }>;
}

export function planOf(basePlanId: string | undefined | null): Plan | null {
  if (basePlanId === "monthly") return "MONTHLY";
  if (basePlanId === "yearly") return "YEARLY";
  return null;
}

/** Our status for a Play subscription state. Unknown states grant nothing. */
export function statusOf(state: string | undefined, autoRenew: boolean): Status {
  switch (state) {
    case "SUBSCRIPTION_STATE_ACTIVE":
      return autoRenew ? "ACTIVE" : "CANCELED";
    case "SUBSCRIPTION_STATE_CANCELED":
      return "CANCELED"; // renewal off, paid time left
    case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD":
      return "GRACE";
    case "SUBSCRIPTION_STATE_ON_HOLD":
      return "ON_HOLD";
    case "SUBSCRIPTION_STATE_PAUSED":
      return "PAUSED";
    case "SUBSCRIPTION_STATE_PENDING":
      return "PENDING";
    case "SUBSCRIPTION_STATE_EXPIRED":
    case "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED":
      return "EXPIRED";
    default:
      return "NONE";
  }
}

export class PurchaseRejected extends Error {
  constructor(public readonly code: "wrong_product" | "wrong_account" | "no_line_item", message: string) {
    super(message);
  }
}

/**
 * Builds the entitlement. Throws [PurchaseRejected] when the purchase is not ours (wrong product) or belongs to
 * another account (obfuscated account id is not [uid]): a token copied from someone else must not work.
 */
export function mapSubscription(sub: PlaySubscription, uid: string, nowMs: number): Entitlement {
  const item = (sub.lineItems ?? []).find((l) => l.productId === PRODUCT_ID);
  if (!item) throw new PurchaseRejected("wrong_product", "Not the Pro subscription.");
  const owner = sub.externalAccountIdentifiers?.obfuscatedExternalAccountId;
  if (owner !== uid) throw new PurchaseRejected("wrong_account", "This purchase belongs to another account.");

  const autoRenew = item.autoRenewingPlan?.autoRenewEnabled === true;
  const status = statusOf(sub.subscriptionState, autoRenew);
  const expiry = item.expiryTime ? Date.parse(item.expiryTime) : NaN;
  const accessUntilMs = Number.isFinite(expiry) ? expiry : 0;

  // Access only while the status says so AND the end is in the future. Everything else grants nothing.
  const grants = status === "ACTIVE" || status === "CANCELED" || status === "GRACE";
  const live = grants && accessUntilMs > nowMs;
  // A grace period whose end is already past is treated as on hold (fail closed; see docs/billing.md, "open questions").
  const final: Status = grants && !live ? (status === "GRACE" ? "ON_HOLD" : "EXPIRED") : status;
  return {
    status: final,
    plan: planOf(item.offerDetails?.basePlanId),
    accessUntilMs,
    autoRenewing: live && autoRenew,
    verifiedAtMs: nowMs,
    testPurchase: sub.testPurchase != null,
  };
}

export const grantsPro = (e: Pick<Entitlement, "status" | "accessUntilMs">, nowMs: number): boolean =>
  (e.status === "ACTIVE" || e.status === "CANCELED" || e.status === "GRACE") && e.accessUntilMs > nowMs;

export function needsAcknowledge(sub: PlaySubscription): boolean {
  return (
    sub.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING" &&
    ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_CANCELED", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"].includes(sub.subscriptionState ?? "")
  );
}
