import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin partnership proxy route handlers (TASK-PC-FE-187):
 *   - POST /api/partnerships (invite) — 201 / 422 malformed / passthrough
 *   - GET  /api/partnerships (list) — 200
 *   - POST /api/partnerships/[id]/accept — 200 / 422 no-reason
 *   - POST /api/partnerships/[id]/terminate — 200
 *   - POST /api/partnerships/[id]/participants/[operatorId] — 201 (scope + null)
 *   - DELETE .../participants/[operatorId] — 204
 *   - producer 403/409/422 passthrough; 503 on PartnershipsUnavailableError.
 * The api layer is mocked; body/param validation + error mapping are asserted
 * in isolation. The tenant id is NEVER accepted from the client (invite body
 * carries only partnerTenantId; the host tenant is the server active tenant).
 */

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

const listPartnerships = vi.fn();
const invitePartnership = vi.fn();
const acceptPartnership = vi.fn();
const terminatePartnership = vi.fn();
const addParticipant = vi.fn();
const removeParticipant = vi.fn();
vi.mock('@/features/partnerships/api/partnerships-api', () => ({
  listPartnerships: (...a: unknown[]) => listPartnerships(...a),
  invitePartnership: (...a: unknown[]) => invitePartnership(...a),
  acceptPartnership: (...a: unknown[]) => acceptPartnership(...a),
  terminatePartnership: (...a: unknown[]) => terminatePartnership(...a),
  addParticipant: (...a: unknown[]) => addParticipant(...a),
  removeParticipant: (...a: unknown[]) => removeParticipant(...a),
}));

import { POST as invitePOST, GET as listGET } from '@/app/api/partnerships/route';
import { POST as acceptPOST } from '@/app/api/partnerships/[id]/accept/route';
import { POST as terminatePOST } from '@/app/api/partnerships/[id]/terminate/route';
import {
  POST as participantPOST,
  DELETE as participantDELETE,
} from '@/app/api/partnerships/[id]/participants/[operatorId]/route';
import { ApiError, PartnershipsUnavailableError } from '@/shared/api/errors';

