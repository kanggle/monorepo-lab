import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { OrgAdminPanel } from '@/features/org-hierarchy/components/OrgAdminPanel';
import { KNOWN_ORG_ADMIN_ROLES } from '@/features/org-hierarchy/api/types';
import { messageForCode } from '@/shared/api/errors';
import type { OrgNode } from '@/features/org-hierarchy/api/types';

/**
 * `OrgAdminPanel` — subtree-scoped `ORG_ADMIN` assignment with the
 * grantable-roles no-escalation pre-filter (TASK-PC-FE-237 / ADR-047 D3).
 */

const NODE: OrgNode = {
  orgNodeId: 'n1',
  parentId: null,
  name: '테스트 회사',
  depth: 1,
  ceiling: { mode: 'UNBOUNDED' },
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

function renderPanel(
  overrides: Partial<React.ComponentProps<typeof OrgAdminPanel>> = {},
) {
  return render(
    <OrgAdminPanel
      node={NODE}
      admins={[]}
      adminsLoading={false}
      adminsError={null}
      grantableRoles={null}
      onGrant={vi.fn()}
      onRevoke={vi.fn()}
      grantPending={false}
      grantError={null}
      revokePending={false}
      revokeError={null}
      {...overrides}
    />,
  );
}

describe('OrgAdminPanel', () => {
  it('never offers SUPER_ADMIN in the role options (even if the caller can grant it)', () => {
    renderPanel({
      grantableRoles: ['SUPER_ADMIN', 'ORG_ADMIN', 'TENANT_ADMIN'],
    });
    const select = screen.getByTestId('org-admin-role-select');
    expect(
      within(select).queryByRole('option', { name: 'SUPER_ADMIN' }),
    ).not.toBeInTheDocument();
    expect(
      within(select).getByRole('option', { name: 'ORG_ADMIN' }),
    ).toBeInTheDocument();
  });

  it('renders a server 422 ORG_ADMIN_GRANT_OUT_OF_CEILING verbatim', () => {
    renderPanel({
      grantError: messageForCode('ORG_ADMIN_GRANT_OUT_OF_CEILING'),
    });
    expect(screen.getByTestId('org-admin-grant-error')).toHaveTextContent(
      '유효 상한이 비어 있어',
    );
  });

  it('falls back to the FULL known set (never an empty select) when grantable-roles is null', () => {
    renderPanel({ grantableRoles: null });
    const select = screen.getByTestId('org-admin-role-select');
    const options = within(select).getAllByRole('option');
    expect(options).toHaveLength(KNOWN_ORG_ADMIN_ROLES.length);
    expect(options.length).toBeGreaterThan(0);
  });
});
