import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin domain-permission-set proxy routes (TASK-PC-FE-338 /
 * ADR-MONO-083):
 *   `app/api/domain-roles/route.ts` (GET catalog),
 *   `app/api/domain-permission-sets/route.ts` (GET list + POST create),
 *   `app/api/domain-permission-sets/[setId]/route.ts` (GET + PATCH + DELETE).
 * The api layer is mocked; body/param validation + error mapping are
 * asserted in isolation (mirrors `groups-proxy.test.ts` conventions).
 */

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

const listDomainRoles = vi.fn();
const listDomainPermissionSets = vi.fn();
const getDomainPermissionSet = vi.fn();
const createDomainPermissionSet = vi.fn();
const updateDomainPermissionSet = vi.fn();
const deleteDomainPermissionSet = vi.fn();
vi.mock('@/features/domain-permission-sets/api/domain-permission-sets-api', () => ({
  listDomainRoles: (...a: unknown[]) => listDomainRoles(...a),
  listDomainPermissionSets: (...a: unknown[]) => listDomainPermissionSets(...a),
  getDomainPermissionSet: (...a: unknown[]) => getDomainPermissionSet(...a),
  createDomainPermissionSet: (...a: unknown[]) => createDomainPermissionSet(...a),
  updateDomainPermissionSet: (...a: unknown[]) => updateDomainPermissionSet(...a),
  deleteDomainPermissionSet: (...a: unknown[]) => deleteDomainPermissionSet(...a),
}));

import { GET as rolesGET } from '@/app/api/domain-roles/route';
import { GET as listGET, POST as createPOST } from '@/app/api/domain-permission-sets/route';
import {
  GET as detailGET,
  PATCH as updatePATCH,
  DELETE as deleteDELETE,
} from '@/app/api/domain-permission-sets/[setId]/route';
import { ApiError, DomainPermissionSetsUnavailableError } from '@/shared/api/errors';

function get(url: string) {
  return new Request(url);
}
function jsonReq(url: string, method: string, body: unknown) {
  return new Request(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}
const setParams = (setId: string) => ({ params: Promise.resolve({ setId }) });

const SET = {
  setId: 'set-1',
  tenantId: 'acme-corp',
  systemKey: null,
  readOnly: false,
  name: '재고팀',
  description: '재고 조회 · 변경',
  roles: [
    { domainKey: 'wms', role: 'INVENTORY_READ' },
    { domainKey: 'wms', role: 'INVENTORY_WRITE' },
  ],
  domains: ['wms'],
  grantCount: 0,
  groupCount: 0,
  createdAt: '2026-10-14T09:00:00Z',
  updatedAt: '2026-10-14T09:00:00Z',
};

beforeEach(() => {
  [
    listDomainRoles,
    listDomainPermissionSets,
    getDomainPermissionSet,
    createDomainPermissionSet,
    updateDomainPermissionSet,
    deleteDomainPermissionSet,
  ].forEach((m) => m.mockReset());
});

describe('GET /api/domain-roles (catalog)', () => {
  it('200 with the producer catalog', async () => {
    listDomainRoles.mockResolvedValue({
      items: [
        { domainKey: 'wms', role: 'INVENTORY_READ', access: 'READ', fineGrained: true, label: '재고 조회' },
      ],
    });
    const res = await rolesGET();
    expect(res.status).toBe(200);
    expect((await res.json()).items).toHaveLength(1);
  });

  it('403 PERMISSION_DENIED (lacks domain_access.manage) passes through', async () => {
    listDomainRoles.mockRejectedValue(new ApiError(403, 'PERMISSION_DENIED', 'no'));
    const res = await rolesGET();
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('PERMISSION_DENIED');
  });

  it('401 forces re-login', async () => {
    listDomainRoles.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'x'));
    const res = await rolesGET();
    expect(res.status).toBe(401);
  });
});

describe('GET /api/domain-permission-sets (list)', () => {
  it('200 with the producer list', async () => {
    listDomainPermissionSets.mockResolvedValue({ items: [SET] });
    const res = await listGET();
    expect(res.status).toBe(200);
    expect((await res.json()).items).toEqual([SET]);
  });

  it('503 on DomainPermissionSetsUnavailableError (section-only degrade)', async () => {
    listDomainPermissionSets.mockRejectedValue(
      new DomainPermissionSetsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'),
    );
    const res = await listGET();
    expect(res.status).toBe(503);
  });

  it('400 NO_ACTIVE_TENANT passes through as the tenant gate', async () => {
    listDomainPermissionSets.mockRejectedValue(
      new ApiError(400, 'NO_ACTIVE_TENANT', 'no tenant'),
    );
    const res = await listGET();
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('NO_ACTIVE_TENANT');
  });
});

