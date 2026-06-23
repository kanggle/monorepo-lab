import { describe, it, expect } from 'vitest';
import { messageForCode } from '@/shared/api/errors';

/**
 * TASK-PC-FE-126 — the ecommerce promotion form (PromotionForm) + coupon
 * issuance surface producer error codes through `messageForCode`. The producer
 * (promotion-service GlobalExceptionHandler) returns `400 INVALID_PROMOTION_REQUEST`
 * for the cross-field guards (endDate ≤ startDate, PERCENTAGE discountValue > 100,
 * unparseable Instant) and `422` state guards (ended / has-issued-coupons /
 * not-active / coupon-limit). Before this fix none of these codes were in the
 * message map, so the operator saw the generic "저장하지 못했습니다." save-failed
 * fallback instead of the real reason (the reported "프로모션 저장 실패" confusion).
 */
describe('messageForCode — ecommerce promotion producer codes', () => {
  const FALLBACK = '저장하지 못했습니다.';

  it('INVALID_PROMOTION_REQUEST maps to an actionable validation message (not the fallback)', () => {
    const msg = messageForCode('INVALID_PROMOTION_REQUEST', FALLBACK);
    expect(msg).not.toBe(FALLBACK);
    // names both fixable causes the console can produce
    expect(msg).toContain('기간');
    expect(msg).toContain('할인값');
  });

  it.each([
    ['PROMOTION_NOT_FOUND', '찾을 수 없습니다'],
    ['PROMOTION_ALREADY_ENDED', '종료'],
    ['PROMOTION_HAS_ISSUED_COUPONS', '쿠폰'],
    ['PROMOTION_NOT_ACTIVE', '발급'],
    ['COUPON_LIMIT_EXCEEDED', '수량'],
  ])('%s maps to an actionable message (not the fallback)', (code, needle) => {
    const msg = messageForCode(code, FALLBACK);
    expect(msg).not.toBe(FALLBACK);
    expect(msg).toContain(needle);
  });

  it('a genuinely unmapped code still returns the provided fallback', () => {
    expect(messageForCode('TOTALLY_UNKNOWN_CODE', FALLBACK)).toBe(FALLBACK);
  });
});
