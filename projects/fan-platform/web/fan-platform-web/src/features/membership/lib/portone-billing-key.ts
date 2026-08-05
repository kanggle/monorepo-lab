'use client';
import * as PortOne from '@portone/browser-sdk/v2';
import { env } from '@/shared/config/env';
import { isDemoPayment } from '@/features/membership/lib/demo-payment';
import { randomUuid } from '@/shared/lib/random-id';
import type { CheckoutBuyer } from '@/features/membership/lib/portone-checkout';

export type { CheckoutBuyer };

export type IssueBillingKeyResult =
  | { ok: true; billingKey: string }
  | { ok: false; message: string };

// Buyer-identity fallbacks mirror `portone-checkout.ts`: some PGs (notably
// KG이니시스-class) reject a billing-key issuance up-front when `customer.email`
// is absent, so we always send a well-formed customer block. The signed-in fan's
// identity is preferred; the fallbacks only ensure issuance never hard-blocks in
// demo mode (issuance itself is not a charge — ADR-002 §D1 / ADR-MONO-057 §7).
const FALLBACK_BUYER_EMAIL = 'demo@fanplatform.com';
const FALLBACK_BUYER_NAME = '팬플랫폼 회원';
// We do not collect the fan's phone number; a neutral placeholder keeps the
// request well-formed for PGs that require one (no receipt is sent in test mode).
const PLACEHOLDER_PHONE = '010-0000-0000';

/**
 * Open the PortOne V2 billing-key issuance window (client-side, `billingKeyMethod:
 * 'CARD'`) so a fan can register a card **once** for auto-renewal — this is a card
 * registration, NOT a charge (ADR-002 §D1). On success the vendor-opaque
 * `billingKey` is returned so the caller can hand it to the `enrollBillingKey`
 * `'use server'` action; the key is treated as sensitive (never logged here).
 *
 * A user cancel, a PG failure, a missing SDK config, or a thrown SDK error all
 * resolve to {@code { ok: false, message }} (no throw), mirroring
 * `requestPortOnePayment`, so the calling panel renders the notice inline. On a
 * thrown SDK error the PG's own message is surfaced (it is the actionable signal,
 * e.g. a missing-required-field rejection).
 */
export async function requestIssueBillingKey(
  buyer?: CheckoutBuyer,
): Promise<IssueBillingKeyResult> {
  // TASK-FAN-FE-015: auto-renewal is behind the SAME pre-guard as checkout, so fixing
  // only `requestPortOnePayment` would have left the toggle dead in the demo with an
  // identical "결제 모듈 미설정" notice. Enrollment stores the key without asking the PG
  // to validate it (EnrollBillingKeyUseCase), and the later charge runs through the mock
  // recurring gateway, so a demo-issued key is honoured end to end.
  if (await isDemoPayment()) {
    return { ok: true, billingKey: `bkey-demo-${randomUuid()}` };
  }
  if (!env.portoneStoreId || !env.portoneChannelKey) {
    return { ok: false, message: '결제 모듈이 설정되지 않았습니다 (PortOne 키 미설정).' };
  }
  let response: Awaited<ReturnType<typeof PortOne.requestIssueBillingKey>>;
  try {
    response = await PortOne.requestIssueBillingKey({
      storeId: env.portoneStoreId,
      channelKey: env.portoneChannelKey,
      billingKeyMethod: 'CARD',
      // Mandatory for KG이니시스-class PGs (email); prefer the authenticated fan's
      // identity, fall back to demo-safe values so issuance never hard-blocks.
      customer: {
        email: buyer?.email || FALLBACK_BUYER_EMAIL,
        fullName: buyer?.fullName || FALLBACK_BUYER_NAME,
        phoneNumber: PLACEHOLDER_PHONE,
      },
    });
  } catch (err) {
    const detail = err instanceof Error ? err.message : String(err);
    // eslint-disable-next-line no-console
    console.error('[portone] requestIssueBillingKey threw', err);
    return { ok: false, message: `자동 갱신 등록 창을 여는 중 오류가 발생했습니다: ${detail}` };
  }
  // PortOne surfaces a cancel / PG failure as a defined `code`; a completed
  // issuance has no `code` and carries the vendor `billingKey`.
  if (response == null || response.code !== undefined || !response.billingKey) {
    return { ok: false, message: response?.message ?? '자동 갱신 등록이 취소되었습니다.' };
  }
  return { ok: true, billingKey: response.billingKey };
}
