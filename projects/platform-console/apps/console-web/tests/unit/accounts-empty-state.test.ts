import { describe, it, expect } from 'vitest';
import { classifyAccountsEmpty } from '@/features/accounts/lib/classify-empty';
import { ApiError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-063 — distinguish 검색 결과 없음 vs 조회 권한 없음 on the 계정 운영
 * empty state, as far as the backend allows (the producer returns empty-200 for
 * no-permission, so the unfiltered-empty case is the honest union).
 */
describe('classifyAccountsEmpty (TASK-PC-FE-063)', () => {
  it('client 403 → forbidden ("조회 권한이 없습니다.")', () => {
    const err = new ApiError(403, 'PERMISSION_DENIED', 'not permitted');
    expect(classifyAccountsEmpty(true, err, false)).toEqual({
      reason: 'forbidden',
      message: '조회 권한이 없습니다.',
    });
  });

  it('PERMISSION_DENIED code (any status) → forbidden', () => {
    const err = new ApiError(200, 'PERMISSION_DENIED', 'denied');
    expect(classifyAccountsEmpty(true, err, true).reason).toBe('forbidden');
  });

  it('other query error → load-error ("목록을 불러올 수 없습니다.")', () => {
    const err = new ApiError(503, 'DOWNSTREAM_ERROR', 'down');
    expect(classifyAccountsEmpty(true, err, false)).toEqual({
      reason: 'load-error',
      message: '목록을 불러올 수 없습니다.',
    });
  });

  it('non-ApiError error → load-error', () => {
    expect(classifyAccountsEmpty(true, new Error('boom'), false).reason).toBe(
      'load-error',
    );
  });

  it('no error + search filter active + empty → no-results ("검색 결과가 없습니다.")', () => {
    expect(classifyAccountsEmpty(false, null, true)).toEqual({
      reason: 'no-results',
      message: '검색 결과가 없습니다.',
    });
  });

  it('no error + unfiltered + empty → forbidden-or-empty (honest union)', () => {
    expect(classifyAccountsEmpty(false, null, false)).toEqual({
      reason: 'forbidden-or-empty',
      message: '조회 권한이 없거나 등록된 계정이 없습니다.',
    });
  });
});
