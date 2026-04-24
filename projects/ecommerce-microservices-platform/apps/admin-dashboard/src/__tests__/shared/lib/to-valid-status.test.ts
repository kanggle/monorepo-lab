import { toValidStatus } from '@/shared/lib/to-valid-status';

const VALID_STATUSES = ['ON_SALE', 'SOLD_OUT', 'HIDDEN'] as const;

describe('toValidStatus', () => {
  it('유효한 값이면 해당 값을 반환한다', () => {
    expect(toValidStatus('ON_SALE', VALID_STATUSES)).toBe('ON_SALE');
    expect(toValidStatus('HIDDEN', VALID_STATUSES)).toBe('HIDDEN');
  });

  it('유효하지 않은 값이면 undefined를 반환한다', () => {
    expect(toValidStatus('INVALID', VALID_STATUSES)).toBeUndefined();
  });

  it('null이면 undefined를 반환한다', () => {
    expect(toValidStatus(null, VALID_STATUSES)).toBeUndefined();
  });

  it('빈 문자열이면 undefined를 반환한다', () => {
    expect(toValidStatus('', VALID_STATUSES)).toBeUndefined();
  });
});
