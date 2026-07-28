'use server';
import { randomUUID } from 'node:crypto';
import { revalidatePath } from 'next/cache';
import { gatewayFetch } from '@/shared/api/client';
import { getFanSession } from '@/shared/auth/session';
import { ApiError } from '@/shared/api/errors';
import type { Membership, MembershipTier } from '@/entities/membership';

/**
 * Result of a subscribe attempt. The expected business decline
 * (422 PAYMENT_DECLINED / MEMBERSHIP_TIER_INVALID) is returned as
 * `{ ok: false }` — NOT thrown — so the client renders it inline instead of
 * tripping the error boundary. Auth/transport errors still throw.
 */
export type SubscribeResult =
  | { ok: true; membership: Membership }
  | { ok: false; code: string; message: string };

/**
 * Upgrade-quote preview (TASK-FAN-BE-032). `chargeMinor` is what the client must
 * request from PortOne — the backend re-computes and re-verifies the same value.
 * `supersedesMembershipId` is non-null only when a PREMIUM request would upgrade
 * from (and cancel) an active MEMBERS_ONLY membership.
 */
export interface UpgradeQuote {
  tier: MembershipTier;
  planMonths: number;
  listPriceMinor: number;
  creditMinor: number;
  chargeMinor: number;
  supersedesMembershipId: string | null;
}

const DECLINE_CODES = new Set(['PAYMENT_DECLINED', 'MEMBERSHIP_TIER_INVALID']);
const RENEW_DECLINE_CODES = new Set(['PAYMENT_DECLINED', 'MEMBERSHIP_NOT_RENEWABLE']);
// A billing-key enroll rejects a bad tier / blank key as a business validation
// (returned inline as `{ ok: false }`, not thrown) — mirrors the subscribe decline.
const ENROLL_DECLINE_CODES = new Set(['MEMBERSHIP_TIER_INVALID', 'VALIDATION_ERROR']);

/**
 * A billing-key enrollment (ADR-002 §D1 auto-renewal). Mirrors the flat body of
 * `POST /api/v1/memberships/billing-key` (NOT the `{ data, meta }` envelope).
 * NOTE: the `billingKey` itself is NEVER part of this shape — it is sent once to
 * the backend and discarded, never returned or stored client-side (ADR-002 §D5).
 */
export interface BillingKeyEnrollment {
  enrollmentId: string;
  tier: MembershipTier;
  active: boolean;
  createdAt: string;
}

/**
 * Result of a billing-key enroll attempt. The expected business validation
 * (422 MEMBERSHIP_TIER_INVALID / VALIDATION_ERROR) is returned as `{ ok: false }`
 * — NOT thrown — so the client renders it inline. Auth/transport errors still throw.
 */
export type EnrollBillingKeyResult =
  | { ok: true; enrollment: BillingKeyEnrollment }
  | { ok: false; code: string; message: string };

/**
 * Preview the price of a subscribe/upgrade before opening the payment window.
 * Returns the plain tier list price, or — for a PREMIUM request while an active
 * MEMBERS_ONLY membership is held — the prorated charge + credit (§ BE-032).
 */
export async function getUpgradeQuote(
  tier: MembershipTier,
  planMonths: number,
): Promise<UpgradeQuote> {
  const session = await getFanSession();
  const res = await gatewayFetch<UpgradeQuote>(
    `/api/v1/memberships/upgrade-quote?tier=${encodeURIComponent(tier)}&planMonths=${planMonths}`,
    { accessToken: session.accessToken, cache: 'no-store' },
  );
  return res.data;
}

/**
 * Subscribe to a tier. A fresh `Idempotency-Key` is generated server-side per
 * attempt (membership-api.md T1 — the header is required). `paymentId` is the PG
 * payment reference the client obtained from the PortOne payment window; the
 * backend verifies it server-side (portone profile) or treats it as an opaque
 * token (mock profile — `tok_decline` forces a 422 PAYMENT_DECLINED).
 */
export async function subscribe(
  tier: MembershipTier,
  planMonths: number,
  paymentId: string,
): Promise<SubscribeResult> {
  const session = await getFanSession();
  try {
    const res = await gatewayFetch<Membership>('/api/v1/memberships', {
      accessToken: session.accessToken,
      method: 'POST',
      headers: { 'Idempotency-Key': randomUUID() },
      body: {
        tier,
        planMonths,
        paymentId,
      },
    });
    revalidatePath('/membership');
    return { ok: true, membership: res.data };
  } catch (err) {
    if (err instanceof ApiError && DECLINE_CODES.has(err.code)) {
      return { ok: false, code: err.code, message: err.message };
    }
    throw err;
  }
}

