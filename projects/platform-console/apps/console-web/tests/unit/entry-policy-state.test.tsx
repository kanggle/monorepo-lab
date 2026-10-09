import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

/**
 * TASK-MONO-771 S5 — the entry-policy SSR state (§ 2.4.3.3 resilience) and the
 * section that renders it: 403 → inline «권한 없음» with NO toggle (hidden),
 * 503 → degraded note for this control only, 401 → re-login; and the
 * «self-effect» hint read from the session IAM token's `amr`.
 */

const getEntryPolicy = vi.fn();
vi.mock('@/features/tenant-entry-policy/api/entry-policy-api', () => ({
  getEntryPolicy: (...a: unknown[]) => getEntryPolicy(...a),
}));

const session = vi.hoisted(() => ({ accessToken: null as string | null }));
vi.mock('@/shared/lib/session', () => ({
  getAccessToken: async () => session.accessToken,
}));

const redirectSpy = vi.fn((to: string) => {
  throw new Error(`NEXT_REDIRECT ${to}`);
});
vi.mock('next/navigation', () => ({ redirect: (to: string) => redirectSpy(to) }));

vi.mock('@/features/tenant-entry-policy/components/EntryPolicyPanel', () => ({
  EntryPolicyPanel: ({ tenantId }: { tenantId: string }) => (
    <div data-testid="entry-policy-panel" data-tenant-id={tenantId}>
      <button data-testid="entry-policy-toggle" />
    </div>
  ),
}));

import { getEntryPolicyState } from '@/features/tenant-entry-policy/api/entry-policy-state';
import { EntryPolicySection } from '@/features/tenant-entry-policy/components/EntryPolicySection';
import { ApiError, TenantsUnavailableError } from '@/shared/api/errors';

function jwt(payload: Record<string, unknown>): string {
  const b64 = (o: unknown) => Buffer.from(JSON.stringify(o)).toString('base64url');
  return `${b64({ alg: 'none' })}.${b64(payload)}.sig`;
}

const POLICY = { tenantId: 'acme', requireMfa: false, updatedAt: null, updatedBy: null };

beforeEach(() => {
  getEntryPolicy.mockReset();
  redirectSpy.mockClear();
  session.accessToken = null;
});

describe('getEntryPolicyState — self-effect hint from the session amr', () => {
  it.each([
    [{ amr: ['pwd', 'otp', 'mfa'] }, true],
    [{ amr: ['pwd'] }, false],
    [{}, null],
  ])('amr %j → selfHasSecondFactor=%s', async (payload, expected) => {
    session.accessToken = jwt(payload);
    getEntryPolicy.mockResolvedValue(POLICY);
    expect((await getEntryPolicyState('acme')).selfHasSecondFactor).toBe(expected);
  });
});

describe('getEntryPolicyState + EntryPolicySection — resilience', () => {
  it('ok → the panel for that tenant', async () => {
    getEntryPolicy.mockResolvedValue(POLICY);
    const state = await getEntryPolicyState('acme');
    const { getByTestId } = render(<EntryPolicySection tenantId="acme" state={state} />);
    expect(getByTestId('entry-policy-panel')).toHaveAttribute('data-tenant-id', 'acme');
  });

  it.each(['PERMISSION_DENIED', 'TENANT_SCOPE_DENIED'])(
    '🔴 403 %s → inline «권한 없음», and the toggle is NOT rendered',
    async (code) => {
      getEntryPolicy.mockRejectedValue(new ApiError(403, code, 'no'));
      const state = await getEntryPolicyState('acme');
      const { getByTestId, queryByTestId } = render(<EntryPolicySection tenantId="acme" state={state} />);
      expect(getByTestId('entry-policy-permission-denied')).toBeInTheDocument();
      expect(queryByTestId('entry-policy-toggle')).toBeNull();
    },
  );

  it('503 → degraded note for this control only', async () => {
    getEntryPolicy.mockRejectedValue(new TenantsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'));
    const state = await getEntryPolicyState('acme');
    const { getByTestId, queryByTestId } = render(<EntryPolicySection tenantId="acme" state={state} />);
    expect(getByTestId('entry-policy-degraded')).toBeInTheDocument();
    expect(queryByTestId('entry-policy-toggle')).toBeNull();
  });

  it('401 → whole-session re-login', async () => {
    getEntryPolicy.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'x'));
    await expect(getEntryPolicyState('acme')).rejects.toThrow('NEXT_REDIRECT');
    expect(redirectSpy).toHaveBeenCalledWith('/login?error=session_expired');
  });
});
