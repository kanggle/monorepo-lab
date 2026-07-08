import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import TenantsPage from '@/app/(console)/tenants/page';
import OperatorGroupsPage from '@/app/(console)/operator-groups/page';
import PermissionsPage from '@/app/(console)/permissions/page';
import PermissionSetsPage from '@/app/(console)/permission-sets/page';

/**
 * TASK-PC-FE-225 — IAM nav 정석 재편성: the 4 new stub routes it introduces
 * (`/tenants`, `/operator-groups`, `/permissions`, `/permission-sets`). Each
 * is a plain static server component (no data fetch / no auth gate — the
 * `(console)` layout supplies the auth guard) that renders a placeholder
 * notice. Real functionality is out of scope here (TASK-PC-FE-226/227/228,
 * ADR-MONO-046) — this only proves the route exists and renders (never a
 * 404), per the task's Failure Scenarios.
 */
afterEach(() => cleanup());

describe('TASK-PC-FE-225 stub routes', () => {
  it('/tenants renders the 테넌트 placeholder', () => {
    render(<TenantsPage />);
    expect(
      screen.getByRole('heading', { name: '테넌트' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('tenants-stub')).toBeInTheDocument();
  });

  it('/operator-groups renders the 운영자 그룹 placeholder', () => {
    render(<OperatorGroupsPage />);
    expect(
      screen.getByRole('heading', { name: '운영자 그룹' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('operator-groups-stub')).toBeInTheDocument();
  });

  it('/permissions renders the 권한 placeholder', () => {
    render(<PermissionsPage />);
    expect(
      screen.getByRole('heading', { name: '권한' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('permissions-stub')).toBeInTheDocument();
  });

  it('/permission-sets renders the 권한 세트 placeholder', () => {
    render(<PermissionSetsPage />);
    expect(
      screen.getByRole('heading', { name: '권한 세트' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('permission-sets-stub')).toBeInTheDocument();
  });
});
