'use client';
import * as PortOne from '@portone/browser-sdk/v2';
import { env } from '@/shared/config/env';
import type { MembershipTier } from '@/entities/membership';

/**
 * Tier-aware monthly price in KRW. MUST equal membership-service
 * {@code MembershipPricing} (MEMBERS_ONLY 7,900 / PREMIUM 17,900) — the PortOne
 * adapter verifies the paid amount server-side (ADR-001 amount-tamper guard), so
 * the payment window MUST request exactly the backend charge. An upgrade passes the
 * prorated quote amount instead of this list price.
 */
export const TIER_MONTHLY_KRW: Record<MembershipTier, number> = {
  MEMBERS_ONLY: 7_900,
  PREMIUM: 17_900,
};

export type CheckoutResult =
  | { ok: true; paymentId: string }
  | { ok: false; message: string };

/**
 * Open the PortOne V2 payment window (client-side) for {@code amountKrw} and return
 * the resulting {@code paymentId}. The backend (PortOnePaymentAdapter) independently
 * VERIFIES that paymentId AND that the paid amount equals what it charges — this
 * client signal is never trusted on its own (ADR-001).
 *
 * A user cancel, a PG failure, a missing SDK config, or a thrown SDK error all
 * resolve to {@code { ok: false, message }} (no throw) so the panel renders inline.
 */
export async function requestPortOnePayment(
  orderName: string,
  amountKrw: number,
): Promise<CheckoutResult> {
  if (!env.portoneStoreId || !env.portoneChannelKey) {
    return { ok: false, message: '결제 모듈이 설정되지 않았습니다 (PortOne 키 미설정).' };
  }
  // A fresh unique id per attempt — reusing one would collide with the backend's
  // idempotency/replay guard.
  const paymentId = `pay-${crypto.randomUUID()}`;
  let response: Awaited<ReturnType<typeof PortOne.requestPayment>>;
  try {
    response = await PortOne.requestPayment({
      storeId: env.portoneStoreId,
      channelKey: env.portoneChannelKey,
      paymentId,
      orderName,
      totalAmount: amountKrw,
      currency: 'CURRENCY_KRW',
      payMethod: 'CARD',
    });
  } catch {
    return { ok: false, message: '결제 창을 여는 중 오류가 발생했습니다.' };
  }
  // PortOne surfaces a cancel / PG failure as a defined `code`; a completed
  // payment has no `code` and records the paymentId we generated.
  if (response == null || response.code !== undefined) {
    return { ok: false, message: response?.message ?? '결제가 취소되었습니다.' };
  }
  return { ok: true, paymentId };
}