describe('POST /api/domain-permission-sets (create)', () => {
  it('201 + forwards reason + Idempotency-Key (REQUIRED on create)', async () => {
    createDomainPermissionSet.mockResolvedValue(SET);
    const res = await createPOST(
      jsonReq('http://console.local/api/domain-permission-sets', 'POST', {
        name: '재고팀',
        description: '재고 조회 · 변경',
        roles: [{ domainKey: 'wms', role: 'INVENTORY_READ' }],
        reason: '재고팀 신설',
        idempotencyKey: 'idem-1',
      }),
    );
    expect(res.status).toBe(201);
    expect(createDomainPermissionSet).toHaveBeenCalledWith(
      {
        name: '재고팀',
        description: '재고 조회 · 변경',
        roles: [{ domainKey: 'wms', role: 'INVENTORY_READ' }],
      },
      '재고팀 신설',
      'idem-1',
    );
  });

  it('422 when the idempotency key is missing (schema rejects)', async () => {
    const res = await createPOST(
      jsonReq('http://console.local/api/domain-permission-sets', 'POST', {
        name: '재고팀',
        roles: [{ domainKey: 'wms', role: 'INVENTORY_READ' }],
        reason: 'r',
      }),
    );
    expect(res.status).toBe(422);
    expect(createDomainPermissionSet).not.toHaveBeenCalled();
  });

  it('422 when roles is empty', async () => {
    const res = await createPOST(
      jsonReq('http://console.local/api/domain-permission-sets', 'POST', {
        name: '재고팀',
        roles: [],
        reason: 'r',
        idempotencyKey: 'k',
      }),
    );
    expect(res.status).toBe(422);
    expect(createDomainPermissionSet).not.toHaveBeenCalled();
  });

  it('422 DOMAIN_NOT_SUBSCRIBED passes through', async () => {
    createDomainPermissionSet.mockRejectedValue(
      new ApiError(422, 'DOMAIN_NOT_SUBSCRIBED', 'not subscribed'),
    );
    const res = await createPOST(
      jsonReq('http://console.local/api/domain-permission-sets', 'POST', {
        name: '재고팀',
        roles: [{ domainKey: 'scm', role: 'SCM_OPERATOR' }],
        reason: 'r',
        idempotencyKey: 'k',
      }),
    );
    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('DOMAIN_NOT_SUBSCRIBED');
  });

  it('400 VALIDATION_ERROR (producer tenantId=* rejection) passes through', async () => {
    createDomainPermissionSet.mockRejectedValue(
      new ApiError(400, 'VALIDATION_ERROR', 'platform set must be a preset'),
    );
    const res = await createPOST(
      jsonReq('http://console.local/api/domain-permission-sets', 'POST', {
        name: '전역 세트',
        roles: [{ domainKey: 'wms', role: 'INVENTORY_READ' }],
        reason: 'r',
        idempotencyKey: 'k',
      }),
    );
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('VALIDATION_ERROR');
  });
});

