import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin group ADR-MONO-083 「업무 권한 세트」 proxy routes
 * (TASK-PC-FE-338 slice b — admin-api.md § 도메인 업무 권한 세트 rows 9-11):
 *   `app/api/groups/[groupId]/domain-permission-sets/route.ts` (GET + POST),
 *   `app/api/groups/[groupId]/domain-permission-sets/[setId]/route.ts`
 *   (DELETE). The api layer is mocked (mirrors `groups-proxy.test.ts`).
 */

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

const listGroupDomainSets = vi.fn();
const addGroupDomainSet = vi.fn();
const removeGroupDomainSet = vi.fn();
vi.mock('@/features/operator-groups/api/group-domain-sets-api', () => ({
  listGroupDomainSets: (...a: unknown[]) => listGroupDomainSets(...a),
  addGroupDomainSet: (...a: unknown[]) => addGroupDomainSet(...a),
  removeGroupDomainSet: (...a: unknown[]) => removeGroupDomainSet(...a),
}));

import { GET as listGET, POST as addPOST } from '@/app/api/groups/[groupId]/domain-permission-sets/route';
import { DELETE as removeDELETE } from '@/app/api/groups/[groupId]/domain-permission-sets/[setId]/route';
import { ApiError, GroupsUnavailableError } from '@/shared/api/errors';

function jsonReq(url: string, method: string, body: unknown) {
  return new Request(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}
const groupParams = (groupId: string) => ({ params: Promise.resolve({ groupId }) });
const groupSetParams = (groupId: string, setId: string) => ({
  params: Promise.resolve({ groupId, setId }),
});

const GROUP_SET = {
  setId: 'set-1',
  name: '재고팀',
  systemKey: null,
  grantedAt: '2026-10-14T00:00:00Z',
  grantedBy: 'op-9',
};

beforeEach(() => {
  [listGroupDomainSets, addGroupDomainSet, removeGroupDomainSet].forEach((m) => m.mockReset());
});

describe('GET /api/groups/{groupId}/domain-permission-sets (row 9)', () => {
  it('200 with the producer list', async () => {
    listGroupDomainSets.mockResolvedValue([GROUP_SET]);
    const res = await listGET(new Request('http://console.local/x'), groupParams('g-1'));
    expect(res.status).toBe(200);
    expect((await res.json()).items).toEqual([GROUP_SET]);
  });

  it('404 GROUP_NOT_FOUND passes through verbatim', async () => {
    listGroupDomainSets.mockRejectedValue(new ApiError(404, 'GROUP_NOT_FOUND', 'x'));
    const res = await listGET(new Request('http://console.local/x'), groupParams('g-1'));
    expect(res.status).toBe(404);
  });

  it('503 on GroupsUnavailableError (section-only degrade)', async () => {
    listGroupDomainSets.mockRejectedValue(
      new GroupsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'),
    );
    const res = await listGET(new Request('http://console.local/x'), groupParams('g-1'));
    expect(res.status).toBe(503);
  });
});

describe('POST /api/groups/{groupId}/domain-permission-sets (row 10 — add, Idempotency-Key REQUIRED)', () => {
  it('201 + forwards setId + reason + idempotencyKey (BOTH required) and fannedOutRows is in the response', async () => {
    addGroupDomainSet.mockResolvedValue({ item: GROUP_SET, fannedOutRows: 5 });
    const res = await addPOST(
      jsonReq('http://console.local/x', 'POST', {
        setId: 'set-1',
        reason: 'team grant',
        idempotencyKey: 'idem-1',
      }),
      groupParams('g-1'),
    );
    expect(res.status).toBe(201);
    expect(addGroupDomainSet).toHaveBeenCalledWith('g-1', 'set-1', 'team grant', 'idem-1');
    const body = await res.json();
    expect(body.fannedOutRows).toBe(5);
  });

  it('422 when idempotencyKey is missing (REQUIRED on this surface, unlike the operator-personal-grant matrix)', async () => {
    const res = await addPOST(
      jsonReq('http://console.local/x', 'POST', { setId: 'set-1', reason: 'team grant' }),
      groupParams('g-1'),
    );
    expect(res.status).toBe(422);
    expect(addGroupDomainSet).not.toHaveBeenCalled();
  });

  it('409 IDEMPOTENCY_KEY_CONFLICT passes through verbatim', async () => {
    addGroupDomainSet.mockRejectedValue(
      new ApiError(409, 'IDEMPOTENCY_KEY_CONFLICT', 'x'),
    );
    const res = await addPOST(
      jsonReq('http://console.local/x', 'POST', {
        setId: 'set-1',
        reason: 'r',
        idempotencyKey: 'idem-1',
      }),
      groupParams('g-1'),
    );
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('IDEMPOTENCY_KEY_CONFLICT');
  });
});

describe('DELETE /api/groups/{groupId}/domain-permission-sets/{setId} (row 11 — remove, NO key)', () => {
  it('204 + forwards the reason, no idempotencyKey accepted', async () => {
    removeGroupDomainSet.mockResolvedValue(undefined);
    const res = await removeDELETE(
      jsonReq('http://console.local/x', 'DELETE', { reason: 'cleanup' }),
      groupSetParams('g-1', 'set-1'),
    );
    expect(res.status).toBe(204);
    expect(removeGroupDomainSet).toHaveBeenCalledWith('g-1', 'set-1', 'cleanup');
  });

  it('422 when the body carries an idempotencyKey (.strict() rejects unknown keys)', async () => {
    const res = await removeDELETE(
      jsonReq('http://console.local/x', 'DELETE', {
        reason: 'cleanup',
        idempotencyKey: 'should-not-be-sent',
      }),
      groupSetParams('g-1', 'set-1'),
    );
    expect(res.status).toBe(422);
    expect(removeGroupDomainSet).not.toHaveBeenCalled();
  });

  it('404 DOMAIN_PERMISSION_SET_NOT_FOUND passes through verbatim', async () => {
    removeGroupDomainSet.mockRejectedValue(
      new ApiError(404, 'DOMAIN_PERMISSION_SET_NOT_FOUND', 'x'),
    );
    const res = await removeDELETE(
      jsonReq('http://console.local/x', 'DELETE', { reason: 'cleanup' }),
      groupSetParams('g-1', 'set-1'),
    );
    expect(res.status).toBe(404);
  });
});