/**
 * Renew a membership (`POST /{id}/renew`) — seamless re-activation of the same
 * tier (the backend stacks the new window onto `max(now, prior.validTo)`). Like
 * subscribe, a fresh `Idempotency-Key` is generated server-side and the expected
 * business decline (422 PAYMENT_DECLINED / MEMBERSHIP_NOT_RENEWABLE) is returned
 * as `{ ok: false }` rather than thrown.
 */
export async function renewMembership(
  membershipId: string,
  planMonths: number,
  paymentId: string,
): Promise<SubscribeResult> {
  const session = await getFanSession();
  try {
    const res = await gatewayFetch<Membership>(
      `/api/v1/memberships/${encodeURIComponent(membershipId)}/renew`,
      {
        accessToken: session.accessToken,
        method: 'POST',
        headers: { 'Idempotency-Key': randomUUID() },
        body: { planMonths, paymentId },
      },
    );
    revalidatePath('/membership');
    return { ok: true, membership: res.data };
  } catch (err) {
    if (err instanceof ApiError && RENEW_DECLINE_CODES.has(err.code)) {
      return { ok: false, code: err.code, message: err.message };
    }
    throw err;
  }
}

/**
 * Enroll a billing key for auto-renewal (`POST /api/v1/memberships/billing-key`,
 * ADR-002 §D1). The `billingKey` is the vendor-opaque value the client obtained
 * from `PortOne.requestIssueBillingKey(...)`; it is sent once and discarded — this
 * action NEVER logs it, NEVER returns it, and NEVER stores it (ADR-002 §D5,
 * treated as sensitive as an access token). The backend replaces any existing
 * active enrollment for the same tier (at most one active per tier). The expected
 * business validation (422 MEMBERSHIP_TIER_INVALID / VALIDATION_ERROR) is returned
 * as `{ ok: false }` rather than thrown; other errors rethrow.
 */
export async function enrollBillingKey(
  tier: MembershipTier,
  billingKey: string,
): Promise<EnrollBillingKeyResult> {
  const session = await getFanSession();
  try {
    const res = await gatewayFetch<BillingKeyEnrollment>('/api/v1/memberships/billing-key', {
      accessToken: session.accessToken,
      method: 'POST',
      body: { tier, billingKey },
    });
    revalidatePath('/membership');
    return { ok: true, enrollment: res.data };
  } catch (err) {
    if (err instanceof ApiError && ENROLL_DECLINE_CODES.has(err.code)) {
      return { ok: false, code: err.code, message: err.message };
    }
    throw err;
  }
}

/**
 * Turn off auto-renewal for a tier (`DELETE /api/v1/memberships/billing-key/{tier}`,
 * ADR-002 §D1). Soft-deactivates the active enrollment; the membership itself is
 * untouched (it stays valid until its window ends, it just won't auto-renew). A 404
 * (`BILLING_KEY_ENROLLMENT_NOT_FOUND`) is an idempotent no-op at the UI level — the
 * user intent ("no auto-renew") is satisfied either way — mirroring `cancelMembership`.
 */
export async function cancelBillingKeyEnrollment(tier: MembershipTier): Promise<void> {
  const session = await getFanSession();
  try {
    await gatewayFetch(`/api/v1/memberships/billing-key/${encodeURIComponent(tier)}`, {
      accessToken: session.accessToken,
      method: 'DELETE',
    });
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      revalidatePath('/membership');
      return;
    }
    throw err;
  }
  revalidatePath('/membership');
}

/**
 * Cancel an active membership (`ACTIVE → CANCELED`). 404 / already-canceled is
 * an idempotent no-op at the UI level — the user intent ("not subscribed") is
 * satisfied either way.
 */
export async function cancelMembership(membershipId: string): Promise<void> {
  const session = await getFanSession();
  try {
    await gatewayFetch(`/api/v1/memberships/${encodeURIComponent(membershipId)}/cancel`, {
      accessToken: session.accessToken,
      method: 'POST',
      body: {},
    });
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      revalidatePath('/membership');
      return;
    }
    throw err;
  }
  revalidatePath('/membership');
}
