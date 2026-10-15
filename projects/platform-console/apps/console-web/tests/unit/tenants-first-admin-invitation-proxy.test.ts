import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-773 S6 (owner decision 2026-10-16 UTC) — the BFF behind the `/tenants` «다시 초대» (new address / NONE):
 * `POST /api/tenants/{tenantId}/first-admin-invitation` → IAM `POST /api/admin/tenants/{tenantId}/first-admin-invitation`
 * (admin-api.md — `X-Operator-Reason` + `Idempotency-Key` required; body `{email, displayName}` with the same rules as
 * the create's `firstAdmin`). And the PATCH proxy lets the two row-action statuses through (`SUSPENDED` «정리» ·
 * `ACTIVE` «활성화 마치기»).
 *
 * The api layer is mocked (same lane as `tenants-proxy.test.ts`).
 */

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

const inviteFirstAdmin = vi.fn();
const updateTenant = vi.fn();
vi.mock('@/features/tenants/api/tenants-api', () => ({
  listTenants: vi.fn(),
  getTenant: vi.fn(),
  createTenant: vi.fn(),
  updateTenant: (...a: unknown[]) => updateTenant(...a),
  inviteFirstAdmin: (...a: unknown[]) => inviteFirstAdmin(...a),
}));

import { POST as invitePOST } from '@/app/api/tenants/[tenantId]/first-admin-invitation/route';
import { PATCH as updatePATCH } from '@/app/api/tenants/[tenantId]/route';
import { ApiError } from '@/shared/api/errors';

const params = (tenantId: string) => ({ params: Promise.resolve({ tenantId }) });

function postInvite(body: unknown) {
  return new Request('http://console.local/api/tenants/acme-corp/first-admin-invitation', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

const VALID = {
  email: ' owner2@acme.example ',
  displayName: ' 김대표 ',
  reason: '담당자 변경',
  idempotencyKey: 'key-1',
};

beforeEach(() => {
  inviteFirstAdmin.mockReset();
  updateTenant.mockReset();
});

describe('POST /api/tenants/{tenantId}/first-admin-invitation', () => {
  it('🔴 201 — forwards (tenantId, {email, displayName} trimmed, reason, idempotencyKey)', async () => {
    inviteFirstAdmin.mockResolvedValue({ invitationId: 'inv-2', firstAdmin: true });
    const res = await invitePOST(postInvite(VALID), params('acme-corp'));
    expect(res.status).toBe(201);
    expect(await res.json()).toEqual({ invitationId: 'inv-2', firstAdmin: true });
    expect(inviteFirstAdmin).toHaveBeenCalledWith(
      'acme-corp',
      { email: 'owner2@acme.example', displayName: '김대표' },
      '담당자 변경',
      'key-1',
    );
  });

  it.each([
    ['no idempotency key', { ...VALID, idempotencyKey: undefined }],
    ['malformed email', { ...VALID, email: 'not-an-email' }],
    ['blank display name', { ...VALID, displayName: '  ' }],
    ['unknown field', { ...VALID, firstAdmin: true }],
  ])('🔴 %s → 422 before IAM', async (_l, body) => {
    const res = await invitePOST(postInvite(body), params('acme-corp'));
    expect(res.status).toBe(422);
    expect(inviteFirstAdmin).not.toHaveBeenCalled();
  });

  it.each([
    [409, 'TENANT_NOT_PENDING'],
    [409, 'OPERATOR_INVITATION_ALREADY_PENDING'],
    [404, 'TENANT_NOT_FOUND'],
  ])('🔴 IAM %i %s passes through inline', async (status, code) => {
    inviteFirstAdmin.mockRejectedValue(new ApiError(status, code, 'x'));
    const res = await invitePOST(postInvite(VALID), params('acme-corp'));
    expect(res.status).toBe(status);
    expect(((await res.json()) as { code: string }).code).toBe(code);
  });
});

describe('PATCH /api/tenants/{tenantId} — the row-action statuses pass', () => {
  it.each(['SUSPENDED', 'ACTIVE'])('🔵 status=%s reaches updateTenant', async (status) => {
    updateTenant.mockResolvedValue({ tenantId: 'acme-corp', status });
    const res = await updatePATCH(
      new Request('http://console.local/api/tenants/acme-corp', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status, reason: 'r' }),
      }),
      params('acme-corp'),
    );
    expect(res.status).toBe(200);
    expect(updateTenant).toHaveBeenCalledWith('acme-corp', { displayName: undefined, status }, 'r');
  });

  it('🔴 409 TENANT_FIRST_ADMIN_PENDING passes through inline', async () => {
    updateTenant.mockRejectedValue(new ApiError(409, 'TENANT_FIRST_ADMIN_PENDING', 'x'));
    const res = await updatePATCH(
      new Request('http://console.local/api/tenants/acme-corp', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'ACTIVE', reason: 'r' }),
      }),
      params('acme-corp'),
    );
    expect(res.status).toBe(409);
    expect(((await res.json()) as { code: string }).code).toBe('TENANT_FIRST_ADMIN_PENDING');
  });
});