function req(path: string, method: string, body?: unknown) {
  return new Request(`http://console.local${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}
const idParams = (id: string) => ({ params: Promise.resolve({ id }) });
const participantParams = (id: string, operatorId: string) => ({
  params: Promise.resolve({ id, operatorId }),
});

const SCOPE = { domains: ['wms'], roles: ['WMS_OUTBOUND_OPERATOR'] };
const PARTNERSHIP = {
  partnershipId: 'p-1',
  hostTenantId: 'acme-corp',
  partnerTenantId: 'globex-corp',
  status: 'PENDING',
  delegatedScope: SCOPE,
  invitedAt: '2026-07-04T10:00:00Z',
};

beforeEach(() => {
  listPartnerships.mockReset();
  invitePartnership.mockReset();
  acceptPartnership.mockReset();
  terminatePartnership.mockReset();
  addParticipant.mockReset();
  removeParticipant.mockReset();
});

describe('POST /api/partnerships (invite)', () => {
  it('201 with the producer result on a valid body', async () => {
    invitePartnership.mockResolvedValue(PARTNERSHIP);
    const res = await invitePOST(
      req('/api/partnerships', 'POST', {
        partnerTenantId: 'globex-corp',
        delegatedScope: SCOPE,
        reason: '초대',
      }),
    );
    expect(res.status).toBe(201);
    expect(await res.json()).toEqual(PARTNERSHIP);
    // tenant id is NOT passed from the client — only partnerTenantId + scope.
    expect(invitePartnership).toHaveBeenCalledWith('globex-corp', SCOPE, '초대');
  });

  it('422 on a malformed body (missing delegatedScope)', async () => {
    const res = await invitePOST(
      req('/api/partnerships', 'POST', {
        partnerTenantId: 'globex-corp',
        reason: 'x',
      }),
    );
    expect(res.status).toBe(422);
    expect(invitePartnership).not.toHaveBeenCalled();
  });

  it('422 on an unknown extra key (.strict — never forwarded)', async () => {
    const res = await invitePOST(
      req('/api/partnerships', 'POST', {
        partnerTenantId: 'globex-corp',
        delegatedScope: SCOPE,
        reason: 'x',
        tenantId: 'attacker-supplied', // must be rejected, not forwarded
      }),
    );
    expect(res.status).toBe(422);
    expect(invitePartnership).not.toHaveBeenCalled();
  });

  it('403 PERMISSION_DENIED passes through', async () => {
    invitePartnership.mockRejectedValue(
      new ApiError(403, 'PERMISSION_DENIED', 'no'),
    );
    const res = await invitePOST(
      req('/api/partnerships', 'POST', {
        partnerTenantId: 'globex-corp',
        delegatedScope: SCOPE,
        reason: 'x',
      }),
    );
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('PERMISSION_DENIED');
  });

  it('409 PARTNERSHIP_ALREADY_EXISTS passes through', async () => {
    invitePartnership.mockRejectedValue(
      new ApiError(409, 'PARTNERSHIP_ALREADY_EXISTS', 'exists'),
    );
    const res = await invitePOST(
      req('/api/partnerships', 'POST', {
        partnerTenantId: 'globex-corp',
        delegatedScope: SCOPE,
        reason: 'x',
      }),
    );
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('PARTNERSHIP_ALREADY_EXISTS');
  });

  it('422 PARTNERSHIP_SCOPE_INVALID passes through', async () => {
    invitePartnership.mockRejectedValue(
      new ApiError(422, 'PARTNERSHIP_SCOPE_INVALID', 'bad scope'),
    );
    const res = await invitePOST(
      req('/api/partnerships', 'POST', {
        partnerTenantId: 'globex-corp',
        delegatedScope: SCOPE,
        reason: 'x',
      }),
    );
    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('PARTNERSHIP_SCOPE_INVALID');
  });

  it('503 on PartnershipsUnavailableError', async () => {
    invitePartnership.mockRejectedValue(
      new PartnershipsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'),
    );
    const res = await invitePOST(
      req('/api/partnerships', 'POST', {
        partnerTenantId: 'globex-corp',
        delegatedScope: SCOPE,
        reason: 'x',
      }),
    );
    expect(res.status).toBe(503);
  });
});

describe('GET /api/partnerships (list)', () => {
  it('200 with the producer page + passes role/status filters', async () => {
    const page = {
      items: [{ ...PARTNERSHIP, myRole: 'host', participantCount: 0 }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    listPartnerships.mockResolvedValue(page);
    const res = await listGET(
      new Request(
        'http://console.local/api/partnerships?role=host&status=ACTIVE&page=0&size=20',
      ),
    );
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual(page);
    expect(listPartnerships).toHaveBeenCalledWith({
      role: 'host',
      status: 'ACTIVE',
      page: 0,
      size: 20,
    });
  });

  it('503 on PartnershipsUnavailableError (list degrades)', async () => {
    listPartnerships.mockRejectedValue(
      new PartnershipsUnavailableError('timeout', 'TIMEOUT', 'x'),
    );
    const res = await listGET(
      new Request('http://console.local/api/partnerships'),
    );
    expect(res.status).toBe(503);
  });
});

describe('POST /api/partnerships/[id]/accept', () => {
  it('200 and calls acceptPartnership(id, reason)', async () => {
    acceptPartnership.mockResolvedValue({ ...PARTNERSHIP, status: 'ACTIVE' });
    const res = await acceptPOST(
      req('/api/partnerships/p-1/accept', 'POST', { reason: '수락' }),
      idParams('p-1'),
    );
    expect(res.status).toBe(200);
    expect(acceptPartnership).toHaveBeenCalledWith('p-1', '수락');
  });

  it('422 when the body has no reason (never fabricated)', async () => {
    const res = await acceptPOST(
      req('/api/partnerships/p-1/accept', 'POST', {}),
      idParams('p-1'),
    );
    expect(res.status).toBe(422);
    expect(acceptPartnership).not.toHaveBeenCalled();
  });
});

describe('POST /api/partnerships/[id]/terminate', () => {
  it('200 and calls terminatePartnership(id, reason)', async () => {
    terminatePartnership.mockResolvedValue({
      ...PARTNERSHIP,
      status: 'TERMINATED',
    });
    const res = await terminatePOST(
      req('/api/partnerships/p-1/terminate', 'POST', { reason: '종료' }),
      idParams('p-1'),
    );
    expect(res.status).toBe(200);
    expect(terminatePartnership).toHaveBeenCalledWith('p-1', '종료');
  });
});

describe('POST /api/partnerships/[id]/participants/[operatorId]', () => {
  it('201 with a participantScope', async () => {
    addParticipant.mockResolvedValue({
      partnershipId: 'p-1',
      operatorId: 'op-1',
      participantScope: SCOPE,
      assignedAt: 'x',
    });
    const res = await participantPOST(
      req('/api/partnerships/p-1/participants/op-1', 'POST', {
        participantScope: SCOPE,
        reason: '배정',
      }),
      participantParams('p-1', 'op-1'),
    );
    expect(res.status).toBe(201);
    expect(addParticipant).toHaveBeenCalledWith('p-1', 'op-1', SCOPE, '배정');
  });

  it('201 with an omitted scope → null (full delegatedScope)', async () => {
    addParticipant.mockResolvedValue({
      partnershipId: 'p-1',
      operatorId: 'op-1',
      assignedAt: 'x',
    });
    const res = await participantPOST(
      req('/api/partnerships/p-1/participants/op-1', 'POST', {
        reason: '배정',
      }),
      participantParams('p-1', 'op-1'),
    );
    expect(res.status).toBe(201);
    expect(addParticipant).toHaveBeenCalledWith('p-1', 'op-1', null, '배정');
  });

  it('422 PARTICIPANT_NOT_OWN_OPERATOR passes through', async () => {
    addParticipant.mockRejectedValue(
      new ApiError(422, 'PARTICIPANT_NOT_OWN_OPERATOR', 'not own'),
    );
    const res = await participantPOST(
      req('/api/partnerships/p-1/participants/op-1', 'POST', {
        reason: 'x',
      }),
      participantParams('p-1', 'op-1'),
    );
    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('PARTICIPANT_NOT_OWN_OPERATOR');
  });
});

describe('DELETE /api/partnerships/[id]/participants/[operatorId]', () => {
  it('204 and calls removeParticipant(id, operatorId, reason)', async () => {
    removeParticipant.mockResolvedValue(undefined);
    const res = await participantDELETE(
      req('/api/partnerships/p-1/participants/op-1', 'DELETE', {
        reason: '해제',
      }),
      participantParams('p-1', 'op-1'),
    );
    expect(res.status).toBe(204);
    expect(removeParticipant).toHaveBeenCalledWith('p-1', 'op-1', '해제');
  });

  it('404 PARTICIPANT_NOT_FOUND passes through', async () => {
    removeParticipant.mockRejectedValue(
      new ApiError(404, 'PARTICIPANT_NOT_FOUND', 'gone'),
    );
    const res = await participantDELETE(
      req('/api/partnerships/p-1/participants/op-1', 'DELETE', {
        reason: 'x',
      }),
      participantParams('p-1', 'op-1'),
    );
    expect(res.status).toBe(404);
    expect((await res.json()).code).toBe('PARTICIPANT_NOT_FOUND');
  });
});
