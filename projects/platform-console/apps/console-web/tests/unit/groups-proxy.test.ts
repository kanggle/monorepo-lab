import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin operator-group proxy routes (TASK-PC-FE-250 / ADR-MONO-046):
 *   `app/api/groups/route.ts` (GET list + POST create),
 *   `.../[groupId]/route.ts` (GET + PATCH + DELETE),
 *   `.../[groupId]/members/route.ts` (GET + POST),
 *   `.../[groupId]/members/[operatorId]/route.ts` (DELETE),
 *   `.../[groupId]/grants/route.ts` (GET + POST),
 *   `.../[groupId]/grants/[grantId]/route.ts` (DELETE).
 * The api layer is mocked; body/param validation + error mapping are asserted in
 * isolation (mirrors `tenants-proxy.test.ts` / `org-nodes` proxy conventions).
 */

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

const listGroups = vi.fn();
const getGroup = vi.fn();
const createGroup = vi.fn();
const updateGroup = vi.fn();
const deleteGroup = vi.fn();
const listMembers = vi.fn();
const addMember = vi.fn();
const removeMember = vi.fn();
const listGrants = vi.fn();
const addGrants = vi.fn();
const removeGrant = vi.fn();
vi.mock('@/features/operator-groups/api/operator-groups-api', () => ({
  listGroups: (...a: unknown[]) => listGroups(...a),
  getGroup: (...a: unknown[]) => getGroup(...a),
  createGroup: (...a: unknown[]) => createGroup(...a),
  updateGroup: (...a: unknown[]) => updateGroup(...a),
  deleteGroup: (...a: unknown[]) => deleteGroup(...a),
  listMembers: (...a: unknown[]) => listMembers(...a),
  addMember: (...a: unknown[]) => addMember(...a),
  removeMember: (...a: unknown[]) => removeMember(...a),
  listGrants: (...a: unknown[]) => listGrants(...a),
  addGrants: (...a: unknown[]) => addGrants(...a),
  removeGrant: (...a: unknown[]) => removeGrant(...a),
}));

import { GET as listGET, POST as createPOST } from '@/app/api/groups/route';
import {
  GET as detailGET,
  PATCH as updatePATCH,
  DELETE as deleteDELETE,
} from '@/app/api/groups/[groupId]/route';
import {
  GET as membersGET,
  POST as addMemberPOST,
} from '@/app/api/groups/[groupId]/members/route';
import { DELETE as removeMemberDELETE } from '@/app/api/groups/[groupId]/members/[operatorId]/route';
import {
  GET as grantsGET,
  POST as addGrantsPOST,
} from '@/app/api/groups/[groupId]/grants/route';
import { DELETE as revokeGrantDELETE } from '@/app/api/groups/[groupId]/grants/[grantId]/route';
import { ApiError, GroupsUnavailableError } from '@/shared/api/errors';

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
const groupParams = (groupId: string) => ({
  params: Promise.resolve({ groupId }),
});

const GROUP = {
  groupId: 'g-1',
  tenantId: 'acme-corp',
  name: '물류 지원팀',
  description: 'WMS 출고 지원 스쿼드',
  memberCount: 5,
  grantCount: 3,
  createdAt: '2026-07-19T09:00:00Z',
  updatedAt: '2026-07-19T09:00:00Z',
};

beforeEach(() => {
  [
    listGroups,
    getGroup,
    createGroup,
    updateGroup,
    deleteGroup,
    listMembers,
    addMember,
    removeMember,
    listGrants,
    addGrants,
    removeGrant,
  ].forEach((m) => m.mockReset());
});

describe('GET /api/groups (list)', () => {
  it('200 with the producer page + forwards the tenantId filter', async () => {
    listGroups.mockResolvedValue({
      items: [GROUP],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    const res = await listGET(
      get('http://console.local/api/groups?tenantId=acme-corp&page=0&size=20'),
    );
    expect(res.status).toBe(200);
    expect((await res.json()).items).toEqual([GROUP]);
    expect(listGroups).toHaveBeenCalledWith({
      tenantId: 'acme-corp',
      page: 0,
      size: 20,
    });
  });

  it('403 PERMISSION_DENIED (lacks group.manage) passes through', async () => {
    listGroups.mockRejectedValue(new ApiError(403, 'PERMISSION_DENIED', 'no'));
    const res = await listGET(get('http://console.local/api/groups'));
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('PERMISSION_DENIED');
  });

  it('401 forces re-login', async () => {
    listGroups.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'x'));
    const res = await listGET(get('http://console.local/api/groups'));
    expect(res.status).toBe(401);
  });

  it('503 on GroupsUnavailableError', async () => {
    listGroups.mockRejectedValue(
      new GroupsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'),
    );
    const res = await listGET(get('http://console.local/api/groups'));
    expect(res.status).toBe(503);
  });
});

