import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin per-operator ADR-MONO-083 「업무 권한」 proxy routes
 * (TASK-PC-FE-338 slice b — admin-api.md § 도메인 업무 권한 세트 rows 6-8):
 *   `app/api/operators/[operatorId]/domain-access/route.ts` (GET),
 *   `app/api/operators/[operatorId]/domain-permission-sets/route.ts` (POST),
 *   `app/api/operators/[operatorId]/domain-permission-sets/[setId]/route.ts`
 *   (DELETE). The api layer is mocked; body validation + error mapping are
 *   asserted in isolation (mirrors `domain-permission-sets-proxy.test.ts`).
 */

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

const getOperatorDomainAccess = vi.fn();
const grantOperatorDomainSet = vi.fn();
const revokeOperatorDomainSet = vi.fn();
vi.mock('@/features/operators/api/operators-domain-access-api', () => ({
  getOperatorDomainAccess: (...a: unknown[]) => getOperatorDomainAccess(...a),
  grantOperatorDomainSet: (...a: unknown[]) => grantOperatorDomainSet(...a),
  revokeOperatorDomainSet: (...a: unknown[]) => revokeOperatorDomainSet(...a),
}));

import { GET as accessGET } from '@/app/api/operators/[operatorId]/domain-access/route';
import { POST as grantPOST } from '@/app/api/operators/[operatorId]/domain-permission-sets/route';
import { DELETE as revokeDELETE } from '@/app/api/operators/[operatorId]/domain-permission-sets/[setId]/route';
import { ApiError, OperatorsUnavailableError } from '@/shared/api/errors';

function jsonReq(url: string, method: string, body: unknown) {
  return new Request(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}
const opParams = (operatorId: string) => ({ params: Promise.resolve({ operatorId }) });
const opSetParams = (operatorId: string, setId: string) => ({
  params: Promise.resolve({ operatorId, setId }),
});

beforeEach(() => {
  [getOperatorDomainAccess, grantOperatorDomainSet, revokeOperatorDomainSet].forEach((m) =>
    m.mockReset(),
  );
});

describe('GET /api/operators/{operatorId}/domain-access (row 6)', () => {
  it('200 with the producer response', async () => {
    getOperatorDomainAccess.mockResolvedValue({
      operatorId: 'op-1',
      tenantId: 'acme-corp',
      roles: [],
      sets: [],
    });
    const res = await accessGET(new Request('http://console.local/x'), opParams('op-1'));
    expect(res.status).toBe(200);
    expect(getOperatorDomainAccess).toHaveBeenCalledWith('op-1');
  });

  it('404 OPERATOR_NOT_FOUND passes through verbatim', async () => {
    getOperatorDomainAccess.mockRejectedValue(
      new ApiError(404, 'OPERATOR_NOT_FOUND', 'no'),
    );
    const res = await accessGET(new Request('http://console.local/x'), opParams('op-1'));
    expect(res.status).toBe(404);
    expect((await res.json()).code).toBe('OPERATOR_NOT_FOUND');
  });

  it('503 on OperatorsUnavailableError (section-only degrade)', async () => {
    getOperatorDomainAccess.mockRejectedValue(
      new OperatorsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'),
    );
    const res = await accessGET(new Request('http://console.local/x'), opParams('op-1'));
    expect(res.status).toBe(503);
  });

  it('401 forces re-login', async () => {
    getOperatorDomainAccess.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'x'));
    const res = await accessGET(new Request('http://console.local/x'), opParams('op-1'));
    expect(res.status).toBe(401);
  });
});

describe('POST /api/operators/{operatorId}/domain-permission-sets (row 7 — DIRECT grant)', () => {
  it('201 + forwards setId + reason, no idempotencyKey field accepted', async () => {
    grantOperatorDomainSet.mockResolvedValue({
      operatorId: 'op-1',
      tenantId: 'acme-corp',
      setId: 'set-1',
      source: 'DIRECT',
      grantedAt: '2026-10-14T00:00:00Z',
      grantedBy: 'op-9',
      selfGrant: false,
    });
    const res = await grantPOST(
      jsonReq('http://console.local/x', 'POST', { setId: 'set-1', reason: 'onboarding' }),
      opParams('op-1'),
    );
    expect(res.status).toBe(201);
    expect(grantOperatorDomainSet).toHaveBeenCalledWith('op-1', 'set-1', 'onboarding');
  });

  it('422 when the body carries an idempotencyKey (.strict() rejects unknown keys)', async () => {
    const res = await grantPOST(
      jsonReq('http://console.local/x', 'POST', {
        setId: 'set-1',
        reason: 'onboarding',
        idempotencyKey: 'should-not-be-sent',
      }),
      opParams('op-1'),
    );
    expect(res.status).toBe(422);
    expect(grantOperatorDomainSet).not.toHaveBeenCalled();
  });

  it('409 DOMAIN_PERMISSION_SET_ALREADY_GRANTED passes through verbatim', async () => {
    grantOperatorDomainSet.mockRejectedValue(
      new ApiError(409, 'DOMAIN_PERMISSION_SET_ALREADY_GRANTED', 'x'),
    );
    const res = await grantPOST(
      jsonReq('http://console.local/x', 'POST', { setId: 'set-1', reason: 'r' }),
      opParams('op-1'),
    );
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('DOMAIN_PERMISSION_SET_ALREADY_GRANTED');
  });

  it('422 DOMAIN_NOT_SUBSCRIBED passes through verbatim', async () => {
    grantOperatorDomainSet.mockRejectedValue(
      new ApiError(422, 'DOMAIN_NOT_SUBSCRIBED', 'x'),
    );
    const res = await grantPOST(
      jsonReq('http://console.local/x', 'POST', { setId: 'set-1', reason: 'r' }),
      opParams('op-1'),
    );
    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('DOMAIN_NOT_SUBSCRIBED');
  });
});

describe('DELETE /api/operators/{operatorId}/domain-permission-sets/{setId} (row 8 — personal revoke)', () => {
  it('204 + forwards the reason from the body', async () => {
    revokeOperatorDomainSet.mockResolvedValue(undefined);
    const res = await revokeDELETE(
      jsonReq('http://console.local/x', 'DELETE', { reason: 'cleanup' }),
      opSetParams('op-1', 'set-1'),
    );
    expect(res.status).toBe(204);
    expect(revokeOperatorDomainSet).toHaveBeenCalledWith('op-1', 'set-1', 'cleanup');
  });

  it('404 DOMAIN_PERMISSION_SET_NOT_FOUND (group-only set — nothing to revoke) passes through', async () => {
    revokeOperatorDomainSet.mockRejectedValue(
      new ApiError(404, 'DOMAIN_PERMISSION_SET_NOT_FOUND', 'x'),
    );
    const res = await revokeDELETE(
      jsonReq('http://console.local/x', 'DELETE', { reason: 'cleanup' }),
      opSetParams('op-1', 'set-1'),
    );
    expect(res.status).toBe(404);
    expect((await res.json()).code).toBe('DOMAIN_PERMISSION_SET_NOT_FOUND');
  });
});
