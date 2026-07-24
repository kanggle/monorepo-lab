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