describe('POST /api/groups (create)', () => {
  it('201 + forwards reason + idempotency key', async () => {
    createGroup.mockResolvedValue(GROUP);
    const res = await createPOST(
      jsonReq('http://console.local/api/groups', 'POST', {
        tenantId: 'acme-corp',
        name: '물류 지원팀',
        description: 'WMS 출고 지원 스쿼드',
        reason: '신규 스쿼드',
        idempotencyKey: 'idem-1',
      }),
    );
    expect(res.status).toBe(201);
    expect(createGroup).toHaveBeenCalledWith(
      {
        tenantId: 'acme-corp',
        name: '물류 지원팀',
        description: 'WMS 출고 지원 스쿼드',
      },
      '신규 스쿼드',
      'idem-1',
    );
  });

  it('422 on a platform-global tenant (`*`)', async () => {
    const res = await createPOST(
      jsonReq('http://console.local/api/groups', 'POST', {
        tenantId: '*',
        name: 'X',
        reason: 'r',
        idempotencyKey: 'k',
      }),
    );
    expect(res.status).toBe(422);
    expect(createGroup).not.toHaveBeenCalled();
  });

  it('422 when the idempotency key is missing', async () => {
    const res = await createPOST(
      jsonReq('http://console.local/api/groups', 'POST', {
        tenantId: 'acme-corp',
        name: 'X',
        reason: 'r',
      }),
    );
    expect(res.status).toBe(422);
    expect(createGroup).not.toHaveBeenCalled();
  });

  it('409 GROUP_NAME_CONFLICT passes through', async () => {
    createGroup.mockRejectedValue(
      new ApiError(409, 'GROUP_NAME_CONFLICT', 'exists'),
    );
    const res = await createPOST(
      jsonReq('http://console.local/api/groups', 'POST', {
        tenantId: 'acme-corp',
        name: '물류 지원팀',
        reason: 'r',
        idempotencyKey: 'k',
      }),
    );
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('GROUP_NAME_CONFLICT');
  });
});

describe('GET/PATCH/DELETE /api/groups/[groupId]', () => {
  it('GET 200 with the producer group', async () => {
    getGroup.mockResolvedValue(GROUP);
    const res = await detailGET(
      get('http://console.local/api/groups/g-1'),
      groupParams('g-1'),
    );
    expect(res.status).toBe(200);
    expect(getGroup).toHaveBeenCalledWith('g-1');
  });

  it('GET 404 GROUP_NOT_FOUND passes through', async () => {
    getGroup.mockRejectedValue(new ApiError(404, 'GROUP_NOT_FOUND', 'missing'));
    const res = await detailGET(
      get('http://console.local/api/groups/missing'),
      groupParams('missing'),
    );
    expect(res.status).toBe(404);
  });

  it('PATCH 200 on a valid partial update (name only)', async () => {
    updateGroup.mockResolvedValue({ ...GROUP, name: '물류 지원팀 (개편)' });
    const res = await updatePATCH(
      jsonReq('http://console.local/api/groups/g-1', 'PATCH', {
        name: '물류 지원팀 (개편)',
        reason: '개편',
      }),
      groupParams('g-1'),
    );
    expect(res.status).toBe(200);
    expect(updateGroup).toHaveBeenCalledWith(
      'g-1',
      { name: '물류 지원팀 (개편)', description: undefined },
      '개편',
    );
  });

  it('PATCH 422 when neither name nor description is present', async () => {
    const res = await updatePATCH(
      jsonReq('http://console.local/api/groups/g-1', 'PATCH', { reason: 'r' }),
      groupParams('g-1'),
    );
    expect(res.status).toBe(422);
    expect(updateGroup).not.toHaveBeenCalled();
  });

  it('DELETE 204 forwards the body reason (never the query string)', async () => {
    deleteGroup.mockResolvedValue(undefined);
    const res = await deleteDELETE(
      jsonReq('http://console.local/api/groups/g-1', 'DELETE', { reason: '해체' }),
      groupParams('g-1'),
    );
    expect(res.status).toBe(204);
    expect(deleteGroup).toHaveBeenCalledWith('g-1', '해체');
  });
});

