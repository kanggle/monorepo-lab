import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

/**
 * TASK-MONO-771 S5 — `/security-settings` («보안 설정», § 2.4.3.3 OD-1): the
 * page targets the ACTIVE tenant; no tenant → tenant gate; platform scope `*`
 * → pointer to 테넌트 상세 (no policy read at all); otherwise the control.
 */

const session = vi.hoisted(() => ({ activeTenant: 'acme' as string | null }));
vi.mock('@/shared/lib/session', () => ({
  getActiveTenant: async () => session.activeTenant,
}));

const getEntryPolicyState = vi.fn();
vi.mock('@/features/tenant-entry-policy', () => ({
  getEntryPolicyState: (a: unknown) => getEntryPolicyState(a),
  EntryPolicySection: ({ tenantId }: { tenantId: string }) => (
    <div data-testid="entry-policy-section" data-tenant-id={tenantId} />
  ),
}));

vi.mock('@/widgets/no-tenant-notice', () => ({
  NoTenantNotice: async ({ testId }: { testId: string }) => <div data-testid={testId} />,
}));
vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

import SecuritySettingsPage from '@/app/(console)/security-settings/page';

beforeEach(() => {
  session.activeTenant = 'acme';
  getEntryPolicyState.mockReset();
  getEntryPolicyState.mockResolvedValue({
    policy: { tenantId: 'acme', requireMfa: false, updatedAt: null, updatedBy: null },
    noTenant: false,
    permissionError: null,
    degraded: false,
    selfHasSecondFactor: true,
  });
});

describe('SecuritySettingsPage', () => {
  it('🔴 targets the ACTIVE tenant (path tenantId = X-Tenant-Id)', async () => {
    const { getByTestId } = render(await SecuritySettingsPage());
    expect(getEntryPolicyState).toHaveBeenCalledWith('acme');
    expect(getByTestId('entry-policy-section')).toHaveAttribute('data-tenant-id', 'acme');
    expect(getByTestId('security-settings-tenant')).toHaveTextContent('acme');
  });

  it('no active tenant → the tenant gate, no policy read', async () => {
    session.activeTenant = null;
    const { getByTestId } = render(await SecuritySettingsPage());
    expect(getByTestId('security-settings-no-tenant')).toBeInTheDocument();
    expect(getEntryPolicyState).not.toHaveBeenCalled();
  });

  it("platform scope '*' → pointer to 테넌트 상세, no policy read ('*' cannot hold a policy)", async () => {
    session.activeTenant = '*';
    const { getByTestId, queryByTestId } = render(await SecuritySettingsPage());
    expect(getByTestId('security-settings-platform-scope')).toHaveTextContent('테넌트');
    expect(queryByTestId('entry-policy-section')).toBeNull();
    expect(getEntryPolicyState).not.toHaveBeenCalled();
  });
});
