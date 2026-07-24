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
 * Authenticated buyer identity forwarded to the PG. Some PG providers make buyer
 * fields mandatory — notably KG이니시스 V2 일반결제 REJECTS the request up-front if
 * {@code customer.email} is absent ("구매자 이메일은 필수 입력입니다"). We therefore
 * always send a customer block; `email`/`fullName` come from the signed-in fan's
 * session (see `page.tsx` → `getFanSession().email`).
 */
export interface CheckoutBuyer {
  email?: string | null;
  fullName?: string | null;
}

// A well-formed fallback so a missing IAM email claim never hard-blocks checkout
// (the PG only validates format for a test-mode payment; the backend re-verifies
// the payment independently — the buyer email is not part of that check).
const FALLBACK_BUYER_EMAIL = 'demo@fanplatform.com';
const FALLBACK_BUYER_NAME = '팬플랫폼 회원';
// We do not collect the fan's phone number; KG이니시스 test config still expects a
// well-formed value, so send a neutral placeholder (no receipt is sent in test mode).
const PLACEHOLDER_PHONE = '010-0000-0000';

/**
 * Open the PortOne V2 payment window (client-side) for {@code amountKrw} and return
 * the resulting {@code paymentId}. The backend (PortOnePaymentAdapter) independently
 * VERIFIES that paymentId AND that the paid amount equals what it charges — this
 * client signal is never trusted on its own (ADR-001).
 *
 * A user cancel, a PG failure, a missing SDK config, or a thrown SDK error all
 * resolve to {@code { ok: false, message }} (no throw) so the panel renders inline.
 * On a thrown SDK error the PG's own message is surfaced (it is the actionable
 * signal, e.g. a missing-required-field rejection).
 */
export async function requestPortOnePayment(
  orderName: string,
  amountKrw: number,
  buyer?: CheckoutBuyer,
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
      // Mandatory for KG이니시스 V2 일반결제 (email); prefer the authenticated fan's
      // identity, fall back to demo-safe values so checkout never hard-blocks.
      customer: {
        email: buyer?.email || FALLBACK_BUYER_EMAIL,
        fullName: buyer?.fullName || FALLBACK_BUYER_NAME,
        phoneNumber: PLACEHOLDER_PHONE,
      },
    });
  } catch (err) {
    const detail = err instanceof Error ? err.message : String(err);
    // eslint-disable-next-line no-console
    console.error('[portone] requestPayment threw', err);
    return { ok: false, message: `결제 창을 여는 중 오류가 발생했습니다: ${detail}` };
  }
  // PortOne surfaces a cancel / PG failure as a defined `code`; a completed
  // payment has no `code` and records the paymentId we generated.
  if (response == null || response.code !== undefined) {
    return { ok: false, message: response?.message ?? '결제가 취소되었습니다.' };
  }
  return { ok: true, paymentId };
}