describe('members proxy', () => {
  it('GET 200 lists members', async () => {
    listMembers.mockResolvedValue({ items: [] });
    const res = await membersGET(
      get('http://console.local/api/groups/g-1/members'),
      groupParams('g-1'),
    );
    expect(res.status).toBe(200);
    expect(listMembers).toHaveBeenCalledWith('g-1');
  });

  it('POST 201 add-member forwards reason + idempotency key', async () => {
    addMember.mockResolvedValue({
      operatorId: 'op-1',
      displayName: '김운영',
      addedAt: '2026-07-19T09:00:00Z',
      fannedOutGrants: 3,
    });
    const res = await addMemberPOST(
      jsonReq('http://console.local/api/groups/g-1/members', 'POST', {
        operatorId: 'op-1',
        reason: '스쿼드 합류',
        idempotencyKey: 'k',
      }),
      groupParams('g-1'),
    );
    expect(res.status).toBe(201);
    expect(addMember).toHaveBeenCalledWith(
      'g-1',
      { operatorId: 'op-1' },
      '스쿼드 합류',
      'k',
    );
  });

  it('POST 422 GROUP_MEMBER_TENANT_MISMATCH passes through', async () => {
    addMember.mockRejectedValue(
      new ApiError(422, 'GROUP_MEMBER_TENANT_MISMATCH', 'mismatch'),
    );
    const res = await addMemberPOST(
      jsonReq('http://console.local/api/groups/g-1/members', 'POST', {
        operatorId: 'op-x',
        reason: 'r',
        idempotencyKey: 'k',
      }),
      groupParams('g-1'),
    );
    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('GROUP_MEMBER_TENANT_MISMATCH');
  });

  it('DELETE 204 remove-member forwards the body reason', async () => {
    removeMember.mockResolvedValue(undefined);
    const res = await removeMemberDELETE(
      jsonReq('http://console.local/api/groups/g-1/members/op-1', 'DELETE', {
        reason: '이탈',
      }),
      { params: Promise.resolve({ groupId: 'g-1', operatorId: 'op-1' }) },
    );
    expect(res.status).toBe(204);
    expect(removeMember).toHaveBeenCalledWith('g-1', 'op-1', '이탈');
  });
});

describe('grants proxy', () => {
  it('GET 200 lists grants', async () => {
    listGrants.mockResolvedValue({ items: [] });
    const res = await grantsGET(
      get('http://console.local/api/groups/g-1/grants'),
      groupParams('g-1'),
    );
    expect(res.status).toBe(200);
    expect(listGrants).toHaveBeenCalledWith('g-1');
  });

  it('POST 201 add-grants forwards roles + tenantAssignments + headers', async () => {
    addGrants.mockResolvedValue({ items: [], fannedOutRows: 8 });
    const res = await addGrantsPOST(
      jsonReq('http://console.local/api/groups/g-1/grants', 'POST', {
        roles: ['SUPPORT_LOCK'],
        tenantAssignments: [{ tenantId: 'acme-corp' }],
        reason: '권한 부여',
        idempotencyKey: 'k',
      }),
      groupParams('g-1'),
    );
    expect(res.status).toBe(201);
    expect(addGrants).toHaveBeenCalledWith(
      'g-1',
      { roles: ['SUPPORT_LOCK'], tenantAssignments: [{ tenantId: 'acme-corp' }] },
      '권한 부여',
      'k',
    );
  });

  it('POST 422 when neither roles nor tenantAssignments is present', async () => {
    const res = await addGrantsPOST(
      jsonReq('http://console.local/api/groups/g-1/grants', 'POST', {
        reason: 'r',
        idempotencyKey: 'k',
      }),
      groupParams('g-1'),
    );
    expect(res.status).toBe(422);
    expect(addGrants).not.toHaveBeenCalled();
  });

  it('POST 403 ROLE_GRANT_FORBIDDEN (no-escalation) passes through', async () => {
    addGrants.mockRejectedValue(
      new ApiError(403, 'ROLE_GRANT_FORBIDDEN', 'no escalation'),
    );
    const res = await addGrantsPOST(
      jsonReq('http://console.local/api/groups/g-1/grants', 'POST', {
        roles: ['SUPER_ADMIN'],
        reason: 'r',
        idempotencyKey: 'k',
      }),
      groupParams('g-1'),
    );
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('ROLE_GRANT_FORBIDDEN');
  });

  it('POST 400 ROLE_NOT_FOUND passes through (deliberate 400)', async () => {
    addGrants.mockRejectedValue(new ApiError(400, 'ROLE_NOT_FOUND', 'no role'));
    const res = await addGrantsPOST(
      jsonReq('http://console.local/api/groups/g-1/grants', 'POST', {
        roles: ['BOGUS_ROLE'],
        reason: 'r',
        idempotencyKey: 'k',
      }),
      groupParams('g-1'),
    );
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('ROLE_NOT_FOUND');
  });

  it('DELETE 204 revoke-grant forwards the body reason', async () => {
    removeGrant.mockResolvedValue(undefined);
    const res = await revokeGrantDELETE(
      jsonReq('http://console.local/api/groups/g-1/grants/gr-1', 'DELETE', {
        reason: '회수',
      }),
      { params: Promise.resolve({ groupId: 'g-1', grantId: 'gr-1' }) },
    );
    expect(res.status).toBe(204);
    expect(removeGrant).toHaveBeenCalledWith('g-1', 'gr-1', '회수');
  });
});
