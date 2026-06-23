import { describe, it, expect } from 'vitest';
import { messageForCode } from '@/shared/api/errors';

/**
 * TASK-PC-FE-125 — the ecommerce notification template form (TemplateForm)
 * surfaces producer error codes through `messageForCode`. The producer returns
 * `409 TEMPLATE_ALREADY_EXISTS` when an operator registers a template whose
 * (type, channel) pair already exists for the tenant — the form defaults to
 * ORDER_PLACED + EMAIL, so the first save of a seeded tenant hits this. Before
 * this fix the code was absent from the message map, so the user saw the
 * generic "저장하지 못했습니다." save-failed fallback instead of the real reason.
 * `404 TEMPLATE_NOT_FOUND` (concurrent delete on an edit submit) had the same gap.
 */
describe('messageForCode — ecommerce notification template producer codes', () => {
  const FALLBACK = '저장하지 못했습니다.';

  it('TEMPLATE_ALREADY_EXISTS maps to an actionable duplicate message (not the fallback)', () => {
    const msg = messageForCode('TEMPLATE_ALREADY_EXISTS', FALLBACK);
    expect(msg).not.toBe(FALLBACK);
    expect(msg).toContain('이미');
  });

  it('TEMPLATE_NOT_FOUND maps to an actionable not-found message (not the fallback)', () => {
    const msg = messageForCode('TEMPLATE_NOT_FOUND', FALLBACK);
    expect(msg).not.toBe(FALLBACK);
    expect(msg).toContain('찾을 수 없습니다');
  });

  it('a genuinely unmapped code still returns the provided fallback', () => {
    expect(messageForCode('TOTALLY_UNKNOWN_CODE', FALLBACK)).toBe(FALLBACK);
  });
});