describe('GET/PATCH/DELETE /api/domain-permission-sets/[setId]', () => {
  it('GET 200 with the producer set', async () => {
    getDomainPermissionSet.mockResolvedValue(SET);
    const res = await detailGET(
      get('http://console.local/api/domain-permission-sets/set-1'),
      setParams('set-1'),
    );
    expect(res.status).toBe(200);
    expect(getDomainPermissionSet).toHaveBeenCalledWith('set-1');
  });

  it('GET 404 DOMAIN_PERMISSION_SET_NOT_FOUND passes through', async () => {
    getDomainPermissionSet.mockRejectedValue(
      new ApiError(404, 'DOMAIN_PERMISSION_SET_NOT_FOUND', 'missing'),
    );
    const res = await detailGET(
      get('http://console.local/api/domain-permission-sets/missing'),
      setParams('missing'),
    );
    expect(res.status).toBe(404);
    expect((await res.json()).code).toBe('DOMAIN_PERMISSION_SET_NOT_FOUND');
  });

  it('PATCH 200 on a valid partial update (name only) — forwards reason, NO idempotency key arg', async () => {
    updateDomainPermissionSet.mockResolvedValue({ ...SET, name: '재고팀 (개편)' });
    const res = await updatePATCH(
      jsonReq('http://console.local/api/domain-permission-sets/set-1', 'PATCH', {
        name: '재고팀 (개편)',
        reason: '개편',
      }),
      setParams('set-1'),
    );
    expect(res.status).toBe(200);
    expect(updateDomainPermissionSet).toHaveBeenCalledWith(
      'set-1',
      { name: '재고팀 (개편)', description: undefined, roles: undefined },
      '개편',
    );
    // Sibling-function arity check — the SAME invariant proxy_route tests
    // assert for groups/tenants: the api fn never receives a 4th
    // (idempotencyKey) argument on PATCH.
    expect(updateDomainPermissionSet.mock.calls[0]).toHaveLength(3);
  });

  it('PATCH 422 — a body carrying `idempotencyKey` is rejected (schema .strict(), MUST NOT send)', async () => {
    const res = await updatePATCH(
      jsonReq('http://console.local/api/domain-permission-sets/set-1', 'PATCH', {
        name: '재고팀 (개편)',
        reason: '개편',
        idempotencyKey: 'should-not-be-sent',
      }),
      setParams('set-1'),
    );
    expect(res.status).toBe(422);
    expect(updateDomainPermissionSet).not.toHaveBeenCalled();
  });

  it('PATCH 422 when none of name/description/roles is present', async () => {
    const res = await updatePATCH(
      jsonReq('http://console.local/api/domain-permission-sets/set-1', 'PATCH', {
        reason: 'r',
      }),
      setParams('set-1'),
    );
    expect(res.status).toBe(422);
    expect(updateDomainPermissionSet).not.toHaveBeenCalled();
  });

  it('PATCH 409 DOMAIN_PERMISSION_SET_READ_ONLY (a system preset) passes through', async () => {
    updateDomainPermissionSet.mockRejectedValue(
      new ApiError(409, 'DOMAIN_PERMISSION_SET_READ_ONLY', 'preset'),
    );
    const res = await updatePATCH(
      jsonReq('http://console.local/api/domain-permission-sets/preset-1', 'PATCH', {
        name: 'x',
        reason: 'r',
      }),
      setParams('preset-1'),
    );
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('DOMAIN_PERMISSION_SET_READ_ONLY');
  });

  it('DELETE 204 forwards the body reason (never the query string), NO idempotency key', async () => {
    deleteDomainPermissionSet.mockResolvedValue(undefined);
    const res = await deleteDELETE(
      jsonReq('http://console.local/api/domain-permission-sets/set-1', 'DELETE', {
        reason: '폐지',
      }),
      setParams('set-1'),
    );
    expect(res.status).toBe(204);
    // 204 passthrough: null body, not a crashing `NextResponse.json` on no content.
    expect(await res.text()).toBe('');
    expect(deleteDomainPermissionSet).toHaveBeenCalledWith('set-1', '폐지');
    expect(deleteDomainPermissionSet.mock.calls[0]).toHaveLength(2);
  });

  it('DELETE 409 DOMAIN_PERMISSION_SET_IN_USE passes through', async () => {
    deleteDomainPermissionSet.mockRejectedValue(
      new ApiError(409, 'DOMAIN_PERMISSION_SET_IN_USE', 'in use'),
    );
    const res = await deleteDELETE(
      jsonReq('http://console.local/api/domain-permission-sets/set-1', 'DELETE', {
        reason: '폐지',
      }),
      setParams('set-1'),
    );
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('DOMAIN_PERMISSION_SET_IN_USE');
  });

  it('DELETE 422 when a body carrying `idempotencyKey` is rejected (schema .strict())', async () => {
    const res = await deleteDELETE(
      jsonReq('http://console.local/api/domain-permission-sets/set-1', 'DELETE', {
        reason: '폐지',
        idempotencyKey: 'should-not-be-sent',
      }),
      setParams('set-1'),
    );
    expect(res.status).toBe(422);
    expect(deleteDomainPermissionSet).not.toHaveBeenCalled();
  });
});
